#!/bin/bash
set -e  # Exit on error

# -------------------------------------------------------------------
# 1. Setup Logging
# -------------------------------------------------------------------
exec > >(sudo tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# -------------------------------------------------------------------
# 2. Setting home directory
# -------------------------------------------------------------------
export HOME=/home/admin
echo "Changing to home directory"
cd $HOME
pwd

# -------------------------------------------------------------------
# 3. Handle Environment Variables
# -------------------------------------------------------------------
SECRET_NAME="prod/pulseserver/appenv"
ENV_FILE=".pulse-server.env"

echo "Fetching secret '$SECRET_NAME' from AWS Secrets Manager..."
# Fetch the secret string from AWS Secrets Manager
SECRET_JSON=$(aws secretsmanager get-secret-value \
  --secret-id "$SECRET_NAME" \
  --query SecretString \
  --output text)

if [ -z "$SECRET_JSON" ]; then
  echo "Error: SECRET_JSON is empty"
  exit 1
fi

# Write secrets to .env file
touch "$ENV_FILE"
echo "$SECRET_JSON" | jq -r '.app_env[] | "\(.key)=\(.value)"' > "$ENV_FILE"
chmod 600 "$ENV_FILE"

echo "Exported $(wc -l < $ENV_FILE) environment variables to $HOME/.pulse-server.env"

# -------------------------------------------------------------------
# 4. Download code artifact
# -------------------------------------------------------------------
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-server"
APPLICATION_NAME="pulse-server"
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
  "$APPLICATION_NAME".zip

# -------------------------------------------------------------------
# 5. Unzip and Verify artifact
# -------------------------------------------------------------------
unzip "$APPLICATION_NAME".zip
APP_DIR="$HOME/$APPLICATION_NAME"

if [ ! -f "$APPLICATION_NAME/$APPLICATION_NAME.jar" ]; then
    echo "ERROR: JAR file not found at $APP_DIR"
    exit 1
fi

# -------------------------------------------------------------------
# 6. Start Application
# -------------------------------------------------------------------
echo "Starting pulse-server"
set -a
source "$ENV_FILE"
set +a

nohup java \
    -Dlogback.configurationFile=$APPLICATION_NAME/logback/logback.xml \
    -jar "$APPLICATION_NAME/$APPLICATION_NAME".jar \
    run org.dreamhorizon.pulseserver.verticle.MainVerticle \
    > "$APPLICATION_NAME".log 2>&1 &

echo $! > "$APPLICATION_NAME".pid

sleep 10

if ps -p $(cat "$APPLICATION_NAME".pid) > /dev/null 2>&1; then
    echo "$APPLICATION_NAME started successfully (PID: $(cat $APPLICATION_NAME.pid))"

    echo "Waiting for application to be ready..."
    sleep 15

    if netstat -tlnp 2>/dev/null | grep -q ':8080' || ss -tlnp 2>/dev/null | grep -q ':8080'; then
        echo "Application is listening on port 8080"
    else
        echo "Warning: Port 8080 may not be listening yet"
    fi
else
    echo "Warning: $APPLICATION_NAME may not have started properly"
    echo "Check logs at: $HOME/$APPLICATION_NAME.log"
    if [ -f "$APPLICATION_NAME".log ]; then
        echo "Recent log output:"
        tail -n 50 "$APPLICATION_NAME".log || true
    fi
fi

echo "Log file: $HOME/$APPLICATION_NAME.log"
echo "PID file: $HOME/$APPLICATION_NAME.pid"
echo "User-data script completed at $(date)"