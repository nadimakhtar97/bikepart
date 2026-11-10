# Shop schema history

`com.nadim.bikepart.shop.ShopDatabase/1.json` is the first persistent shop schema.
The starter application had no database, so there is no earlier shop schema to
migrate. First use seeds four categories in the same initialization transaction
as the dataset identity and revision zero. Reopening never reseeds an existing shop.

Room is accessed through `Shop`. Its database, DAO and entities are internal and
the database instance is private. Product creation saves the generated UUID and
unique permanent SKU, whole-rupee price, balance (including zero), positive opening
history and checked revision advance in one transaction. A zero opening has no
movement. The opening table records the origin and timestamp of positive balances.
Description edits never rewrite identity, price, balance or opening history.

For each future schema change:

1. Increment the Room version and commit its generated JSON alongside all old JSON.
2. Register an explicit data-preserving migration in `Shop.open`; never use
   destructive migration fallback or delete the shop database to fix an upgrade.
3. Test upgrades from every supported historical schema using a file-backed
   fixture, verify identifiers, prices, balances, history, settings and revisions
   through `Shop`, and run SQLite foreign-key and integrity checks.
4. Extend workflow and ledger reconciliation coverage. Extend backup/restore
   coverage once that feature exists; it is not present in this first slice.

Signed 64-bit integer input is parsed without rounding. Only ASCII digits with
optional surrounding whitespace are accepted. Monetary/quantity grouping is a
display concern, never an input conversion. Revision overflow rejects and rolls
back the entire write. The schema deliberately uses integer columns rather than
floating-point stock or money.
