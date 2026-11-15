# UI ownership and contracts

The files below are presentation boundaries. All remain in `com.nadim.bikepart.ui`;
functions retain their existing parameters and callbacks. The shell owns route,
selection and saveable state, and invokes feature entry points. A feature can
change its presentation without editing the shell when that contract is unchanged.
Changes to a route or a callback contract require coordination with the shell owner.
Domain models, `ShopViewModel`, Room and services are outside these boundaries.
Ticket numbers refer to the implementation issues under specification #22.

| Ticket / owner | Presentation files / contract |
| --- | --- |
| #25 Theme and buttons | `theme/Theme.kt`, `theme/ShopTokens.kt`, `ShopButtons.kt`, `ShopSecondaryControls.kt` |
| #26 Form, selection and feedback controls | `ShopFeedback.kt`, `ShopFeedbackControls.kt`; extend shared controls here, with feature-specific form composition owned by its feature |
| #27 Collections, search and menus | `ShopCollections.kt`, `ShopLayout.kt`; row actions and labels remain caller supplied |
| #28 Shell and navigation | `ShopApp.kt`; route strings, saveable-state keys, back behavior and callbacks |
| #29 Stock search/results | `StockScreens.kt`, `StockSearch.kt` |
| #30 Product forms/details | `ProductForm.kt`, `ProductDetails.kt`, `StockThreshold.kt`, `ProductPhotos.kt`, `CategoryFields.kt`, `CatalogManagement.kt`, `CategorySettings.kt`, `FindingDetails.kt` |
| #31 Catalog/templates | `Templates.kt`; consume #30's `ProductForm`, `ProductFormInput` and `FindingLookups` contracts |
| #32 Receiving/corrections/archive | `StockArrivals.kt`, `StockCorrections.kt`, `ArchivedProducts.kt` |
| #33 Scanner/manual picker/aliases | `ContextualScanner.kt`, `ScanCamera.kt`, `BarcodeLinker.kt`, `ScanManualPicker.kt` |
| #34 Labels/printing | `LabelPreview.kt`, `LabelPrintControls.kt` |
| #35 Carts | `Carts.kt` |
| #36 Checkout/receipt | `Checkout.kt`, `SaleReceipt.kt`; retain `SaleStockHistory` for product history callers |
| #37 Sales history | `SalesHistory.kt`, `SalesLog.kt`; consume #36's receipt navigation callback |
| #38 Home/general More | `HomeScreen.kt`, `MoreScreen.kt`; consume appearance and feature-specific settings contracts |
| #39 Local backups/storage | `BackupStorageControls.kt`, `LocalBackupControls.kt` |
| #40 Cloud backup/restore | `DriveBackupControls.kt`; keep authorization and restore callbacks available to storage/replacement sections |
| #41 Phone replacement | `PhoneReplacementControls.kt` |
| #42 App updates | `UpdateControls.kt` |

`ShopApp.kt` retains callback wiring and the existing route dispatcher. Feature
owners should edit their entry points, not move workflow state or service calls
into shared controls. The split does not add Fluent dependencies or change visual
fallbacks; existing Material controls remain the baseline until each migration.

## Evidence ownership

Existing `ShopWorkflowTest` remains the shared actual-app, Room-backed regression
suite. Feature owners keep new evidence in feature-named tests rather than
modifying shared scenarios. Relevant existing suites include `CartWorkflowTest`,
`CheckoutWorkflowTest`, `ScanWorkflowTest`, `BackupWorkflowTest`,
`RestoreWorkflowTest` and `PhoneReplacementWorkflowTest`. Device runs and UI
inspection must be scheduled serially on the same device. Record actual light and
dark screenshots at normal and 200% font scale, keyboard and accessibility
checks for each migrated feature; compilation alone is not rendering evidence.

## Shared contracts

- Keep function names, parameters, default arguments, callback meaning and
  accessible action labels stable while replacing presentation. Feature owners
  consume the shared families; changes to shared signatures belong to their owner.
- `StockSearch` owns `StockProductRow` and `StockHealthIndicator`, consumed by
  product details, carts and the manual picker. Coordinate those APIs with #29.
- `ProductForm` owns `ProductFormInput` and its validation/focus composition;
  `FindingDetails` owns `FindingLookups` and `idSetSaver`. Templates consume these
  APIs through #30 rather than forking product-form logic.
- Preserve `ShopApp` route values and the `stock:<session>` and
  `form:<session>:<route>:<selection>:<catalog>` saveable-state scopes. Keep writes and
  service handoffs in the existing callbacks. This extraction changes file
  ownership while retaining the existing composition and callback wiring.
- Appearance rows currently live in `MoreScreen`. #25 owns their initial theme
  pilot change; #38 consumes that completed contract. Existing feature-local
  inputs/selectors stay with their feature until #26 supplies compatible controls.
- Backup/storage/replacement controls already have separate entry points.
  Cloud authorization remains in `DriveBackupControls`, which invokes the storage
  and replacement sections through their existing callback interfaces.
