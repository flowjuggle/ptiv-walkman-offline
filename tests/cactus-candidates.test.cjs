const assert=require('node:assert/strict');
const api=require('../cactus-candidates.js');
const ready='TEXT_READY_FOR_AUDIO_CHECK_UNVERIFIED';
const statuses=Array.from({length:131},(_,i)=>i<108?ready:i<127?'REVIEW_HOLD':'SOURCE_MISSING_HOLD');
const catalog=Array.from({length:131},(_,i)=>({
 id:'tl-'+(100000000+i),title:i===1||i===2?'Shared Version Name':'Test Track '+i,
 album:'Release '+Math.floor(i/10),isrc:'TEST'+String(i).padStart(8,'0'),releaseStatus:'published'
}));
const source={schema:api.SOURCE_SCHEMA,tracks:catalog.map((c,i)=>({
 id:c.id.slice(3),title:c.title,release:c.album,isrc:c.isrc,textStatus:statuses[i],
 lines:i<127?[{text:'Synthetic user-owned test line '+i,start:i+2,cueSource:'HISTORICAL_UNVERIFIED'}]:[]
}))};
let count=0;function test(name,fn){fn();count++;console.log('PASS '+name)}
const p=api.plan(source,catalog);
test('131 matching source identities',()=>assert.equal(p.counts.total,131));
test('108 default candidates',()=>assert.equal(p.counts.ready,108));
test('19 manual-review holds',()=>assert.equal(p.counts.hold,19));
test('4 missing words held blank',()=>assert.equal(p.counts.missing,4));
test('no synthetic identity mismatches',()=>assert.equal(p.counts.mismatched,0));
test('source lines are preserved',()=>assert.equal(p.items[0].lines[0].text,source.tracks[0].lines[0].text));
test('catalog ID remains unique',()=>assert.equal(api.makeRecord(p.items[0]).key,'ptiv-cactus-id:'+source.tracks[0].id));
test('lyrics explicitly unverified',()=>{const r=api.makeRecord(p.items[0]);assert.equal(r.status,'ORIGINAL_SOURCE_UNVERIFIED');assert.equal(r.verification.status,'UNVERIFIED')});
test('source times are draft only',()=>assert.equal(api.makeRecord(p.items[0]).timing_status,'HISTORICAL_UNVERIFIED'));
test('no text invented for missing track',()=>assert.equal(api.makeRecord(p.items[130]),null));
test('review hold is visibly preserved',()=>assert.equal(api.makeRecord(p.items[110]).review_status,'REVIEW_HOLD'));
const normalize=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');
const saved=new Map([[api.candidateKey(source.tracks[0].id),api.makeRecord(p.items[0])],
 [api.candidateKey(source.tracks[1].id),api.makeRecord(p.items[1])],
 [api.candidateKey(source.tracks[2].id),api.makeRecord(p.items[2])]]);
// Use stripped filename as the production normalizer does:
const normalizer=s=>normalize(String(s||'').replace(/\.(mp3|wav)$/i,''));
test('uniquely titled audio resolves candidate',()=>assert.equal(api.resolveSong({id:'recording-a',name:'Test Track 0.wav'},catalog,{},saved,normalizer)?.source_track_id,source.tracks[0].id));
test('duplicate title blocks automatic guess',()=>assert.equal(api.resolveSong({id:'recording-b',name:'Shared Version Name.wav'},catalog,{},saved,normalizer),null));
test('explicit catalog assignment disambiguates duplicate title',()=>assert.equal(api.resolveSong({id:'recording-b',name:'Shared Version Name.wav'},catalog,{[catalog[2].id]:'recording-b'},saved,normalizer)?.source_track_id,source.tracks[2].id));
test('existing lyric source wins',()=>assert.equal(api.resolveSong({id:'recording-a',name:'Test Track 0.wav'},catalog,{},new Map([[normalizer('Test Track 0.wav'),{status:'VERIFIED'}]]),normalizer).status,'VERIFIED'));
test('missing title in catalog becomes mismatch',()=>{const s=structuredClone(source);s.tracks[0].title='Wrong';assert.equal(api.plan(s,catalog).counts.mismatched,1)});
test('wrong ISRC becomes mismatch',()=>{const s=structuredClone(source);s.tracks[0].isrc='WRONG';assert.equal(api.plan(s,catalog).counts.mismatched,1)});
test('wrong album becomes mismatch',()=>{const s=structuredClone(source);s.tracks[0].release='Wrong';assert.equal(api.plan(s,catalog).counts.mismatched,1)});
test('unrecognized schema rejects archive',()=>assert.throws(()=>api.plan({schema:'different',tracks:source.tracks},catalog)));
test('catalog too small rejects',()=>assert.throws(()=>api.plan(source,catalog.slice(0,20))));
console.log('PASS_TOTAL='+count);
