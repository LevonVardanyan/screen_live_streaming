package com.google.android.apps.watchme;

import android.content.Context;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;

public class LiveStreamDatas {
    private static LiveStreamDatas liveStreamDatas;
    private Context context;
    private MediaProjection mediaProjection;
    private LiveService streamerService;
    private VideoStreamingConnection videoStreamingConnection;
    private ServiceConnection streamerConnection;

    public static LiveStreamDatas getInstance(Context context) {
        if (liveStreamDatas == null) {
            liveStreamDatas = new LiveStreamDatas(context);
        }
        return liveStreamDatas;
    }

    private LiveStreamDatas() {}

    private LiveStreamDatas(Context context) {
        this.context = context;
    }

    public MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public LiveService getStreamerService() {
        return streamerService;
    }

    public void setStreamerService(LiveService streamerService) {
        this.streamerService = streamerService;
    }

    public VideoStreamingConnection getVideoStreamingConnection() {
        return videoStreamingConnection;
    }

    public void setVideoStreamingConnection(VideoStreamingConnection videoStreamingConnection) {
        this.videoStreamingConnection = videoStreamingConnection;
    }

    public ServiceConnection getStreamerConnection() {
        return streamerConnection;
    }

    public void setStreamerConnection(ServiceConnection streamerConnection) {
        this.streamerConnection = streamerConnection;
    }
}
