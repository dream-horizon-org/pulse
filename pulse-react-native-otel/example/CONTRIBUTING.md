# Contributing to Pulse React Native iOS

This guide is for contributors who want to work on the React Native iOS implementation of Pulse.

## Overview

Pulse React Native iOS is built on top of the **Pulse iOS SDK**. We expose its functionality to the React Native world using native bridges/JSI, depending on the architecture.

## Quick Start

### Working with React Native/JavaScript Only

If you're only making changes to the React Native/JavaScript layer, follow these steps:

1. **Install dependencies:**
   ```bash
   cd example/ios
   pod install
   ```

2. **Run the app:**
   ```bash
   # Debug build
   yarn ios
   
   # Release build
   yarn ios --mode=Release
   ```

> **Note:** The example app currently points to the latest published version of Pulse iOS SDK from CocoaPods.

## Working with Local Pulse iOS SDK

If you need to make changes to the Pulse iOS SDK itself (located in [pulse-ios-sdk]), you'll need to clone the repository and point the Podfile to your local SDK path.

### Steps

1. **Clone the Pulse iOS SDK repository:**
   
   ```bash
   git clone https://github.com/dream-horizon-org/pulse-ios-sdk.git
   cd pulse-ios-sdk
   # Optionally checkout a specific branch or tag
   # git checkout <branch-name>
   ```
   
   Note the absolute path where you cloned the repository (e.g., `/Users/yourname/pulse-ios-sdk`).

2. **Update the Podfile:**
   
   Edit `pulse-react-native-otel/example/ios/Podfile` and update the `PULSE_SDK_PATH` variable:

   ```ruby
   PULSE_SDK_PATH = '<absolute-path-to>/pulse-ios-sdk'

   # Core dependencies (required by PulseKit)
   pod 'Pulse-Swift-Protocol-Exporter-Common', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-Protocol-Exporter-Http', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-SdkResourceExtension', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-Instrumentation-NetworkStatus', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-Instrumentation-URLSession', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-Instrumentation-Interaction', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-Sessions', :path => "#{PULSE_SDK_PATH}"
   pod 'Pulse-Swift-SignPostIntegration', :path => "#{PULSE_SDK_PATH}"

   # Main PulseKit SDK
   pod 'PulseKit', :path => "#{PULSE_SDK_PATH}"
   ```

   Replace `<absolute-path-to>` with the actual absolute path to your local `pulse-ios-sdk` repository.

3. **Reinstall pods:**
   ```bash
   cd example/ios
   pod install
   ```

4. **Run the app:**
   ```bash
   # Debug build
   yarn ios
   
   # Release build
   yarn ios --mode=Release
   ```

## Architecture Notes

- **Native Bridge/JSI:** The SDK uses native bridges (or JSI for New Architecture) to communicate between React Native and iOS native code.
- **Dependency Management:** iOS dependencies are managed via CocoaPods.
- **SDK Integration:** The React Native wrapper exposes Pulse iOS SDK functionality through a thin native layer.