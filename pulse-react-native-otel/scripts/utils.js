const fs = require('fs');
const path = require('path');
const packageJson = require('../package.json');

const FILE_TYPE_TO_BACKEND_TYPE = {
  'js-sourcemap': 'JS',
  'mapping': 'mapping',
  'android-ndk': 'ndk',
};

function checkAndAssertNodeVersion() {
  const nodeVersion = process.versions.node.split('.');
  const majorVersion = parseInt(nodeVersion[0], 10);

  const requiredVersion = packageJson.engines?.node;
  let minMajorVersion = 18; // fallback

  if (requiredVersion) {
    const match = requiredVersion.match(/>=(\d+)/);
    if (match) {
      minMajorVersion = parseInt(match[1], 10);
    }
  }

  if (majorVersion < minMajorVersion) {
    console.error(
      `✗ Error: Pulse CLI requires Node.js ${minMajorVersion}.0.0 or higher.`
    );
    console.error(`  Current version: ${process.versions.node}`);
    console.error(
      `  Required: ${requiredVersion || `>=${minMajorVersion}.0.0`}`
    );
    console.error(`  Please upgrade Node.js: https://nodejs.org/`);
    process.exit(1);
  }
}

function getPlatform(commandName) {
  if (commandName.includes('android')) {
    return 'android';
  }
  if (commandName.includes('ios')) {
    return 'ios';
  }
  return 'Unknown';
}

function validateFiles(options) {
  const files = [];
  const errors = [];

  Object.keys(FILE_TYPE_TO_BACKEND_TYPE).forEach((fileOption) => {
    const optionKey = fileOption.replace(/-([a-z])/g, (_, letter) =>
      letter.toUpperCase()
    );
    const optionValue = options[optionKey];

    if (!optionValue) {
      return;
    }

    const filePath = path.resolve(optionValue);
    if (!fs.existsSync(filePath)) {
      errors.push(`File not found for filepath: ${filePath}`);
      return;
    }

    files.push({
      optionName: fileOption,
      path: filePath,
      fileName: path.basename(filePath),
      metadataType: FILE_TYPE_TO_BACKEND_TYPE[fileOption],
    });
  });

  if (errors.length > 0) {
    throw new Error(`Validation errors:\n  ${errors.join('\n  ')}`);
  }

  if (files.length === 0) {
    throw new Error('No files to upload');
  }

  return files;
}

function validateVersionVersionCodeBundleId(options, commandName) {
  const platform = getPlatform(commandName);
  const isIOS = platform === 'ios';
  const version = isIOS ? options.bundleVersion : options.appVersion;

  if (!options.versionCode) {
    throw new Error('Version code is required');
  }
  const versionCodeNum = parseInt(options.versionCode, 10);
  if (isNaN(versionCodeNum) || versionCodeNum <= 0) {
    throw new Error(
      `Invalid version code: "${options.versionCode}". Must be a positive integer.`
    );
  }

  if (!version || typeof version !== 'string' || version.trim().length === 0) {
    throw new Error(
      !version
        ? `Missing required option: ${isIOS ? '--bundle-version' : '--app-version'}`
        : `Invalid ${isIOS ? 'bundle version' : 'app version'}: "${version}". Must be a non-empty string.`
    );
  }

  if (options.bundleId) {
    const bundleIdPattern = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/i;
    if (!bundleIdPattern.test(options.bundleId)) {
      throw new Error(
        `Invalid bundle-id: "${options.bundleId}". Must be in reverse domain notation (e.g., com.example.app).`
      );
    }
  }
}

module.exports = {
  checkAndAssertNodeVersion,
  getPlatform,
  validateFiles,
  validateVersionVersionCodeBundleId,
};
