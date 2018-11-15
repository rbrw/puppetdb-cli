# puppetdb-cli (experimental [GraalVM](https://www.graalvm.org/) version)

This is an experimental branch, toying with GraalVM's native-image
support, and right now the trivial scripts haven't been adjusted to
support anything other than amd64.

To build it:

    ./configure  # download graalvm
    ./compile    # compile puppet-db

To try it, first make sure you have PuppetDB up and running, and then:

    ./puppet db --version
    ./puppet db status --urls http://localhost:8080
    ./puppet db export --urls http://localhost:8080 export.gz
    ./puppet db import --urls http://localhost:8080 export.gz

The same code should also work fine via the normal JVM, and as a
demonstration, the included `./puppet-lein` helper does just that via
`lein trampoline run`:

    ./puppet-lein db --version
    ./puppet-lein db status --urls http://localhost:8080
    ./puppet-lein db export --urls http://localhost:8080 export.gz
    ./puppet-lein db import --urls http://localhost:8080 export.gz

Note that while the current code does support SSL, it can't yet handle
unknown certificates.

At the moment, the only code that's likely to be interesting is in
src/puppetlabs/puppetdb/tool/db.clj, and it's notably more limited
than the existing puppetdb-cli tool.

You can also play around with the downloaded graalvm using the
included `graal-env` helper like this:

    ./graal-env native-image --help

## Compatibility

This CLI is compatible with
[PuppetDB 4.0.0](https://docs.puppetlabs.com/puppetdb/4.0/release_notes.html#section)
and greater.

## Usage

Example usage:

```bash

$ puppet-query 'nodes[certname]{}'
[
  {
    "certname" : "baz.example.com"
  },
  {
    "certname" : "bar.example.com"
  },
  {
    "certname" : "foo.example.com"
  }
]
$ puppet-db status
{
  "puppetdb-status": {
    "service_version": "4.0.0-SNAPSHOT",
    "service_status_version": 1,
    "detail_level": "info",
    "state": "running",
    "status": {
      "maintenance_mode?": false,
      "queue_depth": 0,
      "read_db_up?": true,
      "write_db_up?": true
    }
  },
  "status-service": {
    "service_version": "0.3.1",
    "service_status_version": 1,
    "detail_level": "info",
    "state": "running",
    "status": {}
  }
}

```

## Configuration

The Rust PuppetDB CLI accepts a `--config=<path_to_config>` flag which allows
you to configure your ssl credentials and the location of your PuppetDB.

By default the tool will use `$HOME/.puppetlabs/client-tools/puppetdb.conf` as
it's configuration file if it exists. You can also configure a global
configuration (for all users) in `/etc/puppetlabs/client-tools/puppetdb.conf`
(`C:\ProgramData\puppetlabs\client-tools\puppetdb.conf` on Windows) to fall back
to if the per-user configuration is not present.

The format of the config file can be deduced from the following example.

```json
  {
    "puppetdb" : {
      "server_urls" : [
        "https://<PUPPETDB_HOST>:8081",
        "https://<PUPPETDB_REPLICA_HOST>:8081"
      ],
      "cacert" : "/path/to/cacert",
      "cert" : "/path/to/cert",
      "key" : "/path/to/private_key",
      "token-file" : "/path/to/token (PE only)"
      },
    }
  }
```
