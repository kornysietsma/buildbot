(ns buildbot.core
  (:require [irclj.core :as irclj]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [overtone.at-at :as at-at]
            [buildbot.cctray :as cctray])
  (:gen-class))

(def botname "buildbot")

(comment ;"irc" implied structure is
  {:connection nil ; - irclj thing
   :quit-reason (promise) ;- to be delivered on quitting (yes, this will be done better some day)
   :past-statuses (atom []) ; vector of project statuses so far - should be capped at some restart-agent
   })

(defn quit! [irc reason]
  (irclj/quit (:connection irc))
  (deliver (:quit-reason irc) reason))

(defn do-command [irc text nick]
  (case text
    "quit" (quit! irc (str "request from " nick))
    "help" (irclj/message (:connection irc) nick (str
                                                   "send message via 'buildbot:cmd' or msg buildbot with 'cmd'"
                                                   "commands: 'help' and 'quit' only!"))
    (irclj/message (:connection irc) nick (str "unknown command: " text " - try help"))))

(defn send-help [connection nick]
  (irclj/message connection nick "try \"/msg buildbot help\" or \"buildbot: help\" from a channel the bot is in"))

(defn parse-command [text target]
  (println "command to target " target)
  (when-let [match (if (= botname target)
                     (re-matches #"(.*)" text)
                     (re-matches #"buildbot:(.*)" text))]
    (clojure.string/trim (second match))))

(defn privmsg
  "send a private message - takes a promise of an irc connection as real one may not yet exist"
  [irc-promise raw-irc {:keys [nick text target] :as data}]
  (when (realized? irc-promise)
    (if-let [command (parse-command text target)]
      (do
        (println "running command:" command)
        (do-command @irc-promise command nick))
      (if (= botname target)
        (send-help (:connection @irc-promise) nick)))))

(defn callbacks [irc-promise] {:privmsg (partial privmsg irc-promise)})

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

(defn connection [irc-promise]
  (irclj/connect
    host
    port
    botname
    :username "buildbot"
    :realname "buildbot"
    :callbacks (callbacks irc-promise)
    :auto-reconnect-delay-mins 1 ; reconnect delay after disconnect
    :timeout-mins 20 ; socket timeout - length of time to keep socket open when nothing happens
    ))

(defn report [irc events]
  (doseq [event events]
    (do
      (println "reporting: " event)
    (irclj/message irc channel (:message event)))))

(defn add-status [irc status]
  (swap! (:past-statuses irc) conj status))

(defn last-status [irc]
  (last @(:past-statuses irc)))

(defn on-tick [irc]
  (try
    (when-let [status (get-project-statuses)]
      (do
        (when-let [last (last-status irc)]
          (report (:connection irc) (cctray/status->events last status)))
        (add-status irc status)))
    (catch Exception e
      (do
        (println "caught exception:" e)
        (clojure.stacktrace/print-stack-trace e)))))

(defn connect []
  ; circular - callbacks needed in connection want access to our "irc" that we haven't built yet!
  (let [self (promise)
        conn (connection self)
        _ (irclj/join conn channel)
        result    {:connection conn
                   :quit-reason (promise)
                   :past-statuses (atom [])}
        _ (deliver self result)]
    result))

(defn -main [& args]
  (let [pool (at-at/mk-pool)
        irc (connect)
        schedule (at-at/every 5000 #(on-tick irc) pool)]
    (prn "waiting to die.")
    (println @(:quit-reason irc)) ; only get here when promise is delivered
    (prn "done!")
    (at-at/stop schedule)))
