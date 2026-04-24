#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting session-replay-ingestion user-data at $(date)"

export HOME=/home/admin
cd "$HOME" || cd /root

echo "Installing dependencies..."
# Disable broken PPA and update
sudo rm -f /etc/apt/sources.list.d/deadsnakes-ppa-*.list || true
sudo apt-get update -qq 2>/dev/null || true
sudo apt-get install -y -qq unzip curl 2>/dev/null || true

# Install Node + pm2
echo "Installing Node and PM2..."
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.4/install.sh | bash
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
nvm install 20
nvm use 20
npm install -g pm2
which pm2

AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-session-replay-ingestion"
APPLICATION_NAME="pulse-session-replay-ingestion"
VERSION="${artifact_version}"
INSTALL_DIR="/opt/pulse-session-replay-ingestion"

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
if [ ! -f "$APPLICATION_NAME/dist/index.js" ]; then
  echo "ERROR: dist/index.js not found under $APPLICATION_NAME/"
  exit 1
fi

sudo rm -rf "$INSTALL_DIR"
sudo mkdir -p "$INSTALL_DIR"
sudo cp -a "$APPLICATION_NAME"/. "$INSTALL_DIR"/
sudo chown -R root:root "$INSTALL_DIR"

sudo mkdir -p /etc/pulse
sudo tee /etc/pulse/ingestion.env >/dev/null <<EOF
KAFKA_BROKERS=${kafka_brokers}
KAFKA_TOPIC=${kafka_topic}
KAFKA_METADATA_TOPIC=${kafka_metadata_topic}
KAFKA_GROUP_ID=${kafka_group_id}
S3_ENDPOINT=${s3_endpoint}
S3_BUCKET=${s3_bucket}
S3_REGION=${s3_region}
S3_PREFIX=${s3_prefix}
MAX_BATCH_SIZE_KB=${max_batch_size_kb}
MAX_BATCH_AGE_MS=${max_batch_age_ms}
FETCH_BATCH_SIZE=${fetch_batch_size}
S3_TIMEOUT_MS=${s3_timeout_ms}
NODE_ENV=production
EOF
sudo chmod 644 /etc/pulse/ingestion.env

echo "Starting pulse-session-replay-ingestion service with pm2..."
cd "$INSTALL_DIR"
# Load env and start with pm2
set -a; source /etc/pulse/ingestion.env; set +a
pm2 start dist/index.js --name "pulse-session-replay-ingestion"
pm2 save

sleep 3

if pm2 list | grep -q "pulse-session-replay-ingestion"; then
  echo "Service started successfully"
else
  echo "WARNING: Service may not have started. Checking logs:"
  pm2 logs pulse-session-replay-ingestion --lines 30 --nostream || true
fi

echo "User-data complete at $(date)"
echo "View logs: pm2 logs pulse-session-replay-ingestion"
