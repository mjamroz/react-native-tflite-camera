package org.reactnative.camera.tasks;

import android.util.Log;
import com.facebook.react.bridge.Arguments;

import com.facebook.react.uimanager.ThemedReactContext;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import com.facebook.react.bridge.WritableArray;
import java.util.PriorityQueue;
import java.util.Comparator;
import com.facebook.react.bridge.WritableMap;

public class ModelProcessorAsyncTask extends android.os.AsyncTask<Void, Void, WritableMap[]> {

    private ModelProcessorAsyncTaskDelegate mDelegate;
    private Interpreter mModelProcessor;
    private ByteBuffer mInputBuf;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private String mLabel;
    private float[][] labelProb;
    private Vector<String> labels = new Vector<String>();
    private ThemedReactContext readReactContext;
    private final int numResults = 5;
    private final float cutoff = 0.001f;

    public ModelProcessorAsyncTask(
            ModelProcessorAsyncTaskDelegate delegate,
            Interpreter modelProcessor,
            ByteBuffer inputBuf,
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
        mLabel = mLabelFile;
        mWidth = width;
        mHeight = height;
        mRotation = rotation;
            }

    @Override
    protected WritableMap[] doInBackground(Void... ignored) {
        if (isCancelled() || mDelegate == null || mModelProcessor == null || mLabel == null) {
            return null;
        }
        if (labels.size() < 1) {
            try {
                InputStream fileDescriptor = readReactContext.getAssets().open(mLabel);
                BufferedReader br = new BufferedReader(new InputStreamReader(fileDescriptor));
                String line;
                while ((line = br.readLine()) != null) {
                    labels.add(line);
                }
                br.close();
            }catch (Exception e) {

               Log.e("EEEEEEEEEEEEEEEEEEEEEEEEE LABEL", "if called");

                System.out.println(e);}
        }

        try {
            labelProb = new float[1][labels.size()];
            Map<Integer, Object> outputs = new HashMap<>();
            Object[] inputs = { mInputBuf };
            outputs.put(0, labelProb);
            mModelProcessor.runForMultipleInputsOutputs(inputs, outputs);
        } catch (Exception e){
               Log.e("EEEEEEEEEEEEEEEEEEEEEEEEE RUN", "if called");
            System.out.println(e);
        }
        final float[] classes = new float[labels.size()];
        for (int c = 0; c < labels.size(); c++) {
            classes[c] = labelProb[0][c];
        }
        softmax(classes);
        PriorityQueue<WritableMap> pq =
            new PriorityQueue<>(
                    numResults,
                    new Comparator<WritableMap>() {
                        @Override
                        public int compare(WritableMap lhs, WritableMap rhs) {
                            return Double.compare(rhs.getDouble("confidence"), lhs.getDouble("confidence"));
                        }
                    });

        for (int i = 0; i < labels.size(); ++i) {
            if (classes[i] < cutoff)
                continue;
            WritableMap res = Arguments.createMap();
            res.putString("label", labels.size() > i ? labels.get(i) : "unknown");
            res.putDouble("confidence", classes[i]);
            pq.add(res);
        }

        int recognitionsSize = Math.min(pq.size(), numResults);
        final WritableMap[] recognitions = new WritableMap[recognitionsSize];
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions[i] = pq.poll();
        }
        return recognitions;
    }

    private void softmax(final float[] vals) {
        float max = Float.NEGATIVE_INFINITY;
        for (final float val : vals) {
            max = Math.max(max, val);
        }
        float sum = 0.0f;
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = (float) Math.exp(vals[i] - max);
            sum += vals[i];
        }
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = vals[i] / sum;
        }
    }

    @Override
    protected void onPostExecute(WritableMap[] data) {
        super.onPostExecute(data);

        if (data != null) {
            mDelegate.onModelProcessed(data, mWidth, mHeight, mRotation);
        }
        mDelegate.onModelProcessorTaskCompleted();
    }
}
