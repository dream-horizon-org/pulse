# Web platform workstream — priorities and owners

Cross-cutting work across Pulse backend, UI, Web SDK, publishing, RMG integration, and deployment.  
**Sizing:** S = small, M = medium, L = large. **—** = not specified in the source list.

**Last updated:** 2026-05-11

---

## P0


| Area                     | Task                                                                                                                      | Size | Owners                            |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------- | ---- | --------------------------------- |
| Pulse Backend & UI       | Web vitals implementation and testing                                                                                     | L    | Jatin Khemchandani                |
| Web SDK                  | Screen navigation testing across sample app and PR merge (Next.js, Android WebView)                                       | M    | Jatin Khemchandani                |
| Web SDK                  | Web vitals correctness on SDK SPA                                                                                         | S    | Jatin Khemchandani                |
| Publishing & integration | Package publish                                                                                                           | S    | Shruti Pathak                     |
| Publishing & integration | Documentation update, maintenance, and verification                                                                       | S    | Shruti Pathak                     |
| Publishing & integration | RMG integration: readiness — tenant setup (API key generation, adding members), help tenant integrate in SDK, issue fixes | M    | Shruti Pathak                     |
| Deployment               | Main Web SDK PR review, issue fixes, and merge                                                                            | S    | Shruti Pathak                     |
| Deployment               | Backend deployment                                                                                                        | —    | Jatin Khemchandani                |
| Deployment               | Frontend deployment                                                                                                       | —    | Shruti Pathak                     |
| Deployment               | E2E verification post deployment                                                                                          | —    | Jatin Khemchandani, Shruti Pathak |


**Note:** Deployment rows were not tagged with P/size in the original checklist; they are grouped under P0 as the typical release-critical path. Re-tag if your process uses different priorities.

---

## P1


| Area                     | Task                                                                     | Size | Owners                            |
| ------------------------ | ------------------------------------------------------------------------ | ---- | --------------------------------- |
| Pulse Backend & UI       | Across instruments: verify attributes                                    | M    | Shruti Pathak, Jatin Khemchandani |
| Pulse Backend & UI       | PR review into `main` branch                                             | —    | Jatin Khemchandani                |
| Web SDK                  | Golden flow testing and WebView stability (Android WebView, React, Next) | M    | Shruti Pathak, Jatin Khemchandani |
| Web SDK                  | Testing on iOS app                                                       | L    | Shruti Pathak                     |
| Publishing & integration | Workflow for publish                                                     | S    | Shruti Pathak                     |


---

## P2


| Area               | Task                                 | Size | Owners             |
| ------------------ | ------------------------------------ | ---- | ------------------ |
| Pulse Backend & UI | Mocks update for supporting web data | S    | Jatin Khemchandani |


---

## Owner rollup


| Owner                  | P0                                                                                                                                                                               | P1                                                                                                         | P2                     |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------- |
| **Jatin Khemchandani** | Web vitals implementation and testing (L); screen navigation testing — Next.js + Android WebView (M); web vitals correctness on SDK SPA (S); backend deployment; E2E post-deploy | Instrument attribute verification (M); PR review to `main`; golden flow + WebView stability (M)            | Mocks for web data (S) |
| **Shruti Pathak**      | Package publish (S); documentation (S); RMG integration (M); main Web SDK PR / merge (S); frontend deployment; E2E post-deploy                                                   | Attribute verification (M); golden flow + WebView stability (M); iOS app testing (L); publish workflow (S) | —                      |


---

## Related repo docs (Web SDK)

- Publishing: `pulse-web-otel/docs/publishing/SPEC.md`, `pulse-web-otel/docs/publishing/QUICKSTART.md`
- Integration: `pulse-web-otel/docs/instrumentations/integration/SPEC.md`
- Next.js: `pulse-web-otel/docs/instrumentations/nextjs-integration/SPEC.md`

