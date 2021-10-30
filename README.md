# io.github.sorted-falnyd/clj-sse-client

No dependency, specification compliant SSE client in Clojure

https://html.spec.whatwg.org/multipage/server-sent-events.html

## Usage

```clojure
(ns user
  (:require
   [clj-sse-client.client :as http]
   [clj-sse-client.sse :as sse]))

(def client (http/client))

(def request
  {:uri "http://localhost:8005/lowfreq"
   :headers {}
   :method :get})

(def opts
  {:on-complete (fn [state] (println "Subscription completed with state:" state))
   :on-error (fn [state ^Throwable t] (println "Error with state:" state t))
   :on-next (fn [eff] (doto eff println))
   :on-subscribe (fn [state] (println "Initializing subscription with state:" state))})

(def sub (sse/sse-subscription client request opts))

(.cancel (sse/-subscription (second sub)))

```

## Testing

Run the project's tests:

    $ clojure -T:build test

### Integration testing
    
Besides the tests verifying correctness of the SSE implementation, the
following can be used for integration tests:

https://github.com/OasisDigital/sse-example-server.git

Clone the repo and follow the instructions, and run the example from the
usage section.

## Building and deployment

Run the project's CI pipeline and build a JAR:

    $ clojure -T:build ci
    
This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables (requires the `ci` task be run first):

    $ clojure -T:build deploy

The library will be deployed to io.github.sorted-falnyd/clj-sse-client on clojars.org by default.

## License

Copyright © 2021 sorted-falnyd

Distributed under the Eclipse Public License version 1.0.
