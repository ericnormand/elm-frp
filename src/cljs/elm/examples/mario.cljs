(ns elm.examples.mario
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [elm.core :as elm]
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [cljs.core.async :refer [put! chan <! >!] :as async]))

(defonce events (elm/event-stream))

(defonce current-frame (atom {:focus false
                              :mario {:x 200
                                      :y 400
                                      :vx 0
                                      :vy 0
                                      :dir "right"}}))

(defn render-scene [app owner]
  (reify
    om/IRender
    (render [this]
      (dom/div
       #js {:style #js {:position "relative"
                        :backgroundColor "#aeeeee"
                        :width "100%"
                        :height "100%"
                        :overflow "hidden"}}
       (dom/div
        #js {:style #js {:position "absolute"
                         :backgroundColor "green"
                         :height "50px"
                         :width "100%"
                         :bottom "0"
                         :left "0"
                         :right "0"}})
       (when-let [mario (:mario app)]
         (let [verb (cond
                     (not (zero? (:y mario)))
                     "jump"
                     (not (zero? (:vx mario)))
                     "walk"
                     :else
                     "stand")
               image (str "http://www.lispcast.com/img/mario/"
                          verb
                          "-"
                          (:dir mario)
                          ".gif")]
           (dom/img
            #js {:src image
                 :style #js {:position "absolute"
                             :bottom (str (+ 45 (:y mario)) "px")
                             :left (str (:x mario) "px")
                             :width "35px"
                             :height "35px"}})))
       (when-not (:focus app)
         (dom/div
          #js {:style #js {:position "absolute"
                           :text-align "center"
                           :font-family "sans-serif"
                           :font-size "100px"
                           :color "blue"
                           :line-height "100px"
                           :width "400px"
                           :height "100px"
                           :top "0"
                           :left "0"
                           :right "0"
                           :bottom "0"
                           :margin "auto"}}
          "CLICK"))))))

;; Our input nodes
(defonce focusi      (elm/input events :focus      false))
(defonce jumpi       (elm/input events :jump       0))
(defonce movementi   (elm/input events :movement   0))
(defonce timei       (elm/pulse events :time       0))
(defonce dimensionsi (elm/input events :dimensions {:h 1 :w 1}))

(defn jump
  "Set y velocity to +5 if mario is touching ground and jump is pressed."
  [mario j]
  (if (and (pos? j) (zero? (:y mario)))
    (assoc mario :vy 5)
    mario))

(defn gravity
  "Accelerate down if mario is in the air"
  [mario dt]
  (if (pos? (:y mario))
    (update-in mario [:vy] - (/ dt 4))
    mario))

(defn physics
  "Move based on velocity. Also, don't let y go negative."
  [mario dt]
  (-> mario
      (update-in [:x] + (* dt (:vx mario)))
      (update-in [:y] + (* dt (:vy mario)))
      (update-in [:y] max 0)))

(defn walk
  "Set x velocity based on direction. Also set facing direction."
  [mario dir]
  (assoc mario
    :vx dir
    :dir (cond
          (neg? dir)
          "left"
          (pos? dir)
          "right"
          :else
          (:dir mario))))

(defn toroid [mario {w :w}]
  (cond
   (< (:x mario) -35)
   (assoc mario :x w)
   (> (:x mario) w)
   (assoc mario :x -35)
   :else
   mario))

(defn step [world f dim t j dir]
  (if f
    (-> world
        (assoc :focus true)
        (update-in [:mario] jump    j)
        (update-in [:mario] gravity t)
        (update-in [:mario] walk    dir)
        (update-in [:mario] physics t)
        (update-in [:mario] toroid  dim))
    (assoc world :focus false)))

(defn step* [& args]
  (apply step args))

(defonce main
  (elm/foldp step @current-frame focusi dimensionsi timei jumpi movementi))

(defn now [] (.getTime (js/Date.)))

(let [t1 (atom (now))]
  (js/setInterval
   #(let [t2 (now)]
      (put! events [:time (/ (- t2 @t1) 20)])
      (reset! t1 t2))
   16.667))

(let [[m main] (elm/port main)]
  (go
    (while true
      (reset! current-frame (:value (<! main))))))

(defonce mario-element (js/document.getElementById "mario"))

(defn set-dimensions []
  (put! events [:dimensions {:h (.-clientHeight mario-element)
                             :w (.-clientWidth  mario-element)}]))

(defonce --events
  (do
    (set! js/window.onresize set-dimensions)

    (.addEventListener
     mario-element
     "keydown"
     (fn [e]
       (let [e (or js/window.event e)
             key (or (.-keyCode e) (.-charCode e))]
         (.preventDefault e)
         (cond
          (= 38 key)
          (put! events [:jump 1])
          (= 37 key)
          (put! events [:movement -1])
          (= 39 key)
          (put! events [:movement 1]))))
     false)

    (.addEventListener
     mario-element
     "keyup"
     (fn [e]
       (let [e (or js/window.event e)
             key (or (.-keyCode e) (.-charCode e))]
         (.preventDefault e)
         (cond
          (= 38 key)
          (put! events [:jump 0])
          (= 37 key)
          (put! events [:movement 0])
          (= 39 key)
          (put! events [:movement 0]))))
     false)

    (.addEventListener
     mario-element
     "focus"
     #(put! events [:focus true])
     false)

    (.addEventListener
     mario-element
     "blur"
     #(put! events [:focus false])
     false)))

(set-dimensions)

(om/root
 render-scene
 current-frame
 {:target mario-element})
    
