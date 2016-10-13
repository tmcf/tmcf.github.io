(ns lcluster-app.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[lcluster-app started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[lcluster-app has shut down successfully]=-"))
   :middleware identity})
