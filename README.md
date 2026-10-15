# com.yetanalytics.lrs/test-runner

Run the [ADL LRS Conformance Test Suite](https://github.com/adlnet/lrs-conformance-test-suite) from clojure tests. Create per-test-run or per-test instances of the test suite.

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

## Dependency vulnerability tracking

The `Deps` GitHub Actions workflow generates a Maven POM from `deps.edn` and
submits the resolved Maven dependency tree to GitHub on pushes to `main`.
It can also be run manually from the Actions tab. With dependency graph and
Dependabot alerts enabled, GitHub can report known vulnerabilities in these
dependencies.

Run `make pom.xml` to generate the same POM locally. The generated file replaces
the old checked-in POM and is ignored by Git; `deps.edn` remains the dependency
source.

This submission covers the base library dependencies and their Maven transitives.
It excludes dependencies added by the `:test` alias, including its Git dependencies.
That alias needs separate dependency submission handling before test dependency
coverage is complete. The external ADL conformance suite and its npm dependencies
are also outside this Maven submission.

## Roadmap

This is basically a port of what `lrs` does, and assumes a working node/npm environment.

- [ ] Ensure node/npm environment
- [ ] Async shell (currently uses `clojure.java.shell`)
- [ ] Spec for test output, better output handling
- [ ] Logging control/config

## License

Copyright © 2021 Yet Analytics

Distributed under the Apache License version 2.0.
