package org.reactnative.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.google.android.cameraview.CameraView;

import org.reactnative.camera.tasks.ModelProcessorAsyncTask;
import org.reactnative.camera.tasks.ModelProcessorAsyncTaskDelegate;
import org.reactnative.camera.utils.ImageDimensions;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.DataType;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;



public class RNCameraView extends CameraView implements LifecycleEventListener,
    ModelProcessorAsyncTaskDelegate {
  private ThemedReactContext mThemedReactContext;
  private Boolean mPlaySoundOnCapture = false;
  private boolean mIsPaused = false;
  private boolean mIsNew = true;
  public volatile boolean modelProcessorTaskLock = false;

  // Scanning-related properties
  private String mModelFile;
  private String mLabelFile;
  private final Interpreter.Options options = new Interpreter.Options();
  private Interpreter mModelProcessor;
  private ByteBuffer mModelInput;
  private int[] intValues;
  private int inputSize;
  private static final int NUM_THREADS = 1;
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
          new ModelProcessorAsyncTask(delegate, mModelProcessor, mModelInput, mThemedReactContext, mLabelFile, width, height, correctRotation).execute();
        }
      }
    });
  }

  private void getImageData(TextureView view) {
    Bitmap bitmap = view.getBitmap(this.inputSize, this.inputSize);
    bitmap = Bitmap.createScaledBitmap(bitmap, this.inputSize, this.inputSize, true);
    if (bitmap == null || mModelInput == null){
      return;
    }
      final float imageMean2= 123.68f;
      final float imageMean1= 116.779f;
      final float imageMean3= 103.939f;
      final float imageStd2= 58.393f;
      final float imageStd1= 57.12f;
      final float imageStd3= 57.375f;

intValues = new int[this.inputSize* this.inputSize];
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    mModelInput.rewind();
    int pixel = 0;
    for (int i = 0; i < this.inputSize; ++i) {
      for (int j = 0; j < this.inputSize; ++j) {
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

  private void setupModelProcessor() {
    int numBytesPerChannel;
    try {
      mModelProcessor = new Interpreter(loadModelFile());
      Tensor tensor = mModelProcessor.getInputTensor(0);
      this.inputSize = tensor.shape()[1];
      int inputChannels = tensor.shape()[3];
      int bytePerChannel = tensor.dataType() == DataType.UINT8 ? 1 : 4;

      mModelInput = ByteBuffer.allocateDirect(1 * this.inputSize * this.inputSize *  inputChannels * bytePerChannel);

      mModelInput.order(ByteOrder.nativeOrder());
      mModelProcessor.setNumThreads(NUM_THREADS);
    } catch(Exception e) {
          Log.d("EXCEPTION!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", "Called");
    }
  }

  private MappedByteBuffer loadModelFile() throws IOException {
    AssetFileDescriptor fileDescriptor = mThemedReactContext.getAssets().openFd(mModelFile);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  public void setModelFile(String modelFile, String labelFile) {
    this.mModelFile = modelFile;
    this.mLabelFile = labelFile;
    loadModel();
  }
  private void loadModel() {
    boolean shouldProcessModel = (this.mModelFile != null && this.mLabelFile != null);
    if (shouldProcessModel && mModelProcessor == null) {
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
    if (!mIsPaused && isCameraOpened()) {
      mIsPaused = true;
      stop();
    }
  }

  @Override
  public void onHostDestroy() {
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
