(ns lcluster-app.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [lcluster-app.core-test]))

(doo-tests 'lcluster-app.core-test)

