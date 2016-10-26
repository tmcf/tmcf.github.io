(ns licensebroker.pages.core
  (:require
   [reagent.session :as session]))


;; Initial app state
(session/reset! {
                 :page :home
                 :count 0})


;; Multimethod for routing

(defmulti rpage identity)
