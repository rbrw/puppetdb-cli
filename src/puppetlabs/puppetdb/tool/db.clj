(ns puppetlabs.puppetdb.tool.db
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str])
  (:import
   (clojure.lang ExceptionInfo)
   (java.io File FileNotFoundException)
   (java.net URI URL URLEncoder)
   (java.nio.file Path Paths))
  (:gen-class))

(defn msg [stream & items]
  (binding [*out* stream]
    (apply print items)))

(defn msgn [stream & items]
  (binding [*out* stream]
    (apply println items)))

(defn exit [rc]
  (throw (ex-info "" {:kind ::exit ::rc rc})))

(defn version []
  (with-open [r (-> "META-INF/maven/puppetdb/puppetdb/pom.properties"
                    io/resource
                    io/reader)]
    (-> (doto (java.util.Properties.) (.load r))
        (.getProperty "version"))))

(defn usage [stream]
  (msgn stream (str/trim "
Usage:
  puppet-db --help
  puppet-db --version
  puppet-db status [common] [connection]
  puppet-db export [common] [connection] [--anon <profile>] [--] [<path>]
  puppet-db import [common] [connection] [--] [<path>]
Miscellaneous options:
  -h --help            Show this screen
  -v --version         Show version
Export options:
  --anon=<profile>     Archive anonymization [default: none]
[common] options:
  -c, --config <path>  Config file location.  If not specified tries to read
                       ~/.puppetlabs/client-tools/puppetdb.conf or
                       /etc/puppetlabs/client-tools/puppetdb.conf.
[connection] options:
  -u, --urls=<urls>    Urls to PuppetDB instances
  --cacert=<path>      Path to CA certificate for auth
  --cert=<path>        Path to client certificate for auth
  --key=<path>         Path to client private key for auth
  --token=<path>       Path to RBAC token for auth (PE Only)")))

(defn home-dir []
  (or (System/getProperty "user.home")
      (System/getenv "HOME")))

(defn exit-on-misuse []
  (usage *err*)
  (exit 2))

(defn exit-on-unsupported-arg [arg]
  (msgn *err* "puppet-db: argument" arg "is not yet supported")
  (exit 2))

(defn parse-urls [s]
  (->> (str/split s #",")
       (map #(if (or (str/starts-with? % "http://")
                     (str/starts-with? % "https://"))
               %
               (str "https://" %)))
       (map #(str/replace % #"/+$" ""))
       (map #(URI. %))))

(defn encode-params
  [m]
  (->> m 
       (map #(vector (URLEncoder/encode (name %1)) "=" (URLEncoder/encode %2)))
       (interpose "&")
       flatten
       (apply str)))

;; FIXME: don't just throw on errors

(defn stream-from-uri [uri]
  (let [c (doto (.openConnection (.toURL uri))
            (.setRequestProperty "User-Agent" "puppetdb-cli")
            (.setRequestMethod "GET")
            .connect)]
    (.getInputStream c)))

(defn stream-to-uri [uri]
  ;; FIXME: multpart, etc.
  (let [c (doto (.openConnection (.toURL uri))
            (.setRequestProperty "User-Agent" "puppetdb-cli")
            (.setRequestMethod "POST")
            (.setDoInput false)
            (.setDoOutput true)
            .connect)]
    (.getOutputStream c)))

(defn read-config-file [path]
  ;; FIXME: more validation?
  ;; FIXME: be nice when config syntax is bad...
  (let [cfg (-> (File. path) slurp json/parse-string)
        opts #{:server_urls :cacert :cert :key :token-file}]
    ;; FIXME: correct to ignore everything except puppetdb section?
    (when-let [unexpected (seq (set/difference (keys (:puppetdb cfg)) opts))]
      (msgn *err* "puppet-db: unexpected item(s) in config file:" (seq opts)))
    (let [cfg (update cfg :puppetdb #(set/rename-keys % {:server_urls :urls
                                                         :token-file :token}))]
      (if-not (get-in cfg [:puppetdb :urls])
        cfg
        (update-in cfg [:puppetdb :urls] parse-urls)))))

(defn export-db [pdb-url anon-profile dest-path]
  (let [params (when anon-profile
                 (str "?" (encode-params {:anonymization_profile anon-profile})))]
    (with-open [src (-> (URI. (str pdb-url "/pdb/admin/v1/archive" params))
                        stream-from-uri)]
      ;; FIXME: is io/copy ok here, perf-wise, etc.?
      (io/copy src
               (if dest-path (File. dest-path) System/out)
               :buffer-size 65536))))

(defn import-db [pdb-url src-path]
  (with-open [dest (-> (URI. (str pdb-url "/pdb/admin/v1/archive"))
                       stream-to-uri)]
    ;; FIXME: is io/copy ok here, perf-wise, etc.?
    (io/copy (if src-path (File. src-path) System/in)
             dest
             :buffer-size 65536)))

(defn status [pdb-urls]
  (-> (into {}
            (for [url pdb-urls]
              [url (-> (URI. (str url "/status/v1/services"))
                       stream-from-uri slurp json/parse-string)]))
      (json/generate-stream *out* {:pretty true})))

(defn maybe-read-config-file [args]
  (let [[cfg ignored-args]
        (loop [args args
               cfg {}
               ignored-args []]
          (if-not (seq args)
            [cfg ignored-args]
            (case (first args)
              ("-c" "--config")
              (if-let [[path & remainder] (seq (rest args))]
                (recur remainder (assoc cfg :config path) ignored-args)
                (exit-on-misuse))
              (recur (rest args) cfg (conj ignored-args (first args))))))
        maybe-read #(try
                      (read-config-file %)
                      (catch FileNotFoundException _ nil))
        cfg (if-let [c (:config cfg)]
              (read-config-file c)
              (or (when-let [home (home-dir)]
                    (maybe-read (str home "/.puppetlabs/client-tools/puppetdb.conf")))
                  ;; FIXME: windows
                  (maybe-read (File. "/etc/puppetlabs/client-tools/puppetdb.conf"))))]
    [cfg ignored-args]))

(defn extract-connection-config [args config]
  ;; FIXME: implement ssl, etc, opts.
  (loop [args args
         cfg {}
         ignored-args []]
    (if-not (seq args)
      [cfg ignored-args]
      (case (first args)
        ("-u" "--urls")
        (if-let [[urls & remainder] (seq (rest args))]
          (recur remainder
                 (assoc-in cfg [:puppetdb :urls] (parse-urls urls))
                 ignored-args)
          (exit-on-misuse))
        (recur (rest args) cfg (conj ignored-args (first args)))))))

(defn export-args->config [args]
  (let [[cfg args] (maybe-read-config-file args)
        [cfg args] (extract-connection-config args cfg)]
    (loop [args args
           cfg cfg]
      (if-not (seq args)
        cfg
        (case (first args)
          "--anon"
          (if-let [[profile & remainder] (seq (rest args))]
            (recur remainder (assoc-in cfg [:puppetdb :anon] profile))
            (exit-on-misuse))
          (cond
            (= "--" (first args))
            (if (= 1 (count (rest args)))
              (assoc-in cfg [:puppetdb :dest] (first (rest args)))
              (exit-on-misuse))

            (str/starts-with? (first args) "-")
            (do
              (msgn *err* "puppet-db: unknown export argument" (pr-str (first args)))
              (exit 2))

            :else
            (if (= 1 (count args))
              (assoc-in cfg [:puppetdb :dest] (first args))
              (exit-on-misuse))))))))

(defn args->export-db [args]
  (let [cfg (export-args->config args)
        {:keys [anon dest urls]} (:puppetdb cfg)]
    (when-not (seq urls)
      (msgn *err* "puppet-db: no server url provided")
      (exit 2))
    (when-not (= (count urls) 1)
      (msgn *err* "puppet-db: export only accepts one server url")
      (exit 2))
    (export-db (first urls) anon dest)))

(defn import-args->config [args]
  (let [[cfg args] (maybe-read-config-file args)
        [cfg args] (extract-connection-config args cfg)]
    (loop [args args
           cfg cfg]
      (if-not (seq args)
        cfg
        (cond
          (= "--" (first args))
          (if (= 1 (count (rest args)))
            (assoc-in cfg [:puppetdb :dest] (first (rest args)))
            (exit-on-misuse))

          (str/starts-with? (first args) "-")
          (do
            (msgn *err* "puppet-db: unknown import argument" (pr-str (first args)))
            (exit 2))

          :else
          (if (= 1 (count args))
            (assoc-in cfg [:puppetdb :source] (first args))
            (exit-on-misuse)))))))

(defn args->import-db [args]
  (let [cfg (import-args->config args)
        {:keys [source urls]} (:puppetdb cfg)]
    (when-not (seq urls)
      (msgn *err* "puppet-db: no server url provided")
      (exit 2))
    (when-not (= (count urls) 1)
      (msgn *err* "puppet-db: import only accepts one server url")
      (exit 2))
    (import-db (first urls) source)))

(defn status-args->config [args]
  (let [[cfg args] (maybe-read-config-file args)
        [cfg args] (extract-connection-config args)]
    (when (seq args)
      (exit-on-misuse))
    cfg))

(defn args->status [args]
  (let [cfg (export-args->config args)
        {:keys [urls]} (:puppetdb cfg)]
    (when-not (seq urls)
      (msgn *err* "puppet-db: no server url provided")
      (exit 2))
    (status urls)))

(defn main [args]
  (case (vec args)
    ["--help"] (do (usage *out*) (exit 0))
    ["--version"] (do (msgn *out* (version)) (exit 0))
    (let [[command & args] args]
      (case command
        nil (exit-on-misuse)
        "export" (args->export-db args)
        "import" (args->import-db args)
        "status" (args->status args)
        (do
          (msgn *err* "puppet-db:" (pr-str command) "is not a valid command."
                " See  \"puppet-db --help\".")
          (exit 2))))))

(defn -main [& args]
  (let [rc (try
             (main args)
             0
             (catch ExceptionInfo ex
               (let [data (ex-data ex)]
                 (when-not (= ::exit (:kind data))
                   (throw ex))
                 (::rc data))))]
    (shutdown-agents)
    (flush)
    (binding [*out* *err*] (flush))
    (System/exit rc)))
