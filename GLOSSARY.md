# Spare Parts Shop

The shop's inventory and cash-sale vocabulary.

## Language

**Stocked product**:
A distinct kind of item whose quantity the shop tracks separately. Brake pads and loose nuts or bolts have separate stock records.

**Archived product**:
A stocked product removed from active selection while its details and history remain readable. Archival requires zero stock and no references from unfinished carts.

**SKU**:
A permanent, automatically assigned identifier for a stocked product. Changes to its descriptive details do not change its SKU.

**Shared item QR**:
A QR label identifying a stocked product, including loose items in a bin. It does not identify each individual piece; the cashier supplies the quantity.
_Avoid_: Per-piece QR, when referring to a shared label

**Barcode alias**:
A manufacturer or other external barcode explicitly linked by staff to one local stocked product or product template, allowing it to be found by scanning.

**Cart**:
An unfinished sale that staff can pause and resume while serving other customers.

**Quoted unit price**:
The selling price retained for a product line in a cart, including additional quantities on that line. Removing the whole line ends that quote; adding the product again uses its current selling price.

**Opening stock**:
The starting quantity of a stocked product when the shop begins tracking it.

**Low-stock threshold**:
An optional whole-number quantity set for a stocked product. Stock at or below this quantity is low; zero stock is always out of stock, even without a threshold.

**Stock arrival**:
A received quantity of an existing stocked product, with the purchase cost of that arrival retained in its history.

**Stock correction**:
A reasoned adjustment for a physical count difference, damaged stock or lost stock. It does not represent a customer refund.

**Sale**:
A completed cash sale retained in the shop's history. An unfinished customer bill is a cart.

**Product template**:
A reusable set of catalog details copied into a new stocked product for editing. Later template changes do not change products already created from it.

**Fitment**:
The motorcycle compatibility assigned to a product by the shopkeeper or staff, potentially covering several makes and models. Fitment starts as unknown until staff identify it.

**Category attribute**:
A descriptive field defined for products in a category, such as bolt diameter or oil bottle volume. It may be required or optional.

**Product custom field**:
A descriptive field added to one product, which becomes a category attribute only when staff explicitly choose that broader scope.

**Storage location**:
A bin, shelf or other place where staff can find a stocked product. A product can have several locations while retaining one combined stock count.

**Shop backup**:
A recovery copy of the shop's catalog, stock and sale history, unfinished carts, settings and associated files. It includes only changes successfully backed up; changes still held solely on a lost phone cannot be recovered from it.

**Restore point**:
A complete shop backup representing one consistent shop state, available for recovery after its required contents have been backed up successfully.

**Active shop phone**:
The single phone designated for current shop work and backups. Replacing it requires explicitly activating the replacement and retiring the old installation.
