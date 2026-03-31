const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');
const packageJson = require('../package.json');

const FILE_TYPE_TO_BACKEND_TYPE = {
  'js-sourcemap': 'JS',
  mapping: 'mapping',
  'android-ndk': 'ndk',
  dsym: 'dsym',
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

    let metadataType = FILE_TYPE_TO_BACKEND_TYPE[fileOption];
    if (fileOption === 'dsym') {
      metadataType = validateDsymPathType(filePath);
    }

    files.push({
      optionName: fileOption,
      path: filePath,
      fileName: path.basename(filePath),
      metadataType,
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

  if (options.bundleId !== undefined && options.bundleId !== null) {
    const trimmed = String(options.bundleId).trim();
    if (trimmed.length === 0) {
      throw new Error(
        `Invalid bundle-id: "${options.bundleId}". Must be a non-empty string.`
      );
    }
  }
}

function pulseUploadDetectFileType(filePath) {
  const base = path.basename(filePath);
  const lower = base.toLowerCase();
  const ext = path.extname(base).slice(1).toLowerCase();
  if (lower.endsWith('.dsym') || ext === 'dsym') {
    return 'dsym';
  }
  return 'unknown';
}

function validateDsymPathType(filePath) {
  const detected = pulseUploadDetectFileType(filePath);
  if (detected === 'dsym') {
    return 'dsym';
  }
  const base = path.basename(filePath);
  console.warn(
    `warning: dSYM path does not look like a .dSYM bundle (${base}); upload may be rejected.`
  );
  return 'unknown';
}

/**
 * Zip a dSYM bundle directory to a temp file, or pass through a non-empty file.
 */
function prepareDsymForUpload(originalPath) {
  const stat = fs.statSync(originalPath);
  if (stat.isDirectory()) {
    const parent = path.dirname(originalPath);
    const base = path.basename(originalPath);
    const zipPath = path.join(os.tmpdir(), `${base}.zip`);
    if (fs.existsSync(zipPath)) {
      fs.unlinkSync(zipPath);
    }
    execFileSync('/usr/bin/zip', ['-r', '-q', zipPath, base], {
      cwd: parent,
    });
    if (!fs.existsSync(zipPath)) {
      throw new Error('Failed to create zip archive: zip did not produce a file');
    }
    const sz = fs.statSync(zipPath).size;
    if (sz === 0) {
      fs.unlinkSync(zipPath);
      throw new Error(`Zip archive is empty: ${zipPath}`);
    }
    return {
      path: zipPath,
      fileName: `${base}.zip`,
      cleanup: () => {
        try {
          fs.unlinkSync(zipPath);
        } catch {
          /* ignore */
        }
      },
    };
  }
  if (stat.isFile()) {
    if (stat.size === 0) {
      throw new Error(`File is empty: ${originalPath}`);
    }
    return {
      path: originalPath,
      fileName: path.basename(originalPath),
      cleanup: () => {},
    };
  }
  throw new Error(`Not a file or directory: ${originalPath}`);
}

module.exports = {
  checkAndAssertNodeVersion,
  getPlatform,
  validateFiles,
  validateVersionVersionCodeBundleId,
  prepareDsymForUpload,
};
