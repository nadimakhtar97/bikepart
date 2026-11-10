# Now in Android Default theme

From the repository root, run `npm ci --prefix tools/theme`, then
`node tools/theme/generate.mjs`. The locked Google Material color utilities
generate light/dark Material roles from the inspected `figma-tokens.json`,
including neutral surface containers and fixed accent roles missing from the
older reference. Wallpaper colors are never consulted for this Default theme.

The current bound variables in the user's Figma phone/settings nodes override
older palette swatches. See [UI guidelines](../../docs/ui-guidelines.md) for the
exact source nodes, discrepancies and shop-specific adaptations.

Run `node tools/theme/validate.mjs` to check text/background and essential outline
contrast in the generated Kotlin roles. Device UI coverage remains separate;
palette contrast checks alone do not prove accessible layouts or reading order.

Sources: [Google Material color utilities](https://github.com/material-foundation/material-color-utilities)
and [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).
The four navigation vectors are Google Material Symbols (Apache 2.0) from
the [official icon repository](https://github.com/google/material-design-icons).
