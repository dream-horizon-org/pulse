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

module.exports = {
  checkAndAssertNodeVersion,
  getPlatform,
  validateFiles,
};
