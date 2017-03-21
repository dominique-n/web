(ns web.guardian
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [web.credentials :as creds]
            ))


(def ^:dynamic *api-key* (-> creds/portfolio :guardian :api-key))

(def urls {:content "https://content.guardianapis.com/search"
           :tags "http://content.guardianapis.com/tags"
           :sections "https://content.guardianapis.com/sections"
           :editions "https://content.guardianapis.com/editions"
           :item "https://content.guardianapis.com/"})

(defn http-iterate [endpoint query-params]
  (let [query-params0 (merge {:api-key *api-key* :format "json" page-size 50} query-params)
        http-get (partial http/get (endpoint urls))
        {:keys [status0 headers0 body0 error0 opts0]} @(http-get :query-params query-params0)
        (if (= status 200)
          )]
    ))

(defn take-n-item [n http-it])

