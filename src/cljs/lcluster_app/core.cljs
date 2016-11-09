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
            [lcluster-app.pages.vtest2]
            [lcluster-app.pages.cshow1]
            )
  (:import goog.History))

(defn nav-link [uri title page]
  (let [active (= page (session/get :page))
        title (if active [:bold {:style {:color "black"}} title]
                         title)]
    [:li.nav-item
     {:class (when active "active")}
     [:a.nav-link
      {:href uri
       :on-click nil} title]]))


(defn navbar []
  (let []
    (fn []
      [:nav.navbar.navbar-light.bg-faded
       [:div.collapse.navbar-toggleable-xs
        {:class "in"}
        [:a.navbar-brand {:href "#/"} "Leximancer:"]
        [:ul.nav.navbar-nav
         [nav-link "home" "Home" :home]
         [nav-link "cshow1" "Cluster Viz #1" :cshow1]
         [nav-link "vtest1" "Visual Test1" :vtest1]
         [nav-link "vtest2" "Cluster Test2" :vtest2]
         [nav-link "about" "About" :about]]]])))

(defmethod rpage :about []
  [:div.container
   [:div.row
    [:div
     [:img {:src "img/clustertest-about.png"}]
     ]]])

(defmethod rpage  :home []
  [:div.container
   [:div.jumbotron
    [:h2 "LexSurveys Clustering Tests 0.3"]
    ]
   (when-let [docs (session/get :docs)]
     [:div.row
      [:div.col-md-12
       [:div {:dangerouslySetInnerHTML
              {:__html (md->html docs)}}]]])])


;; Application UI routes
(def routes ["/" { "home" :home
                  "vtest1" :vtest1
                  "vtest2" :vtest2
                  "cshow1" :cshow1
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
