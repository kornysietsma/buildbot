(ns buildbot.t-cctray
  (:require [midje.sweet :refer :all]
            [buildbot.cctray :as subject]))

{:breakers "George Gray <ggray@thoughtworks.com>", :name "mep :: Functional :: Functional", :activity "Sleeping", :lastBuildStatus "Failure", :lastBuildLabel "278", :lastBuildTime "2014-06-02T12:46:08", :webUrl "http://dc1dev01:8153/go/tab/build/detail/mep/278/Functional/1/Functional"}

(defn testprj
  ([name activity lastBuildStatus]
{:name name, :activity activity, :lastBuildStatus lastBuildStatus, :lastBuildLabel "278", :lastBuildTime "2014-06-02T12:46:08", :webUrl "http://server/go/tab/build/detail/foo/278/Functional/1/Functional"})
  ([name activity lastBuildStatus breaker]
   (update-in (testprj name activity lastBuildStatus)
              [name]
              assoc :breakers breaker)))

(fact "changed status can produce events"
      (subject/status-change
        (testprj "foo" "Sleeping" "Success")
        (testprj "foo" "Sleeping" "Failure"))
        => {:event :broken :project "foo" :message "foo is broken!"})

#_(fact "changed status can produce events"
      (subject/changed-events
        (testprj "foo" "Sleeping" "Success")
        (testprj "foo" "Sleeping" "Failure"))
        => {:event :broken :project "foo" :message "foo is broken!"})

(fact "changed statuses can produce events"
      (subject/status->events
        {"foo" (testprj "foo" "Sleeping" "Success")
         "bar" (testprj "bar" "Sleeping" "Failure")}
        {"foo" (testprj "foo" "Sleeping" "Failure")
         "bar" (testprj "bar" "Sleeping" "Success")})
      => (just {:event :broken :project "foo" :message "foo is broken!"}
               {:event :fixed :project "bar" :message "bar is fixed!"}
               :in-any-order))


