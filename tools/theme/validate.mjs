import fs from 'node:fs';
import crypto from 'node:crypto';
const file = process.argv[2] ?? 'docs/fluent/evidence/dynamic-theme/fluent-contrast.json';
const exportData = JSON.parse(fs.readFileSync(file, 'utf8'));
const paths = JSON.parse(fs.readFileSync('tools/theme/source-files.json', 'utf8'));
const hash = crypto.createHash('sha256');
for (const path of paths) hash.update(path + '\n').update(fs.readFileSync(path)).update('\n');
if (hash.digest('hex') !== exportData.sourceSha256) throw Error('Stale runtime export: rebuild and rerun ThemeContrastExportTest.');
const luminance = hex => {
  const c = hex.match(/../g).map(x => parseInt(x, 16) / 255).map(x => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4);
  return .2126 * c[0] + .7152 * c[1] + .0722 * c[2];
};
let checked = 0;
const failures = [];
const brands = ['Fluent', 'OneNote', 'Word', 'Excel', 'PowerPoint', 'M365', 'Teams'];
if (exportData.themes.length !== 28) throw Error('Expected all 28 brand/style/appearance scopes');
for (const brand of brands) for (const style of ['Brand', 'Neutral']) for (const appearance of ['light', 'dark']) {
  const identity = `${brand}/${style}/${appearance}`;
  const themes = exportData.themes.filter(t => t.appearance === appearance && t.brand === brand && t.style === style);
  if (themes.length !== 1 || !themes[0].pairs.length) throw Error(`Missing or duplicated ${identity} theme`);
  for (const pair of themes[0].pairs) {
    if (!/^[0-9A-F]{6}$/i.test(pair.foreground) || !/^[0-9A-F]{6}$/i.test(pair.background)) throw Error(`Invalid colors: ${pair.name}`);
    if (pair.minimum === 0 && pair.name.endsWith('.disabled.exempt')) continue;
    if (![3, 4.5].includes(pair.minimum)) throw Error(`Invalid threshold: ${pair.name}`);
    const [hi, lo] = [luminance(pair.foreground), luminance(pair.background)].sort((a, b) => b - a);
    const ratio = (hi + .05) / (lo + .05);
    if (ratio < pair.minimum) failures.push(`${identity} ${pair.name}: ${ratio.toFixed(2)} < ${pair.minimum}`);
    checked++;
  }
}
if (failures.length) throw Error(failures.join('\n'));
console.log(`Passed ${checked} effective Fluent/bridge contrast pairs; source fingerprint current. Disabled controls explicitly exempt.`);
