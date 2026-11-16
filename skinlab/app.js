/* PTIV SkinLab 0.1: local opt-in player, three original visual concepts.
 * Preview files live in memory only; main PTIV IndexedDB and Wear OS remain untouched.
 * No bundled lyrics, music, Winamp skin art, or MilkDrop preset files.
 */
(() => {
'use strict';
const $=id=>document.getElementById(id), app=$('app'), audio=$('audio'), canvas=$('visual'), ctx=canvas.getContext('2d',{alpha:false});
const profiles=new Set(['iphone','flip','fold','watch']), skins=new Set(['cassette','orbit','lyrics']);
const params=new URLSearchParams(location.search);
let device=profiles.has(params.get('device'))?params.get('device'):'iphone';
let skin=skins.has(params.get('skin'))?params.get('skin'):(device==='fold'?'orbit':device==='iphone'?'lyrics':'cassette');
let files=[],current=-1,objectUrl=null,analyser=null,audioContext=null,source=null,frequency=null,timeData=null;
let selectedLyrics=new Map(),frame=0,started=performance.now(),isPlaying=false,lastRender=0;
let focus=false, reducedMotion=matchMedia('(prefers-reduced-motion: reduce)').matches;
const labels={iphone:'iPHONE · SAFARI VIEW',flip:'SAMSUNG FLIP · COMPACT VIEW',fold:'PIXEL FOLD · EXPANDED VIEW',watch:'ROUND WATCH · CONCEPT VIEW'};
function setStatus(text){$('status').textContent=text}
function setRoute(){
 const path=new URL(location.href);path.searchParams.set('device',device);path.searchParams.set('skin',skin);
 history.replaceState(null,'',path.pathname+path.search+path.hash);
}
function applyChoice(){
 app.className='skin-'+skin+' device-'+device+(focus?' skinlab-full':'');
 $('device').value=device;$('skin').value=skin;$('profileLabel').textContent=labels[device];
 $('skinMark').setAttribute('aria-label',skin+' visual design');
 setRoute();syncLyricView();
}
$('device').onchange=e=>{device=e.target.value;applyChoice()};
$('skin').onchange=e=>{skin=e.target.value;applyChoice()};
$('full').onclick=()=>{
 focus=!focus;app.classList.toggle('skinlab-full',focus);
 $('full').textContent=focus?'✕ Exit focus':'⛶ Focus';
 $('full').setAttribute('aria-pressed',String(focus));
 if(focus)$('full').focus();
};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&focus){e.preventDefault();$('full').click()}});
const format=secs=>{secs=Math.max(0,Math.floor(Number(secs)||0));return Math.floor(secs/60)+':'+String(secs%60).padStart(2,'0')};
function currentLyric(){
 if(current<0)return null;
 return selectedLyrics.get(files[current].key)||null;
}
function syncLyricView(){
 const pane=$('lyricScroll'),rec=currentLyric();
 pane.replaceChildren();
 $('lyricState').textContent=rec?'USER-SELECTED LYRICS · NOT VERIFIED':'NO REVIEWED LYRICS IMPORTED';
 if(!rec||!rec.lines.length){
   const p=document.createElement('p');p.textContent='Choose a reviewed .LRC file to see its words here.';
   pane.append(p);
 }else{
   for(const item of rec.lines){
    const p=document.createElement('p');p.textContent=item.text;
    pane.append(p);
   }
 }
 highlightLyric();
}
let lastLyric=-2;
function highlightLyric(){
 const rec=currentLyric(), pane=$('lyricScroll');
 if(!rec||!rec.lines.length)return;
 let line=-1;
 for(let i=0;i<rec.lines.length;i++){
  const t=rec.lines[i].time;
  if(t!==null && t<=audio.currentTime+.06)line=i;
  else if(t!==null)break;
 }
 if(line===lastLyric)return;
 if(lastLyric>=0&&pane.children[lastLyric])pane.children[lastLyric].classList.remove('active');
 if(line>=0&&pane.children[line]){
   pane.children[line].classList.add('active');
   const node=pane.children[line];
   const target=node.offsetTop-pane.offsetTop-pane.clientHeight/2+node.clientHeight/2;
   pane.scrollTo({top:Math.max(0,target),behavior:Math.abs(pane.scrollTop-target)>200||reducedMotion?'auto':'smooth'});
 }
 lastLyric=line;
}
function parseLyrics(sourceText){
 const result=[];let hasTimes=false;
 for(const raw of String(sourceText).split(/\r?\n/).slice(0,3000)){
   const line=raw.trim();if(!line)continue;
   const match=line.match(/^\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?\](.*)$/);
   if(match){
     const min=Number(match[1]),sec=Number(match[2]),frac=Number('0.'+(match[3]||'0')),s=min*60+sec+frac;
     const lyric=match[4].trim();
     if(lyric && sec<60 && Number.isFinite(s)){result.push({time:s,text:lyric});hasTimes=true}
   }else if(!line.startsWith('[')){result.push({time:null,text:line})}
 }
 return {lines:result,hasTimes};
}
$('pick').onclick=()=>$('files').click();
$('files').onchange=e=>{
 const incoming=Array.from(e.target.files||[]);e.target.value='';
 let accepted=0,duplicate=0,unsupported=0;
 for(const file of incoming){
  if(!/\.(mp3|m4a|aac|flac|wav|ogg|opus|aiff?)$/i.test(file.name)){unsupported++;continue}
  if(files.some(x=>x.name===file.name&&x.size===file.size&&x.lastModified===file.lastModified)){duplicate++;continue}
  const key=file.name+'|'+file.size+'|'+file.lastModified;
  files.push({file,name:file.name,size:file.size,lastModified:file.lastModified,key});accepted++;
 }
 renderTracks();
 setStatus('Selected '+accepted+' new local file(s). '+duplicate+' duplicates skipped; '+unsupported+' unsupported. Files remain on this device, in this preview session only.');
 if(current<0&&files.length)select(0,false);
};
function renderTracks(){
 const box=$('trackList');box.replaceChildren();
 $('trackCount').textContent=String(files.length);
 if(!files.length){const p=document.createElement('p');p.className='placeholder';p.textContent='No local music selected';box.append(p);return}
 files.forEach((entry,i)=>{
  const item=document.createElement('div');item.className='track'+(current===i?' active':'');
  const btn=document.createElement('button');btn.type='button';btn.textContent=(current===i?'●':'▶')+' '+entry.name;
  btn.onclick=()=>select(i,true);
  const meta=document.createElement('small');meta.textContent=(entry.size/1048576).toFixed(1)+' MB';
  item.append(btn,meta);box.append(item);
 });
}
function setTrackInfo(){
 if(current<0){$('song').textContent='Choose music from Files';$('subsong').textContent='P.T. ARCHITECH / OFFLINE PREVIEW'}
 else{$('song').textContent=files[current].name.replace(/\.[^.]+$/,'');$('subsong').textContent=(current+1)+' / '+files.length+' · SESSION-ONLY LOCAL AUDIO'}
}
async function select(index,autoplay){
 if(index<0||index>=files.length)return;
 if(objectUrl){URL.revokeObjectURL(objectUrl);objectUrl=null}
 current=index;objectUrl=URL.createObjectURL(files[current].file);
 audio.src=objectUrl;audio.load();lastLyric=-2;setTrackInfo();renderTracks();syncLyricView();
 $('play').textContent='▶';
 if(autoplay)try{await play()}catch(e){}
}
function connectAudio(){
 if(analyser||!audio.src)return;
 const AudioCtx=window.AudioContext||window.webkitAudioContext;
 if(!AudioCtx){setStatus('Audio visuals unavailable in this browser; basic playback can still work.');return}
 try{
  audioContext=new AudioCtx();
  source=audioContext.createMediaElementSource(audio);
  analyser=audioContext.createAnalyser();analyser.fftSize=1024;analyser.smoothingTimeConstant=.78;
  source.connect(analyser);analyser.connect(audioContext.destination);
  frequency=new Uint8Array(analyser.frequencyBinCount);timeData=new Uint8Array(analyser.fftSize);
 }catch(err){setStatus('Visual analysis unavailable: '+(err?.message||err));analyser=null}
}
async function play(){
 if(!audio.src){
   setStatus('Choose audio files first. The animated screen is a design preview until you play music.');
   $('files').click();return;
 }
 connectAudio();
 if(audioContext?.state==='suspended')await audioContext.resume();
 try{await audio.play();isPlaying=true;$('play').textContent='❚❚';setStatus('Playing a user-selected local file. Visuals reflect this audio when supported. No upload.')}
 catch(err){setStatus('Could not start audio. Tap Play again or choose a supported MP3/AAC file. '+(err?.name||''))}
}
$('play').onclick=()=>audio.paused?play():audio.pause();
$('prev').onclick=()=>select(current>0?current-1:files.length-1,true);
$('next').onclick=()=>select(files.length?(current+1)%files.length:-1,true);
audio.onplay=()=>{isPlaying=true;$('play').textContent='❚❚'};
audio.onpause=()=>{isPlaying=false;$('play').textContent='▶'};
audio.onended=()=>{if(files.length)select((current+1)%files.length,true)};
audio.onloadedmetadata=()=>{$('duration').textContent=format(audio.duration)};
audio.ontimeupdate=()=>{
 $('elapsed').textContent=format(audio.currentTime);
 if(Number.isFinite(audio.duration)&&audio.duration>0)$('seek').value=Math.round(1000*audio.currentTime/audio.duration);
 highlightLyric();
};
$('seek').oninput=e=>{
 if(Number.isFinite(audio.duration)&&audio.duration>0)audio.currentTime=audio.duration*Number(e.target.value)/1000;
};
$('pickLrc').onclick=()=>{
 if(current<0){setStatus('Choose a song first, then import its reviewed LRC or TXT file.');return}
 $('lrc').click();
};
$('lrc').onchange=async e=>{
 const file=e.target.files?.[0];e.target.value='';if(!file||current<0)return;
 if(file.size>400000){setStatus('Lyrics file too large: maximum 400 KB.');return}
 try{
  const rec=parseLyrics(await file.text());
  if(!rec.lines.length){setStatus('No usable text lines found in that lyrics file.');return}
  selectedLyrics.set(files[current].key,rec);
  lastLyric=-2;syncLyricView();
  setStatus('Imported '+rec.lines.length+' lyric lines locally. '+(rec.hasTimes?'Timestamps are user supplied and NOT independently verified.':'No timestamps; words shown without synchronization.')+' No publication.');
 }catch(err){setStatus('Lyrics could not be read. '+(err.message||err))}
};
function resize(){
 const dpr=Math.min(2,window.devicePixelRatio||1);
 const width=Math.max(200,canvas.clientWidth),height=Math.max(240,canvas.clientHeight);
 const w=Math.round(width*dpr),h=Math.round(height*dpr);
 if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}
 return {w,h};
}
function draw(t){
 if(!ctx)return;
 const {w,h}=resize();
 const bg=skin==='cassette'?'#081d25':skin==='orbit'?'#0b1231':'#0b2324';
 ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
 const live=!!analyser && !audio.paused && audioContext?.state==='running';
 if(live){analyser.getByteFrequencyData(frequency);analyser.getByteTimeDomainData(timeData)}
 $('audioMode').textContent=live?'LIVE AUDIO · LOCAL':'DEMO MOTION — NOT LIVE AUDIO';
 const accent=skin==='orbit'?'#7ce3ef':skin==='lyrics'?'#c6fda8':'#b9f47a';
 if(skin==='cassette'){
  const bars=44,width=w/bars;
  for(let i=0;i<bars;i++){
   const amp=live?Math.max(.03,frequency[Math.floor(i/(bars-1)*(frequency.length-1))]/255):.15+.22*Math.sin(i*.56+t*1.2)**2;
   const height=(.12+amp*.72)*h;
   ctx.fillStyle=i%5===0?'#fb975b':accent;
   ctx.globalAlpha=.19+(live?amp*.46:.25);
   ctx.fillRect(i*width+2,h-height,width-4,height);
  }
 }else if(skin==='orbit'){
  const cx=w/2,cy=h*.5,rad=Math.min(w,h)*.26;
  for(let i=0;i<5;i++){
   ctx.beginPath();ctx.lineWidth=1.7;ctx.strokeStyle=i%2?'#9b73f3':'#4bbbb9';ctx.globalAlpha=.15+i*.04;
   ctx.ellipse(cx,cy,rad+i*22,rad*.48+i*18,t*.07+i*.25,0,Math.PI*2);ctx.stroke();
  }
  for(let i=0;i<34;i++){
   const amp=live?frequency[Math.floor(i/(33)*(frequency.length-1))]/255:.22+.1*Math.sin(t*2+i*.8);
   const a=i*Math.PI*2/34+t*.08;
   const r=rad+amp*65;ctx.beginPath();ctx.strokeStyle=i%3?'#80d9e5':'#b997ff';ctx.lineWidth=4;
   ctx.globalAlpha=.15+amp*.38;
   ctx.moveTo(cx+Math.cos(a)*(rad-24),cy+Math.sin(a)*(rad-24));
   ctx.lineTo(cx+Math.cos(a)*r,cy+Math.sin(a)*r);ctx.stroke();
  }
 }else{
  ctx.globalAlpha=.38;ctx.strokeStyle=accent;ctx.lineWidth=2;ctx.beginPath();
  const N=80,mid=h*.50;
  for(let i=0;i<N;i++){
   const v=live?((timeData[Math.floor(i/N*(timeData.length-1))]-128)/128):Math.sin(t+i*.22)*.11;
   const x=i/(N-1)*w,y=mid+v*h*.26;
   if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);
  }
  ctx.stroke();
 }
 ctx.globalAlpha=1;
}
function tick(t){
 if(document.hidden){frame=0;return}
 if(t-lastRender>(reducedMotion?250:34)){draw((t-started)/1000);lastRender=t}
 frame=requestAnimationFrame(tick);
}
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&!frame){lastRender=0;frame=requestAnimationFrame(tick)}});
applyChoice();setTrackInfo();frame=requestAnimationFrame(tick);
})();
