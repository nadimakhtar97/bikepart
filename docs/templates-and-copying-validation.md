# Templates and catalog copying

Ticket 06 adds a searchable local template browser under More and the new-product
form. Staff create templates manually or from a product, edit catalog details and
custom fields, prefill a new product, and retire templates while retaining a
readable reference. Product details offer explicit catalog copying.

`CatalogDraft` contains descriptive data only. New-product forms require separate
stock and price input and offer fresh location selection. Template forms omit
inventory fields. Drafts survive activity recreation without writing a product.
Saving a new product creates fresh identity/SKU and independently owned custom
definitions, complete choice domains and values. Category definitions retain
their shared scope. Later source/template edits leave derived products unchanged.

The additive schema-6 migration and payload format are documented in
[schema history](../app/schemas/README.md). Template payloads contain their own
custom definitions rather than borrowing the product-only definition foreign key.
Immutable photo blobs may be shared safely; published bytes are retained. There
is no backup/restore or sale/arrival implementation yet. Templates are absent from
the stocked-product collection and cannot be passed as product targets.

The UI retains the approved Now in Android tokens, grouped form surfaces, labelled
fields and shared primary buttons. No inherited palette, typography, navigation
style or asset decision changes. Template-specific copy explains the separate
inventory step. Retired shared meanings remain readable but are excluded from
new assignments. Incomplete templates may be saved; product creation still
enforces required fields.

Real file-backed `TemplatesTest` workflows exercise manual and product-derived
templates, both product-copy routes, custom choice ownership, category scope,
photo retention, excluded inventory, opening-history reconciliation, source and
derived independence, retirement, reopen and interrupted revision commits.
Photo transport failure leaves no template. `CategoryMigrationTest` upgrades
historical schemas 1–5 and checks durable state and SQLite integrity/foreign keys.
`TemplateWorkflowTest` exercises actual app forms with Room storage, including
custom-field editing, manual search, draft cancellation and activity recreation.

Inspected 20 API-37 emulator captures of empty/search results, long template names, details,
keyboard insets, maximum whole-rupee price input and announced validation in
light/dark at normal/200% system font size. Captures are local validation artifacts
under `.scratch/templates-visual/`. Physical-device and manual TalkBack checks
are not performed by these emulator workflows.
The 200% review caught clipping when actions repeated a full long template name.
Visible labels now use short Open/Use actions; accessibility descriptions retain
the complete template title. Rechecked captures show readable labels, wrapped
status filters, unobscured keyboard input and visible validation feedback.

## Standards

The independent review against starting commit `2457ab2` identified a documented
validation-pattern violation: custom field name errors were not attached to the
offending input. It also identified duplicated descriptive data in the form
submission as a possible Data Clumps smell. Both were fixed: typed errors retain
definition identity and render inline with announced feedback and automatic
scrolling; form submission carries descriptive data once in `CatalogDraft`.
Template status filters now wrap for large text. The reviewer rechecked the
staged changes and reported no remaining substantive standards findings.

## Spec

The independent review identified one issue: changing a template's category could
discard its previous category values. Template payloads now retain those values
as historical attributes, display them in details and omit them from new-product
prefill. A real persistence regression checks the category move, copy exclusions
and close/reopen. The reviewer rechecked the fix and reported no remaining
requirements, scope or implementation findings.

Review totals: Standards had one documented violation and one optional smell,
both resolved; Spec had one finding, resolved. Neither axis has an open finding.

## Final verification

On 2026-11-11 the final API-37 emulator run passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --no-configuration-cache --console=plain
node tools/theme/validate.mjs
```

Results: both JVM tests, all 43 instrumentation tests, zero lint errors with the
12 pre-existing warnings, and all 94 theme contrast checks (minimum normal-text
contrast 4.55:1). The corrected template app workflows also passed in isolation.
No UI inspection helper was active during the final instrumentation run.

The first full run passed 42/43 instrumentation tests: the new custom-name error
assertion matched both field and supporting-text semantics. Its assertion now
targets the displayed supporting text. Visual inspection separately found and
fixed long template action clipping at 200% text size. The subsequent focused
and complete runs passed after both corrections.
The final lint report also identified the new shared button's optional-parameter
order. Reordered `Modifier` before `enabled` and named the affected arguments;
app/test compilation and lint were rerun after this behavior-preserving cleanup.
