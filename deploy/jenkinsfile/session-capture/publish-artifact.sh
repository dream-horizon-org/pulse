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

APPLICATION_NAME="pulse-session-capture"
ZIP_NAME="${APPLICATION_NAME}-${VERSION}.zip"
ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"

rm -rf "${ROOT_DIR}/artifact"
mkdir -p "${ROOT_DIR}/artifact"

echo "Building ${APPLICATION_NAME} (release) version ${VERSION}"
cd "${ROOT_DIR}/backend/session-capture-service"
cargo build --release

mkdir -p "${ROOT_DIR}/artifact/${APPLICATION_NAME}"
cp "target/release/${APPLICATION_NAME}" "${ROOT_DIR}/artifact/${APPLICATION_NAME}/"

cd "${ROOT_DIR}/artifact"
zip -q -r "${ZIP_NAME}" "${APPLICATION_NAME}"

echo "Uploading ${ZIP_NAME} to AWS CodeArtifact..."
AWS_REGION="ap-south-1"
CODEARTIFACT_DOMAIN="pulse-prod"
CODEARTIFACT_REPOSITORY="pulse-session-capture"
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
