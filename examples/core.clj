(ns hanasu.core
  "Simple example use case. Echo and broadcast to clients"

  (:require
   [hanasu.server :as srv]
   [hanasu.client :as cli]
   [hanasu.common :as com]

   [clojure.core.async :as async :refer [>! <! go-loop go]]

   [environ.core :refer [env]]

   [msgpack.core :as mpk]
   [msgpack.clojure-extensions]
   [clojure.data.json :as json]

   [clojure.tools.logging :as log]))


;;; Server example stuff ===========================================

(defn broadcast [ch payload]
  (let [msg {:type "broadcast" :payload payload}]
    (run! #(srv/send-msg % msg)
          (mapv (fn[[ch m]] ch) (com/get-svrws))))
  (srv/send-msg ch {:type "broadcastResult" :payload payload}))

(defn echo [ch payload]
  (srv/send-msg ch {:type "echo" :payload payload} :encode :text))

(defn unknown-type-response [ch _]
  (srv/send-msg ch {:type "error" :payload "ERROR: unknown message type"}))

(defn msg-handler [msg]
  (let [{:keys [data ws]} msg
        {:keys [type payload]} data]
    (prn :DATA data :TYPE type :payload payload)
    ((case type
        "echo" echo
        "broadcast" broadcast
        unknown-type-response)
     ws payload)))

;;; Server END example stuff ===========================================



;;; Client example stuff ===============================================
(def user-db (atom {}))

(defn update-udb
  ([] (com/update-db user-db {}))
  ([keypath vorf]
   (com/update-db user-db keypath vorf))
  ([kp1 vof1 kp2 vof2 kps-vs]
   (apply com/update-db user-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-udb [key-path] (com/get-db user-db key-path))


(defn user-dispatch [ch op payload]
  (case op
    :msg (let [{:keys [ws data]} payload]
           (println :CLIENT :msg :payload payload)
           (update-udb [ws :last] data))
    :open (do (println :CLIENT :open :ws payload)
              (update-udb payload {:errcnt 0 :last "NYRCV"}))
    :close (let [{:keys [ws code reason]} payload]
             (println :CLIENT :RMTclose :payload payload)
             (async/put! ch {:op :stop
                             :payload {:ws ws :cause :rmtclose}}))
    :error (let [{:keys [ws err]} payload]
             (println :CLIENT :error :payload payload)
             (update-udb [ws :errcnt] inc))
    :bpwait (let [{:keys [ws msg encode]} payload]
              (println :CLIENT "Waiting to send msg " msg)
              (Thread/sleep 5000)
              (println :CLIENT "Trying resend...")
              (cli/send-msg ws msg :encode encode))
    :sent (println :CLIENT "Sent msg " (payload :msg))
    :stop (let [{:keys [ws cause]} payload]
            (println :CLIENT "Stopping reads... Cause " cause)
            (cli/close-connection ws)
            (update-udb ws :rm))
    (println :CLIENT :WTF :op op :payload payload)))


;;; Client END example stuff ===========================================





(comment

  (let [ch (srv/start-server 3000)]
    (println "Server start, reading msgs from " ch)
    (def srv-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (case op
            :msg (msg-handler payload)
            :open (println :SRV :open :payload payload)
            :close (println :SRV :close :payload payload)
            :bpwait (let [{:keys [ws msg encode]} payload]
                      (println :SRV "Waiting to send msg " msg)
                      (Thread/sleep 5000)
                      (println :SRV "Trying resend...")
                      (srv/send-msg ws msg :encode encode))
            :sent (println :SRV "Sent msg " msg)
            :failsnd (println :SRV "Failed send for " msg)
            :stop (println :SRV "Stopping reads...")
            (println :SRV :WTF msg))
          (when (not= op :stop)
            (recur (<! ch)))))))

  (srv/stop-server)





  ;; Client testing....


  (let [ch (cli/open-connection "ws://localhost:3000/ws")]
    (println "Opening client, reading msgs from " ch)
    (def cli-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (future (user-dispatch ch op payload))
          (when (not= op :stop)
            (recur (<! ch)))))))


  (let [ws (first (get-udb []))]
    (cli/send-msg ws {:type "echo", :payload {:client "Clojure"}}))
)
