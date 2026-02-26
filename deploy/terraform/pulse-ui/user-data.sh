#!/bin/bash
set -e  # Exit on error

# 1. Setup Logging
exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

echo "Changing to home directory"
cd /home/admin

# 2. Activating NVM
echo "Installing Node and PM2"
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.4/install.sh | bash
export NVM_DIR="$HOME/.nvm"
# Load nvm function into the current shell session
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
nvm install 20
nvm use 20
npm install -g pm2
which pm2
npm install -g yarn
which yarn

# 3. Download code artifact
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-ui"
APPLICATION_NAME="pulse-ui"
VERSION=${artifact_version}

aws codeartifact get-package-version-asset \
  --region "$AWS_REGION" \
  --domain "$CODEARTIFACT_DOMAIN" \
  --repository "$CODEARTIFACT_REPOSITORY" \
  --format generic \
  --namespace "pulse" \
  --package "$APPLICATION_NAME" \
  --package-version "$VERSION" \
  --asset "pulse-ui-$VERSION.zip" \
  pulse-ui.zip

# 4. Unzip artifact
unzip pulse-ui.zip

# 7. Start & Persist Application with PM2
cd pulse-ui
echo "### Starting pulse dashboard with PM2..."
# Assuming your pm2-prod.json is relative to pulse/pulse-ui
pm2 start "pm2/pm2-prod.json" -i 1

# This makes sure PM2 restarts the app if the EC2 instance reboots
#pm2 save
#pm2 startup systemd -u root --hp /root
#env PATH=$PATH:/usr/bin /usr/lib/node_modules/pm2/bin/pm2 startup systemd -u root --hp /root

echo "### User-data script completed at $(date)"