(defproject buildbot "0.1.0-SNAPSHOT"
  :description "Basic IRC cctray build Bot"
  :license {:name "Do What The Fuck You Want To Public License (WTFPL)"
            :url "http://www.wtfpl.net/"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [irclj "0.5.0-alpha4"]
                 [midje "1.6.3"]
                 [overtone/at-at "1.2.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/core.match "0.2.1"]
                 [clj-http "0.9.2"]]
  :main buildbot.core)
