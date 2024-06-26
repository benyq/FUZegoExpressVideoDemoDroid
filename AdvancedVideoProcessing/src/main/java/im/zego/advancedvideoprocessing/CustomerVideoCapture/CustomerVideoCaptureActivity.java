package im.zego.advancedvideoprocessing.CustomerVideoCapture;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import im.zego.advancedvideoprocessing.CustomerVideoCapture.faceunity.FuCaptureRenderActivity;
import im.zego.advancedvideoprocessing.R;
import im.zego.commontools.logtools.AppLogger;
import im.zego.commontools.logtools.LogView;
import im.zego.commontools.logtools.logLinearLayout;
import im.zego.commontools.uitools.ZegoViewUtil;
import im.zego.keycenter.KeyCenter;
import im.zego.keycenter.UserIDHelper;
import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoApiCalledEventHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoUser;

public class CustomerVideoCaptureActivity extends AppCompatActivity {

    ZegoExpressEngine engine;
    long appID;
    String userID;
    String appSign;
    String roomID;
    String publishStreamID;
    ZegoUser user;
    ZegoCustomVideoCaptureConfig captureConfig = new ZegoCustomVideoCaptureConfig();

    TextView roomIDText;
    TextView userIDText;
    Button startPublishButton;
    TextureView preview;
    EditText editPublishStreamID;
    RadioGroup sourceButton;
    RadioButton cameraButton;
    RadioButton imageButton;
    Spinner bufferTypeSpinner;
    RelativeLayout relativeLayout;
    TextView roomState;

    // To store whether the user is publishing the stream
    boolean isPublish = false;
    // To store whether the data source is camera.
    boolean isCamera = true;
    ZegoVideoCaptureCallback videoCapture = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_video_capture);

        requestPermission();
        bindView();
        getAppIDAndUserIDAndAppSign();
        setDefaultValue();
        initEngineAndUser();
        setEventHandler();
        setStartPublishButtonEvent();
        setSourceButton();
        setBufferTypeSpinner();
        setLogComponent();
        setApiCalledResult();
    }

    public void requestPermission() {
        String[] PERMISSIONS_STORAGE = {
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO"};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(PERMISSIONS_STORAGE, 101);
            }
        }
    }

    public void bindView() {
        roomIDText = findViewById(R.id.roomID);
        userIDText = findViewById(R.id.userID);
        startPublishButton = findViewById(R.id.publishButton);
        preview = findViewById(R.id.PreviewView);
        editPublishStreamID = findViewById(R.id.editStreamID);
        sourceButton = findViewById(R.id.SourceBut);
        bufferTypeSpinner = findViewById(R.id.BufferTypeBut);
        cameraButton = findViewById(R.id.CameraBut);
        imageButton = findViewById(R.id.ImageBut);
        relativeLayout = findViewById(R.id.relative_view);
        roomState = findViewById(R.id.roomState);
    }

    //get appID and userID and appSign
    public void getAppIDAndUserIDAndAppSign() {
        appID = KeyCenter.getInstance().getAppID();
        userID = UserIDHelper.getInstance().getUserID();
        appSign = KeyCenter.getInstance().getAppSign();
    }

    public void setDefaultValue() {
        //set default publish StreamID
        publishStreamID = "0014";
        roomID = "0014";

        // set UI
        userIDText.setText(userID);
        sourceButton.check(cameraButton.getId());
        captureConfig.bufferType = ZegoVideoBufferType.RAW_DATA;
        setTitle(getString(R.string.custom_video_capture));
    }

    public void initEngineAndUser() {
        // Initialize ZegoExpressEngine
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
        //create the user
        user = new ZegoUser(userID);
    }

    public void setStartPublishButtonEvent() {
        startPublishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publishStreamID = editPublishStreamID.getText().toString();
                //if the user is publishing the stream, this button is used to stop publishing. Otherwise, this button is used to start publishing.
                if (isPublish) {
                    stopPublish();
                    AppLogger.getInstance().callApi("Stop Publishing Stream:%s", publishStreamID);
                } else {
                    startPublish();
                    startPublishButton.setText(getString(R.string.stop_publishing));
                    AppLogger.getInstance().callApi("Start Publishing Stream:%s", publishStreamID);
                }
            }
        });
    }

    public void setSourceButton() {
        sourceButton.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == cameraButton.getId()) {
                    isCamera = true;
                    AppLogger.getInstance().callApi("Change Source: Camera");
                } else {
                    isCamera = false;
                    AppLogger.getInstance().callApi("Change Source: Image");
                }
                // If the user change the setting, stop the publishing.
                if (isPublish) {
                    stopPublish();
                }
            }
        });
    }

    public void setBufferTypeSpinner() {
        bufferTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String[] ViewOptions = getApplicationContext().getResources().getStringArray(R.array.CaptureBufferType);
                switch (ViewOptions[position]) {
                    case "Raw_Data":
                        captureConfig.bufferType = ZegoVideoBufferType.RAW_DATA;
                        AppLogger.getInstance().callApi("Change BufferType: RAW_DATA");
                        break;
                    case "GL_Texture_2D":
                        captureConfig.bufferType = ZegoVideoBufferType.GL_TEXTURE_2D;
                        AppLogger.getInstance().callApi("Change BufferType: GL_TEXTURE_2D");
                        break;
                    case "Encoded_data":
                        captureConfig.bufferType = ZegoVideoBufferType.ENCODED_DATA;
                        AppLogger.getInstance().callApi("Change BufferType: ENCODED_DATA");
                        break;
                }
                // If the user change the setting, stop the publishing.
                if (isPublish) {
                    stopPublish();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void startPublish() {
        videoCapture = null;
        // Enable the custom video capture and set capture configuration.
        engine.enableCustomVideoCapture(true, captureConfig, ZegoPublishChannel.MAIN);
        if (isCamera) {
            if (captureConfig.bufferType == ZegoVideoBufferType.RAW_DATA) {
                videoCapture = new VideoCaptureFromCamera();
                videoCapture.setView(preview);
            } else if (captureConfig.bufferType == ZegoVideoBufferType.ENCODED_DATA) {
                videoCapture = new VideoCaptureFromCamera3(CustomerVideoCaptureActivity.this);
                videoCapture.setView(preview);
            } else {
                videoCapture = new VideoCaptureFromCamera2();
                videoCapture.setView(preview);
            }
        } else {
            if (captureConfig.bufferType == ZegoVideoBufferType.GL_TEXTURE_2D) {

                videoCapture = new VideoCaptureFromImage2(CustomerVideoCaptureActivity.this, engine);
                videoCapture.setView(preview);
            } else if (captureConfig.bufferType == ZegoVideoBufferType.RAW_DATA) {
                videoCapture = new VideoCaptureFromImage(CustomerVideoCaptureActivity.this, engine);
                videoCapture.setView(preview);
            } else {
                videoCapture = new VideoCaptureFromImage3(CustomerVideoCaptureActivity.this, engine);
                videoCapture.setView(preview);
            }
        }
        engine.setCustomVideoCaptureHandler(videoCapture);
        engine.loginRoom(roomID, new ZegoUser(userID));
        AppLogger.getInstance().callApi("LoginRoom: %s", roomID);

        engine.startPublishingStream(publishStreamID);
        isPublish = true;
    }

    public void stopPublish() {
        startPublishButton.setText(getString(R.string.start_publishing));
        engine.stopPreview();
        engine.stopPublishingStream();

        // The user should enable custom video and set related configuration before logging in the room.
        // When user stop publishing, it will logout room for further operations. For instance, disable the custom capture or
        // reset capture configuration.
        engine.logoutRoom(roomID);
        AppLogger.getInstance().callApi("LogoutRoom: %s", roomID);
        engine.enableCustomVideoCapture(false, captureConfig, ZegoPublishChannel.MAIN);
        // Remove the view to initialize the preview.
        relativeLayout.removeView(preview);
        relativeLayout.addView(preview);
        isPublish = false;
    }

    public static void actionStart(Activity activity) {
        Intent intent = new Intent(activity, FuCaptureRenderActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        // Log out of the room and release the ZEGO SDK
        if (isPublish) {
            stopPublish();
        }
        ZegoExpressEngine.destroyEngine(null);
        super.onDestroy();
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
                        startPublishButton.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.crossEmoji) + getString(R.string.stop_publishing));
                    }
                } else {
                    if (isPublish) {
                        startPublishButton.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.checkEmoji) + getString(R.string.stop_publishing));
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
}