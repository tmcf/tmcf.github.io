(ns lcluster-app.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [lcluster-app.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[lcluster-app started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[lcluster-app has shut down successfully]=-"))
   :middleware wrap-dev})
