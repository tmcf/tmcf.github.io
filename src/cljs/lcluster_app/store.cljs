(ns licensebroker.store
  (:require
    [reagent.session :as rsession]
    )
  )



(defn +auth-header
  "Add Bearer authorization header based on current user id_token"
  ([]
   (+auth-header {}))
  ([headers]
   (merge headers { :Authorization (str "Bearer " (rsession/get-in [:user :id_token]))})))



(defn put-auth0-user-authenticated!
  "Set :user state based on auth0 authentication"
  [id_token auth0-js-profile]

  (let [user { :authenticated true
              :id_token id_token
              :profile (js->clj auth0-js-profile)
              }]
    (rsession/put! :user user)
  ))

(defn clear-auth0-user!
  []
  (rsession/put! :user {:authenticated false}))


(defn auth0-401-check
  "Add check for 401 status failure handler"
  [jq-ajax-req]
  (.fail jq-ajax-req
         (fn [jqXHR _textStatus _errorThrown]
           (condp = (aget jqXHR "status")
             403 (js/alert (str "Unauthorized\n" (aget jqXHR "responseText")))
             500 (js/alert "Internal Service Error\nContact Leximancer Support")
             401 (clear-auth0-user!)
             (js/alert (str "Unknown error: \n" (aget jqXHR "status")))
             )
           nil)
         ;;(when (= 401 (aget jqXHR "status"))
         ;;  (clear-auth0-user!))))
         jq-ajax-req))

