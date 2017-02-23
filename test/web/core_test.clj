(ns web.core-test
  (:require [midje.sweet :refer :all]
            [web.core :refer :all]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer :all]
            [clojure.core.async :refer [go go-loop chan <!! <! put!]]
            ))

(with-fake-http ["http://www.true.html/page" "<html><body>html</html></body>"
                 "http://www.true.content.html/page" "<html><body>
                                                     <p>content1</p>
                                                     <p>content2</p>
                                                     </html></body>"
                 "http://www.only.txt/page" "only text"
                 "http://fomatted.doc/format" 101
                 "http://ly.c" "non-resource"
                 "http://google.com/" "non-resources"]
  
  (future-facts "About `extract-urls"
                (extract-urls )))
