## Summary

<!-- One or two lines in plain English.
Example: Add latency breakdown charts to the session details page. -->

## Context / Motivation

<!-- Why is this change needed?
Link issues / discussion / docs if relevant. -->

- Closes #ISSUE_ID
- Related: #ANOTHER_ISSUE_ID

## Mobile SDK size delta (Android / iOS / RN only)

If this PR changes dependency manifests (`build.gradle.kts`, `package.json`, `yarn.lock`, `Podfile`, etc.) under `pulse-android-otel/`, `pulse-ios-otel/`, or `pulse-react-native-otel/`:

- Add label **`sdk-size-delta`** before merge (triggers size build; **25 KB** limit per artifact).
- If growth **> 25 KB** is intentional: add **`sdk-size-baseline-update`**, then get **one** approval from **@kunalchavhan**, **@anirudhdream11**, or **@chiragSharmaD11** (baseline files on the PR).
- Code-only SDK changes (no manifest diff): **no label** — check passes automatically.

See [`.github/sdk-size-baselines/README.md`](.github/sdk-size-baselines/README.md).

## What Changed

<!-- Short, implementation-focused bullets.
Try to group by component if change spans multiple areas. -->

- Backend:
  - ...
- UI:
  - ...
- Android SDK:
  - ...
- iOS SDK:
  - ...
- React Native SDK:
  - ...
- Deploy / Infra:
  - ...

## Screenshots / Recordings (UI only)

<!-- If UI changes, add before/after screenshots or a short GIF/video.
If not applicable, write "N/A". -->

- N/A
