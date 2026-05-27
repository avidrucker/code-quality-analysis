#!/usr/bin/env bb
;; assess_test.bb — unit tests for the core helpers in ../assess.bb.
;;
;; Run:  ./test/assess_test.bb
;; Exit: 0 if all tests pass, 1 if any fail.
;;
;; assess.bb is loaded via `load-file` rather than required as a
;; dependency. The (when (= *file* babashka.file) ...) guard at the
;; bottom of assess.bb keeps `-main` from running when loaded this way.
;; Private helpers are reached via var-deref (@#'assess/foo) so the
;; production namespace stays uncluttered.

(ns assess-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing run-tests]]))

(load-file
  (str (-> (System/getProperty "babashka.file") fs/absolutize fs/parent str)
       "/../assess.bb"))

;; --- private-function aliases ---

(def expand-tilde          @#'assess/expand-tilde)
(def numeric-pred-pass?    @#'assess/numeric-pred-pass?)
(def check-one-pred        @#'assess/check-one-pred)
(def pass-when-satisfied?  @#'assess/pass-when-satisfied?)
(def parse-claude-envelope @#'assess/parse-claude-envelope)
(def parse-claude-reply    @#'assess/parse-claude-reply)
(def claude-error-note     @#'assess/claude-error-note)
(def resolve-claude-opts   @#'assess/resolve-claude-opts)
(def compute-exit-code     @#'assess/compute-exit-code)
(def collect-config-errors @#'assess/collect-config-errors)
(def pass-when-valid?      @#'assess/pass-when-valid?)

;; ---------------------------------------------------------------------------
;; numeric-pred-pass?
;; ---------------------------------------------------------------------------

(deftest numeric-pred-pass?-test
  (testing "nil actual is always false"
    (is (false? (numeric-pred-pass? nil 0)))
    (is (false? (numeric-pred-pass? nil {:>= 4}))))

  (testing "bare number is equality"
    (is (numeric-pred-pass? 0 0))
    (is (numeric-pred-pass? 4 4))
    (is (not (numeric-pred-pass? 1 0)))
    (is (not (numeric-pred-pass? 0 1))))

  (testing ":= explicit equality"
    (is (numeric-pred-pass? 4 {:= 4}))
    (is (not (numeric-pred-pass? 3 {:= 4}))))

  (testing ":>= and :<="
    (is (numeric-pred-pass? 5 {:>= 4}))
    (is (numeric-pred-pass? 4 {:>= 4}))
    (is (not (numeric-pred-pass? 3 {:>= 4})))
    (is (numeric-pred-pass? 3 {:<= 4}))
    (is (numeric-pred-pass? 4 {:<= 4}))
    (is (not (numeric-pred-pass? 5 {:<= 4}))))

  (testing ":> and :< are strict"
    (is (numeric-pred-pass? 5 {:> 4}))
    (is (not (numeric-pred-pass? 4 {:> 4})))
    (is (numeric-pred-pass? 3 {:< 4}))
    (is (not (numeric-pred-pass? 4 {:< 4}))))

  (testing ":in matches any value in the set"
    (is (numeric-pred-pass? 0 {:in [0 1]}))
    (is (numeric-pred-pass? 1 {:in [0 1]}))
    (is (not (numeric-pred-pass? 2 {:in [0 1]})))
    (is (not (numeric-pred-pass? 0 {:in []}))))

  (testing "unsupported spec keys are rejected"
    (is (not (numeric-pred-pass? 5 {:bogus 4}))))

  (testing "non-numeric, non-map spec is rejected"
    (is (not (numeric-pred-pass? 5 "string")))
    (is (not (numeric-pred-pass? 5 [4])))))

;; ---------------------------------------------------------------------------
;; check-one-pred
;; ---------------------------------------------------------------------------

(deftest check-one-pred-test
  (testing ":exit-code predicate (numeric)"
    (is (true?  (check-one-pred :exit-code 0 {:exit-code 0})))
    (is (false? (check-one-pred :exit-code 0 {:exit-code 1})))
    (is (true?  (check-one-pred :exit-code {:in [0 1]} {:exit-code 1}))))

  (testing ":rating predicate (numeric)"
    (is (true?  (check-one-pred :rating {:>= 4} {:rating 5})))
    (is (false? (check-one-pred :rating {:>= 4} {:rating 3})))
    (is (false? (check-one-pred :rating {:>= 4} {})))
    (is (false? (check-one-pred :rating {:>= 4} {:rating nil}))))

  (testing ":stdout-matches"
    (is (true?  (check-one-pred :stdout-matches "FAIL" {:stdout "x FAIL y"})))
    (is (false? (check-one-pred :stdout-matches "FAIL" {:stdout "all good"})))
    (is (false? (check-one-pred :stdout-matches "FAIL" {:stdout nil})))
    (is (false? (check-one-pred :stdout-matches "FAIL" {}))))

  (testing ":stdout-not-matches"
    (is (true?  (check-one-pred :stdout-not-matches "FAIL" {:stdout "all good"})))
    (is (false? (check-one-pred :stdout-not-matches "FAIL" {:stdout "x FAIL y"})))
    (is (true?  (check-one-pred :stdout-not-matches "FAIL" {:stdout ""}))))

  (testing "unknown predicate key returns nil"
    (is (nil? (check-one-pred :unknown-key "anything" {})))))

;; ---------------------------------------------------------------------------
;; pass-when-satisfied?
;; ---------------------------------------------------------------------------

(deftest pass-when-satisfied?-test
  (testing "empty pass-when refuses to pass (no fabricated passes)"
    (is (not (pass-when-satisfied? {} {:exit-code 0})))
    (is (not (pass-when-satisfied? nil {:exit-code 0}))))

  (testing "single predicate"
    (is (pass-when-satisfied? {:exit-code 0} {:exit-code 0}))
    (is (not (pass-when-satisfied? {:exit-code 0} {:exit-code 1}))))

  (testing "multiple predicates AND-combine"
    (is (pass-when-satisfied? {:exit-code 0 :stdout-not-matches "FAIL"}
                              {:exit-code 0 :stdout "ok"}))
    (is (not (pass-when-satisfied? {:exit-code 0 :stdout-not-matches "FAIL"}
                                   {:exit-code 0 :stdout "ok FAIL"})))
    (is (not (pass-when-satisfied? {:exit-code 0 :stdout-not-matches "FAIL"}
                                   {:exit-code 1 :stdout "ok"}))))

  (testing "unknown predicate key fails the whole pass-when"
    (is (not (pass-when-satisfied? {:bogus 1 :exit-code 0}
                                   {:exit-code 0})))))

;; ---------------------------------------------------------------------------
;; parse-claude-envelope
;; ---------------------------------------------------------------------------

(deftest parse-claude-envelope-test
  (testing "valid envelope JSON is parsed"
    (let [out (parse-claude-envelope
                "{\"result\":\"hello\",\"is_error\":false}")]
      (is (= "hello" (get-in out [:envelope :result])))
      (is (= false   (get-in out [:envelope :is_error])))))

  (testing "malformed JSON flags :parse-failed?"
    (is (:parse-failed? (parse-claude-envelope "not json")))
    (is (:parse-failed? (parse-claude-envelope ""))))

  (testing "empty JSON object is valid"
    (is (= {} (get (parse-claude-envelope "{}") :envelope)))))

;; ---------------------------------------------------------------------------
;; parse-claude-reply
;; ---------------------------------------------------------------------------

(deftest parse-claude-reply-test
  (testing "envelope with valid inner JSON result returns the parsed map"
    (let [env {:is_error false
               :result "{\"rating\":4,\"reasoning\":\"ok\",\"confidence\":\"medium\"}"}]
      (is (= {:rating 4 :reasoning "ok" :confidence "medium"}
             (parse-claude-reply env)))))

  (testing "envelope reporting :is_error returns nil"
    (is (nil? (parse-claude-reply
                {:is_error true :result "Not logged in"}))))

  (testing "envelope with missing or empty :result returns nil"
    (is (nil? (parse-claude-reply {:is_error false})))
    (is (nil? (parse-claude-reply {:is_error false :result ""}))))

  (testing "envelope with malformed inner :result returns nil"
    (is (nil? (parse-claude-reply
                {:is_error false :result "not json"}))))

  (testing "nil envelope returns nil"
    (is (nil? (parse-claude-reply nil)))))

;; ---------------------------------------------------------------------------
;; claude-error-note
;; ---------------------------------------------------------------------------

(deftest claude-error-note-test
  (testing "parse-failed surfaces the parse failure with exit code"
    (let [note (claude-error-note {:exit 1 :stdout "garbage" :stderr ""}
                                  {:parse-failed? true})]
      (is (re-find #"not parseable JSON" note))
      (is (re-find #"exit 1" note))))

  (testing "envelope :is_error surfaces the model's error text"
    (let [note (claude-error-note
                 {:exit 1 :stdout "..." :stderr ""}
                 {:envelope {:is_error true
                             :result "Not logged in · Please run /login"}})]
      (is (re-find #"Not logged in" note))))

  (testing "non-zero exit (without :is_error) surfaces exit + stderr"
    (let [note (claude-error-note {:exit 7 :stdout "" :stderr "boom"}
                                  {:envelope {:is_error false}})]
      (is (re-find #"exited 7" note))
      (is (re-find #"boom"     note))))

  (testing "zero exit + valid envelope but the model's reply was off-schema"
    (let [note (claude-error-note {:exit 0 :stdout "..." :stderr ""}
                                  {:envelope {:is_error false}})]
      (is (re-find #"did not match the JSON schema" note)))))

;; ---------------------------------------------------------------------------
;; expand-tilde
;; ---------------------------------------------------------------------------

(deftest expand-tilde-test
  (testing "~ alone expands to $HOME"
    (is (= (System/getProperty "user.home")
           (expand-tilde "~"))))

  (testing "~/foo expands"
    (is (= (str (System/getProperty "user.home") "/foo")
           (expand-tilde "~/foo"))))

  (testing "non-tilde paths pass through"
    (is (= "/abs/path"     (expand-tilde "/abs/path")))
    (is (= "relative/path" (expand-tilde "relative/path"))))

  (testing "nil and non-strings pass through unchanged"
    (is (nil? (expand-tilde nil)))
    (is (= 42 (expand-tilde 42)))))

;; ---------------------------------------------------------------------------
;; resolve-claude-opts
;; ---------------------------------------------------------------------------

(deftest resolve-claude-opts-test
  (testing "defaults when both cfgs are empty"
    (let [opts (resolve-claude-opts {} {})]
      (is (= "claude" (:cmd opts)))
      (is (= 1        (:max-budget-usd opts)))
      (is (nil?       (:model opts)))))

  (testing "local cfg supplies values when project is silent"
    (let [opts (resolve-claude-opts
                 {}
                 {:claude/cmd "myclaude"
                  :claude/max-budget-usd 5
                  :claude/model "sonnet"})]
      (is (= "myclaude" (:cmd opts)))
      (is (= 5          (:max-budget-usd opts)))
      (is (= "sonnet"   (:model opts)))))

  (testing "project cfg beats local cfg"
    (let [opts (resolve-claude-opts
                 {:claude/cmd "project-claude"
                  :claude/max-budget-usd 10}
                 {:claude/cmd "local-claude"
                  :claude/max-budget-usd 1})]
      (is (= "project-claude" (:cmd opts)))
      (is (= 10               (:max-budget-usd opts)))))

  (testing "config-dir tilde expansion is applied"
    (let [opts (resolve-claude-opts
                 {:claude/config-dir "~/.claude-test"}
                 {})]
      (is (= (str (System/getProperty "user.home") "/.claude-test")
             (:config-dir opts)))))

  (testing "project EDN config-dir beats process env (and local)"
    ;; Java can't unset env at runtime, so we can't test "no value
    ;; anywhere" portably. Instead test the meaningful precedence: an
    ;; explicit project value wins regardless of what env or local say.
    (let [opts (resolve-claude-opts
                 {:claude/config-dir "/explicit/project/path"}
                 {:claude/config-dir "/local/path"})]
      (is (= "/explicit/project/path" (:config-dir opts))))))

;; ---------------------------------------------------------------------------
;; compute-exit-code
;; ---------------------------------------------------------------------------

(deftest compute-exit-code-test
  (testing "empty results: exit 0"
    (is (= 0 (compute-exit-code []))))

  (testing "all :required passing: exit 0"
    (is (= 0 (compute-exit-code
               [{:check/severity :required :result/status :pass}
                {:check/severity :required :result/status :pass}]))))

  (testing "any :required :fail: exit 1"
    (is (= 1 (compute-exit-code
               [{:check/severity :required :result/status :pass}
                {:check/severity :required :result/status :fail}]))))

  (testing "any :required :unknown: exit 1 (fail closed)"
    (is (= 1 (compute-exit-code
               [{:check/severity :required :result/status :pass}
                {:check/severity :required :result/status :unknown}]))))

  (testing ":recommended / :advisory failures do NOT gate exit"
    (is (= 0 (compute-exit-code
               [{:check/severity :required    :result/status :pass}
                {:check/severity :recommended :result/status :fail}
                {:check/severity :advisory    :result/status :fail}
                {:check/severity :recommended :result/status :unknown}]))))

  (testing ":n/a on a :required check is treated as not-gating"
    (is (= 0 (compute-exit-code
               [{:check/severity :required :result/status :n/a}])))))

;; ---------------------------------------------------------------------------
;; pass-when-valid?
;; ---------------------------------------------------------------------------

(deftest pass-when-valid?-test
  (testing "non-empty map is valid"
    (is (pass-when-valid? {:exit-code 0}))
    (is (pass-when-valid? {:rating {:>= 4} :confidence "high"})))

  (testing "nil, empty map, non-map are all invalid"
    (is (not (pass-when-valid? nil)))
    (is (not (pass-when-valid? {})))
    (is (not (pass-when-valid? [:exit-code 0])))
    (is (not (pass-when-valid? :exit-code)))
    (is (not (pass-when-valid? "0")))))

;; ---------------------------------------------------------------------------
;; collect-config-errors
;; ---------------------------------------------------------------------------

(defn- valid-deterministic [overrides]
  (merge {:check/id        :test-check
          :check/runner    :deterministic
          :check/severity  :advisory
          :check/command   "true"
          :check/pass-when {:exit-code 0}}
         overrides))

(deftest collect-config-errors-test
  (testing "valid minimal config returns empty error vector"
    (is (= [] (collect-config-errors
                {:project/name "p" :checks [(valid-deterministic {})]}))))

  (testing "missing :project/name"
    (let [errs (collect-config-errors {:checks []})]
      (is (some #(re-find #":project/name" %) errs))))

  (testing ":checks must be a vector"
    (let [errs (collect-config-errors {:project/name "p" :checks {}})]
      (is (some #(re-find #":checks to be a vector" %) errs))))

  (testing "missing :check/id"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(dissoc (valid-deterministic {}) :check/id)]})]
      (is (some #(re-find #"missing :check/id" %) errs))))

  (testing "unsupported :check/runner"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(valid-deterministic {:check/runner :bogus})]})]
      (is (some #(re-find #":check/runner must be one of" %) errs))))

  (testing "unsupported :check/severity"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(valid-deterministic {:check/severity :critical})]})]
      (is (some #(re-find #":check/severity must be one of" %) errs))))

  (testing ":deterministic check missing :command"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(dissoc (valid-deterministic {}) :check/command)]})]
      (is (some #(re-find #":deterministic check needs a string :check/command" %) errs))))

  (testing ":deterministic check missing :pass-when"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(dissoc (valid-deterministic {}) :check/pass-when)]})]
      (is (some #(re-find #":deterministic check needs a non-empty map :check/pass-when" %) errs))))

  (testing ":deterministic check with non-map :pass-when (the crash that triggered this)"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(valid-deterministic {:check/pass-when [:not-a-map]})]})]
      (is (some #(re-find #":deterministic check needs a non-empty map :check/pass-when" %) errs))))

  (testing ":deterministic check with empty-map :pass-when"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [(valid-deterministic {:check/pass-when {}})]})]
      (is (some #(re-find #":deterministic check needs a non-empty map :check/pass-when" %) errs))))

  (testing ":ai-assisted check missing :prompt-file (NPE before this fix)"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [{:check/id :ai :check/runner :ai-assisted
                            :check/severity :advisory
                            :check/pass-when {:rating {:>= 4}}}]})]
      (is (some #(re-find #":ai-assisted check needs a string :check/prompt-file" %) errs))))

  (testing ":ai-assisted check missing :pass-when"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [{:check/id :ai :check/runner :ai-assisted
                            :check/severity :advisory
                            :check/prompt-file "p.md"}]})]
      (is (some #(re-find #":ai-assisted check needs a non-empty map :check/pass-when" %) errs))))

  (testing ":human-rated check missing :sign-off-path"
    (let [errs (collect-config-errors
                 {:project/name "p"
                  :checks [{:check/id :hr :check/runner :human-rated
                            :check/severity :advisory}]})]
      (is (some #(re-find #":human-rated check needs a string :check/sign-off-path" %) errs))))

  (testing "valid :ai-assisted and :human-rated checks produce no errors"
    (is (= [] (collect-config-errors
                {:project/name "p"
                 :checks [{:check/id :ai :check/runner :ai-assisted
                           :check/severity :advisory
                           :check/prompt-file "p.md"
                           :check/pass-when {:rating {:>= 4}}}
                          {:check/id :hr :check/runner :human-rated
                           :check/severity :advisory
                           :check/sign-off-path "signoff.md"}]}))))

  (testing "multiple errors are all collected, not just the first"
    (let [errs (collect-config-errors
                 {:checks [(dissoc (valid-deterministic {}) :check/id :check/command :check/pass-when)]})]
      ;; Expect: missing :project/name, missing :check/id, missing :command, missing :pass-when
      (is (>= (count errs) 4)))))

;; ---------------------------------------------------------------------------
;; entry point
;; ---------------------------------------------------------------------------

(let [{:keys [fail error]} (run-tests 'assess-test)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
