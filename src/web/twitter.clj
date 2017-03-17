(ns web.twitter
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [clojure.math.combinatorics :as combo]
            [com.stuartsierra.frequencies :as freq]
            )
  (:use [twitter.oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.restful])
  (:import [twitter.callbacks.protocols SyncSingleCallback]))


(defn flatten1 [coll]
  (for [x coll, xx x] xx))

(defn extract-max-id [body]
  (if-let [next-results (-> body :search_metadata :next_results)]
    (last (re-find #"id=(\d+)" next-results))))

(defn within-quota? [headers]
  (let [x-rate-limit-remaining (:x-rate-limit-remaining headers)]
    (if x-rate-limit-remaining
      (pos? (Integer. x-rate-limit-remaining)) true)))

(defn curr-utime 
  "current unix time in seconds"
  [] 
  (quot (System/currentTimeMillis) 1000))

(defn quota-sleep [headers]
  (let [x-rate-limit-reset (:x-rate-limit-reset headers)] 
    (max 0
      (- (Integer. x-rate-limit-reset) (curr-utime)))))

(defn iterate-search
  [credentials & params]
  (let [request-fn (partial twitter.api.restful/search-tweets :oauth-creds credentials)
        twitter-get (fn [& *params] 
                      ;;expect crucially :max_id to iterate over responses
                      (let [*params (merge params (apply hash-map *params))
                            {:keys [status body headers]} (request-fn :params *params)]
                        {:max_id (extract-max-id body) 
                         :data (seq (:statuses body)) ;;assume twitter resp. contained is seq 
                         :headers headers}))
        response0 (twitter-get)]
    (if (:data response0)
      (->> response0
           (iterate 
             (fn [{:keys [max_id headers]}]
               (if-not (within-quota? headers) (Thread/sleep (quota-sleep headers)))
               (if max_id (twitter-get :max_id max_id))))
           (take-while :data) (map :data) flatten1))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;data extraction helpers

(def fields [:created_at :favorite_count :retweeted :id :entities :user :retweet_count])
(defn extract-tweet-fields [tweet]
  (select-keys tweet fields))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;suggesting tweeters to follow

(defn count-occurrences
  "return a hash-map of tems to their occurrence
  colls a sequence of terms
  preprocess-fn a function to apply to the terms before extraction"
  ([colls] (count-occurrences identity colls))
  ([preprocess-fn colls]
   (reduce #(assoc %1 %2 (inc (get %1 %2 0)))
           {} (map preprocess-fn colls))))

(defn extract-ngrams
  "return a seq of sorted n-grams
  n n in n-grams
  colls a sequence of terms
  preprocess-fn a function to apply to the terms sequence before extraction"
  ([n colls] (extract-ngrams identity n colls))
  ([preprocess-fn n colls]
   (combo/combinations (-> colls preprocess-fn sort) n)))

;((restrict-range :iqr {:a 1 :b 4 :c 5 :d 9}) 1)
(defn restrict-range 
  "return a filtered hash-map of [term occurrences] as of *-bound ratios
  occurences a  map of terms to their occurences
  (left|right)-bound takes a proportion to respectively filter out based on occurences ordering
  :rr filter occs.range is resricicted within [max-value^1/2, max-value^3/4]
  :irq filter for occurrences within the interquartile range
  " 
  ([left-bound right-bound occurrences]
   (assert (and (< left-bound 1) (<= right-bound 1)) "*-bound should be ratios")
   (let [n (count occurrences)
         left-drops (* n left-bound)
         right-drops (* n (- 1 right-bound))]
     (->> occurrences
          (sort-by val)
          (drop left-drops)
          (drop-last right-drops)
          (map first)
          )))
  ([occurrences] (restrict-range :iqr occurrences))
  ([method occurrences]
   (let [val-in? (fn [l-v r-v v] (and (>= v l-v) (<= v r-v)))
         is-map (map? occurrences)
         counts (if is-map (vals occurrences) occurrences)
         process-selector (fn [*val-in?] 
                            (if is-map
                              (let [filtering-set (set (keys (filter #(-> % val *val-in?) occurrences)))]
                                (fn [occ] 
                                  (seq 
                                    (clojure.set/intersection filtering-set (set occ)))))
                              *val-in?))] 
     (condp = method 
         ;;return occurrences in range [max^0.5, max^0.75]]
         :rr (let [vmax (apply max counts)
                   l-val (Math/pow vmax 1/2)
                   r-val (Math/pow vmax 3/4)
                   *val-in? (partial val-in? l-val r-val)]
               (process-selector *val-in?)
               )
         ;;return occurrences within the interquartile range
         :iqr (let [percentiles (:percentiles (freq/stats (frequencies counts)))
                    *val-in? (partial val-in? (get percentiles 25) (get percentiles 75))]
                (process-selector *val-in?)
                )))))

(defn filter-followers [pred colls]
  (filter #(-> % :followers_count pred) colls))

(defn filter-terms [extraction-*grams-fn pred colls]
  (filter #(-> % :hashtags (json/parse-string) extraction-*grams-fn set pred) colls))

(defn rand-take [n colls]
  (repeatedly n #(rand-nth colls)))

(defn sample-bloggers [*extract-ngrams-fn n-take colls]
  (let [colls (map #(assoc % :hashtags (-> % :hashtags (json/parse-string))) colls)
        terms-pred (->> colls (map :hashtags)
                       (map *extract-ngrams-fn) (remove nil?) flatten1 frequencies 
                       (restrict-range :iqr))
        followers-pred (->> colls (map :followers_count) restrict-range)
        keep? #(every? identity [(-> % :hashtags *extract-ngrams-fn terms-pred) 
                                
                                 (-> % :followers_count followers-pred)])]
    (->> colls (filter keep?) (map :screen_name) (rand-take n-take))
    )
  )
