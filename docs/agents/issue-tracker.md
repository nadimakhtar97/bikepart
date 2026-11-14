# Issue tracker: GitHub

Specs and implementation tickets live in GitHub Issues:
https://github.com/nadimakhtar97/bikepart

## Operations

Prefer the connected GitHub plugin where it supports the operation.
Use authenticated GitHub CLI or API operations for missing capabilities.

Before creating issues, verify the target is nadimakhtar97/bikepart.
When a skill says to publish a spec or ticket, create a GitHub issue.
When fetching a ticket, read its full body and comments.

Use the configured triage labels from triage-labels.md.
Create implementation tickets in dependency order.
Use native sub-issue and blocking relationships where supported.
Otherwise, record Parent and Blocked by issue links in the body.
A ticket is available when all its blockers are closed.

Pass multiline CLI bodies through --body-file.
After publishing, return the issue links.

## Pull requests as a triage surface

PRs as a request surface: no.
