# Catalog definition evolution

Ticket 03 adds product-owned custom fields, explicit conflict-checked promotion,
required-field completeness flags, retirement and type replacement. Staff use
Product → Manage fields for definition changes and Edit details for values.
Category changes offer explicit compatible text/decimal copies and always retain
previous category values under their original definitions. Choice values retain
definition-owned option identities and require explicit selection in a new field.

The existing Now in Android theme, tokens, grouped forms, labelled fields and
minimum 48dp controls are reused. Product-only fields have an explicit scope label.
Historical and retired values remain readable. Incomplete records keep their
stock and selling price; required fields are enforced when saving an edit.
Sales and backup/restore are later implementation slices.

The [schema history](../app/schemas/README.md) describes version 3, owner-scoped
uniqueness, retired-name reservation, immutable history identities, migration
preservation and revision transactions. Repeated category moves retain separate
history occurrences rather than overwrite an earlier value from the same field.

Validation uses the actual Compose app and file-backed Room databases. Public
workflows cover custom ownership, Unicode scoped reuse, promotion collisions,
required completion, retirement retention and selection rejection, replacement,
explicit mapping, incompatible units, repeated category changes and close/reopen
preservation. A deterministic failure at revision commit checks rollback of all
new mutation paths. Version-1 and version-2 schema fixtures retain catalog identity,
money, balance, opening history, settings, dataset, revisions and typed values,
then run SQLite integrity and foreign-key checks.

The app workflow creates a custom field, edits its value, resolves a promotion
collision explicitly, changes required status, retires a populated definition,
and verifies that a newly required field blocks only an incomplete edit.
Visual coverage captures actual history and management screens in both themes
at normal and 200% font scale, including a long name, large price and keyboard.
Captures are local verification artifacts, not shipped assets.

Manual TalkBack traversal and physical-device checks have not been performed.

## Review

The code-review skill ran independent Standards and Spec reviews against starting
commit `e376eef`. The Spec reviewer found no missing requirements, correctness
issues or scope creep. The Standards reviewer found one field-validation issue:
definition setup announced failures without marking the offending control.
Definition names and replacement types now produce typed `ShopValidation` errors,
and the definition forms display those errors with the existing announced,
scrolling `FormError` pattern. The app test checks the invalid field's semantics.

The review also suggested consolidating owner-scoped definition creation; category
and custom creation now share that transaction-internal implementation. A broader
ownership type was an optional design observation and is deferred while the
existing nullable product owner and persisted scope key remain explicit.
