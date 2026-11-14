# UI refinement brief

Status: implemented and validated, 2026-11-14. The user confirmed the design
direction and all four screen decisions before implementation.

## Confirmed direction

- Continue the approved Now in Android guidelines and use the user's 40 attached
  reference application screenshots for additional layout and interaction details.
- Prioritize a minimal, clean appearance and better use of existing components:
  floating action buttons, three-dot overflow menus, manual search with a trailing
  QR scan action, filter chips, clearer icons and sales history grouped by date.
- Cover layouts, views, shared components, theme, colors, strings and icons.
- Preserve every existing function, business rule and workflow capability.

## Reference observations

The screenshots show compact product summaries with trailing amounts and menus,
search fields with scan actions, filter sheets, creation FABs, grouped settings,
quantity controls, empty states and chronological history. These are design
inputs, not instructions to add the reference application's business features.

The reference also includes editable orders, unpaid/partial payments, tax,
discounts, customer records and subscriptions. The current shop has autosaved
unfinished carts and immutable completed cash sales. Its glossary remains the
authority for these meanings; the requested UI work preserves these rules.

## Confirmed screen decisions

- Retain Home / Stock / Carts / More. Sales history stays directly accessible
  from Home and More.
- Stock has an Add product FAB; Carts has a New sale FAB. Save and Checkout stay
  visible buttons. Stock rows expose edit, receiving, copying and label shortcuts
  through three-dot menus; detail pages preserve their full existing actions.
- Search fields have trailing scan actions for stock, cart product selection and
  receiving/catalog lookup. Existing scanner context and manual fallbacks remain.
- Stock health/category chips stay visible; advanced make/model/location/decimal
  filters move into a scrollable sheet, with active selections and clearing visible.
- Sales history shows newest receipts first under Today / Yesterday / dated
  headings, daily counts and totals, time, receipt identifier, customer and amount.
  Search and inclusive date ranges narrow the log; tapping opens the receipt.
- Use bundled official outlined Material Symbols and existing Now in Android
  tokens. Shared search surfaces become quieter and rounded. Existing form,
  settings, appearance and adaptive navigation behavior remains available.

## Sources

- [Project UI guidelines](ui-guidelines.md)
- [Shop vocabulary](../GLOSSARY.md)
- User-attached screenshots in this design interview, 2026-11-14.
- [Official Material Symbols](https://developers.google.com/fonts/docs/material_symbols)
  and [source assets](https://github.com/google/material-design-icons).

No glossary additions or ADRs are warranted yet: no domain meaning has changed,
and these presentation decisions remain readily reversible.

Implementation checks and remaining device-validation limits are recorded in
[UI refinement validation](ui-refinement-validation.md).
