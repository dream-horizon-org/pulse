#!/bin/bash
set -e  # Exit on error

# 1. Setup Logging
exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# 2. Install Node.js 20.x and Build Essentials
echo "### Installing Node.js and dependencies..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt-get update && apt-get install -y nodejs git rsync build-essential

# 3. Install Global Packages
echo "### Installing Yarn and PM2..."
npm install -g yarn pm2

# 4. Clone Repository
echo "### Cloning pulse repository to /opt/pulse..."
mkdir -p /opt
cd /opt

if [ -d "pulse" ]; then
    rm -rf pulse
fi
git clone https://github.com/dream-horizon-org/pulse.git
cd pulse/pulse-ui

# 5. Handle Environment Variables (Terraform Template)
echo "### Setting up .env file..."
ENV_FILE=".env"
cat <<EOF > $ENV_FILE
%{ for env_var in env_vars ~}
${env_var.key}=${env_var.value}
%{ endfor ~}
EOF

# 6. Build Application
echo "### Installing project dependencies..."
export NODE_OPTIONS=--max_old_space_size=4096
yarn install

echo "### Building the dashboard..."
NODE_ENV=production PORT=3000 yarn build

# 7. Start & Persist Application with PM2
echo "### Starting pulse dashboard with PM2..."
# Assuming your pm2-prod.json is relative to pulse/pulse-ui
pm2 start "pm2/pm2-prod.json" -i 1

# This makes sure PM2 restarts the app if the EC2 instance reboots
pm2 save
pm2 startup systemd -u root --hp /root
env PATH=$PATH:/usr/bin /usr/lib/node_modules/pm2/bin/pm2 startup systemd -u root --hp /root

echo "### User-data script completed at $(date)"