package com.nadim.bikepart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.microsoft.fluentui.theme.token.controlTokens.TextFieldInfo
import com.microsoft.fluentui.theme.token.controlTokens.TextFieldTokens
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.TypographyTokens
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.controls.TextField

/** A compatibility probe; deliberately not connected to shop navigation. */
@Composable
fun FluentCompatibilityTrial() {
    var input by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    FluentTheme {
        Column(
            Modifier.fillMaxSize()
                .background(FluentTheme.aliasTokens.neutralBackgroundColor[
                    FluentAliasTokens.NeutralBackgroundColorTokens.CanvasBackground
                ].value())
                .safeDrawingPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Label(
                    text = "Fluent compatibility trial", textStyle = TypographyTokens.Title2,
                    modifier = Modifier.semantics { heading() }
                )
                TextField(
                    value = input, onValueChange = { input = it }, label = "Trial input",
                    textFieldContentDescription = "Trial input",
                    textFieldTokens = object : TextFieldTokens() {
                        @Composable
                        override fun dividerColor(textFieldInfo: TextFieldInfo) =
                            if (textFieldInfo.isFocused || textFieldInfo.isStatusError) {
                                super.dividerColor(textFieldInfo)
                            } else {
                                SolidColor(FluentTheme.aliasTokens.neutralStrokeColor[
                                    FluentAliasTokens.NeutralStrokeColorTokens.StrokeAccessible
                                ].value())
                            }
                    }
                )
                Label(text = "Applied: $applied", textStyle = TypographyTokens.Body1)
            }
            // Fluent 0.3.3 adds EditableText to buttons, causing Android to announce
            // an EditText. Replace only its accessibility contract, retaining native visuals.
            Button(
                onClick = { applied = input }, text = "Apply",
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .clearAndSetSemantics {
                        role = Role.Button
                        text = AnnotatedString("Apply")
                        onClick { applied = input; true }
                    }
            )
        }
    }
}
