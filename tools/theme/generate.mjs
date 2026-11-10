import {argbFromHex,hexFromArgb,TonalPalette} from './node_modules/@material/material-color-utilities/index.js';
import fs from 'node:fs';
const tokens=JSON.parse(fs.readFileSync('tools/theme/figma-tokens.json','utf8'));
let out=`package com.nadim.bikepart.ui.theme

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nadim.bikepart.shop.Appearance

// Generated from the reviewed Figma snapshot; see tools/theme/figma-tokens.json.
`;
for(const mode of ['light','dark']) {
 const roles={...tokens[mode]};
 // Fill newer Material roles absent from the older Figma file using its neutral/accent palettes.
 const neutral=TonalPalette.fromInt(argbFromHex('#'+roles.surface));
 const toneHex=tone=>hexFromArgb(neutral.tone(tone)).slice(1).toUpperCase();
 const tones=mode==='light'?{surfaceDim:87,surfaceBright:98,surfaceContainerLowest:100,surfaceContainerLow:96,surfaceContainerHigh:92,surfaceContainerHighest:90,outlineVariant:80}:{surfaceDim:6,surfaceBright:24,surfaceContainerLowest:4,surfaceContainerLow:10,surfaceContainerHigh:17,surfaceContainerHighest:22,outlineVariant:30};
 for(const [key,tone] of Object.entries(tones))roles[key]=toneHex(tone);
 roles.scrim='000000'; roles.surfaceTint=roles.primary;
 for(const group of ['primary','secondary','tertiary']) {
  const palette=TonalPalette.fromInt(argbFromHex('#'+tokens.light[group]));
  for(const [suffix,tone] of [['Fixed',90],['FixedDim',80],['onFixed',10],['onFixedVariant',30]]) {
   const key=suffix.startsWith('on')?suffix.replace('on',`on${group[0].toUpperCase()}${group.slice(1)}`):group+suffix;
   roles[key]=hexFromArgb(palette.tone(tone)).slice(1).toUpperCase();
  }
 }
 out+=`internal val ${mode}ShopColors = ${mode}ColorScheme(\n`;
 for(const [key,value] of Object.entries(roles))out+=`    ${key} = Color(0xFF${value}),\n`;
 out+=')\n\n';
}
out+=`@Composable
fun BikepartTheme(appearance: Appearance = Appearance.System, content: @Composable () -> Unit) {
    val dark = when (appearance) { Appearance.System -> isSystemInDarkTheme(); Appearance.Light -> false; Appearance.Dark -> true }
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) darkShopColors else lightShopColors, typography = Typography, shapes = ShopShapes, content = content)
}
`;
fs.writeFileSync('app/src/main/java/com/nadim/bikepart/ui/theme/Theme.kt',out);
