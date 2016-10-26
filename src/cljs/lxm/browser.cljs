(ns lxm.browser
  (:require
    [dommy.core :as dommy :refer-macros [sel sel1]]
    )
  )




(defn kw->id
  "Convert Keyword representing dom element id to dommy id Keyword :alpha :#alpha"
  [kw]
  (keyword (str  "#" (name kw))))

(defn form-value
  "For a id keyword such as :address or :checkbox get the 'state'"
  [form-id kw]
  (let [f (sel1 [(kw->id form-id) (kw->id kw)])]
    (when f
      (if (= "checkbox" (aget f "type"))
        (aget f "checked")
        (or (dommy/value f) (dommy/text f))))))


(defn form-fields->json
  "Map all non-nil or empty string form values to json form-id-keys, sequence of keyword form control ids"
  [form-id form-id-keys]
  (into {} (remove (fn [[k v]] (or (nil? v) (= "" v))) (map (fn [k] [k (form-value form-id k)]) form-id-keys))))


(defn json->form-validation
  "Set form validation state for the form field keys to specified state
  state can be nil, :success, :error, :warning. Default :error "
  ([form-json vstate]
   (into {} (map (fn [k] [(name k) vstate]) (keys form-json)))))

(defn pschema-errors->form-validation
  "Set form validation state for the form field keys in a prismatic schema/
   swagger response to specified state :success, :error, :warning.
   Default :error"
  ([pschema-errors vstate]
   (into {} (map (fn [k] [(name k) vstate]) (keys pschema-errors)))))

