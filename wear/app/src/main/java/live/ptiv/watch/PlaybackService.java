package live.ptiv.watch;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import java.util.*;

public final class PlaybackService extends Service {
    public static final String ACT_PLAY = "live.ptiv.watch.PLAY";
    public static final String ACT_PAUSE = "live.ptiv.watch.PAUSE";
    public static final String ACT_NEXT = "live.ptiv.watch.NEXT";
    public static final String ACT_PREV = "live.ptiv.watch.PREV";
    private static final String CHANNEL = "ptiv_music_playback";
    private static final int NOTIFICATION_ID = 2351;

    public static final class Track {
        public final Uri uri;
        public final String title;
        public final String album;
        public final long duration;
        public Track(Uri uri, String title, String album, long duration) {
            this.uri = uri;
            this.title = title == null || title.trim().isEmpty() ? "Untitled" : title;
            this.album = album == null ? "" : album;
            this.duration = Math.max(0L, duration);
        }
    }

    public final class LocalBinder extends Binder {
        public PlaybackService getService() { return PlaybackService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final ArrayList<Track> queue = new ArrayList<>();
    private final Random random = new Random();
    private MediaPlayer player;
    private MediaSession mediaSession;
    private boolean shuffle = false;
    private boolean continuous = true;
    private int currentIndex = -1;
    private String lastError = "";

    @Override public void onCreate() {
        super.onCreate();
        android.content.SharedPreferences preferences = getSharedPreferences("ptiv_watch_modes", MODE_PRIVATE);
        shuffle = preferences.getBoolean("shuffle", false);
        continuous = preferences.getBoolean("continuous", true);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "PTIV music", NotificationManager.IMPORTANCE_LOW));
        }
        mediaSession = new MediaSession(this, "PTIVWatchPlayer");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onSeekTo(long pos) { seekTo((int) Math.max(0, pos)); }
        });
        mediaSession.setActive(true);
        updateSession();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACT_PLAY.equals(action)) resume();
            else if (ACT_PAUSE.equals(action)) pause();
            else if (ACT_NEXT.equals(action)) next();
            else if (ACT_PREV.equals(action)) previous();
        }
        return START_NOT_STICKY;
    }

    public synchronized void setQueue(List<Track> tracks) {
        Track previous=currentTrack();
        queue.clear();
        if (tracks != null) queue.addAll(tracks);
        int allowedIndex=-1;
        if (previous!=null) {
            for (int i=0;i<queue.size();i++) {
                if (queue.get(i).uri.equals(previous.uri)) { allowedIndex=i;break; }
            }
        }
        if (previous!=null && allowedIndex<0) {
            // Critical security and catalog guard: stop old non-PTIV playback.
            releasePlayer();
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        currentIndex=allowedIndex;
        updateSession();
    }

    public synchronized List<Track> getQueue() { return new ArrayList<>(queue); }
    public synchronized int getCurrentIndex() { return currentIndex; }
    public synchronized Track currentTrack() {
        return currentIndex >= 0 && currentIndex < queue.size() ? queue.get(currentIndex) : null;
    }
    public synchronized boolean isPlaying() {
        try { return player != null && player.isPlaying(); } catch (Exception e) { return false; }
    }
    public synchronized int currentPosition() {
        try { return player == null ? 0 : player.getCurrentPosition(); } catch (Exception e) { return 0; }
    }
    public synchronized int currentDuration() {
        try { return player == null ? 0 : player.getDuration(); } catch (Exception e) { return 0; }
    }
    public synchronized boolean shuffleEnabled() { return shuffle; }
    public synchronized boolean continuousEnabled() { return continuous; }
    public synchronized String getLastError() { return lastError; }

    public synchronized void setShuffle(boolean enabled) {
        shuffle = enabled;
        getSharedPreferences("ptiv_watch_modes", MODE_PRIVATE).edit().putBoolean("shuffle", shuffle).apply();
    }
    public synchronized void setContinuous(boolean enabled) {
        continuous = enabled;
        getSharedPreferences("ptiv_watch_modes", MODE_PRIVATE).edit().putBoolean("continuous", continuous).apply();
    }

    public synchronized void playIndex(int index) {
        if (index < 0 || index >= queue.size()) {
            lastError = "No audio file selected on the watch.";
            return;
        }
        Track track = queue.get(index);
        releasePlayer();
        currentIndex = index;
        lastError = "";
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(this, track.uri);
            mp.setOnCompletionListener(done -> advanceOnCompletion());
            mp.setOnErrorListener((failed, what, extra) -> {
                synchronized (PlaybackService.this) {
                    lastError = "Watch codec/playback error " + what + "/" + extra;
                    stopPlayback();
                }
                return true;
            });
            mp.prepare();
            player = mp;
            player.start();
            updateSession();
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Exception e) {
            lastError = "Cannot play " + track.title + ": " + e.getClass().getSimpleName();
            releasePlayer();
            updateSession();
        }
    }

    public synchronized void resume() {
        if (player == null) {
            if (!queue.isEmpty()) playIndex(currentIndex >= 0 ? currentIndex : 0);
            return;
        }
        try {
            player.start();
            updateSession();
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Exception e) { lastError = "Resume failed: " + e.getMessage(); }
    }
    public synchronized void pause() {
        if (player != null && isPlaying()) player.pause();
        updateSession();
        if (player != null) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }
    public synchronized void seekTo(int millis) {
        if (player == null) return;
        try {
            player.seekTo(Math.min(Math.max(0, millis), Math.max(0, player.getDuration())));
            updateSession();
        } catch (Exception ignored) { }
    }
    public synchronized void next() {
        if (queue.isEmpty()) return;
        int next;
        if (shuffle && queue.size() > 1) {
            next = random.nextInt(queue.size() - 1);
            if (next >= Math.max(0, currentIndex)) next++;
        } else next = (currentIndex + 1) % queue.size();
        playIndex(next);
    }
    public synchronized void previous() {
        if (player != null && currentPosition() > 4000) { seekTo(0); return; }
        if (queue.isEmpty()) return;
        playIndex((currentIndex - 1 + queue.size()) % queue.size());
    }
    private synchronized void advanceOnCompletion() {
        if (continuous) next();
        else {
            updateSession();
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }
    public synchronized void stopPlayback() {
        releasePlayer();
        updateSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }
    private PendingIntent actionIntent(String action, int request) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, request, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification buildNotification() {
        Track t = currentTrack();
        Intent activity = new Intent(this, WatchActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 12, activity,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(t == null ? "PTIV Walkman" : t.title)
            .setContentText("P.T. Architech · On-watch offline music")
            .setContentIntent(open)
            .setOngoing(isPlaying())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,
                "Previous", actionIntent(ACT_PREV, 1)).build())
            .addAction(new Notification.Action.Builder(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying() ? "Pause" : "Play", actionIntent(isPlaying() ? ACT_PAUSE : ACT_PLAY, 2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,
                "Next", actionIntent(ACT_NEXT, 3)).build());
        if (mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0,1,2));
        }
        return builder.build();
    }
    private void updateSession() {
        if (mediaSession == null) return;
        Track t = currentTrack();
        int state = player == null ? PlaybackState.STATE_STOPPED :
            (isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS |
            PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, currentPosition(), 1.0f, SystemClock.elapsedRealtime()).build());
        if (t != null) {
            long duration = currentDuration() > 0 ? currentDuration() : t.duration;
            mediaSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "P.T. Architech")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration).build());
        }
    }

    @Override public void onDestroy() {
        releasePlayer();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        super.onDestroy();
    }
}
