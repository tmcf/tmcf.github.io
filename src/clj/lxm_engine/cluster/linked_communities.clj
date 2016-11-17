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
    [clojure.math.combinatorics :as combo]

    [lacij.edit.graph :as le]
    [lacij.view.graphview :as lv]
    [lacij.layouts.layout :as lac]
    )

 ;; (:import (com.leximancer.fs LexRFiles LexRFileSystems ILexRPath))
  )


(cm/set-current-implementation :vectorz)

(defn xprintln [& args]
  nil)


;;;;;;; CSV read / parse write

(defn- parse-labeled-csv-headers-and-rows
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


(defn self-prominence-to-avg
  [pvset]
  (println "TO-AVG:" (keys pvset) (count (:vectors pvset)))
  (let [self-prow-to-avg (fn [[ridx rvector]]
                           (println "self-prow-to-avg:" ridx rvector)
                           (let [[rsum rcount] (reduce (fn [[sum count] [idx val]]
                                                         (if (and (> val 0.0) (not= idx ridx))
                                                           [(+ sum val) (inc count)]
                                                           [sum count]))
                                                       [0.0 0] (map-indexed vector rvector))
                                 ^double avg-value (if (= rcount 0) 0.0 (/ rsum rcount))]
                             (println "class rvector:" (class rvector))
                             (println (format "Adjust %s %d:%d from %f to %f" (get (:headers pvset) ridx) ridx ridx (cm/mget rvector ridx) avg-value))
                             (cm/mset! rvector ridx avg-value)))]
    (doseq [rv (map-indexed vector (:vectors pvset))] (self-prow-to-avg rv))
    pvset))


(def defvset-opts {:self-avg false :threshold 3.0})

(defn prominence-csv->vset
  "Parse a prominence csv into vector representation. Values below threshold are set to 0.0.
  Takes incsv arg that is convertible to a Reader.
  Returns "
  ( [incsv] (prominence-csv->vset incsv {}))
  ([incsv popts]
   (let [popts (merge defvset-opts popts)
         threshold (:threshold popts)
         raw-csv (parse-labeled-csv-headers-and-rows incsv)
         prom-vectors (prominence-csv-rows->vectors (:rows raw-csv) threshold)
         pvset {:headers (:headers raw-csv) :vectors prom-vectors}]
     (if (:self-avg popts)
       (do (println "SELF AVG")
           (self-prominence-to-avg pvset))
       pvset))))



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
   The neighbours in community clustering."
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

(defn xdist
  ^double [v1 v2 xlog]
  (let [dot-v1-v2 (cm/dot v1 v2)
        abs-v1 (cm/dot v1 v1)
        abs-v2 (cm/dot  v2 v2)
        s (/ dot-v1-v2 (- (+ abs-v1 abs-v2) dot-v1-v2))
        d (- 1.0 s)]
    (when xlog
      (println "dot-ij" dot-v1-v2)
      (println "abs-v1" abs-v1)
      (println "abs-v2" abs-v2)
      (println "s:" s "d:" d)
      )
    d
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
                             xlog (and (= ik [23 1]) (= jk [23 15]))
                             _ (when xlog (println "vi:" i vi))
                             _ (when xlog (println "vj:" j vj))
                             ;sim (similarity-fn vi vj)]
                             sim (xdist vi vj xlog)]
                         (when xlog (println "zsimilarity:" ik jk sim "\n"))
                         sim)
                       (do
                        ; (println "no keystone / similarity:" ik jk 0.0"\n")
                         1.0)))) edges))))



(defn prom-vset->similarity-vset
  "Convert a prominence matrix (vector set) to
  a linked community similarity matrix (vector set)"
  [vset]
  (let [sfn (memoize tanimoto-distance) ;tanimoto-coefficient)
        edges (vset-edges vset)
        edgepair->sidx (reduce (fn [es [sidx edge]] (assoc es edge sidx)) {} (map-indexed vector edges))
        pvectors (:vectors vset)
        ; for each edge, calculate similarity to all other edges
        ; nieve implementation
        svectors (into [] (reduce
                            (fn [r cedge]
                              (conj r (similarity-vec cedge pvectors edges sfn)))
                            [] edges))]
    {:edgepair->sidx edgepair->sidx :edges edges :vectors svectors :headers (edge-labels edges (:headers vset))}
  ))


(def MAX_CLUSTER_DISTANCE 10.0)

(defn mclusters-str
  [mclusters]
  (clojure.string/join "||" (map (fn [i] (into [] (:data i))) mclusters)))



(defn closest-clusters
  [clusters cdist-fn]
  (xprintln "closest-clusters....")
  (reduce
    (fn [[low-dist low-cluster-combo_ :as current-low] cluster-combo]
      (let [_ (xprintln "closest-clusters/call cluster-distance on combo:"
                       (mclusters-str cluster-combo))
            dist (apply cdist-fn cluster-combo)
            _ (xprintln "closest-clusters:  got pair dist:" (format "%2.2E" dist) "for"
                       (mclusters-str cluster-combo))]
        (if (<= dist low-dist)
          [dist cluster-combo]
          current-low)))
    [MAX_CLUSTER_DISTANCE [-1 -1]]
    (combo/combinations clusters 2)))


(defn- cluster-edges-to-nodes
  "Convert edge set into node list"
  [edges]
  (distinct (flatten edges)))

(defn cluster-edges-to-labels
  [c headers]
  (let [ nodes (cluster-edges-to-nodes c)
        node-labels (map #(get headers %) nodes)]
    node-labels))


(defn cluster-partition-density
  ^double [cluster-edges]
  (let [nlinks (count cluster-edges)
        nc (count (cluster-edges-to-nodes cluster-edges))
        _ (xprintln "nlinks:" nlinks "nnodes:" nc) ;cluster-edges)
        _ (xprintln "nnodes:" nc (cluster-edges-to-nodes cluster-edges))
        ]
    (if (= nc 2)
      0.0
      (let [nc-1 (- nc 1.0)
            cdensity (/ (* nlinks (- nlinks nc-1))
                        (* (- nc 2.0) nc-1))

            cdensity2 (/ (- nlinks nc-1)
                         (- (/ (* nc nc -1) 2) nc-1))
            ]
        ;(println "cpdensity1:" cdensity "cpdensity2:" cdensity2)
        cdensity))))

(defn network-partition-density
  [network total-edge-count]
  (let [dsum (reduce #(+ %1 (cluster-partition-density %2)) 0.0 network)
        dnetwork (/ (* 2.0 dsum) total-edge-count)]
    (xprintln "Network Density:" dnetwork "total-edge-count" total-edge-count)
    dnetwork))


(defn create-default-post-merge-fn
  "Keep track of cluster merge history"
  [simset]
  (let [ecount (count (:edges simset))]
    (fn ([] {:levels 0
             :mclusters []})
      ([mdata new-clusters ^double cdistance]
       (let [levels (inc (:levels mdata))
             old-networks (:mclusters mdata)
             new-network (into [] (map :data new-clusters))
             ndensity (network-partition-density new-network ecount)
             _ (println (format "merge details: %d cdistance;%f density:%f cluster-count:%d" levels (- 1.0 cdistance) ndensity (count new-network)))]
         {:levels levels
          :mclusters (conj old-networks {:merge             levels
                                         :partition-density ndensity
                                         :closest-similarity (- 1.0 cdistance)
                                         :cluster-count     (count new-network)
                                         :clusters          new-network})})))))

(defn ->single-clusters
  "Convert sequence of items to sequence of vectors each containing the single item.
  Used as starting point for clustering."
  [items]
  (map vector items))




(defn hier-cluster-ex
  "Perform hierarchical clustering. Returns vector containing [mclusters mdata],
  where mcluster is the final single (meta) cluster with links to absorbed clusters.
  (keys mdata) (:levels :mclusters)

  And, mdata is a map containing snapshot maps of all merges.
  :levels  total number of merges
  :mclusters vector of merge metadata for each merge level:
  Each merge entry contains:
  :merge (the merge number)
  :partition-density, parition-density for this merge
  :cluster-count, number of clusters in this partition (network)
  :clusters vector of clusters in this network. Each cluster is a vector of the edges in the cluster.
  "
  [initial-clusters cdist-fn cmerge-fn post-merge-fn]
  (let [post-merge-fn (or post-merge-fn post-merge-fn)
        cdist-fn (memoize (fn [c1 c2]
                            (cdist-fn (:data c1) (:data c2))))
        starting-mclusters (set (map chier/bi-cluster initial-clusters))
        [mclusters mdata] (loop [mclusters starting-mclusters
                                 mdata (post-merge-fn (post-merge-fn) starting-mclusters 1.0)]
                            (let [md (last (:mclusters mdata))]
                              (xprintln "Pass#" (:levels mdata) "cluster count:" (:cluster-count md))
                              (doseq [c (:clusters md)]
                                (xprintln "cluster:" c))
                              )
                            (if (<= (count mclusters) 1)
                              [mclusters mdata]
                              (let [_ (println "Pass:" (:levels mdata) "Find closest:============================")
                                    [cdistance closest-mpair] (closest-clusters mclusters cdist-fn)
                                    _ (xprintln (format "Found Closest clusters:%2.2E" cdistance)
                                               (clojure.string/join "|" (map :data closest-mpair)))
                                    combined-cluster (apply cmerge-fn (map :data closest-mpair))
                                    combined-mcluster (chier/bi-cluster combined-cluster closest-mpair cdistance)
                                    mclusters (-> (apply disj mclusters closest-mpair)
                                                  (conj combined-mcluster))
                                    mdata (post-merge-fn mdata mclusters cdistance)]
                                (recur mclusters mdata))))]
    [mclusters mdata]
    )
  )



(defn create-node-prominence-weight-fn
  [prom-vset]
  (let [pvectors (:vectors prom-vset)]
    (fn [n1 n2]
      (cm/mget (pvectors n1) n2))))





(defn create-edge-distance-fn
  "Return a function that given 2 edges [1 2] [2 5]
  will return the distance ( 1- similarity) between the impost nodes.
  Edges with no shared keystone get a similarity of 0, returning a distance of 1.0"
  [svectors edge->sidx]
  (fn [edge1 edge2]
    (let [e1-idx (edge->sidx edge1)
          e2-idx (edge->sidx edge2)
          dist (cm/mget (svectors e1-idx) e2-idx)]
      dist)))


(defn average
  [numbers]
  (let [nc (count numbers)]
    (if (not numbers) (println "NO NUMBERS"))
    (if (= nc 0)
      (do
        (println "nc = 0")
        1.0)
      (/ (apply + numbers) nc))))


(defn dist-single-link-fn [simfn]
  (fn [lc1 lc2]
    (xprintln "cluster-distance C1 links:" lc1)
    (xprintln "cluster-distance C2 links:" lc2)

    (let [combos (combo/cartesian-product lc1 lc2)
          _ (xprintln "cluster-distance C1 C2 link combos:" combos)
          distances (map (fn [[n1 n2 :as nx]]
                             (let [s (simfn  n1 n2)]
                               s))
                           combos)
          md (apply min distances)
          ;;md (average distances)
          ;;md (average (filter #(< % 1.0) similarites))
          _ (xprintln "cluster-distance C1 C2 using min is:" (format "%2.2E" md)
                     (reduce #(format "%s %2.2E" %1 %2) "" distances))]
      md)))

(defn merge-clusters
  [lc1 lc2]
  (concat lc1 lc2))


(defn hier-cluster-simset
  [simset]
  (let [link-simfn (create-edge-distance-fn (:vectors simset) (:edgepair->sidx simset))
        distfn (dist-single-link-fn link-simfn)
        cmergefn merge-clusters
        postmergefn (create-default-post-merge-fn simset)]
  (hier-cluster-ex (->single-clusters (:edges simset)) distfn cmergefn postmergefn)))


;;(def tfile "paper0.csv")
(def tfile "conceptcc-prominence.csv")



(defn cluster
  [pfile popts]
  (let [p (prominence-csv->vset (or pfile tfile) popts)
        s (prom-vset->similarity-vset p)
        x (hier-cluster-simset s)
        mdata (second x)
        merge-count (:levels mdata)
        clusters (:mclusters mdata)]
    ; for debug sanity check
    (write-vset-csv p "Prominence", (str "threshold-" tfile))
    (write-vset-csv s "Similarity", (str "distance-similarity-" tfile))
    {:prominence-vset   p
     :sdistance-vset    s
     :merge-count       merge-count

     :max-partition     (reduce (fn [r c]
                           (if (> (:partition-density r) (:partition-density c))
                             r
                             c))
                              (first clusters) clusters)
     :ranked-partitions (reverse (sort-by :partition-density clusters))
     :node-labels (:headers p)
     }

  ))

(defn test1 []
  (let [p (prominence-csv->vset tfile)
        s (prom-vset->similarity-vset p)
        x (hier-cluster-simset s)]
    (write-vset-csv p "Prominence", (str "threshold-" tfile))
    (write-vset-csv s "Similarity", (str "distance-similarity-" tfile))
    [p s x]
    ))

(defn test2 []
  (let [[p s x :as r] (test1)
        m (last x)]
        [p s x (map-indexed
                 #(let [md %2]
                   (println "idx:" %1 "density:" (:partition-density %2)
                            " cluster-count:" (:cluster-count %2))
                   (doseq [c (:clusters md)]
                     (println "cluster:" c)))
                 (:mclusters m))]
        ))

(defn test3 []
  (let [[p s x :as r] (test1)
         m (second x)
         clusters (:mclusters m)
         _ (println "Cluster count:" (count clusters))
        max-partition (reduce (fn [r c] (if (> (:partition-density r) (:partition-density c)) r c)) (first clusters) clusters)
        sorted-partitions (reverse (sort-by :partition-density clusters))]
    (println (:merge max-partition) "max density: " (:partition-density max-partition) ", cluster-count:" (:cluster-count max-partition))
    {:stuff             [ p s x]
     :clusters          clusters
     :max-partition     max-partition
     :ranked-partitions sorted-partitions}))




(defn mdetails
  [merge headers]
  (xprintln (dissoc merge :clusters))
  (doseq [ c (:clusters merge)]
    (let [ nodes (cluster-edges-to-nodes c)
          node-labels (map #(get headers %) nodes)]
      (xprintln node-labels)
      ))
  )

(defn partition-cluster-details
  [pcluster nwfn]
  {:nodes (cluster-edges-to-nodes pcluster)
   :edges (map (fn [[e1 e2]] (vector e1 e2 (nwfn e1 e2))) pcluster)
   })

(defn partition-details
  ([hcluster-results]
   (partition-details hcluster-results (:max-partition hcluster-results)))
  ([hcluster-results partition]
   (let [nweight-fn (create-node-prominence-weight-fn (:prominence-vset hcluster-results))
         clusters-in-p (:clusters partition)
         cdetails (reduce #(conj %1 (partition-cluster-details %2 nweight-fn)) [] clusters-in-p)]
     {:merge (:merge partition)
      :cluster-count (:cluster-count partition)
      :cluster-graph-details cdetails}

     )))


(defn xaddnode [g node]
  (let [node (str node)]
  (le/add-node g node node))
  )

(defn  xaddedge [g [n1 n2 w]]
  (let [n1 (str n1)
        n2 (str n2)]
    ;(println "addedge" n1 n2 w)
    (le/add-edge g (keyword (format "%s-%s" n1 n2)) n1 n2)
    ) )


(defn lacij-graph-cluster0
  [cluster-detail fname]
  (let [g (le/graph)
        g (reduce xaddnode g (:nodes cluster-detail))
        ;_ (println "znodes" (:nodes g) "bingo" (keys g))
        g (reduce xaddedge g (:edges cluster-detail))
        ;_ (println "layout....")
        l (lac/layout g :radial)
        ;_ (println "build....")
        b (le/build l)]
    ;(println "export....")
    (lv/export b fname :indent "yes")
    ))




#_(defn lacij-graph-partition0
  [hcluster-results cluster-partition fprefix]
  (let [fprefix (or fprefix "./xout")
        numbered-clusters (map-indexed #(vector %1 %2) (:cluster-graph-details cluster-partition))
        layout-cluster (create-lacij-add-cluster-fn hcluster-results)]
    (doseq [[cidx c] numbered-clusters]
      (println "Process " cidx)
      (lacij-graph-cluster c (format "%s/cluster%d.svg" fprefix cidx))
      )

    )
  )

(defn clean-lacij-layout0
  [cl]
  (println "clean cl with nodes:" (count (:nodes cl)))
  (let [clean-label #(select-keys % [:text :position])
        clean-lnode (fn [[nid lnode]]
                      (let [{:keys [id view inedges outedges]} lnode
                            {:keys [x y width height labels]} view
                            ]
                        {:id id
                         :x x
                         :y y
                         :width width
                         :height height
                         :labels  (map clean-label labels)
                         }))
        clean-ledge (fn [[eid ledge]]
                      (let [{:keys [id view src dst]} ledge
                            {:keys [labels]} view]
                        {:id id
                         :labels (map clean-label labels)
                         :src src
                         :dst dst
                         }))]
    {:width (:width cl)
     :height (:height cl)
     :nodes (map clean-lnode (:nodes cl))
     :edges (map clean-ledge (:edges cl))
     }
    ))

(defn clean-lacij-layout
  [cl]
  ;(println "clean cl with nodes:" (count (:nodes cl)))
  (let [clean-label #(select-keys % [:text :position])
        clean-lnode (fn [[_nidkey lnode]]
                      (let [{:keys [id view _inedges _outedges]} lnode
                            {:keys [x y width height labels]} view
                            ]
                        {:id id
                         :x x
                         :y y
                         :width width
                         :height height
                         :labels  (map clean-label labels)
                         }))
        clean-ledge (fn [[_eidkey ledge]]
                      (let [{:keys [id view src dst]} ledge
                            {:keys [labels]} view]
                        {:id id
                         :labels (map clean-label labels)
                         :src src
                         :dst dst
                         }))
        owidth (:width cl)
        oheight (:height cl)
        lnodes (:nodes cl)
        ; dimensions of lacij have a huge offset, need to pull it diagram back in
        first-cnode (clean-lnode (first lnodes))
        ;offset-x 0 ;(:x first-cnode)
        ;offset-y 0 ;(:y first-cnode)
        clean-fix-node (fn [[^double pxmin ^double pymin ^double pwidth ^double pheight nodes] lnodekv]
                         (let [cnode (clean-lnode lnodekv)
                               {:keys [id x y width height]} cnode
                               ;_ (println "pxmin" pxmin "pymin" pymin "pwidth" pwidth "pheight" pheight)
                               ;_ (println "cleaned node:" lnodekv cnode)
                               ;_ (println x y width height)
                               pxmin (Math/min ^double pxmin ^double x)
                               pymin (Math/min pymin y)
                               pwidth (Math/max pwidth (+ x width))
                               pheight (Math/max pheight (+ y height))]
                            [pxmin pymin pwidth pheight (assoc nodes id cnode)]
                           ))
        [px py pwidth pheight cleaned-nodes] (reduce clean-fix-node [Double/MAX_VALUE Double/MAX_VALUE 0.0 0.0 {}] (:nodes cl))]
    {:owidth owidth
     :oheight oheight
     :x-offset px
     :y-offset py
     :width pwidth
     :height pheight
     :nodes (into {} (map (fn [[nid cnode]]  [ nid (assoc cnode :x (- (:x cnode) px) :y (- (:y cnode) py))]) cleaned-nodes))
     :edges (map clean-ledge (:edges cl))
     }
    ))

(defn lacij-layout-partition
  [hcluster-results cluster-partition & [{:keys [clean graph-opts] :as opts :or {graph-opts {}}}]]
  (println "lacij-layout-parition: opts" opts "graph-opts" graph-opts)
  (let [
        node-labels (:node-labels hcluster-results)
        add-node (fn [g node]
                   (let [node (get node-labels node)]
                     (le/add-node g node node)))
        add-edge (fn [g [n1 n2 w]]
                   (let [n1 (get node-labels n1)
                         n2 (get node-labels n2)]
                     (le/add-edge g (keyword (format "%s-%s" n1 n2)) n1 n2)))
        layout-cluster (fn [cluster-detail]
                         (let [g (apply le/graph graph-opts)
                               g (reduce add-node g (:nodes cluster-detail))
                               g (reduce add-edge g (:edges cluster-detail))
                               l (lac/layout g :radial)]
                               l))
        _ (println "clean is:" clean "opts is:" opts)
        process-layout (if clean
                         (comp clean-lacij-layout layout-cluster)
                         layout-cluster)]
    (map (fn [cd] (process-layout cd)) (:cluster-graph-details cluster-partition))))


(defn lacij-svg-partition
  [hcluster-results cluster-partition fprefix & [opts]]
  (println "SVG PARTITION OPTS:" opts)
  (let [playout (apply lacij-layout-partition hcluster-results cluster-partition opts)
        dprefix (format "%s/dummy.txt" fprefix)]
    (clojure.java.io/make-parents  dprefix)
    (doseq [[cidx clayout] (map-indexed #(vector %1 %2) playout)]
      (println "Process svg for cluster:" cidx)
        (lv/export (le/build clayout) (format "%s/cluster-%d.svg" fprefix cidx)))
    playout))




(defn sconvert
  [ppcsv]
  (let [p (parse-labeled-csv-headers-and-rows ppcsv)
        all-nodes (:headers p)
        all-rows (:rows p)
        edges-and-weights (loop [results [] nodes all-nodes rows all-rows]
                            (if-let [cnode (first nodes)]
                              (let [crow (first rows)
                                    results (concat results (map #(vector cnode %1 %2) all-nodes (rest crow)))]
                                (recur results (rest nodes) (rest rows)))
                              results))]
    (println "ED" edges-and-weights)
    (println "LINES:" (count edges-and-weights))
    (with-open [wrt (clojure.java.io/writer (str ppcsv ".edges"))]
      (doseq [[e1 e2 w] edges-and-weights]
        (println e1 e2 w)
        (.write wrt (str e1 " " e2 " " w "\n"))))))


(defn sorted-weights-export
  [ppcsv]
  (let [p (parse-labeled-csv-headers-and-rows ppcsv)
        all-nodes (:headers p)
        all-rows (:rows p)
        edges-and-weights (loop [results [] nodes all-nodes rows all-rows]
                            (if-let [cnode (first nodes)]
                              (let [crow (first rows)
                                    results (concat results (map #(vector cnode %1 %2) all-nodes (rest crow)))]
                                (recur results (rest nodes) (rest rows)))
                              results))
    ;(println "ED" edges-and-weights)
    ;(println "LINES:" (count edges-and-weights))
    esorted (sort-by (fn [[e1 e2 w]] w) edges-and-weights)]
    (with-open [wrt (clojure.java.io/writer (str ppcsv ".sorted-edges"))]
      (doseq [[e1 e2 w] esorted]
        (println e1 e2 w)
        (.write wrt (str w "\t" e1 "\t" e2 "\n"))))))













