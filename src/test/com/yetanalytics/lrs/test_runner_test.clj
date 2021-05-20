(ns com.yetanalytics.lrs.test-runner-test
  (:require [clojure.test :refer :all]
            [com.yetanalytics.lrs.test-runner :refer :all]

            ;; mem lrs service
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [com.yetanalytics.lrs.impl.memory :as lrs-impl :refer [new-lrs]]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]

            ;; mem lrs server
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            ))

;; Mem lrs provided for demo
(defn run-lrs
  "run-the mem lrs. Returns a function that stops the server"
  [& {:keys [lrs]
      :or {lrs (new-lrs {})}}]
  (let [server (-> {:env :dev
                    ::http/routes (build {:lrs lrs})
                    ::http/resource-path "/public"
                    ::http/type :jetty ;; :immutant ;; :jetty
                    ::http/port 8080
                    ::http/container-options {:h2c? true
                                              :h2? false
                                              :ssl? false}
                    ::lrs lrs
                    ;; do not block thread that starts web server
                    ::http/join? false
                    ::http/allowed-origins {:creds true :allowed-origins (constantly true)}
                    }
                   i/xapi-default-interceptors
                   http/create-server)]
    (http/start server)
    #(http/stop server)))

;; manual
(deftest manual-test
  (let [stop-fn (run-lrs)
        test-suite-dir (-> (clone-test-suite)
                           install-test-suite!)
        ret (:success? (run-test-suite*
                        test-suite-dir
                        "-e" "http://localhost:8080/xapi" "-b" "-z"
                        "-g" "XAPI-00315"))]
    (is (true? ret))
    (stop-fn)
    ;; cleanup
    (delete-test-suite! test-suite-dir)))

;; Two different styles for using the dynamic var

;; macro
(deftest with-test-suite-test
  (with-test-suite
    (let [stop-fn (run-lrs)
          ret (conformant? "-e" "http://localhost:8080/xapi" "-b" "-z"
                           "-g" "XAPI-00315")]
      (stop-fn)
      (is (true? ret)))))

;; fixture ;; (use-fixtures :once test-suite-fixture)
(deftest test-suite-fixture-test
  (test-suite-fixture
   #(let [stop-fn (run-lrs)
          ret (conformant? "-e" "http://localhost:8080/xapi" "-b" "-z"
                           "-g" "XAPI-00315")]
      (stop-fn)
      (is (true? ret)))))
