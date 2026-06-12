#!/bin/bash

###############################################################################
# Compose a JS and HBC sourcemap
#
# Usage:
# ./compose-sourcemaps.sh <path-to-js-sourcemap> <path-to-hbc-sourcemap> <path-to-output-sourcemap>
#
###############################################################################

if [[ -z "$1" || -z "$2" || -z "$3" ]]; then
  echo "Missing inputs. Please refer to the script for documentation."
  exit 1
else
  JS_SOURCE_MAP="$1"
  HBC_SOURCE_MAP="$2"
  OUTPUT_SOURCE_MAP="$3"
fi

COMPOSE_SCRIPT="node_modules/react-native/scripts/compose-source-maps.js"

node ${COMPOSE_SCRIPT} ${JS_SOURCE_MAP} ${HBC_SOURCE_MAP} -o ${OUTPUT_SOURCE_MAP}
