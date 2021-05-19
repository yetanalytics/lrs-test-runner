(ns com.yetanalytics.lrs.test-runner
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.tools.logging :as l])
  (:import [java.io File]
           [java.nio.file.attribute FileAttribute]
           [java.nio.file Files Path]
           [java.util UUID]))

(defn temp-dir
  "Create a temp dir for the test suite"
  []
  (let [dir-id (UUID/randomUUID)
        ^Path path (Files/createTempDirectory (format "lrs_test_runner-%s"
                                                      dir-id)
                                              (into-array FileAttribute
                                                          []))
        ^File file (.toFile path)]
    (.deleteOnExit file)
    file))

(defn report-sh-result
  "Print a generic sh output."
  [{:keys [exit out err]}]
  (when-let [o (not-empty out)]
    (.write ^java.io.Writer *out* ^String o)
    (flush))
  (when-let [e (not-empty err)]
    (.write ^java.io.Writer *err* ^String e)
    (flush)))

(defn clone-test-suite
  "Ensure that the test suite exists and is installed, return the dir if OK"
  [& {:keys [branch]
      :or {branch "master"}}]
  ;; Attempt Clone
  (l/infof "Downloading LRS Tests from branch: %s" branch)
  (let [^File tempdir (temp-dir)
        conf-git-uri "https://github.com/adlnet/lrs-conformance-test-suite.git"
        {clone-exit :exit :as clone-result} (sh
                                             "git"
                                             "clone"
                                             "--branch" branch
                                             conf-git-uri
                                             (.getPath tempdir))
        _ (report-sh-result clone-result)
        clone-success? (zero? clone-exit)]
    (when-not clone-success?
      (l/warnf "Could not clone from %s on branch %s"
               conf-git-uri
               branch))
    (when clone-success?
      tempdir)))

(defn install-test-suite!
  "Given the test suite dir, try to install the test deps, return the dir if it
  can"
  [^File tempdir]
  (let [{:keys [exit]
         :as install-result} (sh "npm" "install"
                                 :dir tempdir)]
    (if (zero? exit)
      tempdir
      (l/warnf "Test Suite install failed to %s" (.getPath tempdir)))))

(def ^:dynamic *current-test-suite-dir*
 nil)

(defmacro with-test-suite
  "Do things with a test suite and clean it up"
  [& body]
  `(binding [*current-test-suite-dir*
             (some-> (clone-test-suite)
                     install-test-suite!)]
     (try
       ~@body
       (finally
         (.delete *current-test-suite-dir*)))))

;; TODO: remove boiler

(defn foo
  "I don't do a whole lot."
  [x]
  (prn x "Hello, World!"))
