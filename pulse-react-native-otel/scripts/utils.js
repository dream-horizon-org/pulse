const fs = require('fs');
const path = require('path');
const FILE_TYPE_TO_BACKEND_TYPE = {
  'js-sourcemap': 'JS',
  'java-mapping': 'ANDROID',
};

function checkNodeVersion() {
  const nodeVersion = process.versions.node.split('.');
  const majorVersion = parseInt(nodeVersion[0], 10);
  if (majorVersion < 18) {
    console.error(`✗ Error: Pulse CLI requires Node.js 18.0.0 or higher.`);
    console.error(`  Current version: ${process.versions.node}`);
    console.error(`  Please upgrade Node.js: https://nodejs.org/`);
    process.exit(1);
  }
}

function getPlatform(commandName) {
  if (commandName.includes('android')) {
    return 'Android';
  }
  if (commandName.includes('ios')) {
    return 'iOS';
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
      errors.push(`File not found: ${filePath}`);
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
  checkNodeVersion,
  getPlatform,
  validateFiles,
};
