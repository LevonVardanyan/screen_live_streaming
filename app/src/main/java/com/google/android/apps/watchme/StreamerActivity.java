/*
 * Copyright (c) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.watchme;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.apps.watchme.util.YouTubeApi;

import java.nio.ByteBuffer;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         StreamerActivity class which previews the camera and streams via StreamerService.
 */
public class StreamerActivity extends Activity {
    private static final String TAG = StreamerActivity.class.getName();
    private static final int START_PROJECTION = 100;
    private MediaProjectionManager projectionManager;
    private ImageReader imageReader;
    private Handler handler;
    private int width = 640;
    private int height = 800;

    //	private byte[] yuvData;
    private byte[] rgbaData;

    private String rtmpUrl;
    private String broadcastId;

    private VirtualDisplay virtualDisplay;

    private MediaProjection mediaProjection;
    private LiveService streamerService;
    private ServiceConnection streamerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(MainActivity.APP_NAME, "onServiceConnected");

            streamerService = ((LiveService.LocalBinder) service).getService();
            LiveStreamDatas.getInstance(StreamerActivity.this).setStreamerService(streamerService);
            startProjection();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.e(MainActivity.APP_NAME, "onServiceDisconnected");

            // This should never happen, because our service runs in the same process.
            streamerService = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.streamer);

        Toast.makeText(StreamerActivity.this, "Please, wait 20 second and your event will be started", Toast.LENGTH_SHORT).show();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        broadcastId = getIntent().getStringExtra(YouTubeApi.BROADCAST_ID_KEY);

        rtmpUrl = getIntent().getStringExtra(YouTubeApi.RTMP_URL_KEY);



        if (!bindService(new Intent(this, LiveService.class), streamerConnection, BIND_AUTO_CREATE | BIND_DEBUG_UNBIND)) {
            Log.e(MainActivity.APP_NAME, "Failed to bind StreamerService!");
        }
        LiveStreamDatas.getInstance(this).setStreamerConnection(streamerConnection);
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                handler = new Handler();
                Looper.loop();
            }
        }.start();

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 4);
        imageReader.setOnImageAvailableListener(new ImageAvailableListener(), handler);
    }

    public void endEvent() {
        Intent data = new Intent();
        data.putExtra(YouTubeApi.BROADCAST_ID_KEY, broadcastId);
        if (getParent() == null) {
            setResult(Activity.RESULT_OK, data);
        } else {
            getParent().setResult(Activity.RESULT_OK, data);
        }
        finish();
    }


    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();

            if (image != null) {
                streamerService.setImageAvailable(true);
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                if (rgbaData == null) {
                    rgbaData = new byte[width * height * 4];
                }

                streamerService.encode(rgbaData.clone());
                buffer.get(rgbaData);

                streamerService.encode(rgbaData.clone());

                image.close();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == START_PROJECTION) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            LiveStreamDatas.getInstance(this).setMediaProjection(mediaProjection);
            virtualDisplay = mediaProjection.createVirtualDisplay("asd", width, height, getResources().getDisplayMetrics().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageReader.getSurface(), null, handler);

            streamerService.startStreaming(rtmpUrl, width, height);
            finish();
        } else {
            endEvent();
        }
    }


    private void startProjection() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), START_PROJECTION);
    }
}
