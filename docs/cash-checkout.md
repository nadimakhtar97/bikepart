# Cash checkout and durable receipts — implementation ticket 11

Open a nonempty cart and choose Checkout. Entry uses the existing draft save
barrier, then retains the reviewed edit version through activity recreation.
Whole-rupee cash is required; Exact amount fills the cart total and Change is
calculated with checked integer arithmetic. Cash below the total, invalid input
and overflow leave the saved cart intact. Zero-priced lines remain valid.

Complete cash sale checks every line against stock in one Room transaction.
Any shortage lists requested and available quantities and saves no sale or
deduction. Edit cart explicitly reloads the saved cart for correction. A stale
cart version is rejected, including edits made while checkout is displayed.
Edits and repeated completion taps are disabled during the pending save.

One transaction saves the receipt and line snapshots, cash/change, signed stock
movements and before/after balances, completes the cart and advances the business
revision. The cart identity uniquely identifies its sale: a retry first returns
the original receipt, even with an old edit version, changed stock or malformed
retry tender. Catalog changes cannot rewrite receipt title, SKU, description,
category, fitment or quoted prices. Required catalog-field improvements do not
block sales of existing stock.

Before commit, an interruption leaves the entire unfinished cart recoverable.
After commit, reopening by cart identity returns the same receipt. A saved cart
or checkout route automatically shows its receipt if that cart has completed.
After a fresh app launch, More → Sales history opens the durable local receipt.
Product details also show sale stock history with quantities and balances.
Sale confirmation describes local persistence independently of future backup
and printing capabilities. The receipt is a cash-sale record; physical receipt
printing and backup integration are separate tickets.

## Verification

`CheckoutTest` exercises the public Shop boundary with file-backed Room: retained
quotes, tender/change, invalid and overflowing cash, stale versions, empty carts,
all-line shortages, explicit correction, zero-price goods, maximum tender,
competing carts for the last piece, immutable history and retry after reopening.
SQLite failure triggers interrupt line insertion, balance updates, cart completion
and revision writes; revision overflow is also checked. Each rejected transaction
is reopened and compared with the full pre-checkout state. Lost-response recovery
closes/reopens storage after a successful checkout and retries its original cart
identity. Concurrent same-cart retries return the same sale. Sale movements
reconcile with opening stock, corrections and the resulting balance.

`CheckoutWorkflowTest` uses actual app UI and Room storage for the cashier flow,
save-barrier entry, insufficient tender, exact amount, shortages, explicit cart
editing, change, durable receipt navigation, activity recreation and fresh launch
after catalog changes. A deterministic lost-response check uses the existing
ViewModel write boundary to throw immediately after the real checkout commits,
before state refresh or its success callback. The app recovers the receipt from
observable committed state and does not show a generic save failure on that
receipt. Its visual workflow captures Light/Dark at normal and
200% system text, long titles/customer labels, maximum totals and keyboard-open
validation. Shared Material typography, colors, spacing, grouped surfaces and
buttons follow the existing Now in Android adaptation; inherited design decisions
and theme tokens are unchanged.

Physical-phone and TalkBack listening checks have not been performed.

## Standards

Review against starting commit `d26294a` found one documented issue: the initial
sales-history empty state omitted its bundled icon. It now uses `ShopEmptyState`.
No documented violations remain. Duplicate whole-number parsing and repeated
route strings were nonblocking heuristic observations consistent with existing
patterns; broader parser/navigation refactoring is deferred.

## Spec

Review found one validation gap: reopening after a successful checkout did not
deterministically interrupt the commit-to-acknowledgement window. The new app
write-boundary failure test covers that window. No missing checkout behavior,
unasked scope or incorrect transaction/retry behavior remained in the follow-up
review.

Review totals: Standards — one violation fixed, two nonblocking observations;
Spec — one validation gap fixed. Neither axis has an outstanding material finding.

## Results — 2026-11-11

- Full instrumentation run: 71 tests, zero failures/errors/skips, including all
  nine historical-schema migrations and five checkout persistence tests. This
  run preceded the final review fixes and lost-response UI test.
- Final checkout UI run after review fixes: all three tests passed, including
  deterministic failure after commit/before acknowledgement, stale-cart rejection
  after recreation, retry/reopen recovery and the repeated visual workflow.
- Two unit tests passed. Android lint passed with zero errors and 12 warnings.
  Kotlin app and instrumentation-test compilation passed after review fixes.
- All 94 theme contrast checks passed; minimum normal-text contrast was 4.55:1.
- Actual checkout and receipt screenshots were inspected in Light/Dark at
  normal and 200% system text, including maximum totals, long names and
  keyboard-open validation. Local screenshots are in `.scratch/checkout-visual/`.

The full suite was run once; final review changes were verified by the complete
checkout UI class and Kotlin compilation rather than a second full suite run.
