# Project guidance

## Git Flow

Use Git Flow for all project work:

- `main` contains production-ready code. Do not commit directly to it; publish app
  releases from version tags on `main` as described in [release instructions](docs/releases.md).
- `develop` is the integration branch. Start feature branches from `develop`, and
  name them `feature/<short-description>` (or `bugfix/<short-description>` for
  non-urgent fixes). Merge completed work back into `develop` through a pull request.
- Cut `release/<version>` branches from `develop` when preparing a release. Limit
  them to release fixes and metadata, then merge to `main` and back to `develop`.
  Create the version tag on the resulting `main` commit.
- Cut `hotfix/<short-description>` branches from `main` for urgent production
  fixes. Merge each hotfix to `main` and back to `develop`, then tag the release
  commit on `main` when a shipped app version is required.
- Do not merge feature or bugfix branches directly into `main`. Keep branch names
  lowercase and use hyphens in descriptions.

For Android UI, themes, navigation, shared components or visual validation, read
[UI guidelines](docs/ui-guidelines.md) before editing. They identify the approved
Figma reference, token precedence, shop adaptations and verification requirements.

Use [GLOSSARY.md](GLOSSARY.md) for shop vocabulary and
[schema history](app/schemas/README.md) for persistence changes.

## Agent skills

### Issue tracker

For specs, tickets and issue operations, use GitHub Issues in
nadimakhtar97/bikepart. Read docs/agents/issue-tracker.md.

### Triage labels

For issue classification and readiness, use the roles in
docs/agents/triage-labels.md.

### Domain docs

Before domain exploration or design, read docs/agents/domain.md.
This repo uses one root glossary and docs/adr/ for decisions.
