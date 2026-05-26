from google.adk.agents import LlmAgent

from pulse_ai.constants import SEVERITY_THRESHOLD_TEXT
from pulse_ai.schemas.interaction_overview_v1 import InteractionOverviewOutputV1

summary_agent = LlmAgent(
    name="summary_agent",
    model="gemini-2.0-flash",
    tools=[],
    output_schema=InteractionOverviewOutputV1,
    output_key="interactions_overview_result",
    instruction=f"""\
You are a technical analysis agent. Based on the interaction health data gathered in this
conversation, produce a structured output. Python will assemble the user-facing summary and
inject all numbers — your job is to classify interactions and write qualitative hypothesis text only.

Pre-computed fields in the data (do not recalculate):
- severity: EXCELLENT / GOOD / FAIR / POOR (from apdex)
- error_severity: CRITICAL_ERROR_RATE / ELEVATED_ERROR_RATE / NORMAL_ERROR_RATE
- error_rate: % of error spans
- apdex: 0–1 score
- priority_rank: integer or null (computed by Python — null = excluded from priority list)

{SEVERITY_THRESHOLD_TEXT}

---
OUTPUT FIELDS — return exactly these four fields:

1. poor_interactions (list)
   One entry per interaction where severity = POOR.
   Each entry: {{ interaction_name: "<exact name>", hypothesis: "<5–15 words, qualitative, no numbers>" }}
   Example hypothesis: "users experiencing very slow loads, possibly backend timeout"
   Leave empty [] if no POOR interactions.

2. fair_or_elevated_interactions (list)
   One entry per interaction where severity = FAIR, OR any severity with ELEVATED_ERROR_RATE or
   CRITICAL_ERROR_RATE. Do NOT include POOR interactions here (already in poor_interactions).
   Do NOT include EXCELLENT interactions even if they have elevated errors.
   Each entry: {{ interaction_name: "<exact name>", hypothesis: "<5–15 words, no numbers>" }}
   Example: "elevated error rate suggests active bug or backend regression"
   Leave empty [] if none qualify.

3. trend_note (string or null)
   ONE qualitative sentence if the previous snapshot shows a meaningful apdex shift (>0.05) for
   any interaction compared to the current data. No numbers. Focus on direction and severity.
   Example: "Checkout flow has notably worsened since last snapshot while payment remains stable."
   Return null if: no previous context exists, or no meaningful change observed.
   NEVER write "no notable shifts observed" — just return null.

4. business_impact (string)
   A short qualitative phrase (5–12 words, no numbers) specific to the POOR interactions.
   Describe what users or the business lose due to the POOR interactions specifically.
   Tailor it to the interaction names — e.g. payment/checkout flows → "directly blocking purchases",
   onboarding/launch → "driving users away before they engage", navigation → "preventing core discovery".
   Keep it tight — one phrase, no subject ("directly blocking…" not "These are directly blocking…").
   Examples:
     "directly blocking purchases and degrading user retention"
     "driving users away before they complete checkout"
     "preventing users from discovering and browsing products"

5. context (string)
   Machine-readable snapshot for the next run. This is stored internally and never shown to users.
   Use the pre-formatted string fields from the data (apdex_str, poor_user_rate_str, error_rate_str)
   when writing numeric values in this snapshot.

   Format EXACTLY (sorted by poor_user_rate descending):

   SNAPSHOT [<current UTC time>] window=<last_1h|last_24h>
   <InteractionName>: apdex=<apdex_str>, poor_user_rate=<poor_user_rate_str>, error_rate=<error_rate_str>, p50=<val>ms, severity=<EXCELLENT|GOOD|FAIR|POOR>
   ... (one line per interaction)

   TREND NOTES:
   - <2-4 sentences vs previous snapshot. "Cold start — no prior baseline." if none.>

   WATCH LIST:
   - <CRITICAL and POOR interactions with a one-sentence watch note each.>

---
RULES:
- interaction_name must match the exact name from the data — do not paraphrase or shorten.
- hypothesis is qualitative only — no percentages, no latency numbers, no apdex values.
- trend_note is qualitative only — describe direction ("worsened", "recovered") without numbers.
- Do NOT include EXCELLENT interactions in poor_interactions or fair_or_elevated_interactions.
""",
    disallow_transfer_to_parent=True,
    disallow_transfer_to_peers=True,
)
