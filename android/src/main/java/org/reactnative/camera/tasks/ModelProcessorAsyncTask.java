package org.reactnative.camera.tasks;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.SystemClock;

import com.facebook.react.uimanager.ThemedReactContext;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;


public class ModelProcessorAsyncTask extends android.os.AsyncTask<Void, Void, HashMap[]> {

    private ModelProcessorAsyncTaskDelegate mDelegate;
    private Interpreter mModelProcessor;
    private ByteBuffer mInputBuf;
    private int mModelMaxFreqms;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private String mLabel;
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
    private Vector<String> labels = new Vector<String>();
    private ThemedReactContext readReactContext;

    public ModelProcessorAsyncTask(
            ModelProcessorAsyncTaskDelegate delegate,
            Interpreter modelProcessor,
            ByteBuffer inputBuf,
            int modelMaxFreqms,
            ThemedReactContext mThemedReactContext,
            String mLabelFile,
            int width,
            int height,
            int rotation
    ) {
        mDelegate = delegate;
        mModelProcessor = modelProcessor;
        mInputBuf = inputBuf;
        readReactContext = mThemedReactContext;
        mModelMaxFreqms = modelMaxFreqms;
        mLabel = mLabelFile;
        mWidth = width;
        mHeight = height;
        mRotation = rotation;
    }

    @Override
    protected HashMap[] doInBackground(Void... ignored) {
        if (isCancelled() || mDelegate == null || mModelProcessor == null || mLabel == null) {
            return null;
        }
        try {
            InputStream fileDescriptor = readReactContext.getAssets().open(mLabel);
            BufferedReader br = new BufferedReader(new InputStreamReader(fileDescriptor));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            br.close();
        }catch (Exception e) { System.out.println(e);}
        

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
//        try {
//            if (mModelMaxFreqms > 0) {
//                long endTime = SystemClock.uptimeMillis();
//                long timeTaken = endTime - startTime;
//                if (timeTaken < mModelMaxFreqms) {
//                    TimeUnit.MILLISECONDS.sleep(mModelMaxFreqms - timeTaken);
//                }
//            }
//        } catch (Exception e) {}
        final HashMap[] recognitions = new HashMap[10];
        int labelOffset = 1;
        for (int i = 0; i < 10; ++i) {
            HashMap<String, String> arrMap = new HashMap<String, String>();
            String confidence = String.valueOf(outputScores[0][i]);
            arrMap.put("classname", labels.get((int) outputClasses[0][i] + labelOffset));
            arrMap.put("confidence", confidence);
            recognitions[i] = arrMap;
        }

        return recognitions;
    }

    private boolean inRange(float number, float max, float min) {
        return number < max && number >= min;
    }

    @Override
    protected void onPostExecute(HashMap[] data) {
        super.onPostExecute(data);

        if (data != null) {
            mDelegate.onModelProcessed(data, mWidth, mHeight, mRotation);
        }
        mDelegate.onModelProcessorTaskCompleted();
    }
}