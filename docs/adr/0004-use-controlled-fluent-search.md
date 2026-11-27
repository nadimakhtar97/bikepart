# Adapt Fluent SearchBar for screen-owned queries

Use one shared search field adapted from Microsoft Fluent UI Android for shop
list search and lookup selectors. Keep the published Fluent dependency pins
and use their search tokens. The published SearchBar owns its query and clears
it when the component enters composition. The shared adaptation lets each
screen restore and explicitly clear its query, and lets Home open Stock with a
submitted query. See [upstream source and license](../../third_party/fluent-android-sources.md).
