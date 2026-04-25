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

# Build node-rdkafka from source using its bundled librdkafka (statically linked).
# Do NOT set npm_config_librdkafka_root — using the system librdkafka causes
# version mismatches between build and deploy hosts (ERR__NOT_IMPLEMENTED at runtime).
# Build toolchain is still needed for native compilation.
sudo rm -f /etc/apt/sources.list.d/*deadsnakes* 2>/dev/null || true
sudo apt-get update -qq 2>&1 | grep -v "^E:" || true
sudo apt-get install -y build-essential pkg-config libssl-dev libcurl4-openssl-dev libsasl2-dev 2>&1 | tail -20

export npm_config_build_from_source=true

echo "=== clearing npm cache ==="
npm cache clean --force

echo "=== npm install ==="
npm install --build-from-source 2>&1 | tee npm-install.log

echo "=== checking node-rdkafka.node ==="
RDKAFKA_NODE=$(find node_modules/node-rdkafka -name "*.node" -type f 2>/dev/null | head -1)
if [ -n "$RDKAFKA_NODE" ]; then
  echo "Found: $RDKAFKA_NODE"
  ldd "$RDKAFKA_NODE" 2>/dev/null || echo "ldd failed"
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
