(ns lxm-engine.cluster.linked-communities
  (:require
    [clojure-csv.core :as csvio]
    [clojure.core.matrix :as cm]
    [clojure.core.matrix.operators :as mop]
    [clojure.core.matrix.dataset :as dm]
    [clojure.core.matrix.linear :as cml]
    [clustering.core.hierarchical :as chier]
    [clustering.data-viz.image :as cviz]
    [clustering.average.simple :as cavg]
    [clustering.distance.euclidean :as cdist-euc]
    [clustering.distance.common :as cdist]
    [clustering.data-viz.dendrogram :as cvizd]
    [clojure.math.combinatorics :as combo] )
  (:import (com.leximancer.fs LexRFiles LexRFileSystems ILexRPath))
  )


(defn- parse-prominence-csv-headers-and-rows
  "Parse a prominence CSV from a incsv convertible to a Reader,
  returning map with :headers :rows. rows are lazy, headers are not.
  First col header in raw prominence csv file is empty. This is taken into account"
  [incsv]
  (let [csvreader (clojure.java.io/reader incsv)
        csv (csvio/parse-csv csvreader)]
    {:headers (into [] (rest (first csv)))
     :rows (rest csv)}))


(defn- ->threshd
  "Convert a numeric string to a double, if below threshold return 0.0"
  ^double
  [dstr ^double threshold]
  (let [dv (Double/parseDouble dstr)]
    (if (< dv threshold)
      0.0
      dv)))

(defn- prom-csv-row-to-pvector
  "Convert a single prominence raw csv string row to vector of prominence doubles"
  [csvrow threshold]
  ; skip first column in csv row (concept), threshold and convert prom
  ; values to doubles
  (let [threshold-row-doubles (map #(->threshd % threshold) (rest csvrow))]
    (cm/array threshold-row-doubles)))


(defn- prominence-csv-rows->vectors
  "Convert prominence csv string rows to core/matrix double vectors applying threshold"
  [prows ^double threshold]
  (reduce #(conj %1 (prom-csv-row-to-pvector %2 threshold)) [] prows))


(defn parse-prominence-csv
  "Parse a prominence csv into vector representation. Values below threshold are set to 0.0.
  Takes incsv arg that is convertible to a Reader.
  Returns "
  ( [incsv] (parse-prominence-csv incsv 3.0))
  ( [incsv ^double threshold]
  (let [raw-csv (parse-prominence-csv-headers-and-rows incsv)
        prom-vectors (prominence-csv-rows->vectors (:rows raw-csv) threshold)]
    {:concepts (:headers raw-csv) :prom-vectors prom-vectors}
    )))




