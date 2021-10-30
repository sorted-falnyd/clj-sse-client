(ns clj-sse-client.event-test
  (:require
   [clj-sse-client.event :as sut]
   [clojure.test :as t]))

(t/deftest parse
  (t/testing "blank line emits dispatch"
    (t/is (= sut/+dispatch+ (sut/parse "")))
    (t/is (= sut/+dispatch+ (sut/parse " "))))
  (t/testing "comment line is ignored"
    (t/is (= sut/+ignore+ (sut/parse ": hello")))
    (t/is (= sut/+ignore+ (sut/parse ":"))))
  (t/testing "Event"
    (t/is (= (sut/->Event "fred") (sut/parse "event:fred")))
    (t/is (= (sut/->Event "fred") (sut/parse "event: fred"))))
  (t/testing "Data"
    (t/is (= (sut/->Data "fred") (sut/parse "data:fred")))
    (t/is (= (sut/->Data "fred") (sut/parse "data: fred"))))
  (t/testing "Id"
    (t/is (= (sut/->Id "") (sut/parse "id:")))
    (t/is (= (sut/->Id "") (sut/parse "id: ")))
    (t/is (= (sut/->Id "fred") (sut/parse "id:fred")))
    (t/is (= (sut/->Id "fred") (sut/parse "id: fred"))))
  (t/testing "Retry"
    (t/testing "Digits emit retry"
      (t/is (= (sut/->Retry 13) (sut/parse "retry:13")))
      (t/is (= (sut/->Retry 13) (sut/parse "retry: 13"))))
    (t/testing "Anything else is ignored"
      (t/is (= sut/+ignore+ (sut/parse "retry:1a")))
      (t/is (= sut/+ignore+ (sut/parse "retry: 1a")))))
  (t/testing "Line without colon"
    (t/testing "Existing field names"
      (t/is (= (sut/->Id "") (sut/parse "id")))
      (t/is (= (sut/->Data "") (sut/parse "data"))))
    (t/testing "Other field names are ignored"
      (t/is (= sut/+ignore+ (sut/parse "foo")))
      (t/is (= sut/+ignore+ (sut/parse "bar"))))))

(t/deftest step
  (t/testing "event"
    (t/testing "Set the event type buffer to field value."
      (let [state (sut/->State "" "" "")
            event-name "event1"
            event (sut/->Event event-name)]
        (t/is (= event-name (:event (sut/step state event)))))))
  (t/testing "data"
    (t/testing "Append the field value to the data buffer, then append a single U+000A LINE FEED (LF) character to the data buffer"
      (let [state (sut/->State "" "" "")
            data "data1"
            event (sut/->Data data)
            state (sut/step state event)]
        (t/is (= (str data "\n") (:data state)))
        (let [data' "data2"
              event (sut/->Data data')]
          (t/is (= (str data "\n" data' "\n") (:data (sut/step state event))))))))
  (t/testing "id"
    (t/testing "If the field value does not contain U+0000 NULL, then set the last event ID buffer to the field value."
      (let [state (sut/->State "" "" "")
            id "id"
            event (sut/->Id id)
            state (sut/step state event)]
        (t/is (= id (:id state)))))
    (t/testing "If the field value does contain U+0000 NULL, then ignore the field."
      (let [state (sut/->State "" "" "")
            id "i\u0000d"
            event (sut/->Id id)
            state (sut/step state event)]
        (t/is (= "" (:id state))))))
  (t/testing "retry"
    (t/testing "set the event stream's reconnection time to that integer - state unchanged"
      (let [state (sut/->State "" "" "")
            event (sut/->Retry 2)]
        (t/is (= state (sut/step state event))))))
  (t/testing "dispatch"
    (t/testing "id field is unchanged, event and data set to empty strings"
      (let [id "id"
            state (sut/->State id "event" "data")
            event (sut/->Dispatch)]
        (t/is (= (sut/->State id "" "") (sut/step state event)))))))

(defn -dispatch
  [state]
  (sut/effect state sut/+dispatch+))

(t/deftest effect
  (t/testing "dispatch"
    (t/testing "Dispatching on empty data emits no effect"
      (t/is (nil? (-dispatch (sut/->State "" "" "")))))
    (t/testing "State emits message with id, event and data"
      (let [id "id" event "event" data "data"]
        (t/is (= (sut/->Message id event data) (-dispatch (sut/->State id event data)))))
      (t/testing "If event is an empty string it is set to message by default"
        (let [id "id" event "" data "data"]
          (t/is (= (sut/->Message id "message" data)
                   (-dispatch (sut/->State id event data))))))
      (t/testing "If data ends with CR character it is removed"
        (let [id "id" event "event" data "data\n"]
          (t/is (= (sut/->Message id event (subs data 0 (dec (count data))))
                   (-dispatch (sut/->State id event data)))))
        (let [id "id" event "event" data "data\n\n"]
          (t/is (= (sut/->Message id event (subs data 0 (dec (count data))))
                   (-dispatch (sut/->State id event data))))))))
  (t/testing "All other messages produce no effects"
    (doseq [event [(sut/->Ignore)
                   (sut/->Event "event")
                   (sut/->Data "data")
                   (sut/->Id "Id")]]
      (t/is (nil? (sut/effect (sut/->State "a" "b" "c") event))))))

(defn simulate
  ([lines]
   (simulate (sut/->State "" "" "") lines))
  ([state lines]
   (loop [[line & lines] lines
          state state
          effects []]
     (if line
       (let [event (sut/parse line)
             effect (sut/effect state event)
             state (sut/step state event)]
         (recur lines state (if (nil? effect) effects (conj effects effect))))
       [state effects]))))



(t/deftest stream
  (t/testing "Example"
    (let [[_state effects] (simulate ["data: YHOO"
                                      "data: +2"
                                      "data: 10"
                                      ""])]
      (t/is (= [(sut/->Message "" "message" "YHOO\n+2\n10") ] effects))))
  (t/testing "Example"
    (let [[_state effects] (simulate
                            [": test stream"
                             ""
                             "data: first event"
                             "id: 1"
                             ""
                             "data:second event"
                             "id"
                             ""
                             "data:  third event"])]
      (t/is (= [(sut/->Message "1" "message" "first event")
                (sut/->Message "" "message" "second event")]
               effects))))
  (t/testing "Example"
    (let [[_state effects] (simulate
                            ["data"
                             ""
                             "data"
                             "data"
                             ""
                             "data:"])]
      (t/is (= [(sut/->Message "" "message" "")
                (sut/->Message "" "message" "\n")]
               effects))))
  (t/testing "Example"
    (let [[_state effects] (simulate
                            ["data:test"
                             ""
                             "data: test"
                             ""])]
      (t/is (= [(sut/->Message "" "message" "test")
                (sut/->Message "" "message" "test")]
               effects)))))
