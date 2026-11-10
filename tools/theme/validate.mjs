import fs from 'node:fs';
const source=fs.readFileSync('app/src/main/java/com/nadim/bikepart/ui/theme/Theme.kt','utf8');
const luminance = hex => {
 const channels=hex.match(/../g).map(c=>parseInt(c,16)/255).map(c=>c<=0.04045?c/12.92:((c+0.055)/1.055)**2.4);
 return .2126*channels[0]+.7152*channels[1]+.0722*channels[2];
};
let count=0,min=Infinity;
for(const mode of ['light','dark']) {
 const block=source.split(`internal val ${mode}ShopColors`)[1].split(')\n')[0];
 const roles=Object.fromEntries([...block.matchAll(/(\w+) = Color\(0xFF([A-F0-9]{6})\)/g)].map(m=>[m[1],m[2]]));
 const check=(a,b,threshold)=>{
  const values=[luminance(roles[a]),luminance(roles[b])].sort((x,y)=>y-x);
  const ratio=(values[0]+.05)/(values[1]+.05);
  if(ratio<threshold)throw Error(`${mode} ${a}/${b}: ${ratio}`);
  count++; if(threshold===4.5)min=Math.min(min,ratio);
 };
 for(const name of ['Primary','Secondary','Tertiary','Error']) {
  const lower=name[0].toLowerCase()+name.slice(1);
  check('on'+name,lower,4.5); check('on'+name+'Container',lower+'Container',4.5);
 }
 check('onBackground','background',4.5);
 check('onSurfaceVariant','surfaceVariant',4.5);
 check('inverseOnSurface','inverseSurface',4.5);
 for(const surface of Object.keys(roles).filter(k=>k==='surface'||k.startsWith('surfaceContainer')||k==='surfaceDim'||k==='surfaceBright')) {
  check('onSurface',surface,4.5); check('primary',surface,4.5); check('error',surface,4.5); check('outline',surface,3);
 }
}
console.log(`Passed ${count} rendered theme role contrast checks; minimum normal-text contrast ${min.toFixed(2)}:1.`);
