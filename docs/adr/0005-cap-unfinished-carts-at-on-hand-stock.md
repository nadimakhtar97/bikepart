# Cap unfinished carts at on-hand stock

Each unfinished cart can contain up to the current on-hand stock of each product. Carts do not reserve stock, so multiple carts can request the same pieces. Checkout still validates the latest stock before completing a sale.

When stock drops below a saved quantity, immediately reduce the affected lines in all unfinished carts, including paused carts. Remove any line reduced to zero and tell staff what changed when they open the cart. This keeps paused carts within current stock limits and means a saved cart can change while staff are away.

Reject additions and quantity increases that exceed stock and preserve the saved quantity. Manual selection and scanning report how many more pieces can be added. In the cart, tapping minus at one piece or committing a typed zero removes the line. Clearing the Pieces field while typing leaves the line in place. The separate Remove line button is removed.
