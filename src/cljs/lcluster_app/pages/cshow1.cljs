(ns lcluster-app.pages.cshow1
  (:require [lcluster-app.pages.core :refer [rpage]]
            [cljsjs.d3]
            [cljsjs.react-dropzone]
            [reagent.core :as rc]
            [reagent.session :as rsession]
            [cljsjs.jquery]
            [goog.userAgent.product :as product]
            [dommy.core :as dommy :refer-macros [sel sel1]]
            [lxm.browser :refer [kw->id form-value form-fields->json pschema-errors->form-validation json->form-validation]]
            [reagent.core :as r]
            [reagent.session :as session]
            [reagent.interop :as ri]
            [lxm.rbootstrap :as rb]
            ))

(def dsets [[{:x 1} {:x 2} {:x 3}]
 [{:x 1} {:x 5} {:x 7} {:x 9}]])

(defonce istate (do
                  (let [istate {:width 600
                                :data-idx 0
                                }]
                    (rsession/put! :vtest2 istate))
                  (rsession/cursor [:vtest2])))


(defonce cstate (
                  do
                  (let [cstate {:cluster-details {:filename ""
                                                  :cluster-count 0
                                                  :merge -1
                                                  :partition-density -1}
                                :self-avg false
                                :linkage "min"}]
                    (rsession/put! :cshow1 cstate)
                    (rsession/cursor [:cshow1]))
                  ))


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



(defmethod rpage :cshow1-nomore
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

(def jq$ js/$)


(defn promFileSelected
  [e]
    (let [_files (aget e "target" "files")
        f (aget e "target" "files" 0)
        fname (aget f "name")]
    (let [fdata (js/FormData.)
          _ (.append fdata "file" f fname)
          _ (println "state self-avg:" (:self-avg @cstate) (str (:self-avg @cstate)))
          lreq (.ajax jq$
                      (clj->js
                        {
                         :method "PUT"
                         :url (str "/api/v1/ctest/data-set?savg="  (str (boolean (:self-avg @cstate))))
                         :contentType false ; must be false with multipart form data
                         :processData false
                         :data fdata
                         }))]
      (-> lreq
          (.done (fn [data textStatus _jqXHR]
                   (js/console.log "OK", textStatus, data)
                   (let [cdata (js->clj data :keywordize-keys true)]
                     (swap! cstate assoc :cluster-details cdata :cluster-details-json data)
                   )))
          (.fail (fn [jqXHR textStatus errorThrown]
                   (js/console.log textStatus, errorThrown, jqXHR)
                   (js/alert "Upload failed:"))
                   )
          (.always (fn []
                     (.val (jq$ "#csvfileupload") "")
                     ))))))


(defn lacij-svg-maps
  [cluster-count cdetails]
  (fn [cluster-count cdetails]
    (when (> cluster-count 0)
      [:div
       [:div [:bold "Prominence File: " (:filename cdetails)]]
       [:div
        (map (fn [i]
               [:div {:key i :style {:width "50%" :float "left" :border "1px solid rgba(0,0,0,.2)"}}
                [:img {:src (str "img/" (:filename cdetails) "/cluster-" i ".svg")}]])
             (range 0 (:cluster-count cdetails)))
        ]
       [:div {:style {:clear "both"}}]]

    )))


(defn cluster-summary
  [cidx clayout]
  (fn [cidx clayout]
    (let []
  [:div
   [:div "Cluster# " cidx]
   (for [[nid node] (:nodes clayout)]
     ^{:key nid}[:div nid])


    ])))




(defn l2-d3-bind-data
  "Select the target dom elements for the graph & attach the data (d3 style)"
  [pid cidx clayout]
  (println "l2-d3-bind-data....")
  (let [
        ;data (get dsets (:data-idx pidx))
        ;sel (-> (js/d3.select "#bars")
        ;        (.selectAll "rect")
        ;        (.data (clj->js data)))))

        cnodes (js/d3.select (str "#" pid " g.cmap"))]
    cnodes
    (println "l2-d3-bind-data exit.")
    ))

(defn l2-d3-enter-merge
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


(defn l2-d3-update
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

(defn l2-d3-exit
  "Grab the unmatched elements via .exit and remove them."
  [d3selection]
  (-> d3selection
      .exit
      .remove))


(defn l2-d3-render0
  [pidx cidx clayout dom-node]
  (println "l2-d3-render enter...." dom-node)
  (let [state @istate
        data (get dsets (:data-idx state))
        bcount (count data)
        width (vw state)
        height (vh state)]
    (-> (l2-d3-bind-data pidx cidx clayout)
        ;(l2-d3-enter-merge width height bcount)
        ;(l2-d3-update width height bcount)
        ;(l2-d3-exit)
        )
    (println "l2-d3-render exit.")))


(defn svg-attr
  ([sattr a1]
   (str (name sattr) "(" a1 ")"))
  ([sattr a1 a2]
   (str (name sattr) "(" a1 "," a2 ")"))
  ([sattr a1 a2 a3]
   (str (name sattr) "(" a1 "," a2 "," a3 ")"))
  ([sattr a1 a2 a3 a4]
   (str (name sattr) "(" a1 "," a2 "," a3 "," a4 ")")))

(defn cluster-summary-render [pid cidx clayout ref]
  (fn [pid cidx clayout]
    (let [margin-x 50
          margin-y 50]
      [:div.csummary  {:ref ref :data-cid cidx}
       [:div "SVG SummaryRender57: " cidx]
       [:svg
        {:width  (+ (:width clayout) (* 2 margin-x))
         :height (+ (:height clayout) (* 2 margin-y))}
        [:g.container {:data-cid cidx :transform (svg-attr :translate margin-x margin-y)}
         ]]])))


(defn l2-d3-render
  [pidx cidx clayout dom-node]
  (let [xnodes (:nodes clayout)
        gcontainer (-> (js/d3.select dom-node)
                       (.select (str "g.container[data-cid='" cidx "']")))
        cnodes (-> (.selectAll gcontainer "g.node")
                   (.data (js/d3.values (clj->js xnodes))))

        cedges (-> (.selectAll gcontainer "g.edge")
                   (.data (js/d3.values (clj->js (:edges clayout)))))
        nlookup (fn [d akeyword]
                  (get xnodes (keyword (aget d (name akeyword)))))
                  ;(println "nlookup" d (name akeyword))
                  ;(println "by-clojure" (akeyword d))
                  ;(println "by-clojureA" (aget d akeyword))
                  ;(println "by-clojureB" (aget d (name akeyword)))
                  ;(println "xn:" (get xnodes (keyword (aget d (name akeyword)))))
                 ; (println "by-clojure2" (ri/$ d akeyword))
                  ;(println "xnodes" xnodes)
                  ;(get xnodes (aget d akeyword)))
                  ]

    (-> cnodes
        .enter
        (.append "g")
        (.attr "class" "node")
        (.attr "data-nid" #(aget % "id"))
        (.attr "transform" (fn [d] (str "translate(" (aget d "x") "," (aget d "y") ")")))
        (.append "text")
        (.text #(aget % "id")))

    (-> cedges
        .enter
        (.append "g")
        (.attr "class" "edge")
        (.attr "data-eid" #(aget % "id"))
        (.attr "data-src" #(aget % "src"))
        (.attr "data-dst" #(aget % "dst"))
        (.append "line")
        (.attr "x1" #(:x (nlookup % :src)))
        (.attr "y1" #(:y (nlookup % :src)))
        (.attr "x2" #(:x (nlookup % :dst)))
        (.attr "y2" #(:y (nlookup % :dst)))
        (.attr "stroke-width" 4)
        (.attr "stroke" "blue"))))



(defn cluster-summary2
  [pid cidx clayout]
  (let [dom-node (atom nil)
        dom-ref #(reset! dom-node %)]
    (rc/create-class
      {:reagent-render  (fn [pid cidx clayout] [cluster-summary-render pid cidx clayout dom-ref])
       :component-did-mount  #(l2-d3-render pid cidx clayout @dom-node)
       :component-did-update #(l2-d3-render pid cidx clayout @dom-node)
       })))


(defn try-layout-2
  [pid cluster-count cdetails]
  (fn [pid cluster-count cdetails]
    (let [cpanels (map-indexed (fn [cidx clayout]
                                 ^{:key cidx}
                                 [cluster-summary2 pid cidx clayout])
                               (:cluster-layouts cdetails))]
      [:div  {:id pid}
       ;;cpanels]
       (concat [[:div.csummary-header {:key "s0"} "layout2 " pid " " (:merge cdetails) "cluster-count:" (:cluster-count cdetails)]]
               cpanels)]
      )))




(defn try-layout-1
  [cluster-count cdetails]
  (fn [cluster-count cdetails]
  (let [cpanels (map-indexed (fn [cidx clayout]
                               ^{:key cidx}
                               [cluster-summary cidx clayout])
                              (:cluster-layouts cdetails))]
    [:div
     ;;cpanels]
     (concat [[:div {:key "0001"}  "merge99:" (:merge cdetails) "cluster-count:" (:cluster-count cdetails)]]
             cpanels)]
    )))





(defmethod rpage :cshow1
  []
  (let [s @cstate
        cdetails (:cluster-details s)]
    (js/console.log "state:" s "cdetails" cdetails)
    [:div
     [rb/Form {:id :cshow1-form :horizontal true}
      [rb/Checkbox {:onChange #(swap! cstate assoc :self-avg (not (:self-avg s)))} "Use Prominence [A->A] = average-prominence[A-N]"]
       (rb/HSelect "Linkage" :cluster-linkage
                   [[:option {:value :min} "Min (matches Nature Paper)"]
                    [:option {:value :avg} "Average (R default)"]]
                   {:v (or (:linkage5 s) "") :h "Cluster distance" :fmeta nil})

      [:div.btn.btn-primary.btn-file  {:bsStyle :primary}
       "Upload Prominence CSV"
      [rb/FormControl
       {:id  "csvfileupload"
        :type "file"
        :label "File"
        :onChange promFileSelected
        }]]
      (if (> (:cluster-count cdetails) 0)
        [try-layout-2 "p29185" (:cluster-count cdetails) cdetails]

       ;; (list
       ;;[try-layout-1 (:cluster-count cdetails) cdetails]
          ;;[lacij-svg-maps (:cluster-count cdetails) cdetails]
        ;;  )
        [:div "Select prominence file to cluster"]
        )
      ]]))




