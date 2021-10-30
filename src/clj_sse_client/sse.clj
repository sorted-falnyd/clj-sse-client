(ns clj-sse-client.sse
  (:require
   [clj-sse-client.body-handler :as h]
   [clj-sse-client.client :as http :refer [noop]]
   [clj-sse-client.event :as e])
  (:import
   (java.util.concurrent Flow$Subscriber Flow$Subscription)
   (java.net.http HttpResponse$BodyHandlers)))

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

(comment
  (def -client (http/client))
  (def -req (http/request {:uri "http://localhost:8005/lowfreq"
                           :headers {}
                           :method :get}))
  (def opts {:on-complete (fn [state] (println "Subscription completed with state:" state))
             :on-error (fn [state ^Throwable t] (println "Error with state:" state t))
             :on-next (fn [eff] (doto eff println))
             :on-subscribe (fn [state] (println "Initializing subscription with state:" state))})
  (def -sub (sse-subscription -client -req opts))
  (def resp (first -sub))
  (def subscriber (second -sub))
  (def subscription (http/-subscription subscriber))
  (.cancel subscription))

(comment
  (def -client (http/client {:follow-redirects :always
                             :cookie-handler (http/-cookie-manager (http/cookie-policy :accept-all))}))
  (def -client (http/client {:cookie-handler nil}))
  (def -client (http/client))
  (.get (.cookieHandler -client))

  (def resp
    (.send
     -client
     (http/request {:uri "http://localhost:80/~/login"
                    :body "password=lidlut-tabwed-pillex-ridrup"
                    :method :post})
     (HttpResponse$BodyHandlers/ofString)))
  (let [c (first (map http/strip-cookie (http/find-set-cookie resp)))]
    (str (.getName c) (.getValue c)));; => "urbauth-~zod0v4.phvuh.dhssc.8a295.efvq8.jqun9"

  (def -sub-req
    (http/request {:uri "http://localhost:80/~/channel/a-channel"
                   :body
                   "[{\"id\":1,\"action\":\"poke\",\"ship\":\"zod\",\"app\":\"hood\",\"mark\":\"helm-hi\",\"json\":\"Opening airlock\"}]"

                   ;; :version HttpClient$Version/HTTP_1_1
                   :headers
                   {
                    "Accept" "*/*"
                    "Content-Type", "application/json"
                    "Cookie" (let [c (first (map http/strip-cookie (http/find-set-cookie resp)))]
                               (str (.getName c) "=" (.getValue c)))
                    }
                   :method :put}))
  (.map (.headers -sub-req))
  (.version -sub-req)
  (.bodyPublisher -sub-req)
  (clojure.reflect/reflect -client)

  (def resp2 (.send -client -sub-req (HttpResponse$BodyHandlers/ofString)))
  (def resp2 (.send (http/client) -sub-req (HttpResponse$BodyHandlers/ofString)))
  (.map (.headers resp2))
  (println (.body resp2))

  (slurp (.body resp2))

  )
