(ns web.core-test
  (:require [midje.sweet :refer :all]
            [web.core :refer :all]
            [test-with-files.core :refer  [with-files public-dir]]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer :all]
            [clojure.core.async :refer [go chan >!! >! <!! <! put!]]
            ))


(defn async-blocker [fun & args]
  (let [chan-test (chan)]
    (go (>! chan-test (apply fun args)))
    (<!! chan-test)))


(facts "About `true-html?"
       (true-html? "<html> lol <body> yo ") => truthy
       (true-html? "<html> <body> yo ") => truthy
       (true-html? "<html> hello") => falsey
       )

(let [true-html-url "http://www.true.html/page"
      true-html-content-url "http://www.true.content.html/page"
      error-url "http://error.com"
      only-txt-url "http://www.only.txt/page"
      formatted-doc-url "http://fomatted.doc/format"
      ly-c-url "http://ly.c"
      google-com "http://google.com/"
      true-html "<html><body>html</html></body>"
      true-content-html "<html><body>
                        <p>content1</p><p>content2</p>
                        </html></body>"
      error {:status 401 :error "error"}
      only-txt {:body "only text"}
      formatted-doc {:body 101}
      non-resource {:body "non-resource"}] 


  (facts "About `extract-html-text"
         (extract-html-text true-html) => "html"
         (extract-html-text true-content-html) => #(re-seq #"content1content2" %)
         )

  (let [*launch-async (partial launch-async insert!)]
    (facts "About `launch-async"
           (with-fake-http [error-url error]
             (launch-async channel [error-url]))
           (<!! channel) => {:status 401 :msg "error" :url error-url}

           (with-fake-http [formatted-doc-url formatted-doc]
             (launch-async channel [formatted-doc-url]))
           (<!! channel) => {:msg :formatted :url formatted-doc-url :type "class java.lang.Long"}

           (with-fake-http [true-html-content-url true-content-html]
             (launch-async #"(?s)<html.+<body.+<" [:html :body :p] channel [true-html-content-url]))
           (<!! channel) => {:body "content1 content2" :url true-html-content-url :msg :tailored}

           (with-fake-http [true-html-url true-html]
             (launch-async channel [true-html-url]))
           (<!! channel) => {:body "html" :url true-html-url :msg :generic}

           (with-fake-http [only-txt-url only-txt]
             (launch-async channel [only-txt-url]))
           (<!! channel) => {:body "only text" :url only-txt-url :msg :unstructured}
           ))

  


  )
