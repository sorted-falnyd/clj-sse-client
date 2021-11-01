(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]))

(def lib 'io.github.sorted-falnyd/clj-sse-client)
(defn -version [x] (format "0.0.%s" x))

(defn ->opts
  [{:keys [snapshot] :as opts}]
  (let [revs (b/git-count-revs nil)
        version (cond-> (-version revs) snapshot (str "-SNAPSHOT"))]
    (-> opts
        (assoc :lib lib)
        (assoc :version version)
        (doto (->> (println "Running with opts:"))))))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      ->opts
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      ->opts
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      ->opts
      (bb/deploy)))
