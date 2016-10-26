(ns lxm.rbootstrap
  (:require [reagent.core :as reagent]
            [cljsjs.react-bootstrap]))



(def Col (reagent/adapt-react-class js/ReactBootstrap.Col))



(def Navbar (reagent/adapt-react-class (aget js/ReactBootstrap "Navbar")))
(def Nav (reagent/adapt-react-class (aget js/ReactBootstrap "Nav")))
(def NavItem (reagent/adapt-react-class (aget js/ReactBootstrap "NavItem")))
(def NavbarBrand (reagent/adapt-react-class (aget js/ReactBootstrap "Navbar" "Brand")))
(def NavbarToggle (reagent/adapt-react-class (aget js/ReactBootstrap "Navbar" "Toggle")))
(def NavbarHeader (reagent/adapt-react-class (aget js/ReactBootstrap "Navbar" "Header")))

(def DropdownButton (reagent/adapt-react-class (aget js/ReactBootstrap "DropdownButton")))

(def MenuItem (reagent/adapt-react-class (aget js/ReactBootstrap "MenuItem")))
(def Button (reagent/adapt-react-class (aget js/ReactBootstrap "Button")))


(def Form (reagent/adapt-react-class (aget js/ReactBootstrap "Form")))
(def FormControl (reagent/adapt-react-class (aget js/ReactBootstrap "FormControl")))
(def FormControl-Feedback (reagent/adapt-react-class (aget js/ReactBootstrap "FormControl" "Feedback")))
(def FormGroup (reagent/adapt-react-class (aget js/ReactBootstrap "FormGroup")))
(def ControlLabel (reagent/adapt-react-class (aget js/ReactBootstrap "ControlLabel")))
(def HelpBlock (reagent/adapt-react-class (aget js/ReactBootstrap "HelpBlock")))

(def Checkbox (reagent/adapt-react-class (aget js/ReactBootstrap "Checkbox")))

(def Panel (reagent/adapt-react-class (aget js/ReactBootstrap "Panel")))
(def Well (reagent/adapt-react-class (aget js/ReactBootstrap "Well")))




(defn vsv
  "Determine validation state of form control based on meta data. Check [:validation dom-id] for :success (or else error
  for any other value). If there is no validation state entry nil is returned.
  Fields with :success validation state have a green halo, :error have a red."
  [id {:keys [fmeta vs]}]
  (let [v (if-let [v (get-in fmeta [:validation (name id)])]
            (if (= v :success) :success :error)
            vs)]
    (when v (name v))))

(defn field-onchange
  "Create form specific update / onchange function that handles text and checkboxes"
  [id fmeta]
  (let [fupdate (:update fmeta)]
    (fn [e]
      (let [target (aget e "target")
            ttype (aget target "type")]
        (if (= ttype "checkbox")
          (fupdate id (aget target "checked"))
          (fupdate id (aget target "value")))))))


(defn HField
  "Horizontal Field React Bootstrap FromGroup / FormControl"
  [type label id placeholder & [{:keys [fmeta v h vs fc] :as opts}]]
  (let [fupdate (:update fmeta)
        fup (field-onchange id fmeta)]
    [FormGroup {:onChange fup :controlId (name id) :validationState (vsv id opts)}
     [Col {:componentClass "label" :sm 2 :md 1 :lg 1} label]
     [Col {:sm 5}
      (if fc
        fc
        [FormControl {:type (name type) :value v :onChange fupdate :placeholder placeholder}])]
     [Col {:sm 4 :md 4 :lg 4} (when h [HelpBlock h])]]))

(defn HFieldCC
  "Horizontal Field React Boostrap with ComponentClass for FormControl"
  [cclass label id placeholder & [{:keys [fmeta v h vs fc] :as opts}]]
  (let [fup (field-onchange id fmeta)]
    [FormGroup {:onChange fup :controlId (name id) :validationState (vsv id opts)}
     [Col {:componentClass "label" :sm 2 :md 1 :lg 1} label]
     [Col {:sm 5}
      (if fc
        fc
        [FormControl {:componentClass (name cclass) :value v :placeholder placeholder}])]
     [Col {:sm 4 :md 4 :lg 4} (when h [HelpBlock h])]]))


(defn HSelect
  "Horizontal React Boostrap Select FormControl"
  [label id children & [{:keys [fmeta cc v h vs fc] :as opts} ]]
  [FormGroup {:controlId (name id) :validationState (vsv id opts)}
   [Col {:componentClass "label" :sm 2 :md 1 :lg 1} label]
   [Col {:sm 5}
    (let [fc (if fc
               fc
               [FormControl {:componentClass "select" :value (or (when v (name v)) js/undefined) }])]
      (if children
        (let [ z (into [](concat fc children))] z)
        (do fc)))]
   [Col {:sm 4 :md 4 :lg 4} (when h [HelpBlock h])]])


