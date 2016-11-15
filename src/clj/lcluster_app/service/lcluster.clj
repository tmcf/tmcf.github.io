(ns lcluster-app.service.lcluster
  (:require [mount.core :refer [defstate]]
            [lxm-engine.cluster.linked-communities :as lc]))


(defstate cstate :start (atom {:pfile-name nil}))




; file {:filename :content-type :size :tempfile (java.io.File)
(defn
  set-prominence-data
  [req-file popts]
  (let[fname (:filename req-file)]
    (swap! cstate assoc :filename fname :tempfile (:tempfile req-file) :opts popts)
    (println "cluster state" @cstate)
    (let [tfile (:tempfile req-file)
          _ (println "Process " (:filename req-file) popts)
          cresults (lc/cluster tfile popts)
          max-partition (:max-partition cresults)
          max-details (lc/partition-details cresults max-partition)
          xr (assoc (dissoc max-partition :clusters) :filename fname)
          playout (lc/lacij-svg-partition
                    cresults max-details (format "resources/public/img/%s" fname) {:graph-opts {:width 400 :height 400}})
          ]
      ;(.delete tfile)
      (println "generate svgs....")
      (assoc xr :cluster-layouts (map lc/clean-lacij-layout playout)))))










