package org.reactnative.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.google.android.cameraview.CameraView;
import com.google.zxing.MultiFormatReader;

import org.reactnative.camera.tasks.ModelProcessorAsyncTask;
import org.reactnative.camera.tasks.ModelProcessorAsyncTaskDelegate;
import org.reactnative.camera.tasks.PictureSavedDelegate;
import org.reactnative.camera.tasks.ResolveTakenPictureAsyncTask;
import org.reactnative.camera.utils.ImageDimensions;
import org.reactnative.camera.utils.RNFileUtils;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.DataType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


public class RNCameraView extends CameraView implements LifecycleEventListener,
    PictureSavedDelegate, ModelProcessorAsyncTaskDelegate {
  private ThemedReactContext mThemedReactContext;
  private Queue<Promise> mPictureTakenPromises = new ConcurrentLinkedQueue<>();
  private Map<Promise, ReadableMap> mPictureTakenOptions = new ConcurrentHashMap<>();
  private Map<Promise, File> mPictureTakenDirectories = new ConcurrentHashMap<>();
  private Promise mVideoRecordedPromise;
  private Boolean mPlaySoundOnCapture = false;

  private boolean mIsPaused = false;
  private boolean mIsNew = true;
  private Boolean mIsRecording = false;
  private Boolean mIsRecordingInterrupted = false;

  public volatile boolean modelProcessorTaskLock = false;

  // Scanning-related properties
  private MultiFormatReader mMultiFormatReader;
  private String mModelFile;
  private String mLabelFile;
  private final Interpreter.Options options = new Interpreter.Options();
  private Interpreter mModelProcessor;
  private int mModelMaxFreqms;
  private ByteBuffer mModelInput;
  private int[] intValues;
  private int mModelImageDimX;
  private int mModelImageDimY;
  private static final int NUM_THREADS = 1;
  private int mClasses;
  private boolean mShouldProcessModel = false;
  private int mPaddingX;
  private int mPaddingY;

  public RNCameraView(ThemedReactContext themedReactContext) {
    super(themedReactContext, true);
    mThemedReactContext = themedReactContext;
    themedReactContext.addLifecycleEventListener(this);

    addCallback(new Callback() {
      @Override
      public void onCameraOpened(CameraView cameraView) {
        RNCameraViewHelper.emitCameraReadyEvent(cameraView);
      }

      @Override
      public void onMountError(CameraView cameraView) {
        RNCameraViewHelper.emitMountErrorEvent(cameraView, "Camera view threw an error - component could not be rendered.");
      }

      @Override
      public void onPictureTaken(CameraView cameraView, final byte[] data, int deviceOrientation) {
        Promise promise = mPictureTakenPromises.poll();
        ReadableMap options = mPictureTakenOptions.remove(promise);
        if (options.hasKey("fastMode") && options.getBoolean("fastMode")) {
            promise.resolve(null);
        }
        final File cacheDirectory = mPictureTakenDirectories.remove(promise);
        if(Build.VERSION.SDK_INT >= 11/*HONEYCOMB*/) {
          new ResolveTakenPictureAsyncTask(data, promise, options, cacheDirectory, deviceOrientation, RNCameraView.this)
                  .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
          new ResolveTakenPictureAsyncTask(data, promise, options, cacheDirectory, deviceOrientation, RNCameraView.this)
                  .execute();
        }
        RNCameraViewHelper.emitPictureTakenEvent(cameraView);
      }

      @Override
      public void onVideoRecorded(CameraView cameraView, String path, int videoOrientation, int deviceOrientation) {
        if (mVideoRecordedPromise != null) {
          if (path != null) {
            WritableMap result = Arguments.createMap();
            result.putBoolean("isRecordingInterrupted", mIsRecordingInterrupted);
            result.putInt("videoOrientation", videoOrientation);
            result.putInt("deviceOrientation", deviceOrientation);
            result.putString("uri", RNFileUtils.uriFromFile(new File(path)).toString());
            mVideoRecordedPromise.resolve(result);
          } else {
            mVideoRecordedPromise.reject("E_RECORDING", "Couldn't stop recording - there is none in progress");
          }
          mIsRecording = false;
          mIsRecordingInterrupted = false;
          mVideoRecordedPromise = null;
        }
      }

      @Override
      public void onFramePreview(CameraView cameraView, byte[] data, int width, int height, int rotation) {
        int correctRotation = RNCameraViewHelper.getCorrectCameraRotation(rotation, getFacing(), getCameraOrientation());
        boolean willCallModelTask = mShouldProcessModel && !modelProcessorTaskLock && cameraView instanceof ModelProcessorAsyncTaskDelegate;
        if (!willCallModelTask) {
          return;
        }

        if (data.length < (1.5 * width * height)) {
            return;
        }

        if (willCallModelTask) {
          modelProcessorTaskLock = true;
          Log.d("willCallModelTask", "Called");
          getImageData((TextureView) cameraView.getView());
          ModelProcessorAsyncTaskDelegate delegate = (ModelProcessorAsyncTaskDelegate) cameraView;
          new ModelProcessorAsyncTask(delegate, mModelProcessor, mModelInput, mModelMaxFreqms, mThemedReactContext, mLabelFile, width, height, correctRotation).execute();
        }
      }
    });
  }

  private void getImageData(TextureView view) {
    Bitmap bitmap = view.getBitmap(mModelImageDimX, mModelImageDimY);
        // Bitmap bitmap = view.getBitmap(view.getMeasuredWidth(),
        // view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
    bitmap = Bitmap.createScaledBitmap(bitmap, mModelImageDimX, mModelImageDimY, true);
    if (bitmap == null) {
      return;
    }
    if(mModelInput == null){
      return;
    }
      final float imageMean2= 0.485f * 225.0f;
      final float imageMean1= 0.456f * 225.0f;
      final float imageMean3= 0.406f * 225.0f;
      final float imageStd2= 0.229f * 225.0f;
      final float imageStd1= 0.224f * 225.0f;
      final float imageStd3= 0.225f * 225.0f;
intValues = new int[mModelImageDimX* mModelImageDimY];
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    mModelInput.rewind();
    int pixel = 0;
    for (int i = 0; i < mModelImageDimX; ++i) {
      for (int j = 0; j < mModelImageDimY; ++j) {
        int pixelValue = intValues[pixel++];
          mModelInput.putFloat((((pixelValue >> 16) & 0xFF) - imageMean1) / imageStd1);
          mModelInput.putFloat((((pixelValue >> 8) & 0xFF) - imageMean2) / imageStd2);
          mModelInput.putFloat(((pixelValue & 0xFF) - imageMean3) / imageStd3);
      }
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    View preview = getView();
    if (null == preview) {
      return;
    }
    float width = right - left;
    float height = bottom - top;
    float ratio = getAspectRatio().toFloat();
    int orientation = getResources().getConfiguration().orientation;
    int correctHeight;
    int correctWidth;
    this.setBackgroundColor(Color.BLACK);
    if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
      if (ratio * height < width) {
        correctHeight = (int) (width / ratio);
        correctWidth = (int) width;
      } else {
        correctWidth = (int) (height * ratio);
        correctHeight = (int) height;
      }
    } else {
      if (ratio * width > height) {
        correctHeight = (int) (width * ratio);
        correctWidth = (int) width;
      } else {
        correctWidth = (int) (height / ratio);
        correctHeight = (int) height;
      }
    }
    int paddingX = (int) ((width - correctWidth) / 2);
    int paddingY = (int) ((height - correctHeight) / 2);
    mPaddingX = paddingX;
    mPaddingY = paddingY;
    preview.layout(paddingX, paddingY, correctWidth + paddingX, correctHeight + paddingY);
  }

  @SuppressLint("all")
  @Override
  public void requestLayout() {
    // React handles this for us, so we don't need to call super.requestLayout();
  }

  public void setPlaySoundOnCapture(Boolean playSoundOnCapture) {
    mPlaySoundOnCapture = playSoundOnCapture;
  }

  public void takePicture(final ReadableMap options, final Promise promise, final File cacheDirectory) {
    mBgHandler.post(new Runnable() {
      @Override
      public void run() {
        mPictureTakenPromises.add(promise);
        mPictureTakenOptions.put(promise, options);
        mPictureTakenDirectories.put(promise, cacheDirectory);
        if (mPlaySoundOnCapture) {
          MediaActionSound sound = new MediaActionSound();
          sound.play(MediaActionSound.SHUTTER_CLICK);
        }
        try {
          RNCameraView.super.takePicture(options);
        } catch (Exception e) {
          mPictureTakenPromises.remove(promise);
          mPictureTakenOptions.remove(promise);
          mPictureTakenDirectories.remove(promise);

          promise.reject("E_TAKE_PICTURE_FAILED", e.getMessage());
        }
      }
    });
  }

  @Override
  public void onPictureSaved(WritableMap response) {
    RNCameraViewHelper.emitPictureSavedEvent(this, response);
  }

  public void record(final ReadableMap options, final Promise promise, final File cacheDirectory) {
    mBgHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          String path = options.hasKey("path") ? options.getString("path") : RNFileUtils.getOutputFilePath(cacheDirectory, ".mp4");
          int maxDuration = options.hasKey("maxDuration") ? options.getInt("maxDuration") : -1;
          int maxFileSize = options.hasKey("maxFileSize") ? options.getInt("maxFileSize") : -1;

          CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
          if (options.hasKey("quality")) {
            profile = RNCameraViewHelper.getCamcorderProfile(options.getInt("quality"));
          }
          if (options.hasKey("videoBitrate")) {
            profile.videoBitRate = options.getInt("videoBitrate");
          }

          boolean recordAudio = true;
          if (options.hasKey("mute")) {
            recordAudio = !options.getBoolean("mute");
          }

          int orientation = Constants.ORIENTATION_AUTO;
          if (options.hasKey("orientation")) {
            orientation = options.getInt("orientation");
          }

          if (RNCameraView.super.record(path, maxDuration * 1000, maxFileSize, recordAudio, profile, orientation)) {
            mIsRecording = true;
            mVideoRecordedPromise = promise;
          } else {
            promise.reject("E_RECORDING_FAILED", "Starting video recording failed. Another recording might be in progress.");
          }
        } catch (IOException e) {
          promise.reject("E_RECORDING_FAILED", "Starting video recording failed - could not create video file.");
        }
      }
    });
  }


  private void setupModelProcessor() {
    int numBytesPerChannel;
    try {
      mModelProcessor = new Interpreter(loadModelFile());
      Tensor tensor = mModelProcessor.getInputTensor(0);
      int inputSize = tensor.shape()[1];
      int inputChannels = tensor.shape()[3];
      int bytePerChannel = tensor.dataType() == DataType.UINT8 ? 1 : 4;

      mModelInput = ByteBuffer.allocateDirect(1 * inputSize * inputSize *  inputChannels * bytePerChannel);

      mModelInput.order(ByteOrder.nativeOrder());
      mModelProcessor.setNumThreads(NUM_THREADS);
    } catch(Exception e) {}
  }

  private MappedByteBuffer loadModelFile() throws IOException {
    AssetFileDescriptor fileDescriptor = mThemedReactContext.getAssets().openFd(mModelFile);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  public void setModelFile(String modelFile, String labelFile, int inputDimX, int inputDimY, int classes) {
    this.mModelFile = modelFile;
    this.mLabelFile = labelFile;
    this.mModelImageDimX = inputDimX;
    this.mModelImageDimY = inputDimY;
    this.mClasses = classes;
    boolean shouldProcessModel = (modelFile != null && labelFile != null);
    if (shouldProcessModel && mModelProcessor == null) {
      Log.v("setModelFile", "if called");
      setupModelProcessor();
    }
    this.mShouldProcessModel = shouldProcessModel;
    setScanning(mShouldProcessModel);

  }

  @Override
  public void onModelProcessed(WritableMap[] data, int sourceWidth, int sourceHeight, int sourceRotation) {
    if (!mShouldProcessModel) {
      return;
    }
    ImageDimensions dimensions = new ImageDimensions(sourceWidth, sourceHeight, sourceRotation, getFacing());

    RNCameraViewHelper.emitModelProcessedEvent(this, data, dimensions);
  }


  @Override
  public void onModelProcessorTaskCompleted() {
    modelProcessorTaskLock = false;
  }

  @Override
  public void onHostResume() {
    if (hasCameraPermissions()) {
      mBgHandler.post(new Runnable() {
        @Override
        public void run() {
          if ((mIsPaused && !isCameraOpened()) || mIsNew) {
            mIsPaused = false;
            mIsNew = false;
            start();
          }
        }
      });
    } else {
      RNCameraViewHelper.emitMountErrorEvent(this, "Camera permissions not granted - component could not be rendered.");
    }
  }

  @Override
  public void onHostPause() {
    if (mIsRecording) {
      mIsRecordingInterrupted = true;
    }
    if (!mIsPaused && isCameraOpened()) {
      mIsPaused = true;
      stop();
    }
  }

  @Override
  public void onHostDestroy() {
    if (mModelProcessor != null) {
      mModelProcessor.close();
    }
    mMultiFormatReader = null;
    stop();
    mThemedReactContext.removeLifecycleEventListener(this);

    this.cleanup();
  }

  private boolean hasCameraPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      int result = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA);
      return result == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }
}
