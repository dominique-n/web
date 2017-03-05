(ns web.twitter
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
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

(defn iterate-twitter
  "request-fn is a request function compatible with `twitter-api"
  [request-fn process-fn & args]
  (let [args (conj args :result_type "popular" :include_entities "true")
        twitter-get (fn [& *args] 
                      ;;expect crucially :max_id to iterate over responses
                      (let [*args (apply conj args *args)
                            {:keys [status body]} (apply request-fn *args)]
                        {:max_id (extract-max-id body) 
                         :statuses (seq (:statuses body))}))
        response0 (twitter-get)]
    (if (:statuses response0)
      (->> response0
           (iterate 
             (fn [{max_id :max_id}]
               (if max_id (twitter-get max_id))))
           (take-while :statuses) (map :statuses) flatten1
           (map process-fn)))))
