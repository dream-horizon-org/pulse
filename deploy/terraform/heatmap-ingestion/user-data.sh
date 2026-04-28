#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting heatmap-ingestion user-data at $(date)"

export HOME=/home/admin
cd "$HOME" || cd /root

AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-heatmap-screenshot-ingestion"
APPLICATION_NAME="pulse-heatmap-screenshot-ingestion"
VERSION="${artifact_version}"
INSTALL_DIR="/opt/pulse-heatmap-screenshot-ingestion"

# Download artifact from CodeArtifact
aws codeartifact get-package-version-asset \
  --region "$AWS_REGION" \
  --domain "$CODEARTIFACT_DOMAIN" \
  --repository "$CODEARTIFACT_REPOSITORY" \
  --format generic \
  --namespace "pulse" \
  --package "$APPLICATION_NAME" \
  --package-version "$VERSION" \
  --asset "$APPLICATION_NAME-$VERSION.zip" \
  "$APPLICATION_NAME.zip"

# Unzip + validate
unzip -o "$APPLICATION_NAME.zip"
if [ ! -f "$APPLICATION_NAME/dist/index.js" ]; then
  echo "ERROR: dist/index.js not found under $APPLICATION_NAME/"
  exit 1
fi

# Install to /opt
sudo rm -rf "$INSTALL_DIR"
sudo mkdir -p "$INSTALL_DIR"
sudo cp -a "$APPLICATION_NAME"/. "$INSTALL_DIR"/
sudo chown -R root:root "$INSTALL_DIR"

# Pull runtime env from Secrets Manager and write env file
SECRET_NAME="prod/pulse-heatmap-screenshot-ingestion/appenv"
ENV_FILE="/etc/pulse/heatmap-ingestion.env"

echo "Fetching secret '$SECRET_NAME' from AWS Secrets Manager..."
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --region "$AWS_REGION" \
  --secret-id "$SECRET_NAME" \
  --query SecretString \
  --output text)

if [ -z "$SECRET_JSON" ]; then
  echo "ERROR: secret '$SECRET_NAME' is empty"
  exit 1
fi

sudo mkdir -p /etc/pulse
# Secret format matches pulse-server / pulse-alerts-cron: { "app_env": [ { "key": "...", "value": "..." } ] }
# Use stdlib json (AMI may not ship jq).
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 not found; cannot parse appenv secret without jq"
  exit 1
fi
echo "$SECRET_JSON" | python3 -c 'import json, sys
data = json.load(sys.stdin)
for item in data.get("app_env", []):
    k = item.get("key") or ""
    v = item.get("value", "")
    if k:
        print("%s=%s" % (k, v))
' | sudo tee "$ENV_FILE" >/dev/null
sudo chmod 600 "$ENV_FILE"

echo "Starting pulse-heatmap-screenshot-ingestion service..."
# If AMI has systemd unit, this will work; otherwise harmless if it fails
sudo systemctl restart pulse-heatmap-screenshot-ingestion || true

echo "Starting pulse-heatmap-screenshot-ingestion via pm2..."
set -a; source "$ENV_FILE"; set +a
pm2 start "$INSTALL_DIR/dist/index.js" --name "pulse-heatmap-screenshot-ingestion"
pm2 save

sleep 5

if pm2 list | grep -q "pulse-heatmap-screenshot-ingestion"; then
  echo "Service started successfully via pm2"
else
  echo "WARNING: pm2 process may not have started. Checking logs:"
  pm2 logs pulse-heatmap-screenshot-ingestion --lines 30 --nostream || true
fi

echo "User-data complete at $(date)"
echo "View logs: pm2 logs pulse-heatmap-screenshot-ingestion"