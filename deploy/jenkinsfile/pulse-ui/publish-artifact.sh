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

# 2. Activating NVM
echo "Sourcing NVM directly"
export NVM_DIR="$HOME/.nvm"
# Load nvm function into the current shell session
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

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
echo "Artifact version: $VERSION"

APPLICATION_NAME="pulse-ui"
ZIP_NAME="$APPLICATION_NAME-$VERSION.zip"

cd ..
zip -q -r $ZIP_NAME $APPLICATION_NAME -x "${APPLICATION_NAME}/node_modules/*"

# 7. Upload zip to AWS CodeArtifact (generic package)
echo "Uploading $ZIP_NAME to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-ui"
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