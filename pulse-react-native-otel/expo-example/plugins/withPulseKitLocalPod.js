const { withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

const MARKER = '# [pulse expo-example] Local PulseKit';

/**
 * expo-example only: injects `pod 'PulseKit', :path => …` for local iOS SDK testing.
 * Not part of the published npm package.
 *
 * Pair with `expo-build-properties` → `ios.useFrameworks: "dynamic"` in `app.json`
 * (same idea as `pulse-react-native-otel/example/ios/Podfile`) so source PulseKit +
 * CocoaPods `libwebp` integrate like the bare RN example.
 *
 * @param {{ pulseKitLocalPath?: string }} [props]
 * @param {string} [props.pulseKitLocalPath] - **Absolute** path to a folder containing
 *   `PulseKit.podspec`. If omitted, defaults to `../../../pulse-ios-otel` from `ios/`.
 */
const withPulseKitLocalPod = (config, props = {}) => {
  const raw = props.pulseKitLocalPath;
  const useAbsolute =
    typeof raw === 'string' && raw.length > 0 && path.isAbsolute(raw.trim())
      ? raw.trim()
      : null;

  if (typeof raw === 'string' && raw.length > 0 && !useAbsolute) {
    console.warn(
      '[withPulseKitLocalPod] pulseKitLocalPath must be an absolute path (e.g. /Users/you/repos/pulse-ios-otel). Using default relative path.'
    );
  }

  const pulseKitLines = useAbsolute
    ? `pod 'PulseKit', :path => ${JSON.stringify(useAbsolute)}`
    : `pulsekit_sdk_root = File.expand_path('../../../pulse-ios-otel', __dir__)
  pod 'PulseKit', :path => pulsekit_sdk_root`;

  const injection = `  ${MARKER}
  ${pulseKitLines}
`;

  return withDangerousMod(config, [
    'ios',
    async (cfg) => {
      const podfilePath = path.join(
        cfg.modRequest.platformProjectRoot,
        'Podfile'
      );
      if (!fs.existsSync(podfilePath)) {
        console.warn(
          `[withPulseKitLocalPod] Podfile not found: ${podfilePath}`
        );
        return cfg;
      }
      let contents = fs.readFileSync(podfilePath, 'utf8');
      if (contents.includes(MARKER)) {
        return cfg;
      }
      const needle = /(\n\s*use_expo_modules!\s*\n)/;
      if (!needle.test(contents)) {
        console.warn(
          '[withPulseKitLocalPod] Could not find use_expo_modules! in Podfile'
        );
        return cfg;
      }
      contents = contents.replace(needle, `$1${injection}\n`);
      fs.writeFileSync(podfilePath, contents);
      return cfg;
    },
  ]);
};

module.exports = withPulseKitLocalPod;
