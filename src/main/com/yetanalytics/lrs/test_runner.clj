(ns com.yetanalytics.lrs.test-runner
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.tools.logging :as l]
            [clojure.pprint :as pprint]
            [clojure.walk :as w]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as cs])
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
  [& {:keys [branch git-uri]
      :or {git-uri "https://github.com/adlnet/lrs-conformance-test-suite.git"
           branch "master"}}]
  ;; Attempt Clone
  (l/debugf "Downloading LRS Tests from branch: %s" branch)
  (let [^File tempdir (temp-dir)

        {clone-exit :exit :as clone-result} (sh
                                             "git"
                                             "clone"
                                             "--branch" branch
                                             git-uri
                                             (.getPath tempdir))
        _ (report-sh-result clone-result)
        clone-success? (zero? clone-exit)]
    (if clone-success?
      tempdir
      (do
        (l/errorf  (ex-info "Clone Exception"
                            {:type ::clone-exception
                             :git-uri git-uri
                             :branch branch})
                   "Could not clone from %s on branch %s"
                   git-uri
                   branch)))))
(defn remove-lock!
  "Remove the package lock which causes issues in some envs"
  [^File tempdir]
  (l/debug "removing package lock")
  (let [{:keys [exit] :as remove-lock-result}
        (sh "rm" "-f" (format "%s/package-lock.json"
                              (.getPath tempdir)))]
    (zero? exit)))

(defn install-test-suite!
  "Given the test suite dir, try to install the test deps, return the dir if it
  can"
  [^File tempdir]
  (or (and (.exists (io/file (format
                              "%s/node_modules"
                              (.getPath tempdir))
                             ))
           tempdir)
      (if (remove-lock! tempdir)
        (let [{:keys [exit]
               :as install-result} (sh "npm" "install"
                                       :dir tempdir)]
          (report-sh-result install-result)
          (if (zero? exit)
            tempdir
            (do (report-sh-result install-result)
                (l/warnf "Test Suite install failed to %s" (.getPath tempdir))
                (throw (ex-info "Test Suite Install exception"
                                {:type ::install-exception
                                 :tempdir tempdir
                                 :install-result install-result})))))
        (throw (ex-info "Can't remove lockfile"
                        {:type ::delete-lockfile-exception
                         :tempdir tempdir})))))


(defrecord RequestLog [out-str])

(defn wrap-request-logs
  [log-root]
  (w/prewalk (fn [node]
               (if (some-> node :log :out-str string?)
                 (update node :log #(RequestLog. %))
                 node))
             log-root))

(defmethod clojure.pprint/simple-dispatch RequestLog
  [{:keys [out-str]}]
  (pprint/pprint-logical-block
   :prefix "<" :suffix ">"
   (doseq [line (cs/split-lines out-str)]
     (pprint/pprint-newline :linear)
     (.write ^java.io.Writer *out* ^String line))
   (pprint/pprint-newline :linear)))

(defn extract-logs [^File tempdir]
  (into []
        (for [^File f (rest (file-seq (io/file (format "%s/logs"
                                                       (.getPath tempdir)))))
              :when (and (not (.isDirectory f))
                         (not (.endsWith (.getPath f) ".json")))
              :let [{:keys [log] :as test-output} (with-open [rdr (io/reader f)]
                                                    (json/read rdr :key-fn keyword))]]
          {:file f
           :output test-output})))

(defn print-logs [logs]
  (doall
   (doseq [{:keys [^File file
                   output]} logs]
     (l/debugf "\nLog: %s\n\n" (.getPath file))
     (pprint/pprint (wrap-request-logs output))))
  (flush))

(defn delete-logs [^File tempdir]
  (l/debug "Cleaning Logs...")
  (doseq [^File f (rest (file-seq (io/file (format "%s/logs"
                                                   (.getPath tempdir)))))]
    (l/debugf "Deleting Log: %s"
             (.getPath f))
    (.delete f)))

(defn run-test-suite*
  "Run tests for a directory, with args if desired"
  [^File tempdir & args]
  (l/debugf "Running Tests at %s, args: %s" (.getPath tempdir) (prn-str args))
  (let [run-args (or (not-empty args) ["-e" "http://localhost:8080/xapi" "-b" "-z"])
        {:keys [exit out err] :as test-result}
        (apply sh
               "node" "bin/console_runner.js"
               (concat
                run-args
                [:dir tempdir]))
        logs (extract-logs tempdir)]
    (report-sh-result test-result)
    (print-logs logs)
    {:success? (zero? exit)
     :logs (mapv :output logs)}))

(def ^:dynamic *current-test-suite-dir*
  nil)

(defn run-test-suite
  [& args]
  (if *current-test-suite-dir*
    (apply run-test-suite* *current-test-suite-dir* args)
    (throw (ex-info "No test suite currently loaded!"
                    {:type ::no-test-suite
                     :args args}))))

(defn conformant?
  "shortcut to just see if it is success"
  [& args]
  (:success? (apply run-test-suite args) false))

(defn delete-test-suite!
  [^File tempdir]
  (.delete tempdir))

;; macro wrapper makes it work
(defmacro with-test-suite
  "Do things with a test suite and clean it up"
  [& body]
  `(binding [*current-test-suite-dir*
             (some-> (clone-test-suite)
                     install-test-suite!)]
     (try
       ~@body
       (finally
         (delete-test-suite! *current-test-suite-dir*)))))

;; or fixture

(defn test-suite-fixture
  "Fixture to ensure clean test environment."
  [f]
  (binding [*current-test-suite-dir*
            (some-> (clone-test-suite)
                    install-test-suite!)]
    (try
      (f)
      (finally
        (delete-test-suite! *current-test-suite-dir*)))))
