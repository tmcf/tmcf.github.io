(ns user
  (:require [mount.core :as mount]
            [lcluster-app.figwheel :refer [start-fw stop-fw cljs]]
            lcluster-app.core))

(defn start []
  (mount/start-without #'lcluster-app.core/repl-server))

(defn stop []
  (mount/stop-except #'lcluster-app.core/repl-server))

(defn restart []
  (stop)
  (start))


