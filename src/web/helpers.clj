(ns web.helpers)


(defn flatten1 [coll]
  (for [x coll, xx x] xx))
