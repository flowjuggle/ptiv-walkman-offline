/* PTIV Progressive Lyric Follow v1
 * Display-only estimated alignment for source lyrics while timestamps are reviewed.
 * Original lyric lines and verified records are NEVER altered by this module.
 * User timing corrections are stored separately, on this device, by text fingerprint.
 */
(function(root){
'use strict';
const PREFIX='ptiv-progressive-lyric-anchors-v1:';
const MAX_LINES=5000;
const MIN_DURATION=.5;
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const clamp=(x,a,b)=>Math.min(b,Math.max(a,x));
const nonblank=x=>String(x??'').trim().length>0;
const cleanLines=record=>{
 const source=Array.isArray(record?.lines)?record.lines:[];
 const result=[];
 for(let i=0;i<Math.min(MAX_LINES,source.length);i++){
   const line=source[i];
   if(!nonblank(line?.text))continue;
   result.push({sourceIndex:i,text:String(line.text),sourceTime:finite(line.time)&&line.time>=0?line.time:null});
 }
 return result;
};
function fingerprint(record){
 // FNV-1a, used solely to prevent applying anchors to changed words, not for source integrity.
 let h=2166136261;
 const text=(String(record?.key||record?.name||'')+'|'+cleanLines(record).map(x=>x.text).join('\n'));
 for(let i=0;i<text.length;i++){h^=text.charCodeAt(i);h=Math.imul(h,16777619)}
 return (h>>>0).toString(16).padStart(8,'0');
}
function storeKey(record,trackKey=''){
 // Intentionally no external URL, metadata, text, or model name in persisted value.
 return PREFIX+encodeURIComponent(String(trackKey||record?.key||record?.name||'').slice(0,180))+':'+fingerprint(record);
}
function readAnchors(record,trackKey='',duration=NaN){
 const key=storeKey(record,trackKey);
 try{
  const data=JSON.parse(root.localStorage?.getItem(key)||'null');
  if(!data||!Array.isArray(data.marks))return [];
  if(finite(duration)&&duration>0&&finite(data.duration)&&Math.abs(duration-data.duration)>Math.max(8,duration*.12))return [];
  return data.marks.filter(x=>Number.isInteger(x.index)&&x.index>=0&&finite(x.time)&&x.time>=0&&( !finite(duration)||x.time<=duration)).slice(0,100).sort((a,b)=>a.index-b.index);
 }catch(e){return []}
}
function saveAnchors(record,trackKey,duration,marks){
 const key=storeKey(record,trackKey);
 try{
  if(!root.localStorage)throw Error('Local storage unavailable');
  root.localStorage.setItem(key,JSON.stringify({
   format:'PTIV_APPROX_SYNC_ANCHORS_V1',duration,createdFrom:fingerprint(record),
   updatedAt:new Date().toISOString(),verification:'UNVERIFIED_USER_TIMING_DRAFT',
   marks:marks.slice(0,100)
  }));
  return {ok:true,marks:marks.length};
 }catch(e){return {ok:false,reason:'Timing mark could not be saved on this device.'}}
}
function weightedLocations(lines){
 const n=lines.length,positions=Array(n).fill(0);
 let x=0;
 for(let i=1;i<n;i++){
  const words=(lines[i-1]?.text.trim().split(/\s+/)||[]).length;
  x+=clamp(Math.sqrt(words||1),1,4.6);
  positions[i]=x;
 }
 if(n>1&&x>0)for(let i=1;i<n;i++)positions[i]/=x;
 return positions;
}
function interpolate(knots,positions,length){
 const sorted=[...knots].sort((a,b)=>a.index-b.index),times=Array(length).fill(0);
 for(let k=0;k<sorted.length-1;k++){
  const a=sorted[k],b=sorted[k+1],span=positions[b.index]-positions[a.index];
  for(let i=a.index;i<=b.index;i++){
   const ratio=span>1e-9?(positions[i]-positions[a.index])/span:(i-a.index)/Math.max(1,b.index-a.index);
   times[i]=a.time+(b.time-a.time)*clamp(ratio,0,1);
  }
 }
 return times;
}
function timeline(record,duration=NaN,trackKey=''){
 const lines=cleanLines(record),n=lines.length;
 if(!n||!finite(duration)||duration<MIN_DURATION)return {lines,times:[],status:n?'WAITING_FOR_DURATION':'NO_LYRICS',marks:[],mode:'waiting',index:-1};
 const marks=readAnchors(record,trackKey,duration).filter(m=>m.index<n);
 const positions=weightedLocations(lines);
 const start=Math.min(duration*.035,2.5);
 const end=Math.max(start,duration*.955);
 let base=n===1?[start]:positions.map(x=>start+(end-start)*x);
 const stamped=lines.map((r,index)=>r.sourceTime!==null&&r.sourceTime<=duration+.5?{index,time:clamp(r.sourceTime,0,duration)}:null).filter(Boolean);
 let sourceHasUsableTimes=false;
 if(stamped.length){
  let monotonic=true;for(let i=1;i<stamped.length;i++)if(stamped[i].time<stamped[i-1].time)monotonic=false;
  if(monotonic){
   sourceHasUsableTimes=true;
   const knots=[...stamped];
   if(knots[0].index!==0)knots.unshift({index:0,time:Math.min(start,knots[0].time)});
   if(knots[knots.length-1].index!==n-1)knots.push({index:n-1,time:Math.max(end,knots[knots.length-1].time)});
   if(n>1)base=interpolate(knots,positions,n);
   else base=[stamped[0].time];
  }
 }
 let times=[...base];
 if(marks.length&&n>1){
  const knots=[...marks];
  if(knots[0].index!==0)knots.unshift({index:0,time:Math.min(base[0],knots[0].time)});
  if(knots[knots.length-1].index!==n-1)knots.push({index:n-1,time:Math.max(base[n-1],knots[knots.length-1].time)});
  for(let i=1;i<knots.length;i++)if(knots[i].time<knots[i-1].time){return {lines,times:base,status:'TIMING_DRAFT_CONFLICT',marks,mode:'approximate',sourceHasUsableTimes,index:-1}}
  times=interpolate(knots,positions,n);
 }
 for(let i=1;i<times.length;i++)times[i]=Math.max(times[i],times[i-1]);
 const timingStatus=String(record?.timing_status||'').toUpperCase();
 const verifiedTime=timingStatus==='VERIFIED'&&record?.verification?.timing_status==='VERIFIED';
 const mode=verifiedTime&&sourceHasUsableTimes&&stamped.length===n&&!marks.length?'reviewed':sourceHasUsableTimes?'source-draft':'estimated';
 const status=mode==='reviewed'?'REVIEWED_LINE_TIMING':mode==='source-draft'?'SOURCE_TIMES_NEED_REVIEW':marks.length?'APPROXIMATE_WITH_MANUAL_MARKS':'ESTIMATED_SCROLL_IN_PROGRESS';
 return {lines,times,status,mode,sourceHasUsableTimes,sourceTimeCount:stamped.length,marks,duration};
}
function follow(record,duration,currentTime,trackKey=''){
 const data=timeline(record,duration,trackKey);
 const now=finite(currentTime)?Math.max(0,currentTime):0;
 let index=-1;
 for(let i=0;i<data.times.length;i++){if(data.times[i]<=now+.06)index=i;else break}
 return {...data,index,sourceIndex:index<0?-1:data.lines[index].sourceIndex};
}
function mark(record,trackKey,sourceIndex,currentTime,duration){
 if(!finite(duration)||duration<MIN_DURATION)return {ok:false,reason:'Play audio first so its duration is known.'};
 if(!finite(currentTime)||currentTime<0||currentTime>duration)return {ok:false,reason:'Invalid playback position.'};
 const sourceLines=cleanLines(record);
 const index=sourceLines.findIndex(x=>x.sourceIndex===sourceIndex);
 if(index<0)return {ok:false,reason:'Lyric line not found.'};
 const marks=readAnchors(record,trackKey,duration).filter(x=>x.index!==index);
 const item={index,time:Math.round(currentTime*100)/100};
 marks.push(item);marks.sort((a,b)=>a.index-b.index);
 const k=marks.findIndex(x=>x.index===index);
 if((k>0&&marks[k-1].time>item.time)||(k<marks.length-1&&marks[k+1].time<item.time)){
  return {ok:false,reason:'Timing conflicts with an earlier mark. Try another line or undo the last mark.'};
 }
 const result=saveAnchors(record,trackKey,duration,marks);
 if(result.ok)return {...result,line:index+1,time:item.time,sourceIndex};
 return result;
}
function undo(record,trackKey,duration){
 const marks=readAnchors(record,trackKey,duration);
 if(!marks.length)return {ok:false,reason:'No local timing marks to undo.'};
 marks.pop();
 return saveAnchors(record,trackKey,duration,marks);
}
function exportDraft(record,trackKey,duration){
 const data=timeline(record,duration,trackKey);
 return {
  format:'PTIV_PROGRESSIVE_SYNC_DRAFT_V1',track:String(record?.name||record?.key||''),
  sourceFingerprint:fingerprint(record),status:data.status,
  certification:'NONE',publishReady:false,sourceWordsModified:false,
  reviewedMasterMatch:false,generatedAt:new Date().toISOString(),
  duration:finite(duration)?duration:null,
  sourceTimingCount:data.sourceTimeCount||0,manualAnchorCount:data.marks.length,
  lines:data.lines.map((row,i)=>({sourceIndex:row.sourceIndex,text:row.text,estimatedTime:Number.isFinite(data.times[i])?Number(data.times[i].toFixed(2)):null,sourceTime:row.sourceTime}))
 };
}
const API=Object.freeze({timeline,follow,mark,undo,exportDraft,fingerprint,storeKey,readAnchors});
root.PTIVProgressiveLyrics=API;
if(typeof module==='object'&&module.exports)module.exports=API;
})(typeof window!=='undefined'?window:globalThis);
