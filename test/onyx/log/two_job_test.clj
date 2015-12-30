(ns onyx.log.two-job-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [onyx.extensions :as extensions]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.api :as api]
            [onyx.test-helper :refer [playback-log get-counts load-config]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [clojure.test :refer [deftest is testing]]))

(def my-inc identity)

(deftest log-two-job
  (let [onyx-id (java.util.UUID/randomUUID)
        config (load-config)
        env-config (assoc (:env-config config) :onyx/id onyx-id)
        peer-config (assoc (:peer-config config)
                           :onyx/id onyx-id
                           :onyx.peer/job-scheduler :onyx.job-scheduler/balanced)
        env (onyx.api/start-env env-config)
        peer-group (onyx.api/start-peer-group peer-config)
        n-peers 12
        v-peers (onyx.api/start-peers n-peers peer-group)
        catalog-1 [{:onyx/name :a
                    :onyx/plugin :onyx.test-helper/dummy-input
                    :onyx/type :input
                    :onyx/medium :dummy
                    :onyx/batch-size 20}

                   {:onyx/name :b
                    :onyx/fn :onyx.log.two-job-test/my-inc
                    :onyx/type :function
                    :onyx/batch-size 20}

                   {:onyx/name :c
                    :onyx/plugin :onyx.test-helper/dummy-output
                    :onyx/type :output
                    :onyx/medium :dummy
                    :onyx/batch-size 20}]

        catalog-2 [{:onyx/name :d
                    :onyx/plugin :onyx.test-helper/dummy-input
                    :onyx/type :input
                    :onyx/medium :dummy
                    :onyx/batch-size 20}

                   {:onyx/name :e
                    :onyx/fn :onyx.log.two-job-test/my-inc
                    :onyx/type :function
                    :onyx/batch-size 20}

                   {:onyx/name :f
                    :onyx/plugin :onyx.test-helper/dummy-output
                    :onyx/type :output
                    :onyx/medium :dummy
                    :onyx/batch-size 20}]

        j1 (onyx.api/submit-job peer-config
                                {:workflow [[:a :b] [:b :c]]
                                 :catalog catalog-1
                                 :task-scheduler :onyx.task-scheduler/balanced})

        j2 (onyx.api/submit-job peer-config
                                {:workflow [[:d :e] [:e :f]]
                                 :catalog catalog-2
                                 :task-scheduler :onyx.task-scheduler/balanced})
        ch (chan n-peers)
        replica (playback-log (:log env) (extensions/subscribe-to-log (:log env) ch) ch 2000)]

    (testing "peers balanced on 2 jobs" 
      (is (= [{(:id (:a (:task-ids j1))) 2
               (:id (:b (:task-ids j1))) 2
               (:id (:c (:task-ids j1))) 2}
              {(:id (:d (:task-ids j2))) 2
               (:id (:e (:task-ids j2))) 2
               (:id (:f (:task-ids j2))) 2}]
             (get-counts replica [j1 j2]))))

    (doseq [v-peer v-peers]
      (onyx.api/shutdown-peer v-peer))

    (onyx.api/shutdown-peer-group peer-group)

    (onyx.api/shutdown-env env)))
