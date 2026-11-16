/* PTIV Focus UI: visualizer + lyrics, intentional file scan, compact player.
 * Maintains all existing IndexedDB song and lyric stores; never sends files away.
 */
(() => {
'use strict';
const $focus = id => document.getElementById(id);
const main = document.querySelector('main');
const tape = $focus('tape');
const importPanel = $focus('files')?.closest('section.panel');
const player = $focus('now')?.closest('section.panel');
const lyricsPanel = $focus('lyricsPanel');
if (!main || !tape || !importPanel || !player || !lyricsPanel) return;

const actions = document.createElement('nav');
actions.className = 'focus-actions';
actions.setAttribute('aria-label','Quick music actions');
actions.innerHTML = '<button class="btn" type="button" id="focusScan">＋ Add Music</button><button class="btn alt" type="button" id="focusOpen">⛶ Visual + Lyrics</button>';
main.insertBefore(actions, tape);
main.insertBefore(player, tape);
player.classList.add('focus-player');

const mini = document.createElement('button');
mini.type = 'button';
mini.id = 'focusMini';
mini.className = 'focus-mini';
mini.setAttribute('aria-label','Open full-screen audio visualizer and lyrics');
mini.innerHTML = '<canvas id="focusMiniCanvas" width="480" height="120" aria-hidden="true"></canvas><span class="caption">PTIV · VISUAL + LYRICS</span><span class="meta">Tap to immerse · Audio-reactive when enabled</span>';
player.insertBefore(mini, player.querySelector('audio'));

function fold(content, summary, id) {
  const holder = document.createElement('details');
  holder.className = 'focus-extra';
  holder.id = id;
  const label = document.createElement('summary');
  label.textContent = summary;
  holder.append(label);
  content.before(holder);
  holder.append(content);
  return holder;
}
const cassetteExtra = fold(tape,'Cassette · Skins & classic controls','focusCassetteExtra');
const backTap = $focus('copyBackTap')?.parentElement;
if(backTap && backTap.parentElement === main) cassetteExtra.append(backTap);
fold(importPanel,'Import options · Music files, folders & ZIP','focusImportExtra');
fold(lyricsPanel,'Lyrics sources, font size & review tools','focusLyricsExtra');

const mode = $focus('continuous')?.closest('.mode-controls');
if (mode) {
 const advanced = document.createElement('details');
 advanced.className='focus-extra';
 const summary=document.createElement('summary');summary.textContent='Shuffle · Continuous · Bluetooth';
 advanced.append(summary);
 mode.before(advanced);
 advanced.append(mode);
 const note=$focus('modeStatus'); if(note)advanced.append(note);
 const speaker=player.querySelector('.speaker-guide');if(speaker)advanced.append(speaker);
}

const scanPanel = document.createElement('section');
scanPanel.id='focusScanReview';
scanPanel.className='focus-preview hidden';
scanPanel.setAttribute('aria-label','Review music files before importing');
scanPanel.innerHTML='<h3>Review music found</h3><p id="scanSummary"></p><div class="focus-preview-list" id="scanList"></div><div class="focus-preview-actions"><button class="btn" id="scanImport" type="button">Import selected</button><button class="btn alt" id="scanCancel" type="button">Cancel</button></div><p class="hint">Only files you explicitly choose are examined here. Scanning does not import or upload anything.</p>';
player.after(scanPanel);

const overlay = document.createElement('section');
overlay.id='focusOverlay';
overlay.className='focus-overlay hidden';
overlay.setAttribute('role','dialog');
overlay.setAttribute('aria-modal','true');
overlay.setAttribute('aria-label','Full-screen visualizer and lyrics');
overlay.innerHTML = `
<canvas id="focusCanvas" width="720" height="1100" aria-hidden="true"></canvas>
<header class="focus-overlay-header"><div><div class="focus-overlay-title">PTIV · IMMERSIVE PLAYER</div><div class="focus-overlay-sub" id="focusSong">Choose a track</div></div><button type="button" id="focusClose" class="focus-iconbutton" aria-label="Close full-screen player">✕</button></header>
<div class="focus-overlay-center"><div class="focus-lyric-status" id="focusLyricStatus">Lyrics status</div><div id="focusLyricText" class="focus-lyrics" aria-live="off"></div></div>
<footer class="focus-overlay-footer">
 <input id="focusSeek" type="range" min="0" max="1000" value="0" aria-label="Playback position">
 <div class="timing"><span id="focusElapsed">0:00</span><span id="focusDuration">0:00</span></div>
 <div class="focus-transport"><button class="focus-iconbutton" id="focusPrev" type="button" aria-label="Previous track">⏮</button><button class="focus-iconbutton focus-play" id="focusPlay" type="button" aria-label="Play or pause">▶</button><button class="focus-iconbutton" id="focusNext" type="button" aria-label="Next track">⏭</button></div>
 <div class="focus-footer-tools">
  <button class="focus-iconbutton" id="focusLyricsToggle" type="button" aria-pressed="true">Lyrics ✓</button>
  <button class="focus-iconbutton" id="focusFontMinus" type="button" aria-label="Smaller lyrics">A−</button>
  <button class="focus-iconbutton" id="focusFontPlus" type="button" aria-label="Larger lyrics">A＋</button>
  <select id="focusVizMode" aria-label="Visualizer style"><option value="spectrum">Spectrum</option><option value="oscilloscope">Waveform</option><option value="embers">Fire</option></select>
  <button class="focus-iconbutton" id="focusEnableViz" type="button">Enable visual</button>
 </div>
</footer>`;
document.body.append(overlay);
let scanRows = [];
let lyricsVisible = true;
let focusLyricSignature = '';
let focusLineIndex = -2;
let previousFocus = null;
let focusFrameActive = false;
let miniFrameActive = false;

function openFocus() {
 previousFocus=document.activeElement;
 overlay.classList.remove('hidden');
 document.body.classList.add('ptiv-focus-open');
 $focus('focusSong').textContent=$focus('now').textContent;
 try { $focus('focusVizMode').value=vizMode; } catch(e) {}
 refreshFocusLyrics(true);
 updateFocusPlayback();
 $focus('focusClose').focus();
 startFocusDrawing();
}
function closeFocus() {
 overlay.classList.add('hidden');
 document.body.classList.remove('ptiv-focus-open');
 if(previousFocus?.focus)previousFocus.focus();
}
$focus('focusOpen').onclick=openFocus;
mini.onclick=openFocus;
$focus('focusClose').onclick=closeFocus;
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!overlay.classList.contains('hidden'))closeFocus()});
$focus('focusPlay').onclick=()=>$focus('play').click();
$focus('focusPrev').onclick=()=>$focus('prev').click();
$focus('focusNext').onclick=()=>$focus('next').click();
$focus('focusLyricsToggle').onclick=()=>{
 lyricsVisible=!lyricsVisible;
 $focus('focusLyricText').classList.toggle('hidden',!lyricsVisible);
 $focus('focusLyricStatus').textContent=lyricsVisible?'Lyrics visible':'Visualizer only · lyrics hidden';
 $focus('focusLyricsToggle').setAttribute('aria-pressed',String(lyricsVisible));
 $focus('focusLyricsToggle').textContent=lyricsVisible?'Lyrics ✓':'Lyrics';
 if(lyricsVisible)refreshFocusLyrics(true);
};
$focus('focusFontMinus').onclick=()=>setLyricFont(Number($focus('lyricFont').value)-2);
$focus('focusFontPlus').onclick=()=>setLyricFont(Number($focus('lyricFont').value)+2);
$focus('focusVizMode').onchange=e=>{
 vizMode=e.target.value;
 try { localStorage.setItem(VIS_MODE_KEY,vizMode) } catch(err) {}
};
$focus('focusEnableViz').onclick=()=>{
 if(!audio.src){$focus('focusLyricStatus').textContent='Select a local song, then enable audio visuals.';return}
 enableVisualizer();
 startFocusDrawing();
};

function updateFocusPlayback() {
 const duration=Number.isFinite(audio.duration)?audio.duration:0;
 $focus('focusSong').textContent=$focus('now').textContent||'Choose music';
 $focus('focusPlay').textContent=audio.paused?'▶':'❚❚';
 $focus('focusElapsed').textContent=fmt(audio.currentTime);
 $focus('focusDuration').textContent=fmt(duration);
 if(duration)$focus('focusSeek').value=String(Math.round(1000*audio.currentTime/duration));
}
$focus('focusSeek').oninput=e=>{
 if(Number.isFinite(audio.duration)&&audio.duration>0)audio.currentTime=audio.duration*Number(e.target.value)/1000;
};
audio.addEventListener('play',()=>{updateFocusPlayback();startFocusDrawing();startMiniDrawing()});
audio.addEventListener('pause',updateFocusPlayback);
audio.addEventListener('timeupdate',()=>{
 if(overlay.classList.contains('hidden'))return;
 updateFocusPlayback();
 refreshFocusLyrics(false);
});
audio.addEventListener('loadedmetadata',updateFocusPlayback);
audio.addEventListener('ended',updateFocusPlayback);

const progressive=window.PTIVProgressiveLyrics;
const focusUndo=document.createElement('button');
focusUndo.type='button';focusUndo.id='focusUndoDraft';focusUndo.className='focus-iconbutton';
focusUndo.textContent='Undo mark';focusUndo.title='Undo last local timing correction';
const focusExport=document.createElement('button');
focusExport.type='button';focusExport.id='focusExportDraft';focusExport.className='focus-iconbutton';
focusExport.textContent='Export draft';focusExport.title='Save approximate timestamps as an explicitly unverified JSON file';
const focusToolRow=$focus('focusFontPlus')?.parentElement;
if(focusToolRow){focusToolRow.append(focusUndo,focusExport)}
const focusDraftAnnouncement=document.createElement('div');
focusDraftAnnouncement.className='focus-draft-hint';
focusDraftAnnouncement.textContent='Tap a lyric line to mark where it occurs in the song. Approximate only; original text stays unchanged.';
$focus('focusLyricStatus').after(focusDraftAnnouncement);
focusUndo.onclick=()=>{
 const rec=lyricsForCurrent();
 if(!rec)return;
 const changed=progressive?.undo(rec,active,audio.duration);
 if(!changed?.ok){$focus('focusLyricStatus').textContent=changed?.reason||'No correction available';return}
 refreshFocusLyrics(true);syncLyrics();
};
focusExport.onclick=()=>{
 const rec=lyricsForCurrent();
 if(!rec){$focus('focusLyricStatus').textContent='Import lyrics first';return}
 const data=progressive?.exportDraft(rec,active,audio.duration);
 if(!data)return;
 const text=JSON.stringify(data,null,2);
 const filename=fileSafeTitle(rec.name||'Track')+'_APPROXIMATE_NOT_VERIFIED.json';
 userExport(filename,text,'application/json');
};
// Explicitly selected YouTube/WebVTT, SRT, LRC or text captions are lyric CANDIDATES.
const captionInput=document.createElement('input');
captionInput.type='file';captionInput.id='focusCaptionFile';
captionInput.accept='.vtt,.srt,.lrc,.txt,text/plain';captionInput.hidden=true;
const captionButton=document.createElement('button');
captionButton.type='button';captionButton.id='focusCaptionButton';captionButton.className='btn alt';
captionButton.textContent='Import caption candidate (.vtt/.srt/.lrc/.txt)';
captionButton.title='Use a subtitle file you selected as an unverified lyric candidate for the currently playing track';
const lyricsImportSection=$focus('focusLyricsExtra');
if(lyricsImportSection){
 const line=document.createElement('div');
 line.className='focus-caption-import';
 line.append(captionButton,captionInput);
 lyricsImportSection.append(line);
}
captionButton.onclick=()=>{
 const song=allMeta().find(x=>x.id===active);
 if(!song){
  $focus('lyricsNote').textContent='Choose a local PTIV track before importing its caption file.';
  $focus('lyricsNote').className='lyrics-info warning';return;
 }
 captionInput.click();
};
function parseCandidateCaptions(filename,raw){
 const text=String(raw).replace(/\r\n?/g,'\n').replace(/^\uFEFF/,'');
 const out=[];
 function add(time,caption){
  const clean=caption.replace(/<[^>]*>/g,'').replace(/\{\\an\d+\}/g,'').trim();
  if(clean&&!/^(WEBVTT|NOTE(?:\s+.*)?|[0-9]+)$/i.test(clean)){
   out.push({time:Number.isFinite(time)?time:null,text:clean});
  }
 }
 const ext=filename.toLowerCase().split('.').pop();
 if(ext==='txt'){
  for(const line of text.split('\n').slice(0,4000))if(line.trim())add(null,line);
 }else if(ext==='lrc'){
  const parsed=lrcParse(text);
  for(const item of parsed.lines.slice(0,4000))add(item.time,item.text);
 }else{
  // Keep the full original caption text separately; the displayed text is only a candidate.
  const parseTime=s=>{
    const parts=String(s).replace(',','.').split(':').map(Number);
    if(parts.length===2&&parts.every(Number.isFinite))return parts[0]*60+parts[1];
    if(parts.length===3&&parts.every(Number.isFinite))return parts[0]*3600+parts[1]*60+parts[2];
    return null;
  };
  for(const block of text.split(/\n\s*\n/)){
   const lines=block.split('\n').map(x=>x.trim()).filter(Boolean);
   const idx=lines.findIndex(x=>x.includes('-->'));
   if(idx<0)continue;
   const a=lines[idx].split('-->')[0].trim();
   const at=parseTime(a);
   const displayed=lines.slice(idx+1).join(' ').trim();
   if(displayed)add(at,displayed);
   if(out.length>=4000)break;
  }
 }
 return out;
}
captionInput.onchange=async e=>{
 const file=e.target.files?.[0];e.target.value='';if(!file)return;
 const song=allMeta().find(x=>x.id===active),note=$focus('lyricsNote');
 if(!song){note.textContent='No track selected. Nothing imported.';return}
 const key=norm(song.name);
 const prior=lyricRecords.get(key)||lyricRecords.get(norm(song.path));
 if(prior){
  note.className='lyrics-info warning';
  note.textContent=receiptValid(prior)
   ?'Preserved the verified lyric source. Caption candidates will not replace it.'
   :'Existing lyric candidate preserved. Export or review it before attempting replacement.';
  return;
 }
 if(!/\.(vtt|srt|lrc|txt)$/i.test(file.name)||file.size>500000){
  note.textContent='Choose a VTT, SRT, LRC or TXT subtitle file under 500 KB.';note.className='lyrics-info warning';return;
 }
 try{
  const original=await file.text();
  const lines=parseCandidateCaptions(file.name,original);
  if(lines.length<1){note.textContent='No caption lines found. Nothing imported.';note.className='lyrics-info warning';return}
  const rec={
   key,name:song.name,source:file.name,source_kind:'CAPTION_CANDIDATE',kind:lines.some(l=>Number.isFinite(l.time))?'lrc':'text',
   status:'ORIGINAL_SOURCE_UNVERIFIED',timing_status:'CAPTION_DRAFT',
   source_origin:'USER_SELECTED_FILE',source_text_original:original,
   importedAt:new Date().toISOString(),
   lines
  };
  const saved=await saveLyrics(rec);
  renderLyrics();
  if(!overlay.classList.contains('hidden'))refreshFocusLyrics(true);
  note.className='lyrics-info warning';
  note.textContent='Loaded '+lines.length+' caption candidate lines for '+song.name+
   (saved?' (available offline on this device).':' (session-only; offline storage unavailable).')+
   ' NOT audio-verified. Timing is tentative, and no lyrics were published or changed on Too Lost.';
 }catch(err){note.className='lyrics-info warning';note.textContent='Caption import failed: '+(err?.message||err)}
};
function refreshFocusLyrics(force=false){
 if(overlay.classList.contains('hidden'))return;
 const rec=lyricsForCurrent(),verified=receiptValid(rec);
 const progress=rec?progressive?.follow(rec,audio.duration,audio.currentTime,active):null;
 const items=progress?.lines||[];
 const sig=[active,rec?.key,rec?.status,rec?.timing_status,items.length,progressive?.fingerprint(rec)].join('|');
 const host=$focus('focusLyricText');
 if(force||sig!==focusLyricSignature){
   focusLyricSignature=sig;focusLineIndex=-2;host.replaceChildren();
   if(!items.length){
     const p=document.createElement('p');p.className='focus-message';
     p.textContent='No lyrics available for this track yet. Import your original words, a reviewed source, or a caption candidate. The app never invents lyrics.';
     host.appendChild(p);
   }else{
     for(const item of items){
       const line=document.createElement('p');
       line.className='focus-line dim';
       line.textContent=item.text;
       line.tabIndex=0;line.setAttribute('role','button');
       line.title='Tap to align this line at the current playback position (local draft)';
       line.setAttribute('aria-label','Align this lyric to current playback position, approximate timing only');
       const tap=()=>{
          const result=progressive?.mark(rec,active,item.sourceIndex,audio.currentTime,audio.duration);
          if(result?.ok){
            refreshFocusLyrics(true);
            syncLyrics();
          }else{$focus('focusLyricStatus').textContent=result?.reason||'Unable to mark this line';}
       };
       line.addEventListener('click',tap);
       line.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();tap()}});
       host.appendChild(line);
     }
   }
 }
 if(!lyricsVisible)return;
 const kind=progress?.mode;
 const wordState=!rec?'NO WORDS':rec.review_status==='REVIEW_HOLD'?'REVIEW HOLD: WORDS MAY BE WRONG':rec.source_kind==='PTIV_CACTUS_ARCHIVE_CANDIDATE'?'CACTUS DRAFT WORDS UNVERIFIED':verified?'WORDS VERIFIED':'WORDS NOT AUDIO-VERIFIED';
 const timeState=kind==='reviewed'?'REVIEWED TIMING':kind==='source-draft'?'TIMING DRAFT':kind==='waiting'?'WAITING FOR AUDIO DURATION':'APPROXIMATE SCROLL · IN PROGRESS';
 const markCount=progress?.marks?.length||0;
 $focus('focusLyricStatus').textContent=wordState+' · '+timeState+(markCount?' · '+markCount+' manual mark(s)':'');
 focusUndo.disabled=!markCount;
 focusExport.disabled=!items.length;
 const idx=progress?.index??-1;
 if(idx===focusLineIndex)return;
 if(focusLineIndex>=0&&host.children[focusLineIndex])host.children[focusLineIndex].classList.remove('active');
 if(idx>=0&&host.children[idx]){
   const elem=host.children[idx];elem.classList.add('active');
   const target=elem.offsetTop-host.offsetTop-host.clientHeight/2+elem.clientHeight/2;
   host.scrollTo({top:Math.max(0,target),behavior:Math.abs(host.scrollTop-target)>260?'auto':'smooth'});
 }
 focusLineIndex=idx;
}
function startFocusDrawing(){
 if(focusFrameActive)return;
 focusFrameActive=true;let last=0;
 const tick=t=>{
  if(overlay.classList.contains('hidden')||document.hidden){focusFrameActive=false;return}
  if(t-last>=70){
    drawVisualizer($focus('focusCanvas')); last=t;
  }
  requestAnimationFrame(tick);
 };
 requestAnimationFrame(tick);
}
function startMiniDrawing(){
 if(miniFrameActive)return;
 miniFrameActive=true;let last=0;
 const tick=t=>{
   if(document.hidden){miniFrameActive=false;return}
   if(t-last>=90 && vizAnalyser){drawVisualizer($focus('focusMiniCanvas'));last=t}
   if(audio.paused){miniFrameActive=false;return}
   requestAnimationFrame(tick);
 };
 requestAnimationFrame(tick);
}
drawVisualizer($focus('focusMiniCanvas'));
if(!audio.paused)startMiniDrawing();

function pathFor(f){return f.webkitRelativePath||f.ptivZipPath||f.name}
function scanFiles(chosen,source='files'){
 const all=Array.from(chosen||[]);
 const existing=keys();
 const known=allMeta();
 const encountered=new Set();
 scanRows=[];
 let unsupported=0,duplicates=0,possible=0;
 for(const f of all.slice(0,3000)){
   if(!/\.(mp3|m4a|wav|flac|aac|aif|aiff|ogg|opus)$/i.test(f.name)){unsupported++;continue}
   const path=pathFor(f),id=path.toLowerCase()+'|'+f.size+'|'+f.lastModified;
   const duplicate=existing.has(id)||encountered.has(id)||known.some(x=>x.size===f.size&&x.name.toLowerCase()===safeFileName(f.name).toLowerCase());
   encountered.add(id);
   const likely=catalogEntries.find(x=>matches(x.title,safeFileName(f.name)));
   if(duplicate)duplicates++;
   if(likely)possible++;
   scanRows.push({file:f,duplicate,candidate:likely?.title||'',checked:!duplicate});
 }
 const omitted=Math.max(0,all.length-3000);
 const selected=scanRows.filter(x=>x.checked).length;
 $focus('scanSummary').textContent=all.length+' files selected from '+source+' · '+scanRows.length+' supported · '+duplicates+' possible duplicates · '+possible+' possible PTIV catalog matches · '+unsupported+' unsupported'+(omitted?' · '+omitted+' not scanned (3,000-file limit)':'')+'. No songs imported yet.';
 const list=$focus('scanList');list.replaceChildren();
 for(const [i,row] of scanRows.entries()){
   if(i>=250)break;
   const label=document.createElement('label');label.className='focus-preview-item';
   const cb=document.createElement('input');cb.type='checkbox';cb.checked=row.checked;cb.disabled=row.duplicate;
   cb.onchange=e=>{row.checked=e.target.checked;updateScanButton()};
   const text=document.createElement('div');
   const strong=document.createElement('strong');strong.textContent=row.file.name;
   const meta=document.createElement('span');meta.textContent=(row.file.size/1048576).toFixed(1)+' MB · '+(row.duplicate?'Possible duplicate — skipped':row.candidate?'Possible PTIV match: '+row.candidate:'Local music file');
   text.append(strong,meta);label.append(cb,text);list.append(label);
 }
 if(scanRows.length>250){
  const note=document.createElement('p');note.className='hint';
  note.textContent='Showing the first 250 results. All eligible files beyond that are included in the import selection; select a smaller folder for individual review.';
  list.append(note);
 }
 scanPanel.classList.remove('hidden');
 updateScanButton();
 scanPanel.scrollIntoView({behavior:'smooth',block:'start'});
}
function updateScanButton(){
 const n=scanRows.filter(x=>x.checked&&!x.duplicate).length;
 $focus('scanImport').textContent='Import '+n+' selected';
 $focus('scanImport').disabled=n===0;
}
$focus('focusScan').onclick=()=>$focus('files').click();
$focus('files').onchange=e=>{if(e.target.files?.length)scanFiles(e.target.files,'Files picker');e.target.value=''};
$focus('folder').onchange=e=>{if(e.target.files?.length)scanFiles(e.target.files,'selected folder');else msg('No folder selected. Try choosing music files instead.',true);e.target.value=''};
$focus('zip').onchange=async e=>{
 const f=e.target.files?.[0];e.target.value='';if(!f)return;
 try{msg('Reading ZIP locally; no upload…');const files=await unzipFiles(f);if(files.length)scanFiles(files,'ZIP '+f.name);else msg('No supported audio in that ZIP.',true)}
 catch(err){msg('ZIP scan failed: '+(err.message||err),true)}
};
$focus('scanCancel').onclick=()=>{scanRows=[];scanPanel.classList.add('hidden')};
$focus('scanImport').onclick=async()=>{
 const selected=scanRows.filter(x=>x.checked&&!x.duplicate).map(x=>x.file);
 if(!selected.length)return;
 $focus('scanImport').disabled=true;
 try{await importFiles(selected)}finally{
   scanPanel.classList.add('hidden');scanRows=[];
 }
};
})();
