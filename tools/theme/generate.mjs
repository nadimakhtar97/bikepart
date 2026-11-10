import {themeFromSourceColor,argbFromHex,hexFromArgb} from './node_modules/@material/material-color-utilities/index.js';
import fs from 'node:fs';
const theme=themeFromSourceColor(argbFromHex('#006A6A'));
let out=`package com.nadim.bikepart.ui.theme\n\nimport androidx.compose.foundation.isSystemInDarkTheme\nimport androidx.compose.material3.*\nimport androidx.compose.runtime.Composable\nimport androidx.compose.ui.graphics.Color\nimport com.nadim.bikepart.shop.Appearance\n\n// Generated from #006A6A using @material/material-color-utilities 0.3.0.\n`;
for(const mode of ['light','dark']){
 const roles=theme.schemes[mode].toJSON();
 delete roles.shadow;
 out+=`internal val ${mode}ShopColors = ${mode}ColorScheme(\n`;
 for(const [key,value] of Object.entries(roles)) out+=`    ${key} = Color(0xFF${hexFromArgb(value).slice(1).toUpperCase()}),\n`;
 const n=theme.palettes.neutral;
 const tones=mode==='light'?{surfaceDim:87,surfaceBright:98,surfaceContainerLowest:100,surfaceContainerLow:96,surfaceContainer:94,surfaceContainerHigh:92,surfaceContainerHighest:90}:{surfaceDim:6,surfaceBright:24,surfaceContainerLowest:4,surfaceContainerLow:10,surfaceContainer:12,surfaceContainerHigh:17,surfaceContainerHighest:22};
 for(const [key,tone] of Object.entries(tones))out+=`    ${key} = Color(0xFF${hexFromArgb(n.tone(tone)).slice(1).toUpperCase()}),\n`;
 for(const group of ['primary','secondary','tertiary']) {
   const palette=theme.palettes[group];
   for(const [suffix,tone] of [['Fixed',90],['FixedDim',80],['onFixed',10],['onFixedVariant',30]]) {
     const key=suffix.startsWith('on')?suffix.replace('on',`on${group[0].toUpperCase()}${group.slice(1)}`):group+suffix;
     out+=`    ${key} = Color(0xFF${hexFromArgb(palette.tone(tone)).slice(1).toUpperCase()}),\n`;
   }
 }
 out+=')\n\n';
}
out+=`@Composable\nfun BikepartTheme(appearance: Appearance = Appearance.System, content: @Composable () -> Unit) {\n    val dark = when (appearance) { Appearance.System -> isSystemInDarkTheme(); Appearance.Light -> false; Appearance.Dark -> true }\n    MaterialTheme(colorScheme = if (dark) darkShopColors else lightShopColors, typography = Typography, content = content)\n}\n`;
fs.writeFileSync('app/src/main/java/com/nadim/bikepart/ui/theme/Theme.kt',out);
