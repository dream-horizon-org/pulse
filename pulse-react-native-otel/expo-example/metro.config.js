// https://docs.expo.dev/guides/customizing-metro
// https://docs.expo.dev/guides/monorepos/
const fs = require('fs');
const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');

const projectRoot = __dirname;
const sdkRoot = path.resolve(projectRoot, '..');

const pulseMainBuilt = path.join(sdkRoot, 'lib', 'module', 'index.js');
const pulseMainSource = path.join(sdkRoot, 'src', 'index.tsx');

function resolvePulseEntry() {
  if (fs.existsSync(pulseMainBuilt)) {
    return pulseMainBuilt;
  }
  if (fs.existsSync(pulseMainSource)) {
    return pulseMainSource;
  }
  return pulseMainBuilt;
}

const config = getDefaultConfig(projectRoot);

config.watchFolders = [...(config.watchFolders ?? []), sdkRoot];

config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  '@dreamhorizonorg/pulse-react-native': sdkRoot,
};

/**
 * Metro often fails to resolve `file:../` scoped packages outside the app root.
 * Force the entry file so dev client / `expo run:ios` bundling always works.
 */
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === '@dreamhorizonorg/pulse-react-native') {
    return {
      type: 'sourceFile',
      filePath: resolvePulseEntry(),
    };
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
