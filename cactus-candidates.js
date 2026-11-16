/* PTIV Cactus archived lyric candidates — evidence matching only, NO SOURCE TEXT INCLUDED.
 * User-selected archive bytes are checked locally against the known 2026-10-31 snapshot SHA-256.
 * Never automatically validates lyric text or publishes to any service.
 */
(function(root){
 'use strict';
 const SOURCE_SHA256='ce303aec892f2d10193a6b9b08c2a298b617c3ccc1196c1bb87b9ba598d14fc8';
 const SOURCE_SCHEMA='ptiv-cactus-131-unverified-draft-v2';
 const READY='TEXT_READY_FOR_AUDIO_CHECK_UNVERIFIED',HOLD='REVIEW_HOLD';
 const clean=s=>String(s||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]/g,'');
 const tidy=s=>String(s||'').replace(/\u0000/g,'').trim();
 const candidateKey=id=>'ptiv-cactus-id:'+String(id);
 function plan(source,catalog){
   if(!source||source.schema!==SOURCE_SCHEMA||!Array.isArray(source.tracks)||source.tracks.length!==131)throw Error('This is not the expected PTIV Cactus 131-track archive.');
   if(!Array.isArray(catalog)||catalog.length<131)throw Error('PTIV catalog is not loaded; reconnect to load its authoritative metadata before importing.');
   const byId=new Map(catalog.filter(c=>c?.releaseStatus==='published'&&/^tl-\d+$/.test(String(c.id))).map(c=>[c.id,c]));
   if(byId.size!==131)throw Error('Expected 131 distinct published catalog IDs, found '+byId.size);
   const seen=new Set(),items=[],mismatches=[];
   for(const t of source.tracks){
     const id=String(t?.id??'');
     const catalogId='tl-'+id;
     const meta=byId.get(catalogId);
     const problems=[];
     if(!/^\d+$/.test(id)||seen.has(id))problems.push('invalid or duplicate track ID');
     seen.add(id);
     if(!meta)problems.push('catalog track ID absent');
     else{
       if(clean(meta.title)!==clean(t.title))problems.push('track title differs');
       if(clean(meta.album)!==clean(t.release))problems.push('release title differs');
       if(String(meta.isrc||'').toUpperCase()!==String(t.isrc||'').toUpperCase())problems.push('ISRC differs');
     }
     const status=String(t.textStatus||'');
     if(![READY,HOLD,'SOURCE_MISSING_HOLD'].includes(status))problems.push('unknown text status');
     const lines=Array.isArray(t.lines)?t.lines.filter(l=>tidy(l?.text)).map(l=>({
       text:String(l.text),time:typeof l.start==='number'&&Number.isFinite(l.start)&&l.start>=0&&l.start<36000?l.start:null,
       cueSource:String(l.cueSource||'HISTORICAL_UNVERIFIED')
     })):[];
     if(lines.length>2000||lines.some(l=>l.text.length>5000))problems.push('lyrics exceed safe bounds');
     if(status==='SOURCE_MISSING_HOLD'&&lines.length)problems.push('missing-source track unexpectedly has text');
     if(status!== 'SOURCE_MISSING_HOLD'&&!lines.length)problems.push('candidate text unexpectedly empty');
     if(problems.length){mismatches.push({id,title:tidy(t.title),problems});continue}
     items.push({
       id,catalogId,key:candidateKey(id),title:tidy(t.title),release:tidy(t.release),isrc:tidy(t.isrc),
       textStatus:status,sourceStatus:tidy(t.sourceStatus),sourceEvidence:tidy(t.sourceEvidence),
       flags:Array.isArray(t.flags)?t.flags.map(tidy).slice(0,30):[],
       lines,
       legacyCueCount:typeof t.historicalCueCount==='number'?t.historicalCueCount:0,
       verificationStatus:'UNVERIFIED',sourceSha256:SOURCE_SHA256
     });
   }
   const counts={
      total:items.length,
      ready:items.filter(i=>i.textStatus===READY).length,
      hold:items.filter(i=>i.textStatus===HOLD).length,
      missing:items.filter(i=>i.textStatus==='SOURCE_MISSING_HOLD').length,
      mismatched:mismatches.length
   };
   return {sourceSha256:SOURCE_SHA256,sourceSchema:SOURCE_SCHEMA,items,counts,mismatches};
 }
 function makeRecord(entry){
   if(!entry||entry.textStatus==='SOURCE_MISSING_HOLD'||!Array.isArray(entry.lines)||!entry.lines.length)return null;
   const times=entry.lines.map(x=>x.time).filter(x=>x!==null);
   const monotonic=times.every((x,i)=>i===0||x>=times[i-1]);
   const lines=entry.lines.map(x=>({
     text:x.text,
     time:monotonic&&Number.isFinite(x.time)?x.time:null,
     cue_source:x.cueSource
   }));
   return {
     key:entry.key,name:entry.title,album:entry.release,artist:'P.T. Architech',
     status:'ORIGINAL_SOURCE_UNVERIFIED',review_status:entry.textStatus,
     source_kind:'PTIV_CACTUS_ARCHIVE_CANDIDATE',source:'Cactus historical archive 2026-10-31',
     source_sha256:entry.sourceSha256,source_track_id:entry.id,catalog_track_id:entry.catalogId,
     source_evidence:entry.sourceEvidence,source_status:entry.sourceStatus,
     isrc:entry.isrc,source_flags:entry.flags,
     kind:times.length&&monotonic?'lrc':'text',
     timing_status:'HISTORICAL_UNVERIFIED',
     lines,verification:{status:'UNVERIFIED',words:false,timing:false},
     importedAt:new Date().toISOString()
   };
 }
 function resolveSong(song,catalog,assignments,lyricRecords,normalize){
   if(!song)return null;
   const existing=lyricRecords.get(normalize(song.name))||lyricRecords.get(normalize(song.path));
   if(existing)return existing;
   const matches=[...new Set(catalog.filter(c=>c.releaseStatus==='published'&&assignments[c.id]===song.id).map(c=>c.id))];
   if(matches.length===1)return lyricRecords.get(candidateKey(matches[0].slice(3)))||null;
   if(matches.length>1)return null;
   const normalized=normalize(song.name);
   const candidates=catalog.filter(c=>c.releaseStatus==='published'&&normalize(c.title)===normalized);
   if(candidates.length!==1)return null; // never choose among ambiguous same-name versions
   return lyricRecords.get(candidateKey(candidates[0].id.slice(3)))||null;
 }
 const api=Object.freeze({SOURCE_SHA256,SOURCE_SCHEMA,plan,makeRecord,resolveSong,candidateKey});
 root.PTIVCactusCandidates=api;
 if(typeof module==='object'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
