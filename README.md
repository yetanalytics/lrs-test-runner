# com.yetanalytics.lrs/test-runner

Run the [ADL LRS Conformance Test Suite](https://github.com/adlnet/lrs-conformance-test-suite) from clojure tests.

## Usage

The tests show the 3 basic ways to use this:

``` clojure

;; manual
(deftest manual-test
  (let [stop-fn (run-lrs)
        test-suite-dir (-> (clone-test-suite)
                           install-test-suite!)
        ret (:success? (run-test-suite*
                        test-suite-dir
                        "-e" "http://localhost:8080/xapi" "-b" "-z"))]
    (is (true? ret))
    (stop-fn)
    ;; cleanup
    (delete-test-suite! test-suite-dir)))

;; Two different styles for using the dynamic var

;; macro
(deftest with-test-suite-test
  (with-test-suite
    (let [stop-fn (run-lrs)
          ret (conformant? "-e" "http://localhost:8080/xapi" "-b" "-z")]
      (stop-fn)
      (is (true? ret)))))

;; fixture ;; (use-fixtures :once test-suite-fixture)
(deftest test-suite-fixture-test
  (test-suite-fixture
   #(let [stop-fn (run-lrs)
          ret (conformant? "-e" "http://localhost:8080/xapi" "-b" "-z")]
      (stop-fn)
      (is (true? ret)))))

```

## License

Copyright © 2021 Yet Analytics

Distributed under the Eclipse Public License version 1.0.
