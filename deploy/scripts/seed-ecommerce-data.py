"""
Generates comprehensive e-commerce telemetry data for Pulse.

Simulates a large Indian e-commerce platform with 12 critical interactions,
realistic device/network/geo distributions, and intentional bad segments
per interaction for the AI root cause engine to discover.

Usage:
    python3 deploy/scripts/seed-ecommerce-data.py [--clear]
    --clear  Wipe existing data before seeding
"""

import random
import uuid
import sys
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone

# ─── ClickHouse connection ────────────────────────────────────────────────────
import os
CH_HOST = os.environ.get("CH_HOST", "127.0.0.1")
CH_PORT = os.environ.get("CH_PORT", "8123")
CH_USER = os.environ.get("CH_USER", "pulse_user")
CH_PASSWORD = os.environ.get("CH_PASSWORD", "pulse_password")
CH_DB = os.environ.get("CH_DB", "otel")

# ─── MySQL connection ────────────────────────────────────────────────────────
MYSQL_HOST = os.environ.get("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = os.environ.get("MYSQL_PORT", "3307")
MYSQL_USER = os.environ.get("MYSQL_USER", "pulse_user")
MYSQL_PASSWORD = os.environ.get("MYSQL_PASSWORD", "pulse_password")
MYSQL_DB = os.environ.get("MYSQL_DB", "pulse_db")
MYSQL_MODE = os.environ.get("MYSQL_MODE", "docker")  # "docker" = docker exec, "direct" = mysql client

random.seed(2026)

# ─── Helpers ──────────────────────────────────────────────────────────────────

def ch_query(query):
    url = f"http://{CH_HOST}:{CH_PORT}/?database={CH_DB}&user={CH_USER}&password={CH_PASSWORD}"
    req = urllib.request.Request(url, data=query.encode("utf-8"))
    try:
        resp = urllib.request.urlopen(req)
        return resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        print(f"  ClickHouse error ({e.code}): {body[:500]}")
        sys.exit(1)


def mysql_query(query):
    import subprocess
    if MYSQL_MODE == "direct":
        cmd = [
            "mysql", "-h", MYSQL_HOST, "-P", str(MYSQL_PORT),
            "-u", MYSQL_USER, f"-p{MYSQL_PASSWORD}", "--skip-ssl", MYSQL_DB,
            "-e", query
        ]
    else:
        cmd = [
            "docker", "exec", "pulse-mysql",
            "mysql", "-u", MYSQL_USER, f"-p{MYSQL_PASSWORD}", MYSQL_DB,
            "-e", query
        ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        stderr = result.stderr.replace("mysql: [Warning] Using a password on the command line interface can be insecure.\n", "")
        if stderr.strip():
            print(f"  MySQL error: {stderr.strip()}")
            sys.exit(1)
    return result.stdout


def wc(choices, weights):
    return random.choices(choices, weights=weights, k=1)[0]


def escape(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


# ─── Realistic Indian e-commerce data distributions ──────────────────────────

PLATFORMS = ["android", "ios"]
PLATFORM_WEIGHTS = [75, 25]  # India is heavily Android

ANDROID_VERSIONS = ["10", "11", "12", "13", "14"]
ANDROID_VERSION_WEIGHTS = [15, 20, 25, 25, 15]

IOS_VERSIONS = ["16.0", "16.4", "17.0", "17.2", "17.4"]
IOS_VERSION_WEIGHTS = [10, 15, 30, 25, 20]

APP_VERSIONS = ["4.0.0", "4.1.0", "4.2.0", "4.2.1", "4.3.0"]
APP_VERSION_WEIGHTS = [5, 10, 25, 35, 25]

ANDROID_DEVICES = [
    "SM-A135F",    # Samsung Galaxy A13 (budget)
    "SM-A546B",    # Samsung Galaxy A54 (mid)
    "SM-S911B",    # Samsung Galaxy S23 (flagship)
    "SM-M346B",    # Samsung Galaxy M34 (mid)
    "Pixel 7",
    "Pixel 8",
    "OnePlus 11",
    "Redmi Note 12",
    "Realme 10 Pro",
    "Vivo Y56",
    "POCO M5",
]
ANDROID_DEVICE_WEIGHTS = [18, 10, 5, 12, 4, 3, 6, 16, 10, 9, 7]

IOS_DEVICES = [
    "iPhone13,2",   # iPhone 12
    "iPhone14,5",   # iPhone 13
    "iPhone15,2",   # iPhone 14 Pro
    "iPhone15,4",   # iPhone 15
    "iPhone16,1",   # iPhone 15 Pro
]
IOS_DEVICE_WEIGHTS = [15, 25, 20, 25, 15]

NETWORKS = ["Jio", "Airtel", "Vi", "BSNL", "wifi"]
NETWORK_WEIGHTS = [32, 28, 12, 8, 20]

STATES = [
    "IN-MH", "IN-UP", "IN-KA", "IN-TN", "IN-DL",
    "IN-AP", "IN-RJ", "IN-GJ", "IN-WB", "IN-KL",
    "IN-MP", "IN-BR", "IN-HR",
]
STATE_WEIGHTS = [14, 13, 10, 9, 8, 8, 7, 7, 6, 5, 5, 4, 4]

SDK_VERSIONS = ["2.1.0", "2.2.0", "2.3.0"]
SDK_WEIGHTS = [15, 35, 50]

# ─── 12 E-commerce Interactions ──────────────────────────────────────────────

INTERACTIONS = [
    {
        "name": "app_launch",
        "description": "Cold start from tap to home screen fully rendered",
        "lower": 800, "mid": 1500, "upper": 2500, "threshold": 5000,
        "events": [
            {"name": "process_start", "screenName": "SplashScreen"},
            {"name": "home_rendered", "screenName": "HomeScreen"},
        ],
        "base_duration": (1100, 350),
        "base_error_rate": 0.03,
        "volume": 8000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: p == "android" and ov == "10" and n == "Jio",
                "duration": (3200, 700), "error_rate": 0.30, "crash": 0.10, "anr": 0.07, "frozen": (2, 8, 0.35),
            },
            {
                "match": lambda p, ov, d, n, s, av: p == "android" and ov == "10" and n == "Jio" and s == "IN-AP",
                "duration": (4200, 900), "error_rate": 0.45, "crash": 0.18, "anr": 0.12, "frozen": (5, 14, 0.55),
            },
            {
                "match": lambda p, ov, d, n, s, av: d == "SM-A135F",
                "duration": (2600, 500), "error_rate": 0.10, "crash": 0.02, "anr": 0.01, "frozen": (1, 5, 0.25),
            },
        ],
    },
    {
        "name": "home_feed_load",
        "description": "Loading the personalized home feed with product recommendations",
        "lower": 600, "mid": 1200, "upper": 2000, "threshold": 4000,
        "events": [
            {"name": "feed_request_start", "screenName": "HomeScreen"},
            {"name": "feed_rendered", "screenName": "HomeScreen"},
        ],
        "base_duration": (900, 280),
        "base_error_rate": 0.04,
        "volume": 7000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: n == "BSNL",
                "duration": (2800, 600), "error_rate": 0.22, "crash": 0.01, "anr": 0.03, "frozen": (2, 6, 0.30),
            },
            {
                "match": lambda p, ov, d, n, s, av: d == "Redmi Note 12" and p == "android",
                "duration": (2200, 450), "error_rate": 0.08, "crash": 0.03, "anr": 0.04, "frozen": (3, 10, 0.40),
            },
            {
                "match": lambda p, ov, d, n, s, av: av == "4.1.0",
                "duration": (1800, 400), "error_rate": 0.15, "crash": 0.06, "anr": 0.0, "frozen": (0, 0, 0.0),
            },
        ],
    },
    {
        "name": "product_search",
        "description": "User search query to search results fully rendered",
        "lower": 500, "mid": 1000, "upper": 1800, "threshold": 3500,
        "events": [
            {"name": "search_submitted", "screenName": "SearchScreen"},
            {"name": "results_rendered", "screenName": "SearchResultsScreen"},
        ],
        "base_duration": (750, 250),
        "base_error_rate": 0.03,
        "volume": 6000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: n == "BSNL" and s in ("IN-UP", "IN-BR", "IN-MP"),
                "duration": (3500, 800), "error_rate": 0.35, "crash": 0.02, "anr": 0.05, "frozen": (1, 4, 0.20),
            },
            {
                "match": lambda p, ov, d, n, s, av: p == "android" and ov == "11" and s == "IN-UP",
                "duration": (2400, 500), "error_rate": 0.25, "crash": 0.04, "anr": 0.06, "frozen": (2, 7, 0.30),
            },
        ],
    },
    {
        "name": "product_detail_view",
        "description": "Tapping a product card to full product detail page render",
        "lower": 400, "mid": 900, "upper": 1600, "threshold": 3000,
        "events": [
            {"name": "product_tap", "screenName": "FeedScreen"},
            {"name": "detail_rendered", "screenName": "ProductDetailScreen"},
        ],
        "base_duration": (650, 200),
        "base_error_rate": 0.025,
        "volume": 9000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: av == "4.1.0" and p == "android",
                "duration": (1800, 400), "error_rate": 0.12, "crash": 0.09, "anr": 0.02, "frozen": (1, 4, 0.15),
            },
            {
                "match": lambda p, ov, d, n, s, av: p == "android" and ov == "10",
                "duration": (2000, 500), "error_rate": 0.08, "crash": 0.03, "anr": 0.02, "frozen": (2, 6, 0.25),
            },
            {
                "match": lambda p, ov, d, n, s, av: d in ("Vivo Y56", "POCO M5") and n == "Vi",
                "duration": (2500, 600), "error_rate": 0.18, "crash": 0.05, "anr": 0.04, "frozen": (3, 9, 0.35),
            },
        ],
    },
    {
        "name": "add_to_cart",
        "description": "Add to cart button tap to cart update confirmation",
        "lower": 300, "mid": 600, "upper": 1200, "threshold": 2500,
        "events": [
            {"name": "add_cart_tap", "screenName": "ProductDetailScreen"},
            {"name": "cart_updated", "screenName": "ProductDetailScreen"},
        ],
        "base_duration": (400, 150),
        "base_error_rate": 0.02,
        "volume": 5000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: p == "ios" and av == "4.0.0",
                "duration": (1500, 400), "error_rate": 0.30, "crash": 0.04, "anr": 0.0, "frozen": (0, 0, 0.0),
            },
            {
                "match": lambda p, ov, d, n, s, av: s == "IN-GJ" and n in ("Vi", "BSNL"),
                "duration": (2000, 500), "error_rate": 0.20, "crash": 0.01, "anr": 0.02, "frozen": (1, 3, 0.15),
            },
        ],
    },
    {
        "name": "cart_checkout",
        "description": "Cart page load to checkout page ready with address and payment options",
        "lower": 600, "mid": 1200, "upper": 2200, "threshold": 4500,
        "events": [
            {"name": "checkout_initiated", "screenName": "CartScreen"},
            {"name": "checkout_ready", "screenName": "CheckoutScreen"},
        ],
        "base_duration": (950, 300),
        "base_error_rate": 0.04,
        "volume": 4000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: n == "Vi" and p == "android",
                "duration": (3000, 700), "error_rate": 0.25, "crash": 0.02, "anr": 0.06, "frozen": (3, 8, 0.35),
            },
            {
                "match": lambda p, ov, d, n, s, av: d == "SM-A135F",
                "duration": (2800, 600), "error_rate": 0.15, "crash": 0.03, "anr": 0.04, "frozen": (4, 12, 0.45),
            },
        ],
    },
    {
        "name": "payment_processing",
        "description": "Payment initiation to payment confirmation or failure",
        "lower": 1000, "mid": 2500, "upper": 4000, "threshold": 8000,
        "events": [
            {"name": "payment_initiated", "screenName": "PaymentScreen"},
            {"name": "payment_result", "screenName": "PaymentResultScreen"},
        ],
        "base_duration": (2000, 600),
        "base_error_rate": 0.06,
        "volume": 3500,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: p == "android" and ov == "13" and n == "Airtel",
                "duration": (5500, 1200), "error_rate": 0.35, "crash": 0.05, "anr": 0.08, "frozen": (2, 6, 0.20),
            },
            {
                "match": lambda p, ov, d, n, s, av: s == "IN-WB",
                "duration": (4000, 800), "error_rate": 0.22, "crash": 0.03, "anr": 0.04, "frozen": (1, 4, 0.15),
            },
            {
                "match": lambda p, ov, d, n, s, av: av == "4.2.0" and p == "ios",
                "duration": (3500, 700), "error_rate": 0.18, "crash": 0.07, "anr": 0.0, "frozen": (0, 0, 0.0),
            },
        ],
    },
    {
        "name": "order_confirmation",
        "description": "After successful payment to order confirmation screen with order details",
        "lower": 500, "mid": 1000, "upper": 1800, "threshold": 3500,
        "events": [
            {"name": "order_placed", "screenName": "PaymentResultScreen"},
            {"name": "confirmation_rendered", "screenName": "OrderConfirmationScreen"},
        ],
        "base_duration": (800, 250),
        "base_error_rate": 0.03,
        "volume": 3000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: d == "Pixel 8" and ov == "14",
                "duration": (2200, 500), "error_rate": 0.10, "crash": 0.02, "anr": 0.15, "frozen": (1, 3, 0.10),
            },
            {
                "match": lambda p, ov, d, n, s, av: av == "4.2.0" and p == "android",
                "duration": (1600, 400), "error_rate": 0.08, "crash": 0.10, "anr": 0.03, "frozen": (0, 0, 0.0),
            },
        ],
    },
    {
        "name": "order_tracking",
        "description": "Opening order tracking page with delivery status and map",
        "lower": 700, "mid": 1400, "upper": 2500, "threshold": 5000,
        "events": [
            {"name": "tracking_requested", "screenName": "OrdersListScreen"},
            {"name": "tracking_rendered", "screenName": "OrderTrackingScreen"},
        ],
        "base_duration": (1000, 300),
        "base_error_rate": 0.035,
        "volume": 4500,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: n == "BSNL" and s in ("IN-RJ", "IN-MP", "IN-BR"),
                "duration": (4000, 900), "error_rate": 0.30, "crash": 0.02, "anr": 0.05, "frozen": (2, 7, 0.25),
            },
            {
                "match": lambda p, ov, d, n, s, av: p == "ios" and ov == "16.0",
                "duration": (2500, 500), "error_rate": 0.20, "crash": 0.06, "anr": 0.0, "frozen": (0, 0, 0.0),
            },
        ],
    },
    {
        "name": "category_browse",
        "description": "Selecting a product category to category page fully rendered with filters",
        "lower": 500, "mid": 1000, "upper": 1800, "threshold": 3500,
        "events": [
            {"name": "category_selected", "screenName": "HomeScreen"},
            {"name": "category_rendered", "screenName": "CategoryScreen"},
        ],
        "base_duration": (800, 250),
        "base_error_rate": 0.03,
        "volume": 5500,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: n == "Jio" and s == "IN-AP",
                "duration": (2800, 600), "error_rate": 0.15, "crash": 0.02, "anr": 0.03, "frozen": (4, 12, 0.45),
            },
            {
                "match": lambda p, ov, d, n, s, av: d == "OnePlus 11" and p == "android",
                "duration": (1800, 400), "error_rate": 0.05, "crash": 0.12, "anr": 0.02, "frozen": (1, 3, 0.10),
            },
            {
                "match": lambda p, ov, d, n, s, av: av == "4.0.0",
                "duration": (2200, 500), "error_rate": 0.18, "crash": 0.04, "anr": 0.03, "frozen": (2, 6, 0.20),
            },
        ],
    },
    {
        "name": "image_gallery_load",
        "description": "Product image gallery tap to full-screen swipeable gallery loaded",
        "lower": 400, "mid": 800, "upper": 1500, "threshold": 3000,
        "events": [
            {"name": "gallery_tap", "screenName": "ProductDetailScreen"},
            {"name": "gallery_loaded", "screenName": "ImageGalleryScreen"},
        ],
        "base_duration": (600, 200),
        "base_error_rate": 0.025,
        "volume": 6500,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: d in ("SM-A135F", "Vivo Y56", "POCO M5", "Realme 10 Pro"),
                "duration": (2000, 500), "error_rate": 0.12, "crash": 0.03, "anr": 0.02, "frozen": (3, 10, 0.35),
            },
            {
                "match": lambda p, ov, d, n, s, av: d == "SM-A135F" and n == "Jio",
                "duration": (3000, 700), "error_rate": 0.25, "crash": 0.08, "anr": 0.05, "frozen": (5, 15, 0.50),
            },
        ],
    },
    {
        "name": "user_profile_load",
        "description": "Tapping profile tab to full account page with order history rendered",
        "lower": 500, "mid": 1000, "upper": 1800, "threshold": 3500,
        "events": [
            {"name": "profile_tab_tap", "screenName": "HomeScreen"},
            {"name": "profile_rendered", "screenName": "ProfileScreen"},
        ],
        "base_duration": (750, 220),
        "base_error_rate": 0.03,
        "volume": 4000,
        "bad_segments": [
            {
                "match": lambda p, ov, d, n, s, av: s == "IN-KA" and n == "Vi",
                "duration": (2500, 550), "error_rate": 0.28, "crash": 0.04, "anr": 0.06, "frozen": (2, 6, 0.25),
            },
            {
                "match": lambda p, ov, d, n, s, av: av == "4.0.0" and p == "android",
                "duration": (2200, 500), "error_rate": 0.15, "crash": 0.05, "anr": 0.03, "frozen": (1, 4, 0.15),
            },
        ],
    },
]

# ─── Span generation ─────────────────────────────────────────────────────────

now = datetime.now(timezone.utc)
start_time = now - timedelta(hours=24)


def pick_device_context():
    platform = wc(PLATFORMS, PLATFORM_WEIGHTS)
    if platform == "android":
        os_version = wc(ANDROID_VERSIONS, ANDROID_VERSION_WEIGHTS)
        device = wc(ANDROID_DEVICES, ANDROID_DEVICE_WEIGHTS)
    else:
        os_version = wc(IOS_VERSIONS, IOS_VERSION_WEIGHTS)
        device = wc(IOS_DEVICES, IOS_DEVICE_WEIGHTS)
    network = wc(NETWORKS, NETWORK_WEIGHTS)
    state = wc(STATES, STATE_WEIGHTS)
    app_version = wc(APP_VERSIONS, APP_VERSION_WEIGHTS)
    sdk_version = wc(SDK_VERSIONS, SDK_WEIGHTS)
    return platform, os_version, device, network, state, app_version, sdk_version


def apply_bad_segments(interaction, platform, os_version, device, network, state, app_version):
    """Return the most specific matching bad segment (last match wins)."""
    result = None
    for seg in interaction["bad_segments"]:
        if seg["match"](platform, os_version, device, network, state, app_version):
            result = seg
    return result


def compute_apdex(duration_ms, is_error, lower, mid, upper):
    if is_error:
        return "0", ""
    if duration_ms < lower:
        return "1.0", "Excellent"
    elif duration_ms < mid:
        return "0.75", "Good"
    elif duration_ms < upper:
        return "0.25", "Average"
    else:
        return "0.0", "Poor"


def generate_interaction_rows(interaction):
    rows = []
    vol = interaction["volume"]
    base_dur_mean, base_dur_std = interaction["base_duration"]
    base_err = interaction["base_error_rate"]
    lower, mid, upper = interaction["lower"], interaction["mid"], interaction["upper"]
    span_name = interaction["name"]

    for _ in range(vol):
        ts = start_time + timedelta(seconds=random.randint(0, 86400))
        ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")

        trace_id = uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        session_id = f"s_{random.randint(1, 2000)}"
        user_id = f"u_{random.randint(1, 800)}"

        platform, os_version, device, network, state, app_version, sdk_version = pick_device_context()

        dur_mean, dur_std = base_dur_mean, base_dur_std
        error_rate = base_err
        crash_rate = 0.005
        anr_rate = 0.002
        frozen_min, frozen_max, frozen_prob = 0, 0, 0.0

        bad = apply_bad_segments(interaction, platform, os_version, device, network, state, app_version)
        if bad:
            dur_mean, dur_std = bad["duration"]
            error_rate = bad["error_rate"]
            crash_rate = bad["crash"]
            anr_rate = bad["anr"]
            frozen_min, frozen_max, frozen_prob = bad["frozen"]

        is_error = random.random() < error_rate
        duration_ms = max(100, random.gauss(dur_mean, dur_std))
        duration_ns = int(duration_ms * 1e6)

        has_crash = random.random() < crash_rate
        has_anr = random.random() < anr_rate
        frozen_frames = random.randint(frozen_min, frozen_max) if frozen_prob > 0 and random.random() < frozen_prob else 0

        apdex_score, user_category = compute_apdex(duration_ms, is_error, lower, mid, upper)
        status_code = "Error" if is_error else "Ok"

        event_names = []
        if has_crash:
            event_names.append("device.crash")
        if has_anr:
            event_names.append("device.anr")

        events_name_str = "[" + ",".join(f"'{e}'" for e in event_names) + "]"
        events_ts_str = "[" + ",".join(f"'{ts_str}'" for _ in event_names) + "]"
        events_attr_str = "[" + ",".join("map()" for _ in event_names) + "]"

        analysed = frozen_frames + random.randint(50, 200) if frozen_frames > 0 else 0
        unanalysed = random.randint(0, 10) if frozen_frames > 0 else 0

        span_attrs_parts = [
            "'pulse.type','interaction'",
            f"'session.id','{escape(session_id)}'",
            f"'user.id','{escape(user_id)}'",
            f"'geo.region.iso_code','{escape(state)}'",
            "'geo.country.iso_code','IN'",
            f"'network.carrier.name','{escape(network)}'",
            f"'pulse.interaction.apdex_score','{apdex_score}'",
        ]
        if user_category:
            span_attrs_parts.append(f"'pulse.interaction.user_category','{user_category}'")
        if frozen_frames > 0:
            span_attrs_parts.append(f"'app.interaction.frozen_frame_count','{frozen_frames}'")
            span_attrs_parts.append(f"'app.interaction.analysed_frame_count','{analysed}'")
            span_attrs_parts.append(f"'app.interaction.unanalysed_frame_count','{unanalysed}'")

        span_attr_str = "map(" + ",".join(span_attrs_parts) + ")"

        resource_attrs_parts = [
            f"'os.name','{escape(platform)}'",
            f"'os.version','{escape(os_version)}'",
            f"'app.build_name','{escape(app_version)}'",
            f"'device.model.name','{escape(device)}'",
            "'project.id','default'",
            f"'rum.sdk.version','{escape(sdk_version)}'",
        ]
        resource_attr_str = "map(" + ",".join(resource_attrs_parts) + ")"

        row = (
            f"('{ts_str}','{trace_id}','{span_id}','','','{span_name}','CLIENT','pulse-sdk',"
            f"{resource_attr_str},'pulse','1.0',{span_attr_str},"
            f"{duration_ns},'{status_code}','',{events_ts_str},{events_name_str},{events_attr_str},"
            f"[],[],[],[])"
        )
        rows.append(row)

    return rows


def generate_rum_events(interaction_name, count):
    """Generate standalone crash/ANR RUM events in otel_traces."""
    rows = []
    for _ in range(count):
        ts = start_time + timedelta(seconds=random.randint(0, 86400))
        ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")
        trace_id = uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        session_id = f"s_{random.randint(1, 2000)}"
        user_id = f"u_{random.randint(1, 800)}"

        platform, os_version, device, network, state, app_version, sdk_version = pick_device_context()
        pulse_type = wc(["device.crash", "device.anr"], [60, 40])

        span_attrs_parts = [
            f"'pulse.type','{pulse_type}'",
            f"'session.id','{escape(session_id)}'",
            f"'user.id','{escape(user_id)}'",
            f"'geo.region.iso_code','{escape(state)}'",
            "'geo.country.iso_code','IN'",
            f"'network.carrier.name','{escape(network)}'",
        ]
        span_attr_str = "map(" + ",".join(span_attrs_parts) + ")"

        resource_attrs_parts = [
            f"'os.name','{escape(platform)}'",
            f"'os.version','{escape(os_version)}'",
            f"'app.build_name','{escape(app_version)}'",
            f"'device.model.name','{escape(device)}'",
            "'project.id','default'",
            f"'rum.sdk.version','{escape(sdk_version)}'",
        ]
        resource_attr_str = "map(" + ",".join(resource_attrs_parts) + ")"

        row = (
            f"('{ts_str}','{trace_id}','{span_id}','','','{pulse_type}','CLIENT','pulse-sdk',"
            f"{resource_attr_str},'pulse','1.0',{span_attr_str},"
            f"0,'Ok','',[],[],[],"
            f"[],[],[],[])"
        )
        rows.append(row)

    return rows


# ─── Realistic crash/ANR exception types ─────────────────────────────────────

ANDROID_CRASH_TYPES = [
    ("java.lang.NullPointerException", [
        "Attempt to invoke virtual method 'void android.widget.ImageView.setImageBitmap(android.graphics.Bitmap)' on a null object reference",
        "Attempt to read from field 'int com.app.model.Product.price' on a null object reference",
        "Attempt to invoke interface method 'int java.util.List.size()' on a null object reference",
    ]),
    ("java.lang.OutOfMemoryError", [
        "Failed to allocate a 48MB allocation with 16MB free",
        "pthread_create (1040KB stack) failed: Try again",
        "Failed to allocate a 12MB allocation with 4MB free",
    ]),
    ("java.lang.IllegalStateException", [
        "Fragment ProductDetailFragment not attached to a context",
        "Can not perform this action after onSaveInstanceState",
        "Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $",
    ]),
    ("java.lang.IndexOutOfBoundsException", [
        "Index: 5, Size: 3",
        "Inconsistency detected. Invalid view holder adapter positionViewHolder",
    ]),
    ("android.database.sqlite.SQLiteException", [
        "database disk image is malformed (code 11 SQLITE_CORRUPT)",
        "no such table: cart_items (code 1 SQLITE_ERROR)",
    ]),
]

IOS_CRASH_TYPES = [
    ("EXC_BAD_ACCESS", [
        "KERN_INVALID_ADDRESS at 0x0000000000000010",
        "KERN_PROTECTION_FAILURE at 0x000000016fdfc000",
    ]),
    ("NSInvalidArgumentException", [
        "-[__NSCFString objectForKeyedSubscript:]: unrecognized selector sent to instance",
        "-[NSNull length]: unrecognized selector sent to instance 0x1f5a29340",
    ]),
    ("NSRangeException", [
        "*** -[__NSArrayM objectAtIndexedSubscript:]: index 4 beyond bounds [0 .. 2]",
    ]),
    ("EXC_CRASH (SIGABRT)", [
        "Fatal error: Unexpectedly found nil while unwrapping an Optional value",
    ]),
]

ANR_TITLES = [
    "Input dispatching timed out (Waiting to send non-key event because the touched window has not finished processing input events)",
    "Input dispatching timed out (Application does not have a focused window)",
    "Broadcast of Intent { act=com.app.SYNC_COMPLETE }",
    "executing service com.app/.service.SyncService",
    "Input dispatching timed out (Waiting because no window has focus but there is a focused application)",
]

SCREEN_NAMES = [
    "HomeScreen", "ProductDetailScreen", "SearchResultsScreen", "CartScreen",
    "CheckoutScreen", "PaymentScreen", "OrderConfirmationScreen", "OrderTrackingScreen",
    "CategoryScreen", "ProfileScreen", "ImageGalleryScreen", "WishlistScreen",
]


def generate_stack_trace_events(total_crashes, total_anrs):
    """Generate crash/ANR rows for the stack_trace_events table (Vitals page)."""
    import hashlib
    rows = []

    for i in range(total_crashes + total_anrs):
        is_crash = i < total_crashes
        pulse_type = "device.crash" if is_crash else "device.anr"

        ts = start_time + timedelta(seconds=random.randint(0, 86400))
        ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")
        trace_id = uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        session_id = f"s_{random.randint(1, 2000)}"
        user_id = f"u_{random.randint(1, 800)}"

        platform, os_version, device, network, state, app_version, sdk_version = pick_device_context()
        screen = wc(SCREEN_NAMES, [15, 15, 10, 8, 7, 7, 5, 5, 8, 7, 8, 5])

        interactions = wc(
            [["app_launch"], ["home_feed_load"], ["product_detail_view"], ["cart_checkout"],
             ["payment_processing"], ["product_search"], ["category_browse"], ["image_gallery_load"]],
            [15, 15, 20, 10, 10, 10, 10, 10],
        )

        if is_crash:
            if platform == "android":
                exc_type, messages = wc(ANDROID_CRASH_TYPES, [35, 15, 20, 15, 15])
            else:
                exc_type, messages = wc(IOS_CRASH_TYPES, [30, 30, 20, 20])
            exc_message = wc(messages, [1] * len(messages))
            event_name = "device.crash"
        else:
            exc_type = "ANR"
            exc_message = wc(ANR_TITLES, [1] * len(ANR_TITLES))
            event_name = "device.anr"

        sig_input = f"v1|{platform}|exc:{exc_type}|msg:{exc_message[:80]}"
        fingerprint = hashlib.sha1(sig_input.encode()).hexdigest()
        group_id = f"EXC-{fingerprint[:10].upper()}"
        title = f"{exc_type}: {exc_message[:120]} [{group_id}]"

        stack_lines = [
            f"  at com.app.{screen.lower()}.{wc(['onCreate','onResume','loadData','render','bind','process'],  [1]*6)}({screen}.java:{random.randint(50,500)})",
            f"  at com.app.core.{wc(['NetworkManager','DataManager','CacheManager','ImageLoader'],  [1]*4)}.{wc(['fetch','load','process','execute'],  [1]*4)}(Unknown Source:{random.randint(100,800)})",
            f"  at com.app.util.{wc(['JsonParser','ViewHelper','Analytics','Logger'],  [1]*4)}.{wc(['parse','handle','track','log'],  [1]*4)}(Unknown Source:{random.randint(50,300)})",
        ]
        stack_trace = f"{exc_type}: {exc_message}\n" + "\n".join(stack_lines)

        interactions_str = "[" + ",".join(f"'{escape(n)}'" for n in interactions) + "]"

        log_attrs = f"map('pulse.type','{pulse_type}')"
        resource_attrs = (
            f"map('project.id','default','os.name','{escape(platform)}',"
            f"'os.version','{escape(os_version)}',"
            f"'app.build_name','{escape(app_version)}',"
            f"'device.model.name','{escape(device)}',"
            f"'rum.sdk.version','{escape(sdk_version)}')"
        )

        row = (
            f"('{ts_str}','{escape(event_name)}','{escape(title)}',"
            f"'{escape(stack_trace)}','{escape(stack_trace)}','{escape(exc_message)}','{escape(exc_type)}',"
            f"{interactions_str},'{escape(screen)}',"
            f"'{escape(user_id)}','{escape(session_id)}',"
            f"'{escape(platform)}','{escape(os_version)}','{escape(device)}',"
            f"'1','{escape(app_version)}','{escape(sdk_version)}','',"
            f"'{trace_id}','{span_id}',"
            f"'{escape(group_id)}','{escape(sig_input)}','{fingerprint}',"
            f"map(),{log_attrs},{resource_attrs})"
        )
        rows.append(row)

    return rows


def generate_session_start_logs(count):
    """Generate session.start log entries in otel_logs for total user/session counts."""
    rows = []
    for _ in range(count):
        ts = start_time + timedelta(seconds=random.randint(0, 86400))
        ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")
        trace_id = uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        session_id = f"s_{random.randint(1, 2000)}"
        user_id = f"u_{random.randint(1, 800)}"

        platform, os_version, device, network, state, app_version, sdk_version = pick_device_context()

        log_attrs = (
            f"map('pulse.type','session.start',"
            f"'session.id','{escape(session_id)}',"
            f"'user.id','{escape(user_id)}',"
            f"'geo.region.iso_code','{escape(state)}',"
            f"'geo.country.iso_code','IN',"
            f"'network.carrier.name','{escape(network)}')"
        )
        resource_attrs = (
            f"map('project.id','default',"
            f"'os.name','{escape(platform)}',"
            f"'os.version','{escape(os_version)}',"
            f"'app.build_name','{escape(app_version)}',"
            f"'device.model.name','{escape(device)}',"
            f"'rum.sdk.version','{escape(sdk_version)}')"
        )

        row = (
            f"('{ts_str}','{trace_id}','{span_id}',0,"
            f"'INFO',6,'pulse-sdk','Session started',"
            f"{resource_attrs},{log_attrs},'session.start')"
        )
        rows.append(row)

    return rows


def insert_trace_rows(rows, label=""):
    BATCH_SIZE = 500
    total = len(rows)
    for batch_start in range(0, total, BATCH_SIZE):
        batch = rows[batch_start : batch_start + BATCH_SIZE]
        insert_sql = (
            "INSERT INTO otel_traces "
            "(Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind, ServiceName, "
            "ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes, "
            "Duration, StatusCode, StatusMessage, `Events.Timestamp`, `Events.Name`, `Events.Attributes`, "
            "`Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`) "
            "VALUES " + ",".join(batch)
        )
        ch_query(insert_sql)
        done = min(batch_start + BATCH_SIZE, total)
        pct = int(done / total * 100)
        print(f"    {done}/{total} ({pct}%) {label}")


def insert_stack_trace_rows(rows, label=""):
    BATCH_SIZE = 500
    total = len(rows)
    for batch_start in range(0, total, BATCH_SIZE):
        batch = rows[batch_start : batch_start + BATCH_SIZE]
        insert_sql = (
            "INSERT INTO stack_trace_events "
            "(Timestamp, EventName, Title, "
            "ExceptionStackTrace, ExceptionStackTraceRaw, ExceptionMessage, ExceptionType, "
            "Interactions, ScreenName, "
            "UserId, SessionId, "
            "Platform, OsVersion, DeviceModel, "
            "AppVersionCode, AppVersion, SdkVersion, BundleId, "
            "TraceId, SpanId, "
            "GroupId, Signature, Fingerprint, "
            "ScopeAttributes, LogAttributes, ResourceAttributes) "
            "VALUES " + ",".join(batch)
        )
        ch_query(insert_sql)
        done = min(batch_start + BATCH_SIZE, total)
        pct = int(done / total * 100)
        print(f"    {done}/{total} ({pct}%) {label}")


def insert_log_rows(rows, label=""):
    BATCH_SIZE = 500
    total = len(rows)
    for batch_start in range(0, total, BATCH_SIZE):
        batch = rows[batch_start : batch_start + BATCH_SIZE]
        insert_sql = (
            "INSERT INTO otel_logs "
            "(Timestamp, TraceId, SpanId, TraceFlags, "
            "SeverityText, SeverityNumber, ServiceName, Body, "
            "ResourceAttributes, LogAttributes, EventName) "
            "VALUES " + ",".join(batch)
        )
        ch_query(insert_sql)
        done = min(batch_start + BATCH_SIZE, total)
        pct = int(done / total * 100)
        print(f"    {done}/{total} ({pct}%) {label}")


# ─── Main ─────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    clear = "--clear" in sys.argv

    print("=" * 60)
    print("  E-commerce Data Seeder for Pulse")
    print("=" * 60)

    if clear:
        print("\n[0/5] Clearing existing data...")
        ch_query("TRUNCATE TABLE otel_traces")
        ch_query("TRUNCATE TABLE otel_logs")
        ch_query("TRUNCATE TABLE stack_trace_events")
        print("  Cleared ClickHouse tables (otel_traces, otel_logs, stack_trace_events)")
        mysql_query("DELETE FROM interaction WHERE tenant_id = 'default';")
        print("  Cleared MySQL interactions")

    # ── Step 1: Insert interactions in MySQL ──────────────────────────────
    print("\n[1/5] Creating interactions in MySQL...")
    for ix in INTERACTIONS:
        import json
        details = json.dumps({
            "description": ix["description"],
            "uptimeLowerLimitInMs": ix["lower"],
            "uptimeMidLimitInMs": ix["mid"],
            "uptimeUpperLimitInMs": ix["upper"],
            "thresholdInMs": ix["threshold"],
            "events": [{"name": e["name"], "isBlacklisted": False} for e in ix["events"]],
            "globalBlacklistedEvents": [],
        })
        details_escaped = details.replace("\\", "\\\\").replace("'", "\\'")
        sql = (
            f"INSERT INTO interaction (tenant_id, name, status, details, created_by, updated_by) "
            f"VALUES ('default', '{ix['name']}', 'RUNNING', '{details_escaped}', 'seed-script', 'seed-script') "
            f"ON DUPLICATE KEY UPDATE status = VALUES(status), details = VALUES(details), updated_by = VALUES(updated_by);"
        )
        mysql_query(sql)
        print(f"  + {ix['name']}: lower={ix['lower']}ms mid={ix['mid']}ms upper={ix['upper']}ms")

    # ── Step 2: Generate interaction traces ───────────────────────────────
    print("\n[2/5] Generating interaction traces (otel_traces)...")
    total_rows = 0
    for ix in INTERACTIONS:
        print(f"\n  Generating {ix['volume']} spans for '{ix['name']}'...")
        rows = generate_interaction_rows(ix)
        insert_trace_rows(rows, label=ix["name"])
        total_rows += len(rows)

    # ── Step 3: Generate standalone crash/ANR in otel_traces ──────────────
    print("\n[3/5] Generating standalone crash/ANR RUM events (otel_traces)...")
    rum_rows = generate_rum_events("global", 800)
    insert_trace_rows(rum_rows, label="crash/ANR events")
    total_rows += len(rum_rows)

    # ── Step 4: Generate crash/ANR for Vitals (stack_trace_events) ────────
    num_crashes = 600
    num_anrs = 350
    print(f"\n[4/5] Generating {num_crashes} crashes + {num_anrs} ANRs (stack_trace_events for Vitals)...")
    ste_rows = generate_stack_trace_events(num_crashes, num_anrs)
    insert_stack_trace_rows(ste_rows, label="stack_trace_events")
    total_rows += len(ste_rows)

    # ── Step 5: Generate session.start logs (otel_logs for total user counts) ─
    num_sessions = 5000
    print(f"\n[5/5] Generating {num_sessions} session.start logs (otel_logs)...")
    log_rows = generate_session_start_logs(num_sessions)
    insert_log_rows(log_rows, label="session.start logs")
    total_rows += len(log_rows)

    # ── Verification ──────────────────────────────────────────────────────
    print("\n" + "=" * 60)
    print("  Verification")
    print("=" * 60)
    print(f"\n  Total rows inserted: {total_rows}")

    print("\n  Interaction traces (otel_traces):")
    for ix in INTERACTIONS:
        count = ch_query(f"SELECT count() FROM otel_traces WHERE SpanName = '{ix['name']}'").strip()
        apdex = ch_query(
            f"SELECT round(avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), "
            f"StatusCode != 'Error'), 3) FROM otel_traces WHERE SpanName = '{ix['name']}'"
        ).strip()
        err = ch_query(
            f"SELECT round(countIf(StatusCode = 'Error') / count() * 100, 1) "
            f"FROM otel_traces WHERE SpanName = '{ix['name']}'"
        ).strip()
        print(f"    {ix['name']:25s}  vol={count:>6s}  apdex={apdex:>6s}  err%={err:>5s}")

    crash_trace = ch_query("SELECT count() FROM otel_traces WHERE PulseType = 'device.crash'").strip()
    anr_trace = ch_query("SELECT count() FROM otel_traces WHERE PulseType = 'device.anr'").strip()
    print(f"\n  otel_traces standalone: crashes={crash_trace}, ANRs={anr_trace}")

    crash_ste = ch_query("SELECT count() FROM stack_trace_events WHERE EventName = 'device.crash'").strip()
    anr_ste = ch_query("SELECT count() FROM stack_trace_events WHERE EventName = 'device.anr'").strip()
    groups = ch_query("SELECT uniqExact(GroupId) FROM stack_trace_events").strip()
    print(f"  stack_trace_events:    crashes={crash_ste}, ANRs={anr_ste}, unique groups={groups}")

    session_logs = ch_query("SELECT count() FROM otel_logs WHERE EventName = 'session.start'").strip()
    unique_users = ch_query("SELECT uniqExact(LogAttributes['user.id']) FROM otel_logs WHERE EventName = 'session.start'").strip()
    print(f"  otel_logs:             sessions={session_logs}, unique_users={unique_users}")

    print("\n" + "=" * 60)
    print("  Seed complete!")
    print("=" * 60)
    print("\n  Open Pulse UI and you should see:")
    print("    - 12 interactions on the Critical Interactions page")
    print("    - Crashes & ANRs on the App Vitals page")
    print("    - Each interaction has unique bad segments for the AI to discover\n")
