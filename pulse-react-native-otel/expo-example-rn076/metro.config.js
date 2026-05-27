// Mirror of expo-example/metro.config.js, scoped for the RN 0.76 smoke target.
// Force `react` / `react-native` to resolve from the app's node_modules so the
// `file:../` SDK does not pull in a second React copy.
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
