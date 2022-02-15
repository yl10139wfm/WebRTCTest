package com.rd.webrtctest;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

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
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoEncoderSupportedCallback;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
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
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private VideoCapturer captureAndroid;
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
            localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
            localAudioTrack.setEnabled(true);

            peerConnection.addTrack(localAudioTrack);
            //是否显示摄像头画面
            if (isShowCamera) {
                captureAndroid = CameraUtil.createVideoCapture(context);
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());

                videoSource = peerConnectionFactory.createVideoSource(false);

                captureAndroid.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
                captureAndroid.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);

                localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
                localVideoTrack.setEnabled(true);
                if (surfaceViewRenderer != null) {
                    ProxyVideoSink videoSink = new ProxyVideoSink();
                    videoSink.setTarget(surfaceViewRenderer);
                    localVideoTrack.addSink(videoSink);
                }
                peerConnection.addTrack(localVideoTrack);
            }
        }
        peerConnection.createOffer(this, mediaConstraints);
    }

    public void destroy() {
        if (callBack != null) {
            callBack = null;
        }
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (captureAndroid != null) {
            captureAndroid.dispose();
            captureAndroid = null;
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
    public static final String API = "http://%s:1985/rtc/play";

    public void openWebRtc(String sdp) {
        PlayBodyBean playBodyBean = new PlayBodyBean();
        //解析ip
        String serverIp = getIps(playUrl).get(0);
        String api = String.format(API, serverIp);
        if (isPublish) {
            api = api.replace("play", "publish");
        }
        playBodyBean.setApi(api);
        playBodyBean.setClientip(getIpAddressString());
        playBodyBean.setStreamurl(playUrl);
        playBodyBean.setSdp(sdp);
        String body = GsonUtil.toJson(playBodyBean);
        Log.i(TAG, "openWebRtc: api = " + playBodyBean.getApi());
        Log.i(TAG, "openWebRtc: clientIp = " + playBodyBean.getClientip());
        Log.i(TAG, "openWebRtc: streamurl = " + playBodyBean.getStreamurl());
        RxHttp.postBody(api)
                .setBody(body, MediaType.parse("json:application/json;charset=utf-8"))
                .asString()
                .subscribe(s -> {
                    s = s.replaceAll("\n", "");
                    Log.i(TAG, "openWebRtc: result = " + s);
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
                    //openWebRtc(sdp);
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
        if (peerConnection != null) {
            SessionDescription remoteSpd = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
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

    }

    @Override
    public void onCreateFailure(String error) {

    }

    @Override
    public void onSetFailure(String error) {

    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {

    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {

    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        peerConnection.addIceCandidate(candidate);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        peerConnection.removeIceCandidates(candidates);
    }

    @Override
    public void onAddStream(MediaStream stream) {

    }

    @Override
    public void onRemoveStream(MediaStream stream) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {

    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        MediaStreamTrack track = receiver.track();
        if (track instanceof VideoTrack) {
            VideoTrack remoteVideoTrack = (VideoTrack) track;
            remoteVideoTrack.setEnabled(true);
            if (surfaceViewRenderer != null && isShowCamera) {
                ProxyVideoSink videoSink = new ProxyVideoSink();
                videoSink.setTarget(surfaceViewRenderer);
                remoteVideoTrack.addSink(videoSink);
            }
        }
    }
}
