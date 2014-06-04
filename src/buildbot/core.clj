(ns buildbot.core
  (:require [irclj.core :as irclj]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [overtone.at-at :as at-at]
            [buildbot.cctray :as cctray])
  (:gen-class))

(defn raw-log [_ & args]
  (pprint args))

(def botname "buildbot")

(defn admin? "is a user an admin - possibly checking command for some auth info"
  [user _command] (= user "korny"))

(defn command? [text]
  (re-matches #"@.*" text))

(def quitted (promise))

(defn quit! [irc reason]
  (irclj/quit irc)
  (deliver quitted reason))

(defn do-command [irc text nick]
  (case text
    "@quit" (quit! irc (str "request from " nick))
    (irclj/message irc nick (str "unknown command: " text))
    )
  )

(defn send-help [irc nick]
  (irclj/message irc nick "only the @quit command works at the moment - and you need to be an admin"))

(defn privmsg [irc {:keys [nick text target] :as data}]
  (if (= botname target)
    (if (and (command? text) (admin? nick text))
      (do-command irc text nick)
      (send-help irc nick))
    ; no handling of any general messages right now - only command messages to the bot
    ))

(def callbacks {:privmsg privmsg
                ; :raw-log raw-log
                })

(def host (or (System/getenv "BUILDBOT_HOST")
              "localhost"))

(def port (Integer/parseInt (or (System/getenv "BUILDBOT_PORT")
                                "6667")))

(def url (or (System/getenv "BUILDBOT_URL")
              "http://localhost:8000/cctray.xml"))

(def channel (or (System/getenv "BUILDBOT_CHANNEL")
                 "#general"))

; use python to serve test files: python -m SimpleHTTPServer

(defn get-project-statuses []
  (try
    (let [{:keys [status body] :as response} (http/get url)]
      (if (= status 200)
        (cctray/projects body)
        (do
          (println "invalid http response:" response)
          nil)))
    (catch Exception e
      (println "Caught exception " e)
      nil)))

(defn connection [] (irclj/connect
                      host
                      port
                      botname
                      :username "buildbot"
                      :realname "buildbot"
                      :callbacks callbacks))

(def irc (atom nil))

(defn connect []
  (reset! irc (connection))
  (irclj/join @irc channel )) ; test against hircd, which annoyingly can't handle hyphens in channels

(def last-status (atom nil))

(defn report [irc  events]
;  (clojure.pprint/pprint events)
  (doseq [event events]
    (do
      (println "reporting: " event)
    (irclj/message irc channel (:message event)))))

(defn on-tick []
  (try
  (if-let [status (get-project-statuses)]
    (do
      (if @last-status
        (do
          (report @irc (cctray/status->events @last-status status))
          (reset! last-status status)  ; bad bad bad
          )
        (reset! last-status status))))
  (catch Exception e
    (do
      (println "caught exception:" e)
      (clojure.stacktrace/print-stack-trace e)
      ))))

(def schedule (atom nil))

(defn -main [& args]
  (let [pool (at-at/mk-pool)]
    (connect)
    (reset! schedule (at-at/every 5000 on-tick pool))
    (prn "waiting to die.")
    (println @quitted)
    (prn "done!")
    (at-at/stop @schedule)
    ))

(comment
  (quit! @irc "manually from repl"))