(ns lcluster-app.app
  (:require [lcluster-app.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
