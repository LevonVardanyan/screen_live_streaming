package com.google.android.apps.watchme;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.view.Surface;

import com.picsart.studio.gifencoder.GifEncoder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MP4Writer {
    private static final String dotMP4 = ".mp4";
    private static MP4Writer instance;
    private String outputDir;
    private String name;
    private int delay;
    private MediaCodec mEncoder;
    private int frameRate = 15;
    private long durationInNanosec;
    private long presentationTime;
    private MediaCodec.BufferInfo mBufferInfo;
    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private int mWidth = 640;
    private int mHeight = 800;
    private boolean mMuxerStarted;


    public MP4Writer() {
    }

    public String getOutputAbsolutePath() {
        return outputDir + "/" + name + dotMP4;
    }


    public MP4Writer initWithParams(){
        if(instance==null)
            instance=new MP4Writer();
        instance.outputDir="";
        instance.name="";
        instance.mWidth=640;
        instance.mHeight=800;
        instance.delay = 0;
        return instance;
    }

    /**
     * paramas order as frame-rate, bitrate
     */
    /*public static MP4Writer initWithParams(ProjectVideoGenerator.VideoOptions options,int...params){
        if(instance==null)
            instance=new MP4Writer();
        instance.outputDir=options.getOutputDir();
        instance.name=options.getOutputName();
        instance.mWidth=options.getWidth();
        instance.mHeight=options.getHeight();
        instance.delay=(int)options.getDelay();
        if(params[0]!=0)instance.frameRate=params[0];

        return instance;
    }*/
    public ByteBuffer encode(byte[] screenBytes) {

        final String gifPath = instance.outputDir + "/" + instance.name + ".gif";

        try {
            prepareEncoder();
            presentationTime = 0;
            IntBuffer intBuf = ByteBuffer.wrap(screenBytes).order(ByteOrder.nativeOrder()).asIntBuffer();
            int[] array = new int[intBuf.remaining()];
            intBuf.get(array);
            byte[] yuvBuffer = new byte[mWidth * mHeight * 3 / 2];
            GifEncoder.convertToYUV21(array, yuvBuffer, mWidth, mHeight);
            int inputByteBufferIndex = mEncoder.dequeueInputBuffer(1000);
            ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputByteBufferIndex);
            inputBuffer.put(yuvBuffer);
            mEncoder.queueInputBuffer(inputByteBufferIndex, 0, inputBuffer.capacity(), presentationTime, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            presentationTime += durationInNanosec;

            return drainEncoder(false);

        } catch (Exception e) {
            return  null;
        } finally {
            // release encoder, muxer
            //releaseEncoder();

        }
    }

    public void addFrameForVideo(Bitmap frame) {

    }

    private void prepareEncoder() {
        mBufferInfo = new MediaCodec.BufferInfo();
        durationInNanosec = (long) ( 10000);
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 4096000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        try {
            mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mEncoder.start();

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    private void releaseEncoder() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mMuxer != null) {
            try {

                mMuxer.stop();
                mMuxer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMuxer = null;
        }
    }

    /**
     * Extracts all pending data from the encoder.
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     */
    private ByteBuffer drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;

        if (endOfStream) {
            mEncoder.signalEndOfInputStream();
        }
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);

                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }
                return encodedData;
        }
    }
}
