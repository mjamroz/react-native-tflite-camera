package org.reactnative.camera.tasks;

import android.os.SystemClock;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class ModelProcessorAsyncTask extends android.os.AsyncTask<Void, Void, String> {

    private ModelProcessorAsyncTaskDelegate mDelegate;
    private Interpreter mModelProcessor;
    private ByteBuffer mInputBuf;
    private int mModelMaxFreqms;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;
    private String confidence;

    public ModelProcessorAsyncTask(
            ModelProcessorAsyncTaskDelegate delegate,
            Interpreter modelProcessor,
            ByteBuffer inputBuf,
            int modelMaxFreqms,
            int width,
            int height,
            int rotation
    ) {
        mDelegate = delegate;
        mModelProcessor = modelProcessor;
        mInputBuf = inputBuf;
        mModelMaxFreqms = modelMaxFreqms;
        mWidth = width;
        mHeight = height;
        mRotation = rotation;
    }

    @Override
    protected String doInBackground(Void... ignored) {
        if (isCancelled() || mDelegate == null || mModelProcessor == null) {
            return null;
        }
        long startTime = SystemClock.uptimeMillis();
        try {
            outputLocations = new float[1][10][4];
            outputClasses = new float[1][10];
            outputScores = new float[1][10];
            numDetections = new float[1];

            Object[] inputArray = {mInputBuf};
            Map<Integer, Object> outputMap = new HashMap<>();
            outputMap.put(0, outputLocations);
            outputMap.put(1, outputClasses);
            outputMap.put(2, outputScores);
            outputMap.put(3, numDetections);
            mModelProcessor.runForMultipleInputsOutputs(inputArray, outputMap);
        } catch (Exception e){}
        try {
            if (mModelMaxFreqms > 0) {
                long endTime = SystemClock.uptimeMillis();
                long timeTaken = endTime - startTime;
                if (timeTaken < mModelMaxFreqms) {
                    TimeUnit.MILLISECONDS.sleep(mModelMaxFreqms - timeTaken);
                }
            }
        } catch (Exception e) {}
        final ArrayList recognitions = new ArrayList(10);
        for (int i = 0; i < 10; ++i) {
            if(inRange(outputScores[0][i], 1, 0)){
                recognitions.add((int) i, outputScores[0][i]);
            }
        }
        confidence = String.valueOf(Collections.max(recognitions));
        return confidence;
    }

    private boolean inRange(float number, float max, float min) {
        return number < max && number >= min;
    }

    @Override
    protected void onPostExecute(String data) {
        super.onPostExecute(data);

        if (data != null) {
            mDelegate.onModelProcessed(data, mWidth, mHeight, mRotation);
        }
        mDelegate.onModelProcessorTaskCompleted();
    }
}