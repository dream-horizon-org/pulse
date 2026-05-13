#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting session-capture user-data at $(date)"

export HOME=/home/admin
cd "$HOME" || cd /root

echo "Installing dependencies..."
# Disable broken PPA and update
sudo rm -f /etc/apt/sources.list.d/deadsnakes-ppa-*.list || true

# apt update + install must succeed — silent failures here previously left
# instances without jq, breaking the secrets-fetch step below.
sudo apt-get update -qq
sudo apt-get install -y -qq unzip curl jq

# Verify required tools are on PATH before continuing.
for cmd in jq unzip curl aws; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: required tool '$cmd' not found on PATH after install"; exit 1; }
done

# -------------------------------------------------------------------
# App environment from AWS Secrets Manager (same contract as pulse-server:
# secret JSON { "app_env": [ { "key": "...", "value": "..." }, ... ] })
# -------------------------------------------------------------------
SECRET_NAME="prod/pulse-session-capture/appenv"
ENV_FILE="/etc/pulse/capture.env"

sudo mkdir -p /etc/pulse

echo "Fetching secret '$SECRET_NAME' from AWS Secrets Manager..."
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --region ap-south-1 \
  --secret-id "$SECRET_NAME" \
  --query SecretString \
  --output text)

if [ -z "$SECRET_JSON" ]; then
  echo "Error: SECRET_JSON is empty"
  exit 1
fi

echo "$SECRET_JSON" | jq -r '.app_env[] | "\(.key)=\(.value)"' | sudo tee "$ENV_FILE" >/dev/null
sudo chmod 600 "$ENV_FILE"
echo "Exported $(wc -l < "$ENV_FILE") environment variables to $ENV_FILE"

AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-session-capture"
APPLICATION_NAME="pulse-session-capture"
VERSION="${artifact_version}"

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

unzip -o "$APPLICATION_NAME.zip"
if [ ! -f "$APPLICATION_NAME/$APPLICATION_NAME" ]; then
  echo "ERROR: binary not found at $APPLICATION_NAME/$APPLICATION_NAME"
  exit 1
fi

sudo install -m 0755 "$APPLICATION_NAME/$APPLICATION_NAME" /usr/local/bin/"$APPLICATION_NAME"

echo "Creating systemd service file..."
sudo tee /etc/systemd/system/pulse-session-capture.service >/dev/null <<'SVCEOF'
[Unit]
Description=Pulse Session Capture Service
After=network.target

[Service]
Type=simple
User=root
EnvironmentFile=/etc/pulse/capture.env
ExecStart=/usr/local/bin/pulse-session-capture
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SVCEOF

sudo systemctl daemon-reload
echo "Starting pulse-session-capture service..."
sudo systemctl enable pulse-session-capture
sudo systemctl start pulse-session-capture

sleep 3

if systemctl is-active --quiet pulse-session-capture; then
  echo "Service started successfully"
else
  echo "WARNING: Service may not have started. Checking logs:"
  journalctl -u pulse-session-capture --no-pager -n 30 || true
fi

echo "User-data complete at $(date)"
echo "View logs: journalctl -u pulse-session-capture -f"
