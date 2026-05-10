# Write SDK Core SPEC.md

## Package
pulse-web-otel

## Context
Foundation issue. All other SPEC.md files reference the shared data contract table and
lifecycle model defined here (§5). Must complete before issues 02–09.
Covers SDK initialisation, configuration, session lifecycle, consent, feature gates,
remote config, exporters, sampling, and persistence — the non-instrumentation core.
Source: PRD §Solution, §Implementation Decisions.

## Acceptance Criteria
- [ ] `docs/instrumentations/sdk-core/SPEC.md` exists
- [ ] All 9 sections present: `## 1. Goal`, `## 2. Assumptions`, `## 3. Requirements`, `## 4. Architectural Design`, `## 5. LLD`, `## 6. Test Coverage`, `## 7. Known Bugs & Gaps`, `## 8. Redundancy & Cleanup Notes`, `## 9. Open Questions`
- [ ] §5 includes: complete `pulse.type` enum, `platform=web` mandate, shared attribute table (key/type/source/required/notes format)
- [ ] §5 includes SDK init flow, consent gate, feature gate, remote config fetch sequence
- [ ] §6 lists all unit tests from `sdk-lifecycle.test.ts`, `sdk-public-methods.test.ts`, `m1.test.ts`, `m3.test.ts`, `m8.test.ts`, `integration-simplified-init.test.ts`
- [ ] §7 absorbs API critique content from `docs/API-CRITIQUE.md` as P0/P1/P2 items
- [ ] §8 lists every planning doc absorbed (by path)
- [ ] Old docs deleted after triple-eval: `web-sdk-plan/v1/01-foundation/README.md`, `web-sdk-plan/v1/01-foundation/sdk-lifecycle.md`, `web-sdk-plan/INTEGRATION.md`, `docs/API-CRITIQUE.md`

## Implementation hints
Read these in order before writing: `web-sdk-plan/v1/01-foundation/sdk-lifecycle.md`, `web-sdk-plan/INTEGRATION.md`, `docs/API-CRITIQUE.md`, then `src/sdk.ts`, `src/config.ts`, `src/consent.ts`, `src/session.ts`, `src/feature-gate.ts`, `src/remote-config.ts`, `src/instrumentation-registry.ts`, `src/before-send.ts`, `src/index.ts`.
Triple-eval before deleting: pass 1 — is every concept captured? pass 2 — scan old doc line-by-line for missed detail? pass 3 — final confirm.

## Eval
```bash
#!/usr/bin/env bash
set -euo pipefail
spec="pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md"
fail=0

[ -f "$spec" ] || { echo "MISSING: $spec"; exit 1; }

for section in \
  "## 1. Goal" \
  "## 2. Assumptions" \
  "## 3. Requirements" \
  "## 4. Architectural Design" \
  "## 5. LLD" \
  "## 6. Test Coverage" \
  "## 7. Known Bugs" \
  "## 8. Redundancy" \
  "## 9. Open Questions"; do
  grep -q "$section" "$spec" || { echo "MISSING SECTION: $section"; fail=1; }
done

# §5: attribute table has required columns
grep -q "| Attribute key" "$spec" || { echo "§5: missing attribute table header"; fail=1; }
grep -q "pulse\.type" "$spec"     || { echo "§5: missing pulse.type row"; fail=1; }
grep -q "platform.*web\|web.*platform" "$spec" || { echo "§5: missing platform=web mandate"; fail=1; }

# §5: pulse.type enum entries (spot-check a few canonical values)
for pt in "session.start" "session.end" "device.crash" "non_fatal" "http" "app.click" "web_vital" "screen_load" "screen_session"; do
  grep -q "$pt" "$spec" || { echo "§5: pulse.type enum missing '$pt'"; fail=1; }
done

# §5: SDK init flow keywords
for kw in "consent" "feature.gate|feature gate|PulseFeature" "remote.config|remote config|RemoteConfig"; do
  grep -qE "$kw" "$spec" || { echo "§5: init flow missing '$kw'"; fail=1; }
done

# §7: at least one P0 item absorbed from API-CRITIQUE.md
grep -q "P0:" "$spec" || { echo "§7: no P0 items found — API-CRITIQUE.md must be absorbed"; fail=1; }

# Old planning docs gone
for old in \
  "pulse-web-otel/web-sdk-plan/v1/01-foundation/README.md" \
  "pulse-web-otel/web-sdk-plan/v1/01-foundation/sdk-lifecycle.md" \
  "pulse-web-otel/web-sdk-plan/INTEGRATION.md" \
  "pulse-web-otel/docs/API-CRITIQUE.md"; do
  [ -f "$old" ] && { echo "NOT DELETED: $old"; fail=1; }
done

exit $fail
```

## Out of Scope
Individual instrumentation signal contracts (covered in issues 02–07).
React/Next.js integration (issues 08–09). Source code changes.

## Blocked by
None
