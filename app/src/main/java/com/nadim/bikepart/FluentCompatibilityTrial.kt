package com.nadim.bikepart

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.Label
import com.microsoft.fluentui.tokenized.controls.TextField

/** A compatibility probe; deliberately not connected to shop navigation. */
@Composable
fun FluentCompatibilityTrial() {
    var input by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    FluentTheme {
        Column {
            Label(text = "Fluent compatibility trial")
            TextField(value = input, onValueChange = { input = it }, label = "Trial input", textFieldContentDescription = "Trial input")
            Button(onClick = { applied = input }, text = "Apply")
            Label(text = "Applied: $applied")
        }
    }
}
