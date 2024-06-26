package im.zego.advancedvideoprocessing.customrender.ui;

import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import im.zego.advancedvideoprocessing.R;
import im.zego.commontools.logtools.AppLogger;
import im.zego.commontools.logtools.LogView;
import im.zego.commontools.logtools.logLinearLayout;
import im.zego.commontools.uitools.ZegoViewUtil;
import im.zego.commontools.videorender.VideoRenderHandler;
import im.zego.keycenter.KeyCenter;
import im.zego.keycenter.UserIDHelper;
import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoApiCalledEventHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoPlayerState;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.constants.ZegoVideoFrameFormatSeries;
import im.zego.zegoexpress.constants.ZegoViewMode;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoCustomVideoRenderConfig;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoUser;

public class VideoRenderPublish extends AppCompatActivity {

    TextView roomIDText;
    TextView userIDText;
    TextureView customPreview;
    TextureView customPlayView;
    TextView roomState;

    Button startPublish;
    Button startPlay;

    ZegoExpressEngine engine;
    long appID;
    String userID;
    String appSign;
    String roomID;
    String playStreamID;
    String publishStreamID;

    boolean isPublish = true;
    boolean isPlay;
    VideoRenderHandler videoRenderer;
    ZegoCustomVideoRenderConfig renderConfig = new ZegoCustomVideoRenderConfig();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_render_publish);
        getAppIDAndUserIDAndAppSign();
        setRenderConfig();
        setUserConfig();
        setUI();
        setRender();
        initEngineAndLoginRoom();
        setEventHandler();
        setStartPublishButton();
        setStartPlayButton();
        startPublish();
        setLogComponent();
        setApiCalledResult();
    }

    //set render configuration
    public void setRenderConfig() {
        if (!getIntent().getBooleanExtra("isRGB", false)) {
            renderConfig.frameFormatSeries = ZegoVideoFrameFormatSeries.YUV;
        } else {
            renderConfig.frameFormatSeries = ZegoVideoFrameFormatSeries.RGB;
        }
        if (!getIntent().getBooleanExtra("isRawData", false)) {
            renderConfig.bufferType = ZegoVideoBufferType.ENCODED_DATA;
        } else {
            renderConfig.bufferType = ZegoVideoBufferType.RAW_DATA;
        }
        renderConfig.enableEngineRender = true;
    }

    //get appID and userID and appSign
    public void getAppIDAndUserIDAndAppSign() {
        appID = KeyCenter.getInstance().getAppID();
        userID = UserIDHelper.getInstance().getUserID();
        appSign = KeyCenter.getInstance().getAppSign();
    }

    //set user configuration
    public void setUserConfig() {
        playStreamID = getIntent().getStringExtra("playStreamID");
        publishStreamID = getIntent().getStringExtra("publishStreamID");
        roomID = getIntent().getStringExtra("roomID");
    }

    //set UI
    public void setUI() {
        roomIDText = findViewById(R.id.roomID);
        userIDText = findViewById(R.id.userID);
        customPreview = findViewById(R.id.PreviewView);
        customPlayView = findViewById(R.id.PlayView);
        startPublish = findViewById(R.id.publishButton);
        startPlay = findViewById(R.id.playButton);
        setTitle(getString(R.string.custom_video_rendering));
        userIDText.setText(userID);
        roomIDText.setText(roomID);
        roomState = findViewById(R.id.roomState);
    }

    public void initEngineAndLoginRoom() {
        // Initialize the engine
        ZegoEngineProfile profile = new ZegoEngineProfile();
        profile.appID = appID;
        profile.appSign = appSign;

        // Here we use the high quality video call scenario as an example,
        // you should choose the appropriate scenario according to your actual situation,
        // for the differences between scenarios and how to choose a suitable scenario,
        // please refer to https://docs.zegocloud.com/article/14940
        profile.scenario = ZegoScenario.HIGH_QUALITY_VIDEO_CALL;

        profile.application = getApplication();
        engine = ZegoExpressEngine.createEngine(profile, null);

        AppLogger.getInstance().callApi("Create ZegoExpressEngine");
        // Create the user
        ZegoUser user = new ZegoUser(userID);

        // Set the engine
        engine.enableCustomVideoRender(true, renderConfig);
        engine.setCustomVideoRenderHandler(videoRenderer);
        // Log in the room
        engine.loginRoom(roomID, user);
        AppLogger.getInstance().callApi("LoginRoom: %s", roomID);
        engine.enableCamera(true);// Enable the camera
        engine.muteMicrophone(false);// Enable the microphone
        engine.muteSpeaker(false);// Enable the speaker
    }

    public void setRender() {
        //Initialize the renderer
        videoRenderer = new VideoRenderHandler();
        videoRenderer.init();
        // get the view size and pass to renderer
        customPreview.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        videoRenderer.setSize(customPreview.getMeasuredWidth(), customPreview.getMeasuredHeight());
    }

    public void setStartPublishButton() {
        startPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPublish) {
                    startPublish();
                    AppLogger.getInstance().callApi("Start Publishing Stream:%s", publishStreamID);
                    startPublish.setText(getString(R.string.stop_publishing));
                    isPublish = true;
                } else {
                    stopPublish();
                    AppLogger.getInstance().callApi("Stop Publishing Stream:%s", publishStreamID);
                    startPublish.setText(getString(R.string.start_publishing));
                    isPublish = false;
                }
            }
        });
    }

    public void setStartPlayButton() {
        startPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlay) {
                    startPlay();
                    AppLogger.getInstance().callApi("Start Playing Stream:%s", playStreamID);
                    startPlay.setText(getString(R.string.stop_playing));
                    isPlay = true;
                } else {
                    stopPlay();
                    AppLogger.getInstance().callApi("Stop Playing Stream:%s", playStreamID);
                    startPlay.setText(getString(R.string.start_playing));
                    isPlay = false;
                }
            }
        });
    }

    public void startPublish() {
        ZegoCanvas canvas = new ZegoCanvas(customPreview);
        canvas.viewMode = ZegoViewMode.SCALE_TO_FILL;
        if (renderConfig.bufferType == ZegoVideoBufferType.RAW_DATA) {
            engine.startPreview(null);
            videoRenderer.addCaptureView(ZegoPublishChannel.MAIN, customPreview);
        } else {
            engine.startPreview(canvas);
        }
        engine.startPublishingStream(publishStreamID);
    }

    public void stopPublish() {
        engine.stopPreview();
        engine.stopPublishingStream();
        if (renderConfig.bufferType == ZegoVideoBufferType.RAW_DATA) {
            videoRenderer.removeCaptureView(ZegoPublishChannel.MAIN);
        }
    }

    public void startPlay() {
        engine.startPlayingStream(playStreamID, new ZegoCanvas(null));
        if (renderConfig.bufferType == ZegoVideoBufferType.RAW_DATA) {
            videoRenderer.addView(playStreamID, customPlayView);
        } else {
            videoRenderer.addDecodView(customPlayView);
        }
    }

    public void stopPlay() {
        engine.stopPlayingStream(playStreamID);
        videoRenderer.removeView(playStreamID, false);
    }

    public void setEventHandler() {
        engine.setEventHandler(new IZegoEventHandler() {
            // The callback triggered when the room connection state changes.
            @Override
            public void onRoomStateChanged(String roomID, ZegoRoomStateChangedReason reason, int errorCode, JSONObject extendedData) {
                ZegoViewUtil.UpdateRoomState(roomState, reason);
            }

            // The callback triggered when the state of stream publishing changes.
            @Override
            public void onPublisherStateUpdate(String streamID, ZegoPublisherState state, int errorCode, JSONObject extendedData) {
                super.onPublisherStateUpdate(streamID, state, errorCode, extendedData);
                // If the state is PUBLISHER_STATE_NO_PUBLISH and the errcode is not 0, it means that stream publishing has failed
                // and no more retry will be attempted by the engine. At this point, the failure of stream publishing can be indicated
                // on the UI of the App.
                if (errorCode != 0 && state.equals(ZegoPublisherState.NO_PUBLISH)) {
                    if (isPublish) {
                        startPublish.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.crossEmoji) + getString(R.string.stop_publishing));
                    }
                } else {
                    if (isPublish) {
                        startPublish.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.checkEmoji) + getString(R.string.stop_publishing));
                    }
                }
            }

            // The callback triggered when the state of stream playing changes.
            @Override
            public void onPlayerStateUpdate(String streamID, ZegoPlayerState state, int errorCode, JSONObject extendedData) {
                super.onPlayerStateUpdate(streamID, state, errorCode, extendedData);
                // If the state is PLAYER_STATE_NO_PLAY and the errcode is not 0, it means that stream playing has failed and
                // no more retry will be attempted by the engine. At this point, the failure of stream playing can be indicated
                // on the UI of the App.
                if (errorCode != 0 && state.equals(ZegoPlayerState.NO_PLAY)) {
                    if (isPlay) {
                        startPlay.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.crossEmoji) + getString(R.string.stop_playing));
                    }
                } else {
                    if (isPlay) {
                        startPlay.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.checkEmoji) + getString(R.string.stop_playing));
                    }
                }
            }

        });
    }

    public void setApiCalledResult() {
        // Update log with api called results
        ZegoExpressEngine.setApiCalledCallback(new IZegoApiCalledEventHandler() {
            @Override
            public void onApiCalledResult(int errorCode, String funcName, String info) {
                super.onApiCalledResult(errorCode, funcName, info);
                if (errorCode == 0) {
                    AppLogger.getInstance().success("[%s]:%s", funcName, info);
                } else {
                    AppLogger.getInstance().fail("[%d]%s:%s", errorCode, funcName, info);
                }
            }
        });
    }

    // Set log component. It includes a pop-up dialog.
    public void setLogComponent() {
        logLinearLayout logHiddenView = findViewById(R.id.logView);
        logHiddenView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogView logview = new LogView(getApplicationContext());
                logview.show(getSupportFragmentManager(), null);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release the rendering class
        engine.logoutRoom(roomID);
        ZegoExpressEngine.destroyEngine(null);
        videoRenderer.uninit();
    }
}