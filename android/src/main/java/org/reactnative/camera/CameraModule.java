package org.reactnative.camera;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.*;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.react.uimanager.NativeViewHierarchyManager;
import com.facebook.react.uimanager.UIBlock;
import com.facebook.react.uimanager.UIManagerModule;
import com.google.android.cameraview.AspectRatio;
import org.reactnative.camera.utils.ScopedContext;
import com.google.android.cameraview.Size;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Properties;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;


public class CameraModule extends ReactContextBaseJavaModule {
  private static final String TAG = "CameraModule";

  private ScopedContext mScopedContext;
  static final int VIDEO_2160P = 0;
  static final int VIDEO_1080P = 1;
  static final int VIDEO_720P = 2;
  static final int VIDEO_480P = 3;
  static final int VIDEO_4x3 = 4;

  public CameraModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mScopedContext = new ScopedContext(reactContext);
  }

  public ScopedContext getScopedContext() {
    return mScopedContext;
  }

  @Override
  public String getName() {
    return "RNCameraModule";
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    return Collections.unmodifiableMap(new HashMap<String, Object>() {
      {
        put("Type", getTypeConstants());
        put("FlashMode", getFlashModeConstants());
        put("AutoFocus", getAutoFocusConstants());
        put("WhiteBalance", getWhiteBalanceConstants());
        put("VideoQuality", getVideoQualityConstants());
        put("Orientation", Collections.unmodifiableMap(new HashMap<String, Object>() {
            {
              put("auto", Constants.ORIENTATION_AUTO);
              put("portrait", Constants.ORIENTATION_UP);
              put("portraitUpsideDown", Constants.ORIENTATION_DOWN);
              put("landscapeLeft", Constants.ORIENTATION_LEFT);
              put("landscapeRight", Constants.ORIENTATION_RIGHT);
            }
        }));
      }

      private Map<String, Object> getTypeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("front", Constants.FACING_FRONT);
            put("back", Constants.FACING_BACK);
          }
        });
      }

      private Map<String, Object> getFlashModeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("off", Constants.FLASH_OFF);
            put("on", Constants.FLASH_ON);
            put("auto", Constants.FLASH_AUTO);
            put("torch", Constants.FLASH_TORCH);
          }
        });
      }

      private Map<String, Object> getAutoFocusConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("on", true);
            put("off", false);
          }
        });
      }

      private Map<String, Object> getWhiteBalanceConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("auto", Constants.WB_AUTO);
            put("cloudy", Constants.WB_CLOUDY);
            put("sunny", Constants.WB_SUNNY);
            put("shadow", Constants.WB_SHADOW);
            put("fluorescent", Constants.WB_FLUORESCENT);
            put("incandescent", Constants.WB_INCANDESCENT);
          }
        });
      }

      private Map<String, Object> getVideoQualityConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("2160p", VIDEO_2160P);
            put("1080p", VIDEO_1080P);
            put("720p", VIDEO_720P);
            put("480p", VIDEO_480P);
            put("4:3", VIDEO_4x3);
          }
        });
      }
    });
  }

    @ReactMethod
    public void pausePreview(final int viewTag) {
        final ReactApplicationContext context = getReactApplicationContext();
        UIManagerModule uiManager = context.getNativeModule(UIManagerModule.class);
        uiManager.addUIBlock(new UIBlock() {
            @Override
            public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
                final RNCameraView cameraView;

                try {
                    cameraView = (RNCameraView) nativeViewHierarchyManager.resolveView(viewTag);
                    if (cameraView.isCameraOpened()) {
                        cameraView.pausePreview();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @ReactMethod
    public void resumePreview(final int viewTag) {
        final ReactApplicationContext context = getReactApplicationContext();
        UIManagerModule uiManager = context.getNativeModule(UIManagerModule.class);
        uiManager.addUIBlock(new UIBlock() {
            @Override
            public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
                final RNCameraView cameraView;

                try {
                    cameraView = (RNCameraView) nativeViewHierarchyManager.resolveView(viewTag);
                    if (cameraView.isCameraOpened()) {
                        cameraView.resumePreview();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }



  @ReactMethod
  public void getSupportedRatios(final int viewTag, final Promise promise) {
      final ReactApplicationContext context = getReactApplicationContext();
      UIManagerModule uiManager = context.getNativeModule(UIManagerModule.class);
      uiManager.addUIBlock(new UIBlock() {
          @Override
          public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
              final RNCameraView cameraView;
              try {
                  cameraView = (RNCameraView) nativeViewHierarchyManager.resolveView(viewTag);
                  WritableArray result = Arguments.createArray();
                  if (cameraView.isCameraOpened()) {
                      Set<AspectRatio> ratios = cameraView.getSupportedAspectRatios();
                      for (AspectRatio ratio : ratios) {
                          result.pushString(ratio.toString());
                      }
                      promise.resolve(result);
                  } else {
                      promise.reject("E_CAMERA_UNAVAILABLE", "Camera is not running");
                  }
              } catch (Exception e) {
                  e.printStackTrace();
              }
          }
      });
  }

  @ReactMethod
  public void getCameraIds(final int viewTag, final Promise promise) {
      final ReactApplicationContext context = getReactApplicationContext();
      UIManagerModule uiManager = context.getNativeModule(UIManagerModule.class);
      uiManager.addUIBlock(new UIBlock() {
          @Override
          public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
              final RNCameraView cameraView;
              try {
                  cameraView = (RNCameraView) nativeViewHierarchyManager.resolveView(viewTag);
                  WritableArray result = Arguments.createArray();
                  List<Properties> ids = cameraView.getCameraIds();
                  for (Properties p : ids) {
                      WritableMap m = new WritableNativeMap();
                      m.putString("id", p.getProperty("id"));
                      m.putInt("type", Integer.valueOf(p.getProperty("type")));
                      result.pushMap(m);
                  }
                  promise.resolve(result);
              } catch (Exception e) {
                  e.printStackTrace();
                  promise.reject("E_CAMERA_FAILED", e.getMessage());
              }
          }
      });
  }

  @ReactMethod
  public void getAvailablePictureSizes(final String ratio, final int viewTag, final Promise promise) {
      final ReactApplicationContext context = getReactApplicationContext();
      UIManagerModule uiManager = context.getNativeModule(UIManagerModule.class);
      uiManager.addUIBlock(new UIBlock() {
          @Override
          public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
              final RNCameraView cameraView;

              try {
                  cameraView = (RNCameraView) nativeViewHierarchyManager.resolveView(viewTag);
                  WritableArray result = Arguments.createArray();
                  if (cameraView.isCameraOpened()) {
                      SortedSet<Size> sizes = cameraView.getAvailablePictureSizes(AspectRatio.parse(ratio));
                      for (Size size : sizes) {
                          result.pushString(size.toString());
                      }
                      promise.resolve(result);
                  } else {
                      promise.reject("E_CAMERA_UNAVAILABLE", "Camera is not running");
                  }
              } catch (Exception e) {
                  promise.reject("E_CAMERA_BAD_VIEWTAG", "getAvailablePictureSizesAsync: Expected a Camera component");
              }
          }
      });
  }

  @ReactMethod
  public void checkIfRecordAudioPermissionsAreDefined(final Promise promise) {
      try {
          PackageInfo info = getCurrentActivity().getPackageManager().getPackageInfo(getReactApplicationContext().getPackageName(), PackageManager.GET_PERMISSIONS);
          if (info.requestedPermissions != null) {
              for (String p : info.requestedPermissions) {
                  if (p.equals(Manifest.permission.RECORD_AUDIO)) {
                      promise.resolve(true);
                      return;
                  }
              }
          }
      } catch (Exception e) {
          e.printStackTrace();
      }
      promise.resolve(false);
  }
}
