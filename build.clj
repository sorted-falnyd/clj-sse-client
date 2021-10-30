(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]))

(def lib 'io.github.sorted-falnyd/clj-sse-client)
(def version (format "0.0.%s" (b/git-count-revs nil)))

(defn snapshot
  [opts]
  (if (:snapshot opts)
    (assoc opts :version (str version "-SNAPSHOT"))
    opts))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version)
      snapshot
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      snapshot
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      snapshot
      (bb/deploy)))
