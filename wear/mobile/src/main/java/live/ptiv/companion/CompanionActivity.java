package live.ptiv.companion;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.*;
import com.google.android.gms.wearable.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Executors;

public final class CompanionActivity extends Activity implements DataClient.OnDataChangedListener {
    private static final int PICK_FILE = 4200;
    private static final int LIME = Color.rgb(195,247,113);
    private static final int INK = Color.rgb(234,246,237);
    private static final int MUTED = Color.rgb(179,201,191);
    private final ArrayList<CatalogRow> catalog = new ArrayList<>();
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TextView status,watchStatus,trackCount;
    private EditText search;
    private CatalogRow selected;
    private boolean sendLyrics=false;
    private int listLimit=50;
    private String lastAcknowledgment="No watch transfer receipt yet";
    private static final class CatalogRow {
        final String id,title,album,release;
        CatalogRow(JSONObject j) throws JSONException {
            id=j.getString("id");title=j.getString("title");
            album=j.getString("album");release=j.optString("releaseStatus","unknown");
        }
    }
    private TextView text(String s,int size,int color) {
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER);v.setPadding(8,12,8,12);return v;
    }
    private Button button(String label,View.OnClickListener listener) {
        Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);
        b.setAllCaps(false);b.setTextSize(13);b.setOnClickListener(listener);return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(8,19,24));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(22,22,22,25);
        scroll.addView(root);setContentView(scroll);
        TextView brand=text("P.T. ARCHITECH\nPHONE COMPANION",24,LIME);
        brand.setTypeface(null,1);root.addView(brand);
        root.addView(text("Your catalog. Your masters. Your Galaxy Watch.",14,INK));
        watchStatus=text("Checking watch connection...",15,LIME);root.addView(watchStatus);
        Button refresh=button("Check paired watch",v->checkWatch());
        root.addView(refresh);
        status=text("Select a track, then choose its exact MP3/M4A or LRC from Android Files.",14,MUTED);
        root.addView(status);
        Button receipt=button("Check transfer receipts",v->readReceipts());root.addView(receipt);
        Button openWeb=button("Open PTIV Walkman phone player",v->startActivity(
            new Intent(Intent.ACTION_VIEW,Uri.parse("https://flowjuggle.github.io/ptiv-walkman-offline/"))));
        root.addView(openWeb);
        root.addView(text("CATALOG · P.T. ARCHITECH ONLY",18,LIME));
        search=new EditText(this);
        search.setHint("Search song or album");search.setSingleLine(true);
        search.setTextColor(INK);search.setHintTextColor(MUTED);root.addView(search);
        trackCount=text("Loading...",12,MUTED);root.addView(trackCount);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void afterTextChanged(Editable s){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                listLimit=50;renderCatalog();
            }
        });
        root.addView(button("Show more catalog entries",v->{listLimit+=50;renderCatalog();}));
        root.addView(text("Transfers use Wear OS Data Layer (Bluetooth or encrypted relay), not Too Lost uploads. An 'accepted' transfer is not fully delivered until a matching watch checksum receipt appears. Source recordings and lyrics remain untouched.",12,MUTED));
        loadCatalog();checkWatch();
    }

    private void loadCatalog() {
        try(InputStream in=getAssets().open("ptiv-catalog.json");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;
            while((n=in.read(b))>=0)bytes.write(b,0,n);
            JSONObject data=new JSONObject(bytes.toString("UTF-8"));
            JSONArray rows=data.getJSONArray("tracks");
            for(int i=0;i<rows.length();i++)catalog.add(new CatalogRow(rows.getJSONObject(i)));
            if(catalog.size()!=143)status.setText("Catalog count unexpected: "+catalog.size()+". Do not transfer without verifying.");
        } catch(Exception e) {status.setText("Catalog could not load: "+e.getMessage());}
        renderCatalog();
    }
    private void renderCatalog() {
        if(list==null)return;
        list.removeAllViews();
        String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();
        int matches=0,shown=0;
        for(CatalogRow row:catalog){
            if(!query.isEmpty()&&!row.title.toLowerCase(Locale.ROOT).contains(query)
              &&!row.album.toLowerCase(Locale.ROOT).contains(query))continue;
            matches++;
            if(shown>=listLimit)continue;
            shown++;
            TextView title=text(row.title+" · "+row.album+
                (row.release.equals("unreleased")?" · UNRELEASED":""),14,INK);
            list.addView(title);
            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button audio=button("Send MP3/M4A",v->choose(row,false));
            Button lrc=button("Send LRC",v->choose(row,true));
            actions.addView(audio,new LinearLayout.LayoutParams(0,52,1));
            actions.addView(lrc,new LinearLayout.LayoutParams(0,52,1));
            list.addView(actions);
            View line=new View(this);line.setBackgroundColor(Color.rgb(47,67,62));
            list.addView(line,new LinearLayout.LayoutParams(-1,1));
        }
        trackCount.setText(catalog.size()+" catalog entries · "+matches+" matches · "+shown+" displayed");
    }
    private void choose(CatalogRow row,boolean lyrics){
        selected=row;sendLyrics=lyrics;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.setType(lyrics?"*/*":"audio/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(picker,PICK_FILE);}
        catch(Exception e){status.setText("File picker failed: "+e.getMessage());}
    }
    private static String normalized(String value){
        if(value==null)return "";
        String s=Normalizer.normalize(value,Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        s=s.replaceAll("\\s*\\((remastered|mastered|\\d+)\\)\\s*$","");
        return s.replaceAll("[^\\p{L}\\p{N}]","");
    }
    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return uri.getLastPathSegment();
    }
    private String embeddedTitle(Uri uri){
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{
            r.setDataSource(this,uri);
            return r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }catch(Exception ignored){return null;}
        finally{try{r.release();}catch(Exception ignored){}}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_FILE||resultCode!=RESULT_OK||data==null||data.getData()==null||selected==null)return;
        Uri uri=data.getData();
        String title=embeddedTitle(uri),name=displayName(uri);
        String chosen=selected.title;
        String base=name==null?"":name.replaceFirst("(?i)\\.(mp3|m4a|aac|lrc)$","");
        if(!sendLyrics &&
           !normalized(chosen).equals(normalized(title)) &&
           !normalized(chosen).equals(normalized(base))){
            status.setText("BLOCKED: selected file doesn't match '"+chosen+"'. Metadata: "+title+"; filename: "+name);
            return;
        }
        if(sendLyrics && (name==null||!name.toLowerCase(Locale.ROOT).endsWith(".lrc"))){
            status.setText("BLOCKED: lyric transfer requires an original .LRC file.");
            return;
        }
        String ext=sendLyrics?"lrc":name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".m4a")?"m4a":"mp3";
        if(!sendLyrics && name!=null && !name.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a)$")){
            status.setText("For the initial watch transfer, select a compact MP3/M4A export; keep WAV/FLAC masters intact.");
            return;
        }
        CatalogRow target=selected;
        final boolean isLyrics=sendLyrics;
        status.setText("Reading and checksumming "+target.title+" (no source changes)...");
        worker.execute(()->transfer(uri,target,ext,isLyrics));
    }
    private void transfer(Uri uri,CatalogRow track,String ext,boolean lyrics) {
        try{
            int max=lyrics?1_000_000:25_000_000;
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("No access to selected file");
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    if(out.size()+n>max)throw new IOException("Watch transfer is limited to "+(max/1_000_000)+"MB; use MP3/M4A.");
                    out.write(buffer,0,n);
                }
            }
            byte[] audio=out.toByteArray();
            if(audio.length<10)throw new IOException("Selected file is empty");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            StringBuilder h=new StringBuilder();
            for(byte b:digest.digest(audio))h.append(String.format(Locale.US,"%02x",b&0xff));
            PutDataMapRequest request=PutDataMapRequest.create("/ptiv/"+(lyrics?"lrc":"file")+"/"+track.id+"/"+System.currentTimeMillis());
            DataMap map=request.getDataMap();
            map.putString("id",track.id);map.putString("title",track.title);map.putString("album",track.album);
            map.putString("sha256",h.toString());map.putString("extension",ext);
            map.putAsset("payload",Asset.createFromBytes(audio));
            PutDataRequest put=request.asPutDataRequest();
            put.setUrgent();
            Wearable.getDataClient(this).putDataItem(put)
                .addOnSuccessListener(item->runOnUiThread(()->status.setText(
                    "Queued "+track.title+" for watch transfer; SHA-256 "+h.substring(0,12)+"… Waiting for watch receipt.")))
                .addOnFailureListener(error->runOnUiThread(()->status.setText(
                    "Transfer not accepted: "+error.getMessage())));
        }catch(Exception e){
            runOnUiThread(()->status.setText("TRANSFER BLOCKED: "+e.getMessage()));
        }
    }

    private void checkWatch() {
        Wearable.getNodeClient(this).getConnectedNodes()
          .addOnSuccessListener(nodes->{
              StringBuilder message=new StringBuilder();
              for(Node node:nodes){
                  if(message.length()>0)message.append("\n");
                  message.append(node.getDisplayName()).append(node.isNearby()?" · Nearby":" · Remote");
              }
              watchStatus.setText(nodes.isEmpty()?"No Wear OS watch reachable. Verify Galaxy Wearable connection.":message.toString());
          }).addOnFailureListener(e->watchStatus.setText("Watch connection check failed: "+e.getMessage()));
    }
    private void readReceipts() {
        Wearable.getDataClient(this).getDataItems()
          .addOnSuccessListener(buffer->{
              String latest="No watch transfer receipts yet";
              long greatest=0L;
              try{
                  for(DataItem item:buffer){
                      if(!item.getUri().getPath().startsWith("/ptiv/receipt/"))continue;
                      DataMap data=DataMapItem.fromDataItem(item).getDataMap();
                      long created=data.getLong("timestamp");
                      if(created>greatest){
                          greatest=created;
                          latest=data.getString("title")+" · "+data.getString("status")+" · SHA "+
                              data.getString("sha256","");
                      }
                  }
              }finally{buffer.release();}
              status.setText(latest);
          }).addOnFailureListener(e->status.setText("Receipt lookup error: "+e.getMessage()));
    }
    @Override public void onDataChanged(DataEventBuffer events) {
        for(DataEvent e:events){
            if(e.getType()!=DataEvent.TYPE_CHANGED)continue;
            DataItem item=e.getDataItem();
            if(!item.getUri().getPath().startsWith("/ptiv/receipt/"))continue;
            DataMap data=DataMapItem.fromDataItem(item).getDataMap();
            lastAcknowledgment=data.getString("title")+" · "+data.getString("status");
            runOnUiThread(()->status.setText("Watch receipt: "+lastAcknowledgment));
        }
    }
    @Override protected void onStart(){
        super.onStart();Wearable.getDataClient(this).addListener(this);
    }
    @Override protected void onStop(){
        Wearable.getDataClient(this).removeListener(this);
        super.onStop();
    }
    @Override protected void onDestroy(){
        worker.shutdownNow();
        super.onDestroy();
    }
}
