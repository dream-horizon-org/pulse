#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting session-capture user-data at $(date)"

export HOME=/home/admin
cd "$HOME" || cd /root

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

sudo mkdir -p /etc/pulse
sudo tee /etc/pulse/capture.env >/dev/null <<EOF
PORT=${port}
KAFKA_BROKERS=${kafka_brokers}
KAFKA_TOPIC=${kafka_topic}
RUST_LOG=${rust_log}
EOF
sudo chmod 644 /etc/pulse/capture.env

echo "Starting pulse-session-capture service..."
sudo systemctl restart pulse-session-capture

sleep 3

if systemctl is-active --quiet pulse-session-capture; then
  echo "Service started successfully"
else
  echo "WARNING: Service may not have started. Checking logs:"
  journalctl -u pulse-session-capture --no-pager -n 30 || true
fi

echo "User-data complete at $(date)"
echo "View logs: journalctl -u pulse-session-capture -f"
