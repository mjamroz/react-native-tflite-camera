package org.reactnative.camera.tasks;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public interface ModelProcessorAsyncTaskDelegate {
    void onModelProcessed(HashMap[] data, int sourceWidth, int sourceHeight, int sourceRotation);
    void onModelProcessorTaskCompleted();
}