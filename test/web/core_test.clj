(ns web.core-test
  (:require [midje.sweet :refer :all]
            [web.core :refer :all]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer :all]
            [clojure.core.async :refer [go chan <!! <! put!]]
            ))

(let [true-html "<html><body>html</html></body>"
      true-content-html "<html><body>
                        <p>content1</p><p>content2</p>
                        </html></body>"
      error {:status 400 :error "error"}
      only-txt "only text"
      formatted-doc 101
      non-resource "non-resource"
      true-html-url "http://www.true.html/page"
      true-html-content-url "http://www.true.content.html/page"
      error-url "http://error.com"
      only-txt-url "http://www.only.txt/page"
      formatted-doc-url "http://fomatted.doc/format"
      ly-c-url "http://ly.c"
      google-com "http://google.com/"] 

  (with-fake-http [true-html-url true-html
                   true-html-content-url true-content-html
                   error-url error 
                   only-txt-url only-txt
                   formatted-doc-url formatted-doc
                   ly-c-url non-resource
                   google-com non-resource]

    (future-facts "About `extract-html-text"
                  (extract-html-text true-html) => empty?
                  (extract-html-text true-content-html) => (just ["content1" "content2"])
                  )

    (go 
      (future-facts "About `launch-async"
                    (let [channel (chan 1)]
                      (launch-async channel true-html-url)
                      (<!! channel) => "content1,content2\n"
                      )

                    (let [channel (chan 1)]
                      (launch-async channel error-url)
                      (json/parse-string (<!! channel) true) => (content [{:url error-url :status 400}])
                      )
                    ))

    (go
      (future-facts "About `process-async"
                    (let [channel (chan 2)]
                      (doseq [m ["m1" "m2"]] (>! chan m))
                      (process-async channel identity) => (just ["m1" "m2"] :in-any-order))
                    ))

    (go
      (future-facts "About `http-gets-async"
                    (let [channel (chan 3)
                          urls [true-html-url true-html-content-url error-url]
                          func identity]
                      (http-gets-async func urls) => (just ["\n" "content1,content2\n"
                                                                #(re-seq #"^:url.+:headers.+400" %)]
                                                               :in-any-order)
                    )))

  ))
