/* global Buffer */
const fs = require('fs');
const {
  getPlatform,
  validateFiles,
  validateVersionVersionCodeBundleId,
} = require('./utils');

function buildMetadata(files, appVersion, versionCode, platform, bundleId) {
  const metadata = files.map((file) => ({
    type: file.metadataType,
    appVersion: appVersion,
    versionCode: versionCode,
    platform: platform,
    fileName: file.fileName,
    bundleId: bundleId || null,
  }));
  return metadata;
}

async function uploadFiles(commandName, options) {
  const platform = getPlatform(commandName);
  const files = validateFiles(options);
  const version =
    platform === 'ios' ? options.bundleVersion : options.appVersion;

  const metadata = buildMetadata(files, version, options.versionCode, platform, options.bundleId);

  const formData = new FormData();
  const metadataContent = JSON.stringify(metadata, null, 2);

  if (options.debug) {
    console.log('\n📋 Metadata content (metadata.txt):');
    console.log('─'.repeat(60));
    console.log(metadataContent);
    console.log('─'.repeat(60));
    console.log(
      `Metadata size: ${Buffer.byteLength(metadataContent, 'utf-8')} bytes\n`
    );
  }

  const metadataBlob = new Blob([metadataContent], {
    type: 'application/json',
  });
  formData.append('metadata', metadataBlob, 'metadata.txt');

  for (const file of files) {
    const fileBuffer = fs.readFileSync(file.path);
    const fileBlob = new Blob([fileBuffer]);
    formData.append('fileContent', fileBlob, file.fileName);
  }

  console.log(`\n📤 Uploading ${files.length} file(s) to ${options.apiUrl}...`);
  console.log(`   Command: ${commandName}`);
  console.log(`   Platform: ${platform}`);
  files.forEach((file) => {
    console.log(`   - ${file.fileName} (${file.metadataType})`);
  });

  if (options.debug) {
    console.log('\n🔍 Debug Info:');
    console.log(`   API URL: ${options.apiUrl}`);
    console.log(`   App Version: ${version}`);
    console.log(`   Version Code: ${options.versionCode}`);
    if (options.bundleId) {
      console.log(`   Bundle ID: ${options.bundleId}`);
    }
    files.forEach((file) => {
      const stats = fs.statSync(file.path);
      console.log(
        `   File: ${file.fileName} (${(stats.size / 1024).toFixed(2)} KB)`
      );
      console.log(`   File Path: ${file.path}`);
    });
  }

  const response = await fetch(options.apiUrl, {
    method: 'POST',
    body: formData,
  });

  const responseData = await response.json().catch(async () => {
    const text = await response.text();
    return text ? { message: text } : {};
  });

  if (options.debug && responseData && Object.keys(responseData).length > 0) {
    console.log('\n📥 Backend Response:');
    console.log(`   Status: ${response.status} ${response.statusText}`);
    console.log(`   Response: ${JSON.stringify(responseData, null, 2)}`);
  }

  if (!response.ok) {
    const errorText =
      responseData.error || responseData.message || 'Unknown error';
    throw new Error(
      `Upload failed: HTTP ${response.status} ${response.statusText}. ${errorText}`
    );
  }

  console.log('\n✓ Files uploaded successfully');
}

async function upload(commandName, options) {
  try {
    validateVersionVersionCodeBundleId(options, commandName);
    await uploadFiles(commandName, options);
  } catch (error) {
    console.error(`\n✗ Error: ${error.message}`);
    process.exit(1);
  }
}

module.exports = {
  upload,
};
