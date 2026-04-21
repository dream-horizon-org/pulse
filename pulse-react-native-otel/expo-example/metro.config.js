// https://docs.expo.dev/guides/customizing-metro
// https://docs.expo.dev/guides/monorepos/
const fs = require('fs');
const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');

const projectRoot = __dirname;
const sdkRoot = path.resolve(projectRoot, '..');
const appNodeModules = path.resolve(projectRoot, 'node_modules');

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

// Files under sdkRoot must resolve `react` from the app — otherwise Metro loads
// pulse-react-native-otel/node_modules/react and hooks throw (invalid hook call / useMemo of null).
config.resolver.nodeModulesPaths = [
  appNodeModules,
  ...(config.resolver.nodeModulesPaths ?? []),
];

config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  '@dreamhorizonorg/pulse-react-native': sdkRoot,
  'react': path.join(appNodeModules, 'react'),
  'react-native': path.join(appNodeModules, 'react-native'),
};

const defaultResolveRequest = config.resolver.resolveRequest;

function resolveFromAppNodeModules(moduleName) {
  try {
    return require.resolve(moduleName, { paths: [appNodeModules] });
  } catch {
    return null;
  }
}

/**
 * Metro often fails to resolve `file:../` scoped packages outside the app root.
 * Force React / RN to the app's copies so Pulse hooks share one React instance.
 */
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === '@dreamhorizonorg/pulse-react-native') {
    return {
      type: 'sourceFile',
      filePath: resolvePulseEntry(),
    };
  }

  const forceFromApp =
    moduleName === 'react' ||
    moduleName === 'react-native' ||
    moduleName.startsWith('react/') ||
    moduleName.startsWith('react-native/');

  if (forceFromApp) {
    const filePath = resolveFromAppNodeModules(moduleName);
    if (filePath) {
      return { type: 'sourceFile', filePath };
    }
  }

  if (typeof defaultResolveRequest === 'function') {
    return defaultResolveRequest(context, moduleName, platform);
  }

  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
