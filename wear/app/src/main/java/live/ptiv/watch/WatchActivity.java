package live.ptiv.watch;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.text.*;
import org.json.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class WatchActivity extends Activity {
    private static final int PERMISSION_MEDIA = 351;
    private static final int PICK_LRC = 352;
    private static final int LIME = Color.rgb(195,247,113);
    private static final int MUTED = Color.rgb(180,202,193);
    private PlaybackService player;
    private boolean connected = false;
    private final ArrayList<PlaybackService.Track> songs = new ArrayList<>();
    private TextView nowText, progressText, lyricText, nextLyricText, statusText, libraryText;
    private Button playButton, shuffleButton, loopButton;
    private LinearLayout songList;
    private LinearLayout catalogContainer, catalogList;
    private EditText catalogSearch;
    private TextView catalogSummary;
    private final ArrayList<CatalogRow> catalogRows = new ArrayList<>();
    private static final class CatalogRow {
        final String title,album,status;
        CatalogRow(String title,String album,String status) {
            this.title=title;this.album=album;this.status=status;
        }
    }
    private CassetteView cassette;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<LrcLine> timedLyrics = new ArrayList<>();
    private String lyricTrackKey = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updatePlaybackDisplay();
            handler.postDelayed(this, 750L);
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            player = ((PlaybackService.LocalBinder) binder).getService();
            connected = true;
            if (!songs.isEmpty()) player.setQueue(songs);
            updatePlaybackDisplay();
            renderSongList();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            connected = false;
            player = null;
            updatePlaybackDisplay();
        }
    };

    private static final class LrcLine {
        final long millis;
        final String text;
        LrcLine(long millis, String text) { this.millis = millis; this.text = text; }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(3), dp(5), dp(3), dp(5));
        return view;
    }
    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setOnClickListener(listener);
        return b;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(10,24,27));
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(Color.rgb(9,18,22));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(15), dp(12), dp(15), dp(20));
        scroller.addView(root);
        setContentView(scroller);

        TextView brand = label("PTIV  WALKMAN", 20, LIME);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(brand);

        cassette = new CassetteView(this);
        root.addView(cassette, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));

        nowText = label("On-watch offline music", 16, Color.WHITE);
        nowText.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(nowText);
        progressText = label("0:00 / 0:00", 11, MUTED);
        root.addView(progressText);

        LinearLayout transport = new LinearLayout(this);
        transport.setGravity(Gravity.CENTER);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        Button previous = button("◀◀", v->{ if (player != null) { player.previous(); updatePlaybackDisplay(); } });
        playButton = button("▶", v->{
            if (player == null) return;
            if (player.isPlaying()) player.pause(); else player.resume();
            updatePlaybackDisplay();
        });
        Button next = button("▶▶", v->{ if (player != null) { player.next(); updatePlaybackDisplay(); } });
        for (Button b : new Button[]{previous,playButton,next}) {
            transport.addView(b,new LinearLayout.LayoutParams(0,dp(60),1));
        }
        root.addView(transport);

        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        shuffleButton = button("🔀 Off",v->{
            if (player != null) {player.setShuffle(!player.shuffleEnabled());updatePlaybackDisplay();}
        });
        loopButton = button("↻ On",v->{
            if (player != null) {player.setContinuous(!player.continuousEnabled());updatePlaybackDisplay();}
        });
        modes.addView(shuffleButton,new LinearLayout.LayoutParams(0,dp(55),1));
        modes.addView(loopButton,new LinearLayout.LayoutParams(0,dp(55),1));
        root.addView(modes);

        TextView lyricHeader = label("LYRICS", 13, LIME);
        lyricHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(lyricHeader);
        lyricText = label("Load an LRC file on the watch", 17, Color.WHITE);
        lyricText.setMinHeight(dp(62));
        root.addView(lyricText);
        nextLyricText = label("Original words only · timing shown as imported, not verified", 11, MUTED);
        root.addView(nextLyricText);

        Button importLrc = button("Import .LRC from watch",v->pickLrcFile());
        root.addView(importLrc,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        statusText = label("Initializing local player…",12,MUTED);
        root.addView(statusText);
        libraryText = label("Watch music library",13,LIME);
        root.addView(libraryText);
        Button refresh = button("↻ Scan on-watch music",v->requestOrScanAudio());
        root.addView(refresh);
        Button showCatalog = button("Browse all PTIV releases",v->{
            if (catalogContainer.getVisibility()==View.VISIBLE) {
                catalogContainer.setVisibility(View.GONE);
            } else {
                catalogContainer.setVisibility(View.VISIBLE);
                renderWatchCatalog();
            }
        });
        root.addView(showCatalog,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        catalogContainer = new LinearLayout(this);
        catalogContainer.setOrientation(LinearLayout.VERTICAL);
        catalogContainer.setVisibility(View.GONE);
        catalogSummary = label("Loading PTIV catalog…",12,LIME);
        catalogContainer.addView(catalogSummary);
        catalogSearch = new EditText(this);
        catalogSearch.setTextColor(Color.WHITE);
        catalogSearch.setHintTextColor(MUTED);
        catalogSearch.setSingleLine(true);
        catalogSearch.setHint("Find track / album");
        catalogSearch.setTextSize(13);
        catalogContainer.addView(catalogSearch);
        catalogList = new LinearLayout(this);
        catalogList.setOrientation(LinearLayout.VERTICAL);
        catalogContainer.addView(catalogList);
        root.addView(catalogContainer);
        catalogSearch.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderWatchCatalog();}
            @Override public void afterTextChanged(Editable s){}
        });
        loadWatchCatalog();
        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);
        songList.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(songList);

        startService(new Intent(this,PlaybackService.class));
        bindService(new Intent(this,PlaybackService.class),connection,BIND_AUTO_CREATE);
        requestOrScanAudio();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (connected) {
            try { unbindService(connection); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private String requiredAudioPermission() {
        return Build.VERSION.SDK_INT >= 33 ?
            Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }
    private void requestOrScanAudio() {
        if (checkSelfPermission(requiredAudioPermission()) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{requiredAudioPermission()}, PERMISSION_MEDIA);
            statusText.setText("Grant access to music files already stored on your watch.");
            return;
        }
        scanMediaStore();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if (request == PERMISSION_MEDIA) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
                scanMediaStore();
            else statusText.setText("Audio permission denied. Watch playback needs permission to read local music.");
        }
    }

    private void scanMediaStore() {
        songs.clear();
        Uri source = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        };
        try (Cursor cursor = getContentResolver().query(
            source, columns, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                int idColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int albumColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                while (cursor.moveToNext() && songs.size()<2000) {
                    long duration=cursor.getLong(durColumn);
                    if (duration<3000L)continue;
                    long id=cursor.getLong(idColumn);
                    Uri uri=ContentUris.withAppendedId(source,id);
                    songs.add(new PlaybackService.Track(uri,cursor.getString(titleColumn),
                        cursor.getString(albumColumn),duration));
                }
            }
            if (player!=null)player.setQueue(songs);
            libraryText.setText(songs.size()+" songs stored on watch");
            statusText.setText(songs.isEmpty() ?
                "No watch audio found. Transfer an MP3/M4A to the watch with Galaxy Wearable or paired ADB, then rescan." :
                "Local watch library ready. Playback works without phone or internet.");
            renderSongList();
        } catch (Exception e) {
            statusText.setText("Watch media scan failed: "+e.getClass().getSimpleName());
        }
    }

    private static String key(String title){
        return title==null?"":title.toLowerCase(Locale.ROOT)
            .replaceAll("\\([^\\)]*\\)","")
            .replaceAll("[^a-z0-9]","");
    }
    private void loadWatchCatalog() {
        catalogRows.clear();
        try(InputStream in=getAssets().open("ptiv-catalog.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            JSONObject json=new JSONObject(out.toString("UTF-8"));
            JSONArray rows=json.getJSONArray("tracks");
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.getJSONObject(i);
                catalogRows.add(new CatalogRow(
                    item.getString("title"),item.getString("album"),
                    item.optString("releaseStatus","published")));
            }
            renderWatchCatalog();
        }catch(Exception e){
            catalogSummary.setText("Catalog unavailable: "+e.getClass().getSimpleName());
        }
    }
    private void renderWatchCatalog() {
        if(catalogList==null||catalogSearch==null)return;
        catalogList.removeAllViews();
        String query=catalogSearch.getText().toString().toLowerCase(Locale.ROOT).trim();
        int matched=0,shown=0;
        for(CatalogRow row:catalogRows){
            if(!query.isEmpty() && !row.title.toLowerCase(Locale.ROOT).contains(query)
                && !row.album.toLowerCase(Locale.ROOT).contains(query))continue;
            matched++;
            if(shown>=20)continue;
            shown++;
            Button b=button(row.title+" · "+row.album+(row.status.equals("unreleased")?" · UNRELEASED":""),v->{
                String wanted=key(row.title);
                for(int i=0;i<songs.size();i++){
                    String actual=key(songs.get(i).title);
                    if(actual.equals(wanted)||actual.contains(wanted)||wanted.contains(actual)){
                        if(player!=null){player.setQueue(songs);player.playIndex(i);updatePlaybackDisplay();}
                        return;
                    }
                }
                statusText.setText("This catalog title isn't stored on the watch. Transfer the audio first.");
            });
            b.setTextSize(11);
            catalogList.addView(b,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        }
        catalogSummary.setText(catalogRows.size()+" catalog entries · "+matched+
            " matching · showing "+shown+" · separate watch music storage");
    }

    private void renderSongList() {
        if (songList==null)return;
        songList.removeAllViews();
        if (songs.isEmpty())return;
        int count=Math.min(100,songs.size());
        for (int i=0;i<count;i++) {
            final int index=i;
            PlaybackService.Track item=songs.get(i);
            Button b=button((i+1)+". "+item.title,v->{
                if (player==null){statusText.setText("Player connecting. Tap again in a moment.");return;}
                player.setQueue(songs);
                player.playIndex(index);
                lyricTrackKey="";
                updatePlaybackDisplay();
            });
            b.setTextSize(12);
            songList.addView(b,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(51)));
        }
        if (songs.size()>100)songList.addView(label(
            "First 100 of "+songs.size()+" tracks shown. Searchable catalog comes in the next watch revision.",
            11,MUTED));
    }

    private String lyricFilename(PlaybackService.Track track) {
        return track.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_")+".lrc";
    }
    private File lyricsDirectory() {
        File folder=new File(getFilesDir(),"lyrics");
        if(!folder.exists())folder.mkdirs();
        return folder;
    }

    private void pickLrcFile() {
        if (player==null||player.currentTrack()==null) {
            statusText.setText("Play or select a song before importing its .LRC lyrics.");
            return;
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","application/octet-stream","application/x-subrip"});
        try {
            startActivityForResult(intent,PICK_LRC);
        } catch (Exception e) {
            statusText.setText("No watch file picker available. Transfer the matching .LRC to the app using paired ADB.");
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=PICK_LRC||result!=RESULT_OK||data==null||data.getData()==null)return;
        PlaybackService.Track track=player==null?null:player.currentTrack();
        if (track==null)return;
        File destination=new File(lyricsDirectory(),lyricFilename(track));
        try (InputStream in=getContentResolver().openInputStream(data.getData());
             FileOutputStream out=new FileOutputStream(destination)) {
            if (in==null)throw new IOException("Audio picker returned null input");
            byte[] buffer=new byte[8192];int length;long total=0;
            while((length=in.read(buffer))>0){
                total+=length;if(total>1000000)throw new IOException("LRC exceeds 1 MB");
                out.write(buffer,0,length);
            }
            lyricTrackKey="";
            updatePlaybackDisplay();
            statusText.setText("Local lyrics imported. TIMESTAMPS NOT VERIFIED by this watch.");
        } catch(Exception e){
            destination.delete();
            statusText.setText("Could not import LRC: "+e.getMessage());
        }
    }

    private void loadLyricsIfChanged(PlaybackService.Track track) {
        String key=track==null?"":lyricFilename(track);
        if(key.equals(lyricTrackKey))return;
        lyricTrackKey=key;
        timedLyrics.clear();
        if(track==null)return;
        File file=new File(lyricsDirectory(),key);
        if(!file.exists())return;
        Pattern pattern=Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)");
        try(BufferedReader reader=new BufferedReader(
            new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null&&timedLyrics.size()<3000) {
                Matcher match=pattern.matcher(line);
                if(!match.find())continue;
                long minutes=Long.parseLong(match.group(1));
                long seconds=Long.parseLong(match.group(2));
                String fraction=match.group(3)==null?"000":
                    (match.group(3)+"000").substring(0,3);
                long position=(minutes*60+seconds)*1000+Long.parseLong(fraction);
                timedLyrics.add(new LrcLine(position,match.group(4)));
            }
            Collections.sort(timedLyrics,Comparator.comparingLong(x->x.millis));
        }catch(Exception e){timedLyrics.clear();}
    }

    private void updatePlaybackDisplay() {
        if(playButton==null)return;
        boolean active=player!=null && player.isPlaying();
        playButton.setText(active?"❚❚":"▶");
        shuffleButton.setText("🔀 "+(player!=null&&player.shuffleEnabled()?"ON":"OFF"));
        loopButton.setText("↻ "+(player!=null&&player.continuousEnabled()?"ON":"OFF"));
        PlaybackService.Track track=player==null?null:player.currentTrack();
        nowText.setText(track==null?"Choose a watch song":track.title);
        int pos=player==null?0:player.currentPosition(),dur=player==null?0:player.currentDuration();
        progressText.setText(time(pos)+" / "+time(dur));
        if(player!=null&&!player.getLastError().isEmpty())statusText.setText(player.getLastError());
        loadLyricsIfChanged(track);
        if(timedLyrics.isEmpty()) {
            lyricText.setText("No timed lyrics imported");
            nextLyricText.setText("Use an existing .LRC with original words. Any imported timestamps remain unverified.");
        }else{
            int current=-1;
            for(int i=0;i<timedLyrics.size();i++){
                if(timedLyrics.get(i).millis<=pos)current=i;
                else break;
            }
            lyricText.setText(current<0?"♪":timedLyrics.get(current).text);
            nextLyricText.setText(current+1<timedLyrics.size() ?
                "Next: "+timedLyrics.get(current+1).text : "LRC timing draft · source kept unchanged");
        }
        cassette.setPlaying(active);
    }

    private String time(long millis) {
        long seconds=Math.max(0,millis)/1000;
        return (seconds/60)+":"+String.format(Locale.US,"%02d",seconds%60);
    }

    private final class CassetteView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean playing=false;
        CassetteView(Context context) {super(context);}
        void setPlaying(boolean enabled) {
            if(playing!=enabled){playing=enabled;invalidate();}
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width=getWidth(),height=getHeight();
            paint.setColor(Color.rgb(25,48,50));paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(dp(5),dp(4),width-dp(5),height-dp(4),dp(14),dp(14),paint);
            paint.setColor(LIME);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
            canvas.drawRoundRect(dp(6),dp(6),width-dp(6),height-dp(6),dp(14),dp(14),paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(12));canvas.drawText("SIDE PTIV  •  OFFLINE",width/2,dp(29),paint);
            for(int i=0;i<2;i++){
                float cx=width*(i==0?.31f:.69f),cy=height*.64f,rad=Math.min(height*.22f,width*.14f);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(8));
                paint.setColor(Color.rgb(139,167,153));canvas.drawCircle(cx,cy,rad,paint);
                paint.setColor(Color.rgb(209,237,219));paint.setStrokeWidth(dp(3));
                float angle=playing?(SystemClock.uptimeMillis()%4000)*360f/4000f:0f;
                for(int j=0;j<6;j++){
                    float a=(float)Math.toRadians(angle+j*60);
                    canvas.drawLine(cx+(float)Math.cos(a)*rad*.32f,
                        cy+(float)Math.sin(a)*rad*.32f,
                        cx+(float)Math.cos(a)*rad*.88f,
                        cy+(float)Math.sin(a)*rad*.88f,paint);
                }
                paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(10,23,26));
                canvas.drawCircle(cx,cy,rad*.34f,paint);
            }
            if(playing)postInvalidateDelayed(160);
        }
    }
}
