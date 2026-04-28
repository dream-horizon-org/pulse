#!/bin/bash
set -euo pipefail

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

# Same idea as deploy/jenkinsfile/pulse-ui/publish-artifact.sh: source nvm so npm exists (non-login Jenkins sh).
# Extra: default HOME for agents where it is unset; pin Node 20 (matches typical nvm on artifact fleet).
echo "Sourcing NVM directly"
export HOME="${HOME:-/home/admin}"
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ ! -s "${NVM_DIR}/nvm.sh" ]; then
  echo "nvm not found at ${NVM_DIR}/nvm.sh (set HOME or NVM_DIR)" >&2
  exit 1
fi
# shellcheck source=/dev/null
. "${NVM_DIR}/nvm.sh"
if ! nvm use 20 --silent 2>/dev/null; then
  nvm install 20
  nvm use 20
fi

rm -rf "${ROOT_DIR}/artifact"
mkdir -p "${ROOT_DIR}/artifact"

echo "Building ${APPLICATION_NAME} version ${VERSION}"
cd "${APP_DIR}"
npm install
npm run build
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
