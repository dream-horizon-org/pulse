#!/bin/bash
set -euo pipefail

# Load NVM environment
export NVM_DIR="/home/admin/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm use 20 || nvm install 20

while getopts v: flag; do
  case "${flag}" in
    v) VERSION=${OPTARG} ;;
    *) exit 1 ;;
  esac
done

if [ -z "${VERSION:-}" ]; then
  echo 'Missing option -v (artifact version)' >&2
  exit 1
fi

APPLICATION_NAME="pulse-session-replay-ingestion"
ZIP_NAME="${APPLICATION_NAME}-${VERSION}.zip"
ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
APP_DIR="${ROOT_DIR}/backend/session-replay-ingestion"

rm -rf "${ROOT_DIR}/artifact"
mkdir -p "${ROOT_DIR}/artifact"

echo "Building ${APPLICATION_NAME} version ${VERSION}"
cd "${APP_DIR}"

# Install system librdkafka if not present
if ! pkg-config --exists librdkafka 2>/dev/null; then
  echo "Installing librdkafka-dev..."
  # Remove bad PPA sources before apt update
  sudo rm -f /etc/apt/sources.list.d/*deadsnakes* 2>/dev/null || true
  sudo apt-get update -qq 2>&1 | grep -v "^E:" || true
  sudo apt-get install -y librdkafka-dev build-essential pkg-config libssl-dev libcurl4-openssl-dev libsasl2-dev 2>&1 | tail -20
fi

# Build node-rdkafka from source against system librdkafka
echo "=== npm build configuration ==="
export npm_config_build_from_source=true
export npm_config_librdkafka_root=/usr
export npm_config_loglevel=verbose
echo "npm_config_build_from_source=$npm_config_build_from_source"
echo "npm_config_librdkafka_root=$npm_config_librdkafka_root"
echo "npm_config_loglevel=$npm_config_loglevel"

echo "=== system librdkafka info ==="
pkg-config --cflags --libs librdkafka || echo "pkg-config query failed"
ls -la /usr/lib/x86_64-linux-gnu/librdkafka* 2>/dev/null || echo "No system librdkafka found"

echo "=== clearing npm cache ==="
npm cache clean --force

echo "=== npm install with verbose output ==="
npm install --build-from-source 2>&1 | tee npm-install.log

echo "=== checking node-rdkafka.node linkage ==="
RDKAFKA_NODE=$(find node_modules/node-rdkafka -name "*.node" -type f 2>/dev/null | head -1)
if [ -n "$RDKAFKA_NODE" ]; then
  echo "Found: $RDKAFKA_NODE"
  ldd "$RDKAFKA_NODE" 2>/dev/null | tee rdkafka-ldd.log || echo "ldd failed (32-bit binary?)"
  strings "$RDKAFKA_NODE" | grep -i rdkafka | head -20 | tee rdkafka-strings.log || echo "strings grep found nothing"
else
  echo "ERROR: node-rdkafka.node not found!"
  find node_modules -name "*.node" -type f | head -10
fi

echo "=== npm run build ==="
npm run build

echo "=== npm prune for production ==="
npm prune --production

STAGE="${ROOT_DIR}/artifact/${APPLICATION_NAME}"
rm -rf "${STAGE}"
mkdir -p "${STAGE}"
cp -r dist node_modules package.json "${STAGE}/"

cd "${ROOT_DIR}/artifact"
zip -q -r "${ZIP_NAME}" "${APPLICATION_NAME}"

echo "Uploading ${ZIP_NAME} to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-session-replay-ingestion"
FILE_HASH=$(sha256sum "${ZIP_NAME}" | awk '{ print $1 }')

aws codeartifact publish-package-version \
  --region "${AWS_REGION}" \
  --domain "${CODEARTIFACT_DOMAIN}" \
  --repository "${CODEARTIFACT_REPOSITORY}" \
  --format generic \
  --namespace "pulse" \
  --package "${APPLICATION_NAME}" \
  --package-version "${VERSION}" \
  --asset-name "${ZIP_NAME}" \
  --asset-content "${ZIP_NAME}" \
  --asset-sha256 "${FILE_HASH}"

echo "Upload successful: ${APPLICATION_NAME}:${VERSION} (${ZIP_NAME})"
