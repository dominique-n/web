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

(defn extract-max-id [metadata]
  (if-let [next-results (:next_results metadata)]
    (last (re-find #"id=(\d+)" next-results))))



;(defn iterate-twitter [request-fn process-fn & args]
  ;(let [{:keys [status body search_metadata] (apply request-fn args)}
        ;]
    ;))
