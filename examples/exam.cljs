(ns hanasu.exam
  (:require
   [cljs.core.async
    :as async
    :refer [<! >!]
    :refer-macros [go go-loop]]

   [aerial.hanasu.client :as cli]
   [aerial.hanasu.common :as com]))



(def print-chan (async/chan 10))

(go-loop [msg (async/<! print-chan)]
  (js/console.log (print-str msg))
  (recur (async/<! print-chan)))

(defn printchan [& args]
  (async/put! print-chan (clojure.string/join " " args)))





(defonce user-db (atom {}))

(defn update-udb
  ([] (com/update-db user-db {}))
  ([keypath vorf]
   (com/update-db user-db keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply com/update-db user-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-udb
  ([] (com/get-db user-db []))
  ([key-path] (com/get-db user-db key-path)))


(defn user-dispatch [ch op payload]
  (case op
    :msg (let [{:keys [ws data]} payload]
           (printchan :CLIENT :msg :payload payload)
           (update-udb [ws :lastrcv] data, [ws :rcvcnt] inc))
    :open (let [ws payload]
            (printchan :CLIENT :open :ws payload)
            (update-udb ws {:chan ch, :rcvcnt 0, :sntcnt 0, :errcnt 0}))
    :close (let [{:keys [ws code reason]} payload]
             (printchan :CLIENT :RMTclose :payload payload)
             (go (async/put! ch {:op :stop
                                 :payload {:ws ws :cause :rmtclose}})))
    :error (let [{:keys [ws err]} payload]
             (printchan :CLIENT :error :payload payload)
             (update-udb [ws :errcnt] inc))
    :bpwait (let [{:keys [ws msg encode]} payload]
              (printchan :CLIENT "Waiting to send msg " msg)
              (go (async/<! (async/timeout 5000)))
              (printchan :CLIENT "Trying resend...")
              (cli/send-msg ws msg :encode encode))
    :bpresume (printchan :CLIENT "BP Resume " payload)
    :sent (let [{:keys [ws msg]} payload]
            (printchan :CLIENT "Sent msg " msg)
            (update-udb [ws :lastsnt] msg, [ws :sntcnt] inc))
    :stop (let [{:keys [ws cause]} payload]
            (printchan :CLIENT "Stopping reads... Cause " cause)
            (cli/close-connection ws)
            (update-udb ws :rm))
    (printchan :CLIENT :WTF :op op :payload payload)))



(comment

  (go
    (let [ch (async/<! (cli/open-connection "ws://localhost:3000/ws"))]
      (printchan "Opening client, reading msgs from " ch)
      (def cli-handler
        (loop [msg (<! ch)]
          (let [{:keys [op payload]} msg]
            (user-dispatch ch op payload)
            (when (not= op :stop)
              (recur (<! ch))))))))


  (defn get-test-ws []
    (->> (get-udb []) keys (filter #(-> % keyword? not)) first))

  ;; Stop client
  (let [ws (get-test-ws)
        ch (get-udb [ws :chan])]
    (go (async/>! ch {:op :stop :payload {:ws ws :cause :userstop}})))

  ;; Send server echo msg
  (let [ws (get-test-ws)
        ch (get-udb [ws :chan])]
    (cli/send-msg ws  {:type "echo", :payload {:client "Clojure"}}))

  ;; Send server many broadcasts
  (let [ws (get-test-ws)
        ch (get-udb [ws :chan])]
    (dotimes [_ 40]
      (cli/send-msg ws  {:type "broadcast", :payload {:client "Clojure"}})))

  ;;Manual reset...
  (let [ws (get-test-ws)
        ch (get-udb [ws :chan])]
    (cli/send! ws :byte (mpk/pack {:op :reset :payload {:msgsnt 0}})))

  )
