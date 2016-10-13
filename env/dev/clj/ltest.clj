(ns ltest
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
   [clojure.math.combinatorics :as combo]
   ))


(cm/set-current-implementation :vectorz)


(def csvfile (clojure.java.io/reader "conceptcc-prominence.csv"))
(def pf (csvio/parse-csv csvfile))

(def csv-headers (into [] (rest (first pf))))

(def csv-rows (rest pf))

(defn to-d
  ^double
  [s ^double thresh]
  (let [d (Double/parseDouble s)]
    (if (< d thresh)
    0.0
    d)))

(defn prom-row-data
  [row]
  (rest row))

(println "csv headers:" (count csv-headers) csv-headers)
(println "row count:" (count csv-rows))
(println "first headers:" (take 3 csv-headers))


(defn prom-row-to-thresh-doubles
  [csvrow thresh]
  (map #(to-d % thresh) (prom-row-data csvrow)))

(defn add-csv-row-to-pvector
  [sum csv-row thresh]
  (println (rest csv-row))
  (conj sum (cm/array (prom-row-to-thresh-doubles csv-row thresh))))

(defn prom-csv-to-pvectors
  ([csv-seq]
   (prom-csv-to-pvectors csv-seq 3.0))
  ([csv-seq thresh]
  (reduce #(add-csv-row-to-pvector %1 %2 thresh) [] csv-seq)))

(def pp-vectors (prom-csv-to-pvectors csv-rows))

(println "pp-vector count:" (count pp-vectors))


(defn vectors-to-csv-seq
  [label headers vrows]
  (let [str-rows (for [i (range (count vrows))]
                  (concat [(headers i)] (map str (vrows i)))
                  )
        out-header (concat [label] headers)]
    (concat [out-header] str-rows)))


(with-open [fpp (clojure.java.io/writer "out-pp-filtered.csv" :encoding "UTF-8")]
  (.write fpp (csvio/write-csv (vectors-to-csv-seq "Concepts" csv-headers pp-vectors))))



(def t1 0)
(def t2 3)
(def t3 9)

(def row1 (pp-vectors t1))
(def row2 (pp-vectors t2))
(def row3 (pp-vectors t3))


(defn neighbour-count
  [pv]
  (count (filter #(> % 0.0) pv)))

(def total-edges (reduce (fn [total pv]
                           (let [nc (neighbour-count pv)]
                             (+ total nc)))
                         0 pp-vectors))

(defn row-edge-indices
  ([v]
   (row-edge-indices v -1))
  ([v exclude-idx]
  (reduce (fn [sum [idx itm]]
            (if (and (> itm 0.0) (not= exclude-idx idx))
              (conj sum idx)
              sum)) [] (map-indexed vector v))))

(defn row-edge-names2
  [headers [row-idx row]]
  (let [row-name (headers row-idx)]
    (map #(format "%s-%s" (headers %) row-name) (row-edge-indices row row-idx))))

(defn active-row-edge-names
  [headers [row-idx row]]
  (let [row-name (headers row-idx)]
    (map (fn [col]
           (if (> col row-idx)
             [(format "%s-%s" (headers col) row-name) [col row-idx]]
             [(format "%s-%s" row-name (headers col)) [row-idx col]]))
         (row-edge-indices row row-idx))))

(defn pp-matrix-active-edge-names
  [headers vrows]
  (into [] (distinct (reduce #(concat %1 (active-row-edge-names headers %2)) [] (map-indexed vector vrows)))))





(println "total edges:" total-edges "no-dups:" (/ total-edges 2.0))

(println "row1" (csv-headers t1) row1)
(println "row2" (csv-headers t2) row2)
(println "row3" (csv-headers t3) row3)

(println "row1 edge inclusive indices:" (row-edge-indices row1))
(println "row1 edge names:" (active-row-edge-names csv-headers [0 (pp-vectors 0)]))


(def pedges (pp-matrix-active-edge-names csv-headers pp-vectors))

(println "pp-matrix linked edges:" (count pedges))
(println pedges)



(def v1 row1)
(def v2 row2)
(def v3 (cm/array row3))

(println "v1: " v1)
(println "v2: " v2)
(println "v3: " v3)

(def norm-v1 (cml/norm v1))
(def norm-v2 (cml/norm v2))
(def norm-v3 (cml/norm v3))

(def d1d1 (cm/dot v1 v1))
(def d1d2 (cm/dot v1 v2))
(def d1d3 (cm/dot v1 v3))
(def d2d3 (cm/dot v2 v3))

(println "norm-v1" norm-v1)
(println "norm-v2" norm-v2)
(println "norm-v3" norm-v3)

(println "d1d1" d1d1)
(println "d1d2" d1d2)
(println "d1d3" d1d3)
(println "d2d3" d2d3)

(defn tanimoto-coefficient-0
  ;; norm not right?
  [v1 v2]
  (let [dot-v1-v2 (cm/dot v1 v2)
        norm-v1 (cml/norm v1)
        norm-v2 (cml/norm v2)]
    (/ dot-v1-v2 (- (+ (* norm-v1 norm-v1) (* norm-v2 norm-v2)) dot-v1-v2))))

(defn tanimoto-coefficient
  [v1 v2]
  (let [dot-v1-v2 (cm/dot v1 v2)
        abs-v1 (cm/dot v1 v1)
        abs-v2 (cm/dot  v2 v2)
    sim (/ dot-v1-v2 (- (+ abs-v1 abs-v2) dot-v1-v2))]
    sim))





(def similarity tanimoto-coefficient)

(def s1s1 (/ d1d1 (- (+ (* norm-v1 norm-v1) (* norm-v1 norm-v1)) d1d1)))
(def s1s2 (/ d1d2 (- (+ (* norm-v1 norm-v1) (* norm-v2 norm-v2)) d1d2)))
(def s1s3 (/ d1d3 (- (+ (* norm-v1 norm-v1) (* norm-v3 norm-v3)) d1d3)))
(def s2s3 (/ d2d3 (- (+ (* norm-v2 norm-v2) (* norm-v3 norm-v3)) d2d3)))

(println "s1s1:" s1s1)
(println "s1s1 similiarity:" (similarity v1 v1))
(println "s2s1:" s1s2)
(println "s1s2 similiarity:" (similarity v1 v2))
(println "s2s1 similiarity:" (similarity v2 v1))

(println "s3s1:" s1s3)
(println "s3s2:" s1s3)
(println "s2s3" s2s3)


(defn similarity-row
  [nfrom nkey_ a-pvectors active-edges]
  (let [v1 (a-pvectors nfrom)]
    (cm/array
     (map
      (fn [[lname_ [nfrom nkey_]]]
                                        ;(println "fn: lname" lname_ "nfrom:" nfrom  "nkey" nkey_)
        (similarity v1 (a-pvectors nfrom))) active-edges))))



(defn similarity-matrix
  [a-pvectors active-edges]
  (let [edge-count (count active-edges)]
    (loop [smatrix [] edges active-edges edge->nodes []]
      (if-let [edge (first edges)]
        (let [[_link-name [nfrom nkey]] edge
              rvector (similarity-row nfrom nkey a-pvectors active-edges)]
          (recur (conj smatrix rvector) (rest edges) (conj edge->nodes [nfrom nkey])))
        [smatrix edge->nodes]))))

(def smset (similarity-matrix pp-vectors pedges))
(def sm (first smset))
(def edge-nodes-lookup (second smset))


(println "similarity matrix:" sm)
(println edge-nodes-lookup)


(def edge-labels (into [] (map first pedges)))

(def sim-labels (into [] (map first pedges)))

(with-open [spp (clojure.java.io/writer "out-similarity.csv" :encoding "UTF-8")]
  (.write spp (csvio/write-csv (vectors-to-csv-seq "Edge" sim-labels sm))))




;(defn dist1 [rec1 rec2]
;  (distance (:coords rec1) (:coords rec2)))

;(defn avg1 [recs]
;  { :name "n/a" :coords (average (map :coords recs))})

;(defn dendrogram1 [hier-data]
;  (->svg hier-data :name))

;(defn generate-dendrogram1 [dataset-name]
;  (->>
    ;(load-csv (str "test/data/" dataset-name ".csv"))
    ;(cluster dist avg)
    ;dendrogram
    ;(spit (str "doc/" dataset-name ".svg")) ))


(defn similarity-distance [a b]
  (when (= a b)
    (println "ASKIING FOR SETL NODE DISTANCE>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" a b))
  (let [sc (cm/mget (sm a) b)
        sc (if (> sc 0.0)
             (- 1.0 sc)
             (- 1.0 sc))]
    (println ">>>>>>sc:" sc a b)
    sc))


(defn similarity-distance-0 [a b]
  (when (= a b)
    (println "ASKIING FOR SETL NODE DISTANCE>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" a b))
  (let [sc (cm/mget (pp-vectors a) b)
        sc (if (> sc 0.0)
             (-  1.0 sc)
             100000000.0)]
    ;(println ">>>>>>sc:" sc a b)
    sc))




; min dist between clusters
(defn dist-min [rec1 rec2]
  ;;(println "MD DIST START"
  ;;        "r1:" (:name rec1) (count (:nodes rec1)) "r2:" (:name rec2) (count (:nodes rec2)))
  (when (or (= 0 (count (:nodes rec1))) (= 0 (count (:nodes rec2))))
    (println "MD REC1" rec1)
    (println "MD REC2" rec2))

  (let [md (apply min
             (map (fn
                    [[n1 n2]]
                    (similarity-distance  n1 n2))
                  (combo/cartesian-product (:nodes rec1) (:nodes rec2))))]
    (println "MIN DIST" md (:name rec1) (:name rec2))
    md))

;; not efficient
(defn average
  [numbers]
  (/ (apply + numbers) (count numbers)))

; min dist between clusters
(defn dist-avg [rec1 rec2]
  ;;(println "MD DIST START"
  ;;        "r1:" (:name rec1) (count (:nodes rec1)) "r2:" (:name rec2) (count (:nodes rec2)))
  (when (or (= 0 (count (:nodes rec1))) (= 0 (count (:nodes rec2))))
    (println "MD REC1" rec1)
    (println "MD REC2" rec2))

  (let [md (average
                  (map (fn
                         [[n1 n2]]
                         (similarity-distance  n1 n2))
                       (combo/cartesian-product (:nodes rec1) (:nodes rec2))))]
    (println "AVG DIST" md (:name rec1) (:name rec2))
    md))

(def dist dist-avg)

(def mtotal (atom 0))


; avg is really combine / link clusters
(defn avg [recs]
  (if (= 0 (count recs))
    (do
      (println "AVG called with no recs to merge.")
    nil)
    (do
    (println "merge:" (map #(str ", " (:name %)) recs))
    (let [nodes (flatten (map :nodes recs))
          nrec {:name (apply str ":" (map :name recs))
                :nodes nodes
                :merged-cluster-count (count recs)
                :link-count (count nodes)
                :total-merges (swap! mtotal inc)}]
    nrec))))

(defn to->rec [i]
  {:nodes [i]
   :name (edge-labels i)})

(def xlist (map to->rec (range (count sim-labels))))
(println xlist)



(defn find-closest5
  "Loop through every pair looking for the smallest distance"

  [distance-fn points]
  (println "find-closest:" points)
  (reduce
    (fn [state curr]
      (let [dist (apply distance-fn curr)]
        (if (< dist (or (first state) Integer/MAX_VALUE))
          [dist curr]
          state)))
    []
    (combo/combinations points 2)))


(defn cluster5 [distance-fn average-fn dataset]
  (let [distance-fn (memoize
                      (fn [clust1 clust2]
                        (distance-fn (:data clust1) (:data clust2))))]
    (loop [clusters (set (map chier/bi-cluster dataset))]
      (if (<= (count clusters) 1)
        (first clusters)
        (let [[closest lowest-pair] (find-closest5 distance-fn clusters)
              averaged-data (average-fn (map :data lowest-pair))
              new-cluster (chier/bi-cluster averaged-data lowest-pair closest)]
          (recur
            (->
              (apply disj clusters lowest-pair)
              (conj new-cluster))))))))



(defn dendrogram [hier-data]
  (cvizd/->svg hier-data :name))

(def dataset-name "out-x15")

(defn generate-dendrogram []
  (->> xlist
    ;;(load-csv (str "test/data/" dataset-name".csv"))
    (cluster5 dist avg)
    dendrogram
    (spit (str dataset-name ".svg")) ))

(defn generate-clusters []
  (->> xlist
       ;;(load-csv (str "test/data/" dataset-name".csv"))
       (cluster5 dist avg)))



(def xc (generate-clusters))

; M = Total Number of Links


(defn partition-density
  []
  )






