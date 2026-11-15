# UI ownership and contracts

The files below are presentation boundaries. All remain in `com.nadim.bikepart.ui`;
functions retain their existing parameters and callbacks. The shell owns route,
selection and saveable state, and invokes feature entry points. A feature can
change its presentation without editing the shell when that contract is unchanged.
Changes to a route or a callback contract require coordination with the shell owner.
Domain models, `ShopViewModel`, Room and services are outside these boundaries.

| Owner | Presentation files / contract |
| --- | --- |
| Theme and buttons | `theme/Theme.kt`, `theme/ShopTokens.kt`, `ShopButtons.kt`, `ShopSecondaryControls.kt` |
| Feedback and form controls | `ShopFeedback.kt`, `ShopFeedbackControls.kt`; feature-specific form fields remain with the form that owns them |
| Collections, search and menus | `ShopCollections.kt`, `ShopLayout.kt`; row actions and labels remain caller supplied |
| Shell and navigation | `ShopApp.kt`; existing route strings, saveable-state keys, back behavior and callbacks are the integration contract |
| Home | `HomeScreen.kt` |
| More and settings | `MoreScreen.kt`, `CategorySettings.kt`, `FindingDetails.kt`, `UpdateControls.kt` |
| Stock and product | `StockScreens.kt`, `ProductForm.kt`, `StockSearch.kt`, `ProductPhotos.kt`, `CategoryFields.kt`, `CatalogManagement.kt`, `Templates.kt`, `StockCorrections.kt`, `LabelPreview.kt`, `LabelPrintControls.kt` |
| Carts and checkout | `Carts.kt`, `Checkout.kt` |
| Sales history and receipt | `SalesHistory.kt`, `SaleReceipt.kt`, `SalesLog.kt` |
| Scanner and receiving | `ContextualScanner.kt`, `ScanCamera.kt`, `BarcodeLinker.kt`, `ScanManualPicker.kt`, `StockArrivals.kt` |
| Backup, restore and replacement | `BackupStorageControls.kt`, `LocalBackupControls.kt`, `DriveBackupControls.kt`, `PhoneReplacementControls.kt` |

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
