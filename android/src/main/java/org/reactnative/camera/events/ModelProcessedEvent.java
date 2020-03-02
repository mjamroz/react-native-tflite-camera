package org.reactnative.camera.events;

import androidx.core.util.Pools;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import org.reactnative.camera.CameraViewManager;
import org.reactnative.camera.utils.ImageDimensions;
import org.reactnative.facedetector.FaceDetectorUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class ModelProcessedEvent extends Event<ModelProcessedEvent> {

    private static final Pools.SynchronizedPool<ModelProcessedEvent> EVENTS_POOL =
            new Pools.SynchronizedPool<>(3);


    private double mScaleX;
    private double mScaleY;
    private HashMap[] mData;
    private ImageDimensions mImageDimensions;

    private ModelProcessedEvent() {}

    public static ModelProcessedEvent obtain(
            int viewTag,
            HashMap[] data,
            ImageDimensions dimensions,
            double scaleX,
            double scaleY) {
        ModelProcessedEvent event = EVENTS_POOL.acquire();
        if (event == null) {
            event = new ModelProcessedEvent();
        }
        event.init(viewTag, data, dimensions, scaleX, scaleY);
        return event;
    }

    private void init(
            int viewTag,
            HashMap[] data,
            ImageDimensions dimensions,
            double scaleX,
            double scaleY) {
        super.init(viewTag);
        mData = data;
        mImageDimensions = dimensions;
        mScaleX = scaleX;
        mScaleY = scaleY;
    }

    @Override
    public String getEventName() {
        return CameraViewManager.Events.EVENT_ON_MODEL_PROCESSED.toString();
    }

    @Override
    public void dispatch(RCTEventEmitter rctEventEmitter) {
        rctEventEmitter.receiveEvent(getViewTag(), getEventName(), serializeEventData());
    }

    private WritableMap serializeEventData() {
        WritableArray dataList = Arguments.createArray();

        for (int i=0; i<mData.length; i++){
            WritableMap dataMap = Arguments.createMap();
            for (Object key : mData[i].keySet()){
                dataMap.putString(key.toString(), mData[i].get(key).toString());
            }
            dataList.pushMap(dataMap);
        }

        WritableMap event = Arguments.createMap();
        event.putString("type", "textBlock");
        event.putArray("data", dataList);
        event.putInt("target", getViewTag());
        return event;
    }

    private WritableMap rotateTextX(WritableMap text) {
        ReadableMap faceBounds = text.getMap("bounds");

        ReadableMap oldOrigin = faceBounds.getMap("origin");
        WritableMap mirroredOrigin = FaceDetectorUtils.positionMirroredHorizontally(
                oldOrigin, mImageDimensions.getWidth(), mScaleX);

        double translateX = -faceBounds.getMap("size").getDouble("width");
        WritableMap translatedMirroredOrigin = FaceDetectorUtils.positionTranslatedHorizontally(mirroredOrigin, translateX);

        WritableMap newBounds = Arguments.createMap();
        newBounds.merge(faceBounds);
        newBounds.putMap("origin", translatedMirroredOrigin);

        text.putMap("bounds", newBounds);

        ReadableArray oldComponents = text.getArray("components");
        WritableArray newComponents = Arguments.createArray();
        for (int i = 0; i < oldComponents.size(); ++i) {
            WritableMap component = Arguments.createMap();
            component.merge(oldComponents.getMap(i));
            rotateTextX(component);
            newComponents.pushMap(component);
        }
        text.putArray("components", newComponents);

        return text;
    }

}