# react-native-audio-streaming

## Getting started

`$ npm install react-native-audio-streaming --save`

### Mostly automatic installation

`$ react-native link react-native-audio-streaming`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-audio-streaming` and add `TXAudioStreaming.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libTXAudioStreaming.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.reactlibrary.TXAudioStreamingPackage;` to the imports at the top of the file
  - Add `new TXAudioStreamingPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-audio-streaming'
  	project(':react-native-audio-streaming').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-audio-streaming/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-audio-streaming')
  	```


## Usage
```javascript
import TXAudioStreaming from 'react-native-audio-streaming';

// TODO: What to do with the module?
TXAudioStreaming;
```
  