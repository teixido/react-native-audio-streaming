package com.reactlibrary;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.reactlibrary.services.AudioService;

public class TXAudioStreamingModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ServiceConnection {

    private final ReactApplicationContext reactContext;
    private BroadcastReceiver audioStatusBroadcastReceiver;
    private boolean audioIsPlaying = false;
    private String playerTitle;
    private Callback callback;

    public static final String NOTIFICATION_CHANNEL_ID = "channel_01";

    public TXAudioStreamingModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);

        audioStatusBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                if(intent.getBooleanExtra("isPlaying", false) || intent.getBooleanExtra("isPaused", false)) {
                    audioIsPlaying = true;
                    if(TXAudioStreamingModule.this.callback != null) {
                        TXAudioStreamingModule.this.callback.invoke(false);
                        TXAudioStreamingModule.this.callback = null;
                    }
                    playerTitle = intent.getStringExtra("title");
                } else if(intent.getBooleanExtra("isStopped", false)) {
                    audioIsPlaying = false;
                    playerTitle = null;
                } else if(intent.getBooleanExtra("hasErrors", false)) {
                    audioIsPlaying = false;
                    playerTitle = null;
                    if(TXAudioStreamingModule.this.callback != null) {
                        TXAudioStreamingModule.this.callback.invoke("Error al reproducir el audio.");
                        TXAudioStreamingModule.this.callback = null;
                    }
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    "Notificaciones generales",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public String getName() {
        return "TXAudioStreaming";
    }

    @ReactMethod
    public void play(String url, String name, Callback callback) {
        this.callback = callback;
        Intent intent = new Intent(reactContext, AudioService.class);
        intent.putExtra("url", url);
        intent.putExtra("title", name);

        this.reactContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
        this.reactContext.startService(intent);
    }

    @ReactMethod
    public void stop() {
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(new Intent(AudioService.AUDIO_SERVICE_COMMAND_STOP_BROADCAST));
    }

    @ReactMethod
    public void currentPlayerName(Callback callback) {
        if(this.playerTitle != null) {
            callback.invoke(this.playerTitle);
        } else {
            callback.invoke(false);
        }
    }

    @Override
    public void onHostResume() {
        LocalBroadcastManager.getInstance(reactContext).registerReceiver(audioStatusBroadcastReceiver, new IntentFilter(AudioService.AUDIO_SERVICE_UPDATE_BROADCAST));
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(new Intent(AudioService.AUDIO_SERVICE_FORCEUPDATE_BROADCAST));
    }

    @Override
    public void onHostPause() {
        LocalBroadcastManager.getInstance(reactContext).unregisterReceiver(audioStatusBroadcastReceiver);
    }

    @Override
    public void onHostDestroy() {

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        AudioService audioService = ((AudioService.AudioServiceBinder) service).getService();
        audioService.setClassActivity(this.getCurrentActivity().getClass());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}