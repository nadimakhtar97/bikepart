/*
 * Adapted from Microsoft Fluent UI Android SearchBar.kt (MIT).
 * Copyright (c) Microsoft Corporation. See third_party/fluentui-android-MIT.txt.
 */
package com.nadim.bikepart.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.searchbaricons.Arrowback
import com.microsoft.fluentui.icons.searchbaricons.Dismisscircle
import com.microsoft.fluentui.icons.searchbaricons.Search
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.ControlTokens
import com.microsoft.fluentui.theme.token.controlTokens.SearchBarInfo
import com.microsoft.fluentui.theme.token.controlTokens.SearchBarTokens
import com.microsoft.fluentui.topappbars.R
import com.nadim.bikepart.R as ShopR
import com.nadim.bikepart.ui.theme.LocalShopFluentStyle
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopIconVector

/** Fluent's tokenized search layout with query state owned by the screen. */
@Composable
internal fun ShopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    onSubmit: (() -> Unit)? = null,
    scanLabel: String? = null,
    onScan: () -> Unit = {},
) {
    val tokens = FluentTheme.controlTokens.tokens[ControlTokens.ControlType.SearchBarControlType]
        as SearchBarTokens
    val info = SearchBarInfo(LocalShopFluentStyle.current)
    val shape = RoundedCornerShape(tokens.cornerRadius(info))
    val borderWidth = tokens.borderWidth(info)
    val elevation = tokens.elevation(info)
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val textStyle = tokens.typography(info).merge(
        TextStyle(color = tokens.textColor(info), textDirection = TextDirection.ContentOrLtr)
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
            .heightIn(min = tokens.height(info))
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, tokens.borderColor(info), shape)
                else Modifier
            )
            .then(
                if (elevation > 0.dp) Modifier.shadow(elevation, shape, spotColor = tokens.shadowColor(info))
                else Modifier
            )
            .clip(shape)
            .background(tokens.inputBackgroundBrush(info), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(focused, label = "Search navigation icon") { showBack ->
                IconButton(
                    enabled = enabled,
                    modifier = Modifier.size(ShopSpacing.touchTarget),
                    onClick = {
                        if (showBack) {
                            if (value.isNotEmpty()) onValueChange("")
                            focusManager.clearFocus()
                        } else {
                            focusRequester.requestFocus()
                        }
                    },
                ) {
                    val mirror = showBack && LocalLayoutDirection.current == LayoutDirection.Rtl
                    Icon(
                        imageVector = if (showBack) SearchBarIcons.Arrowback else SearchBarIcons.Search,
                        contentDescription = stringResource(
                            if (showBack) ShopR.string.search_exit else R.string.fluentui_search
                        ),
                        modifier = Modifier.size(tokens.leftIconSize(info))
                            .graphicsLayer { scaleX = if (mirror) -1f else 1f },
                        tint = tokens.leftIconColor(info),
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onSubmit?.invoke()
                }),
                modifier = Modifier.weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = label },
                textStyle = textStyle,
                cursorBrush = tokens.cursorColor(info),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            BasicText(
                                text = label,
                                modifier = Modifier.clearAndSetSemantics {},
                                style = textStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            AnimatedVisibility(value.isNotEmpty()) {
                IconButton(
                    enabled = enabled,
                    modifier = Modifier.size(ShopSpacing.touchTarget),
                    onClick = { onValueChange("") },
                ) {
                    Icon(
                        SearchBarIcons.Dismisscircle,
                        contentDescription = stringResource(R.string.fluentui_clear_text),
                        modifier = Modifier.size(tokens.rightIconSize(info)),
                        tint = tokens.rightIconColor(info),
                    )
                }
            }
            if (scanLabel != null) {
                IconButton(
                    enabled = enabled,
                    modifier = Modifier.size(ShopSpacing.touchTarget),
                    onClick = onScan,
                ) {
                    Icon(
                        shopIconVector(ShopR.drawable.ic_qr_code_scanner),
                        contentDescription = scanLabel,
                        modifier = Modifier.size(24.dp),
                        tint = tokens.rightIconColor(info),
                    )
                }
            }
        }
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall)
        }
    }
}
