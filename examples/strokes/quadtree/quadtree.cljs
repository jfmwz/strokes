(ns strokes.examples.quadtree
  (:require [clojure.string :refer [join]]
            [strokes :refer [d3]]))

(strokes/bootstrap)

(def width 960)
(def height 500)

; create root svg element
(defn gen-svg []
  (-> d3 (.select "body") (.append "svg")
    (.attr "width" width)
    (.attr "height" height)))

; generate some random data for the quadtree
(defn gen-data []
  (for [x (range 2500)]
    {:x (rand width), :y (rand height)}))

; gets a seq of all nodes. (the quadtree.visit api seems to beg for this side effecty impl)
(defn nodes [quadtree]
  (let [sidecar (transient [])]
    (.visit quadtree (fn [n, x1, y1, x2, y2]
      (conj! sidecar {:x x1, :y y1, :width (- x2 x1), :height (- y2 y1)})
      ; return false to keep processing...
      false))
    ; return the accumulated side effects
    (persistent! sidecar)))

; sadly, the quadtree and point must be global for the brush callback to work
(def quadtree (atom nil))
(def point (atom nil))

; forward declaration of brush
(def brush)

; determine intersection of brush and nodes, color using css classes
(defn brushed []
  (let [extent (.extent brush)
        scanned (transient #{})
        selected (transient #{})
        x0 (nth (nth extent 0) 0)
        y0 (nth (nth extent 0) 1)
        x3 (nth (nth extent 1) 0)
        y3 (nth (nth extent 1) 1)]

    ; visit nodes and add nodes to sets
    (.visit @quadtree (fn [n, x1, y1, x2, y2]
      (let [p (.-point n)
            px (if p (.-x p) nil)
            py (if p (.-y p) nil)]
        ; mark as scanned and maybe selected
        (if p (do
          (conj! scanned p)
          (if (and (>= px x0) (< px x3) (>= py y0) (< py y3))
            (conj! selected p))))
        ; return value determines if we keep traversing
        (or (>= x1 x3) (>= y1 y3) (< x2 x0) (< y2 y0)))))

    ; now update the view based on what we learned
    (.classed @point "scanned" #(scanned %))
    (.classed @point "selected" #(selected %))))

; on screen brush widget
(def brush 
  (-> d3 .-svg (.brush)
    (.x (-> d3 .-scale (.identity) (.domain [0, width])))
    (.y (-> d3 .-scale (.identity) (.domain [0, height])))
    (.on "brush" brushed)
    (.extent [[100, 100], [200, 200]])))

;(.log js/console (vert-array))
(let [svg (gen-svg)
      data (vec (gen-data))]

  (reset! quadtree 
    (-> d3 .-geom 
      (.quadtree data -1 -1 (+ width 1) (+ height 1))))

  (-> svg (.selectAll ".node")
      (.data (nodes @quadtree))
    (.enter) (.append "rect")
      (.attr "class" "node")
      (.attr "x" :x)
      (.attr "y" :y)
      (.attr "width" :width)
      (.attr "height" :height))

  (reset! point (-> svg (.selectAll ".point")
      (.data data)
    (.enter) (.append "circle")
      (.attr "class" "point")
      (.attr "cx" :x)
      (.attr "cy" :y)
      (.attr "r" 4)))

  (-> svg (.append "g")
      (.attr "class" "brush")
      (.call brush))

  (brushed))
