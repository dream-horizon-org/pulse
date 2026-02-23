#!/bin/bash
set -e  # Exit on error

# 1. Setup Logging
exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# 2. Listing current directory
pwd
ls

# 3. Change to deploy directory
cd pulse/pulse-ui

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