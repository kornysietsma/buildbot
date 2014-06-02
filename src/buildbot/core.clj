(ns buildbot.core
  (:require [irclj.core :as irclj]
            [clojure.pprint :refer [pprint]]))

(defn raw-log [_ & args]
  (pprint args))

(def botname "buildbot")

(defn admin? "is a user an admin - possibly checking command for some auth info"
  [user _command] (= user "korny"))

(defn command? [text]
  (re-matches #"@.*" text))

(defn do-command [irc text nick]
  (case text
    "@quit" (irclj/quit irc)
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
  (irclj/join @irc "#buildbottest" )) ; test against hircd, which annoyingly can't handle hyphens in channels

(connect)

(comment
  (irclj/quit @irc))