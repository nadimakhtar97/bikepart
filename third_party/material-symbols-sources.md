# Material Symbols assets

The shop bundles Google's Material Symbols as Android vector resources, licensed
under [Apache 2.0](material-symbols-LICENSE.txt). Icons render offline and inherit
the app's Material content color.

The UI refinement adds the outlined 24px versions of `qr_code_scanner`,
`content_copy`, `expand_more` and `calendar_month` from the official repository:

https://github.com/google/material-design-icons/tree/master/symbols/android

Each corresponding source file is at
`symbols/android/<name>/materialsymbolsoutlined/<name>_24px.xml` and is bundled
unchanged as `app/src/main/res/drawable/ic_<name>.xml`.
