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
# Env file is written after artifact install so APP_ROOT can be replaced cleanly.
# -------------------------------------------------------------------
SECRET_NAME="prod/pulse-session-replay-ingestion/appenv"
APP_ROOT="$HOME/pulse-session-replay-ingestion"
ENV_FILE="$APP_ROOT/ingestion.env"

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

AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-session-replay-ingestion"
APPLICATION_NAME="pulse-session-replay-ingestion"
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

UNZIP_TMP=$(mktemp -d)
unzip -o "$APPLICATION_NAME.zip" -d "$UNZIP_TMP"
if [ ! -f "$UNZIP_TMP/$APPLICATION_NAME/dist/index.js" ]; then
  echo "ERROR: dist/index.js not found under $UNZIP_TMP/$APPLICATION_NAME/"
  exit 1
fi

sudo rm -rf "$APP_ROOT"
sudo mv "$UNZIP_TMP/$APPLICATION_NAME" "$APP_ROOT"
sudo rm -rf "$UNZIP_TMP"

echo "$SECRET_JSON" | jq -r '.app_env[] | "\(.key)=\(.value)"' | sudo tee "$ENV_FILE" >/dev/null
sudo chmod 600 "$ENV_FILE"
echo "Exported $(wc -l < "$ENV_FILE") environment variables to $ENV_FILE"

sudo chown -R admin:admin "$APP_ROOT"

# Run pm2 as admin (not root) so .pm2/ is admin-owned and the admin user can manage the service.
# cloud-init is root, so we use a temp script + sudo -u admin to switch users cleanly.
echo "Starting $APPLICATION_NAME via pm2..."

PM2_START_SCRIPT=$(mktemp)
cat > "$PM2_START_SCRIPT" << INNERSCRIPT
#!/bin/bash
export NVM_DIR="/home/admin/.nvm"
[ -s "\$NVM_DIR/nvm.sh" ] && . "\$NVM_DIR/nvm.sh"
nvm use 20
set -a
source "$${ENV_FILE}"
set +a
pm2 delete "$${APPLICATION_NAME}" 2>/dev/null || true
cd "$${APP_ROOT}"
pm2 start dist/index.js --name "$${APPLICATION_NAME}" --node-args="--require @opentelemetry/auto-instrumentations-node/register"
pm2 save
INNERSCRIPT
chmod +x "$PM2_START_SCRIPT"
sudo -u admin bash "$PM2_START_SCRIPT"
rm -f "$PM2_START_SCRIPT"

# Enable pm2 auto-restart on reboot for admin user
PM2_STARTUP_SCRIPT=$(mktemp)
cat > "$PM2_STARTUP_SCRIPT" << INNERSCRIPT
#!/bin/bash
export NVM_DIR="/home/admin/.nvm"
[ -s "\$NVM_DIR/nvm.sh" ] && . "\$NVM_DIR/nvm.sh"
nvm use 20
pm2 startup systemd -u admin --hp /home/admin 2>&1
INNERSCRIPT
chmod +x "$PM2_STARTUP_SCRIPT"
STARTUP_OUTPUT="$(sudo -u admin bash "$PM2_STARTUP_SCRIPT")" || true
rm -f "$PM2_STARTUP_SCRIPT"
echo "$STARTUP_OUTPUT"
START_CMD="$(echo "$STARTUP_OUTPUT" | grep -E '^sudo ' | tail -n1 || true)"
if [ -n "$START_CMD" ]; then
  eval "$START_CMD"
fi

sleep 5

if sudo -u admin bash -c "export NVM_DIR=/home/admin/.nvm; . \$NVM_DIR/nvm.sh; nvm use 20 >/dev/null; pm2 list" | grep -q "$APPLICATION_NAME"; then
  echo "Service started successfully via pm2"
else
  echo "WARNING: pm2 process may not have started. Checking logs:"
  sudo -u admin bash -c "export NVM_DIR=/home/admin/.nvm; . \$NVM_DIR/nvm.sh; nvm use 20 >/dev/null; pm2 logs $APPLICATION_NAME --lines 30 --nostream" || true
fi

echo "User-data complete at $(date)"
echo "View logs: pm2 logs $APPLICATION_NAME"
