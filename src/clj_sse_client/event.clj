(ns clj-sse-client.event
  (:require
   [clojure.string :as str]))

(defn parse-int
  [s]
  (try
    (Integer/parseInt s)
    (catch Exception _ nil)))

(defn not-blank [s] (if (str/blank? s) nil s))
(defn non-empty [s] (if (.equals "" s) nil s))

(defprotocol IStep
  (-step [this state]))

(defprotocol IEffect
  (-effect [this state]))

(defprotocol IState
  (-accept [state event])
  (-emit [state event]))

(defrecord Message [last-event-id type data])

(defrecord State [id event data]
  IState
  (-accept [this event]
    (-step event this))
  (-emit [this event]
    (-effect event this)))

(defrecord Dispatch []
    IStep
    (-step [_ {:keys [id] :or {id ""}}] (->State id "" ""))
    IEffect
    (-effect [_ state]
      (let [data (:data state)]
        (if (.equals data "")
          nil
          (let [data (if (str/ends-with? data "\n")
                       (.substring data 0 (dec (.length data)))
                       data)
                event (:event state)
                event (if (str/blank? event) "message" event)
                id (:id state)]
            (->Message id event data))))))

(def +dispatch+ (->Dispatch))

(defrecord Ignore []
  IStep
  (-step [_ state] state)
  IEffect
  (-effect [_ _]))

(def +ignore+ (->Ignore))

(defrecord Event [event]
  IStep
  (-step [_ state] (assoc state :event event))
  IEffect
  (-effect [_ _]))

(defrecord Data [data]
  IStep
  (-step [_ {data' :data :as state}]
    (assoc state :data (if-let [data' (non-empty data')]
                         (str data' data "\n")
                         (str data "\n"))))
  IEffect
  (-effect [_ _]))

(defrecord Id [^String id]
  IStep
  (-step [_ state]
    (if (.contains id "\u0000")
      state
      (assoc state :id id)))
  IEffect
  (-effect [_ _]))

(defrecord Retry [n]
  IStep
  (-step [_ state] state)
  IEffect
  (-effect [_ _] n))

(defn -conform-value
  "Collect the characters on the line after the first U+003A COLON
  character (:), and let value be that string. If value starts with a
  U+0020 SPACE character, remove it from value."
  [^String value]
  (when value (.stripLeading value)))

(defn -parse-dispatch
  [field value]
  (let [value (-conform-value value)]
    (case field
      "event" (->Event value)
      "data" (->Data value)
      "id" (->Id value)
      "retry" (if-let [n (parse-int value)]
                (->Retry n)
                +ignore+)
      +ignore+)))

(defn parse
  "If the line is empty (a blank line)
  - Dispatch the event, as defined below.

  If the line starts with a U+003A COLON character (:)
  - Ignore the line.

  If the line contains a U+003A COLON character (:)
  - Collect the characters on the line before the first U+003A COLON
    character (:), and let field be that string.
  - Collect the characters on the line after the first U+003A COLON
    character (:), and let value be that string. If value starts with a
    U+0020 SPACE character, remove it from value.
  - Process the field using the steps described below, using field as the
    field name and value as the field value.

  Otherwise, the string is not empty but does not contain a U+003A COLON character (:)
  - Process the field using the steps described below, using the whole
    line as the field name, and the empty string as the field value."
  [line]
  (cond
    (str/blank? line) +dispatch+
    (str/starts-with? line ":") +ignore+
    (str/includes? line ":")
    (let [[field value] (str/split line #":" 2)]
      (-parse-dispatch field value))
    :else (-parse-dispatch line "")))

(defrecord Unset []
  IState
  (-accept [this event]
    (let [clazz (class event)]
      (cond
        (instance? Dispatch clazz) this
        (instance? Ignore clazz) this
        :else (-accept (->State "" "" "") event))))
  (-emit [_ _]))

(defn step
  [state event]
  (-step event state))

(defn effect
  [state event]
  (-effect event state))
