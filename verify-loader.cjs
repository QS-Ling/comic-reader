// Run public reader code in an isolated VM without native, filesystem or network access.
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('build/chapter.html', 'utf8');
const encoded = html.match(/\bparams\s*=\s*'([A-Za-z0-9+/=]+)'/)[1];
const stub = { add() { return this; }, append() {}, eq() { return this; }, scroll() {}, length: 0 };
const ctx = vm.createContext({params: encoded, tpl_path:'/template/pc/14/', window:{innerHeight:900,location:{host:'www.qimanwu.app'}}, document:{documentElement:{clientHeight:900}}, console:{log(){}}, $:x=>typeof x==='function'?x():stub});
vm.runInContext(fs.readFileSync('app/src/main/assets/crypto.js','utf8'),ctx,{timeout:3000});
try { vm.runInContext(fs.readFileSync('app/src/main/assets/pic.js','utf8'),ctx,{timeout:3000}); } catch(e) { if(typeof ctx.params !== 'object') throw e; }
if(!Array.isArray(ctx.params.images) || !ctx.params.images.length) throw Error('No chapter images parsed');
const hosts=[...new Set(ctx.params.images.filter(x=>x.startsWith('http')).map(x=>new URL(x).host))];
console.log(JSON.stringify({imageCount:ctx.params.images.length,sourceId:ctx.params.source_id,hosts,firstImage:ctx.params.images[0]},null,2));
fs.writeFileSync('build/loader-result.json',JSON.stringify(ctx.params));
