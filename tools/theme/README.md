# Fixed Shop teal

From the repository root, run `npm ci --prefix tools/theme`, then
`node tools/theme/generate.mjs`. The locked Google Material color utilities
generate light/dark Material roles from `#006A6A`, including neutral surface
containers and fixed accent roles. Wallpaper colors are never consulted.

Run `node tools/theme/validate.mjs` to check text/background and essential outline
contrast in the generated Kotlin roles. Device UI coverage remains separate;
palette contrast checks alone do not prove accessible layouts or reading order.

Sources: [Google Material color utilities](https://github.com/material-foundation/material-color-utilities)
and [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).
The four navigation vectors are Google Material Symbols (Apache 2.0) from
the [official icon repository](https://github.com/google/material-design-icons).
