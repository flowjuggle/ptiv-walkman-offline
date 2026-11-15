package live.ptiv.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Seven Pearl taps -> Performer Mode (experimental).
 * Shares only user-selected, local PTIV MediaStore content URIs. No
 * cloud upload, no automatic transmission, no DRM access, no playlist mutation.
 * The receiving device must explicitly save its own offline copy.
 */
final class PerformerMode {
    private final Activity host;
    private final List<PlaybackService.Track> tracks;
    private final PlaybackService player;
    PerformerMode(Activity host,List<PlaybackService.Track> tracks,PlaybackService player){
        this.host=host; this.tracks=tracks; this.player=player;
    }

    void show(){
        if(tracks==null||tracks.isEmpty()){
            info("No verified local P.T. Architech recordings are indexed on this watch.");
            return;
        }
        String current=player==null||player.currentTrack()==null
            ? "(choose a track)" : player.currentTrack().title;
        new AlertDialog.Builder(host)
            .setTitle("PTIV • PERFORMER MODE")
            .setMessage("Current: "+current+"\n\nSend an actual local audio file to the person running sound. They must save it on their device before the show. No internet is needed after transfer.")
            .setItems(new String[]{
                "SEND CURRENT AUDIO FILE",
                "CHOOSE SONGS / PERFORMANCE SET",
                "SHARE PUBLIC LISTENING LINK (online)",
                "QR • NFC • Wi-Fi handoff info"
            },(dialog,which)->{
                if(which==0)shareCurrent();
                else if(which==1)chooseSet();
                else if(which==2)sharePublishedLink();
                else shareInfo();
            })
            .setNegativeButton("CLOSE",null)
            .show();
    }

    private void shareCurrent(){
        PlaybackService.Track t=player==null?null:player.currentTrack();
        if(t==null){
            chooseSet();return;
        }
        ArrayList<Uri> files=new ArrayList<>();
        files.add(t.uri);
        confirmSend(files,1);
    }

    private void chooseSet(){
        final int count=tracks.size();
        final String[] names=new String[count];
        final boolean[] selected=new boolean[count];
        int active=player==null?-1:player.getCurrentIndex();
        for(int i=0;i<count;i++){
            PlaybackService.Track t=tracks.get(i);
            names[i]=t.title+" • "+t.album;
            selected[i]=i==active;
        }
        new AlertDialog.Builder(host)
            .setTitle("SELECT OFFLINE SONGS")
            .setMultiChoiceItems(names,selected,(dialog,index,checked)->selected[index]=checked)
            .setPositiveButton("SEND FILES",(dialog,which)->{
                ArrayList<Uri> files=new ArrayList<>();
                for(int i=0;i<count;i++)if(selected[i])files.add(tracks.get(i).uri);
                if(files.isEmpty()){info("No songs selected. Nothing shared.");return;}
                confirmSend(files,files.size());
            })
            .setNeutralButton("CANCEL",null)
            .show();
    }

    private void confirmSend(ArrayList<Uri> uris,int count){
        new AlertDialog.Builder(host)
            .setTitle("CONFIRM OFFLINE TRANSFER")
            .setMessage(count+" P.T. Architech audio file"+(count==1?"":"s")+
                "\n\nNext: choose a compatible receiving/share app on this device. The recipient must save the original files for offline use. Do not rely on a web link during a performance.\n\nNo transfer starts until you confirm and choose a receiver.")
            .setPositiveButton("CHOOSE RECEIVER",(dialog,which)->shareFiles(uris))
            .setNegativeButton("CANCEL",null).show();
    }

    private void shareFiles(ArrayList<Uri> uris){
        if(uris.isEmpty())return;
        // These are already-scanned local audio URIs in MediaStore, not file://
        // references. Intent grants are temporary and read-only.
        Intent share=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);
        share.setType("audio/*");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip=ClipData.newRawUri("P.T. Architech offline audio",uris.get(0));
        for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));
        share.setClipData(clip);
        if(uris.size()==1)share.putExtra(Intent.EXTRA_STREAM,uris.get(0));
        else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        try{
            host.startActivity(Intent.createChooser(share,"Transfer P.T. Architech audio"));
        }catch(ActivityNotFoundException | SecurityException e){
            info("This watch has no compatible receiving app for audio files. Use the paired phone or a connected computer as the relay, or install a compatible file-sharing app. The music is still intact.");
        }
    }

    private static String norm(String text){
        if(text==null)return "";
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("\\((remastered|remaster)\\)","")
            .replaceAll("\\bremastered\\b","")
            .replaceAll("[^\\p{Alnum}]","");
    }

    private String publishedLink(PlaybackService.Track track){
        if(track==null)return null;
        try(InputStream in=host.getAssets().open("ptiv-catalog.json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] block=new byte[4096];int n;
            while((n=in.read(block))>=0){
                out.write(block,0,n);
                if(out.size()>2_000_000)return null;
            }
            JSONObject catalog=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            JSONArray rows=catalog.getJSONArray("tracks");
            String found=null;
            int matches=0;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                if(!"published".equalsIgnoreCase(row.optString("releaseStatus")))continue;
                if(!norm(track.title).equals(norm(row.optString("title"))))continue;
                if(!norm(track.album).equals(norm(row.optString("album"))))continue;
                String link=row.optString("track_preview_link","");
                Uri u=Uri.parse(link);
                if(!"https".equalsIgnoreCase(u.getScheme()))continue;
                if(!"toolost.com".equalsIgnoreCase(u.getHost())
                    &&!"too.fm".equalsIgnoreCase(u.getHost()))continue;
                matches++;found=link;
            }
            return matches==1?found:null;
        }catch(Exception ignored){return null;}
    }

    private void sharePublishedLink(){
        PlaybackService.Track current=player==null?null:player.currentTrack();
        String link=publishedLink(current);
        if(link==null){
            info("No verified public listening URL is available for this selected track in the local catalog. For an offline show, send the actual audio file instead. Unreleased drafts are not shared as public links.");
            return;
        }
        final Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT,link);
        try{host.startActivity(Intent.createChooser(i,"Share published track link"));}
        catch(ActivityNotFoundException e){info("No app is available to send this link.");}
    }

    private void shareInfo(){
        info("A QR code or NFC tag can hold a transfer link, but not the full song. A truly offline handoff needs a device-to-device file transfer over local Wi-Fi, Bluetooth, or a phone/computer relay. AirDrop is not native on Wear OS.\n\nOnly the tested Android system share handoff is enabled in this experimental build. QR/NFC pairing and nearby-transfer adapters are not active yet.");
    }

    private void info(String text){
        new AlertDialog.Builder(host).setTitle("PTIV • PERFORMER MODE")
            .setMessage(text).setPositiveButton("OK",null).show();
    }
}
