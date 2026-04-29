#!/bin/bash
set -euo pipefail

exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting session-ingestion user-data at $(date)"

export HOME=/home/admin
cd "$HOME" || cd /root

echo "Installing jq (Secrets Manager JSON parse)..."
sudo rm -f /etc/apt/sources.list.d/deadsnakes-ppa-*.list || true
sudo apt-get update -qq 2>/dev/null || true
sudo apt-get install -y -qq jq 2>/dev/null || true

# Node.js + pm2 via nvm — same placement as deploy/terraform/pulse-ui/user-data.sh (toolchain before artifact).
# Requires curl on the AMI (nvm installer + Node download). No corepack/yarn — runtime only.
echo "Installing Node 20 and pm2 via nvm..."
if [ ! -s "$HOME/.nvm/nvm.sh" ]; then
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.4/install.sh | bash
fi
export NVM_DIR="$HOME/.nvm"
# shellcheck disable=SC1090
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm install 20
nvm use 20
if ! command -v pm2 >/dev/null 2>&1; then
  npm install -g pm2
fi
echo "node $(node --version) pm2 $(pm2 --version)"

# -------------------------------------------------------------------
# App environment from AWS Secrets Manager (same contract as pulse-server /
# session-capture: { "app_env": [ { "key": "...", "value": "..." }, ... ] })
# Keys match backend/session-replay-ingestion/src/config.ts (KAFKA_*, S3_*, etc.).
# -------------------------------------------------------------------
SECRET_NAME="prod/pulse-session-replay-ingestion/appenv"
ENV_FILE="/etc/pulse/ingestion.env"

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

# Ensure nvm node/pm2 are on PATH for this shell (same session as above).
export NVM_DIR="$HOME/.nvm"
# shellcheck disable=SC1090
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm use 20

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
