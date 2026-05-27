#!/usr/bin/env bb
;; Code Quality Assessment Runner (v1)
;;
;; Reads an EDN config, runs each check, writes a markdown scorecard +
;; JSON results file to the reports directory.
;;
;;   ./assess.bb examples/lccjs.edn
;;   ./assess.bb examples/lccjs.edn --reports-dir /tmp/lccjs-reports
;;
;; Exit codes:
;;   0  All :required checks passed (warnings may be present)
;;   1  At least one :required check failed
;;   2  Config missing / invalid
;;
;; See schema/SCHEMA.edn for the config shape.

(ns assess
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str])
  (:import [java.time Instant]
           [java.time.format DateTimeFormatter]))

;; ---------------------------------------------------------------------------
;; Constants
;; ---------------------------------------------------------------------------

(def supported-runners    #{:deterministic :ai-assisted :human-rated})
(def supported-severities #{:required :recommended :advisory})

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- now-iso []
  (.format DateTimeFormatter/ISO_INSTANT (Instant/now)))

(defn- elapsed-ms [^Instant start]
  (- (.toEpochMilli (Instant/now)) (.toEpochMilli start)))

(defn- expand-tilde [path]
  (if (and (string? path) (str/starts-with? path "~"))
    (str (System/getProperty "user.home") (subs path 1))
    path))

(defn- die! [code msg]
  (binding [*out* *err*] (println msg))
  (System/exit code))

;; Resolved at script-load time so `local.edn` is found next to assess.bb
;; regardless of the caller's cwd. Falls back to "." if babashka.file
;; isn't set (e.g. running inside a REPL).
(def ^:private script-dir
  (or (some-> (System/getProperty "babashka.file") fs/absolutize fs/parent str)
      "."))

(defn- load-local-edn
  "Read local.edn from the script directory if present. Returns an empty
   map on missing/invalid file (warns on invalid). local.edn is gitignored
   per-machine config; see local.example.edn for the template."
  []
  (let [path (str script-dir "/local.edn")]
    (when (fs/exists? path)
      (try
        (edn/read-string (slurp path))
        (catch Exception e
          (binding [*out* *err*]
            (println (str "Warning: failed to parse " path ": " (.getMessage e))
                     "— continuing without local config"))
          {})))))

;; ---------------------------------------------------------------------------
;; Config loading & validation
;; ---------------------------------------------------------------------------

(defn- load-config [path]
  (when-not (fs/exists? path)
    (die! 2 (str "Error: config not found at " path)))
  (try
    (edn/read-string (slurp path))
    (catch Exception e
      (die! 2 (str "Error: failed to parse " path ": " (.getMessage e))))))

(defn- validate-config [config]
  (let [errs (volatile! [])]
    (when-not (:project/name config)
      (vswap! errs conj "missing :project/name"))
    (when-not (vector? (:checks config))
      (vswap! errs conj "expected :checks to be a vector"))
    (doseq [[idx c] (map-indexed vector (:checks config))]
      (let [pfx (str "checks[" idx "] (" (:check/id c "?") ")")]
        (when-not (:check/id c)
          (vswap! errs conj (str pfx ": missing :check/id")))
        (when-not (supported-runners (:check/runner c))
          (vswap! errs conj (str pfx ": :check/runner must be one of " supported-runners)))
        (when-not (supported-severities (:check/severity c))
          (vswap! errs conj (str pfx ": :check/severity must be one of " supported-severities)))))
    (when (seq @errs)
      (die! 2 (str "Config validation errors:\n  - "
                   (str/join "\n  - " @errs))))
    config))

;; ---------------------------------------------------------------------------
;; pass-when predicate DSL
;;
;;   {:exit-code 0}                  exit code equals 0
;;   {:exit-code {:in [0 1]}}        exit code in the set
;;   {:stdout-matches "regex"}       stdout contains a match
;;   {:stdout-not-matches "regex"}   stdout has no match
;;
;; All keys in the map are AND-combined.
;; ---------------------------------------------------------------------------

(defn- numeric-pred-pass?
  "Generic numeric comparator.
   spec may be: a number (=), or a map with :=, :>=, :<=, :>, :<, or :in."
  [actual spec]
  (cond
    (nil? actual)  false
    (number? spec) (== actual spec)
    (map? spec)    (cond
                     (contains? spec :=)  (== actual (:= spec))
                     (contains? spec :>=) (>= actual (:>= spec))
                     (contains? spec :<=) (<= actual (:<= spec))
                     (contains? spec :>)  (>  actual (:>  spec))
                     (contains? spec :<)  (<  actual (:<  spec))
                     (contains? spec :in) (boolean (some #{actual} (:in spec)))
                     :else false)
    :else false))

(defn- check-one-pred [k v outcome]
  (case k
    :exit-code          (numeric-pred-pass? (:exit-code outcome) v)
    :rating             (numeric-pred-pass? (:rating outcome) v)
    :stdout-matches     (boolean (re-find (re-pattern v) (or (:stdout outcome) "")))
    :stdout-not-matches (not (re-find (re-pattern v) (or (:stdout outcome) "")))
    ;; Unknown predicate keys are ignored in v1 (forward-compat); they
    ;; surface as :unknown via the runner if no key matches.
    nil))

(defn- pass-when-satisfied? [pass-when outcome]
  (and (seq pass-when)
       (every? (fn [[k v]]
                 (let [r (check-one-pred k v outcome)]
                   (true? r)))
               pass-when)))

;; ---------------------------------------------------------------------------
;; Evidence helpers
;; ---------------------------------------------------------------------------

(defn- default-evidence-path [ctx check ext]
  (str (:reports-dir ctx) "/" (name (:check/id check)) "." ext))

(defn- write-evidence! [path content]
  (when path
    (when-let [parent (fs/parent path)]
      (fs/create-dirs parent))
    (spit path content)))

;; ---------------------------------------------------------------------------
;; AI runner helpers
;; ---------------------------------------------------------------------------

(def ^:private ai-output-schema
  ;; v1: hardcoded JSON schema for AI-assisted check replies. The runner
  ;; passes this to `claude --json-schema` so the model's output is
  ;; strictly conformant. Per-check custom schemas can be added later via
  ;; :check/output-schema if needed.
  {:type "object"
   :properties
   {:rating     {:type "integer" :minimum 1 :maximum 5}
    :reasoning  {:type "string"}
    :evidence   {:type "array"
                 :items {:type "object"
                         :properties {:file    {:type "string"}
                                      :lines   {:type "string"}
                                      :concern {:type "string"}}
                         :required ["file" "lines" "concern"]}}
    :confidence {:type "string" :enum ["high" "medium" "low"]}}
   :required ["rating" "reasoning" "confidence"]})

(defn- claude-available? [cmd]
  (try (boolean (fs/which cmd)) (catch Exception _ false)))

(defn- resolve-claude-opts
  "Walk precedence to produce the resolved knobs used when invoking claude.
   Precedence (highest wins):
     1. Project EDN (e.g. examples/lccjs.edn)
     2. Process env (CLAUDE_CONFIG_DIR only — the others are EDN-only)
     3. local.edn (per-machine)
     4. Built-in default"
  [project-cfg local-cfg]
  (let [cfg-dir (or (:claude/config-dir project-cfg)
                    (System/getenv "CLAUDE_CONFIG_DIR")
                    (:claude/config-dir local-cfg))]
    {:cmd            (or (:claude/cmd project-cfg)
                         (:claude/cmd local-cfg)
                         "claude")
     :config-dir     (some-> cfg-dir expand-tilde)
     :max-budget-usd (or (:claude/max-budget-usd project-cfg)
                         (:claude/max-budget-usd local-cfg)
                         1)
     :model          (or (:claude/model project-cfg)
                         (:claude/model local-cfg))}))

(defn- claude-env
  "Build the subprocess env for invoking claude. Currently only sets
   CLAUDE_CONFIG_DIR, since that's the one env var the claude binary
   reads. Everything else is passed as CLI flags."
  [claude-opts]
  (cond-> {}
    (:config-dir claude-opts) (assoc "CLAUDE_CONFIG_DIR" (:config-dir claude-opts))))

(defn- substitute-inputs [tmpl inputs project-root]
  (let [block (str/join
                "\n\n"
                (map (fn [rel]
                       (let [full (str project-root "/" rel)]
                         (if (fs/exists? full)
                           (str "### `" rel "`\n\n```\n" (slurp full) "\n```")
                           (str "### `" rel "` — **MISSING**"))))
                     inputs))]
    (if (str/includes? tmpl "{{inputs}}")
      (str/replace tmpl "{{inputs}}" block)
      (str tmpl "\n\n## Inputs\n\n" block))))

(defn- invoke-claude
  "Run claude in -p (print) mode with strict JSON output. Reads :cmd,
   :max-budget-usd, :model, and :config-dir from claude-opts. The prompt
   is piped via stdin (no argv-size limits, no 'no stdin received'
   warning). Returns a map with either :timeout? true, or
   :exit/:stdout/:stderr."
  [prompt schema-json claude-opts timeout-ms]
  (let [{:keys [cmd model max-budget-usd]} claude-opts
        args (cond-> [cmd "-p"
                      "--bare"
                      "--no-session-persistence"
                      "--output-format" "json"
                      "--json-schema" schema-json
                      "--tools" ""
                      "--max-budget-usd" (str max-budget-usd)]
               model (into ["--model" model]))
        env  (claude-env claude-opts)
        proc (p/process args
                        {:in prompt :out :string :err :string :extra-env env})
        outcome (deref proc timeout-ms ::timeout)]
    (if (= outcome ::timeout)
      (do (try (.destroy ^Process (:proc proc)) (catch Exception _ nil))
          {:timeout? true})
      {:exit   (:exit outcome)
       :stdout (or (:out outcome) "")
       :stderr (or (:err outcome) "")})))

(defn- parse-claude-envelope
  "Parse the outer JSON envelope from `claude --output-format json`.
   Returns {:envelope <outer-map>} on success, {:parse-failed? true}
   otherwise. An empty/null stdout (cheshire parses '' to nil rather than
   throwing) is treated as a parse failure — claude shouldn't return 0
   with no envelope, and faking an empty {:envelope nil} would mask the
   problem downstream."
  [raw-stdout]
  (try
    (let [parsed (json/parse-string raw-stdout true)]
      (if (nil? parsed)
        {:parse-failed? true}
        {:envelope parsed}))
    (catch Exception _ {:parse-failed? true})))

(defn- parse-claude-reply
  "Given the outer envelope, parse the :result field as JSON (the model's
   schema-conformant reply). Returns nil if the envelope reports an error
   or :result is not parseable JSON."
  [envelope]
  (when (and envelope (not (:is_error envelope)))
    (try
      (let [inner (or (:result envelope) "")]
        (when (seq inner)
          (json/parse-string inner true)))
      (catch Exception _ nil))))

(defn- claude-error-note
  "Build a specific note when the claude invocation didn't yield a usable
   reply: auth/setup errors, non-zero exit, malformed envelope, etc."
  [{:keys [exit stdout stderr]} {:keys [envelope parse-failed?]}]
  (cond
    parse-failed?
    (str "claude stdout was not parseable JSON (exit " exit ")"
         (when (seq stderr) (str "; stderr: " (str/trim stderr))))

    (:is_error envelope)
    (str "claude reported error: "
         (str/trim (or (:result envelope) "(no detail)")))

    (not= 0 exit)
    (str "claude exited " exit
         (when (seq stderr) (str "; stderr: " (str/trim stderr))))

    :else
    "claude returned 0 but the model's reply did not match the JSON schema"))

;; ---------------------------------------------------------------------------
;; Runners
;; ---------------------------------------------------------------------------

(defmulti run-check
  "Dispatch on :check/runner. Returns a :result/* map (merged into the
   check identity by run-all-checks)."
  (fn [check _ctx] (:check/runner check)))

(defmethod run-check :deterministic
  [check ctx]
  (let [{:check/keys [command cwd pass-when evidence-path]} check
        started (Instant/now)
        cwd*    (expand-tilde (or cwd (:project-root ctx) "."))
        ev-path (or evidence-path (default-evidence-path ctx check "log"))
        outcome (try
                  (let [{:keys [exit out err]}
                        @(p/process ["sh" "-c" command]
                                    {:dir cwd* :out :string :err :string})]
                    {:exit-code exit :stdout (or out "") :stderr (or err "")})
                  (catch Exception e
                    {:exit-code -1 :stdout "" :stderr (.getMessage e) :error? true}))]
    (write-evidence!
      ev-path
      (str "$ " command "\n(cwd: " cwd* ")\n"
           "--- exit: " (:exit-code outcome) " ---\n"
           "--- stdout ---\n" (:stdout outcome)
           "\n--- stderr ---\n" (:stderr outcome) "\n"))
    {:result/status      (cond
                           (:error? outcome) :unknown
                           (pass-when-satisfied? pass-when outcome) :pass
                           :else :fail)
     :result/confidence  (if (:error? outcome) :low :high)
     :result/observed    {:exit-code   (:exit-code outcome)
                          :stdout-bytes (count (:stdout outcome))
                          :stderr-bytes (count (:stderr outcome))}
     :result/evidence-path ev-path
     :result/duration-ms   (elapsed-ms started)
     :result/timestamp     (now-iso)}))

(defn- ai-skipped
  "Build an :unknown result with the given note. Used when the AI runner
   can't proceed (missing prompt file, claude not on PATH, etc.)."
  [check ctx ev-path note started]
  (write-evidence!
    ev-path
    (str "# " (name (:check/id check)) " — skipped\n\n" note "\n"))
  {:result/status        :unknown
   :result/confidence    :low
   :result/observed      {:note note}
   :result/evidence-path ev-path
   :result/duration-ms   (elapsed-ms started)
   :result/timestamp     (now-iso)})

(defmethod run-check :ai-assisted
  [check ctx]
  (let [{:check/keys [id prompt-file inputs pass-when evidence-path timeout-ms]} check
        started      (Instant/now)
        project-root (expand-tilde (or (:project-root ctx) "."))
        ev-path      (or evidence-path (default-evidence-path ctx check "md"))
        timeout-ms   (or timeout-ms 180000)]
    (cond
      (or (nil? prompt-file) (not (fs/exists? prompt-file)))
      (ai-skipped check ctx ev-path
                  (str "Prompt file not found: `" prompt-file "`") started)

      (not (claude-available? (:cmd (:claude-opts ctx))))
      (ai-skipped check ctx ev-path
                  (str "`" (:cmd (:claude-opts ctx)) "` CLI is not on PATH. "
                       "Install Claude Code (or set :claude/cmd in local.edn) to enable AI-assisted checks.")
                  started)

      :else
      (let [tmpl        (slurp prompt-file)
            full-prompt (substitute-inputs tmpl (or inputs []) project-root)
            schema-json (json/generate-string ai-output-schema)
            inv         (invoke-claude full-prompt schema-json (:claude-opts ctx) timeout-ms)
            env-result  (when-not (:timeout? inv) (parse-claude-envelope (:stdout inv)))
            parsed      (parse-claude-reply (:envelope env-result))
            usable?     (and (not (:timeout? inv))
                             (= 0 (:exit inv -1))
                             parsed
                             (some? (:rating parsed)))
            status      (cond
                          (:timeout? inv) :unknown
                          (not usable?)   :unknown
                          (pass-when-satisfied? pass-when parsed) :pass
                          :else           :fail)
            note        (cond
                          (:timeout? inv) (str "timed out after " timeout-ms " ms")
                          (not usable?)   (claude-error-note inv env-result))
            model-conf  (some-> (:confidence parsed) keyword)
            confidence  (cond
                          (= :unknown status) :low
                          model-conf          model-conf
                          :else               :medium)]
        (write-evidence!
          ev-path
          (str "# AI-assisted check: " (name id) "\n\n"
               "- **Status:** " (name status) "\n"
               "- **Confidence:** " (name confidence) "\n"
               "- **Timeout hit:** " (if (:timeout? inv) "yes" "no") "\n"
               "- **Claude exit:** " (:exit inv "n/a") "\n"
               (when note (str "- **Note:** " note "\n"))
               "\n"
               (when parsed
                 (str "## Parsed reply\n\n```json\n"
                      (json/generate-string parsed {:pretty true})
                      "\n```\n\n"))
               "## Raw claude stdout\n\n```\n" (:stdout inv) "\n```\n\n"
               (when (seq (:stderr inv ""))
                 (str "## Claude stderr\n\n```\n" (:stderr inv) "\n```\n\n"))
               "## Prompt sent\n\n```\n" full-prompt "\n```\n"))
        {:result/status        status
         :result/confidence    confidence
         :result/observed      (cond-> (or parsed {})
                                 (:timeout? inv) (assoc :timeout? true)
                                 note            (assoc :note note))
         :result/evidence-path ev-path
         :result/duration-ms   (elapsed-ms started)
         :result/timestamp     (now-iso)}))))

(defmethod run-check :human-rated
  [check ctx]
  ;; v1: implemented as a file-presence + freshness check. :pass iff the
  ;; sign-off file exists and (if :check/max-age-days is given) was
  ;; modified within that window.
  (let [{:check/keys [sign-off-path max-age-days]} check
        started       (Instant/now)
        project-root  (expand-tilde (or (:project-root ctx) "."))
        full          (when sign-off-path (str project-root "/" sign-off-path))
        exists?       (and full (fs/exists? full))
        age-days      (when exists?
                        (let [last-mod (.toMillis (fs/last-modified-time full))
                              now-ms   (System/currentTimeMillis)]
                          (double (/ (- now-ms last-mod) 1000 60 60 24))))
        fresh?        (or (nil? max-age-days)
                          (and age-days (<= age-days max-age-days)))
        status        (cond (not exists?) :unknown
                            fresh?        :pass
                            :else         :fail)]
    {:result/status        status
     :result/confidence    (if exists? :medium :low)
     :result/observed      {:sign-off-path sign-off-path
                            :exists?       (boolean exists?)
                            :age-days      age-days
                            :max-age-days  max-age-days}
     :result/evidence-path sign-off-path
     :result/duration-ms   (elapsed-ms started)
     :result/timestamp     (now-iso)}))

(defmethod run-check :default
  [check _ctx]
  {:result/status :unknown
   :result/confidence :low
   :result/observed {:note (str "unsupported :check/runner " (:check/runner check))}
   :result/duration-ms 0
   :result/timestamp (now-iso)})

;; ---------------------------------------------------------------------------
;; Aggregation
;; ---------------------------------------------------------------------------

(def ^:private result-keys
  [:check/id :check/concern :check/level :check/severity :check/runner :check/description])

(defn- run-all-checks [config ctx]
  (mapv (fn [check]
          (merge (select-keys check result-keys) (run-check check ctx)))
        (:checks config)))

(defn- compute-exit-code [results]
  (if (some (fn [r] (and (= :required (:check/severity r))
                         (= :fail (:result/status r))))
            results)
    1 0))

;; ---------------------------------------------------------------------------
;; Report rendering
;; ---------------------------------------------------------------------------

(def ^:private status-label
  {:pass "PASS" :fail "FAIL" :unknown "UNK" :n/a "N/A"})

(defn- concern-verdict [rows]
  (cond
    (some #(and (= :required (:check/severity %))
                (= :fail (:result/status %))) rows) "FAIL"
    (some #(= :fail (:result/status %)) rows) "WARN"
    (every? #(= :pass (:result/status %)) rows) "PASS"
    :else "MIXED"))

(defn- render-markdown [config results ctx]
  (let [by-concern (sort-by key (group-by :check/concern results))]
    (with-out-str
      (println (str "# Code Quality Assessment — " (:project/name config)))
      (println)
      (println (str "- **Started:** " (:started-at ctx)))
      (println (str "- **Duration:** " (elapsed-ms (:started-at-inst ctx)) " ms"))
      (println (str "- **Config:** `" (:config-path ctx) "`"))
      (println (str "- **Checks:** " (count results)))
      (println)
      (println "## Summary")
      (println)
      (println "| Concern | Level | Pass | Fail | Unknown | Verdict |")
      (println "|---|---|---|---|---|---|")
      (doseq [[concern rows] by-concern]
        (let [tally (frequencies (map :result/status rows))
              level (-> rows first :check/level (or :?))]
          (println (str "| " (name (or concern :uncategorized))
                        " | " (name level)
                        " | " (get tally :pass 0)
                        " | " (get tally :fail 0)
                        " | " (get tally :unknown 0)
                        " | " (concern-verdict rows)
                        " |"))))
      (println)
      ;; Required failures
      (let [req-fails (filter #(and (= :required (:check/severity %))
                                    (= :fail (:result/status %))) results)]
        (when (seq req-fails)
          (println "## Required failures")
          (println)
          (doseq [r req-fails]
            (println (str "- **[" (name (:check/concern r)) "] "
                          (name (:check/id r)) "** — "
                          (:check/description r)))
            (when-let [ev (:result/evidence-path r)]
              (println (str "  - Evidence: `" ev "`"))))
          (println)))
      ;; Recommended warnings
      (let [warns (filter #(and (= :recommended (:check/severity %))
                                (= :fail (:result/status %))) results)]
        (when (seq warns)
          (println "## Recommended warnings")
          (println)
          (doseq [r warns]
            (println (str "- [" (name (:check/concern r)) "] "
                          (name (:check/id r)) " — "
                          (:check/description r))))
          (println)))
      ;; Unknowns (Casey's rule: surface, do not silently coerce to pass/fail)
      (let [unks (filter #(= :unknown (:result/status %)) results)]
        (when (seq unks)
          (println "## Unknown (no fake numbers)")
          (println)
          (doseq [r unks]
            (let [note (get-in r [:result/observed :note]
                                (str "no signal for " (name (:check/id r))))]
              (println (str "- [" (name (:check/concern r)) "] "
                            (name (:check/id r)) " — " note))))
          (println)))
      (println "## All checks")
      (println)
      (println "| ID | Concern | Severity | Runner | Status | Conf | Evidence |")
      (println "|---|---|---|---|---|---|---|")
      (doseq [r results]
        (println (str "| " (name (:check/id r))
                      " | " (name (or (:check/concern r) :uncategorized))
                      " | " (name (:check/severity r))
                      " | " (name (:check/runner r))
                      " | " (get status-label (:result/status r) "?")
                      " | " (name (or (:result/confidence r) :unknown))
                      " | " (if-let [e (:result/evidence-path r)]
                              (str "`" e "`") "—")
                      " |"))))))

(defn- write-reports! [config results ctx]
  (let [dir       (:reports-dir ctx)
        md-path   (str dir "/report.md")
        json-path (str dir "/results.json")]
    (fs/create-dirs dir)
    (spit md-path (render-markdown config results ctx))
    (spit json-path
          (json/generate-string {:project     (:project/name config)
                                 :started-at  (:started-at ctx)
                                 :duration-ms (elapsed-ms (:started-at-inst ctx))
                                 :results     results}
                                {:pretty true}))
    {:md md-path :json json-path}))

;; ---------------------------------------------------------------------------
;; CLI
;; ---------------------------------------------------------------------------

(defn- print-help []
  (println "Usage: assess.bb <config.edn> [--reports-dir DIR]")
  (println)
  (println "Loads the EDN config, runs each check, writes a markdown scorecard")
  (println "and a JSON results file to the reports directory.")
  (println)
  (println "Exit codes:")
  (println "  0  all :required checks passed")
  (println "  1  at least one :required check failed")
  (println "  2  config missing or invalid"))

(defn- parse-args [args]
  (loop [xs args, out {}]
    (cond
      (empty? xs) out
      (#{"-h" "--help"} (first xs)) (do (print-help) (System/exit 0))
      (= "--reports-dir" (first xs)) (recur (drop 2 xs) (assoc out :reports-dir (second xs)))
      :else (recur (rest xs) (assoc out :config (first xs))))))

(defn -main [& args]
  (let [{:keys [config reports-dir]} (parse-args args)]
    (when-not config (print-help) (System/exit 2))
    (let [cfg          (validate-config (load-config config))
          local-cfg    (load-local-edn)
          claude-opts  (resolve-claude-opts cfg local-cfg)
          started-inst (Instant/now)
          reports-dir  (or reports-dir
                           (:reports/dir cfg)
                           (str "reports/" (:project/name cfg)))
          ctx          {:config          cfg
                        :local-config    local-cfg
                        :claude-opts     claude-opts
                        :project-root    (:project/path cfg)
                        :reports-dir     reports-dir
                        :config-path     config
                        :started-at      (now-iso)
                        :started-at-inst started-inst}
          results      (run-all-checks cfg ctx)
          {:keys [md json]} (write-reports! cfg results ctx)]
      (println (slurp md))
      (println (str "→ " md))
      (println (str "→ " json))
      (System/exit (compute-exit-code results)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
