(ns pyexsrv.core
  (:require
   [aerial.hanasu.server :as srv]
   [aerial.hanasu.client :as cli]
   [aerial.hanasu.common :as com]
   [clojure.core.async :as async :refer [>! <! go-loop go]]))

(def print-chan (async/chan 10))

(go-loop [msg (async/<! print-chan)]
  (println msg)
  (recur (async/<! print-chan)))

(defn printchan [& args]
  (async/put! print-chan (clojure.string/join " " args)))


(defonce app-bpsize 100)
(defonce app-db (atom {:rcvcnt 0 :sntcnt 0}))

(defn update-adb
  ([] (com/update-db app-db {:rcvcnt 0 :sntcnt 0}))
  ([keypath vorf]
   (com/update-db app-db keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply com/update-db app-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-adb
  ([] (com/get-db app-db []))
  ([key-path] (com/get-db app-db key-path)))


(defn msg-handler [msg]
  (let [{:keys [data ws]} msg
        {:keys [op payload]} data]
    (printchan :msg-handler :op op :data data)
    (if (#{:done "done"} op)
      (srv/send-msg (get-adb :ws) {:op :stop :payload {}} :noenvelope true)
      (srv/send-msg ws {:op "echo" :payload data}))))

(defn server-dispatch [ch op payload]
  (case op
    :msg (let [{:keys [ws]} payload]
           (printchan :MSG payload)
           (msg-handler payload))
    :open (let [ws payload]
            (printchan :SRV :open :payload ws)
            (update-adb :ws ws, [ws :rcvcnt] 0, [ws :sntcnt] 0))
    :close (let [{:keys [ws status]} payload]
             (printchan :SRV :close :payload payload))
    :bpwait (let [{:keys [ws msg encode]} payload]
              (printchan :SRV "Waiting to send msg " msg)
              (Thread/sleep 5000)
              (printchan :SRV "Trying resend...")
              (srv/send-msg ws msg :encode encode))
    :bpresume (printchan :SRV "BP Resume " payload)
    :sent (let [{:keys [ws msg]} payload]
            (printchan :SRV "Sent msg " msg))
    :failsnd (printchan :SRV "Failed send for " {:op op :payload payload})
    :stop (let [{:keys [cause]} payload]
            (printchan :SRV "Stopping reads... Cause " cause)
            (update-adb)
            (srv/stop-server))
    (printchan :SRV :WTF :op op :payload payload)))


(comment

  (let [ch (srv/start-server 8765)]
    (printchan "Server start, reading msgs from " ch)
    (update-adb :chan ch)
    (def srv-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (future (server-dispatch ch op payload))
          (when (not= op :stop)
            (recur (<! ch)))))))

  (async/>!! (get-adb :chan) {:op :stop :payload {:cause :userstop}})

  )
