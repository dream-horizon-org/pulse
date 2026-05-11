#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting heatmap-ingestion user-data at $(date)"

export HOME=/home/admin
cd "$HOME"

AWS_REGION="ap-south-1"
SECRET_NAME="prod/pulse-heatmap-screenshot-ingestion/appenv"
# Under /home/admin only (explicit $HOME like ARTIFACT_ZIP / INSTALL_DIR).
ENV_FILE="$HOME/.heatmap-ingestion.env"

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

# Secret format matches pulse-server / pulse-alerts-cron: { "app_env": [ { "key": "...", "value": "..." } ] }
# Use stdlib json (AMI may not ship jq).
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 not found; cannot parse appenv secret"
  exit 1
fi
echo "$SECRET_JSON" | python3 -c 'import json, sys
data = json.load(sys.stdin)
for item in data.get("app_env", []):
    k = item.get("key") or ""
    v = item.get("value", "")
    if k:
        print("%s=%s" % (k, v))
' | sudo -u admin tee "$ENV_FILE" >/dev/null

sudo -u admin chmod 600 "$ENV_FILE"

CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-heatmap-screenshot-ingestion"
APPLICATION_NAME="pulse-heatmap-screenshot-ingestion"
VERSION="${artifact_version}"
# Same pattern as pulse-server: artifact zip under $HOME, unzip creates $HOME/$APPLICATION_NAME/.
INSTALL_DIR="$HOME/$APPLICATION_NAME"
ARTIFACT_ZIP="$HOME/${APPLICATION_NAME}.zip"

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
  "$ARTIFACT_ZIP"

unzip -o "$ARTIFACT_ZIP"
if [ ! -f "$INSTALL_DIR/dist/index.js" ]; then
  echo "ERROR: dist/index.js not found under $INSTALL_DIR/"
  exit 1
fi


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

# cloud-init runs as root — pm2 matches. Order: start → save (dump) → startup (systemd + resurrect).
# Some pm2 builds print a line starting with "sudo " — eval it to enable the unit.
echo "Starting $APPLICATION_NAME via pm2..."
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

pm2 delete "$APPLICATION_NAME" 2>/dev/null || true
pm2 start "$INSTALL_DIR/dist/index.js" --name "$APPLICATION_NAME" --node-args="--require @opentelemetry/auto-instrumentations-node/register"
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
