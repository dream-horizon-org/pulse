# Pulse Causal Revenue Impact Engine — Implementation Documentation

> **Version**: 2.0 (modular)
> **Date**: March 2026
> **Authors**: Chirag Sharma (Pulse), with AI-assisted design and review
> **Package**: `pulse_ai/causal/` (~3,000 lines, 10 modules)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [The Problem We Solve](#2-the-problem-we-solve)
3. [Theoretical Foundation](#3-theoretical-foundation)
4. [Architecture Overview](#4-architecture-overview)
5. [Module-by-Module Design](#5-module-by-module-design)
   - 5.1 [config.py — Configuration](#51-configpy--configuration)
   - 5.2 [models.py — Data Models](#52-modelspy--data-models)
   - 5.3 [data.py — ClickHouse Data Extraction](#53-datapy--clickhouse-data-extraction)
   - 5.4 [matching.py — Propensity Score Matching Engine](#54-matchingpy--propensity-score-matching-engine)
   - 5.5 [analysis.py — Journey-Conditioned Causal Analysis](#55-analysispy--journey-conditioned-causal-analysis)
   - 5.6 [frustration.py — Composite Frustration Scoring](#56-frustrationpy--composite-frustration-scoring)
   - 5.7 [mining.py — Screen-Graph Process Mining](#57-miningpy--screen-graph-process-mining)
   - 5.8 [report.py — Revenue Impact Reporting](#58-reportpy--revenue-impact-reporting)
   - 5.9 [benchmark.py — Statistical Validation Suite](#59-benchmarkpy--statistical-validation-suite)
6. [Critical Design Decisions and Their Reasoning](#6-critical-design-decisions-and-their-reasoning)
7. [Evolution: From v1 to v2 to Modular Package](#7-evolution-from-v1-to-v2-to-modular-package)
8. [Validation Results](#8-validation-results)
9. [PB-Scale Architecture](#9-pb-scale-architecture)
10. [Known Limitations and Assumptions](#10-known-limitations-and-assumptions)
11. [Streaming Architecture (Future)](#11-streaming-architecture-future)
12. [Academic References](#12-academic-references)

---

## 1. Executive Summary

The Pulse Causal Revenue Impact Engine answers a question no existing DEM (Digital Experience Monitoring) platform answers automatically:

> **"How much revenue is this technical issue actually costing us?"**

It does this entirely from auto-instrumented telemetry — no custom business events, no host-app code changes, no manual funnel definitions. The engine:

1. **Auto-discovers conversion signals** from network operation names (GraphQL mutations containing "payment", "order", "subscribe", etc.)
2. **Builds a screen-transition graph** via process mining to understand user journeys
3. **Conditions analysis on journey stage** — only compares sessions that reached the SAME screen
4. **Enforces temporal ordering** — conversion must happen AFTER the issue, not just "ever in session"
5. **Uses propensity score matching** with device context to isolate the causal effect of each technical issue
6. **Reports revenue impact** with bootstrap confidence intervals and FDR-corrected significance

The result is a prioritized list: "Crash X on screen Y reduces conversion by Z% (95% CI: [a, b]) — estimated N lost conversions per week."

### Why This Matters

Every mobile observability tool tells you "you had 500 crashes yesterday." None of them tell you "those 500 crashes cost you $47,000 in revenue." Pulse does.

The causal framing is essential. Correlation-based approaches (sessions with crashes convert less) conflate causation with selection — users who crash on a payment screen are already high-intent users. Without causal methodology, you get the wrong answer AND the wrong direction.

---

## 2. The Problem We Solve

### 2.1 The Revenue Leakage Gap

Mobile apps lose revenue to technical issues — crashes, ANRs, jank, network errors — but quantifying this loss is surprisingly hard:

- **Crash dashboards** show crash counts and rates, but not business impact
- **Analytics platforms** require manual instrumentation of conversion events
- **A/B testing platforms** can measure impact of changes but not existing bugs
- **DEM tools** (Datadog RUM, Dynatrace, New Relic) show performance metrics but don't connect them to revenue

The gap: nobody answers "which crash should I fix first to recover the most revenue?"

### 2.2 Why Naive Approaches Fail

**Approach 1: Correlation ("sessions with crashes convert less")**
- Problem: confounded by user behavior. Power users who browse 50 screens are more likely to BOTH hit a rare crash AND convert. The correlation goes the wrong direction.

**Approach 2: Pre/Post comparison ("conversion dropped after this bug shipped")**
- Problem: confounded by time. Seasonality, marketing campaigns, and feature launches all affect conversion simultaneously.

**Approach 3: A/B test ("ship a fix and measure")**
- Problem: requires fixing every bug before measuring impact. Impractical for prioritization — you need to know impact BEFORE deciding what to fix.

### 2.3 Our Approach: Observational Causal Inference

We use **propensity score matching** — a technique from epidemiology and economics for estimating causal effects from observational (non-experimental) data. The key insight:

> If we can find pairs of sessions that are identical in every observable way (same device, OS, app version, network, geography, time of day) except that one experienced a crash and the other didn't, then the difference in their conversion rates is a causal estimate of the crash's impact.

This is the same methodology used by:
- Epidemiologists comparing drug effectiveness from patient records
- Economists measuring the effect of job training programs
- Tech companies (Uber, Netflix, Microsoft) for causal measurement from observational data

---

## 3. Theoretical Foundation

### 3.1 The Rubin Causal Model (Potential Outcomes Framework)

We adopt the Rubin Causal Model (RCM), also known as the potential outcomes framework:

- Each session `i` has two potential outcomes:
  - `Y_i(1)`: conversion outcome if the session experiences the issue (treated)
  - `Y_i(0)`: conversion outcome if the session does NOT experience the issue (control)
- The individual causal effect is `Y_i(0) - Y_i(1)` (positive = issue reduces conversion)
- We can only observe ONE potential outcome per session (the "fundamental problem of causal inference")

**Our estimand: Average Treatment Effect on the Treated (ATT)**

```
ATT = E[Y(0) - Y(1) | T = 1]
```

We estimate: "Among sessions that DID experience the issue, what would their conversion rate have been if they hadn't?" This is more relevant than ATE (Average Treatment Effect) because we care about the impact on actually-affected users.

**Reference**: Rubin, D.B. (1974). "Estimating Causal Effects of Treatments in Randomized and Nonrandomized Studies." *Journal of Educational Psychology*, 66(5), 688-701.

### 3.2 Propensity Score Matching

The **propensity score** is the probability of receiving treatment (experiencing the issue) given observed covariates:

```
e(X) = P(T = 1 | X)
```

**Rosenbaum & Rubin's theorem (1983)**: If treatment assignment is strongly ignorable given `X`, then it is also strongly ignorable given `e(X)`. This means we can match on a single scalar (the propensity score) instead of all covariates simultaneously — solving the curse of dimensionality.

**Strong ignorability assumption**:

```
{Y(0), Y(1)} ⊥ T | X
```

Translation: conditional on observed covariates X, treatment assignment is independent of potential outcomes. In our context: given the same device model, OS version, app version, network, and geography, whether a session crashes is independent of its inherent tendency to convert.

This assumption is plausible for technical issues (crashes are largely random given device context) but less plausible for user-behavior-driven issues.

**Reference**: Rosenbaum, P.R. & Rubin, D.B. (1983). "The Central Role of the Propensity Score in Observational Studies for Causal Effects." *Biometrika*, 70(1), 41-55.

### 3.3 Journey Conditioning (Our Key Innovation)

Standard PSM compares ALL affected sessions vs ALL control sessions. This fails for **late-funnel issues** due to journey-stage confounding:

**Example**: A crash on the PaymentListing screen
- Affected sessions: 63 users who reached PaymentListing and crashed
- Control (naive): 112 random sessions, most of which never went near payment
- Result: Affected sessions show 93.7% conversion vs 8.9% for control → **crash appears to HELP conversion by 84.7%**

This is wrong because it confuses "users who reached the payment screen" (high-intent) with "users who crashed" (the treatment effect).

**Our fix: Journey conditioning**

Instead of comparing all sessions, we condition on reaching the SAME screen:

1. Filter to sessions that reached PaymentListing (150 sessions)
2. Split: 63 crashed on that screen, 87 did not
3. Measure conversion AFTER reaching the screen
4. Run PSM within this filtered population

Result: Crash reduces conversion from 78% to 12% → **crash blocks 66% of payments** (correct)

This is related to the literature on **conditioning on intermediate outcomes** and avoiding **collider bias**. By conditioning on the screen visit (an intermediate event), we remove the selection bias but must be careful not to introduce new bias — which we avoid by only conditioning on a pre-treatment variable (the session reached the screen before the crash occurred).

**Reference**: Heckman, J.J. (1979). "Sample Selection Bias as a Specification Error." *Econometrica*, 47(1), 153-161.

### 3.4 Temporal Ordering

A conversion event only "counts" if it happens AFTER the issue. Without temporal ordering:

- Session: start → browse → crash on PaymentScreen → recover → complete payment → end
- Naive: "session had crash AND converted" → crash looks harmless
- Correct: "session crashed at T=10, conversion at T=15" → conversion happened after crash (maybe they retried)

We enforce this with event-level timestamps:
```python
converted_after_issue = any(conv_ts > issue_ts for conv_ts in session_conversions)
```

For control sessions (no issue), we use their arrival timestamp at the issue screen as the reference point — "did they convert after reaching this screen?"

---

## 4. Architecture Overview

### 4.1 Module Structure

```
pulse_ai/causal/
├── __init__.py       — Package exports
├── config.py         — All tunables (zero magic numbers)
├── models.py         — Typed data containers
├── data.py           — ClickHouse extraction (MV-aware, temporal)
├── matching.py       — PSM engine (encoding, matching, bootstrap CI)
├── analysis.py       — Journey-conditioned causal orchestrator
├── frustration.py    — Composite session quality scoring
├── mining.py         — Screen-graph process mining
├── report.py         — Formatted revenue impact reports
└── benchmark.py      — 30-scenario statistical validation suite
```

### 4.2 Pipeline Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. DATA EXTRACTION (data.py)                                 │
│    ClickHouse → session profiles, screen visits,             │
│    issue events, conversion events, jank, log signals        │
│    [MV mode for scale, legacy for dev]                       │
│    [Deterministic sampling for PB scale]                     │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│ 2. CONVERSION PROXY DISCOVERY (data.py)                      │
│    Scan GraphQL operation names → find payment/order signals │
│    No host-app instrumentation needed                        │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│ 3. FRUSTRATION SCORING (frustration.py)                      │
│    Percentile-rank weighted composite score (0-100)          │
│    Optionally calibrated via logistic regression             │
│    NEVER used as PSM matching feature                        │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│ 4. PROCESS MINING (mining.py)                                │
│    Build directed screen graph → discover conversion paths   │
│    → identify drop-off points                                │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│ 5. CAUSAL ANALYSIS (analysis.py → matching.py)               │
│    For each (issue_type, screen_name):                       │
│      a. Filter to sessions reaching that screen              │
│      b. Split affected vs control                            │
│      c. Temporal conversion check                            │
│      d. Propensity score matching (device context only)      │
│      e. BCa bootstrap confidence intervals                   │
│    Apply Benjamini-Hochberg FDR correction                   │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│ 6. REPORTING (report.py)                                     │
│    Ranked revenue impact table, detailed findings,           │
│    screen graph visualization, match quality diagnostics     │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 Data Dependencies

```
ClickHouse Tables:
  otel_traces (spans)          → session profiles, screen visits,
                                  conversion events, network operations
  otel_logs (log records)      → jank events, clicks, lifecycle signals
  stack_trace_events           → crash/ANR/non-fatal events

Materialized Views (PB scale):
  causal_session_profiles      → replaces session profile scan
  causal_screen_visits         → replaces screen visit scan
  causal_network_operations    → replaces operation name extraction
  causal_conversion_events     → replaces conversion event scan
  causal_jank_by_screen        → replaces jank event scan
  causal_log_signals           → replaces log signal aggregation
```

---

## 5. Module-by-Module Design

### 5.1 `config.py` — Configuration

**Design principle**: Zero magic numbers. Every threshold, parameter, and tunable is centralized in a single `CausalConfig` dataclass.

```python
@dataclass
class CausalConfig:
    # PSM matching
    k_neighbors: int = 5              # matched controls per treated unit
    caliper_sd: float = 0.2           # max propensity distance (in SD units)
    caliper_relax_multipliers: tuple = (2.0, 4.0, 8.0)

    # Statistical thresholds
    n_bootstrap: int = 2000           # bootstrap iterations (≥2000 for BCa)
    alpha: float = 0.05               # significance level
    min_affected: int = 10            # minimum treated sessions
    min_control: int = 10             # minimum control sessions
    common_support_trim: float = 0.05 # positivity trim percentage

    # Feature encoding
    max_onehot_cardinality: int = 20  # above → frequency encoding
    matching_features: list           # device context ONLY
    cyclical_features: dict           # session_hour → 24h period
```

**Key reasoning**:

- **`k_neighbors = 5`**: Standard in the PSM literature. k=1 is noisier (single match), k>10 introduces more bias from distant matches. Austin (2011) recommends k=1-5 with caliper.

- **`caliper_sd = 0.2`**: Following Austin (2011) who showed 0.2 SD of the logit of the propensity score removes approximately 98% of bias. We use 0.2 SD of the propensity score directly (a common simplification).

- **`n_bootstrap = 2000`**: The BCa method requires sufficient bootstrap replications for stable acceleration parameter estimation. Efron (1987) recommends ≥1000; we use 2000 for robustness.

- **`min_affected = 10`**: Below 10 treated units, PSM estimates are unreliable. The propensity model can't learn meaningful scores, and bootstrap CIs are too wide to be useful.

- **`common_support_trim = 0.05`**: Trim the 5% tails of each group's propensity distribution. This removes units with extreme propensity scores where the overlap assumption is weakest.

- **`matching_features`**: Only device context (device_model, os_version, app_version, network_provider, geo_country). Crucially excludes `unique_screens` and `frustration_score` — see Section 6.1.

- **`cyclical_features = {"session_hour": 24}`**: Time-of-day is cyclical (hour 23 is close to hour 0). We encode as sine/cosine to preserve this geometry.

### 5.2 `models.py` — Data Models

Three typed dataclasses representing analysis outputs:

**`IssueAnalysis`** — Result of a single journey-conditioned causal test:
- Issue identification (type, label, screen)
- Journey context (funnel_stage, sessions_reaching_screen)
- Causal estimate (delta, CI, significance, p-value)
- Match quality diagnostics (PS balance, caliper status, common support %)
- Prioritization (priority_score = |delta| × affected × significance_weight)
- Actionability (user count, exception detail)

**`ConversionProxy`** — Auto-discovered conversion signal:
- proxy_type: graphql_conversion, graphql_engagement, url_conversion, session_depth
- identifier: the GraphQL operation name (e.g., "GET validateEntitlement")
- conversion_rate: fraction of sessions reaching this operation

**`DropoffEdge`** — Screen graph drop-off point:
- from_screen, on_path_count, off_path_count, dropoff_rate
- top_destinations: where users go instead of continuing the conversion path

### 5.3 `data.py` — ClickHouse Data Extraction

**Dual-mode architecture** for dev/production flexibility:

| Mode | Reads From | Suitable For | Query Pattern |
|------|-----------|-------------|---------------|
| MV mode | Pre-aggregated materialized views | Production (>1M sessions) | Direct reads, no GROUP BY |
| Legacy mode | Raw event tables | Development (<100K sessions) | Full table scan + GROUP BY |

Auto-detection: queries `EXISTS TABLE otel.causal_session_profiles` to choose mode.

**7 extraction functions**, each with MV + legacy variants:

1. **`get_session_profiles()`** — Session-level device context
   - Returns: session_id, user_id, device_model, os_version, app_version, platform, geo_country, network_provider, session_hour, session_start/end, duration, unique_screens, network counts
   - **Critical note**: `unique_screens` and `net_error_count` are for reporting only, NEVER included in matching features

2. **`get_screen_visits()`** — Per-session screen visits with timestamps
   - Returns: session_id, screen_name, first_visit_ts
   - Enables journey conditioning (filter to sessions reaching a specific screen)
   - Ordered by (session_id, first_visit_ts) for temporal analysis

3. **`get_issue_events()`** — Individual crash/ANR/non-fatal events
   - Reads from `stack_trace_events` (always direct — table is small)
   - Returns: session_id, pulse_type, screen_name, issue_timestamp, exception_type
   - Event-level timestamps enable temporal ordering

4. **`get_conversion_events()`** — Individual conversion events
   - Filtered by the auto-discovered operation name
   - Returns: session_id, conversion_timestamp
   - Multiple conversion events per session supported (list of timestamps)

5. **`get_jank_events_by_screen()`** — Jank events per screen
   - Groups by (session_id, pulse_type, screen_name) with min timestamp
   - Enables per-screen jank analysis (same journey conditioning as crashes)

6. **`get_log_signals()`** — Aggregated log signals for frustration scoring
   - Counts: jank_slow, jank_frozen, clicks, network_changes per session
   - Used in frustration scoring (reporting), NOT in matching

7. **`discover_conversion_proxies()`** — GraphQL operation scanning
   - Scans all network operations for keyword matches
   - Conversion keywords: payment, purchase, order, checkout, subscribe, entitlement, transaction, billing, cart, buy, redeem, coupon, promo, reward
   - Engagement keywords: watchlist, follow, preference, notification, profile, review, feedback, share, invite
   - Ranks by (type_priority, -sessions_reached)

**PB-scale optimizations** (see Section 9):

- **Deterministic sampling**: `ORDER BY cityHash64(SessionId) LIMIT N` gives reproducible, uniform sample
- **Session-scoped downstream queries**: After sampling session profiles, all subsequent queries add `AND SessionId IN (sampled_ids)` — avoids scanning events for non-sampled sessions

### 5.4 `matching.py` — Propensity Score Matching Engine

The statistical core. Implements PSM with every fix identified through two rounds of critical review.

#### Feature Encoding (`encode_features`)

**Problem solved**: v1 used `sklearn.LabelEncoder` which creates ordinal numbers (Chrome=0, Firefox=1, Safari=2). This implies Chrome < Firefox < Safari to the logistic regression model — a meaningless ordering that biases propensity scores.

**Our encoding strategy**:

| Feature Type | Example | Encoding | Reasoning |
|-------------|---------|----------|-----------|
| Nominal categorical (≤20 unique) | device_model (10 values) | One-hot (drop first) | No ordinal assumption; each category gets its own coefficient |
| High-cardinality categorical (>20) | device_model (50+ values) | Frequency encoding | One-hot with 50+ columns is sparse and degrades logistic regression; frequency preserves "popularity" signal |
| Cyclical numeric | session_hour (0-23) | sin/cos transform | Hour 23 must be close to hour 0; linear encoding breaks this |
| Missing values | device_model = NaN | Filled with "unknown" | Treated as a category; preserves information about missingness |

**Reference for one-hot encoding**: Agresti, A. (2002). *Categorical Data Analysis*. Wiley. The general principle is that nominal variables must not be encoded with ordinal assumptions in regression models.

#### Common Support Check (`check_common_support`)

**Problem solved**: The positivity assumption requires that every covariate pattern has a non-zero probability of being in both treatment and control groups. Without common support checking, we may match treated units to controls that have very different propensity scores (poor overlap), producing unreliable estimates.

**Implementation**: Trim the 5% tails of each group's propensity distribution. Return masks for which units survive trimming. If too few treated or control units remain, the analysis returns None (insufficient overlap).

**Reference**: Crump, R.K., Hotz, V.J., Imbens, G.W., & Mitnik, O.A. (2009). "Dealing with Limited Overlap in Estimation of Average Treatment Effects." *Biometrika*, 96(1), 187-199.

#### BCa Bootstrap CI (`bootstrap_ci_paired`)

**Problem solved**: Simple percentile bootstrap CIs are biased when the bootstrap distribution is skewed — which happens frequently with small samples and binary outcomes. The BCa (bias-corrected accelerated) method adjusts for both median bias and skewness.

**Algorithm**:

1. **Pair structure**: For each treated unit, compute `pair_diff = matched_control_mean_outcome - treated_outcome`. This preserves the matched-pair structure instead of computing group-level means and bootstrapping those.

2. **Vectorized bootstrap** (no Python loop):
   ```python
   boot_indices = rng.randint(0, n, size=(n_boot, n))  # shape (2000, n)
   boot_deltas = pair_diffs[boot_indices].mean(axis=1)  # shape (2000,)
   ```

3. **BCa adjustment**:
   - Bias correction `z0 = Φ^{-1}(fraction of bootstrap < observed)`
   - Acceleration `a = Σ(θ̄ - θ_{-i})³ / [6(Σ(θ̄ - θ_{-i})²)^{3/2}]` via jackknife
   - Adjusted percentiles: `p_lo = Φ(z0 + (z0 + z_α/2) / (1 - a(z0 + z_α/2)))`

4. **Vectorized jackknife** (no Python loop):
   ```python
   jack_stats = (total_sum - pair_diffs) / (n - 1)  # leave-one-out means
   ```

5. **P-value**: Proportion of bootstrap deltas on the wrong side of zero, floored at 1/n_boot.

6. **Fallback**: If BCa computation fails (numerical instability with very small samples), falls back to simple percentile CI.

**Reference**: Efron, B. (1987). "Better Bootstrap Confidence Intervals." *Journal of the American Statistical Association*, 82(397), 171-185. Also: DiCiccio, T.J. & Efron, B. (1996). "Bootstrap Confidence Intervals." *Statistical Science*, 11(3), 189-228.

#### Propensity Matching (`propensity_match`)

The full PSM pipeline:

```
Input DataFrame → encode_features → fit LogisticRegression → predict P(T=1|X)
  → clip [0.01, 0.99] → check_common_support → KNN matching (k=5)
  → progressive caliper → paired ATT estimation → BCa bootstrap CI
  → balance check → return result dict
```

**Step-by-step**:

1. **Index reset**: `df.reset_index(drop=True)` — the caller may pass a filtered DataFrame with non-contiguous indices (e.g., [0, 5, 12, 71]). Without reset, `ps[df.index[mask]]` would use original indices as offsets into a smaller numpy array. This was a real bug discovered during testing.

2. **Propensity model**: LogisticRegression with `solver='lbfgs'`, `max_iter=1000`. Logistic regression is the standard for propensity scores. We don't use GBM or neural nets because interpretability and stability matter more than prediction accuracy — the goal is balanced matching, not accurate treatment prediction.

3. **Propensity clipping**: `np.clip(ps, 0.01, 0.99)` — extreme propensity scores indicate near-deterministic treatment assignment, violating positivity. Clipping prevents infinite weights in IPW variants and improves matching stability.

4. **Common support trimming**: Removes 5% tails of each group. If insufficient units remain, returns None.

5. **KNN matching**: `NearestNeighbors(n_neighbors=k, metric='euclidean')` on the 1D propensity score. Each treated unit gets k=5 control matches.

6. **Progressive caliper relaxation**:
   - Start with `caliper = 0.2 × std(ps)`
   - If too few matches: try `2×`, then `4×`, then `8×` caliper
   - If still insufficient: match without caliper (last resort, flagged in output)
   - **Why not just drop the caliper?** v1 silently disabled the caliper when any match failed, producing distant matches. Progressive relaxation tries harder before giving up.

7. **Paired ATT estimation** (fixes v1 bug):
   ```python
   for i in range(len(treated)):
       matched_controls = control_idx[indices[i]]
       control_outcomes_per_treated[i] = df.loc[matched_controls, outcome].mean()
   ```
   v1 used `.unique()` on matched controls, which broke the paired structure. If one control is matched to 3 treated units, `.unique()` weights it 1×; paired estimation correctly weights it 3×.

8. **Balance check**: Mean propensity score difference between treated and their matched controls. Lower is better (< 0.01 is excellent).

**Reference**: Austin, P.C. (2011). "An Introduction to Propensity Score Methods for Reducing the Effects of Confounding in Observational Studies." *Multivariate Behavioral Research*, 46(3), 399-424.

### 5.5 `analysis.py` — Journey-Conditioned Causal Analysis

The orchestration layer that implements our key innovation: journey conditioning with temporal ordering.

#### `analyze_issue_journey_conditioned()`

The core function. For a single (issue_type, screen_name) pair:

**Step 1: Journey conditioning**
```python
screen_sessions = screen_visits_df[screen_visits_df["screen_name"] == issue_screen]
sessions_reaching = set(screen_sessions["session_id"])
```
Only sessions that REACHED the issue screen are eligible. This eliminates journey-stage confounding.

**Step 2: Split affected vs control**
```python
affected_ids = set(issue_subset["session_id"].unique())
control_ids = sessions_reaching - affected_ids
```
Both groups visited the same screen. The only difference is whether they experienced the issue.

**Step 3: Temporal conversion for affected sessions**
```python
for sid in affected_ids:
    affected_converted[sid] = any(conv_ts > issue_ts for conv_ts in conversions[sid])
```
Did the session convert AFTER the issue happened? Not "did it ever convert."

**Step 4: Temporal conversion for control sessions**
```python
for sid in control_ids:
    control_converted[sid] = any(conv_ts > screen_arrival_ts for conv_ts in conversions[sid])
```
For control sessions, the reference point is when they arrived at the screen (comparable to when the issue would have occurred).

**Step 5: PSM within filtered population**
Pass the filtered DataFrame (only sessions reaching the screen) to `propensity_match()` with device context features.

**Step 6: Enrich with context**
- Funnel stage classification (early/mid/late based on screen-level conversion rate)
- User count (distinct affected users)
- Exception detail (most common exception type)
- Priority score: `|delta| × n_affected × significance_weight`

#### `analyze_network_errors()`

Session-level analysis for diffuse issues (not tied to a specific screen). Sessions with ≥3 network errors are "treated." This is a weaker causal claim — flagged in the output as "associational + adjusted."

#### `apply_fdr_correction()`

Benjamini-Hochberg FDR correction across all simultaneous tests.

**Problem solved**: When running 20 tests at α=0.05, we expect ~1 false positive. FDR controls the expected proportion of false discoveries among all discoveries.

**Algorithm**:
1. Sort p-values: p₍₁₎ ≤ p₍₂₎ ≤ ... ≤ p₍ₙ₎
2. Find the largest k where p₍ₖ₎ ≤ k/n × α
3. Reject all hypotheses with rank ≤ k

**Reference**: Benjamini, Y. & Hochberg, Y. (1995). "Controlling the False Discovery Rate: A Practical and Powerful Approach to Multiple Testing." *Journal of the Royal Statistical Society: Series B*, 57(1), 289-300.

#### `analyze_all_issues()` — Main Orchestrator

Iterates over all (pulse_type, screen_name) pairs from crash/ANR/non-fatal events, adds jank events by screen, runs network error analysis, and applies FDR correction. Returns results sorted by priority_score.

### 5.6 `frustration.py` — Composite Frustration Scoring

**Purpose**: A 0-100 composite metric summarizing session quality. Used for reporting and prioritization — NOT for PSM matching (see Section 6.1).

**Scoring algorithm**:

1. **Signal extraction**: Binary flags (has_crash, has_anr, short_session) and counts (jank_slow_count, jank_frozen_count, net_error_count, net_timeout_count, network_change_count)

2. **Percentile ranking**: Each signal is ranked within the population (0.0 = best, 1.0 = worst). This normalizes signals to a common scale regardless of absolute values.

3. **Weighted sum**: `score = Σ(percentile_rank × weight) / total_weight × 100`

**Default weights** (heuristic starting point):

| Signal | Weight | Reasoning |
|--------|--------|-----------|
| has_crash | 30 | Session-ending event, strongest impact |
| has_anr | 25 | Near-crash severity (5+ seconds frozen) |
| jank_frozen_count | 15 | >700ms frozen frames, very disruptive |
| net_error_count | 12 | Failed API calls break functionality |
| net_timeout_count | 10 | Timeouts create uncertainty/frustration |
| short_session | 10 | Short sessions suggest user gave up |
| jank_slow_count | 8 | >16ms frames, subtle but cumulative |
| network_change_count | 5 | Network switching introduces instability |

**Calibration from data** (`calibrate_frustration_weights`):

The default weights are reasonable guesses. The calibration function learns weights from actual conversion data:

1. Fit logistic regression predicting NON-conversion from frustration signals
2. Take absolute coefficients as weights (signals that predict non-conversion matter most)
3. Normalize to match default weight total

This means each app gets custom weights reflecting its specific user behavior. An app where network errors are the primary revenue killer will automatically up-weight that signal.

**Why frustration score is NEVER a matching feature**: The frustration score encodes post-treatment information. A session that crashes has `has_crash = 1` → higher frustration score. If we match on frustration score, we're matching on the treatment itself — this creates **post-treatment variable bias** that attenuates or reverses the estimated causal effect.

### 5.7 `mining.py` — Screen-Graph Process Mining

**Problem solved**: Traditional funnel analysis uses a depth-based model (step 1 → step 2 → step 3). Real mobile apps have non-linear navigation: users go back to the home screen, revisit screens, and take idiosyncratic paths. A depth-based funnel shows "home → home → home" at every depth because home is the most visited screen.

**Our approach**: Model the user journey as a directed weighted graph of screen transitions.

**Reference**: van der Aalst, W.M.P. (2011). *Process Mining: Discovery, Conformance and Enhancement of Business Processes.* Springer. We adapt the directly-follows graph concept from process mining to mobile screen transition analysis.

#### `build_screen_graph()`

For each session, iterate through screen visits ordered by timestamp. Create edges between consecutive screens, weighted by the number of sessions making that transition.

```
home ──(5000)──▶ SportsPage ──(3000)──▶ matchdetails
  │                  │                        │
  │◀──(2000)─────────┘                        │
  │                                           ▼
  │◀──(500)──── PaymentListing ◀──(800)── TourDetail
```

Back-navigation is just another edge in the graph — no special handling needed.

#### `find_conversion_paths()`

BFS from entry screens to conversion-adjacent screens:
- Entry screens: most common first screens across all sessions
- Conversion-adjacent: screens where >30% of visitors eventually convert
- BFS follows highest-weight edges first (greedy toward most common paths)
- Branching factor limited to 5 (avoid exponential explosion)
- No cycles allowed (path visited set)

#### `find_dropoff_edges()`

For each screen on a conversion path, compute the ratio of transitions that stay on-path vs. go off-path. High off-path rates indicate where users abandon the conversion journey.

### 5.8 `report.py` — Revenue Impact Reporting

Formatted terminal output with three sections:

1. **Revenue impact table**: All analyzed issues ranked by priority, showing funnel stage, affected/control counts, conversion rates, delta, 95% CI, significance, PS balance

2. **Significant findings detail**: For each significant finding, human-readable interpretation: "This issue REDUCES conversion AFTER reaching this screen by X% (95% CI: [a, b]). Estimated lost conversions: ~N per M affected sessions."

3. **Match quality summary**: Per-issue PS balance, common support percentage, caliper status. This is diagnostic — if PS balance > 0.05, the match quality is questionable.

4. **Screen graph report**: Top transitions, conversion paths, drop-off points

5. **Frustration report**: Mean frustration score for converting vs. non-converting sessions

### 5.9 `benchmark.py` — Statistical Validation Suite

**Purpose**: Prove the PSM engine produces correct causal estimates under controlled conditions with KNOWN ground truth.

**Data Generating Process (DGP)**:

1. Generate realistic covariates (device models, OS versions, etc.) from skewed Dirichlet distributions
2. Assign treatment via logistic model on covariates (controlled confounding strength)
3. Generate potential outcomes Y(0) and Y(1) from covariates + treatment effect
4. Compute TRUE ATT from potential outcomes on treated units
5. Mask counterfactuals — pass only observed outcomes to `propensity_match`
6. Compare estimated ATT to true ATT

**30 scenarios across 6 categories**:

| Category | What It Tests | Scenarios |
|----------|--------------|-----------|
| A: Effect Size Recovery | Can we recover effects of 0%, 5%, 10%, 20%? | 4 |
| B: CI Coverage | Do 95% CIs contain the true effect 95% of the time? | 4 |
| C: False Positive Rate | Under null (no effect), do we reject ≤5%? | 4 |
| D: Power | Can we detect effects across sample sizes? | 6 |
| E: Robustness | 5% treatment, 50% treatment, strong confounding, high-cardinality, small N, missing values, no confounding | 7 |
| F: Scalability | N=100 to N=10,000, wall-clock timing | 5 |

Each scenario runs 100 repetitions (20 in quick mode) with different random seeds.

**Pass/fail criteria**:
- FPR ≤ 10% (allowing simulation noise above nominal 5%)
- CI coverage in [80%, 100%]
- |Bias| < 5 percentage points
- Power ≥ 50% at N=2000 with 10pp effect
- Power monotonically increases with N
- N=10K completes in <120s per repetition
- No scenario has >80% None rate (complete failure)

**Results (24/24 checks EXCELLENT)**:
- Bias < 0.7pp across all effect sizes
- CI coverage 91-94%
- FPR 5-10%
- Power 98% at N=2000 with 10pp effect
- N=10K in 0.21s
- 0% None rate on all robustness scenarios

---

## 6. Critical Design Decisions and Their Reasoning

### 6.1 Post-Treatment Variable Exclusion

**Decision**: `unique_screens` and `frustration_score` are NEVER used as matching features.

**Reasoning**: These variables are causally downstream of the treatment (the technical issue):

```
Device Context → Issue (crash) → unique_screens (truncated by crash)
                              → frustration_score (includes has_crash)
                              → Conversion
```

If we match on `unique_screens`, we're comparing sessions that crashed after visiting 5 screens with sessions that visited 5 screens without crashing. But crashing TRUNCATES the screen count — a session that would have visited 10 screens crashes on screen 5. Matching on 5 screens means we're comparing a truncated high-engagement session with a genuinely low-engagement session. This attenuates the estimated effect.

If we match on `frustration_score`, we're matching on a variable that literally includes `has_crash = 1`. This is matching on the treatment itself — the propensity model will perfectly separate groups, making matching impossible or meaningless.

**Reference**: Rosenbaum, P.R. (1984). "The Consequences of Adjustment for a Concomitant Variable That Has Been Affected by the Treatment." *Journal of the Royal Statistical Society: Series A*, 147(5), 656-666.

### 6.2 One-Hot vs. Ordinal Encoding

**Decision**: Use one-hot encoding for nominal categoricals, not `LabelEncoder`.

**Reasoning**: `LabelEncoder` assigns integers: `{Chrome: 0, Firefox: 1, Safari: 2}`. Logistic regression interprets these as ordinal — it assumes Firefox is "between" Chrome and Safari on some continuum. This is meaningless for device models, browsers, and OS versions. The propensity model learns spurious ordinal relationships, producing biased propensity scores.

One-hot encoding creates binary indicator variables, treating each category independently. The propensity model learns "being on an iPhone 15 increases crash probability by β₁" — a meaningful statement.

**High-cardinality exception**: With 50+ device models, one-hot creates 49 features. This is sparse and can degrade logistic regression performance. We switch to frequency encoding (replace category with its population frequency) for >20 unique values.

### 6.3 Paired ATT Estimation

**Decision**: Preserve matched-pair structure; do not use `.unique()` on control matches.

**Reasoning**: With k=5 matching, one control session can be matched to multiple treated sessions. v1 used `.unique()` to deduplicate controls before computing rates:

```python
# v1 (WRONG):
unique_controls = set(all_matched_controls)
control_rate = df.loc[unique_controls, "outcome"].mean()
```

If control session C is matched to treated sessions T₁, T₂, T₃, `.unique()` counts C once. But C should contribute 3× to the control rate (once per treated match). Deduplication underweights popular controls and breaks the ATT estimand.

```python
# v2 (CORRECT):
for i in range(len(treated)):
    matched_controls = control_idx[indices[i]]
    control_outcomes_per_treated[i] = df.loc[matched_controls, outcome].mean()
att = control_outcomes_per_treated.mean() - treated_outcomes.mean()
```

Each treated unit's counterfactual is the mean of its own matched controls. The ATT is the mean of these paired differences.

### 6.4 Progressive Caliper Relaxation

**Decision**: Try 2×, 4×, 8× caliper before disabling.

**Reasoning**: A caliper prevents matching units with very different propensity scores. But a strict caliper can leave many treated units unmatched, reducing sample size and power.

v1 had two failure modes:
1. Caliper too strict → most matches rejected → too few matched pairs → analysis fails
2. Response: silently disable caliper → match quality degrades → biased estimates

v2 tries progressively wider calipers, documenting which was used. If even 8× caliper is insufficient, caliper is disabled but flagged: `caliper_applied = False` appears in the output.

### 6.5 Journey Conditioning vs. Adding Journey Features to Matching

**Decision**: Filter the population to sessions reaching the same screen, then match on device context only. NOT: add journey depth/stage as matching features.

**Reasoning**: Two approaches were considered:

**Option A (chosen)**: Filter to sessions reaching screen S, then PSM within this population.
**Option B (rejected)**: Include `screens_before_issue`, `funnel_stage`, `time_in_session` as matching features.

Option B has a subtle problem: `screens_before_issue` and `time_in_session_at_issue` are themselves affected by the treatment (crash truncates the session). They are post-treatment variables for the current issue, even though they look like pre-treatment context.

Option A is cleaner: by filtering to the same screen, we implicitly control for journey stage without including any post-treatment variables. Both groups reached the same point — any difference in conversion AFTER that point is attributable to the issue.

### 6.6 Conversion Proxy Discovery

**Decision**: Auto-discover conversion signals from GraphQL operation names without any host-app configuration.

**Reasoning**: Pulse's core value proposition is zero-instrumentation revenue insight. If we require the host app to define conversion events, we lose this differentiator.

**How it works**: The SDK auto-captures network calls including the `operation_name` header for GraphQL requests. We scan all operation names for revenue-related keywords (payment, purchase, order, checkout, subscribe, etc.). The most frequently hit conversion-keyword operation becomes the primary conversion proxy.

**Assumption**: Apps using GraphQL will have operation names that contain business intent. This is true for most production GraphQL APIs because operation names are required by convention and typically descriptive (e.g., `createOrder`, `validatePayment`, `addToCart`).

**Limitation**: Apps using REST APIs without descriptive path patterns may not yield good conversion proxies. The fallback is session depth (top 25% of unique screens visited — a weak proxy for engagement).

### 6.7 Cyclical Feature Encoding

**Decision**: Encode `session_hour` as (sin(2π·h/24), cos(2π·h/24)).

**Reasoning**: Linear encoding treats hour 23 and hour 0 as maximally distant (23 units apart), when they're actually adjacent (1 hour apart). Sine/cosine encoding preserves circular geometry:

```
hour 0:  sin=0.00, cos=1.00
hour 6:  sin=1.00, cos=0.00
hour 12: sin=0.00, cos=-1.00
hour 18: sin=-1.00, cos=0.00
hour 23: sin=-0.26, cos=0.97  ← close to hour 0
```

This matters because usage patterns are time-dependent (crash rates may be higher during peak hours), and time-of-day should be a confounder we control for.

---

## 7. Evolution: From v1 to v2 to Modular Package

### 7.1 v1: Prototype (`revenue_impact_prototype.py`, ~1000 lines)

**What it did**: Connected to ClickHouse, pulled session profiles, discovered conversion proxies, ran PSM for each issue type. Single monolithic script.

**What it got wrong** (identified through two rounds of critical review):

| # | Flaw | Severity | Impact |
|---|------|----------|--------|
| 1 | No journey conditioning | Critical | Late-funnel crashes appeared beneficial (84.7% wrong direction) |
| 2 | No temporal ordering | Critical | Conversion "any time in session" not "after issue" |
| 3 | Post-treatment variables in matching | Critical | unique_screens and frustration_score biased estimates |
| 4 | LabelEncoder for categoricals | Critical | Ordinal encoding created spurious relationships |
| 5 | `.unique()` on matched controls | Critical | Broke paired ATT estimand |
| 6 | Silent caliper disabling | Moderate | Distant matches went undetected |
| 7 | No common support check | Moderate | Matched in low-overlap regions |
| 8 | Simple percentile bootstrap CI | Moderate | Biased CIs for skewed distributions |
| 9 | Depth-based funnel | Moderate | "home → home → home" at every depth |
| 10 | Arbitrary frustration weights | Low | 0.6pp difference with no empirical basis |
| 11 | Global MATCHING_FEATURES list mutation | Low | `.append()` mutated module-level list |
| 12 | No multiple testing correction | Low | Expected false positives across many tests |

### 7.2 v2: Corrected Implementation (`revenue_impact_v2.py`, ~1000 lines)

Fixed flaws #1-3, #6, #9, #10. Still a single monolithic file. Validated on UCI Online Shoppers dataset (7 tests, all passed).

**Key result**: PaymentListing crash delta went from -84.7% (v1, wrong) to +98.3% (v2, correct).

### 7.3 Modular Package (`causal/`, ~3000 lines, 10 modules)

Fixed ALL 12 flaws. Refactored into clean modules with:
- Zero magic numbers (everything in CausalConfig)
- Typed data models
- Dual-mode ClickHouse queries (MV + legacy)
- 30-scenario benchmark suite with synthetic ground truth
- PB-scale architecture via materialized views + sampling

---

## 8. Validation Results

### 8.1 UCI Online Shoppers Dataset (12,330 sessions)

Mapped UCI dataset to Pulse-like signals:
- Page types (Admin/Info/Product) → "screens" in a funnel
- BounceRates/ExitRates → "technical friction" (like crash/jank)
- Revenue → conversion outcome
- OS/Browser/Region → device context

**7 validation tests**:

| Test | Description | Result |
|------|-------------|--------|
| A: Journey-Conditioned PSM | High bounce on product pages hurts conversion? | 4/4 significant (+11-15% deltas) |
| B: v1 vs v2 Head-to-Head | Does v2 give correct answer where v1 doesn't? | v2: +20.9% vs ground truth +24.4% |
| C: Placebo | Random "issue" on same journey stage → no effect? | 1/10 false positive (expected) |
| D: Dose-Response | More friction = bigger drop? | Perfectly monotonic: 26.5% → 20.2% → 11.4% → 2.8% |
| E: Stratified | Consistent across subgroups? | 7/7 subgroups significant |
| F: Sensitivity | Stable across k, caliper, features? | Stable across k=1-20, all calipers, all feature sets |
| G: A/A Test | Split control → zero delta? | 1/5 marginal (within expected FPR) |

### 8.2 Synthetic Benchmark (30 scenarios, 24/24 checks EXCELLENT)

| Metric | Target | Achieved |
|--------|--------|----------|
| Bias | < 5pp | < 0.7pp across all effect sizes |
| CI Coverage | ~95% | 91-94% |
| False Positive Rate | ≤ 10% | 5-10% |
| Power (N=2000, 10pp) | ≥ 50% | 98% |
| Power monotonic with N | Yes | Yes (D1 < D2 < D3) |
| N=10K time | < 120s | 0.21s |
| Robustness | No complete failures | 0% None rate on all scenarios |

### 8.3 Real Data (FanCode Production)

- 10K synthetic sessions (seed data): All known causal effects recovered
- 300 real sessions: Correctly reported "insufficient data" (v1 falsely reported significant findings)

---

## 9. PB-Scale Architecture

### 9.1 The Problem

At 20TB/day raw events:
- ~40 billion events/day
- ~400 million sessions/day
- Per project (~100 projects): ~4M sessions/day
- 30-day lookback: ~120M sessions per project
- `GROUP BY SessionId` across raw tables: 2.4TB aggregation state per query

This cannot fit in memory. The queries would take hours or OOM.

### 9.2 The Solution: 3-Layer Optimization

**Layer 1: Materialized Views (pre-aggregation)**

6 MVs pre-aggregate raw events at INSERT time:

| MV | Input | Output | Compression |
|----|-------|--------|-------------|
| causal_session_profiles | otel_traces | 1 row/session (device context, timestamps, counts) | ~100:1 |
| causal_screen_visits | otel_traces | 1 row/(session, screen) | ~20:1 |
| causal_network_operations | otel_traces | 1 row/(day, op_name) | ~10000:1 |
| causal_conversion_events | otel_traces | 1 row/(session, conversion) | ~50:1 |
| causal_jank_by_screen | otel_logs | 1 row/(session, screen, type) | ~10:1 |
| causal_log_signals | otel_logs | 1 row/session | ~100:1 |

Storage overhead: ~5-8% of base tables.

**Layer 2: Deterministic Sampling**

PSM doesn't need 120M sessions. Statistical analysis with 50K sessions provides:
- 12× the minimum sample size for 80% power
- Bootstrap CI width comparable to full population
- Benchmark proves: N=2000 achieves 98% power at 10pp effect

Sampling: `ORDER BY cityHash64(SessionId) LIMIT 50000`
- Deterministic: same sessions every run (reproducible)
- Uniform: cityHash64 is well-distributed
- Efficient: ClickHouse stops scanning after LIMIT rows

**Layer 3: Session-Scoped Downstream Queries**

After sampling 50K session profiles, ALL subsequent queries filter:
```sql
WHERE SessionId IN (sampled_session_ids)
```

Instead of scanning 120M sessions of screen visits, we fetch for 50K. This is the key optimization: the sample cascades through the entire pipeline.

### 9.3 Memory Profile

| Without MVs/Sampling | With MVs + 50K Sampling |
|---------------------|------------------------|
| 120M sessions × 500 bytes = 60GB session profiles | 50K × 500 bytes = 25MB |
| 2.4B screen visits | ~1M screen visits |
| Full table scan per query | Pre-aggregated reads |
| ~100GB working memory | <500MB working memory |

---

## 10. Known Limitations and Assumptions

### 10.1 Strong Ignorability (Unconfoundedness)

**Assumption**: Given device context (device_model, os_version, app_version, network_provider, geo_country, session_hour), whether a session crashes is independent of its tendency to convert.

**When this is reasonable**: Crashes caused by bugs (memory leaks, race conditions, server errors) are largely random given device context. A Galaxy S23 on Android 14 has a specific crash probability regardless of the user's purchase intent.

**When this may be violated**:
- Users with specific browsing patterns trigger specific bugs (behavior-confounding)
- Returning vs. new users have different crash rates AND conversion rates
- A/B test variants affect both crash rates and conversion
- We mitigate with journey conditioning (same screen = same intent proxy) but cannot fully eliminate

### 10.2 Conversion Proxy Accuracy

**Assumption**: GraphQL operation names containing revenue-related keywords represent actual conversion events.

**When this fails**:
- REST APIs without descriptive paths or operation names
- In-app purchases via Play Store/App Store billing (bypasses the app's network layer)
- Server-side validation failures (200 OK but business logic rejects the transaction)
- The operation name might be called during validation, not actual purchase

### 10.3 Single-Session Attribution

**Assumption**: Conversion is measured within the same session as the issue.

**What this misses**: A user who crashes during payment may return in a new session and complete the purchase. The current engine doesn't link multi-session user journeys (though the data model supports it via `user_id`). This means we may overestimate issue impact.

Fix #5 (multi-session user attribution) is designed but not yet implemented — requires consistent `user_id` across sessions.

### 10.4 SUTVA (Stable Unit Treatment Value Assumption)

**Assumption**: One session's crash does not affect another session's conversion.

**When this may be violated**: In social/viral apps, one user's poor experience may affect another user's behavior (bad reviews, word-of-mouth). We cannot detect or correct for this.

### 10.5 Screen-Level Granularity

**Assumption**: Issues are meaningfully tied to specific screens.

**When this is coarse**:
- Network errors are diffuse (not screen-specific) — we handle with session-level analysis
- A crash on a screen may be caused by events on a previous screen (background thread timing)
- Single-screen apps (e.g., games) have no screen graph to analyze

### 10.6 Temporal Resolution

**Assumption**: Event timestamps are accurate and ordered.

**Potential issues**:
- Clock skew between client and server timestamps
- Events batched by the SDK may have coarsened timestamps
- ANR detection has inherent delay (5+ seconds before Android reports)

---

## 11. Streaming Architecture (Future)

See `pulse_ai/docs/streaming-architecture.md` for the full design.

The causal engine is designed for batch analysis (Cold Path). The streaming architecture extends it:

- **Hot Path** (per-event, <100ms): Session state accumulation, real-time frustration scoring using pre-computed percentile thresholds, at-risk session detection
- **Warm Path** (periodic, every 1-6 hours): Incremental issue detection, quick PSM with pre-fitted model (no refitting, cached control population)
- **Cold Path** (nightly): Full analysis as implemented today, exports baselines for Hot/Warm paths

**Implementation mapping**:
- Hot Path → Vert.x backend (Java, in-process session cache)
- Warm Path → Python cron job with `--mode warm --use-cached-model`
- Cold Path → Python cron job (current `run_causal_analysis.py`)

---

## 12. Academic References

> 35 references organized by topic. Each includes relevance to our specific implementation.

### Foundational Causal Inference

1. **Rubin, D.B.** (1974). "Estimating Causal Effects of Treatments in Randomized and Nonrandomized Studies." *Journal of Educational Psychology*, 66(5), 688-701.
   — Introduced the potential outcomes framework (Rubin Causal Model) that underpins our entire approach. Defines the fundamental problem of causal inference — we can only observe one potential outcome per unit.

2. **Rosenbaum, P.R. & Rubin, D.B.** (1983). "The Central Role of the Propensity Score in Observational Studies for Causal Effects." *Biometrika*, 70(1), 41-55.
   — **The** seminal paper proving that matching on P(T=1|X) eliminates confounding bias, forming the theoretical basis of our PSM engine. Theorem 3 establishes that strong ignorability given X implies strong ignorability given e(X).

3. **Rosenbaum, P.R. & Rubin, D.B.** (1985). "Constructing a Control Group Using Multivariate Matched Sampling Methods That Incorporate the Propensity Score." *The American Statistician*, 39(1), 33-38.
   — Practical guidance on implementing propensity score matching, including nearest-neighbor matching that we use.

4. **Heckman, J.J.** (1979). "Sample Selection Bias as a Specification Error." *Econometrica*, 47(1), 153-161.
   — Foundational work on selection bias correction. Directly informs our journey conditioning approach: without conditioning on reaching the same screen, late-funnel crashes exhibit classic Heckman-style selection bias.

5. **Imbens, G.W. & Rubin, D.B.** (2015). *Causal Inference for Statistics, Social, and Biomedical Sciences: An Introduction.* Cambridge University Press.
   — Comprehensive treatment of the potential outcomes framework, propensity scores, and matching methods. Primary textbook reference for our methodology. Chapter 18 on matching is particularly relevant.

6. **Angrist, J.D. & Pischke, J.S.** (2009). *Mostly Harmless Econometrics: An Empiricist's Companion.* Princeton University Press.
   — Accessible treatment of causal inference methods. Their discussion of selection bias and instrumental variables informed our understanding of the journey-conditioning problem.

### PSM Methodology

7. **Austin, P.C.** (2011). "An Introduction to Propensity Score Methods for Reducing the Effects of Confounding in Observational Studies." *Multivariate Behavioral Research*, 46(3), 399-424.
   — Comprehensive tutorial on PSM methods. Source for our caliper choice (0.2 SD), k-neighbor recommendation, and balance diagnostics. The most-cited practical PSM guide.

8. **Austin, P.C.** (2011). "Optimal Caliper Widths for Propensity-Score Matching When Estimating Differences in Means and Differences in Proportions in Observational Studies." *Pharmaceutical Statistics*, 10(2), 150-161.
   — Establishes that a caliper of 0.2 SD of the logit of the propensity score removes approximately 98% of bias. Directly informs our `caliper_sd = 0.2` default and the progressive relaxation strategy.

9. **Austin, P.C.** (2009). "Some Methods of Propensity-Score Matching Had Superior Performance to Others: Results of an Empirical Investigation and Monte Carlo Simulations." *Biometrical Journal*, 51(1), 171-184.
   — Monte Carlo comparison of PSM methods showing nearest-neighbor with caliper performs well across scenarios. Validates our choice of KNN matching with caliper over alternatives.

10. **Stuart, E.A.** (2010). "Matching Methods for Causal Inference: A Review and a Look Forward." *Statistical Science*, 25(1), 1-21.
    — Comprehensive review covering caliper matching, k:1 matching, common support, and balance diagnostics — all of which we implement. Particularly useful for the comparison of matching vs. weighting approaches.

11. **Dehejia, R.H. & Wahba, S.** (1999). "Causal Effects in Nonexperimental Studies: Reevaluating the Evaluation of Training Programs." *Journal of the American Statistical Association*, 94(448), 1053-1062.
    — Influential empirical demonstration that propensity score methods can replicate experimental benchmarks from observational data. Validates the approach for real-world causal inference.

12. **Rosenbaum, P.R.** (1984). "The Consequences of Adjustment for a Concomitant Variable That Has Been Affected by the Treatment." *Journal of the Royal Statistical Society: Series A*, 147(5), 656-666.
    — Theoretical basis for our exclusion of post-treatment variables (unique_screens, frustration_score) from matching features. Proves that adjusting for post-treatment variables attenuates or reverses causal estimates.

### ATT Estimation

13. **Imbens, G.W. & Wooldridge, J.M.** (2009). "Recent Developments in the Econometrics of Program Evaluation." *Journal of Economic Literature*, 47(1), 5-86.
    — Authoritative review distinguishing ATE, ATT, and ATU estimands. Establishes that ATT (our estimand) is appropriate when interest is in the effect on those actually exposed to a condition.

14. **Heckman, J.J., Ichimura, H. & Todd, P.E.** (1997). "Matching as an Econometric Evaluation Estimator: Evidence from Evaluating a Job Training Programme." *Review of Economic Studies*, 64(4), 605-654.
    — Develops the econometric foundations for ATT estimation via matching, showing matching on propensity scores recovers ATT under weaker assumptions than ATE.

15. **Rubin, D.B. & Thomas, N.** (1996). "Matching Using Estimated Propensity Scores: Relating Theory to Practice." *Biometrics*, 52(1), 249-264.
    — Shows that propensity score model misspecification (such as from incorrect variable encoding) propagates into biased treatment effect estimates. Motivates our careful one-hot encoding of nominal covariates.

### Common Support / Positivity

16. **Crump, R.K., Hotz, V.J., Imbens, G.W., & Mitnik, O.A.** (2009). "Dealing with Limited Overlap in Estimation of Average Treatment Effects." *Biometrika*, 96(1), 187-199.
    — Proposes systematic trimming rules for observations with extreme propensity scores. Our `common_support_trim = 0.05` follows their recommendation.

17. **Petersen, M.L., Porter, K.E., Gruber, S., Wang, Y. & van der Laan, M.J.** (2012). "Diagnosing and Responding to Violations in the Positivity Assumption." *Statistical Methods in Medical Research*, 21(1), 31-54.
    — Practical guide to diagnosing and handling positivity violations. Informs our approach of returning None when common support is insufficient rather than producing unreliable estimates.

18. **King, G. & Zeng, L.** (2006). "The Dangers of Extreme Counterfactuals." *Political Analysis*, 14(2), 131-159.
    — Demonstrates how extrapolation outside common support produces unreliable estimates. Motivates strict overlap enforcement in the engine.

### Bootstrap Methods

19. **Efron, B.** (1987). "Better Bootstrap Confidence Intervals." *Journal of the American Statistical Association*, 82(397), 171-185.
    — Introduced the BCa (bias-corrected accelerated) bootstrap method. Our `bootstrap_ci_paired()` implements this with vectorized computation.

20. **DiCiccio, T.J. & Efron, B.** (1996). "Bootstrap Confidence Intervals." *Statistical Science*, 11(3), 189-228.
    — Comprehensive review comparing bootstrap CI methods (percentile, BC, BCa, ABC). Establishes BCa as the preferred general-purpose method due to second-order accuracy and transformation invariance.

21. **Efron, B. & Tibshirani, R.J.** (1993). *An Introduction to the Bootstrap.* Chapman & Hall/CRC.
    — The standard reference text on bootstrap methods. Covers theory and practical implementation of BCa intervals, jackknife acceleration estimation, and bootstrap diagnostics.

22. **Efron, B.** (1992). "Jackknife-After-Bootstrap Standard Errors and Influence Functions." *Journal of the Royal Statistical Society: Series B*, 54(1), 83-127.
    — Describes the jackknife-based acceleration constant estimation used in BCa, which is the approach implemented in the vectorized jackknife in our matching engine.

### Multiple Testing

23. **Benjamini, Y. & Hochberg, Y.** (1995). "Controlling the False Discovery Rate: A Practical and Powerful Approach to Multiple Testing." *Journal of the Royal Statistical Society: Series B*, 57(1), 289-300.
    — The FDR correction method we use to control false discoveries when testing multiple issue-screen pairs simultaneously. More powerful than Bonferroni for our use case.

24. **Benjamini, Y. & Yekutieli, D.** (2001). "The Control of the False Discovery Rate in Multiple Testing Under Dependency." *Annals of Statistics*, 29(4), 1165-1188.
    — Extends B-H to arbitrary dependency structures. Relevant when tested issues may be correlated (e.g., crashes and ANRs on the same screens share confounders).

### Selection Bias & Collider Conditioning

25. **Pearl, J.** (2009). *Causality: Models, Reasoning, and Inference.* 2nd Edition. Cambridge University Press.
    — The foundational text on structural causal models and DAGs. Provides the framework for understanding why conditioning on intermediate outcomes (screen reached) is necessary while avoiding collider bias.

26. **Hernan, M.A., Hernandez-Diaz, S. & Robins, J.M.** (2004). "A Structural Approach to Selection Bias." *Epidemiology*, 15(5), 615-625.
    — Formalizes selection bias using DAGs and shows how conditioning on post-treatment variables can introduce or remove bias. Directly relevant to our decision of which variables to condition on (screen reached) versus exclude (post-treatment outcomes).

27. **Elwert, F. & Winship, C.** (2014). "Endogenous Selection Bias: The Problem of Conditioning on a Collider Variable." *Annual Review of Sociology*, 40, 31-53.
    — Clear exposition of collider bias. Informs the engine's explicit exclusion of unique_screens and frustration_score from matching features.

### Process Mining

28. **van der Aalst, W.M.P.** (2016). *Process Mining: Data Science in Action.* 2nd Edition. Springer.
    — The definitive textbook on process mining. Our screen-graph construction adapts the directly-follows graph (DFG) concept to mobile screen transitions. BFS path discovery follows from the alpha algorithm family.

29. **Weijters, A.J.M.M. & Ribeiro, J.T.S.** (2011). "Flexible Heuristics Miner (FHM)." *Proceedings of the IEEE Symposium on Computational Intelligence and Data Mining (CIDM)*, 310-317.
    — The Heuristics Miner uses directly-follows frequency and dependency measures to build process graphs, closely related to our directed weighted screen-graph construction.

### Digital Experience Monitoring & Frustration

30. **Brutlag, J.D.** (2009). "Speed Matters for Google Web Search." Google Research.
    — Demonstrates that even small latency increases (100-400ms) measurably reduce user engagement. Establishes the empirical basis for latency-based frustration signals that inform our scoring.

31. **Akamai Technologies.** (2017). "The State of Online Retail Performance."
    — Large-scale study showing 100ms delay in load time reduces conversion rates by 7%. Directly supports the revenue-leakage framing of UX performance metrics.

32. **Google Core Web Vitals** (2020). web.dev documentation.
    — Defines Core Web Vitals (LCP, FID/INP, CLS) as standardized UX quality signals. Our frustration score is analogous to a mobile-specific composite of such metrics. Jank thresholds (16ms slow, 700ms frozen) align with the RAIL performance model.

33. **Ceaparu, I., Lazar, J., Bessiere, K., Robinson, J. & Shneiderman, B.** (2004). "Determining Causes and Severity of End-User Frustration." *International Journal of Human-Computer Interaction*, 17(3), 333-356.
    — Empirical study categorizing and quantifying sources of user frustration. Provides the theoretical grounding for composite frustration scoring from multiple signal types.

### Causal Inference in Technology

34. **Kohavi, R., Tang, D. & Xu, Y.** (2020). *Trustworthy Online Controlled Experiments: A Practical Guide to A/B Testing.* Cambridge University Press.
    — The standard industry reference on A/B testing at scale (Microsoft/Bing). Relevant for understanding when observational causal methods are needed as a complement to experimentation.

35. **Xu, Y., Chen, N., Fernandez, A., Sinno, O. & Bhasin, A.** (2015). "From Infrastructure to Culture: A/B Testing Challenges in Large Scale Social Networks." *KDD '15*, 2227-2236.
    — LinkedIn's account of A/B testing challenges at scale (network effects, interference). Motivates observational causal methods when experimentation is infeasible — exactly the use case Pulse addresses.

### Feature Encoding

36. **Agresti, A.** (2013). *Categorical Data Analysis.* 3rd Edition. Wiley.
    — Standard reference establishing that nominal (unordered) categorical predictors require dummy/one-hot coding in logistic regression. Motivates our switch from LabelEncoder to one-hot encoding.

37. **Hosmer, D.W., Lemeshow, S. & Sturdivant, R.X.** (2013). *Applied Logistic Regression.* 3rd Edition. Wiley.
    — Covers practical consequences of encoding choices in logistic regression. Demonstrates that integer codes for nominal categories force a linear relationship that misspecifies the propensity model.

### Simpson's Paradox (Motivating Example)

38. **Charig, C.R., Webb, D.R., Payne, S.R. & Wickham, J.E.A.** (1986). "Comparison of Treatment of Renal Calculi by Open Surgery, Percutaneous Nephrolithotomy, and Extracorporeal Shockwave Lithotripsy." *British Medical Journal*, 292(6524), 879-882.
    — The classic real-world demonstration of Simpson's paradox. Illustrates exactly why naive aggregate comparisons produce misleading causal conclusions — the same phenomenon we observed in v1 where crashes on PaymentListing appeared to HELP conversion (aggregate confounding by journey stage).

---

## Appendix A: File Inventory

| File | Lines | Purpose |
|------|-------|---------|
| `causal/__init__.py` | 31 | Package exports |
| `causal/config.py` | 51 | Configuration dataclass |
| `causal/models.py` | 64 | IssueAnalysis, ConversionProxy, DropoffEdge |
| `causal/data.py` | 543 | ClickHouse extraction (7 functions, MV + legacy) |
| `causal/matching.py` | 294 | PSM engine (encoding, matching, bootstrap) |
| `causal/analysis.py` | 433 | Journey-conditioned analysis + FDR |
| `causal/frustration.py` | 172 | Frustration scoring + calibration |
| `causal/mining.py` | 226 | Screen-graph process mining |
| `causal/report.py` | 183 | Formatted reporting |
| `causal/benchmark.py` | 627 | 30-scenario benchmark suite |
| `run_causal_analysis.py` | 322 | Main entry point |
| **Total** | **~2,946** | |

## Appendix B: Materialized View Schema

See `backend/ingestion/clickhouse-causal-mvs.sql` for the full DDL. The 6 MVs use `AggregatingMergeTree` engine with `anyState`, `groupUniqArrayState`, and `uniqCombined64State` aggregate functions for incremental pre-aggregation at INSERT time.

## Appendix C: Configuration Reference

All parameters in `CausalConfig` with their defaults, valid ranges, and impact:

| Parameter | Default | Range | Impact |
|-----------|---------|-------|--------|
| k_neighbors | 5 | 1-20 | Higher = more bias, less variance |
| caliper_sd | 0.2 | 0.1-1.0 | Lower = stricter matching, fewer matches |
| n_bootstrap | 2000 | 500-10000 | Higher = more precise CIs, slower |
| alpha | 0.05 | 0.01-0.10 | Lower = fewer discoveries, fewer false positives |
| min_affected | 10 | 5-50 | Lower = more analyses, more noise |
| common_support_trim | 0.05 | 0.01-0.10 | Higher = stricter overlap requirement |
| max_onehot_cardinality | 20 | 10-50 | Higher = more features in propensity model |
| apply_fdr | True | True/False | Controls false discovery rate across tests |
