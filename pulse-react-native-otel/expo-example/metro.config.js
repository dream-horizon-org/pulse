// Learn more https://docs.expo.dev/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const monorepoRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

// Add the parent directory to watchFolders so Metro can watch for changes
config.watchFolders = [monorepoRoot];

// Resolve the local package from parent directory
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(monorepoRoot, 'node_modules'),
];

// Ensure Metro can resolve the local package
config.resolver.extraNodeModules = {
  '@dreamhorizonorg/pulse-react-native': path.resolve(monorepoRoot),
};

module.exports = config;

