(ns elm.example
  (:require
   [elm.core :refer :all]
   [clojure.core.async :as async
    :refer [>! <! alts! chan put! go]]))

(def events (event-stream))

(def tick (input events :tick :none))
(def poop (input events :poop 0))

(def tick-count (foldp (fn [v x]
                         (update-in v [x] (fnil inc 0)))
                       {}
                       tick))

(def tick-wait (lift (fn [tick]
                       (Thread/sleep 2000)
                       (str "Waited " tick))
                     tick))

(def tick-drop (lift (fn [a b] (str a b))
                     (async events tick-wait)
                     poop))

(def graph tick-drop)

(def alive? (atom true))

(defn run []
  (let [[v c] (port graph)]
    (go
      (loop [v {:type :init :value v}]
        (println v)
        (when (not= v :death)
          (recur (<! c))))))

  (go
    (while @alive?
      (<! (async/timeout 1000))
      (>! events [:tick :tick])
      (<! (async/timeout 1000))
      (>! events [:tick :tock]))
    (>! events [:tick :death])
    (<! (async/timeout 2000))
    (reset! alive? true))

  (go
    (while @alive?
      (<! (async/timeout 100))
      (>! events [:poop (long (rand 100))]))))

(defn kill [] (reset! alive? false))
