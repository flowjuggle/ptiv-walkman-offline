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

function refreshFocusLyrics(force=false){
 if(overlay.classList.contains('hidden'))return;
 const rec=lyricsForCurrent();
 const items=Array.isArray(rec?.lines)?rec.lines.filter(l=>String(l.text||'').trim()):[];
 const verified=receiptValid(rec);
 const sig=[active,rec?.key,rec?.status,rec?.timing_status,items.length,items[0]?.text,items[items.length-1]?.text].join('|');
 const host=$focus('focusLyricText');
 if(force||sig!==focusLyricSignature) {
   focusLyricSignature=sig; focusLineIndex=-2;
   host.replaceChildren();
   if(!rec||!items.length) {
     const p=document.createElement('p');p.className='focus-message';
     p.textContent='No lyrics have been imported for this song. Use Lyrics sources to add the original or reviewed words.';
     host.append(p);
   } else {
     for(const item of items){const line=document.createElement('p');line.className='focus-line dim';line.textContent=item.text;host.append(line)}
   }
 }
 if(!lyricsVisible)return;
 $focus('focusLyricStatus').textContent=!rec?'NO LYRICS ON DEVICE':!verified?'ORIGINAL WORDS · NOT AUDIO-VERIFIED':rec.timing_status==='AI_DRAFT'?'WORDS VERIFIED · AI TIMING DRAFT':'VERIFIED WORDS · NO VERIFIED TIMING';
 const canFollow=verified&&rec.timing_status==='AI_DRAFT';
 let idx=-1;
 if(canFollow){for(let i=0;i<items.length;i++){const line=items[i];if(Number.isFinite(line.time)&&line.time<=audio.currentTime+.05)idx=i;else if(Number.isFinite(line.time))break}}
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
