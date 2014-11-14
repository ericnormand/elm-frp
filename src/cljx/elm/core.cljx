(ns elm.core
  #+cljs (:require-macros
          [cljs.core.async.macros :refer [go go-loop]])
  (:require
   #+cljs [cljs.core.async :as async
           :refer [>! <! alts! chan put!]]
   #+clj  [clojure.core.async :as async
           :refer [>! <! alts! chan put! go go-loop]]))

(deftype MultiRead [in out]
  #+cljs cljs.core.async.impl.protocols/WritePort
  #+clj clojure.core.async.impl.protocols.WritePort
  (put! [_ v f]
    (#+cljs cljs.core.async.impl.protocols/put!
     #+clj clojure.core.async.impl.protocols/put!

     in v f))

  #+cljs cljs.core.async/Mult
  #+clj  clojure.core.async.Mult
  (tap* [_ ch close?]
    (async/tap* out ch close?))
  (untap* [_ ch]
    (async/untap* out ch))
  (untap-all* [_]
    (async/untap-all* out)))

(defn event-stream []
  (let [in (chan 100)]
    (MultiRead. in (async/mult in))))

(defn <tap100
  "Create a new channel (buffer 100) that taps the
  argument (multi-channel)."
  [mc]
  (let [c (chan 100)]
    (async/tap mc c)
    c))

(defn port
  "Tap a signal. Returns the default value of signal and a channel
  containing output."
  [[v mc]]
  [v (<tap100 mc)])

(defn change [value]
  {:type :change
   :value value})

(defn no-change [value]
  {:type :no-change
   :value value})

(defn change? [x]
  (= :change (:type x)))

(defn input
  "Create an input node.

   events: an events mult
   id: unique idenfier to listen for messages on
   default: the default value

   returns: a signal"
  [events id default]
  (let [<msgs (<tap100 events)
        >out (chan 100)]
    (go-loop [current (no-change default)]
      (let [[i msg] (<! <msgs)]
        (if (= id i)
          (do
            (>! >out (change msg))
            (recur (no-change msg)))
          (do
            (>! >out current)
            (recur current)))))
    [default (async/mult >out)]))

(defn pulse
  "Create an input node. It returns to default after the signal is
  emitted."
  [events id default]
  (let [<msgs (<tap100 events)
        >out (chan 100)]
    (go-loop [current (no-change default)]
      (let [[i msg] (<! <msgs)]
        (if (= id i)
          (do
            (>! >out (change msg))
            (recur current))
          (do
            (>! >out current)
            (recur current)))))
    [default (async/mult >out)]))

(defn value
  "Create a constant value node, which is an input node that always
  returns the default value."
  [events value]
  (let [<msgs (<tap100 events)
        >out (chan 100)
        current (no-change value)]
    (go-loop []
      (<! <msgs)
      (>! >out current)
      (recur))
    [value (async/mult >out)]))

(defn lift
  "Create a lift node, which calls and maps a function on the values of signals.

  f: the function
  & signals: the signals of arguments to pass to f (in
  order)

  returns: a signal"
  [f & signals]
  (assert (seq signals))
  (let [signals* (map port signals)
        values   (map first  signals*)
        channels (map second signals*)
        <msgs (async/map vector channels)
        >out (chan 100)
        default (apply f values)]
    (go-loop [current (no-change default)]
      (let [msgs (<! <msgs)]
        (if (some change? msgs)
          (let [v (apply f (map :value msgs))]
            (>! >out (change v))
            (recur (no-change v)))
          (do
            (>! >out current)
            (recur current)))))
    [default (async/mult >out)]))

(defn foldp
  "Create a fold node, which folds f over a default value and
  signals.

  f: fold function
  default: default value
  signals: input signals

  returns: a signal"
  [f default & signals]
  (assert (seq signals))
  (let [signals* (map port signals)
        channels (map second signals*)
        <msgs (async/map vector channels)
        >out (chan 100)]
    (go-loop [current (no-change default)]
      (let [msgs (<! <msgs)]
        (if (some change? msgs)
          (let [v (apply f (:value current) (map :value msgs))]
            (>! >out (change v))
            (recur (no-change v)))
          (do
            (>! >out current)
            (recur current)))))
    [default (async/mult >out)]))

(defn async
  "Creates an async node from a signal. These are used mainly to decouple two subgraphs in time.

  in: events input events: signal: signal

  returns: a new signal"
  [events signal]
  (let [[default <msgs] (port signal)
        id (gensym "async_node")]
    (go-loop []
      (let [mi (<! <msgs)]
        (when (change? mi)
          (>! events [id (:value mi)])))
      (recur))
    (input events id default)))
