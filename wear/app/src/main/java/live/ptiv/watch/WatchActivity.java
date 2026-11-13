package live.ptiv.watch;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.media.AudioManager;
import android.text.*;
import org.json.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;

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
    private View basePage;
    private FrameLayout cosmicRoot;
    private CosmicView cosmic;
    private boolean cosmicMode=false;
    private AudioManager audioManager;
    private boolean twoFingerActive=false;
    private float twoFingerLastX=0f, twoFingerOriginY=0f;
    private int volumeStepsThisGesture=0;
    private long lastVolumeAt=0L;
    private long singleTapAt=0L, tapStartedAt=0L;
    private int tapSeriesCount=0;
    private float tapSeriesX=0f,tapSeriesY=0f,tapDownX=0f,tapDownY=0f;
    private boolean tapIsValid=false;
    private static final int MAX_STEPS_PER_GESTURE=5;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<LrcLine> timedLyrics = new ArrayList<>();
    private String lyricTrackKey = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updatePlaybackDisplay();
            if(cosmicMode && cosmic!=null)cosmic.invalidate();
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
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(Color.rgb(9,18,22));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(15), dp(12), dp(15), dp(20));
        scroller.addView(root);
        basePage=scroller;
        setContentView(basePage);

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

        Button pairAudio = button("Speaker / earbuds: pair Bluetooth", v -> openBluetoothAudioSettings());
        root.addView(pairAudio, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        Button cosmicButton=button("COSMIC KARAOKE (triple tap player)",v->toggleCosmic());
        root.addView(cosmicButton,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        Button helpButton=button("? 30-second watch controls",v->showMiniTutorial());
        root.addView(helpButton,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(47)));

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
        if(cosmic!=null)cosmic.setScreenVisible(true);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        if(cosmic!=null)cosmic.setScreenVisible(false);
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (connected) {
            try { unbindService(connection); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private void showMiniTutorial() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("P.T. Architech • watch controls")
            .setMessage(
                "1. PLAY: tap the central button; next/previous are on either side.\n\n"+
                "2. VOLUME: place TWO fingers on the screen together. Slide both RIGHT to increase, LEFT to decrease. Each small movement changes one level, never more than five per gesture. In Cosmic mode, use + / - if preferred.\n\n"+
                "3. COSMIC KARAOKE: triple tap a quiet area of the player (not a button). Triple tap again to return. There's also a visible Cosmic button.\n\n"+
                "4. LYRICS: import an approved .LRC. If none is available, Cosmic shows 'NO VERIFIED LRC YET' instead of guessing.\n\n"+
                "5. SPEAKER: tap 'Speaker / earbuds' to open watch Bluetooth settings. The watch plays its own locally transferred P.T. Architech music.\n\n"+
                "P.T. Architech only. Original lyrics and audio remain unchanged.")
            .setPositiveButton("TRY COSMIC", (dialog,which)->toggleCosmic())
            .setNegativeButton("CLOSE",null).show();
    }

    private void toggleCosmic() {
        tapSeriesCount=0;
        cosmicMode=!cosmicMode;
        if(cosmicMode){
            if(cosmicRoot==null){
                cosmicRoot=new FrameLayout(this);
                cosmicRoot.setBackgroundColor(Color.rgb(5,8,22));
                cosmic=new CosmicView(this,new CosmicView.Source() {
                    @Override public String title(){
                        PlaybackService.Track t=player==null?null:player.currentTrack();
                        return t==null?"Select PTIV song":t.title;
                    }
                    @Override public String lyric(){return karaokeLine(false);}
                    @Override public String nextLyric(){return karaokeLine(true);}
                    @Override public boolean hasTimedLyrics(){return !timedLyrics.isEmpty();}
                    @Override public boolean isPlaying(){return player!=null&&player.isPlaying();}
                    @Override public int positionMs(){return player==null?0:player.currentPosition();}
                    @Override public int durationMs(){return player==null?0:player.currentDuration();}
                });
                cosmicRoot.addView(cosmic,new FrameLayout.LayoutParams(-1,-1));
                // Explicit controls are a fallback when two fingers cannot be used.
                Button back=button("BACK",v->toggleCosmic());back.setTextSize(11);
                FrameLayout.LayoutParams backLp=new FrameLayout.LayoutParams(dp(72),dp(48),Gravity.TOP|Gravity.START);
                backLp.setMargins(dp(8),dp(8),0,0);
                cosmicRoot.addView(back,backLp);
                Button minus=button("−",v->changeMusicVolume(-1));minus.setTextSize(22);
                FrameLayout.LayoutParams leftLp=new FrameLayout.LayoutParams(dp(50),dp(52),Gravity.BOTTOM|Gravity.START);
                leftLp.setMargins(dp(7),0,0,dp(8));cosmicRoot.addView(minus,leftLp);
                Button plus=button("+",v->changeMusicVolume(1));plus.setTextSize(22);
                FrameLayout.LayoutParams rightLp=new FrameLayout.LayoutParams(dp(50),dp(52),Gravity.BOTTOM|Gravity.END);
                rightLp.setMargins(0,0,dp(7),dp(8));cosmicRoot.addView(plus,rightLp);
            }
            setContentView(cosmicRoot);
            cosmic.setScreenVisible(true);
            cosmic.invalidate();
        }else{
            if(cosmic!=null)cosmic.setScreenVisible(false);
            setContentView(basePage);
        }
    }

    private String karaokeLine(boolean next){
        PlaybackService.Track t=player==null?null:player.currentTrack();
        loadLyricsIfChanged(t);
        if(timedLyrics.isEmpty())return "";
        int i=-1,ms=player==null?0:player.currentPosition();
        for(int k=0;k<timedLyrics.size();k++){
            if(timedLyrics.get(k).millis<=ms)i=k;
            else break;
        }
        int wanted=next?i+1:i;
        if(wanted<0)return "♪";
        if(wanted>=timedLyrics.size())return "♪";
        return timedLyrics.get(wanted).text;
    }

    @Override public void onBackPressed(){
        if(cosmicMode)toggleCosmic();
        else super.onBackPressed();
    }

    private boolean tapOnInteractive(View view,float x,float y) {
        if(view==null||!view.isShown())return false;
        if(view instanceof Button || view instanceof EditText){
            android.graphics.Rect r=new android.graphics.Rect();
            return view.getGlobalVisibleRect(r)&&r.contains((int)x,(int)y);
        }
        if(view instanceof ViewGroup){
            ViewGroup v=(ViewGroup)view;
            for(int i=0;i<v.getChildCount();i++){
                if(tapOnInteractive(v.getChildAt(i),x,y))return true;
            }
        }
        return false;
    }

    private void changeMusicVolume(int direction) {
        if(audioManager==null)return;
        if(audioManager.isVolumeFixed()){
            if(cosmic!=null)cosmic.showVolume("FIXED OUTPUT VOLUME");
            return;
        }
        int max=audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int next=Math.max(0,Math.min(max,current+(direction>0?1:-1)));
        if(next==current)return;
        try{
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);
            String status="VOLUME "+next+" / "+max;
            if(cosmicMode&&cosmic!=null)cosmic.showVolume(status);
            else android.widget.Toast.makeText(this,status,android.widget.Toast.LENGTH_SHORT).show();
            View target=cosmicMode?cosmic:basePage;
            if(target!=null)target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }catch(SecurityException ex){
            if(cosmic!=null)cosmic.showVolume("USE WATCH VOLUME SETTINGS");
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            tapDownX=e.getRawX();tapDownY=e.getRawY();
            tapStartedAt=android.os.SystemClock.uptimeMillis();
            tapIsValid=true;
            twoFingerActive=false;
        } else if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()==2) {
            twoFingerActive=true;tapIsValid=false;tapSeriesCount=0;
            twoFingerLastX=(e.getX(0)+e.getX(1))/2f;
            twoFingerOriginY=(e.getY(0)+e.getY(1))/2f;
            volumeStepsThisGesture=0;lastVolumeAt=0L;
            MotionEvent cancel=MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);cancel.recycle();
            return true;
        } else if(twoFingerActive && action==MotionEvent.ACTION_MOVE) {
            if(e.getPointerCount()>=2){
                float centerX=(e.getX(0)+e.getX(1))/2f;
                float centerY=(e.getY(0)+e.getY(1))/2f;
                long now=android.os.SystemClock.uptimeMillis();
                if(Math.abs(centerY-twoFingerOriginY)<=dp(72)){
                    float delta=centerX-twoFingerLastX;
                    if(Math.abs(delta)>=dp(29) &&
                        volumeStepsThisGesture<MAX_STEPS_PER_GESTURE && now-lastVolumeAt>=75){
                        changeMusicVolume(delta>0?1:-1);
                        twoFingerLastX=centerX;
                        volumeStepsThisGesture++;
                        lastVolumeAt=now;
                    }
                }
            }
            return true;
        } else if(twoFingerActive && (action==MotionEvent.ACTION_POINTER_UP ||
                                     action==MotionEvent.ACTION_UP ||
                                     action==MotionEvent.ACTION_CANCEL)){
            if(action!=MotionEvent.ACTION_POINTER_UP)twoFingerActive=false;
            tapIsValid=false;return true;
        } else if(action==MotionEvent.ACTION_MOVE){
            if(Math.hypot(e.getRawX()-tapDownX,e.getRawY()-tapDownY)>dp(20))
                tapIsValid=false;
        } else if(action==MotionEvent.ACTION_CANCEL){
            tapIsValid=false;tapSeriesCount=0;
        } else if(action==MotionEvent.ACTION_UP) {
            long now=android.os.SystemClock.uptimeMillis();
            boolean candidate=tapIsValid && now-tapStartedAt<280 &&
                !tapOnInteractive(cosmicMode?cosmicRoot:basePage,e.getRawX(),e.getRawY());
            boolean result=super.dispatchTouchEvent(e);
            if(candidate){
                if(now-singleTapAt<=400 &&
                   Math.hypot(e.getRawX()-tapSeriesX,e.getRawY()-tapSeriesY)<dp(65)){
                    tapSeriesCount++;
                }else tapSeriesCount=1;
                singleTapAt=now;tapSeriesX=e.getRawX();tapSeriesY=e.getRawY();
                if(tapSeriesCount>=3){tapSeriesCount=0;toggleCosmic();return true;}
            }else tapSeriesCount=0;
            return result;
        }
        return super.dispatchTouchEvent(e);
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
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DURATION
        };
        try (Cursor cursor = getContentResolver().query(
            source, columns, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                int idColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int albumColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int artistColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int pathColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);
                int durColumn=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int rejected=0;
                while (cursor.moveToNext() && songs.size()<2000) {
                    long duration=cursor.getLong(durColumn);
                    if (duration<3000L)continue;
                    String title=cursor.getString(titleColumn);
                    String album=cursor.getString(albumColumn);
                    String artist=cursor.getString(artistColumn);
                    String path=cursor.getString(pathColumn);
                    // Fail closed: PTIV provenance AND exact catalog title. Never play arbitrary watch MP3s.
                    if (!explicitPTIVSource(artist,path)) { rejected++; continue; }
                    CatalogRow approved=authorizedCatalogMatch(title,album);
                    if (approved==null) { rejected++; continue; }
                    long id=cursor.getLong(idColumn);
                    Uri uri=ContentUris.withAppendedId(source,id);
                    songs.add(new PlaybackService.Track(uri,approved.title,approved.album,duration));
                }
                if (rejected>0) {
                    statusText.setText(rejected+" other watch recordings excluded. Only authorized P.T. Architech catalog titles may play.");
                }
            }
            if (player!=null)player.setQueue(songs);
            libraryText.setText(songs.size()+" P.T. Architech tracks ready on watch");
            statusText.setText(songs.isEmpty() ?
                "No authorized PTIV audio on watch. Transfer a catalog-matching MP3/M4A to Music/PTIV/, then rescan. Unrelated MP3s are excluded." :
                "P.T. Architech only: "+songs.size()+" local tracks. Unrelated files are excluded. Playback works offline.");
            renderSongList();
        } catch (Exception e) {
            statusText.setText("Watch media scan failed: "+e.getClass().getSimpleName());
        }
    }

    private static String key(String title){
        if(title==null)return "";
        String n=Normalizer.normalize(title,Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        // Suffixes are versions of the same supplied recording, not new words.
        n=n.replaceAll("\\s*\\((remastered|mastered|\\d+)\\)\\s*$","");
        return n.replaceAll("[^\\p{L}\\p{N}]","");
    }
    private CatalogRow authorizedCatalogMatch(String title,String album){
        String wanted=key(title);
        if(wanted.isEmpty())return null;
        CatalogRow candidate=null;
        int count=0;
        for(CatalogRow row:catalogRows){
            if(wanted.equals(key(row.title))){
                if(album!=null && key(album).equals(key(row.album)))return row;
                count++;
                candidate=row;
            }
        }
        // A reused title across releases needs a real album match; don't guess.
        return count==1?candidate:null;
    }
    private boolean explicitPTIVSource(String artist,String relativePath){
        if("ptarchitech".equals(key(artist)))return true;
        if(relativePath==null)return false;
        String path=relativePath.replace('\\','/').toLowerCase(Locale.ROOT);
        return path.startsWith("music/ptiv/") ||
               path.startsWith("music/p.t. architech/") ||
               path.startsWith("music/pt architech/");
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
                    if(actual.equals(wanted) && row.album.equals(songs.get(i).album)){
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

    private void openBluetoothAudioSettings() {
        // The OS, not this app, handles pairing, audio routing and authorization.
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                if (statusText != null) {
                    statusText.setText("Open Connections / Bluetooth to pair speaker or headphones.");
                }
            } catch (Exception fallback) {
                if (statusText != null) {
                    statusText.setText("Open Watch Settings > Connections > Bluetooth manually.");
                }
            }
        }
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
