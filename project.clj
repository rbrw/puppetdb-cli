(defproject puppetdb "2.0.0-SNAPSHOT"
  :description "PuppetDB command line client"
  :url "http://example.com/FIXME"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :pedantic? :abort
  :dependencies [[cheshire "5.8.0"]
                 [org.clojure/clojure "1.8.0"]]
  :main nil
  :resource-paths ["resources"]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
