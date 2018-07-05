(ns hanasu.client
  (:require [http.async.client :as http]
            [http.async.client.websocket :as wss]
            [clojure.core.async :as async]

            [msgpack.core :as mpk]
            [msgpack.clojure-extensions]
            [clojure.data.json :as json]
            #_[cheshire.core :as json]

            [clojure.tools.logging :as log]))


(defonce cli-db (atom {:open-chan (async/chan) :close-chan (async/chan)}))
#_(reset! cli-db {:open-chan (async/chan) :close-chan (async/chan)})

(defn get-chans []
  @cli-db)

(defn get-chan-rec [ch]
  (get @cli-db ch))

#_(def url "ws://localhost:3000/ws")


(defn send-msg [ch msg & {:keys [encode] :or {encode :binary}}]
  (let [msg {:op :msg :payload msg}
        emsg (if (= encode :binary)
               (mpk/pack msg)
               (json/write-str msg))
        enc (if (= encode :binary) :byte :text)]
    (wss/send ch enc emsg)))


(defn msg-handler [ch msg]
  (let [msg (if (bytes? msg)
              (mpk/unpack msg)
              (json/read-str msg))]
    (case (msg :op)
      :reset
      (let [bpsize (msg :payload)]
        (swap! cli-db
               (fn[db] (-> db (update-in [ch :msgcnt] (constantly 0))
                             (update-in [ch :bpsize] (constantly bpsize)))))
        (wss/send ch :binary (mpk/pack {:op :reset :payload 0})))

      :msg
      (let [client-rec (@cli-db ch)
            msg (msg :payload)]
        (if (>= (inc (client-rec :msgcnt)) (client-rec :bpsize))
          (do (swap! cli-db (fn[db] (update-in db [ch :msgcnt] (constantly 0))))
              (wss/send ch :binary (mpk/pack {:op :reset :payload 0})))
          (swap! cli-db (fn[db] (update-in db [ch :msgcnt] inc))))
        (async/>!! (client-rec :chan) msg)))))

(defn on-open [open-ws]
  (let [cur-db @cli-db
        [client ws client-chan] (async/<!! (cur-db :open-chan))
        client-rec (cur-db client-chan)]
    (when (not (contains? client-rec open-ws))
      (assert (= open-ws ws)
              (format "OPEN unequal channels %s %s" open-ws ws))
      (swap! cli-db
             (fn[db]
               (let [client-rec (assoc client-rec :ws ws :msgcnt 0 :bpsize 0)]
                 (assoc db client-chan client-rec, open-ws client-rec))))
      (async/>!! (client-rec :chan) {:op :open :payload ws}))))

(defn on-close [close-ws code reason]
  (when (contains? @cli-db close-ws)
    (let [cur-db @cli-db
          [client ws client-chan] (async/<!! (cur-db :close-chan))
          client-rec (cur-db client-chan)]
      (assert (= close-ws ws)
              (format "CLOSE unequal channels %s %s" close-ws ws))
      (swap! cli-db
             (fn[db] (dissoc db close-ws client-chan)))
      (async/>!! (client-rec :chan)
                 {:op :close :payload {:ws ws :code code :reason reason}}))))

(defn on-error [ws err]
  (let [client-rec (@cli-db ws)]
    (async/>!! (client-rec :chan) {:op :error :payload err})))


(defn open-connection
  [url]
  (let [client (http/create-client)
        client-chan (async/chan)
        db (swap! cli-db
                  (fn[db]
                    (assoc db client-chan
                              {:client client
                               :url url
                               :chan client-chan})))
        ws (http/websocket client
                           url
                           :open  on-open
                           :close on-close
                           :error on-error
                           :text msg-handler
                           :byte msg-handler
                           )]
    (async/>!! (@cli-db :open-chan) [client ws client-chan])
    client-chan))

(defn close-connection [ch]
  (let [client-rec (@cli-db ch)
        ws (client-rec :ws)
        client (client-rec :client)
        client-chan (client-rec :chan)]
    (http/close client)
    (async/>!! (@cli-db :close-chan) [client ws client-chan])
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
  (def ws2 (http/websocket client
                          url
                          :open  on-open
                          :close on-close
                          :error on-error
                          :text handle-message
                          :byte handle-message
                          ))
  (http/close client)

  (wss/send
   ws :text (json/write-str
             {:type "broadcast", :payload {:client "Clojure"}}))
  (wss/send
   ws :text (json/write-str
             {:type "echo", :payload {:client "Clojure"}}))

  (wss/send
   ws :byte (mpk/pack
             {:type "broadcast", :payload {:client "Clojure"}}))

  )
