package org.reactnative.camera.tasks;

import java.nio.ByteBuffer;

public interface ModelProcessorAsyncTaskDelegate {
    void onModelProcessed(String data, int sourceWidth, int sourceHeight, int sourceRotation);
    void onModelProcessorTaskCompleted();
}