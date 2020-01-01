For more information, see [react-native-camera](https://github.com/react-native-community/react-native-camera)

# Real time Object detection with React Native and tflite

Earlier attempts at Object detection over React Native involved sending image data to the tflite model classifier by sending the image over the bridge or storing the image to disk and accessing the image on the native side. Here's an attempt at live object detection by processing from the camera feed on the native side and getting the output / confidence as a string on the JS side using the react-native-tflite-camera library.

Huge shout-out to the people over at [react-native-camera](https://github.com/react-native-community/react-native-camera). This is essentially just a fork of their awesome work.

Note: This is currently developed only for Android. I am planning to add the support for ios as well. Any contribution is welcomed.

To start, let's create an empty react native project:

```
react-native init tfliteapp
cd tfliteapp
```

Let's add our dependencies:

```
npm i react-native-tflite-camera
yarn add react-native-tflite-camera
```

Follow the install instructions (for android. Same as react-native-camera):

1. Insert the following lines inside android/app/build.gradle:

```
 android {
        ...
        aaptOptions {
            noCompress "tflite"
            noCompress "lite"
        }
    ...
```

Now let's copy .tflite file over to our project.

```
    mkdir -p ./android/app/src/main/assets
    cp <name_of_model_file>.tflite ./android/app/src/main/assets
```

Replace the content of App.js in your project root directory with the following:

```
    import React, {Component} from 'react';
    import {StyleSheet, Text, View } from 'react-native';
    import { RNCamera } from 'react-native-tflite-camera';

    export default class App extends Component {
      constructor(props) {
        super(props);
        this.state = {
          output: false
        };
      }
    processOutput({data}) {
      const confidence = parseFloat(data).toFixed(3) > 0.85
      this.setState(() => {
        output: confidence
      })
    }

      render() {
        const modelParams = {
          file: "<file_name>.tflite",
          inputDimX: 300, //Model input dimension
          inputDimY: 300, //Model input dimension
          isQuantized: false, // set to true if quantized
          freqms: 0
        };
        return (
          <View style={styles.container}>
            <RNCamera
                ref={ref => {
                    this.camera = ref;
                  }}
                style = {styles.preview}
                type={RNCamera.Constants.Type.back}
                flashMode={RNCamera.Constants.FlashMode.on}
                permissionDialogTitle={'Permission to use camera'}
                permissionDialogMessage={'We need your permission to use your camera phone'}
                onModelProcessed={data => this.processOutput(data)}
                modelParams={modelParams}
            >
              {outpur && <Text style={styles.cameraText}>"Hello World"</Text>}
            </RNCamera>
          </View>
        );
      }
    }

 const styles = StyleSheet.create({
      container: {
        flex: 1,
        flexDirection: 'column',
        backgroundColor: 'black'
      },
      preview: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center'
      },
      cameraText: {
        color: 'white',
        fontSize: 18,
        fontWeight: 'bold'
      }
    });
```

We're done! Run your app with the following command.

```
react-native run-android
```

This project has a lot of rough edges. I hope to clean up this up a lot more in the coming days. The rest of the features are the same as `react-native-camera`.
Links:
[Github](https://github.com/FaisalAli19/react-native-tflite-camera)
[npm]()
