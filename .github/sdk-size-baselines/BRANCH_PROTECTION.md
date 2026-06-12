# Branch protection setup (maintainers)

Apply on the default branch after baselines are seeded via **workflow_dispatch**.

## Required status check

- **Mobile SDK size delta** (job `required-status-check` in [mobile-sdk-size-delta.yml](../workflows/mobile-sdk-size-delta.yml))

## Code owners

When `.github/sdk-size-baselines/**` changes, require **review from Code Owners**:

**One** approval from any of the three is sufficient.

## GitHub Actions

- Allow GitHub Actions to create and approve pull requests (or permit `github-actions[bot]` pushes to PR branches) so `update_baselines` can commit to the PR head.

## Labels (create in repo settings)

- `sdk-size-delta`
- `sdk-size-baseline-update` (optional: restrict to maintainers)
