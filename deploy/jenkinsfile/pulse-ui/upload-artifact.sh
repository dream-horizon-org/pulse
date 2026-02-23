#!/bin/bash
set -e  # Exit on error

# 1. Setup Logging
exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# 2. Upload zip to AWS CodeArtifact (generic package)
echo "Uploading $ZIP_NAME to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-ui"
FILE_HASH=$(sha256sum pulse-ui-0.1.0.zip | awk '{ print $1 }')

aws codeartifact publish-package-version \
  --region "$AWS_REGION" \
  --domain "$CODEARTIFACT_DOMAIN" \
  --repository "$CODEARTIFACT_REPOSITORY" \
  --format generic \
  --namespace "pulse" \
  --package "$APPLICATION_NAME" \
  --package-version "$VERSION" \
  --asset-name "$ZIP_NAME" \
  --asset-content "$ZIP_NAME" \
  --asset-sha256 "$FILE_HASH"

echo "Upload successful: $APPLICATION_NAME:$VERSION ($ZIP_NAME)"