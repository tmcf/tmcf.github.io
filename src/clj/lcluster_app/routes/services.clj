(ns lcluster-app.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.upload :as upload]
            [compojure.api.meta :refer [restructure-param]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]

            [lcluster-app.service.lcluster :as scluster]
            ))

(defn access-error [_ _]
  (unauthorized {:error "unauthorized"}))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler  rule
                     :on-error access-error}))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-restricted rule]))

(defmethod restructure-param :current-user
  [_ binding acc]
  (update-in acc [:letks] into [binding `(:identity ~'+compojure-api-request+)]))

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "Sample API"
                           :description "Sample Services"}}}}
  
  (GET "/authenticated" []
       :auth-rules authenticated?
       :current-user user
       (ok {:user user}))
  (context "/api/v1/ctest" []
    :tags ["cluster test operations"]

    (PUT "/data-set" []
      :multipart-params [file :- upload/TempFileUpload]
      :query-params [savg :- Boolean]
      :middleware [upload/wrap-multipart-params]
      (fn [r]
        ; file {:filename :content-type :size :tempfile (java.io.File)
        (println "SAVG:" savg)
        (println "file in:" file)
        (ok (scluster/set-prominence-data file {:self-avg savg}))))

    (GET "/plus" []
      :return       Long
      :query-params [x :- Long, {y :- Long 1}]
      :summary      "x+y with query-parameters. y defaults to 1."
      (ok (+ x y)))

    (POST "/minus" []
      :return      Long
      :body-params [x :- Long, y :- Long]
      :summary     "x-y with body-parameters."
      (ok (- x y)))

    (GET "/times/:x/:y" []
      :return      Long
      :path-params [x :- Long, y :- Long]
      :summary     "x*y with path-parameters"
      (ok (* x y)))

    (POST "/divide" []
      :return      Double
      :form-params [x :- Long, y :- Long]
      :summary     "x/y with form-parameters"
      (ok (/ x y)))

    (GET "/power" []
      :return      Long
      :header-params [x :- Long, y :- Long]
      :summary     "x^y with header-parameters"
      (ok (long (Math/pow x y))))))
