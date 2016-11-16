(ns ^:figwheel-no-load lcluster-app.app
  (:require [lcluster-app.core :as core]
            [devtools.core :as devtools]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
                                        ;:websocket-url "ws://localhost:3449/figwheel-ws"
 :websocket-url "ws://tmcf4.local:3449/figwheel-ws"

                                        ;websocket-host :js-client-host
 :on-jsload core/mount-components)

(devtools/install!)

(core/init!)
