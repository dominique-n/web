(ns web.guardian
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [web.credentials :as creds]
            [web.helpers :refer :all]
            ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;helpers

(def ^:dynamic *api-key* (-> creds/portfolio :guardian :api-key))

(def *endpoints {:content "https://content.guardianapis.com/search"
                 :tags "http://content.guardianapis.com/tags"
                 :sections "https://content.guardianapis.com/sections"
                 :editions "https://content.guardianapis.com/editions"
                 :item "https://content.guardianapis.com/"})

(defn sleep [ms] (Thread/sleep ms))

(defn respect-quota 
  ([] (sleep 100))
  ([headers] (respect-quota 0 headers))
  ([page-size headers]
   (let [remaining-queries (-> headers :x-ratelimit-remaining-day Integer.)
         projected-queries (- remaining-queries page-size)]
     (if (pos? projected-queries)
       (sleep 100)
       (throw (Exception. "daily quota used"))))))

(def *query-params {:api-key *api-key* 
                    :format "json" :page-size 50 :show-fields ["headline" "body"]
                    :timeout 1000})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;content

(defn http-get-apiurl [api-url]
  @(http/get api-url {:query-params *query-params}))

(defn http-iterate 
  ([api-url] (http-iterate api-url {}))
  ([endpoint query-params]
   (if (keyword? endpoint) 
     (do (assert (contains? #{:content :tags} endpoint) "iterateble endoints are [:content :tags]")
         (http-iterate (endpoint *endpoints) query-params))
     (let [query-params (merge  *query-params query-params)
           ;http-get (partial http/get (endpoint urls))
           http-get (partial http/get endpoint)
           *respect-quota (partial respect-quota (:page-size query-params))]
       (->> {:page 0}
            (iterate (fn [{page :page headers :headers}]
                       (if page
                         (do (if headers (*respect-quota headers))
                             (let [page (inc page)
                                   query-params (assoc query-params :page page)
                                   {:keys [status headers body error opts]} @(http-get {:query-params query-params})
                                   response (:response (json/parse-string body true))
                                   *status (:status response)
                                   {:keys [pages pageSize results total]} response
                                   ]
                               (cond
                                 error (throw (Exception. error))
                                 (not= status 200) (throw (Exception. (str "status: " status)))
                                 (not= *status "ok") (throw (Exception. (str "api status: " *status)))
                                 (= page pages) {:results results}
                                 :else {:page page :headers headers :results results})))
                         )))
            next (take-while identity)
            (map :results))))))

(defn take-n-item 
  ([n http-it] (take-n-item identity n http-it))
  ([kw n http-it] (->> http-it flatten1 (take n) (map kw))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;singleitem

(defn http-singleitem 
  ([api-url] (http-singleitem api-url {}))
  ([api-url query-params]
   (let [query-params (merge *query-params query-params)
         item-content #(-> % :body (json/parse-string true) :response :content)]
     (respect-quota)
     (item-content
          @(http/get api-url {:query-params query-params})))))

(defn get-body [http-response]
  (-> http-response :body (json/parse-string true) :response))

(defn extract-singlitem-text [item]
  (-> item :fields :body))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;sections

(defn http-content-section [q]
  (respect-quota)
  (cond 
   (re-find #"^http" q) @(http/get q {:query-params *query-params})
   :else @(http/get (:content *endpoints) {:query-params (assoc *query-params :section q)})))

(defn get-section-total [q]
  {:section q :total (-> q http-content-section get-body :total)})

(defn map-section-total [qs]
  (map get-section-total qs))

(defn props 
  ([counts] (props :section :total counts))
  ([n counts] (props n :section :total counts))
  ([k v counts]
   (let [sum (reduce + (map v counts))]
     (reduce #(assoc %1 (k %2) (/ (v %2) sum))
             {} counts)))
  ([n k v counts]
   (->> (props k v counts)
        (reduce (fn [acc [k v]] (assoc acc k (long (* v n)))) {})
        (filter #(pos? (val %)))
        (into {}))))

(defn make-sections-size [target world-count world-n]
  (assoc (props world-n world-count) (:section target) (:total target)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;integration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn retrieve-sections-sample 
  ([sections-count] 
   (assert (<= (reduce + (vals sections-count)) 5000)
           "you wont be able to retrieve them in a single request")
   (retrieve-sections-sample false sections-count))
  ([quota-check? sections-count]
   (let [section (first (keys sections-count))
         http-it (if (re-find #"^http" section) http-iterate
                   #(http-iterate :content {:sectionId %}))]
     (for [[section cnt] sections-count, 
           api-url (take-n-item :apiUrl cnt (http-it section))]
       {:section section :api_url api-url}))))

(defn retrieve-section-articles [section-articles-api-url]
  (map (fn [{:keys [section api_url]}]
         {:section section 
          :content (-> api_url http-singleitem )})
       section-articles-api-url))
