(ns hanasu.client
  (:require [http.async.client :as http]
            [http.async.client.websocket :as wss]
            [clojure.core.async :as async]

            [com.rpl.specter :as sp]

            [msgpack.core :as mpk]
            [msgpack.clojure-extensions]
            [clojure.data.json :as json]

            [hanasu.common :refer [update-cdb get-cdb]]))


(def send! wss/send)

(defn send-msg
  [ws msg & {:keys [encode] :or {encode :binary}}]
  (if (>= (get-cdb [ws :msgsnt])
          (get-cdb [ws :bpsize]))
    (async/>!! (get-cdb [ws :chan])
               {:op :bpwait
                :payload {:ws ws :msg msg :encode encode
                          :msgsnt (get-cdb [ws :msgsnt])}})
    (let [msg {:op :msg :payload msg}
          emsg (if (= encode :binary)
                 (mpk/pack msg)
                 (json/write-str msg))
          enc (if (= encode :binary) :byte :text)]
      (wss/send ws enc emsg)
      (update-cdb [ws :msgsnt] inc)
      (async/>!! (get-cdb [ws :chan])
                 {:op :sent
                  :payload {:ws ws :msg msg
                            :msgsnt (get-cdb [:conns ws :msgsnt])}}))))

(defn receive [ws msg]
  (let [msg (if (bytes? msg)
              (mpk/unpack msg)
              (json/read-str msg))]
    (case (msg :op)
      :set
      (let [bpsize (-> msg :payload :bpsize)
            msgrcv (-> msg :payload :msgrcv)]
        (update-cdb [ws :msgrcv] msgrcv, [ws :bpsize] bpsize))

      :reset
      (update-cdb [ws :msgsnt] (-> msg :payload :msgsnt))

      :msg
      (let [rcvd (get-cdb [ws :msgrcv])]
        (if (>= (inc rcvd) (get-cdb [ws :bpsize]))
          (do (update-cdb [ws :msgrcv] 0)
              (send! ws :binary (mpk/pack {:op :reset :payload {:msgsnt 0}})))
          (update-cdb [ws :msgrcv] inc))
        (async/>!! (get-cdb [ws :chan])
                   {:op :msg, :payload {:data (msg :payload)}})))))


(defn on-open [ws]
  (println "Client OPEN " ws)
  (async/>!! (get-cdb :open-chan) ws))

(defn on-close [ws code reason]
  (println "Client CLOSE " :code code :reason reason :ws ws)
  (async/>!! (get-cdb :close-chan) [ws code reason]))

(defn on-error [ws err]
  (let [client-rec (get-cdb ws)]
    (async/>!! (client-rec :chan) {:op :error :payload {:ws ws :err err}})))


(defn open-connection
  [url]
  (let [client (http/create-client)
        client-chan (async/chan (async/sliding-buffer 3))
        _ (update-cdb client-chan {:client client :url url :chan client-chan})
        ws (http/websocket client
                           url
                           :open  on-open
                           :close on-close
                           :error on-error
                           :text receive
                           :byte receive
                           )]
    (let [ws (async/<!! (get-cdb :open-chan))
          client-rec (get-cdb client-chan)
          client-rec (assoc client-rec :ws ws :bpsize 0 :msgrcv 0 :msgsnt 0)]
      (async/<!! (get-cdb :open-chan)) ; bogus second call of open callback
      (update-cdb client-chan client-rec ws client-rec)
      (async/>!! client-chan {:op :open :payload ws}))
    client-chan))

(defn close-connection [ws]
  (let [client (get-cdb [ws :client])
        client-chan (get-cdb [ws :chan])]
    (http/close client)
    (let [[ws code reason] (async/<!! (get-cdb :close-chan))]
      (async/<!! (get-cdb :close-chan)) ; bogus second call of close callback
      (update-cdb client-chan :rm ws :rm)
      (async/>!! client-chan
                 {:op :close :payload {:ws ws :code code :reason reason}}))
    client-chan))





;;; Testing comment area
;;;
(comment

  (def client (http/create-client))
  (def ws (http/websocket client
                          url
                          :open  on-open
                          :close on-close
                          :error on-error
                          :text handle-message
                          :byte handle-message
                          ))
  (http/close client)

  (send!
   ws :text (json/write-str
             {:type "broadcast", :payload {:client "Clojure"}}))
  (send!
   ws :text (json/write-str
             {:type "echo", :payload {:client "Clojure"}}))

  (send!
   ws :byte (mpk/pack
             {:type "broadcast", :payload {:client "Clojure"}}))

  )
