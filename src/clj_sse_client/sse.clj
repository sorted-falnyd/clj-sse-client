(ns clj-sse-client.sse
  (:require
   [clj-sse-client.body-handler :as h]
   [clj-sse-client.client :as http :refer [noop]]
   [clj-sse-client.event :as e])
  (:import
   (java.util.function BiFunction)
   (java.util.concurrent
    Flow$Subscriber
    Flow$Subscription
    Executor
    CompletableFuture)))

(set! *warn-on-reflection* true)

(defprotocol ISubscriber
  "A protocol for accessing a subscription from an underlying object."
  (-subscription [subscriber]))

(definterface ISSE
  (nextEvent [line]))

(deftype SSEFlowSubscriber
    [on-complete
     on-error
     on-next
     on-subscribe
     ^:unsynchronized-mutable ^Flow$Subscription subscription
     ^:unsynchronized-mutable state]
  ISSE
  (nextEvent [_ line]
    (when line
      (let [message (e/parse line)
            effect (e/effect state message)
            state' (e/step state message)
            ret (if effect (on-next effect) true)]
        (set! state state')
        ret)))
  Flow$Subscriber
  (onSubscribe [_ sub]
    (set! subscription sub)
    (set! state (e/->State "" "" ""))
    (.request sub 1)
    (on-subscribe state))
  (onComplete [_]
    (on-complete state))
  (onError [_ throwable]
    (on-error state throwable))
  (onNext [this item]
    (if (.nextEvent this item)
      (.request subscription 1)
      (.cancel subscription)))
  ISubscriber
  (-subscription [_] subscription))

(defn sse-flow-subscriber
  "Create a [[SSEFlowSubscriber]] with callbacks similar to
  a [[Flow$Subscriber]] with slight differences:

  `on-complete`: called when the flow completes with an internal state.
  `on-subscribe`: called on subscription with an internal state.
  `on-next`: Unlike a [[Flow$Subscriber]], called when an SSE event is
  emitted from the buffer with the event as an argument.
  `on-next` SHOULD return a truth-y value. A false-y value will cause
  events to stop being consumed.
  `on-error`: Called with the internal state and a Throwable.

  The returned subscriber also implements [[ISubscriber]], exposing
  its internal [[Flow$Subscription]], and [[ISSE]] which defines how the
  next SSE event must be handled."
  ^SSEFlowSubscriber
  [{:keys [on-complete
           on-error
           on-next
           on-subscribe]
    :or {on-complete  noop
         on-error     identity
         on-next      identity
         on-subscribe noop}}]
  (new SSEFlowSubscriber on-complete on-error on-next on-subscribe nil nil))

(defn sse-subscription
  "Initialize a SSE subscription with `client` according to `request` and `opts`.
  `opts` are passed to [[sse-flow-subscriber]].
  Returns a tuple of [CompletableFuture<Response> [[ISubscriber]]].
  It's worth considering the future won't be completed until the end of
  the response. If the subscription is expected to go on for a while,
  don't block a thread waiting for completion."
  [client request opts]
  (let [subscriber (sse-flow-subscriber opts)
        resp (http/send-async! client request (h/from-line-subscriber subscriber))]
    [resp subscriber]))

(defprotocol IConnection
  (-connect [this] [this subscription])
  (-reconnect [this]))

(defn reconnect-callback
  ^BiFunction [connection attempts max-reconnection-attemps]
  (reify BiFunction
    (apply [_ r e]
      (if (nil? e)
        (println "Connection complete")
        (when (< attempts max-reconnection-attemps)
          (println "Reconnecting")
          (-reconnect connection))))))

(defn delayed-executor
  ^Executor [executor delay]
  (if executor
    (CompletableFuture/delayedExecutor
     delay
     java.util.concurrent.TimeUnit/SECONDS
     executor)
    (CompletableFuture/delayedExecutor
     delay
     java.util.concurrent.TimeUnit/SECONDS)))

(deftype SSEConnection
    [client
     connection-request
     ^:volatile-mutable subscription
     options
     ^:volatile-mutable subscriber
     ^:volatile-mutable response
     ^Executor reconnect-executor
     reconnect?
     ^:volatile-mutable reconnect-delay
     ^:volatile-mutable attempts]
  IConnection
  (-reconnect [this]
    (set! attempts (inc attempts))
    (-connect this))
  (-connect [this]
    (-connect this subscription))
  (-connect [this sub]
    (set! subscription sub)
    (let [-subscriber (sse-flow-subscriber subscription)
          -resp (http/send-async! client connection-request (h/from-line-subscriber -subscriber))
          reconnect (reconnect-callback this attempts (:max-reconnection-attemps options))]
      (.handleAsync ^CompletableFuture -resp reconnect (delayed-executor reconnect-executor reconnect-delay))
      (set! response -resp)
      (set! subscriber -subscriber)))
  ISubscriber
  (-subscription [_] (-subscription subscriber))
  java.lang.AutoCloseable
  (close [this] (.cancel ^Flow$Subscription (-subscription this))))

(def default-connection-options
  {:max-reconnection-attemps 3
   :reconnect? false
   :reconnect-delay 20})

(defn sse-connection
  "Takes HttpClient, connection request subscription specification and connection options.
  Returns a [[SSEConnection]] which can connect, close and reconnect."
  [client
   connection-request
   subscription
   options]
  (let [{:keys [executor reconnect? reconnect-delay]}
        (merge default-connection-options options)]
    (new
     SSEConnection
     client
     connection-request
     subscription
     options
     nil
     nil
     executor
     reconnect?
     reconnect-delay
     0)))

(defn connect
  "Initiated a SSE connection.
  Optionally takes SSE options which override the existing options.
  This allows referring to the connection object in the callbacks."
  ([connection]
   (-connect connection))
  ([connection sse-options]
   (-connect connection sse-options)))

(comment
  (def client (http/client))
  (def req (http/request {:uri "http://localhost:8005/lowfreq"
                          :headers {}
                          :method :get}))
  (def opts {:on-complete (fn [state] (println "Subscription completed with state:" state))
             :on-error (fn [state ^Throwable t] (println "Error with state:" state t))
             :on-next (fn [eff] (doto eff println))
             :on-subscribe (fn [state] (println "Initializing subscription with state:" state))})
  (def conn (sse-connection client req opts {:reconnect? true}))
  (connect conn)

  (.close conn))

