package com.bassbeats.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.KeyEvent;

public class PlaybackService extends Service {
    public static final String PLAY = "com.bassbeats.player.PLAY";
    public static final String TOGGLE = "com.bassbeats.player.TOGGLE";
    public static final String NEXT = "com.bassbeats.player.NEXT";
    public static final String PREV = "com.bassbeats.player.PREV";
    public static final String EQ_BAND = "com.bassbeats.player.EQ_BAND";
    public static final String EQ_BASS = "com.bassbeats.player.EQ_BASS";
    public static final String STATE = "com.bassbeats.player.STATE";
    public static final String GET_STATE = "com.bassbeats.player.GET_STATE";
    public static final String SEEK = "com.bassbeats.player.SEEK";
    public static final String SKIP_NEXT = "com.bassbeats.player.SKIP_NEXT";
    public static final String SKIP_PREV = "com.bassbeats.player.SKIP_PREV";

    private static final String CHANNEL_ID = "bass_playback";
    private static final int NOTIFICATION_ID = 42;
    private MediaPlayer player;
    private MediaSession session;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private boolean playerPrepared = false;
    private String title = "BASS BEATS";
    private String artist = "Offline Player";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("BASS BEATS playback controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        session = new MediaSession(this, "BASS BEATS");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onSkipToNext() { sendBroadcast(new Intent(SKIP_NEXT)); }
            @Override public void onSkipToPrevious() { sendBroadcast(new Intent(SKIP_PREV)); }
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent == null ? null : intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    int key = event.getKeyCode();
                    if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePlayback(); return true; }
                    if (key == KeyEvent.KEYCODE_MEDIA_PLAY) { resumePlayback(); return true; }
                    if (key == KeyEvent.KEYCODE_MEDIA_PAUSE) { pausePlayback(); return true; }
                    if (key == KeyEvent.KEYCODE_MEDIA_NEXT) { sendBroadcast(new Intent(SKIP_NEXT)); return true; }
                    if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { sendBroadcast(new Intent(SKIP_PREV)); return true; }
                }
                return super.onMediaButtonEvent(intent);
            }
        });
        session.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (PLAY.equals(action)) startTrack(intent);
        else if (TOGGLE.equals(action)) togglePlayback();
        else if (GET_STATE.equals(action)) broadcastState(player != null && player.isPlaying());
        else if (SEEK.equals(action)) { if (player != null && playerPrepared) try { long duration = Math.max(0L, (long) player.getDuration()); long target = Math.max(0L, Math.min(duration, intent.getLongExtra("position", 0L))); player.seekTo((int)Math.min(Integer.MAX_VALUE, target)); broadcastState(player.isPlaying()); } catch (IllegalStateException ignored) {} }
        else if (NEXT.equals(action)) sendBroadcast(new Intent(SKIP_NEXT));
        else if (PREV.equals(action)) sendBroadcast(new Intent(SKIP_PREV));
        else if (EQ_BAND.equals(action)) {
            int band = intent.getIntExtra("band", -1);
            int level = intent.getIntExtra("level", 0);
            getSharedPreferences("audio_effects", MODE_PRIVATE).edit().putInt("band_" + band, level).apply();
            applyBand(band, level);
        } else if (EQ_BASS.equals(action)) {
            boolean enabled = intent.getBooleanExtra("enabled", false);
            int strength = intent.getIntExtra("strength", 700);
            getSharedPreferences("audio_effects", MODE_PRIVATE).edit().putBoolean("bass_enabled", enabled).putInt("bass_strength", strength).apply();
            applyBass(enabled, strength);
        }
        return START_NOT_STICKY;
    }

    private void startTrack(Intent intent) {
        String uriString = intent.getStringExtra("uri");
        if (uriString == null) { broadcastState(false); return; }
        title = intent.getStringExtra("title");
        artist = intent.getStringExtra("artist");
        if (title == null) title = "Unknown title";
        if (artist == null) artist = "Unknown artist";
        // Promote to foreground immediately, before asynchronous media preparation.
        startForeground(NOTIFICATION_ID, buildNotification(false));
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this, Uri.parse(uriString));
            player.setOnPreparedListener(mp -> {
                playerPrepared = true;
                mp.start();
                initAudioEffects(mp.getAudioSessionId());
                updateSession(true);
                updateNotification(true);
                broadcastState(true);
            });
            player.setOnCompletionListener(mp -> {
                updateSession(false);
                updateNotification(false);
                broadcastState(false);
                sendBroadcast(new Intent(SKIP_NEXT));
            });
            player.setOnErrorListener((mp, what, extra) -> {
                broadcastState(false);
                updateNotification(false);
                return true;
            });
            player.prepareAsync();
            updateSession(false);
        } catch (Exception e) {
            releasePlayer();
            broadcastState(false);
            updateNotification(false);
        }
    }

    private void initAudioEffects(int audioSessionId) {
        releaseAudioEffects();
        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            for (int i = 0; i < 10; i++) {
                int level = getSharedPreferences("audio_effects", MODE_PRIVATE).getInt("band_" + i, 0);
                applyBand(i, level);
            }
        } catch (Exception ignored) { if (equalizer != null) { try { equalizer.release(); } catch (Exception e) {} equalizer = null; } }
        try {
            bassBoost = new BassBoost(0, audioSessionId);
            boolean enabled = getSharedPreferences("audio_effects", MODE_PRIVATE).getBoolean("bass_enabled", false);
            int strength = getSharedPreferences("audio_effects", MODE_PRIVATE).getInt("bass_strength", 700);
            bassBoost.setStrength((short) Math.max(0, Math.min(1000, strength)));
            bassBoost.setEnabled(enabled);
        } catch (Exception ignored) { if (bassBoost != null) { try { bassBoost.release(); } catch (Exception e) {} bassBoost = null; } }
    }

    private void applyBand(int uiBand, int db) {
        if (equalizer == null || uiBand < 0 || uiBand >= 10) return;
        try {
            short count = equalizer.getNumberOfBands();
            if (count <= 0) return;
            int[] targetHz = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
            short best = 0;
            long bestDiff = Long.MAX_VALUE;
            for (short b = 0; b < count; b++) {
                long centerHz = equalizer.getCenterFreq(b) / 1000L;
                long diff = Math.abs(centerHz - targetHz[uiBand]);
                if (diff < bestDiff) { bestDiff = diff; best = b; }
            }
            short[] range = equalizer.getBandLevelRange();
            int millibels = db * 100;
            millibels = Math.max(range[0], Math.min(range[1], millibels));
            equalizer.setBandLevel(best, (short) millibels);
        } catch (Exception ignored) { }
    }

    private void applyBass(boolean enabled, int strength) {
        if (bassBoost == null) return;
        try { bassBoost.setStrength((short) Math.max(0, Math.min(1000, strength))); bassBoost.setEnabled(enabled); } catch (Exception ignored) { }
    }

    private void releaseAudioEffects() {
        if (equalizer != null) { try { equalizer.setEnabled(false); equalizer.release(); } catch (Exception ignored) { } equalizer = null; }
        if (bassBoost != null) { try { bassBoost.setEnabled(false); bassBoost.release(); } catch (Exception ignored) { } bassBoost = null; }
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) pausePlayback(); else resumePlayback();
    }
    private void resumePlayback() {
        if (player == null) return;
        try { player.start(); updateSession(true); updateNotification(true); broadcastState(true); } catch (IllegalStateException ignored) { }
    }
    private void pausePlayback() {
        if (player != null) {
            try { if (player.isPlaying()) player.pause(); } catch (IllegalStateException ignored) { }
        }
        updateSession(false); updateNotification(false); broadcastState(false);
    }
    private void updateSession(boolean isPlaying) {
        if (session == null) return;
        long duration = 0, position = 0;
        if (player != null) try { duration = player.getDuration(); position = player.getCurrentPosition(); } catch (IllegalStateException ignored) { }
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, artist).putLong(MediaMetadata.METADATA_KEY_DURATION, duration).build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, 1f).build());
    }
    private PendingIntent serviceAction(String action, int requestCode) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification buildNotification(boolean isPlaying) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setContentTitle(title).setContentText(artist).setSmallIcon(android.R.drawable.ic_media_play).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(isPlaying)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", serviceAction(PREV, 1)).build())
            .addAction(new Notification.Action.Builder(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, isPlaying ? "Pause" : "Play", serviceAction(TOGGLE, 2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", serviceAction(NEXT, 3)).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && session != null) {
            builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }
    private void updateNotification(boolean isPlaying) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(isPlaying));
    }
    private void broadcastState(boolean isPlaying) {
        Intent i = new Intent(STATE).setPackage(getPackageName())
            .putExtra("playing", isPlaying).putExtra("title", title).putExtra("artist", artist);
        long duration = 0L, position = 0L;
        if (player != null && playerPrepared) {
            try { duration = Math.max(0, (long) player.getDuration()); position = Math.max(0, (long) player.getCurrentPosition()); }
            catch (IllegalStateException ignored) { duration = 0L; position = 0L; }
        }
        i.putExtra("duration", duration).putExtra("position", position);
        sendBroadcast(i);
    }
    private void releasePlayer() { playerPrepared = false; releaseAudioEffects(); if (player != null) { try { player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.release(); } catch (Exception ignored) { } player = null; } }
    @Override public void onDestroy() {
        releasePlayer();
        if (session != null) { session.setActive(false); session.release(); session = null; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
