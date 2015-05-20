package com.google.android.apps.watchme;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

public class LiveService extends IntentService {
    private VideoStreamingConnection videoStreamingConnection;
    private final IBinder binder = new LocalBinder();
    private byte[] sameData;
    private final Object lock = new Object();
    private boolean imageAvailable = false;
    private Handler mHandler;
    private byte[] rgbaData;

    public LiveService() {
        this("name");
    }

    public LiveService(String name) {
        super(name);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mHandler = new Handler();
        mStatusChecker.run();
        if (videoStreamingConnection == null) {
            videoStreamingConnection = new VideoStreamingConnection();
            LiveStreamDatas.getInstance(this).setVideoStreamingConnection(videoStreamingConnection);
        }
        return binder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            if (videoStreamingConnection != null) {
                    if (sameData != null) {
                        videoStreamingConnection.sendVideoFrame(sameData.clone());
                    }
            }
            mHandler.postDelayed(mStatusChecker, 0);

        }
    };

    public boolean isImageAvailable() {
        return imageAvailable;
    }

    public void setImageAvailable(boolean imageAvailable) {
        this.imageAvailable = imageAvailable;
    }

    public void startStreaming(String rtmpUrl, int width, int height) {
        videoStreamingConnection.open(rtmpUrl, width, height);
    }

    public void encode(byte[] rgbaData) {
        sameData = rgbaData.clone();
            videoStreamingConnection.sendVideoFrame(rgbaData);
        setImageAvailable(false);
    }

    public class LocalBinder extends Binder {
        LiveService getService() {
            return LiveService.this;
        }
    }
}
