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

# build-essential is needed for snappy native addon compilation
sudo rm -f /etc/apt/sources.list.d/*deadsnakes* 2>/dev/null || true
sudo apt-get update -qq 2>&1 | grep -v "^E:" || true
sudo apt-get install -y build-essential 2>&1 | tail -5

echo "=== clearing npm cache ==="
npm cache clean --force

echo "=== npm install ==="
npm install 2>&1 | tee npm-install.log

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
