# Persistent quoted carts — implementation ticket 10

Cashiers can start a New sale from Home or Carts, optionally label the customer,
add products through catalog search, change whole-piece quantities, and return
to the cart list to serve another customer. Cart names use a durable increasing
number. Discard asks for confirmation and removes only the unfinished bill.

Cart rows show the product title, permanent SKU, on-hand balance, other-cart
demand, quoted unit price and saved line total. The cart total uses Indian
grouping and whole rupees. Other-cart demand is informational: several carts
can request the last piece, and carts never reduce or reserve stock.

Each new line captures the current selling price in its database transaction.
Changing a product's selling price updates matching lines in every unfinished
cart. Quantity edits and additions to an existing line keep its current quote.
Removing the whole line ends it; adding it again captures the current product
price. Completed sale snapshots are unchanged by later price edits. Cash payment
and checkout are separate implementation ticket 11.

## Persistence and save behavior

Room schema 9 adds carts, product-referencing lines and a durable numbering
sequence. Its explicit additive migration preserves existing shop state. See
[schema history](../app/schemas/README.md) for the table and transaction contract.
Backup/restore is not implemented yet, so there is no snapshot codec to extend.

Field edits autosave after a 450 ms pause. During a local commit, structural
actions and navigation are disabled. The screen reports unsaved/saving state until commit
and displays saved totals independently of pending input. Returning to the cart
list flushes pending edits first. A rejected draft stays visible; staff can
correct it, retry, or explicitly reload the saved cart. Edits not yet acknowledged
as saved can be lost if the process is terminated before commit.
The input session and raw field text stay in place through successful autosaves,
so the keyboard remains open and a pause after a space does not join words.
The committed customer label is trimmed; reloading or reopening shows that saved
form. Cart autosave deliberately buffers further text input during a commit,
adapting the ordinary explicit-save form pattern so typing is not dropped and
the keyboard session stays alive. Once the captured draft commits, any newer
draft is saved against that exact committed version before navigation proceeds.
An intervening writer's version is rejected rather than silently rebased.

Every changed cart edit, creation, discard and catalog price change advances the
business revision atomically. Repricing a line also advances its cart's edit
version. Cart edits advance a per-cart edit version.
Saving or discarding with an old version fails without overwriting current work.
An unchanged save writes nothing. A discarded cart cannot be recreated by an
old draft. The ViewModel's `flushCart` is the save barrier for navigation and the
future checkout entry point.

Quantities accept positive ASCII whole numbers within signed 64-bit bounds.
Line multiplication, cart-total addition, quantity addition, numbering and
version advances are checked. Invalid input and overflow retain the previously
saved cart and stock. Other-cart demand uses an exact arbitrary-precision sum
because independent valid carts can together exceed one 64-bit quantity.

## Verification

The tests use the public `Shop` boundary and actual app UI with file-backed Room;
there are no mocked cart repositories. `CartsTest` covers all-cart repricing,
removal/re-add, reopening, competing demand, numbering, stale/concurrent writes,
invalid inputs, quantity/total overflow and transaction interruption. Revision
failures are injected at the SQLite storage boundary, with durable outcomes
checked through `Shop` after reopening.

`CartWorkflowTest` covers customer labels, manual search/selection, editable
quantities, cart-list switching, no reservation, repricing after catalog price
changes, activity recreation and fresh activity launch, stale-edit
rejection/reload, removal/re-add and discard. It also captures actual app
screens in Light/Dark at normal and 200% system text, including long product and
customer names, maximum whole-rupee totals, empty carts and validation with the
keyboard open. Product-specific accessibility descriptions accompany concise
quantity/removal labels to avoid long floating labels at large text sizes.
The cashier workflow holds the actual SQLite writer lock to verify that text
entered during a pending commit is buffered, saved and retained through an
immediate return to the cart list.

Historical fixtures exercise upgrades from schemas 1–8, preserving catalog,
stock, history, settings and business revisions, with SQLite integrity and
foreign-key checks. Schema 2–8 fixtures additionally create and reopen a quoted
cart after migration.

Physical-phone and TalkBack listening checks have not been performed. Theme
tokens are inherited unchanged from the approved Now in Android adaptation;
the cart screens reuse grouped surfaces, stock rows and shared button patterns.

The existing stock-correction workflow's click helper now waits for the target
to be visible after scrolling, before its unchanged visibility assertion and
semantic click. The prior immediate assertion could run while keyboard insets
were still changing after quantity entry. The wait remains bounded at five
seconds; persistently missing or hidden controls still fail.

## Results — 2026-11-11

- Full instrumentation run: 63 tests, 61 passed initially. The existing search
  filter workflow passed an unchanged focused rerun. The stock-correction
  workflow reproduced the keyboard-viewport timing failure and passed after the
  bounded visibility wait described above. No product behavior changed for
  either existing workflow.
- Final focused runs: both cart UI workflows, four cart persistence tests and
  all eight historical-schema migration tests passed. These include buffered
  typing under an actual SQLite writer lock, exact committed-version chaining,
  stale edits, retained quotes, interruption rollback and unchanged stock.
- Two unit tests passed. Android lint completed with zero errors and 12 warnings.
  Theme validation passed all 94 checks, with minimum normal-text contrast 4.55:1.
- Actual Light/Dark app screenshots at normal and 200% text were inspected,
  including long names, maximum totals, empty carts and keyboard-open errors.
  The keyboard remains open and the quantity label/remove button no longer
  overlap or clip at large text.
- External restart: created `RestartBolt` and `Cart 1` through the app UI, saved
  customer `RestartCheck` and two pieces quoted at ₹80, then ran
  `adb shell am force-stop com.nadim.bikepart`. PID 28638 disappeared; launching
  `com.nadim.bikepart/.MainActivity` created PID 29325. Reopening the cart showed
  identical application UI content: customer, permanent SKU, quantity 2,
  quote ₹80, total ₹160 and on-hand stock 0. The cart was then discarded through
  its confirmation dialog and the empty cart list verified. Local before/after
  layout evidence is in `.scratch/carts-visual/`.
- Standards and spec reviews found no remaining material issues after fixes.

The complete instrumentation suite was not rerun after the focused fixes; the
initial full report and the final focused results are recorded separately.
