(ns clj-sse-client.client
  (:require
   [clj-sse-client.util :as u]
   [clojure.spec.alpha :as s]
   [clj-sse-client.client :as client])
  (:import
   (java.util.concurrent CompletableFuture)
   (java.net
    CookieManager
    #_CookieHandler
    CookiePolicy
    #_CookieStore
    HttpCookie)
   (java.net.http
    HttpResponse
    HttpClient
    HttpClient$Version
    HttpClient$Redirect
    HttpResponse
    HttpRequest
    HttpRequest$Builder
    HttpRequest$BodyPublisher
    HttpRequest$BodyPublishers
    HttpResponse$BodyHandler
    HttpResponse$BodyHandlers)))

(set! *warn-on-reflection* true)

(defprotocol IBodyPublisher
  (-publisher [body] "Try to coerce the body to a Publisher of a compatible type."))

(def ^:const byte-array-class (Class/forName "[B"))

(extend-protocol IBodyPublisher

  (Class/forName "[B")
  (-publisher [arr]
    (HttpRequest$BodyPublishers/ofByteArray arr))

  nil
  (-publisher [_]
    (HttpRequest$BodyPublishers/noBody))

  java.nio.file.Path
  (-publisher [f]
    (HttpRequest$BodyPublishers/ofFile f))

  java.io.File
  (-publisher [f]
    (-publisher (.toPath f)))

  java.io.InputStream
  (-publisher [is]
    (HttpRequest$BodyPublishers/ofInputStream is))

  String
  (-publisher [s]
    (HttpRequest$BodyPublishers/ofString s))

  Object (-publisher [o] o))

(s/def ::body
  (s/or
   :byte-array #(instance? byte-array-class %)
   :nil nil?
   :path #(instance? java.nio.file.Path %)
   :file #(instance? java.io.File %)
   :input-stream #(instance? java.io.InputStream %)
   :string #(instance? String %)
   :publisher #(instance? HttpRequest$BodyPublisher %)))

(s/def ::redirect #{:never :always :normal})

(defn redirect
  [r]
  (case r
    :never HttpClient$Redirect/NEVER
    :always HttpClient$Redirect/ALWAYS
    :normal HttpClient$Redirect/NORMAL))

(s/fdef http-version
  :args (s/cat :version ::redirect)
  :ret #(instance? HttpClient$Redirect %))

(s/def ::version #{1 1.1 2})

(defn http-version
  [v]
  (case v
    (1.1 1) HttpClient$Version/HTTP_1_1
    2 HttpClient$Version/HTTP_2))

(s/fdef http-version
  :args (s/cat :version ::version)
  :ret #(instance? HttpClient$Version %))

(s/def ::cookie-policy #{:accept-all :accept-none :accept-original-server})

(defn cookie-policy
  [k]
  (case k
    :accept-all CookiePolicy/ACCEPT_ALL
    :accept-none CookiePolicy/ACCEPT_NONE
    :accept-original-server CookiePolicy/ACCEPT_ORIGINAL_SERVER))

(s/fdef cookie-policy
  :args (s/cat :policy ::cookie-policy)
  :ret #(instance? CookiePolicy %))

(defn -cookie-manager
  (^CookieManager []
   (CookieManager.))
  (^CookieManager [^CookiePolicy policy]
   (doto (-cookie-manager) (.setCookiePolicy policy))))

(defn find-set-cookie
  [^HttpResponse resp]
  (into
   []
   (mapcat #(java.net.HttpCookie/parse %))
   (-> resp
       .headers
       .map
       (.get "set-cookie"))))

(defn strip-cookie
  [^HttpCookie c]
  (HttpCookie. (.getName c) (.getValue c)))

(defn noop
  ([])
  ([_a])
  ([_a _a])
  ([_a _a _a]))

(s/def ::client #(instance? HttpClient %))

(defn client
  "Create a HttpClient with provided settings or with default settings if
  non are provided."
  (^HttpClient []
   (HttpClient/newHttpClient))
  (^HttpClient
   [{:keys [authenticator
            connect-timeout
            cookie-handler
            executor
            follow-redirects
            priority
            proxy
            ssl-context
            ssl-parameters
            version]}]
   (let [builder (HttpClient/newBuilder)]
     (cond-> builder
       authenticator (.authenticator authenticator)
       connect-timeout (.connectTimeout (u/-duration connect-timeout))
       cookie-handler (.cookieHandler cookie-handler)
       executor (.executor executor)
       follow-redirects (.followRedirects (redirect follow-redirects))
       priority (.priority priority)
       proxy (.proxy proxy)
       ssl-context (.sslContext ssl-context)
       ssl-parameters (.sslParameters ssl-parameters)
       version (.version (http-version version)))
     (.build builder))))

(s/def ::headers
  (s/or
   :headers-map (s/map-of string? string?)
   :headers-tuples (s/coll-of (s/tuple string? string?))))

(s/def ::method #{:get :GET :put :PUT :post :POST :delete :DELETE})

(s/def ::uri ::u/uri-able)
(s/def ::port pos-int?)

(s/def ::timeout ::u/duration-able)

(s/def ::http-request-options
  (s/keys
   :req-un [::uri ::method]
   :opt-un [::headers ::body ::version ::timeout]))

(s/def ::http-request #(instance? HttpRequest %))

(defn request
  "Build a request to `uri` with `method`.
  `uri` can be URI, URL, Path, File or String.
  for POST and PUT requests a `body` should be specified. It can be a byte
  array, nil, Path, File, Input Stream, String or a BodyPublisher.
  Headers, timeout and version are optional."
  [{:keys [uri method headers timeout body version]}]
  (let [^HttpRequest$Builder builder (HttpRequest/newBuilder (u/-uri uri))]
    (case method
      (:get :GET) (.GET builder)
      (:put :PUT) (.PUT builder (-publisher body))
      (:post :POST) (.POST builder (-publisher body))
      (:delete :DELETE) (.DELETE builder))
    (doseq [[k v] headers]
      (.setHeader builder k v))
    (when timeout (.timeout builder (u/-duration timeout)))
    (when version (.version builder (http-version version)))
    (.build builder)))

(s/fdef request
  :args (s/cat :opts ::http-request-options)
  :ret ::http-request)

(defprotocol IRequest
  (-request [this] "Coerce the input to a request."))

(extend-protocol IRequest
  HttpRequest
  (-request [this] this)
  java.util.Map
  (-request [this] (request this)))

(defprotocol IHttpClient
  (-send! [client request] [client request handler]))

(extend-protocol IHttpClient
  HttpClient
  (-send! [client request]
    (.sendAsync
     client
     (-request request)
     (HttpResponse$BodyHandlers/ofByteArray)))
  (-send! [client request handler]
    (.sendAsync client (-request request) handler)))

(defn create
  ([] (create nil))
  ([opts]
   (let [client (client opts)]
     (reify IHttpClient
       (-send! [_ request]
         (.sendAsync client request (HttpResponse$BodyHandlers/ofByteArray)))
       (-send! [_ request handler]
         (.sendAsync client request handler))))))

(defn send!
  (^CompletableFuture [client request]
   (send! client (-request request) (HttpResponse$BodyHandlers/ofByteArray)))
  (^CompletableFuture [client request handler]
   (-send! client (-request request) handler)))

(defn send-sync!
  ([client request]
   (send-sync! client request (HttpResponse$BodyHandlers/ofByteArray)))
  ([client request handler]
   (.get (send! client request handler))))

(defn failed?
  ^Boolean [^HttpResponse response]
  (<= 400 (.statusCode response)))
