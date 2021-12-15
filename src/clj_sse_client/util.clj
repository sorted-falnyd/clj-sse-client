(ns clj-sse-client.util
  (:require
   [clojure.spec.alpha :as s])
  (:import
   (java.io File)
   (java.nio.file Path)
   (java.time Duration)
   (java.net URI URL)))

(set! *warn-on-reflection* true)

(defprotocol IPath
  (-path [this]))

(extend-protocol IPath
  Path
  (-path [p] p)

  java.io.File
  (-path [f] (.toPath f))

  String
  (-path [s] (Path/of ^String s))

  URI
  (-path [uri] (Path/of ^URI uri)))

(s/def ::path-able
  (s/or
   :uri #(instance? URI %)
   :path #(instance? Path %)
   :File #(instance? File %)
   :string #(instance? String %)))

(defprotocol IURI
  (^URI -uri [this]))

(extend-protocol IURI
  URI
  (-uri [u] u)

  URL
  (-uri [u] (.toURI ^URL u))

  Path
  (-uri [p] (.toUri ^Path p))

  File
  (-uri [f] (.toURI f))

  String
  (-uri [s] (URI/create s)))

(s/def ::uri-able
  (s/and
   (s/or
    :uri #(instance? URI %)
    :url #(instance? URL %)
    :path #(instance? Path %)
    :File #(instance? File %)
    :string #(instance? String %))
   #(try (-uri (val %)) (catch Exception _))))

(defprotocol IDuration (-duration [this]))

(extend-protocol IDuration
  Long
  (-duration [n] (Duration/ofMillis n))
  Duration
  (-duration [d] d))

(s/def ::duration-able
  (s/or
   :number nat-int?
   :duration #(instance? Duration %)))
