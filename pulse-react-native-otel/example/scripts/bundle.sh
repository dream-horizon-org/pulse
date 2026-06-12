#!/bin/bash

###############################################################################
# Generate a JS bundle and assets, compile to HBC and optionally emit sourcemap
#
# Usage:
# ./bundle.sh [options]
#
# The script expects the caller to set a few environment variables
#
# [Required]
# 1. BUNDLE_PATH: Directory to place the bundle in
# 2. ASSETS_PATH: Directory to place assets (images etc) in
# 3. PLATFORM: Can be either "android" or "ios"
#
# [Optional]
# 4. MAKE_SOURCEMAP: Set if a sourcemap is required. Any value will do.
#
# Any other options passed as flags would be handed over to `react-native bundle`
# as it is. For example: `./bundle.sh --dev false`
###############################################################################

set -e

#####################################################################
# Validate inputs

if [[ -z ${BUNDLE_PATH} || -z ${ASSETS_PATH} || -z ${PLATFORM} ]]; then
  echo "Missing inputs. Please refer to the script for documentation."
  exit 1
fi

#####################################################################
# Generate JS bundle

if [ ${PLATFORM} = "ios" ]; then
  JS_BUNDLE_FILE="${BUNDLE_PATH}/main.jsbundle"
fi

if [ ${PLATFORM} = "android" ]; then
  JS_BUNDLE_FILE="${BUNDLE_PATH}/index.${PLATFORM}.bundle"
fi

JS_SOURCEMAP_FILE="${SOURCE_MAP_PATH}/packager.${PLATFORM}.json"
JS_SOURCEMAP_FLAGS=""
if ! [[ -z "${MAKE_SOURCEMAP}" ]]; then
  JS_SOURCEMAP_FLAGS="--sourcemap-output ${JS_SOURCEMAP_FILE}"
fi

yarn react-native bundle --entry-file ${ENTRY_FILE} --platform ${PLATFORM} --dev false --bundle-output ${JS_BUNDLE_FILE} ${JS_SOURCEMAP_FLAGS} --assets-dest ${ASSETS_PATH} --minify false "${@:1}";
echo "Wrote JS output to: ${JS_BUNDLE_FILE}"

if ! [[ -z "${MAKE_SOURCEMAP}" ]]; then
  echo "Wrote JS sourcemap to: ${JS_SOURCEMAP_FILE}"
fi

#####################################################################
# Generate HBC bundle

HBC_TEMP_FILE="${JS_BUNDLE_FILE}.hbc"
HBC_SOURCEMAP_FILE="${JS_BUNDLE_FILE}.hbc.map" # NOTE: This is by convention always true

scripts/bundle-to-binary.sh ${JS_BUNDLE_FILE} ${HBC_TEMP_FILE} ${PLATFORM} ${MAKE_SOURCEMAP}

echo "Replacing JS bundle with HBC"
mv ${HBC_TEMP_FILE} ${JS_BUNDLE_FILE}

#####################################################################
# Compose sourcemaps if required

if ! [[ -z "${MAKE_SOURCEMAP}" ]]; then
  COMPOSED_SOURCEMAP_FILE="${JS_BUNDLE_FILE}.json"

  scripts/compose-sourcemaps.sh ${JS_SOURCEMAP_FILE} ${HBC_SOURCEMAP_FILE} ${COMPOSED_SOURCEMAP_FILE}
  echo "Wrote compose output ${COMPOSED_SOURCEMAP_FILE}"
  # These ones are no longer required
  rm ${JS_SOURCEMAP_FILE} ${HBC_SOURCEMAP_FILE} 
fi

# comment out this line to keep the assets and bundle files for debugging
rm -rf ${ASSETS_PATH} ${JS_BUNDLE_FILE} 