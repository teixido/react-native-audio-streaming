package com.reactlibrary.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.reactlibrary.TXAudioStreamingModule;

import java.io.IOException;

/**
 * Created by larcho on 3/30/17.
 */

public class AudioService extends Service implements OnPreparedListener, OnAudioFocusChangeListener, OnErrorListener {

    public enum PLAY_STATUS {
        PLAY_STATUS_PLAYING,
        PLAY_STATUS_PAUSED,
        PLAY_STATUS_STOPPED
    };

    private MediaPlayer player;
    private WifiLock wifiLock;
    private static final int NOTIFICATION_ID = 1;
    private PLAY_STATUS playStatus = PLAY_STATUS.PLAY_STATUS_STOPPED;
    private BroadcastReceiver statusBroadcastReceiver;
    private BroadcastReceiver stopBroadcastReceiver;
    private BroadcastReceiver pauseBroadcastReceiver;
    private final IBinder binder = new AudioServiceBinder();
    private String audioURL;
    private String audioTitle;
    private boolean isPrepared = false;
    private Class<?> classActivity;

    public static final String AUDIO_SERVICE_UPDATE_BROADCAST = "AUDIO_SERVICE_UPDATE_BROADCAST";
    public static final String AUDIO_SERVICE_FORCEUPDATE_BROADCAST = "AUDIO_SERVICE_FORCEUPDATE_BROADCAST";
    public static final String AUDIO_SERVICE_COMMAND_STOP_BROADCAST = "AUDIO_SERVICE_COMMAND_STOP_BROADCAST";
    public static final String AUDIO_SERVICE_COMMAND_PAUSE_BROADCAST = "AUDIO_SERVICE_COMMAND_PAUSE_BROADCAST";

    public class AudioServiceBinder extends Binder {

        public AudioService getService() {
            return AudioService.this;
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private boolean setupAudioPlayer() {
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if(result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopSelf();
            return false;
        }

        final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());
        statusBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                Intent bIntent = new Intent(AUDIO_SERVICE_UPDATE_BROADCAST);

                switch (playStatus) {
                    case PLAY_STATUS_PLAYING:
                        bIntent.putExtra("isPlaying", true);
                        break;
                    case PLAY_STATUS_PAUSED:
                        bIntent.putExtra("isPaused", true);
                        break;
                    case PLAY_STATUS_STOPPED:
                        bIntent.putExtra("isStopped", true);
                        break;
                }

                if(audioTitle != null) {
                    bIntent.putExtra("title", audioTitle);
                }

                manager.sendBroadcast(bIntent);

            }
        };

        manager.registerReceiver(statusBroadcastReceiver, new IntentFilter(AUDIO_SERVICE_FORCEUPDATE_BROADCAST));

        stopBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if(player != null && isPrepared) {
                    player.stop();
                }
                audioManager.abandonAudioFocus(AudioService.this);
                isPrepared = false;
                stopForeground(true);
                stopSelf();
            }
        };

        manager.registerReceiver(stopBroadcastReceiver, new IntentFilter(AUDIO_SERVICE_COMMAND_STOP_BROADCAST));

        pauseBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(player != null && isPrepared) {
                    if(player.isPlaying()) {
                        player.pause();
                        setForegroundNotification(false);
                    } else {
                        player.start();
                    }
                }
            }
        };

        manager.registerReceiver(pauseBroadcastReceiver, new IntentFilter(AUDIO_SERVICE_COMMAND_PAUSE_BROADCAST));

        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "radio_wifi_lock");
        wifiLock.acquire();

        player.setOnErrorListener(this);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Intent bIntent = new Intent(AUDIO_SERVICE_UPDATE_BROADCAST);
                bIntent.putExtra("isStopped", true);
                if(audioTitle != null) {
                    bIntent.putExtra("title", audioTitle);
                }

                isPrepared = false;
                stopForeground(true);
                stopSelf();
            }
        });

        return true;
    }

    public void setClassActivity(Class<?> classActivity) {
        this.classActivity = classActivity;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(intent != null && intent.getAction() != null) {
            if(intent.getAction().equals("Close")) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if(player != null) {
                if(intent.getAction().equals("Play") && !player.isPlaying()) {
                    player.start();
                    setForegroundNotification(true);
                } else if(intent.getAction().equals("Pause") && player.isPlaying()) {
                    player.pause();
                    setForegroundNotification(false);
                }

                return START_STICKY;
            }

        }

        if(intent != null) {
            this.audioTitle = intent.getStringExtra("title");
            this.audioURL = intent.getStringExtra("url");
        }

        if(this.player == null) {
            setupAudioPlayer();
        }

        if(this.player == null) {
            return START_NOT_STICKY;
        } else {
            this.player.reset();
            try {
                this.isPrepared = false;
                this.player.setDataSource(this.audioURL);
                this.player.prepareAsync();
            } catch (IOException ex) {
                return START_NOT_STICKY;
            }

            return START_STICKY;
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        player.start();
        isPrepared = true;

        //Set as foreground
        setForegroundNotification(true);

        playStatus = PLAY_STATUS.PLAY_STATUS_PLAYING;

        Intent bIntent = new Intent(AUDIO_SERVICE_UPDATE_BROADCAST);
        bIntent.putExtra("isPlaying", true);

        if(audioTitle != null) {
            bIntent.putExtra("title", audioTitle);
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(bIntent);
    }

    private void setForegroundNotification(boolean play) {

        Log.d("LARCHO", "Setting foreground " + this.classActivity);

        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), this.classActivity),
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), TXAudioStreamingModule.NOTIFICATION_CHANNEL_ID);

        if(this.audioTitle != null) {
            builder.setTicker(this.audioTitle);
            builder.setContentText(this.audioTitle);
        }

        //builder.setSmallIcon(getNotificationIcon());
        builder.setContentIntent(pi);
        builder.setOngoing(true);
        builder.setAutoCancel(false);

        if(play) {
            Intent pauseIntent = new Intent(getApplicationContext(), AudioService.class);
            pauseIntent.setAction("Pause");
            PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, pauseIntent, 0);
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pendingIntent);
        } else {
            Intent pauseIntent = new Intent(getApplicationContext(), AudioService.class);
            pauseIntent.setAction("Play");
            PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, pauseIntent, 0);
            builder.addAction(android.R.drawable.ic_media_play, "Reproducir", pendingIntent);
        }

        Intent closeIntent = new Intent(getApplicationContext(), AudioService.class);
        closeIntent.setAction("Close");
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, closeIntent, 0);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", pendingIntent);

        Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(player != null) {
            player.release();
            player = null;
        }
        if(wifiLock != null) {
            wifiLock.release();
        }
        if(statusBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(statusBroadcastReceiver);
        }
        if(stopBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(stopBroadcastReceiver);
        }
        if(pauseBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(pauseBroadcastReceiver);
        }
        Intent bIntent = new Intent(AUDIO_SERVICE_UPDATE_BROADCAST);
        bIntent.putExtra("isStopped", true);

        if(audioTitle != null) {
            bIntent.putExtra("title", audioTitle);
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(bIntent);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch(focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                //ya iguale a null en el release, pero just in case uso el try catch
                try {
                    if(player != null && isPrepared && !player.isPlaying()) {
                        player.start();
                        setForegroundNotification(true);
                        player.setVolume(1.0f, 1.0f);
                        playStatus = PLAY_STATUS.PLAY_STATUS_PLAYING;
                    }
                } catch (IllegalStateException e) { }

                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.abandonAudioFocus(this);
                if(player != null && isPrepared) {
                    try {
                        player.stop();
                    } catch (Exception ex) {}
                }
                stopForeground(true);
                stopSelf();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                if(player != null && isPrepared && player.isPlaying()) {
                    player.pause();
                    setForegroundNotification(false);
                    playStatus = PLAY_STATUS.PLAY_STATUS_PAUSED;
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                if(player != null && isPrepared && player.isPlaying()) {
                    player.setVolume(0.1f, 0.1f);
                    playStatus = PLAY_STATUS.PLAY_STATUS_PLAYING;
                }
                break;
        }

    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);

        Intent bIntent = new Intent(AUDIO_SERVICE_UPDATE_BROADCAST);
        bIntent.putExtra("hasErrors", true);

        if(audioTitle != null) {
            bIntent.putExtra("title", audioTitle);
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(bIntent);

        stopForeground(true);
        stopSelf();
        return true;
    }

}
