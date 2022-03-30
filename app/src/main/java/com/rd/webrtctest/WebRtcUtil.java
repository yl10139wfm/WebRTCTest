package com.rd.webrtctest;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.DefaultVideoEncoderFactoryExtKt;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoEncoderSupportedCallback;
import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import rxhttp.RxHttp;
import rxhttp.wrapper.utils.GsonUtil;


/**
 * @author haimian on 2021/4/24 0024
 */
public class WebRtcUtil implements PeerConnection.Observer, SdpObserver {

    private static final String TAG = "WebRtcUtil";

    private Context context;

    public WebRtcUtil(Context context) {
        this.context = context.getApplicationContext();
    }

    private EglBase eglBase;

    private String playUrl;

    private PeerConnection peerConnection;
    private SurfaceViewRenderer surfaceViewRenderer;
    private PeerConnectionFactory peerConnectionFactory;

    private AudioSource audioSource;
    private VideoSource videoSource;
    private AudioTrack audioTrack;
    private VideoTrack videoTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private boolean isShowCamera = false;
    private static final int VIDEO_RESOLUTION_WIDTH = 1280;
    private static final int VIDEO_RESOLUTION_HEIGHT = 720;
    private static final int FPS = 30;
    /**
     * isPublish true为推流 false为拉流
     */
    private boolean isPublish;

    public void create(EglBase eglBase, SurfaceViewRenderer surfaceViewRenderer, String playUrl, WebRtcCallBack callBack) {
        create(eglBase, surfaceViewRenderer, false, playUrl, callBack);
    }

    public void create(EglBase eglBase, SurfaceViewRenderer surfaceViewRenderer, boolean isPublish, String playUrl, WebRtcCallBack callBack) {
        this.eglBase = eglBase;
        this.surfaceViewRenderer = surfaceViewRenderer;
        this.callBack = callBack;
        this.playUrl = playUrl;
        this.isPublish = isPublish;

        init();
    }

    public void create(EglBase eglBase, SurfaceViewRenderer surfaceViewRenderer, boolean isPublish, boolean isShowCamera, String playUrl, WebRtcCallBack callBack) {
        this.eglBase = eglBase;
        this.surfaceViewRenderer = surfaceViewRenderer;
        this.callBack = callBack;
        this.playUrl = playUrl;
        this.isPublish = isPublish;
        this.isShowCamera = isShowCamera;

        init();
    }

    private void init() {
        peerConnectionFactory = getPeerConnectionFactory(context);
        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_NONE);

        peerConnection = peerConnectionFactory.createPeerConnection(getConfig(), this);
        MediaConstraints mediaConstraints = new MediaConstraints();

        if (!isPublish) {
            //设置仅接收音视频
            peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
            peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
        } else {
            //设置仅推送音视频
            peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));
            peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));

            //设置回声去噪
            WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
            WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);

            // 音频
            audioSource = peerConnectionFactory.createAudioSource(createAudioConstraints());
            audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
            audioTrack.setEnabled(true);

            peerConnection.addTrack(audioTrack);
            //是否显示摄像头画面
            if (isShowCamera) {
                videoCapturer = CameraUtil.createVideoCapture(context);
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
                videoSource = peerConnectionFactory.createVideoSource(false);
                //videoSource.setVideoProcessor(videoProcessor);
                videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
                videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);
                videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
                videoTrack.setEnabled(true);
                if (surfaceViewRenderer != null) {
                    ProxyVideoSink videoSink = new ProxyVideoSink(this.eglBase.getEglBaseContext());
                    videoSink.setTarget(surfaceViewRenderer);
                    videoTrack.addSink(videoSink);
                }
                peerConnection.addTrack(videoTrack);
            }
        }
        peerConnection.createOffer(this, mediaConstraints);
    }

    private H264Encoder h264Encoder;

    private final VideoProcessor videoProcessor = new VideoProcessor() {
        @Override
        public void setSink(@Nullable VideoSink videoSink) {
            Log.i(TAG, "setSink: ");
        }

        @Override
        public void onCapturerStarted(boolean b) {
            Log.i(TAG, "onCapturerStarted: ");
            h264Encoder = new H264Encoder(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS, VIDEO_RESOLUTION_WIDTH * VIDEO_RESOLUTION_HEIGHT * 5);
            h264Encoder.StartEncoderThread();
        }

        @Override
        public void onCapturerStopped() {
            Log.i(TAG, "onCapturerStopped: ");
            h264Encoder.StopThread();
            h264Encoder = null;
        }

        @Override
        public void onFrameCaptured(VideoFrame videoFrame) {
            Log.i(TAG, "onFrameCaptured: w = " + videoFrame.getBuffer().getWidth() + ", h = " + videoFrame.getBuffer().getHeight());
            if (H264Encoder.YUVQueue.size() >= 10) {
                H264Encoder.YUVQueue.poll();
            }
            // YUV420 大小总是 width * height * 3 / 2

            byte[] mYuvBytes = new byte[videoFrame.getRotatedWidth() * videoFrame.getRotatedHeight() * 3 / 2];
            // YUV_420_888

            // Y通道
            ByteBuffer yBuffer = videoFrame.getBuffer().toI420().getDataY();
            int yLen = VIDEO_RESOLUTION_WIDTH * VIDEO_RESOLUTION_HEIGHT;
            yBuffer.get(mYuvBytes, 0, yLen);
            // U通道
            ByteBuffer uBuffer = videoFrame.getBuffer().toI420().getDataU();
            int pixelStride = videoFrame.getBuffer().toI420().getStrideU(); // pixelStride = 2
            for (int i = 0; i < uBuffer.remaining(); i += pixelStride) {
                mYuvBytes[yLen++] = uBuffer.get(i);
            }
            // V通道
            ByteBuffer vBuffer = videoFrame.getBuffer().toI420().getDataV();
            pixelStride = videoFrame.getBuffer().toI420().getStrideV(); // pixelStride = 2
            for (int i = 0; i < vBuffer.remaining(); i += pixelStride) {
                mYuvBytes[yLen++] = vBuffer.get(i);
            }
            H264Encoder.YUVQueue.add(mYuvBytes);
        }
    };

    public void destroy() {
        if (videoCapturer != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (callBack != null) {
            callBack = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        if (surfaceViewRenderer != null) {
            surfaceViewRenderer.clearImage();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
    }

    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    /**
     * 配置音频参数
     *
     * @return
     */
    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        return audioConstraints;
    }


    private int reConnCount;
    private final int MAX_CONN_COUNT = 10;
    public static final String API = "https://%s:1985/rtc/v1/play/";
    public static final String STREAM_URL = "webrtc://%s/live/livestream";

    public void openWebRtc(String sdp) {
        PlayBodyBean playBodyBean = new PlayBodyBean();
        //解析ip

        System.out.println("sdp::"+sdp);
        System.out.println("playUrl::"+playUrl);

        String serverIp = getIps(playUrl).get(0);
//        String streamUrl = String.format(STREAM_URL, serverIp);
        String streamUrl = String.format(playUrl);
        String api = String.format(API, serverIp);
        if (isPublish) {
            api = api.replace("play", "publish");
        }


        playBodyBean.setApi(api);
        playBodyBean.setClientip(getIpAddressString());
        playBodyBean.setStreamurl(streamUrl);
        playBodyBean.setSdp(sdp);
        String body = GsonUtil.toJson(playBodyBean);
        Log.i(TAG, "openWebRtc: api = " + playBodyBean.getApi());
        Log.i(TAG, "openWebRtc: clientIp = " + playBodyBean.getClientip());
        Log.i(TAG, "openWebRtc: streamurl = " + playBodyBean.getStreamurl());
        Log.i(TAG, "openWebRtc: body = " + body);
        RxHttp.postBody(api)
//                .setBody(body, MediaType.parse("json:application/json;charset=utf-8"))
                .setBody(body, MediaType.parse("Content-Type:application/json;charset=utf-8"))
                .asString()
                .subscribe(s -> {
                    s = s.replaceAll("\n", "");
                    Log.i(TAG, "openWebRtc: result11 = " + s);
                    if (!TextUtils.isEmpty(s)) {
                        SdpBean sdpBean = new Gson().fromJson(s, SdpBean.class);
                        if (sdpBean.getCode() == 400) {
                            openWebRtc(sdp);
                            return;
                        }
                        if (!TextUtils.isEmpty(sdpBean.getSdp())) {
                            setRemoteSdp(sdpBean.getSdp());
                        }
                    }
                }, throwable -> {
                    Log.e(TAG, "openWebRtc: throwable " + throwable.getMessage());
//                    openWebRtc(sdp);
                });
    }

    public static String getIpAddressString() {
        try {
            for (Enumeration<NetworkInterface> enNetI = NetworkInterface
                    .getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
                NetworkInterface netI = enNetI.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = netI
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "0.0.0.0";
    }

    /**
     * 正则提前字符串中的IP地址
     *
     * @param ipString
     * @return
     */
    public static List<String> getIps(String ipString) {
        String regEx = "((2[0-4]\\d|25[0-5]|[01]?\\d\\d?)\\.){3}(2[0-4]\\d|25[0-5]|[01]?\\d\\d?)";
        List<String> ips = new ArrayList<String>();
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(ipString);
        while (m.find()) {
            String result = m.group();
            ips.add(result);
        }
        return ips;
    }

    public void setRemoteSdp(String sdp) {
        Log.i(TAG, "setRemoteSdp: ");
        if (peerConnection != null) {
            SessionDescription remoteSpd = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            Log.i(TAG, "setRemoteDescription: ");
            peerConnection.setRemoteDescription(this, remoteSpd);
        }
    }

    public interface WebRtcCallBack {

        void onSuccess();

        void onFail();
    }

    private WebRtcCallBack callBack;

    /**
     * 获取 PeerConnectionFactory
     */
    private PeerConnectionFactory getPeerConnectionFactory(Context context) {
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions();

        PeerConnectionFactory.initialize(initializationOptions);

        // 2. 设置编解码方式：默认方法
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(),
                false,
                true);

        //use java
        DefaultVideoEncoderFactory encoderFactorySupportH264 = DefaultVideoEncoderFactoryExtKt.createCustomVideoEncoderFactory(eglBase.getEglBaseContext(),
                true,
                true,
                new VideoEncoderSupportedCallback() {
                    @Override
                    public boolean isSupportedH264(@NonNull MediaCodecInfo info) {
                        return true;
                    }

                    @Override
                    public boolean isSupportedVp8(@NonNull MediaCodecInfo info) {
                        return false;
                    }

                    @Override
                    public boolean isSupportedVp9(@NonNull MediaCodecInfo info) {
                        return false;
                    }
                });


        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        // 构造Factory
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions());

        return PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
                .setVideoEncoderFactory(encoderFactorySupportH264)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    private PeerConnection.RTCConfiguration getConfig() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(new ArrayList<>());
        //关闭分辨率变换
        rtcConfig.enableCpuOveruseDetection = false;
        //修改模式 PlanB无法使用仅接收音视频的配置
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        return rtcConfig;
    }

    @Override
    public void onCreateSuccess(SessionDescription sdp) {
        if (sdp.type == SessionDescription.Type.OFFER) {
            //设置setLocalDescription offer返回sdp
            peerConnection.setLocalDescription(this, sdp);
            if (!TextUtils.isEmpty(sdp.description)) {
                reConnCount = 0;
                openWebRtc(sdp.description);
            }
        }
    }

    @Override
    public void onSetSuccess() {
        Log.i(TAG, "onSetSuccess: ");
    }

    @Override
    public void onCreateFailure(String error) {
        Log.i(TAG, "onCreateFailure: ");
    }

    @Override
    public void onSetFailure(String error) {
        Log.i(TAG, "onSetFailure: ");
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
        Log.i(TAG, "onSignalingChange: ");
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
        Log.i(TAG, "onIceConnectionChange: ");
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.i(TAG, "onIceConnectionReceivingChange: ");
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        Log.i(TAG, "onIceGatheringChange: ");
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        peerConnection.addIceCandidate(candidate);
        Log.i(TAG, "onIceCandidate: ");
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        peerConnection.removeIceCandidates(candidates);
        Log.i(TAG, "onIceCandidatesRemoved: ");
    }

    @Override
    public void onAddStream(MediaStream stream) {
        Log.i(TAG, "onAddStream: ");
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
        Log.i(TAG, "onRemoveStream: ");
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.i(TAG, "onDataChannel: ");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.i(TAG, "onRenegotiationNeeded: ");
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        MediaStreamTrack track = receiver.track();
        if (track instanceof VideoTrack) {
            VideoTrack remoteVideoTrack = (VideoTrack) track;
            remoteVideoTrack.setEnabled(true);
            if (surfaceViewRenderer != null && isShowCamera) {
                ProxyVideoSink videoSink = new ProxyVideoSink(this.eglBase.getEglBaseContext());
                videoSink.setTarget(surfaceViewRenderer);
                remoteVideoTrack.addSink(videoSink);
            }
        }
    }
}
