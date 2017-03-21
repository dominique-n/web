(ns web.helpers-test
  (:require [midje.sweet :refer :all]
            [web.helpers :refer :all]))


(facts "About `flatten1"
       (flatten1 [[1 2] [3]]) => [1 2 3])

