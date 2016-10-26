(ns lcluster-app.core
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [goog.events :as events]
            [markdown.core :refer [md->html]]
            [lcluster-app.ajax :refer [load-interceptors!]]
            [ajax.core :refer [GET POST]]
            [lcluster-app.pages.core :refer [rpage]]
            [lxm.brouting :as broute]

            [lcluster-app.pages.vtest1]
            )
  (:import goog.History))

(defn nav-link [uri title page collapsed?]
  [:li.nav-item
   {:class (when (= page (session/get :page)) "active")}
   [:a.nav-link
    {:href uri
     :on-click nil} title]])

(defn navbar []
  (let [collapsed? false]
    (fn []
      [:nav.navbar.navbar-light.bg-faded
      ; [:button.navbar-toggler.hidden-sm-up
      ;  {:on-click #(swap! collapsed? not)} "☰"]
       [:div.collapse.navbar-toggleable-xs
        (when-not collapsed? {:class "in"})
        [:a.navbar-brand {:href "#/"} "LexSurveys Cluster Tests:"]
        [:ul.nav.navbar-nav
         [nav-link "home" "Home2" :home collapsed?]
         [nav-link "vtest1" "Visual Test1" :vtest1 collapsed?]
         [nav-link "about" "About" :about collapsed?]]]])))

(defmethod rpage :about []
  [:div.container
   [:div.row
    [:div.col-md-12
     "Z1 lcluster-app... work in progress"]]])

(defmethod rpage  :home []
  [:div.container
   [:div.jumbotron
    [:h1 "Clustering Tests"]
    ]
   (when-let [docs (session/get :docs)]
     [:div.row
      [:div.col-md-12
       [:div {:dangerouslySetInnerHTML
              {:__html (md->html docs)}}]]])])


;; Application UI routes
(def routes ["/" { "home" :home
                    "vtest1" :vtest1
                    "about" :about}])


(defn app-page
  []
  (fn []
    [:div.container-fluid
     [:div.row
      [:div.col-md-12
         (rpage (broute/active-page))]]]))


;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET (str js/context "/docs") {:handler #(session/put! :docs %)}))

(defn mount-components []
  (r/render [#'navbar] (.getElementById js/document "navbar"))
  (r/render [#'app-page] (.getElementById js/document "app")))

(defn init! []
  (load-interceptors!)
  (fetch-docs!)
  ;(hook-browser-navigation!)
  (broute/start-routing-history routes)
  (mount-components))
