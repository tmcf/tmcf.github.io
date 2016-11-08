(defproject lcluster-app "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[metosin/compojure-api "1.1.9"]
                 
                 [cljs-ajax "0.5.8"]
                 [secretary "1.2.3"]
                 [reagent-utils "0.2.0"]
                 [reagent "0.6.0"]
                 [org.clojure/clojurescript "1.9.293" :scope "provided"]
                 ;;[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [selmer "1.10.0"]
                 [markdown-clj "0.9.90"]
                 [ring-middleware-format "0.7.0"]
                 [metosin/ring-http-response "0.8.0"]
                 [bouncer "1.0.0"]
                 [org.webjars/bootstrap "3.3.7"]
                 [org.webjars/font-awesome "4.6.3"]
                 [org.webjars.bower/tether "1.3.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [compojure "1.5.1"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-defaults "0.2.1"]
                 [mount "0.1.10"]
                 [cprop "0.1.9"]
                 [org.clojure/tools.cli "0.3.5"]
                 [luminus-nrepl "0.1.4"]
                 [buddy "1.1.0"]
                 [cider/cider-nrepl "0.14.0"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [luminus-immutant "0.2.2"]
                 
                 [net.mikera/core.matrix "0.56.0"]
                 [net.mikera/vectorz-clj "0.45.0"]
                 [clojure-csv/clojure-csv "2.0.2"]
                 [rm-hull/clustering "0.1.3"]

                 [lacij/lacij "0.10.0"]

                                        ; to allow nightlight plugin to worOAk
                 ;;[org.flatland/useful "0.9.3"]

                [com.leximancer/leximancer-util "V4.50.19-SNAPSHOT"
                 :exclusions [org.antlr/antlr-runtime commons-io com.google.guava/guava org.codehaus.woodstox/stax2-api]]

                 ;; cljs
                 [prismatic/dommy "1.1.0"]
                 [bidi "2.0.13"]
                 [kibu/pushy "0.3.6"]
                 [cljsjs/react-bootstrap "0.30.2-0"]
                 [cljsjs/auth0-lock "10.4.0-0"]
                 [cljsjs/moment "2.10.6-4"]
                 [cljsjs/jquery "2.2.4-0"]

                 [cljsjs/d3 "4.2.2-0"]

                 [cljsjs/react-dropzone "3.7.0-0"]

                 ]

  :repositories {"maven-local" {:url "file:///.m2/repository"  :checksum :warn}}

  :min-lein-version "2.0.0"

  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main lcluster-app.core

  :plugins [[lein-cprop "1.0.1"]
            [lein-cljsbuild "1.1.4"]
            [lein-immutant "2.1.0"]
            [lein-kibit "0.1.2"]]
  :clean-targets ^{:protect false}
  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware
   [cemerick.piggieback/wrap-cljs-repl cider.nrepl/cider-middleware]}
  

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-to "target/cljsbuild/public/js/app.js"
                 :externs ["react/externs/react.js"]
                 :optimizations :advanced
                 :pretty-print false
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}}}}}
             
             
             :aot :all
             :uberjar-name "lcluster-app.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[prone "1.1.2"]
                                 [ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.5.0"]
                                 [pjstadig/humane-test-output "0.8.1"]
                                 [doo "0.1.7"]
                                 [binaryage/devtools "0.8.2"]
                                 [figwheel-sidecar "0.5.8"]
                                 [com.cemerick/piggieback "0.2.2-SNAPSHOT"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.14.0"]
                                 [lein-doo "0.1.7"]
                                 [lein-figwheel "0.5.8"]
                                 [org.clojure/clojurescript "1.9.229"]]
                  :cljsbuild
                  {:builds
                   {:app
                    {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                     :compiler
                     {:main "lcluster-app.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true}}}}
                  
                  
                  
                  :doo {:build "test"}
                  :source-paths ["env/dev/clj" "test/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:resource-paths ["env/dev/resources" "env/test/resources"]
                  :cljsbuild
                  {:builds
                   {:test
                    {:source-paths ["src/cljc" "src/cljs" "test/cljs"]
                     :compiler
                     {:output-to "target/test.js"
                      :main "lcluster-app.doo-runner"
                      :optimizations :whitespace
                      :pretty-print true}}}}
                  
                  }
   :profiles/dev {}
   :profiles/test {}})
