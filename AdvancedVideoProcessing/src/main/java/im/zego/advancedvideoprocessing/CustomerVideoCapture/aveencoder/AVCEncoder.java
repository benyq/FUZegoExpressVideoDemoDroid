package im.zego.advancedvideoprocessing.CustomerVideoCapture.aveencoder;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * AVCEncoder
 * AVCANNEXB Encoder
 * This class is to encode the collected video data
 * By referring to this class, developers may implement video encoder.
 * AVCANNEXB 模式编码器
 * 此类的作用是编码采集的视频数据
 * 开发者可参考该类的代码实现编码视频数据
 */
@TargetApi(23)
public class AVCEncoder {

    private final static String TAG = "AVCEncoder";
    private final static int CONFIGURE_FLAG_ENCODE = MediaCodec.CONFIGURE_FLAG_ENCODE;

    // Audio and video codec components
    // 音视频编解码器组件
    private MediaCodec mMediaCodec;
    // Media format information
    // 媒体数据格式信息
    private MediaFormat mMediaFormat;
    // Width of view to be encoded
    // 待编码视图宽
    private int         mViewWidth;
    // Height of view to be encoded
    // 待编码视图高
    private int         mViewHeight;

    private ByteBuffer configData = ByteBuffer.allocateDirect(1);

    /**
     * Video data information structure
     * Contains timestamp, video data and key frame markers
     * 视频数据信息结构体
     * 包含时间戳，视频数据，关键帧标记
     */
    public static class TransferInfo {
        public long timeStmp;
        public byte[] inOutData;
        public boolean isKeyFrame;
    }

    // Video data queue to be encoded
    // 待编码视频数据队列
    private final static ConcurrentLinkedQueue<TransferInfo> mInputDatasQueue = new ConcurrentLinkedQueue<TransferInfo>();
    //Encoded video data queue
    // 已编码视频数据队列
    private final static ConcurrentLinkedQueue<TransferInfo> mOutputDatasQueue = new ConcurrentLinkedQueue<TransferInfo>();

    // Encoder callback
    // 编码器回调
    private MediaCodec.Callback mCallback = new MediaCodec.Callback() {
        // Input buffer callback, waiting for input
        // 输入缓冲区回调，等待输入
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inputBufferId) {
            try {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                inputBuffer.clear();

                // Get data from the video data queue to be encoded
                // 从待编码视频数据队列取数据
                TransferInfo transferInfo = mInputDatasQueue.poll();

                if (transferInfo != null) {
                    // Write the video frame data into the buffer of MediaCodec
                    // 将视频帧数据写入MediaCodec的buffer中
                    inputBuffer.put(transferInfo.inOutData, 0, transferInfo.inOutData.length);
                    // The video frame data enters the MediaCodec queue and waiting for encoding.
                    // It requires linearly increasing timestamps
                    // 视频帧数据入MediaCodec队列，等待编码，需要传递线性递增的时间戳
                    mediaCodec.queueInputBuffer(inputBufferId,0, transferInfo.inOutData.length,transferInfo.timeStmp*1000,0);
                } else {
                    long now = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        now = SystemClock.elapsedRealtimeNanos();
                    } else {
                        now = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                    }
                    // 入空数据进MediaCodec队列
                    mediaCodec.queueInputBuffer(inputBufferId,0, 0,now*1000,0);
                }
            } catch (IllegalStateException exception) {
                Log.e(TAG, "encoder mediaCodec input exception: " + exception.getMessage());
            } catch (BufferOverflowException exception) {
                Log.e(TAG, "input buffer put exception: " + exception.getMessage());
            } catch (IndexOutOfBoundsException exception) {
                Log.e(TAG, "input buffer put exception: " + exception.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "onInputBufferAvailable exception: " + e.getMessage());
            }
        }

        /**
         * Callback when encoding is complete
         * 编码完成回调
         *
         */
        @Override
            // Get MediaCodec's output buffer address according to the buffer index
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outputBufferId, @NonNull MediaCodec.BufferInfo bufferInfo) {
            // 根据buffer索引获取MediaCodec的输出缓冲区buffer地址
            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferId);
            MediaFormat outputFormat = mMediaCodec.getOutputFormat(outputBufferId);

            // 关键帧buffer
            ByteBuffer keyFrameBuffer;

            if (outputBuffer != null && bufferInfo.size > 0) {
                TransferInfo transferInfo = new TransferInfo();
                transferInfo.timeStmp = bufferInfo.presentationTimeUs / 1000 ;

                // Determine whether it is Codec-specific Data, gett csd parameters from OutputBuffer
                // 判断是否为 Codec-specific Data，从OutputBuffer中获取csd参数
                boolean isConfigFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (isConfigFrame) {
                    Log.d(TAG, "Config frame generated. Offset: " + bufferInfo.offset +
                            ", Size: " + bufferInfo.size + ", num: " + (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG));

                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    if (configData.capacity() < bufferInfo.size) {
                        configData = ByteBuffer.allocateDirect(bufferInfo.size);
                    }
                    // Save Codec-specific Data
                    // 保存Codec-specific Data
                    configData.put(outputBuffer);
                }

                // Determine whether it is a key frame
                // 判断是否为关键帧
                boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                if (isKeyFrame) {

                    Log.d(TAG, "Appending config frame of size " + configData.capacity() +
                            " to output buffer with offset " + bufferInfo.offset + ", size " +
                            bufferInfo.size);
                    // For H.264 key frame append SPS and PPS NALs at the start
                    keyFrameBuffer = ByteBuffer.allocateDirect(
                            configData.capacity() + bufferInfo.size);
                    configData.rewind();
                    // If it is a keyframe, it is necessary to combine Codec-specific Data and keyframe data.
                    // 为关键帧时需要拼接Codec-specific Data和关键帧数据
                    keyFrameBuffer.put(configData);
                    keyFrameBuffer.put(outputBuffer);
                    keyFrameBuffer.position(0);

                    byte[] buffer = new byte[keyFrameBuffer.remaining()];
                    keyFrameBuffer.get(buffer);

                    transferInfo.inOutData = buffer;
                    transferInfo.isKeyFrame = true;

                } else {

                    byte[] buffer = new byte[outputBuffer.remaining()];
                    outputBuffer.get(buffer);

                    transferInfo.inOutData = buffer;
                    transferInfo.isKeyFrame = false;
                }
                // Store the encoded data into the encoded video data queue

                // 将编码后的数据存入已编码视频数据队列
                boolean result = mOutputDatasQueue.offer(transferInfo);

                if (!result) {
                    Log.e(TAG, "encoder offer to queue failed, queue in full state");
                }
            }
            // Processing is complete. Release the ByteBuffer data
            // The rendering function of MediaCodec is not used.
            // 处理完成，释放ByteBuffer数据，此处不用MediaCodec的渲染功能
            mMediaCodec.releaseOutputBuffer(outputBufferId, false);
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, "encoder onError");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            Log.d(TAG, "encoder onOutputFormatChanged, mediaFormat: "+mediaFormat);
        }
    };

    //
    private static MediaCodecInfo selectCodec(String mimeType) {

        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    Log.d(TAG, "selectCodec OK, get " + mimeType);
                    return codecInfo;
                }
            }
        }
        return null;
    }

    // Determine whether the device supports I420 color formats
    // 判断设备是否支持 I420 color formats
    public static boolean isSupportI420() {
        boolean isSupport = false;
        int colorFormat = 0;
        MediaCodecInfo codecInfo = selectCodec("video/avc");
        if (codecInfo != null) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
            for (int i = 0; i < capabilities.colorFormats.length && colorFormat == 0; i++) {
                int format = capabilities.colorFormats[i];
                switch (format) {
                    //support color formats
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:    /*I420 --- YUV4:2:0 --- Nvidia Tegra 3, Samsu */
                        colorFormat = format;
                        break;
                    default:
                        Log.d("Zego", " AVCEncoder unsupported color format " + format);
                        break;
                }
            }
            if (colorFormat != 0) {
                isSupport = true;
            } else {
                isSupport = false;
            }
        }

        return isSupport;
    }

    /**
     * Initialize the encoder
     * 初始化编码器
     *
     * @param viewwidth  渲染展示视图的宽  the width of the rendered view
     * @param viewheight 渲染展示视图的高  the height of the rendered view
     */
    public AVCEncoder(int viewwidth, int viewheight) {

        try {
            // Choose an encoder whose MIME type is AVC to construct MediaCodec
            // 选用MIME类型为AVC的编码器来构造MediaCodec
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            mMediaCodec = null;
            return;
        }

        this.mViewWidth = viewwidth;
        this.mViewHeight = viewheight;

        // Set MediaFormat
        // It is necessary to set KEY_COLOR_FORMAT，KEY_BIT_RATE，KEY_FRAME_RATE，KEY_I_FRAME_INTERVAL
        // 设置MediaFormat，必须设置 KEY_COLOR_FORMAT，KEY_BIT_RATE，KEY_FRAME_RATE，KEY_I_FRAME_INTERVAL的值
        mMediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mViewWidth, mViewHeight);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar); //COLOR_FormatYUV420PackedSemiPlanar
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

    }
    // To provide video frame data to the encoder
    // I420 format data is required

    public void setRotation(int rotation) {
        mMediaFormat.setInteger(MediaFormat.KEY_ROTATION, rotation);
    }

    public void setMediaFormat(MediaFormat mediaFormat) {
        if (mediaFormat != null) {
            mMediaFormat = mediaFormat;
        }
    }

    // 为编码器提供视频帧数据，需要 I420 格式的数据
    public void inputFrameToEncoder(byte[] needEncodeData, long timeStmp) {
        if (needEncodeData == null) {
            return;
        }

        int inputBufferId = mMediaCodec.dequeueInputBuffer(0);
        if (inputBufferId < 0) {
            return;
        }

        try {
            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferId);
            inputBuffer.clear();

            inputBuffer.put(needEncodeData, 0, needEncodeData.length);

            mMediaCodec.queueInputBuffer(inputBufferId,0, needEncodeData.length,timeStmp*1000,0);
        } catch (IllegalStateException exception) {
            Log.e(TAG, "encoder mediaCodec input exception: " + exception.getMessage());
        } catch (BufferOverflowException exception) {
            Log.e(TAG, "input buffer put exception: " + exception.getMessage());
        } catch (IndexOutOfBoundsException exception) {
            Log.e(TAG, "input buffer put exception: " + exception.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "onInputBufferAvailable exception: " + e.getMessage());
        }
    }

    public TransferInfo outputFrameFromEncoder() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferId = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        if (outputBufferId < 0) {
            return null;
        }

        ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferId);
        if (outputBuffer == null || bufferInfo.size == 0) {
            return null;
        }

        TransferInfo transferInfo = new TransferInfo();
        transferInfo.timeStmp = bufferInfo.presentationTimeUs / 1000;

        // Determine whether it is Codec-specific Data, gett csd parameters from OutputBuffer
        // 判断是否为 Codec-specific Data，从OutputBuffer中获取csd参数
        boolean isConfigFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
        if (isConfigFrame) {
            Log.d(TAG, "Config frame generated. Offset: " + bufferInfo.offset +
                    ", Size: " + bufferInfo.size + ", num: " + (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG));

            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
            if (configData.capacity() < bufferInfo.size) {
                configData = ByteBuffer.allocateDirect(bufferInfo.size);
            }
            // Save Codec-specific Data
            // 保存Codec-specific Data
            configData.put(outputBuffer);
        }

        // Determine whether it is a key frame
        // 判断是否为关键帧
        boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
        if (isKeyFrame) {

            Log.d(TAG, "Appending config frame of size " + configData.capacity() +
                    " to output buffer with offset " + bufferInfo.offset + ", size " +
                    bufferInfo.size);
            // For H.264 key frame append SPS and PPS NALs at the start
            ByteBuffer keyFrameBuffer = ByteBuffer.allocateDirect(
                    configData.capacity() + bufferInfo.size);
            configData.rewind();
            // If it is a keyframe, it is necessary to combine Codec-specific Data and keyframe data.
            // 为关键帧时需要拼接Codec-specific Data和关键帧数据
            keyFrameBuffer.put(configData);
            keyFrameBuffer.put(outputBuffer);
            keyFrameBuffer.position(0);

            byte[] buffer = new byte[keyFrameBuffer.remaining()];
            keyFrameBuffer.get(buffer);

            transferInfo.inOutData = buffer;
            transferInfo.isKeyFrame = true;

        } else {

            byte[] buffer = new byte[outputBuffer.remaining()];
            outputBuffer.get(buffer);

            transferInfo.inOutData = buffer;
            transferInfo.isKeyFrame = false;
        }
        mMediaCodec.releaseOutputBuffer(outputBufferId, false);

        return transferInfo;
    }

    /**
     * Get the encoded video frame
     * Return null when the queue is empty
     * 获取编码后的视频帧数据，队列为空时返回null
     */
    public TransferInfo pollFrameFromEncoder() {
        return mOutputDatasQueue.poll();
    }

    // Start Encoder
    // 启动编码器
    public void startEncoder() {
        if (mMediaCodec != null) {
            // 设置编码器的回调监听
//            mMediaCodec.setCallback(mCallback);
            // Configure MediaCodec.
            // Choose to use encoding function.
            // 配置MediaCodec，选择采用编码器功能
            mMediaCodec.configure(mMediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
            // 启动编码器
            mMediaCodec.start();
        } else {
            throw new IllegalArgumentException("startEncoder failed,is the MediaCodec has been init correct?");
        }
    }

    // 停止编码器
    public void stopEncoder() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
        }
    }

    // 释放编码器
    public void releaseEncoder() {
        if (mMediaCodec != null) {
            mInputDatasQueue.clear();
            mOutputDatasQueue.clear();
            mMediaCodec.release();
            mMediaCodec = null;
        }
    }
}
