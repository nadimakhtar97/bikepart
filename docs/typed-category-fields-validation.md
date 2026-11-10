# Typed category fields

Ticket 02 adds category-owned text, exact decimal, single-choice and multiple-choice
fields to stocked products. Staff can create categories, field definitions and
choice options inside the product form. These writes commit independently, so
cancelling the product draft preserves the lookup setup. Required settings and
unit labels belong to definitions; edits validate the selected category's fields.

New shops receive eight optional fields: brake-pad Position (Front/Rear), nut
Thread diameter and Thread pitch (mm), bolt Thread diameter, Thread pitch and
Length (mm), and oil Bottle volume (ml) and Viscosity grade. Existing shops gain
the capability through an additive schema migration without guessing the meaning
of categories that staff may have renamed.

The [schema history](../app/schemas/README.md) documents input limits, exact-value
storage, missing/empty distinctions, decimal-aware ordering, lookup normalization
version 1, owner scopes, migration behavior and revision transactions. Definition
evolution and product-owned custom fields belong to ticket 03; backup/restore is
a later slice and has not landed.

Validation uses the actual Compose app with file-backed Room storage, plus public
`Shop` workflows and a file-backed version-1 fixture generated from the committed
schema. The checks cover all four types, exact close/reopen values, numeric rather
than lexical ordering, missing measurements, required input, cross-owner option
rejection, normalized scoped reuse, inline lookup survival after cancellation,
activity recreation of an unsaved form, integer stock/money constraints, storage
interruption and revision-overflow rollback. Migration checks retain UUID/SKU,
dataset, price, balance, opening history, appearance and revision and run SQLite
foreign-key and integrity checks.

Final suite, visual inspection and review results are recorded below after running.
