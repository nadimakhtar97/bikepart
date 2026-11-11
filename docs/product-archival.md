# Safe product archival — implementation ticket 12

Open an active product and choose Archive product. The shop rechecks its current
stock and unfinished-cart references in the same Room transaction as the archive
write. Nonzero stock or any unfinished cart line blocks archival with an announced
explanation. Staff remove the cart line or discard the cart explicitly. Completed
sales do not block archival.

Archived products leave stock search, cart selection, dashboard counts and stock
alerts. More → Archived products provides title/SKU search and read-only details,
including permanent SKU, catalog attributes, fitment, locations, photo, opening
stock, corrections and sale movements. Receipts keep their saved snapshots.
Archival changes no balance or movement and deletes no historical record.

A successful archive advances the business revision once; retries are no-ops.
Previously opened cart, stock and product forms reject writes to an archived
product. SQLite transactions serialize competing archive, count and cart writes.
The 10 → 11 migration defaults existing products to active and preserves data.
Scanning and backup/restore have not landed; their inactive-target fallback and
snapshot integration remain acceptance work in their respective tickets.

Verification uses the public Shop boundary with file-backed Room and actual app
UI. Persistence coverage includes zero/nonzero stock, unfinished and completed
carts, stale attempts through another Shop connection, competing stock/cart
writes, history/receipt preservation, reopen, retry, revision advancement,
storage interruption and revision overflow. Migration coverage upgrades every
supported historical schema and checks foreign keys and integrity. UI coverage
checks live blockers, successful archival, read-only details, recreation and
fresh-launch navigation. Visual coverage captures long titles, maximum prices
and the archive list/search in Light/Dark at normal and 200% system text. These
screens reuse the current Now in Android adaptation without changing its tokens.

Physical-phone and TalkBack listening checks have not been performed.

## Standards

Review against starting commit `3df959c` found one low-priority violation:
archiving the last active product exposed first-use Home/Stock copy despite
retained archived records. Both screens now say “No active products” and point
to More for archived products/history; a genuinely empty shop keeps first-use
copy. No material baseline code smells were reported.

## Spec

The independent Spec review found no missing/partial requirements, unasked
behavior or incorrect implementation. Its inspection covered atomic guards,
active selection, retained history and receipts, revisions, migration and real
persistence/UI workflows.

Review totals: Standards — one copy violation fixed; Spec — zero findings.

## Results — 2026-11-11

- All five archival persistence tests passed, including concurrent cart/count
  writes, stale attempts, retained catalog/ledger/receipts, retry, storage failure
  and revision overflow.
- Both final archival UI workflows passed, including blocker explanations,
  read-only history, fresh launch and the repeated four-appearance visual run.
- Final historical migration run: ten tests passed. Upgrades from versions 2–10
  additionally archive after the existing preservation checks and verify reopen,
  retained receipts/opening history and revision advancement. Version 1 retains
  its original full-chain preservation and integrity checks.
- Full instrumentation run: 80 tests, 79 passed, one checkout UI timeout waiting
  for the cash-entry screen after editing the customer label. The timeout also
  repeated on the first checkout-class rerun; the affected method then passed
  alone and all three checkout tests passed on the subsequent class rerun. Its
  cause remains unconfirmed. No production checkout changes or temporary
  diagnostics remain.
- Two unit tests passed. Final app/test Kotlin compilation and Android lint
  passed; lint reports zero errors and 12 warnings. All 94 contrast checks pass
  with minimum normal-text contrast 4.55:1.
- Actual app screenshots were inspected in Light/Dark at normal and 200% text,
  including long titles, maximum prices, empty search results and keyboard
  insets. Captures are local in `.scratch/archive-visual/`.

The full suite ran once; review copy changes and expanded migration assertions
were checked afterward with final compilation, lint and focused workflows.
