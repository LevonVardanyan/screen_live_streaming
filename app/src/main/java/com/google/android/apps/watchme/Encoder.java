package com.google.android.apps.watchme;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.picsart.studio.gifencoder.GifEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Created by levon on 7/31/15.
 */
public class Encoder {
    private static Encoder encoder;

    private MediaCodec mediaCodec;
    private MediaCodec.BufferInfo bufferInfo;

    private int mWidth;
    private int mHeight;

    private long presentationTime = 0;

    public Encoder() {
        prepareEncoder();
    }

    public static Encoder getInstance() {
        if (encoder == null) {
            encoder = new Encoder();
            encoder.mWidth=640;
            encoder.mHeight=800;
        }
        return encoder;
    }

    public ByteBuffer encode(byte[] screenBytes) {

        try {
            IntBuffer intBuf = ByteBuffer.wrap(screenBytes).order(ByteOrder.nativeOrder()).asIntBuffer();
            int[] array = new int[intBuf.remaining()];
            intBuf.get(array);
            byte[] yuvBuffer = new byte[mWidth * mHeight * 3 / 2];
            GifEncoder.convertToYUV21(array, yuvBuffer, mWidth, mHeight);
            int inputByteBufferIndex = mediaCodec.dequeueInputBuffer(1000);
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputByteBufferIndex);
            inputBuffer.put(yuvBuffer);
            mediaCodec.queueInputBuffer(inputByteBufferIndex, 0, inputBuffer.capacity(), presentationTime, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            presentationTime += 10;

            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);
            ByteBuffer outputBuffer = null;
            if (outputBufferIndex >= 0) {
                outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
            }
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            return outputBuffer;

        } catch (Exception e) {
            return  null;
        }
    }

    private void prepareEncoder() {
        bufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 800);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 4096000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mediaCodec.start();
    }

}
