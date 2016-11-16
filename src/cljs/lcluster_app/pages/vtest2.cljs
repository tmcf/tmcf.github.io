(ns lcluster-app.pages.vtest2
  (:require [lcluster-app.pages.core :refer [rpage]]
            [cljsjs.d3]
            ;;[cljsjs.react-dropzone]
            [reagent.core :as rc]
            [reagent.session :as rsession]))


(def dsets [[{:x 1} {:x 2} {:x 3}]
 [{:x 1} {:x 5} {:x 7} {:x 9}]])

(defonce istate (do
                  (let [istate {:width 600
                                :data-idx 0
                                }]
                    (rsession/put! :vtest2 istate))
                  (rsession/cursor [:vtest2])))


;; reagent render
(defn viz [w h]
  [:div
   {:id "barchart"}
  ;; [:div [js/Dropzone]]
   [:svg
    {:width  w
     :height h}
    [:g.container
     [:g#bars]]]])


(defn vw [s] (:width s))
(defn vh [s] (* 0.8 (vw s)))


(defn d3-bind-data
  "Select the target dom elements for the graph & attach the data (d3 style)"
  [s]
  (let [data (get dsets (:data-idx s))]
    (-> (js/d3.select "#bars")
        (.selectAll "rect")
        (.data (clj->js data)))))

(defn d3-enter-merge
  "With the d3 selection, which is the update set, use .enter to grab the new
   elements and format them.

   This instance does not perform a merge before returning the original d3 update selection.
   "
  [d3selection width height bcount]
  (let [rheight (/ height bcount)
        xscale (-> js/d3
                   .scaleLinear
                   (.domain #js [0 10])
                   (.range #js [0 width]))]
  (-> d3selection
      .enter
      (.append "rect")
      (.attr "fill" "blue")
      (.attr "x" (xscale 0))
      (.attr "y" (fn [_ i]
                   (* i rheight)))
      (.attr "height" (- rheight 1))
      (.attr "width" (fn [d]
                       (xscale (aget d "x")
      ;(.merge d3selection)
                               ))))
  d3selection))


(defn d3-update
  "Update the bar chart elements, returning the d3 update selection"
  [d3selection width height bcount]
  (let [rheight (/ height bcount)
        xscale (-> js/d3
                   .scaleLinear
                   (.domain #js [0 10])
                   (.range #js [0 width]))]
    (-> d3selection
        (.attr "fill" "green")
        (.attr "x" (xscale 0))
        (.attr "y" (fn [_ i]
                      (* i rheight)))
        (.attr "height" (- rheight 1))
        (.attr "width" (fn [d]
                         (xscale (aget d "x")))))))

(defn d3-exit
  "Grab the unmatched elements via .exit and remove them."
  [d3selection]
  (-> d3selection
      .exit
      .remove))


(defn d3-render
  [istate]
  (let [state @istate
        data (get dsets (:data-idx state))
        bcount (count data)
        width (vw state)
        height (vh state)]
    (-> (d3-bind-data state)
        (d3-enter-merge width height bcount)
        (d3-update width height bcount)
        (d3-exit))))


(defn v1 [s]
  (let [w [(vw s)]
        h (vh s)]

    [:div {:style { :color "green" :border "1px solid black"}}
     [:h1 "View2"]
     [viz w h]]))

(defn v1a [istate]
  (let [s @istate
        w [(vw s)]
        h (vh s)]

    [:div {:style { :color "red" :border "1px solid black"}}
     [:h1 "View3"]
     [viz w h]]))


(defn gdset [istate]
  (clj->js (get dsets (:data-idx @istate))))

(defn v2
  [istate]
  (rc/create-class
    {:reagent-render       #(v1a istate)
     :component-did-mount  #(do (js/console.log "did-mount" @istate (gdset istate)) (d3-render istate))
     :component-did-update #(do (js/console.log "did-update" @istate (gdset istate)) (d3-render istate))
     }))



(defmethod rpage :vtest2
  []
  ;;(rpage :testpage)
  (let [s @istate]
  [:div
   [:button
    {:on-click  #(swap! istate update :width (fn [width]
                                               (if (= 800 width) 400 800)))} (str "Width:" (vw @istate))]
   [:button
    {:on-click  #(swap! istate update :data-idx (fn [didx]
                                               (if (= 0 didx) 1 0)))} (str "Data:" (:data-idx @istate))]
  [v2 istate]]))



