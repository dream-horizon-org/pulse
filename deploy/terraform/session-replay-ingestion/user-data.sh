#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting session-ingestion user-data at $(date)"

export HOME=/home/admin
cd "$HOME" || cd /root

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
EOF
sudo chmod 644 /etc/pulse/ingestion.env

# Node.js + pm2 (minimal AMIs often lack both). Tarball from nodejs.org with retries;
# bake Node into AMI or ship via CodeArtifact to avoid boot-time egress to nodejs.org.
export PATH="/usr/local/bin:$PATH"
if ! command -v node >/dev/null 2>&1; then
  NODE_VERSION="20.19.0"
  ARCH="$(uname -m)"
  case "$ARCH" in
    x86_64) NODE_DIST="linux-x64" ;;
    aarch64|arm64) NODE_DIST="linux-arm64" ;;
    *)
      echo "ERROR: unsupported machine $ARCH for Node.js install"
      exit 1
      ;;
  esac
  NODE_TGZ="node-v$NODE_VERSION-$NODE_DIST.tar.xz"
  NODE_URL="https://nodejs.org/dist/v$NODE_VERSION/$NODE_TGZ"
  echo "Installing Node.js $NODE_VERSION ($NODE_DIST) from nodejs.org..."
  attempt=0
  until curl -fsSL "$NODE_URL" -o "/tmp/$NODE_TGZ"; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 5 ]; then
      echo "ERROR: failed to download Node.js after $attempt attempts"
      exit 1
    fi
    echo "curl failed (attempt $attempt), sleeping 15s..."
    sleep 15
  done
  tar -xJf "/tmp/$NODE_TGZ" -C /usr/local --strip-components=1
  rm -f "/tmp/$NODE_TGZ"
  echo "Node.js $(node --version) installed"
fi
if ! command -v pm2 >/dev/null 2>&1; then
  echo "Installing pm2 globally..."
  npm install -g pm2
  echo "pm2 $(pm2 --version) installed"
fi

# librdkafka (node-rdkafka) loads libzstd at runtime for zstd-compressed Kafka batches
ensure_libzstd() {
  if ldconfig -p 2>/dev/null | grep -q 'libzstd.so'; then
    echo "libzstd shared library already present"
    return 0
  fi
  echo "Installing libzstd runtime for Kafka zstd decode..."
  if command -v apt-get >/dev/null 2>&1; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y libzstd1
  elif command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y libzstd
  elif command -v yum >/dev/null 2>&1; then
    sudo yum install -y libzstd
  else
    echo "WARNING: install libzstd manually; node-rdkafka may fail on zstd batches without libzstd.so"
  fi
}
ensure_libzstd

# cloud-init runs as root — pm2 matches. Order: start → save → startup (systemd + resurrect).
# Some pm2 builds print a line starting with "sudo " — eval it to enable the unit.
echo "Starting $APPLICATION_NAME via pm2..."
set -a
# shellcheck disable=SC1090
source /etc/pulse/ingestion.env
set +a

pm2 delete "$APPLICATION_NAME" 2>/dev/null || true
pm2 start "$INSTALL_DIR/dist/index.js" --name "$APPLICATION_NAME"
pm2 save

STARTUP_OUTPUT="$(pm2 startup systemd -u root --hp /root 2>&1)" || true
echo "$STARTUP_OUTPUT"
START_CMD="$(echo "$STARTUP_OUTPUT" | grep -E '^sudo ' | tail -n1 || true)"
if [ -n "$START_CMD" ]; then
  eval "$START_CMD"
fi

sleep 5

if pm2 list | grep -q "$APPLICATION_NAME"; then
  echo "Service started successfully via pm2"
else
  echo "WARNING: pm2 process may not have started. Checking logs:"
  pm2 logs "$APPLICATION_NAME" --lines 30 --nostream || true
fi

echo "User-data complete at $(date)"
echo "View logs: pm2 logs $APPLICATION_NAME"
