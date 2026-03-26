#!/usr/bin/env bash
#
# ADB loop — OpenTelemetry Android demo (Astronomy Shop). Fixed pixel coordinates only
# (tune once for your device; no scaling).
#
# Calibrate: Developer options → Pointer location, note x/y while tapping each target,
# then set the variables below or export them when running.
#
# Each cycle (NUM_ITERATIONS, default 100):
#   MainActivity (-S) → Go shopping → scroll list → tap card → scroll detail →
#   Add to Cart → back → scroll list → back → repeat
#
# Usage:
#   ./benchmark/flashlight_card_scroll_nav_adb.sh
#
# Default coordinates: Oppo CPH2185, adb wm size 720×1600, calibrated with Pointer location.
# Edit the defaults below if you change device or theme scale.

set -euo pipefail

NUM_ITERATIONS="${NUM_ITERATIONS:-100}"
SWIPE_MS="${SWIPE_MS:-350}"
PAUSE_SEC="${PAUSE_SEC:-0.3}"
ITER_PAUSE_SEC="${ITER_PAUSE_SEC:-0.2}"

MAIN_ACTIVITY="${MAIN_ACTIVITY:-io.opentelemetry.android.demo/.MainActivity}"
LAUNCH_SETTLE_SEC="${LAUNCH_SETTLE_SEC:-1.8}"
RESET_MAIN_EACH_ITER="${RESET_MAIN_EACH_ITER:-1}"

SHOP_LOAD_SEC="${SHOP_LOAD_SEC:-2.0}"
DETAIL_LOAD_SEC="${DETAIL_LOAD_SEC:-1.2}"
AFTER_ADD_SEC="${AFTER_ADD_SEC:-0.5}"
DETAIL_SCROLL_SWIPES="${DETAIL_SCROLL_SWIPES:-2}"

# --- Scenario: Main → “Go shopping” (pointer ~409, 1189 on 720×1600) ---
GO_SHOPPING_X="${GO_SHOPPING_X:-409}"
GO_SHOPPING_Y="${GO_SHOPPING_Y:-1189}"

# --- Scenario: Astronomy Shop list → product card (e.g. Solar System … ~356, 880) ---
PRODUCT_CARD_X="${PRODUCT_CARD_X:-356}"
PRODUCT_CARD_Y="${PRODUCT_CARD_Y:-880}"

# --- Scenario: Product detail → purple “Add to Cart” (below description; ~360×1370) ---
ADD_TO_CART_X="${ADD_TO_CART_X:-360}"
ADD_TO_CART_Y="${ADD_TO_CART_Y:-1370}"

# --- Scrolls: Y must stay within 0..1599 on 720×1600 ---
# List: short nudge (finger moves up)
SCROLL_LIST_X="${SCROLL_LIST_X:-360}"
SCROLL_LIST_Y_FROM="${SCROLL_LIST_Y_FROM:-1150}"
SCROLL_LIST_Y_TO="${SCROLL_LIST_Y_TO:-850}"

# Detail: longer swipe to bring Add to Cart into view
SCROLL_DETAIL_X="${SCROLL_DETAIL_X:-360}"
SCROLL_DETAIL_Y_FROM="${SCROLL_DETAIL_Y_FROM:-1300}"
SCROLL_DETAIL_Y_TO="${SCROLL_DETAIL_Y_TO:-720}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

adb_cmd() {
  adb "$@"
}

print_wm_size() {
  local line
  line=$(adb_cmd shell wm size 2>/dev/null | head -1 || true)
  echo -e "${GREEN}adb wm size: ${line:-unknown}${NC}"
}

tap_xy() {
  adb_cmd shell input tap "$1" "$2"
}

swipe_xy() {
  # $1 $2 = start, $3 $4 = end
  adb_cmd shell input swipe "$1" "$2" "$3" "$4" "$SWIPE_MS"
}

scroll_list_nudge() {
  swipe_xy "$SCROLL_LIST_X" "$SCROLL_LIST_Y_FROM" "$SCROLL_LIST_X" "$SCROLL_LIST_Y_TO"
}

scroll_detail_down() {
  swipe_xy "$SCROLL_DETAIL_X" "$SCROLL_DETAIL_Y_FROM" "$SCROLL_DETAIL_X" "$SCROLL_DETAIL_Y_TO"
}

press_back() {
  adb_cmd shell input keyevent 4
}

open_main_activity() {
  if [[ "${RESET_MAIN_EACH_ITER}" == "1" ]]; then
    adb_cmd shell am start -W -S -n "$MAIN_ACTIVITY" >/dev/null
  else
    adb_cmd shell am start -W -n "$MAIN_ACTIVITY" >/dev/null
  fi
  sleep "$LAUNCH_SETTLE_SEC"
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Shop flow × ${NUM_ITERATIONS} (fixed pixel coords)${NC}"
echo -e "${BLUE}========================================${NC}"

if ! adb_cmd devices 2>/dev/null | grep -qE $'\tdevice$'; then
  echo -e "${RED}No adb device.${NC}" >&2
  exit 1
fi

echo -e "${GREEN}Device: $(adb_cmd devices | grep -E $'\tdevice$' | head -1 | awk '{print $1}')${NC}"
print_wm_size

echo ""
echo "Pixels (edit script or export to tune):"
echo "  Go shopping     : ${GO_SHOPPING_X},${GO_SHOPPING_Y}"
echo "  Product card    : ${PRODUCT_CARD_X},${PRODUCT_CARD_Y}"
echo "  Add to cart     : ${ADD_TO_CART_X},${ADD_TO_CART_Y}"
echo "  Scroll list     : (${SCROLL_LIST_X},${SCROLL_LIST_Y_FROM}) → (${SCROLL_LIST_X},${SCROLL_LIST_Y_TO})"
echo "  Scroll detail   : (${SCROLL_DETAIL_X},${SCROLL_DETAIL_Y_FROM}) → (${SCROLL_DETAIL_X},${SCROLL_DETAIL_Y_TO})"
echo "RESET_MAIN_EACH_ITER=${RESET_MAIN_EACH_ITER}"
echo ""

START_TS=$(date +%s)
for i in $(seq 1 "$NUM_ITERATIONS"); do
  echo -e "${YELLOW}── Cycle ${i}/${NUM_ITERATIONS} ──${NC}"

  open_main_activity

  tap_xy "$GO_SHOPPING_X" "$GO_SHOPPING_Y"
  sleep "$SHOP_LOAD_SEC"

  scroll_list_nudge
  sleep "$PAUSE_SEC"

  tap_xy "$PRODUCT_CARD_X" "$PRODUCT_CARD_Y"
  sleep "$DETAIL_LOAD_SEC"

  for ((s = 0; s < DETAIL_SCROLL_SWIPES; s++)); do
    scroll_detail_down
    sleep "$PAUSE_SEC"
  done

  tap_xy "$ADD_TO_CART_X" "$ADD_TO_CART_Y"
  sleep "$AFTER_ADD_SEC"

  press_back
  sleep "$PAUSE_SEC"

  scroll_list_nudge
  sleep "$PAUSE_SEC"

  press_back
  sleep "$PAUSE_SEC"

  if (( i % 10 == 0 )); then
    ELAPSED=$(($(date +%s) - START_TS))
    echo -e "${GREEN}  ${i}/${NUM_ITERATIONS} done (~${ELAPSED}s)${NC}"
  fi
  sleep "$ITER_PAUSE_SEC"
done

echo ""
echo -e "${GREEN}Finished ${NUM_ITERATIONS} cycles.${NC}"
