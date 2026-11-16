# Demo brand and Accent settings (#47)

Work in progress on the completed #25 pilot. Source research and exact revisions
are in [demo-source.md](demo-source.md); the accepted customization amendment is
[fluent-scope-amendment.md](../fluent-scope-amendment.md).

Seven complete upstream alias themes are offered in demo order. Brand and Accent
are installation-local SharedPreferences settings owned by the app view model;
they never write Room, advance the shop revision, or enter shop backup formats.
The existing System / Light / Dark field remains authoritative through
`Shop.setAppearance`, including its existing backup/restore contract.

The root receives selected alias tokens explicitly. Native radio/switch token
methods in core0.3.11 and controls0.3.3 read scoped aliases on composition; their
`themeID` reads trigger recomposition and do not memoize colors. No global theme
singleton changes or keyed subtree resets are introduced. Actual rendered
color changes and unfinished input preservation still require runtime evidence.

Final runtime/effective-contrast evidence and limitations will be recorded here
when checks finish. No release is published by this slice.
