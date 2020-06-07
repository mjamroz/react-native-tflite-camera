package org.reactnative.camera.tasks;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import com.facebook.react.bridge.WritableMap;

public interface ModelProcessorAsyncTaskDelegate {
    void onModelProcessed(WritableMap[] data, int sourceWidth, int sourceHeight, int sourceRotation);
    void onModelProcessorTaskCompleted();
}
