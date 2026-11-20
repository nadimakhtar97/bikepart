# Pinned source map

Demo activity paths and exact Fluent imports read from Microsoft tag `v0.3.14` (`71be08ca96fd3c1b449920cb8d3e1c268f3dfe35`). Imports include configuration helpers as well as demonstrated controls; they are not a minimal dependency list. XML-backed legacy examples can obtain their controls through layout resources rather than imports.

## 1. V2 Acrylic Pane

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2AcrylicActivity.kt) · [screenshot](01-v2-acrylic-pane.png) · [layout](01-v2-acrylic-pane.layout.json)

```kotlin
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.acrylicpane.AcrylicPane
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.searchbaricons.Office
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens.Foreground2
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.AcrylicPaneInfo
import com.microsoft.fluentui.theme.token.controlTokens.AcrylicPaneOrientation
import com.microsoft.fluentui.theme.token.controlTokens.AcrylicPaneTokens
import com.microsoft.fluentui.tokenized.SearchBar
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.drawer.DrawerValue
import com.microsoft.fluentui.tokenized.drawer.rememberBottomDrawerState
import com.microsoft.fluentui.tokenized.persona.Person
```

## 2. V2 ActionBar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ActionBarActivity.kt) · [screenshot](02-v2-action-bar-rendered.png) · [layout](02-v2-action-bar-rendered.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.listitem.ListItem
```

## 3. V2 AppBarLayout

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2AppBarActivity.kt) · [screenshot](03-v2-app-bar.png) · [layout](03-v2-app-bar.layout.json)

```kotlin
import com.microsoft.fluentui.icons.ListItemIcons
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.listitemicons.Chevron
import com.microsoft.fluentui.icons.searchbaricons.Arrowback
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentColor
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.Icon
import com.microsoft.fluentui.theme.token.controlTokens.AppBarInfo
import com.microsoft.fluentui.theme.token.controlTokens.AppBarSize
import com.microsoft.fluentui.theme.token.controlTokens.AppBarTokens
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.TooltipControls
import com.microsoft.fluentui.tokenized.AppBar
import com.microsoft.fluentui.tokenized.SearchBar
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.persona.Avatar
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillBar
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillMetaData
```

## 4. V2 Avatar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2AvatarActivity.kt) · [screenshot](04-v2-avatar.png) · [layout](04-v2-avatar.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.CutoutStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.persona.Avatar
import com.microsoft.fluentui.tokenized.persona.Group
import com.microsoft.fluentui.tokenized.persona.Person
```

## 5. V2 Avatar Carousel

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2AvatarCarouselActivity.kt) · [screenshot](05-v2-avatar-carousel.png) · [layout](05-v2-avatar-carousel.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.controlTokens.AvatarCarouselSize
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.tokenized.persona.AvatarCarousel
import com.microsoft.fluentui.tokenized.persona.AvatarCarouselItem
import com.microsoft.fluentui.tokenized.persona.Person
```

## 6. V2 Avatar Group

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2AvatarGroupActivity.kt) · [screenshot](06-v2-avatar-group.png) · [layout](06-v2-avatar-group.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme.aliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.AvatarGroupStyle
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.persona.AvatarGroup
import com.microsoft.fluentui.tokenized.persona.Group
import com.microsoft.fluentui.tokenized.persona.Person
```

## 7. V2 Badge

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2BadgeActivity.kt) · [screenshot](07-v2-badge.png) · [layout](07-v2-badge.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.AppBarSize
import com.microsoft.fluentui.theme.token.controlTokens.BadgeType
import com.microsoft.fluentui.tokenized.notification.Badge
```

## 8. V2 Banner

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2BannerActivity.kt) · [screenshot](08-v2-banner.png) · [layout](08-v2-banner.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.notification.Banner
```

## 9. V2 Basic Chip

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2BasicChipActivity.kt) · [screenshot](09-v2-basic-chip.png) · [layout](09-v2-basic-chip.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.Icon
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.StateColor
import com.microsoft.fluentui.theme.token.controlTokens.BasicChipInfo
import com.microsoft.fluentui.theme.token.controlTokens.BasicChipTokens
import com.microsoft.fluentui.tokenized.controls.BasicChip
import com.microsoft.fluentui.tokenized.controls.Label
```

## 10. V2 Basic Controls

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2BasicControlsActivity.kt) · [screenshot](10-v2-basic-controls.png) · [layout](10-v2-basic-controls.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.AliasTokens
import com.microsoft.fluentui.theme.token.ControlTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.tokenized.controls.CheckBox
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
```

## 11. V2 Bottom Drawer

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2BottomDrawerActivity.kt) · [screenshot](11-v2-bottom-drawer.png) · [layout](11-v2-bottom-drawer.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.Background1
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.Background1Pressed
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.Background1Selected
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.BorderType
import com.microsoft.fluentui.theme.token.controlTokens.ListItemInfo
import com.microsoft.fluentui.theme.token.controlTokens.ListItemTokens
import com.microsoft.fluentui.tokenized.SearchBar
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.drawer.BottomDrawer
import com.microsoft.fluentui.tokenized.drawer.DrawerValue
import com.microsoft.fluentui.tokenized.drawer.rememberBottomDrawerState
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.util.KeyboardVisibilityObserver
import com.microsoft.fluentui.util.getStringResource
```

## 12. V2 BottomSheet

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2BottomSheetActivity.kt) · [screenshot](12-v2-bottom-sheet.png) · [layout](12-v2-bottom-sheet.layout.json)

```kotlin
import com.microsoft.fluentui.persona.PersonaListView
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.BottomSheetInfo
import com.microsoft.fluentui.theme.token.controlTokens.BottomSheetTokens
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheet
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheetState
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheetValue
import com.microsoft.fluentui.tokenized.bottomsheet.rememberBottomSheetState
import com.microsoft.fluentui.tokenized.contentBuilder.ItemData
import com.microsoft.fluentui.tokenized.contentBuilder.ListContentBuilder
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
```

Resource references: `R.layout.demo_drawer_content`.

## 13. V2 Buttons

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ButtonsActivity.kt) · [screenshot](13-v2-buttons.png) · [layout](13-v2-buttons.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.FluentTheme.themeMode
import com.microsoft.fluentui.theme.token.AliasTokens
import com.microsoft.fluentui.theme.token.ControlTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonTokens
import com.microsoft.fluentui.theme.token.controlTokens.FABSize
import com.microsoft.fluentui.theme.token.controlTokens.FABState
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton
import com.microsoft.fluentui.tokenized.controls.RadioButton
```

## 14. V2 Card

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2CardActivity.kt) · [screenshot](14-v2-card.png) · [layout](14-v2-card.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme.aliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.controlTokens.BorderType
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.AnnouncementCard
import com.microsoft.fluentui.tokenized.controls.BasicCard
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.FileCard
import com.microsoft.fluentui.tokenized.listitem.ListItem
```

## 15. V2 Card Nudge

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2CardNudgeActivity.kt) · [screenshot](15-v2-card-nudge.png) · [layout](15-v2-card-nudge.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentGlobalTokens
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.notification.CardNudge
import com.microsoft.fluentui.tokenized.notification.CardNudgeMetaData
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillMetaData
```

## 16. V2 Citation

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2CitationActivity.kt) · [screenshot](16-v2-citation.png) · [layout](16-v2-citation.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.BorderType
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheet
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheetValue
import com.microsoft.fluentui.tokenized.bottomsheet.rememberBottomSheetState
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.notification.Citation
```

## 17. V2 ContextualCommandBar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ContextualCommandBarActivity.kt) · [screenshot](17-v2-contextual-command-bar.png) · [layout](17-v2-contextual-command-bar.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens.Foreground2
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.contextualcommandbar.ActionButtonPosition
import com.microsoft.fluentui.tokenized.contextualcommandbar.CommandGroup
import com.microsoft.fluentui.tokenized.contextualcommandbar.CommandItem
import com.microsoft.fluentui.tokenized.contextualcommandbar.ContextualCommandBar
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.drawer.BottomDrawer
import com.microsoft.fluentui.tokenized.drawer.rememberBottomDrawerState
```

## 18. V2 Dialog

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2DialogActivity.kt) · [screenshot](18-v2-dialog.png) · [layout](18-v2-dialog.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.menu.Dialog
```

## 19. V2 Drawer

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2DrawerActivity.kt) · [screenshot](19-v2-drawer.png) · [layout](19-v2-drawer.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.BehaviorType
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.drawer.Drawer
import com.microsoft.fluentui.tokenized.drawer.rememberDrawerState
import com.microsoft.fluentui.tokenized.listitem.ListItem
```

## 20. V2 Label

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2LabelActivity.kt) · [screenshot](20-v2-label.png) · [layout](20-v2-label.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.FluentAliasTokens.TypographyTokens
import com.microsoft.fluentui.theme.token.controlTokens.ColorStyle
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillMetaData
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillTabs
```

## 21. V2 ListItem

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ListItemActivity.kt) · [screenshot](21-v2-list-item.png) · [layout](21-v2-list-item.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme.aliasTokens
import com.microsoft.fluentui.theme.FluentTheme.themeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentGlobalTokens
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.Icon
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize.Size24
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize.Size40
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize.Size56
import com.microsoft.fluentui.theme.token.controlTokens.BasicCardInfo
import com.microsoft.fluentui.theme.token.controlTokens.BasicCardTokens
import com.microsoft.fluentui.theme.token.controlTokens.BorderInset.XXLarge
import com.microsoft.fluentui.theme.token.controlTokens.BorderType
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.ListItemTextAlignment
import com.microsoft.fluentui.theme.token.controlTokens.SectionHeaderStyle.Subtle
import com.microsoft.fluentui.theme.token.controlTokens.TextPlacement.Bottom
import com.microsoft.fluentui.tokenized.controls.BasicCard
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.CheckBox
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.notification.Badge
import com.microsoft.fluentui.tokenized.notification.ToolTipBox
import com.microsoft.fluentui.tokenized.notification.rememberTooltipState
import com.microsoft.fluentui.tokenized.persona.Avatar
import com.microsoft.fluentui.tokenized.persona.AvatarGroup
import com.microsoft.fluentui.tokenized.persona.Group
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.progress.LinearProgressIndicator
```

## 22. V2 Menu

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2MenuActivity.kt) · [screenshot](22-v2-menu.png) · [layout](22-v2-menu.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.menu.Menu
```

## 23. V2 People Picker

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2PeoplePickerActivity.kt) · [screenshot](23-v2-people-picker.png) · [layout](23-v2-people-picker.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.PersonaChipStyle
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.peoplepicker.PeoplePicker
import com.microsoft.fluentui.tokenized.peoplepicker.PeoplePickerItemData
import com.microsoft.fluentui.tokenized.peoplepicker.rememberPeoplePickerItemDataList
import com.microsoft.fluentui.tokenized.persona.AvatarGroup
import com.microsoft.fluentui.tokenized.persona.Group
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.persona.Persona
import com.microsoft.fluentui.tokenized.persona.PersonaList
```

## 24. V2 Persona

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2PersonaActivity.kt) · [screenshot](24-v2-persona.png) · [layout](24-v2-persona.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.persona.Persona
```

## 25. V2 PersonaChip

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2PersonaChipActivity.kt) · [screenshot](25-v2-persona-chip.png) · [layout](25-v2-persona-chip.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus.Available
import com.microsoft.fluentui.theme.token.controlTokens.PersonaChipSize
import com.microsoft.fluentui.theme.token.controlTokens.PersonaChipStyle.*
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.persona.PersonaChip
import com.microsoft.fluentui.tokenized.persona.SearchBarPersonaChip
```

## 26. V2 PersonaList

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2PersonaListActivity.kt) · [screenshot](26-v2-persona-list.png) · [layout](26-v2-persona-list.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.Icon
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus.Available
import com.microsoft.fluentui.theme.token.controlTokens.BorderInset
import com.microsoft.fluentui.theme.token.controlTokens.BorderType
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.persona.Persona
import com.microsoft.fluentui.tokenized.persona.PersonaList
```

## 27. V2 Progress

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ProgressActivity.kt) · [screenshot](27-v2-progress.png) · [layout](27-v2-progress.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.CircularProgressIndicatorSize
import com.microsoft.fluentui.theme.token.controlTokens.ColorStyle
import com.microsoft.fluentui.theme.token.controlTokens.ProgressTextInfo
import com.microsoft.fluentui.theme.token.controlTokens.ProgressTextTokens
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.progress.CircularProgressIndicator
import com.microsoft.fluentui.tokenized.progress.LinearProgressIndicator
import com.microsoft.fluentui.tokenized.progress.ProgressText
```

## 28. V2 Scaffold

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ScaffoldActivity.kt) · [screenshot](28-v2-scaffold.png) · [layout](28-v2-scaffold.layout.json)

```kotlin
import com.microsoft.fluentui.compose.Scaffold
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.controlTokens.*
import com.microsoft.fluentui.tokenized.AppBar
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton
import com.microsoft.fluentui.tokenized.drawer.Drawer
import com.microsoft.fluentui.tokenized.drawer.rememberDrawerState
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.navigation.TabBar
import com.microsoft.fluentui.tokenized.navigation.TabData
import com.microsoft.fluentui.tokenized.notification.Badge
import com.microsoft.fluentui.tokenized.notification.NotificationResult
import com.microsoft.fluentui.tokenized.notification.Snackbar
import com.microsoft.fluentui.tokenized.notification.SnackbarState
```

## 29. V2 SearchBar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2SearchBarActivity.kt) · [screenshot](29-v2-search-bar.png) · [layout](29-v2-search-bar.layout.json)

```kotlin
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.searchbaricons.Office
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.BorderType
import com.microsoft.fluentui.tokenized.SearchBar
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.persona.Persona
import com.microsoft.fluentui.tokenized.persona.PersonaList
```

## 30. V2 SegmentedControl

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2SegmentedControlActivity.kt) · [screenshot](30-v2-segmented-control-rendered.png) · [layout](30-v2-segmented-control-rendered.layout.json)

```kotlin
import com.microsoft.fluentui.icons.AvatarIcons
import com.microsoft.fluentui.icons.avataricons.Icon
import com.microsoft.fluentui.icons.avataricons.icon.Anonymous
import com.microsoft.fluentui.icons.avataricons.icon.anonymous.Xxlarge
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentColor
import com.microsoft.fluentui.theme.token.FluentGlobalTokens
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.segmentedcontrols.*
import com.microsoft.fluentui.tokenized.navigation.ViewPager
```

## 31. V2 Shimmer

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ShimmerActivity.kt) · [screenshot](31-v2-shimmer.png) · [layout](31-v2-shimmer.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.AvatarSize
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.BadgeInfo
import com.microsoft.fluentui.theme.token.controlTokens.BadgeTokens
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.ColorStyle
import com.microsoft.fluentui.theme.token.controlTokens.PersonaChipSize
import com.microsoft.fluentui.theme.token.controlTokens.PersonaChipStyle
import com.microsoft.fluentui.theme.token.controlTokens.ShimmerInfo
import com.microsoft.fluentui.theme.token.controlTokens.ShimmerOrientation
import com.microsoft.fluentui.theme.token.controlTokens.ShimmerTokens
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.notification.Badge
import com.microsoft.fluentui.tokenized.persona.Avatar
import com.microsoft.fluentui.tokenized.persona.Person
import com.microsoft.fluentui.tokenized.persona.PersonaChip
import com.microsoft.fluentui.tokenized.shimmer.Shimmer
```

## 32. V2 SideRail

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2SideRailActivity.kt) · [screenshot](32-v2-side-rail.png) · [layout](32-v2-side-rail.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.AvatarStatus
import com.microsoft.fluentui.theme.token.controlTokens.BadgeType
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.navigation.SideRail
import com.microsoft.fluentui.tokenized.navigation.TabData
import com.microsoft.fluentui.tokenized.notification.Badge
import com.microsoft.fluentui.tokenized.persona.Avatar
import com.microsoft.fluentui.tokenized.persona.Person
```

## 33. V2 Snackbar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2SnackbarActivity.kt) · [screenshot](33-v2-snackbar.png) · [layout](33-v2-snackbar.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme.aliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.SnackbarStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.notification.AnimationBehavior
import com.microsoft.fluentui.tokenized.notification.AnimationVariables
import com.microsoft.fluentui.tokenized.notification.NotificationDuration
import com.microsoft.fluentui.tokenized.notification.NotificationResult
import com.microsoft.fluentui.tokenized.notification.Snackbar
import com.microsoft.fluentui.tokenized.notification.SnackbarState
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillBar
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillMetaData
```

## 34. V2 StackableSnackbar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/V2StackableSnackbarActivity.kt) · [screenshot](34-v2-stackable-snackbar.png) · [layout](34-v2-stackable-snackbar.layout.json)

```kotlin
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.notification.Scrim
import com.microsoft.fluentui.tokenized.notification.SnackBarItemModel
import com.microsoft.fluentui.tokenized.notification.SnackBarStack
import com.microsoft.fluentui.tokenized.notification.SnackBarStackConfig
import com.microsoft.fluentui.tokenized.notification.rememberSnackBarStackState
```

## 35. V2 TabBar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2TabBarActivity.kt) · [screenshot](35-v2-tab-bar.png) · [layout](35-v2-tab-bar.layout.json)

```kotlin
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.controlTokens.BadgeType
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.TabTextAlignment
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.RadioButton
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.navigation.TabBar
import com.microsoft.fluentui.tokenized.navigation.TabData
import com.microsoft.fluentui.tokenized.notification.Badge
```

## 36. V2 TextField

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2TextFieldActivity.kt) · [screenshot](36-v2-text-field.png) · [layout](36-v2-text-field.layout.json)

```kotlin
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.searchbaricons.Dismisscircle
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.tokenized.controls.TextField
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import com.microsoft.fluentui.tokenized.listitem.ChevronOrientation
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillBar
import com.microsoft.fluentui.tokenized.segmentedcontrols.PillMetaData
```

## 37. V2 ToolTip

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/V2ToolTipActivity.kt) · [screenshot](37-v2-tool-tip.png) · [layout](37-v2-tool-tip.layout.json)

```kotlin
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.TextField
import com.microsoft.fluentui.tokenized.divider.Divider
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.notification.ToolTipBox
import com.microsoft.fluentui.tokenized.notification.TooltipState
import com.microsoft.fluentui.tokenized.notification.rememberTooltipState
import com.microsoft.fluentui.util.isAccessibilityEnabled
```

## 38. ActionBarLayout

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/ActionBarLayoutActivity.kt) · [screenshot](38-action-bar-layout-rendered.png) · [layout](38-action-bar-layout-rendered.layout.json)

```kotlin
import com.microsoft.fluentui.actionbar.ActionBarLayout
import com.microsoft.fluentui.snackbar.Snackbar
```

## 39. AppBarLayout

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/AppBarLayoutActivity.kt) · [screenshot](39-app-bar-layout.png) · [layout](39-app-bar-layout.layout.json)

```kotlin
import com.microsoft.fluentui.appbarlayout.AppBarLayout
import com.microsoft.fluentui.listitem.ListItemDivider
import com.microsoft.fluentui.listitem.ListSubHeaderView
import com.microsoft.fluentui.search.Searchbar
import com.microsoft.fluentui.snackbar.Snackbar
import com.microsoft.fluentui.theming.FluentUIContextThemeWrapper
import com.microsoft.fluentui.util.ThemeUtil
import com.microsoft.fluentui.util.getTintedDrawable
```

Resource references: `R.style.AppTheme`, `R.style.AppTheme_Neutral`, `R.style.AppTheme_Orange`, `R.style.Theme_FluentUI_Components`.

## 40. AvatarView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/AvatarViewActivity.kt) · [screenshot](40-avatar-view.png) · [layout](40-avatar-view.layout.json)

```kotlin
import com.microsoft.fluentui.persona.AvatarSize
import com.microsoft.fluentui.persona.AvatarStyle
import com.microsoft.fluentui.persona.AvatarView
```

## 41. AvatarGroupView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/AvatarGroupViewActivity.kt) · [screenshot](41-avatar-group-view.png) · [layout](41-avatar-group-view.layout.json)

```kotlin
import com.microsoft.fluentui.persona.AvatarBorderStyle
import com.microsoft.fluentui.persona.AvatarGroupStyle
import com.microsoft.fluentui.persona.AvatarGroupView
import com.microsoft.fluentui.persona.AvatarSize
import com.microsoft.fluentui.popupmenu.PopupMenu
import com.microsoft.fluentui.popupmenu.PopupMenuItem
import com.microsoft.fluentui.snackbar.Snackbar
```

## 42. Basic Inputs

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/BasicInputsActivity.kt) · [screenshot](42-basic-inputs.png) · [layout](42-basic-inputs.layout.json)

Fluent controls/styles are supplied through the activity layout/resources; inspect the linked demo source and its `R.layout`/`R.style` references.

## 43. BottomNavigation

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/BottomNavigationActivity.kt) · [screenshot](43-bottom-navigation.png) · [layout](43-bottom-navigation.layout.json)

Fluent controls/styles are supplied through the activity layout/resources; inspect the linked demo source and its `R.layout`/`R.style` references.

## 44. BottomSheet

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/BottomSheetActivity.kt) · [screenshot](44-bottom-sheet.png) · [layout](44-bottom-sheet.layout.json)

```kotlin
import com.microsoft.fluentui.bottomsheet.BottomSheet
import com.microsoft.fluentui.bottomsheet.BottomSheetDialog
import com.microsoft.fluentui.bottomsheet.BottomSheetItem
import com.microsoft.fluentui.snackbar.Snackbar
```

Resource references: `R.layout.accessory_content`.

## 45. CalendarView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/CalendarViewActivity.kt) · [screenshot](45-calendar-view.png) · [layout](45-calendar-view.layout.json)

```kotlin
import com.microsoft.fluentui.calendar.OnDateSelectedListener
import com.microsoft.fluentui.util.DateStringUtils
```

## 46. ContextualCommandBar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/ContextualCommandBarActivity.kt) · [screenshot](46-contextual-command-bar.png) · [layout](46-contextual-command-bar.layout.json)

```kotlin
import com.microsoft.fluentui.contextualcommandbar.CommandItem
import com.microsoft.fluentui.contextualcommandbar.CommandItemGroup
import com.microsoft.fluentui.contextualcommandbar.ContextualCommandBar
import com.microsoft.fluentui.contextualcommandbar.DefaultCommandItem
```

## 47. DateTimePicker

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/DateTimePickerActivity.kt) · [screenshot](47-date-time-picker.png) · [layout](47-date-time-picker.layout.json)

```kotlin
import com.microsoft.fluentui.datetimepicker.DateTimePicker
import com.microsoft.fluentui.datetimepicker.DateTimePickerDialog
import com.microsoft.fluentui.datetimepicker.DateTimePickerDialog.DateRangeMode
import com.microsoft.fluentui.datetimepicker.DateTimePickerDialog.Mode
import com.microsoft.fluentui.util.DateStringUtils
import com.microsoft.fluentui.util.accessibilityManager
import com.microsoft.fluentui.util.isAccessibilityEnabled
```

## 48. Drawer

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/DrawerActivity.kt) · [screenshot](48-drawer.png) · [layout](48-drawer.layout.json)

```kotlin
import com.microsoft.fluentui.drawer.Drawer
import com.microsoft.fluentui.drawer.DrawerDialog
import com.microsoft.fluentui.drawer.OnDrawerContentCreatedListener
import com.microsoft.fluentui.persona.PersonaListView
```

Resource references: `R.layout.demo_drawer_content`, `R.layout.demo_side_drawer_content`.

## 49. ListItemView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/ListItemViewActivity.kt) · [screenshot](49-list-item-view.png) · [layout](49-list-item-view.layout.json)

```kotlin
import com.microsoft.fluentui.listitem.ListItemDivider
import com.microsoft.fluentui.listitem.ListItemView
import com.microsoft.fluentui.listitem.ListItemView.Companion.DEFAULT_CUSTOM_VIEW_SIZE
import com.microsoft.fluentui.listitem.ListItemView.Companion.DEFAULT_LAYOUT_DENSITY
import com.microsoft.fluentui.listitem.ListSubHeaderView
import com.microsoft.fluentui.persona.AvatarSize
import com.microsoft.fluentui.persona.AvatarView
import com.microsoft.fluentui.snackbar.Snackbar
```

Resource references: `R.style.FluentUIDemo_ListItemSubHeaderTitle`, `R.style.TextAppearance_ListItemValue`, `R.style.TextAppearance_DemoListItemSubtitle`.

## 50. PeoplePickerView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/PeoplePickerViewActivity.kt) · [screenshot](50-people-picker-view.png) · [layout](50-people-picker-view.layout.json)

```kotlin
import com.microsoft.fluentui.peoplepicker.PeoplePickerAccessibilityTextProvider
import com.microsoft.fluentui.peoplepicker.PeoplePickerPersonaChipClickStyle
import com.microsoft.fluentui.peoplepicker.PeoplePickerView
import com.microsoft.fluentui.persona.IPersona
import com.microsoft.fluentui.snackbar.Snackbar
```

## 51. PersistentBottomSheet

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/PersistentBottomSheetActivity.kt) · [screenshot](51-persistent-bottom-sheet.png) · [layout](51-persistent-bottom-sheet.layout.json)

```kotlin
import com.microsoft.fluentui.bottomsheet.BottomSheetAdapter
import com.microsoft.fluentui.bottomsheet.BottomSheetItem
import com.microsoft.fluentui.persistentbottomsheet.PersistentBottomSheet
import com.microsoft.fluentui.persistentbottomsheet.SheetHorizontalItemAdapter
import com.microsoft.fluentui.persistentbottomsheet.SheetItem
import com.microsoft.fluentui.snackbar.Snackbar
```

Resource references: `R.layout.demo_persistent_sheet_content`, `R.layout.accessory_content`, `R.style.TextAppearance_FluentUI_HorizontalListItemTitle`.

## 52. PersonaChipView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/PersonaChipViewActivity.kt) · [screenshot](52-persona-chip-view.png) · [layout](52-persona-chip-view.layout.json)

```kotlin
import com.microsoft.fluentui.persona.PersonaChipView
import com.microsoft.fluentui.snackbar.Snackbar
```

## 53. PersonaListView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/PersonaListViewActivity.kt) · [screenshot](53-persona-list-view.png) · [layout](53-persona-list-view.layout.json)

```kotlin
import com.microsoft.fluentui.persona.IPersona
import com.microsoft.fluentui.persona.PersonaListView
import com.microsoft.fluentui.snackbar.Snackbar
```

## 54. PersonaView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/PersonaViewActivity.kt) · [screenshot](54-persona-view.png) · [layout](54-persona-view.layout.json)

```kotlin
import com.microsoft.fluentui.persona.AvatarSize
import com.microsoft.fluentui.persona.PersonaView
```

## 55. PopupMenu

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/PopupMenuActivity.kt) · [screenshot](55-popup-menu.png) · [layout](55-popup-menu.layout.json)

```kotlin
import com.microsoft.fluentui.popupmenu.PopupMenu
import com.microsoft.fluentui.popupmenu.PopupMenuItem
import com.microsoft.fluentui.snackbar.Snackbar
```

## 56. Progress

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/ProgressActivity.kt) · [screenshot](56-progress.png) · [layout](56-progress.layout.json)

Fluent controls/styles are supplied through the activity layout/resources; inspect the linked demo source and its `R.layout`/`R.style` references.

## 57. Snackbar

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/SnackbarActivity.kt) · [screenshot](57-snackbar.png) · [layout](57-snackbar.layout.json)

```kotlin
import com.microsoft.fluentui.persona.AvatarSize
import com.microsoft.fluentui.persona.AvatarView
import com.microsoft.fluentui.progress.ProgressBar
import com.microsoft.fluentui.snackbar.Snackbar
import com.microsoft.fluentui.util.createImageView
```

Resource references: `R.style.Widget_FluentUI_CircularProgress_Small`.

## 58. TabLayout

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/TabLayoutActivity.kt) · [screenshot](58-tab-layout-rendered.png) · [layout](58-tab-layout-rendered.layout.json)

```kotlin
import com.microsoft.fluentui.tablayout.TabLayout
import com.microsoft.fluentui.tablayout.TabLayout.TabType.*
```

## 59. TemplateView

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/TemplateViewActivity.kt) · [screenshot](59-template-view.png) · [layout](59-template-view.layout.json)

Fluent controls/styles are supplied through the activity layout/resources; inspect the linked demo source and its `R.layout`/`R.style` references.

Resource references: `R.layout.template_cell_vertical`.

## 60. Tooltip

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/TooltipActivity.kt) · [screenshot](60-tooltip.png) · [layout](60-tooltip.layout.json)

```kotlin
import com.microsoft.fluentui.calendar.CalendarView
import com.microsoft.fluentui.snackbar.Snackbar
import com.microsoft.fluentui.tooltip.Tooltip
import com.microsoft.fluentui.util.ThemeUtil
import com.microsoft.fluentui.widget.Button
```

Resource references: `R.layout.tooltip_custom_view`.

## 61. Typography

[Demo source](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/demos/TypographyActivity.kt) · [screenshot](61-typography.png) · [layout](61-typography.layout.json)

Fluent controls/styles are supplied through the activity layout/resources; inspect the linked demo source and its `R.layout`/`R.style` references.

Resource references: `R.style.TextAppearance_FluentUI_Body2`.
