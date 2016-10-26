(ns lxm.brouting
  (:require [reagent.session :as session]
            [bidi.bidi :as bidi :refer [tag]]
           [pushy.core :as pushy])
  )



;; Routing setup using HTML 5 history via Bidi router and pushy history library
;;
(defn set-page!
  "Set the current page route by the :handler key in match"
  [match]
  (session/assoc-in! [:page] (:handler match)))

(defn match-url-fn
  [routes]
  "Find the bidi route match for the specified URL.
  If the URL does not start with a leading /, one is inserted for route matching
  purposes as bidi is / based where as pushy is not."
  (fn
  [url]
  ;; bidi matches routes from the root of "/"
  ;; pushy (history) only takes relative paths
  ;; sometimes with page reloads an extra leading slash appears
  ;; This explanation may be incorrect but guard against it until
  ;; understood property
  (let [url (if (= (.charAt url 0) \/)
              url
              (str "/" url))]
    (bidi/match-route routes url))))


;; Configure pushy
;;(def history
;;  (pushy/pushy set-page! match-url))

;; Start pushy
;;(pushy/start! history)

(defn start-routing-history
  [routes]
  (let [phistory (pushy/pushy set-page! (match-url-fn routes))]
    (pushy/start! phistory)))


(defn active-page
  []
  (session/get-in [:page]))



