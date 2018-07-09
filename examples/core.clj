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




(def print-chan (async/chan 10))

(go-loop [msg (async/<! print-chan)]
  (println msg)
  (recur (async/<! print-chan)))

(defn printchan [& args]
  (async/put! print-chan (clojure.string/join " " args)))




;;; Server example stuff ===========================================

(defn broadcast [ch payload]
  (let [msg {:type "broadcast" :payload payload}]
    (run! #(srv/send-msg % msg)
          (mapv (fn[[ch m]] ch) (com/get-svrws))))
  (srv/send-msg ch {:type "broadcastResult" :payload payload}))

(defn echo [ch payload]
  (srv/send-msg ch {:type "echo" :payload payload} :encode :binary #_:text))

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


(defonce app-db (atom {}))

(defn update-adb
  ([] (com/update-db app-db {}))
  ([keypath vorf]
   (com/update-db app-db keypath vorf))
  ([kp1 vof1 kp2 vof2 kps-vs]
   (apply com/update-db app-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-adb
  ([] (com/get-db app-db []))
  ([key-path] (com/get-db app-db key-path)))

(defn server-dispatch [ch op payload]
  (case op
    :msg (msg-handler payload)
    :open (do (printchan :SRV :open :payload payload)
              (update-adb :chan ch))
    :close (printchan :SRV :close :payload payload)
    :bpwait (let [{:keys [ws msg encode]} payload]
              (printchan :SRV "Waiting to send msg " msg)
              (Thread/sleep 5000)
              (printchan :SRV "Trying resend...")
              (srv/send-msg ws msg :encode encode))
    :sent (printchan :SRV "Sent msg " (payload :msg))
    :failsnd (printchan :SRV "Failed send for " {:op op :payload payload})
    :stop (let [{:keys [cause]} payload]
            (printchan :SRV "Stopping reads... Cause " cause)
            (srv/stop-server))
    (printchan :SRV :WTF :op op :payload payload)))

;;; Server END example stuff ===========================================



;;; Client example stuff ===============================================

(defonce user-db (atom {}))

(defn update-udb
  ([] (com/update-db user-db {}))
  ([keypath vorf]
   (com/update-db user-db keypath vorf))
  ([kp1 vof1 kp2 vof2 kps-vs]
   (apply com/update-db user-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-udb
  ([] (com/get-db user-db []))
  ([key-path] (com/get-db user-db key-path)))


(defn user-dispatch [ch op payload]
  (case op
    :msg (let [{:keys [ws data]} payload]
           (printchan :CLIENT :msg :payload payload)
           (update-udb [ws :last] data))
    :open (do (printchan :CLIENT :open :ws payload)
              (update-udb payload
                          {:chan ch :ws payload :errcnt 0 :last "NYRCV"}))
    :close (let [{:keys [ws code reason]} payload]
             (printchan :CLIENT :RMTclose :payload payload)
             (async/put! ch {:op :stop
                             :payload {:ws ws :cause :rmtclose}}))
    :error (let [{:keys [ws err]} payload]
             (printchan :CLIENT :error :payload payload)
             (update-udb [ws :errcnt] inc))
    :bpwait (let [{:keys [ws msg encode]} payload]
              (printchan :CLIENT "Waiting to send msg " msg)
              (Thread/sleep 5000)
              (printchan :CLIENT "Trying resend...")
              (cli/send-msg ws msg :encode encode))
    :sent (printchan :CLIENT "Sent msg " (payload :msg))
    :stop (let [{:keys [ws cause]} payload]
            (printchan :CLIENT "Stopping reads... Cause " cause)
            (cli/close-connection ws)
            (update-udb ws :rm))
    (printchan :CLIENT :WTF :op op :payload payload)))


;;; Client END example stuff ===========================================





(comment

  ;; Server testing ...

  (let [ch (srv/start-server 3000)]
    (printchan "Server start, reading msgs from " ch)
    (def srv-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (future (server-dispatch ch op payload))
          (when (not= op :stop)
            (recur (<! ch)))))))

  (async/>!! (get-adb :chan) {:op :stop :payload {:cause :userstop}})



  ;; Client testing....

  (let [ch (cli/open-connection "ws://localhost:3000/ws")]
    (printchan "Opening client, reading msgs from " ch)
    (def cli-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (future (user-dispatch ch op payload))
          (when (not= op :stop)
            (recur (<! ch)))))))


  ;; Stop client
  (let [ws (ffirst (get-udb []))
        ch (get-udb [ws :chan])]
    (async/>!! ch {:op :stop :payload {:ws ws :cause :userstop}}))

  ;; Send server echo msg
  (let [ws (ffirst (get-udb []))
        ch (get-udb [ws :chan])]
    (cli/send-msg ws  {:type "echo", :payload {:client "Clojure"}}))

  ;; Send server many broadcasts
  (let [ws (ffirst (get-udb []))
        ch (get-udb [ws :chan])]
    (dotimes [_ 50]
      (cli/send-msg ws  {:type "broadcast", :payload {:client "Clojure"}})))

  ;;Manual reset??
  (let [ws (ffirst (get-udb []))
        ch (get-udb [ws :chan])]
    (cli/send! ws :byte (mpk/pack {:op :reset :payload {:msgsnt 0}})))

  )
