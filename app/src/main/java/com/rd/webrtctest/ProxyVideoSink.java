package com.rd.webrtctest;

import android.os.Environment;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.File;
import java.io.IOException;

/**
 * Created by dds on 2019/4/4.
 * android_shuai@163.com
 */
public class ProxyVideoSink implements VideoSink {
    private static final String TAG = "dds_ProxyVideoSink";
    private VideoSink target;
    private VideoFileRenderer videoFileRenderer;

    public ProxyVideoSink(@Nullable EglBase.Context sharedContext) {
        try {
            File file = new File(Environment.getExternalStorageDirectory()+"/webrtc/webrtc.mp4");
            if(file.exists()){
                file.delete();
            }
            file.createNewFile();
            this.videoFileRenderer = new VideoFileRenderer(file.getAbsolutePath(), 720, 1280, sharedContext);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    synchronized public void onFrame(VideoFrame frame) {
        if (target == null) {
            Logging.d(TAG, "Dropping frame in proxy because target is null.");
            return;
        }
        target.onFrame(frame);
        if (videoFileRenderer != null) {
            videoFileRenderer.onFrame(frame);
        }
    }

    synchronized public void setTarget(VideoSink target) {
        this.target = target;
    }

}