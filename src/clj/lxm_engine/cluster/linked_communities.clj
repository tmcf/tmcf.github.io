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
 ;; (:import (com.leximancer.fs LexRFiles LexRFileSystems ILexRPath))
  )


;;;;;;; CSV read / parse write

(defn- parse-prominence-csv-headers-and-rows
  "Parse a prominence CSV from a incsv convertible to a Reader,
  returning map with :headers :rows. rows are lazy, headers are not.
  First col header in raw prominence csv file is empty. This is taken into account"
  [incsv]
  (let [csvreader (clojure.java.io/reader incsv)
        csv (csvio/parse-csv csvreader)
        hrow (first csv)]
    {:header-label (first hrow)
     :headers (into [] (rest hrow))
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


(defn prominence-csv->vset
  "Parse a prominence csv into vector representation. Values below threshold are set to 0.0.
  Takes incsv arg that is convertible to a Reader.
  Returns "
  ( [incsv] (prominence-csv->vset incsv 3.0))
  ( [incsv ^double threshold]
  (let [raw-csv (parse-prominence-csv-headers-and-rows incsv)
        prom-vectors (prominence-csv-rows->vectors (:rows raw-csv) threshold)]
    {:headers (:headers raw-csv) :vectors prom-vectors}
    )))



(defn vset->csv
  "Turn a vector set into a sequence of CSV rows."
  [vset hlabel]
  (let [csv-header (concat [hlabel] (:headers vset))
        ; reach row made from [label-for-row e1 e2 e3 double->str]
        csv-vector-rows (map #(concat [%1] (map str %2)) (:headers vset) (:vectors vset))]
    ;; Add header row and return
    (concat [csv-header] csv-vector-rows)))


(defn write-vset-csv
  "Write a vector set out as csv to *ocsv*, which is coerced into a Writer."
  [vset hlabel ocsv]
  (with-open [fpp (clojure.java.io/writer ocsv :encoding "UTF-8")]
    (let [csv-content (vset->csv vset hlabel)]
  (.write fpp (csvio/write-csv csv-content)))))


;;;;  Ops to support linked clustering
;;;;  Prominence & Similarity matrices are symmetric, this implementation
;;;;  doesn't do any symmetric or sparse optimisation

(defn vec-neighbours
  "Return a sequence of indices for non-zero valued dimensions in this vector.
   The neighboursin community clustering."
  ([v] (vec-neighbours v -1))
  ([v exclude-node]
   (reduce (fn [neighbours [idx val]]
             (if (and (> val 0.0) (not= exclude-node idx))
               (conj neighbours idx)
               neighbours)) [] (map-indexed vector v))))


(defn canonical-edge
  "return row col pair where row is always <= col.
  so A->B will be treated the same as B->A. Matrix is symmetrical"
  [r c]
  (if (> c r)
    [c r]
    [r c]))

(defn edge-label
  "Return the edge label name string for given label set row col"
  ([labels [r c]]
   (edge-label labels r c))
  ([labels r c]
  (format "%s-%s" (labels r) (labels c))))


(defn- vset-inclusive-neighbours-count
  "Sum of the inclusive neighbor (a node and its immediate neighbours)
  count for vset"
  [vset]
  (let [ecount (reduce (fn [total pv]
            (let [nc (count (vec-neighbours pv))]
              (+ total nc)))
          0 (:vectors vset))]
       (/ ecount 2)))

(defn vec-edges
  "Sequence of edge-pairs where weight > 0, excluded self link"
  ([[v vidx]]
   (vec-edges v vidx))
  ([v vidx]
  (map #(canonical-edge vidx %) (vec-neighbours v vidx))))


(defn edge-labels
  "Sequence of edge labels a-b c-b for input of form [1 2] [3 4]
   and label array for nodes"
  [edges labels]
  (into [] (map #(edge-label labels %) edges)))

(defn vset-edges
  "Return distinct edge pairs in vset excluding
  self edges.
  [[3 0] [9 0] ....]"
  [vset]
  ; vec-edges arg order is reversed from what map-indexed produces
  (into [] (distinct (reduce #(concat %1 (vec-edges (get %2 1) (get %2 0)) )
                             [] (map-indexed vector (:vectors vset))))))



(defn tanimoto-coefficient
  ^double [v1 v2]
  (let [dot-v1-v2 (cm/dot v1 v2)
        abs-v1 (cm/dot v1 v1)
        abs-v2 (cm/dot  v2 v2)]
        (/ dot-v1-v2 (- (+ abs-v1 abs-v2) dot-v1-v2))))

(defn tanimoto-distance
  "Return 1 minus tanimoto-coefficient so it is a
  similarity/distance metric. Technically a distance coefficient"
  ^double [v1 v2]
  (- 1.0 (tanimoto-coefficient v1 v2)))

(defn- impost-nodes
  "Similarity is calculated of [i k] [j k] using j,k only (impost nodes),
  so return appropriate nodes or nil if the edges are disjoint."
  [[from-i to-i] [from-j to-j]]
  (cond
    (= from-i from-j) [to-i to-j]
    (= from-i to-j) [to-i from-j]
    (= to-i from-j) [from-i to-j]
    (= to-i to-j)  [from-i from-j]
    :else
    (do ;(println "Unrelated edges:" from-i to-i "/" from-j to-j)
        nil)
    ))


(defn pvec->similarity-vec
  "calc similarity between this row's vector and the vector's of all linked (neighbour) rows"
  [pvectors v1-row similarity-fn]
  (let [v1 (pvectors v1-row)]
    (cm/array (map #(similarity-fn v1 (pvectors %))
                   (vec-neighbours v1 v1-row)))))


(defn similarity-vec0-bad
  "Given an edge, calculate a similarity vector to the entire edge set."
  [[nfrom-i to-i :as ik] pvectors edges similarity-fn]
  (let [v1 (pvectors nfrom-i)]
    (cm/array (map (fn [[nfrom-j to-j :as jk]]
                     (let [v2 (pvectors nfrom-j)
                           _ (println "v1:" nfrom-i v1)
                           _ (println "v2:" to-j v2)
                           s (similarity-fn v1 v2)]
                       (println "similarity:" ik jk s "\n")
                       s)) edges))))

(defn similarity-vec
  "Given an edge, calculate a similarity vector to the entire edge set."
  [[nfrom-i to-i :as ik] pvectors edges similarity-fn]
  (let []
    (cm/array (map (fn [[nfrom-j to-j :as jk]]
                     (let [[i j :as inodes] (impost-nodes ik jk)]
                       (if inodes
                       (let [vi (pvectors i)
                             vj (pvectors j)
                             _ (println "vi:" i vi)
                             _ (println "vj:" j vj)
                             sim (similarity-fn vi vj)]
                         (println "zsimilarity:" ik jk sim "\n")
                         sim)
                       (do
                        ; (println "no keystone / similarity:" ik jk 0.0"\n")
                         0.0)))) edges))))



(defn prom-vset->similarity-vset
  "Convert a prominence matrix (vector set) to
  a linked community similarity matrix (vector set)"
  [vset]
  (let [sfn (memoize tanimoto-coefficient)
        edges (vset-edges vset)
        pvectors (:vectors vset)
        ; for each edge, calculate similarity to all other edges
        ; nieve implementation
        svectors (into [] (reduce
                            (fn [r cedge]
                              (conj r (similarity-vec cedge pvectors edges sfn)))
                            [] edges))]
    {:edges edges :vectors svectors :headers (edge-labels edges (:headers vset))}
  ))

(defn closest-clusters
  [clusters cdist-fn]
  (reduce
    (fn [[low-dist low-cluster-combo_ :as current-low] cluster-combo]
      (let [dist (apply cdist-fn cluster-combo)]
        (if (< dist low-dist)
          [dist cluster-combo]
          current-low)))
    [Double/MAX_VALUE [-1 -1]]
    (combo/combinations clusters 2)))


(defn- cluster-edges-to-nodes
  "Convert edge set into node list"
  [edges]
  (distinct (flatten edges)))

(defn cluster-partition-density
  ^double [cluster-edges]
  (let [nlinks (count cluster-edges)
        nc (count (cluster-edges-to-nodes cluster-edges))
        _ (println "nlinks:" nlinks "nnodes:" nc) ;cluster-edges)
        ;_ (println "nnodes:" nc (cluster-edges-to-nodes cluster-edges))
        ]
    (if (= nc 2)
      0.0
      (let [nc-1 (- nc 1.0)
            cdensity (/ (* nlinks (- nlinks nc-1))
                        (* (- nc 2.0) nc-1))

            cdensity2 (/ (- nlinks nc-1)
                         (- (/ (* nc nc -1) 2) nc-1))
            ]
        (println "cdensity1:" cdensity "cdensity2:" cdensity2)
        cdensity))))

(defn network-partition-density
  [network total-edge-count]
  (let [dsum (reduce #(+ %1 (cluster-partition-density %2)) 0.0 network)
        dnetwork (/ (* 2.0 dsum) total-edge-count)]
    (println "Network Density:" dnetwork)
    dnetwork))


(defn create-default-post-merge-fn
  "Keep track of cluster merge history"
  [simset]
  (let [ecount (count (:edges simset))]
    (fn ([] {:levels 0
             :mclusters []})
      ([mdata new-clusters]
       (let [levels (:levels mdata)
             old-networks (:mclusters mdata)
             new-network (into [] (map :data new-clusters))
             ndensity (network-partition-density new-network ecount)]
         {:levels (inc levels)
          :mclusters (conj old-networks {:density ndensity
                                         :cluster-count (count new-network)
                                         :clusters new-network})})))))

(defn ->single-clusters
  "Convert sequence of items to sequence of vectors each containing the single item.
  Used as starting point for clustering."
  [items]
  (map vector items))



(defn hier-cluster-ex
  [initial-clusters cdist-fn cmerge-fn post-merge-fn]
  (let [post-merge-fn (or post-merge-fn post-merge-fn)
        cdist-fn (memoize (fn [c1 c2]
                           (cdist-fn (:data c1) (:data c2))))
        starting-mclusters (set (map chier/bi-cluster initial-clusters))
        [mclusters mdata] (loop [mclusters starting-mclusters
                                 mdata (post-merge-fn)]
                            (if (<= (count mclusters) 1)
                              [mclusters mdata]
                              (let [[cdistance closest-mpair] (closest-clusters mclusters cdist-fn)
                                    combined-cluster (apply cmerge-fn (map :data closest-mpair))
                                    combined-mcluster (chier/bi-cluster combined-cluster closest-mpair cdistance)
                                    mclusters (-> (apply disj mclusters closest-mpair)
                                                  (conj combined-mcluster))
                                    mdata (post-merge-fn mdata mclusters)]
                                (recur mclusters mdata))))]
    [mclusters mdata]
    )
  )







(defn link-sim-distance-fn
  "Return a function that given 2 edges [1 2] [2 5]
  will return the distance ( 1- similarity) between the impost nodes.
  Edges with no shared keystone get a similarity of 0, returning a distance of 1.0"
  [svectors]
  (fn [edge1 edge2]
    (let [[impost1 impost2 :as inodes] (impost-nodes edge1 edge2)
          dist (if inodes
                (- 1.0 (cm/mget (svectors impost1) impost2))
                nil)]
      dist)))


(defn average
  [numbers]
  (let [nc (count numbers)]
    (if (= nc 0)
      (do
        (println "nc = 0")
        0)
      (/ (apply + numbers) nc))))


(defn dist-single-link-fn [simfn]
  (fn [lc1 lc2]
    (when (= lc1 lc2)
      (println "$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$EQ CLUSTER" lc1 lc2))
    (let [md (average
               (keep (fn [[n1 n2 :as nx]]
                           (let [s (simfn  n1 n2)]
                             ;;(println "nx: " nx s)
                             s))
                         (combo/cartesian-product lc1 lc2)))]
      md)))

(defn merge-clusters
  [lc1 lc2]
  (concat lc1 lc2))


(defn hier-cluster-simset
  [simset]
  (let [link-simfn (link-sim-distance-fn (:vectors simset))
        distfn (dist-single-link-fn link-simfn)
        cmergefn merge-clusters
        postmergefn (create-default-post-merge-fn simset)]
  (hier-cluster-ex (->single-clusters (:edges simset)) distfn cmergefn postmergefn)))


(def tfile "paper0.csv")
;(def tfile "conceptcc-prominence.csv")

(defn test1 []
  (let [p (prominence-csv->vset tfile)
        s (prom-vset->similarity-vset p)
        x (hier-cluster-simset s)]
    [p s x]
    ))

(defn test2 []
  (let [[p s x :as r] (test1)
        m (last x)]
        [p s x (map-indexed
                 #(println "idx:" %1 "density:" (:density %2)
                           " cluster-count:" (:cluster-count %2))
                 (:mclusters m))]
        ))















