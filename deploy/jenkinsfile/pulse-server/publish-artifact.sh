#!/bin/bash
set -e  # Exit on error

while getopts v: flag
do
    case "${flag}" in
        v) VERSION=${OPTARG};;
    esac
done

if [ -z "$VERSION" ]; then
    echo 'Missing option -a (Git username)' >&2
    exit 1
fi

# 1. Setup Logging
exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# 2. Building Package
echo "Building pulse-server"
cd backend/server
mvn clean package -DskipTests -Dcheckstyle.skip=true

# 3. Preparing artifact
echo "Artifact version: $VERSION"
APPLICATION_NAME="pulse-server"
ZIP_NAME="$APPLICATION_NAME-$VERSION.zip"
JAR_PATH="target/pulse-server/pulse-server.jar"

mkdir -p artifact/${APPLICATION_NAME}
cp ${JAR_PATH} artifact/${APPLICATION_NAME}/
cp -r src/main/resources/conf artifact/${APPLICATION_NAME}/
cp -r src/main/resources/logback artifact/${APPLICATION_NAME}/

cd artifact
zip -q -r ${ZIP_NAME} ${APPLICATION_NAME}

# 4. Upload zip to AWS CodeArtifact (generic package)
echo "Uploading $ZIP_NAME to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-server"
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

