(ns clj-sse-client.body-handler
  (:require
   [clj-sse-client.util :refer [-path]])
  (:import
   (java.net.http
    HttpResponse$BodyHandlers)))

(defn buffering [handler size]
  (HttpResponse$BodyHandlers/buffering handler size))

(defn discarding []
  (HttpResponse$BodyHandlers/discarding))

(defn from-line-subscriber [subscriber]
  (HttpResponse$BodyHandlers/fromLineSubscriber subscriber))

(defn from-subscriber [subscriber]
  (HttpResponse$BodyHandlers/fromSubscriber subscriber))

(defn of-byte-array []
  (HttpResponse$BodyHandlers/ofByteArray))

(defn of-file [f]
  (HttpResponse$BodyHandlers/ofFile (-path f)))

(defn of-input-stream []
  (HttpResponse$BodyHandlers/ofInputStream))

(defn of-lines []
  (HttpResponse$BodyHandlers/ofLines))

(defn of-publisher []
  (HttpResponse$BodyHandlers/ofPublisher))

(defn of-string
  ([] (HttpResponse$BodyHandlers/ofString))
  ([charset] (HttpResponse$BodyHandlers/ofString charset)))

(defn replacing [v] (HttpResponse$BodyHandlers/replacing v))
