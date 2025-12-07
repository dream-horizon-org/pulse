const fs = require('fs');
const { getPlatform, validateFiles } = require('./utils');

function buildMetadata(files, version, versionCode, platform, bundleId) {
  const metadata = files.map(file => ({
    type: file.metadataType,
    appVersion: version,
    versionCode: versionCode,
    platform: platform,
    fileName: file.fileName
  }));
  
  if (bundleId) {
    metadata.forEach(item => {
      item.bundleId = bundleId;
    });
  }
  
  return metadata;
}

async function uploadFiles(commandName, options) {
  const platform = getPlatform(commandName);
  const files = validateFiles(options);

  const version = platform === 'iOS' ? options.bundleVersion : options.appVersion;
  
  if (!version || !options.versionCode) {
    const versionFlag = platform === 'iOS' ? '--bundle-version' : '--app-version';
    throw new Error(`Missing required options: ${versionFlag} and --version-code`);
  }
  
  const metadata = buildMetadata(
    files,
    version,
    options.versionCode,
    platform,
    options.bundleId
  );
  
  const formData = new FormData();
  const metadataContent = JSON.stringify(metadata, null, 2);
  
  if (options.debug) {
    console.log('\n📋 Metadata content (metadata.txt):');
    console.log('─'.repeat(60));
    console.log(metadataContent);
    console.log('─'.repeat(60));
    console.log(`Metadata size: ${Buffer.byteLength(metadataContent, 'utf-8')} bytes\n`);
  }
  
  const metadataBlob = new Blob([metadataContent], { type: 'application/json' });
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
  
  const response = await fetch(options.apiUrl, {
    method: 'POST',
    body: formData,
  });
  
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Upload failed: ${response.status} ${response.statusText}\n${errorText}`);
  }
  
  const responseData = await response.json().catch(async () => {
    const text = await response.text();
    return text ? { message: text } : {};
  });
  
  console.log('\n✓ Files uploaded successfully');
  if (responseData && Object.keys(responseData).length > 0) {
    console.log('Response:', JSON.stringify(responseData, null, 2));
  }
}

module.exports = {
  uploadFiles
};
