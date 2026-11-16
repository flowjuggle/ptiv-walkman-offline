/* Browser-only private Cactus candidate importer. No bundles in public code.
 * Selecting a file previews counts. Importing requires a second explicit click.
 */
(() => {
'use strict';
const api=window.PTIVCactusCandidates;
const host=document.getElementById('focusLyricsExtra');
if(!api||!host)return;
const wrap=document.createElement('section');
wrap.className='cactus-import-panel';
wrap.setAttribute('aria-label','Private PTIV Cactus lyric drafts');
const title=document.createElement('h3');
title.textContent='Recover archived PTIV lyric drafts';
const description=document.createElement('p');
description.textContent='Choose your private PTIV Cactus 131-track JSON archive. It stays on this device. Its SHA-256 and every track ID, title, release and ISRC are checked before import.';
const choose=document.createElement('button');
choose.type='button';choose.className='btn alt';choose.textContent='Choose private Cactus JSON';
choose.id='chooseCactusArchive';
const fileInput=document.createElement('input');
fileInput.type='file';fileInput.id='cactusArchiveFile';fileInput.accept='.json,application/json';fileInput.hidden=true;
const report=document.createElement('p');report.id='cactusImportReport';report.className='hint';report.setAttribute('role','status');report.setAttribute('aria-live','polite');
const stage=document.createElement('div');stage.id='cactusImportStage';stage.hidden=true;
const holdLabel=document.createElement('label');
holdLabel.className='cactus-holds';
const holdToggle=document.createElement('input');holdToggle.type='checkbox';holdToggle.id='cactusIncludeHolds';
const holdText=document.createElement('span');
holdText.textContent='Include 19 REVIEW HOLD candidates (possible wrong words/version). Leave off unless reviewing these tracks.';
holdLabel.append(holdToggle,holdText);
const start=document.createElement('button');start.className='btn';start.type='button';start.id='cactusImportStart';
const cancel=document.createElement('button');cancel.className='btn alt';cancel.type='button';cancel.id='cactusImportCancel';cancel.textContent='Cancel';
const buttons=document.createElement('div');buttons.className='cactus-action-row';buttons.append(start,cancel);
const caution=document.createElement('p');caution.className='hint';
caution.textContent='All imported text remains UNVERIFIED, even if automatically scrolled. Nothing is published or changed on Too Lost or Musixmatch. Existing lyric records are never overwritten.';
stage.append(holdLabel,buttons,caution);
wrap.append(title,description,choose,fileInput,report,stage);
host.append(wrap);
let plan=null,loadedFile=null;
function label(){
 if(!plan)return;
 const n=plan.counts.ready+(holdToggle.checked?plan.counts.hold:0);
 start.textContent='Import up to '+n+' unverified lyric candidates';
}
function reset(){
 plan=null;loadedFile=null;stage.hidden=true;holdToggle.checked=false;fileInput.value='';
}
choose.onclick=()=>fileInput.click();
cancel.onclick=()=>{reset();report.textContent='Import cancelled. No library records changed.'};
holdToggle.onchange=label;
function hex(bytes){return Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('')}
fileInput.onchange=async()=>{
 const f=fileInput.files?.[0];if(!f)return;
 reset();loadedFile=f;
 if(f.size>3500000||f.size<50000){
   report.textContent='File rejected. Expected private Cactus JSON is around 1.4 MB (limit 3.5 MB).';return;
 }
 choose.disabled=true;
 report.textContent='Checking source fingerprint and matching the 131 published catalog records locally…';
 try{
   if(!crypto.subtle?.digest)throw Error('SHA-256 unavailable. Use HTTPS or the installed Safari PWA.');
   const bytes=await f.arrayBuffer();
   const hash=hex(await crypto.subtle.digest('SHA-256',bytes));
   if(hash!==api.SOURCE_SHA256)throw Error('Source SHA-256 differs from the archived Cactus snapshot. Nothing imported.');
   const json=JSON.parse(new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/,''));
   plan=api.plan(json,catalogEntries);
   const c=plan.counts;
   if(c.total!==131||c.ready!==108||c.hold!==19||c.missing!==4||c.mismatched!==0){
     throw Error('Catalog or source counts are unexpected. No import performed.');
   }
   report.textContent='Source hash VERIFIED · 131 IDs matched · 108 available drafts · 19 REVIEW HOLD · 4 without words. All lyric words and cue times remain UNVERIFIED.';
   stage.hidden=false;
   label();
 }catch(e){
   reset();report.textContent='Import blocked: '+String(e?.message||e);
 }finally{choose.disabled=false}
};
start.onclick=async()=>{
 if(!plan)return;
 const includeHolds=holdToggle.checked;
 const wanted=plan.items.filter(e=>e.textStatus==='TEXT_READY_FOR_AUDIO_CHECK_UNVERIFIED'||includeHolds&&e.textStatus==='REVIEW_HOLD');
 if(!wanted.length)return;
 choose.disabled=true;start.disabled=true;cancel.disabled=true;
 let added=0,already=0,prior=0,persisted=0,sessionOnly=0;
 try{
  for(const entry of wanted){
    if(lyricRecords.has(entry.key)){already++;continue}
    const currentTitleKey=norm(entry.title);
    if(lyricRecords.has(currentTitleKey)){prior++;continue}
    const rec=api.makeRecord(entry);
    if(!rec)continue;
    const saved=await saveLyrics(rec);added++;
    if(saved)persisted++;else sessionOnly++;
  }
  report.textContent='Imported '+added+' new UNVERIFIED drafts ('+persisted+' stored offline, '+sessionOnly+' session-only). '+already+' existing Cactus records kept; '+prior+' existing user lyrics preserved. '+(includeHolds?'19 held candidates included by choice.':'19 review holds remain excluded.')+' Four missing-source tracks remain blank.';
  stage.hidden=true;plan=null;loadedFile=null;
  renderLyrics();
  if(!document.getElementById('focusOverlay')?.classList.contains('hidden')){
    audio.dispatchEvent(new Event('timeupdate'));
  }
 }catch(e){report.textContent='Import interrupted after '+added+' additions: '+String(e?.message||e)+'. Existing lyrics not overwritten.'}
 finally{choose.disabled=false;start.disabled=false;cancel.disabled=false}
};
})();
