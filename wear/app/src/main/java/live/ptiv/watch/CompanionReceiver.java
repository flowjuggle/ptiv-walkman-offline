package live.ptiv.watch;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import com.google.android.gms.wearable.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit user-initiated companion transfers; never edits originals or Too Lost. */
public final class CompanionReceiver extends WearableListenerService {
    private static final ExecutorService queue=Executors.newSingleThreadExecutor();
    private static final int AUDIO_LIMIT=25_000_000;
    private static final int LRC_LIMIT=1_000_000;

    @Override public void onDataChanged(DataEventBuffer events) {
        for(DataEvent event:events){
            if(event.getType()!=DataEvent.TYPE_CHANGED)continue;
            DataItem item=event.getDataItem();
            String path=item.getUri().getPath();
            if(path==null||(!path.startsWith("/ptiv/file/")&&!path.startsWith("/ptiv/lrc/")))continue;
            boolean lyrics=path.startsWith("/ptiv/lrc/");
            DataMap map=DataMapItem.fromDataItem(item).getDataMap();
            String id=map.getString("id"),title=map.getString("title"),
                   album=map.getString("album"),sha=map.getString("sha256"),
                   extension=map.getString("extension");
            Asset asset=map.getAsset("payload");
            if(asset==null){receipt(id,title,sha,"blocked_no_payload");continue;}
            if(!allowed(id,title,album)){
                receipt(id,title,sha,"blocked_not_ptiv_catalog");continue;
            }
            String ext=extension==null?"":extension.toLowerCase(Locale.ROOT);
            if(lyrics && !ext.equals("lrc")){receipt(id,title,sha,"blocked_not_lrc");continue;}
            if(!lyrics && !(ext.equals("mp3")||ext.equals("m4a"))){
                receipt(id,title,sha,"blocked_unsupported_watch_audio");continue;
            }
            final String tid=id,tt=title,ta=album,th=sha,te=ext;
            Wearable.getDataClient(this).getFdForAsset(asset)
                .addOnSuccessListener(response->queue.execute(()->{
                    try(InputStream stream=response.getInputStream()){
                        if(stream==null)throw new IOException("No bytes from data layer");
                        importVerified(tid,tt,ta,th,te,lyrics,stream);
                    }catch(Exception e){
                        receipt(tid,tt,th,"failed_"+e.getClass().getSimpleName());
                    }
                }))
                .addOnFailureListener(e->receipt(tid,tt,th,"failed_asset_unavailable"));
        }
    }

    private boolean allowed(String id,String title,String album){
        if(id==null||title==null||album==null)return false;
        try(InputStream in=getAssets().open("ptiv-catalog.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            JSONArray tracks=new JSONObject(out.toString("UTF-8")).getJSONArray("tracks");
            for(int i=0;i<tracks.length();i++){
                JSONObject item=tracks.getJSONObject(i);
                if(id.equals(item.getString("id")) &&
                   title.equals(item.getString("title")) &&
                   album.equals(item.getString("album")))return true;
            }
        }catch(Exception ignored){}
        return false;
    }

    private void importVerified(String id,String title,String album,String sha,
                                String ext,boolean lyrics,InputStream stream) throws Exception {
        if(sha==null||!sha.matches("(?i)[a-f0-9]{64}"))throw new IOException("Invalid fingerprint");
        final int limit=lyrics?LRC_LIMIT:AUDIO_LIMIT;
        File temp=new File(getCacheDir(),"ptiv-"+id+"-"+System.nanoTime()+".partial");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        int bytes=0;
        try(FileOutputStream out=new FileOutputStream(temp)){
            byte[] block=new byte[8192];int n;
            while((n=stream.read(block))!=-1){
                bytes+=n;
                if(bytes>limit)throw new IOException("Transfer too large");
                out.write(block,0,n);digest.update(block,0,n);
            }
            out.getFD().sync();
            if(bytes<=0)throw new IOException("No file bytes");
            StringBuilder calculated=new StringBuilder();
            for(byte b:digest.digest())calculated.append(String.format(Locale.US,"%02x",b&0xff));
            if(!calculated.toString().equalsIgnoreCase(sha))throw new IOException("Hash mismatch");
            String result=lyrics?saveLrc(temp,title,sha):saveAudio(temp,id,title,album,ext,sha);
            receipt(id,title,sha,result);
        }catch(Exception e){
            receipt(id,title,sha,"failed_"+e.getClass().getSimpleName());
            throw e;
        }finally{if(temp.exists())temp.delete();}
    }

    private String sourceHash(InputStream in) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1)digest.update(b,0,n);
        StringBuilder result=new StringBuilder();
        for(byte v:digest.digest())result.append(String.format(Locale.US,"%02x",v&0xff));
        return result.toString();
    }

    private String saveLrc(File temp,String title,String sha) throws Exception {
        File dir=new File(getFilesDir(),"lyrics");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Lyrics dir unavailable");
        String filename=title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_")+".lrc";
        File target=new File(dir,filename);
        if(target.exists()){
            try(InputStream old=new FileInputStream(target)){
                if(sourceHash(old).equalsIgnoreCase(sha))return "already_present_lrc_sha_verified";
            }
            return "blocked_conflicting_lrc_preserved";
        }
        if(!temp.renameTo(target))throw new IOException("Could not commit lyrics");
        return "verified_lrc_copied_not_timing_approved";
    }

    private String saveAudio(File temp,String id,String title,String album,String extension,String sha) throws Exception {
        String filename="PTIV_"+id+"."+extension;
        Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String where=MediaStore.Audio.Media.DISPLAY_NAME+"=? AND "+MediaStore.Audio.Media.RELATIVE_PATH+"=?";
        try(Cursor old=getContentResolver().query(collection,
           new String[]{MediaStore.Audio.Media._ID},where,
           new String[]{filename,"Music/PTIV/"},null)){
            if(old!=null&&old.moveToFirst()){
                long oid=old.getLong(0);
                Uri existing=android.content.ContentUris.withAppendedId(collection,oid);
                try(InputStream input=getContentResolver().openInputStream(existing)){
                    if(input!=null&&sourceHash(input).equalsIgnoreCase(sha))return "already_present_audio_sha_verified";
                }
                return "blocked_conflicting_audio_preserved";
            }
        }
        ContentValues metadata=new ContentValues();
        metadata.put(MediaStore.Audio.Media.DISPLAY_NAME,filename);
        metadata.put(MediaStore.Audio.Media.MIME_TYPE,extension.equals("m4a")?"audio/mp4":"audio/mpeg");
        metadata.put(MediaStore.Audio.Media.TITLE,title);
        metadata.put(MediaStore.Audio.Media.ALBUM,album);
        metadata.put(MediaStore.Audio.Media.ARTIST,"P.T. Architech");
        metadata.put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/PTIV/");
        metadata.put(MediaStore.Audio.Media.IS_PENDING,1);
        Uri target=getContentResolver().insert(collection,metadata);
        if(target==null)throw new IOException("Watch media store unavailable");
        boolean committed=false;
        try(InputStream input=new FileInputStream(temp);
            OutputStream output=getContentResolver().openOutputStream(target)){
            if(output==null)throw new IOException("No destination stream");
            byte[] b=new byte[8192];int n;
            while((n=input.read(b))!=-1)output.write(b,0,n);
            output.flush();
            ContentValues ready=new ContentValues();
            ready.put(MediaStore.Audio.Media.IS_PENDING,0);
            getContentResolver().update(target,ready,null,null);
            committed=true;
        }finally{
            if(!committed)getContentResolver().delete(target,null,null);
        }
        return "verified_audio_copied";
    }

    private void receipt(String id,String title,String sha,String status) {
        if(id==null||id.length()>90)return;
        try {
            PutDataMapRequest request=PutDataMapRequest.create("/ptiv/receipt/"+id);
            DataMap data=request.getDataMap();
            data.putString("id",id);data.putString("title",title==null?"":title);
            data.putString("sha256",sha==null?"":sha);
            data.putString("status",status);
            data.putLong("timestamp",System.currentTimeMillis());
            PutDataRequest put=request.asPutDataRequest();
            put.setUrgent();
            Wearable.getDataClient(this).putDataItem(put);
        }catch(Exception ignored){}
    }
}
