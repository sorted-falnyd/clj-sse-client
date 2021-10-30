(ns dev
  (:require
   [clj-sse-client.client :as http]
   [clj-sse-client.sse :as sse]))

(comment (System/setProperty "jdk.internal.httpclient.debug" "true")
         (System/setProperty "jdk.httpclient.HttpClient.log" "all"))

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
