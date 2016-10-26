(ns lcluster-app.pages.core
  (:require
   [reagent.session :as session]
   [lxm.rbootstrap :as rb]))


;; Initial app state
;; includes testcount for use with test render method :testpage at the bottom of this file
;;
(session/reset! {
                 :page :home
                 :testcount 0})

; Multi-method for 'page' rendering based on identity
;; pages are identified by keyword in routing
;;
(defmulti rpage identity)

;; on routing error render no content message
(defmethod rpage nil []
  [:div "This page is not available."])


;; Sample :testpage rpage route render

(defn inc-test-state-count
  "inc test counter on state"
  []
  (session/update! :testcount inc))

;; Multimethod for routing
;;
; Example
(defmethod rpage :testpage
  []
  (let []
    [:div
     "TestPage: About this App"
     [:div
      "What a great app"
      [:div {:style {:margin-top "20px"}}
       [rb/Button {:bsStyle "default" :on-click #(js/alert "WHAT?")} (str "Do something")]]
      [:div.row {:style {:margin-top "100px"}}
       [:div.col-md-1  [rb/Button {:bsStyle "danger" :on-click inc-test-state-count}
                        (str "Test Counter: " (session/get :testcount))]]]]]))



