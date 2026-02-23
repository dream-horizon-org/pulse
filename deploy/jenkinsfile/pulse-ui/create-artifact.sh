#!/bin/bash
set -e  # Exit on error

# 1. Setup Logging
exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# 2. Listing current directory
pwd
ls

# 3. Change to deploy directory
cd pulse-ui

# 4. Handle Environment Variables (via AWS Secrets Manager)
# Define variables
SECRET_NAME="prod/pulseui/appenv"
ENV_FILE=".env"

echo "Fetching secret '$SECRET_NAME' from AWS Secrets Manager..."

# Fetch the secret string from AWS Secrets Manager
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_NAME" \
  --query SecretString \
  --output text)

# Check if the AWS CLI command was successful
if [ $? -ne 0 ]; then
  echo "Error: Failed to fetch the secret. Please check your AWS credentials and permissions."
  exit 1
fi

echo "Parsing JSON and writing to '$ENV_FILE'..."

# Parse the JSON using jq and write to the .env file
# -r ensures raw output (no quotes around the final strings)
echo "$SECRET_JSON" | jq -r '.app_env[] | "\(.key)=\(.value)"' > "$ENV_FILE"

# Check if jq command was successful
if [ $? -ne 0 ]; then
  echo "Error: Failed to parse the JSON. Ensure 'jq' is installed and the secret contains valid JSON."
  exit 1
fi

echo "Success! Secrets have been written to $ENV_FILE."

# 5. Build Application
echo "### Installing project dependencies..."
export NODE_OPTIONS=--max_old_space_size=4096
yarn install

echo "### Building the dashboard..."
NODE_ENV=production PORT=3000 yarn build

# 6. Preparing artifact
echo "Getting version from package.json"
RAW_VERSION=$(jq -r '.version' package.json)

if [ -z "$RAW_VERSION" ] || [ "$RAW_VERSION" = "null" ]; then
  echo "Error: version not found in pulse-ui/package.json" >&2
  exit 1
fi

VERSION="${RAW_VERSION%-SNAPSHOT}"
echo "Artifact version: $VERSION"

APPLICATION_NAME="pulse-ui"
ZIP_NAME="$APPLICATION_NAME-$VERSION.zip"

cd ..
zip -q -r $ZIP_NAME $APPLICATION_NAME

# 7. Upload zip to AWS CodeArtifact (generic package)
echo "Uploading $ZIP_NAME to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse"
CODEARTIFACT_REPOSITORY="pulse-ui"

aws codeartifact publish-package-version \
  --region "$AWS_REGION" \
  --domain "$CODEARTIFACT_DOMAIN" \
  --repository "$CODEARTIFACT_REPOSITORY" \
  --format generic \
  --namespace "pulse" \
  --package "$APPLICATION_NAME" \
  --package-version "$VERSION" \
  --asset-name "$ZIP_NAME" \
  --asset-content "fileb://$ZIP_NAME"

echo "Upload successful: $APPLICATION_NAME:$VERSION ($ZIP_NAME)"