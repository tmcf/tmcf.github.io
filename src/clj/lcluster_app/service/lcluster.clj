(ns lcluster-app.service.lcluster
  (:require [mount.core :refer [defstate]]
            [lxm-engine.cluster.linked-communities :as lc]))


(defstate cstate :start (atom {:pfile-name nil}))




; file {:filename :content-type :size :tempfile (java.io.File)
(defn
  set-prominence-data
  [req-file]
  (let[fname (:filename req-file)]
    (swap! cstate assoc :filename fname :tempfile (:tempfile req-file))
    (println "cluster state" @cstate)
    (let [tfile (:tempfile req-file)
          _ (println "Process " (:filename req-file))
          cresults (lc/cluster tfile)
          max-partition (:max-partition cresults)
          max-details (lc/partition-details cresults max-partition)
          xr (assoc (dissoc max-partition :clusters) :filename fname)]
      ;(.delete tfile)
      (println "generate svgs....")
      (lc/lacij-svg-partition cresults max-details (format "resources/public/img/%s" fname))
      xr)))







