(ns hanasu.common
  (:require [clojure.core.async :as async]
            [com.rpl.specter :as sp]))



(defonce hanasu-db
  (atom {:srv-db {:server nil :conns {}}
         :cli-db {:open-chan (async/chan (async/sliding-buffer 10))
                  :close-chan (async/chan (async/sliding-buffer 10))}}))

#_(reset! hanasu-db
          {:srv-db {:server nil :conns {}}
           :cli-db {:open-chan (async/chan (async/sliding-buffer 10))
                  :close-chan (async/chan (async/sliding-buffer 10))}})


(defn ev [x] (if (vector? x) x [x]))


(defn get-db [key-path]
  (let [key-path (ev key-path)]
    (sp/select-one [sp/ATOM (apply sp/keypath key-path)] hanasu-db)))

(defn update-db
  ([] (reset! hanasu-db
              {:srv-db {:server nil :conns {}}
               :cli-db {:open-chan (async/chan (async/sliding-buffer 10))
                        :close-chan (async/chan (async/sliding-buffer 10))}}))
  ([key-path vorf]
   (let [vorf (if (= vorf :rm) sp/NONE vorf)
         vorf (if (fn? vorf) vorf (constantly vorf))
         key-path (ev key-path)]
     (sp/transform [sp/ATOM (apply sp/keypath key-path)] vorf hanasu-db)))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (doseq [[k v] (->> kps-vs (sp/setval sp/BEGINNING [kp1 vof1 kp2 vof2])
                      (partition-all 2))]
     (update-db k v))
   @hanasu-db))


(defn xform-keys [db-key kps-vs]
  (let [xform-ks (mapv #(sp/setval sp/BEFORE-ELEM db-key (ev %))
                       (take-nth 2 kps-vs))
        vs (take-nth 2 (rest kps-vs))]
    (interleave xform-ks vs)))


(defn update-sdb
  ([] (update-db :srv-db {:server nil :conns {}}))
  ([key-path vorf]
   (update-db (sp/setval sp/BEFORE-ELEM :srv-db (ev key-path)) vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (let [kps-vs (->> kps-vs (sp/setval sp/BEGINNING [kp1 vof1 kp2 vof2])
                     (xform-keys :srv-db))]
     (apply update-db kps-vs))))

(defn get-sdb
  ([] (get-db :srv-db))
  ([key-path]
   (get-db (sp/setval sp/BEFORE-ELEM :srv-db (ev key-path)))))

(defn get-svrws [] (get-sdb :conns))
(defn get-sws-rec [ws] (get-sdb [:conns ws]))


(defn update-cdb
  ([] (update-db :cli-db
                 {:open-chan (async/chan (async/sliding-buffer 10))
                  :close-chan (async/chan (async/sliding-buffer 10))}))
  ([key-path vorf]
   (update-db (sp/setval sp/BEFORE-ELEM :cli-db (ev key-path)) vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (let [kps-vs (->> kps-vs (sp/setval sp/BEGINNING [kp1 vof1 kp2 vof2])
                     (xform-keys :cli-db))]
     (apply update-db kps-vs))))

(defn get-cdb
  ([] (get-db :cli-db))
  ([key-path]
   (get-db (sp/setval sp/BEFORE-ELEM :cli-db (ev key-path)))))

(defn get-cliws [] (get-cdb []))
(defn get-cws-rec [ws] (get-cdb [ws]))



(comment
  (update-sdb)
  (update-sdb :x 1 :y 2 [:kk1 :kk2] 10)

  (update-cdb)
  (update-cdb :x 1 :y 2 [:kk1 :kk2] 10)

  )
