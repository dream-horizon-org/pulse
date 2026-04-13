#!/bin/bash
set -e

while getopts v: flag
do
    case "${flag}" in
        v) VERSION=${OPTARG};;
    esac
done

if [ -z "$VERSION" ]; then
    echo 'Missing option -v (version)' >&2
    exit 1
fi

# 1. Setup Logging
exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting pulse-ai artifact publish at $(date)"

# 2. Prepare artifact directory
echo "Preparing pulse-ai artifact"
cd pulse_ai

# 3. Create artifact structure
APPLICATION_NAME="pulse-ai"
ZIP_NAME="$APPLICATION_NAME-$VERSION.zip"
ARTIFACT_DIR="$APPLICATION_NAME"

mkdir -p ../artifact/$ARTIFACT_DIR

# 4. Copy entire pulse_ai directory
echo "Copying pulse-ai source files..."
cp -r . ../artifact/$ARTIFACT_DIR/


# 5. Create ZIP
echo "Creating artifact ZIP..."
cd ../artifact
zip -q -r ${ZIP_NAME} ${ARTIFACT_DIR}

# 7. Upload to AWS CodeArtifact
echo "Uploading $ZIP_NAME to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-ai"
FILE_HASH=$(sha256sum $ZIP_NAME | awk '{ print $1 }')

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
echo "Published at: $(date)"
