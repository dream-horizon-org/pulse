#!/usr/bin/env python3
"""
Seed realistic FanCode-inspired data into local ClickHouse.
Generates ~10,000 sessions with correlated behavior patterns that
create REAL causal relationships for the prototype to discover.

Key causal relationships baked in:
1. Crashes on PaymentListing screen CAUSE 40% drop in purchase completion
2. ANRs on matchdetailsV2 CAUSE 25% drop in engagement (watchlist adds)
3. Network 502 errors on payment APIs CAUSE 50% drop in purchase completion
4. Slow screen loads (>3s) on home CAUSE 15% drop in deeper navigation
5. Crashes on home screen CAUSE 30% drop in reaching payment flow at all
"""

import os
import sys
import uuid
import random
import hashlib
from datetime import datetime, timedelta, timezone
from typing import Optional

import clickhouse_connect
import numpy as np
from dotenv import load_dotenv

load_dotenv()

# ═══════════════════════════════════════════════════════════════
# Configuration
# ═══════════════════════════════════════════════════════════════

PROJECT_ID = "fancode-seed"
NUM_SESSIONS = 10_000
START_DATE = datetime(2026, 2, 1, tzinfo=timezone.utc)
END_DATE = datetime(2026, 3, 20, tzinfo=timezone.utc)

# Device distributions (from real data)
DEVICES = [
    ("sdk_gphone64_arm64", "14", 0.15),
    ("sdk_gphone16k_arm64", "16", 0.12),
    ("Pixel 6a", "14", 0.12),
    ("Pixel 7", "14", 0.08),
    ("Pixel 8 Pro", "15", 0.05),
    ("SM-A235F", "13", 0.10),
    ("SM-A545F", "14", 0.06),
    ("SM-S918B", "14", 0.04),
    ("V2152", "13", 0.08),
    ("V2338", "15", 0.04),
    ("CPH2691", "14", 0.05),
    ("OnePlus 9", "13", 0.04),
    ("OnePlus 11", "14", 0.03),
    ("Redmi Note 12", "13", 0.02),
    ("Redmi Note 13 Pro", "14", 0.02),
]

APP_VERSIONS = [
    ("9.5.0_2820", 0.35),
    ("9.5.0_2806", 0.25),
    ("9.4.0_10960287", 0.20),
    ("9.3.0_10960287", 0.10),
    ("9.5.0_10960691", 0.05),
    ("9.1.0_10960287", 0.03),
    ("9.5.0_2788", 0.02),
]

NETWORK_PROVIDERS = [
    ("Reliance Jio", 0.30),
    ("Airtel", 0.25),
    ("Vi", 0.15),
    ("T-Mobile - US", 0.10),
    ("BSNL", 0.05),
    ("", 0.15),
]

GEO_COUNTRIES = [
    ("IN", 0.70),
    ("US", 0.15),
    ("GB", 0.05),
    ("AE", 0.03),
    ("AU", 0.02),
    ("", 0.05),
]

# ── Screens: the user journey ──
# Typical flow: MainActivity → home → (browse) → matchdetailsV2 → (payment flow)
SCREENS = {
    "entry": ["MainActivity"],
    "home": ["com.fc.home"],
    "browse": ["com.fc.scheduleScreen", "com.fc.channelsHome", "com.fc.SportsPage",
               "com.fc.LiveNow", "com.fc.VideoDetail", "com.fc.Watchlist"],
    "detail": ["com.fc.matchdetailsV2", "com.fc.TourDetail"],
    "engagement": ["com.fc.UserProfileScreen", "com.fc.SelectionModal",
                    "com.fc.InappReviewFeedback", "com.fc.EnableNotificationsModal"],
    "payment": ["com.fc.PaymentListing", "com.fc.webScreenNewArch"],
    "onboarding": ["com.fc.OnboardingV2", "com.fc.OtpAuthentication"],
    "settings": ["com.fc.DeveloperSettings"],
}

# GraphQL operations by category
GQL_OPS = {
    "home_load": [
        ("GET", "HomePageLiveNowMatches"),
        ("GET", "HybridSliderSegmentV2"),
        ("GET", "HeroCarouselPreviewHighlights"),
        ("GET", "HeroCarouselWatchlistBatch"),
        ("GET", "RankedCuratedTourList"),
        ("GET", "HomePopularHeroCarousel"),
        ("GET", "homeCollectionNeo"),
        ("GET", "sportsFloatingBarData"),
        ("GET", "NudgeSegment"),
        ("GET", "AppConfig"),
        ("GET", "ClientInfo"),
    ],
    "browse": [
        ("GET", "ExploreBySports"),
        ("GET", "FanLiveStream"),
        ("GET", "CasaWatchlistBatch"),
    ],
    "match_detail": [
        ("GET", "mdpV3AppCollection"),
        ("GET", "mdpV3AppTabs"),
        ("GET", "mdpRenewInfo"),
    ],
    "engagement": [
        ("POST", "UserPreferences"),
        ("GET", "FollowStatus"),
        ("GET", "YourWatchlist"),
        ("POST", "HeroCarouselWatchlistBatch"),
        ("POST", "sportsFloatingBarData"),
        ("POST", "CasaWatchlistBatch"),
    ],
    "payment": [
        ("GET", "TourEntitlementStatus"),
        ("GET", "validateEntitlement"),
        ("GET", "paymentWidgetsNewUser"),
        ("GET", "getPaymentConfigs"),
        ("GET", "VideoDetailsBuyPassNudge"),
    ],
    "notification": [
        ("GET", "tourMatchNotificationStatus"),
        ("POST", "updateUserNotifications"),
        ("GET", "matchTourNotification"),
        ("GET", "getMatchNotificationStatus"),
    ],
}

# Crash/ANR definitions with causal rates
CRASH_DEFS = [
    # (screen, exception_type, pulse_type, base_rate, extra_rate_for_old_devices)
    ("com.fc.PaymentListing", "java.lang.NullPointerException", "device.crash", 0.03, 0.06),
    ("com.fc.matchdetailsV2", "java.lang.NoClassDefFoundError", "device.crash", 0.02, 0.04),
    ("com.fc.home", "java.lang.RuntimeException", "device.crash", 0.015, 0.03),
    ("MainActivity", "java.lang.IllegalStateException", "device.crash", 0.005, 0.01),
    ("com.fc.DeveloperSettings", "java.lang.RuntimeException", "device.crash", 0.01, 0.0),
    ("com.fc.VideoDetail", "java.lang.OutOfMemoryError", "device.crash", 0.01, 0.03),
]

ANR_DEFS = [
    ("com.fc.matchdetailsV2", "", "device.anr", 0.025, 0.05),
    ("com.fc.home", "", "device.anr", 0.015, 0.03),
    ("com.fc.channelsHome", "", "device.anr", 0.01, 0.02),
    ("CartScreen", "", "device.anr", 0.008, 0.02),
    ("MainActivity", "", "device.anr", 0.005, 0.01),
]

NON_FATAL_DEFS = [
    ("com.fc.DeveloperSettings", "Error", "non_fatal", 0.04, 0.0),
    ("com.fc.home", "Error", "non_fatal", 0.02, 0.03),
    ("unknown", "Error", "non_fatal", 0.03, 0.0),
]

# ═══════════════════════════════════════════════════════════════
# Helpers
# ═══════════════════════════════════════════════════════════════

rng = np.random.RandomState(42)


def weighted_choice(items_with_weights):
    items = [i[:-1] if len(i) > 2 else (i[0],) for i in items_with_weights]
    weights = [i[-1] for i in items_with_weights]
    total = sum(weights)
    weights = [w / total for w in weights]
    idx = rng.choice(len(items), p=weights)
    return items[idx] if len(items[idx]) > 1 else items[idx][0]


def gen_session_id():
    return uuid.uuid4().hex


def gen_trace_id():
    return uuid.uuid4().hex


def gen_span_id():
    return uuid.uuid4().hex[:16]


def gen_user_id():
    return hashlib.md5(uuid.uuid4().bytes).hexdigest()[:24]


def ts_to_nano(dt: datetime) -> int:
    return int(dt.timestamp() * 1_000_000_000)


def random_timestamp():
    delta = (END_DATE - START_DATE).total_seconds()
    offset = rng.random() * delta
    return START_DATE + timedelta(seconds=offset)


# ═══════════════════════════════════════════════════════════════
# Session Generator
# ═══════════════════════════════════════════════════════════════

def generate_session(session_idx: int):
    """Generate one complete session with all its spans."""

    session_id = gen_session_id()
    user_id = gen_user_id()
    trace_id = gen_trace_id()

    # Pick device/app characteristics
    device_model, os_version = weighted_choice(DEVICES)
    app_version = weighted_choice(APP_VERSIONS)
    network_provider = weighted_choice(NETWORK_PROVIDERS)
    geo_country = weighted_choice(GEO_COUNTRIES)

    is_old_device = os_version in ("11", "12", "13") or device_model.startswith("Redmi")
    is_old_app = app_version.startswith("9.1") or app_version.startswith("9.3")

    # Session start time
    session_start = random_timestamp()
    current_time = session_start

    traces = []  # (Timestamp, TraceId, SpanId, ParentSpanId, SpanName, SpanKind, ServiceName, ResourceAttributes, SpanAttributes, Duration, StatusCode, StatusMessage, Events.Timestamp, Events.Name, Events.Attributes, PulseType_for_tracking)
    stack_events = []  # for stack_trace_events table

    resource_attrs = {
        "project.id": PROJECT_ID,
        "os.name": "Android",
        "os.version": os_version,
        "device.model.name": device_model,
        "app.build_name": app_version,
        "rum.sdk.version": "1.2.0",
        "telemetry.sdk.name": "pulse_android_rn",
    }

    span_base = {
        "session.id": session_id,
        "user.id": user_id,
        "network.carrier.name": network_provider,
        "geo.country.iso_code": geo_country,
    }

    # Track what happened in this session for causal modeling
    session_crashed = False
    session_crash_screen = None
    session_anr = False
    session_anr_screen = None
    session_had_payment_error = False
    visited_screens = []
    reached_payment = False
    completed_purchase = False

    def add_span(span_name, pulse_type, duration_ms, extra_attrs=None, status="Ok"):
        nonlocal current_time
        attrs = {**span_base, "pulse.type": pulse_type}
        if extra_attrs:
            attrs.update(extra_attrs)
        span_id = gen_span_id()
        duration_ns = int(duration_ms * 1_000_000)
        traces.append((
            current_time, trace_id, span_id, "", span_name, "SPAN_KIND_INTERNAL",
            "fancode-android", resource_attrs, attrs, duration_ns,
            "Error" if status == "Error" else "",
            "", [], [], [],
        ))
        current_time += timedelta(milliseconds=duration_ms + rng.randint(100, 2000))

    def add_network_span(method, op_name, status_code=200, duration_ms=None):
        nonlocal current_time
        if duration_ms is None:
            duration_ms = int(rng.lognormal(5, 1))  # ~150ms median
            duration_ms = min(duration_ms, 10000)
        pulse_type = f"network.{status_code}"
        attrs = {
            **span_base,
            "pulse.type": pulse_type,
            "http.method": method,
            "http.request.method": method,
            "http.status_code": str(status_code),
            "http.url": "https://www.fancode.com/graphql",
            "http.target": "/graphql",
            "http.host": "www.fancode.com",
            "http.scheme": "https",
            "http.request.type": "xmlhttprequest",
            "http.request.header.operation_name": op_name,
            "http.request.header.appversion": app_version.split("_")[0],
            "http.request.header.source": "sportsguruand",
        }
        span_id = gen_span_id()
        duration_ns = int(duration_ms * 1_000_000)
        traces.append((
            current_time, trace_id, span_id, "", f"HTTP {method}", "SPAN_KIND_CLIENT",
            "fancode-android", resource_attrs, attrs, duration_ns,
            "Error" if status_code >= 400 else "",
            "", [], [], [],
        ))
        current_time += timedelta(milliseconds=duration_ms + rng.randint(50, 500))

    def add_screen(screen_name, duration_sec=None):
        nonlocal current_time
        if duration_sec is None:
            duration_sec = max(1, int(rng.lognormal(2, 1)))
        visited_screens.append(screen_name)

        # screen_load span
        load_ms = int(rng.lognormal(6, 0.8))  # ~400ms median
        load_ms = min(load_ms, 8000)
        add_span("Navigated", "screen_load", load_ms, {"screen.name": screen_name})

        # screen_session span
        session_ms = duration_sec * 1000
        add_span("ScreenSession", "screen_session", session_ms, {"screen.name": screen_name})

    def maybe_crash(screen_name):
        nonlocal session_crashed, session_crash_screen
        if session_crashed:
            return False
        for sn, exc_type, pt, base_rate, extra in CRASH_DEFS:
            if sn == screen_name:
                rate = base_rate + (extra if is_old_device else 0)
                if is_old_app:
                    rate *= 1.5
                if rng.random() < rate:
                    session_crashed = True
                    session_crash_screen = screen_name
                    stack_events.append((
                        current_time, "device.crash", f"Crash in {screen_name}",
                        f"java.lang.RuntimeException: crash in {screen_name}\n\tat com.fc.{screen_name}.onCreate()\n\tat android.app.Activity.performCreate()",
                        "", f"Crash in {screen_name}", exc_type,
                        [], screen_name, user_id, session_id,
                        "Android", os_version, device_model, app_version.split("_")[0], app_version,
                        "1.2.0", "com.fancode.app", trace_id, gen_span_id(),
                        hashlib.md5(f"{screen_name}:{exc_type}".encode()).hexdigest()[:16],
                        f"{screen_name}:{exc_type}", f"{screen_name}:{exc_type}",
                        {}, {"pulse.type": "device.crash", "session.id": session_id},
                        resource_attrs,
                    ))
                    return True
        return False

    def maybe_anr(screen_name):
        nonlocal session_anr, session_anr_screen
        if session_anr:
            return False
        for sn, exc_type, pt, base_rate, extra in ANR_DEFS:
            if sn == screen_name:
                rate = base_rate + (extra if is_old_device else 0)
                if rng.random() < rate:
                    session_anr = True
                    session_anr_screen = screen_name
                    stack_events.append((
                        current_time, "device.anr", f"ANR in {screen_name}",
                        f"ANR in {screen_name}\n\tmain thread blocked",
                        "", f"ANR in {screen_name}", "",
                        [], screen_name, user_id, session_id,
                        "Android", os_version, device_model, app_version.split("_")[0], app_version,
                        "1.2.0", "com.fancode.app", trace_id, gen_span_id(),
                        hashlib.md5(f"anr:{screen_name}".encode()).hexdigest()[:16],
                        f"anr:{screen_name}", f"anr:{screen_name}",
                        {}, {"pulse.type": "device.anr", "session.id": session_id},
                        resource_attrs,
                    ))
                    return True
        return False

    def maybe_non_fatal(screen_name):
        for sn, exc_type, pt, base_rate, extra in NON_FATAL_DEFS:
            if sn == screen_name:
                rate = base_rate + (extra if is_old_device else 0)
                if rng.random() < rate:
                    stack_events.append((
                        current_time, "non_fatal", f"Non-fatal in {screen_name}",
                        f"Error: non-fatal in {screen_name}",
                        "", f"Non-fatal in {screen_name}", exc_type,
                        [], screen_name, user_id, session_id,
                        "Android", os_version, device_model, app_version.split("_")[0], app_version,
                        "1.2.0", "com.fancode.app", trace_id, gen_span_id(),
                        hashlib.md5(f"nf:{screen_name}".encode()).hexdigest()[:16],
                        f"nf:{screen_name}", f"nf:{screen_name}",
                        {}, {"pulse.type": "non_fatal", "session.id": session_id},
                        resource_attrs,
                    ))

    # ═══════════════════════════════════════════════
    # Generate the actual session journey
    # ═══════════════════════════════════════════════

    # 1. App start
    add_span("app_start", "app_start", int(rng.lognormal(7, 0.5)),
             {"start_type": rng.choice(["cold", "warm", "hot"])})

    # 2. MainActivity
    add_screen("MainActivity", duration_sec=rng.randint(1, 3))
    if maybe_crash("MainActivity"):
        return traces, stack_events  # session ends

    # 3. Home screen (95% of sessions)
    if rng.random() < 0.95:
        add_screen("com.fc.home", duration_sec=rng.randint(3, 30))

        # Home screen network calls
        indices = rng.choice(len(GQL_OPS["home_load"]),
                             size=min(8, rng.randint(3, 11)), replace=False)
        for idx in indices:
            m, op_name = GQL_OPS["home_load"][idx]
            add_network_span(m, op_name)

        maybe_non_fatal("com.fc.home")
        if maybe_crash("com.fc.home") or maybe_anr("com.fc.home"):
            # ─── CAUSAL EFFECT: crash/ANR on home prevents deeper navigation ───
            return traces, stack_events

    # 4. Onboarding (10% - new users)
    if rng.random() < 0.10:
        add_screen("com.fc.OnboardingV2", duration_sec=rng.randint(5, 20))
        if rng.random() < 0.7:
            add_screen("com.fc.OtpAuthentication", duration_sec=rng.randint(10, 40))

    # 5. Browse phase (70% of remaining sessions)
    if not session_crashed and rng.random() < 0.70:
        n_browse = rng.randint(1, 4)
        for _ in range(n_browse):
            screen = rng.choice(SCREENS["browse"])
            add_screen(screen, duration_sec=rng.randint(5, 60))

            # Browse network calls
            browse_indices = rng.choice(len(GQL_OPS["browse"]),
                                        size=min(2, rng.randint(1, 3)), replace=True)
            for idx in browse_indices:
                m, op_name = GQL_OPS["browse"][idx]
                add_network_span(m, op_name)

            if maybe_crash(screen) or maybe_anr(screen):
                return traces, stack_events

    # 6. Match detail (55% of remaining sessions)
    if not session_crashed and rng.random() < 0.55:
        n_matches = rng.randint(1, 4)
        for _ in range(n_matches):
            screen = rng.choice(SCREENS["detail"])
            add_screen(screen, duration_sec=rng.randint(10, 120))

            # Match detail network calls
            for m, op_name in GQL_OPS["match_detail"]:
                add_network_span(m, op_name)

            # Engagement calls during match viewing
            if rng.random() < 0.4:
                m, op_name = GQL_OPS["engagement"][rng.randint(0, len(GQL_OPS["engagement"]))]
                add_network_span(m, op_name)

            if maybe_anr(screen):
                # ─── CAUSAL EFFECT: ANR on match detail reduces engagement ───
                if rng.random() < 0.70:  # 70% abandon after ANR
                    return traces, stack_events

            if maybe_crash(screen):
                return traces, stack_events

    # 7. Engagement screens (30%)
    if not session_crashed and rng.random() < 0.30:
        screen = rng.choice(SCREENS["engagement"])
        add_screen(screen, duration_sec=rng.randint(5, 30))

    # 8. Payment flow (15% base rate)
    payment_intent_rate = 0.15
    if is_old_device:
        payment_intent_rate *= 0.8  # slightly lower for old devices
    if len(visited_screens) > 5:
        payment_intent_rate *= 1.3  # more engaged users more likely to pay

    if not session_crashed and not session_anr and rng.random() < payment_intent_rate:
        reached_payment = True
        add_screen("com.fc.PaymentListing", duration_sec=rng.randint(10, 60))

        # Payment API calls
        for m, op_name in GQL_OPS["payment"]:
            # ─── CAUSAL EFFECT: 502 errors on payment APIs kill conversion ───
            status = 200
            if rng.random() < 0.04:  # 4% payment API error rate
                status = 502
                session_had_payment_error = True
            add_network_span(m, op_name, status_code=status)

        if maybe_crash("com.fc.PaymentListing"):
            # ─── CAUSAL EFFECT: crash on PaymentListing = lost sale ───
            return traces, stack_events

        # Purchase completion (if no crash, no payment error)
        if not session_had_payment_error:
            if rng.random() < 0.65:  # 65% of non-error payment sessions complete
                completed_purchase = True
                add_screen("com.fc.webScreenNewArch", duration_sec=rng.randint(15, 90))
                # Final payment confirmation calls
                add_network_span("POST", "validateEntitlement")
                add_network_span("POST", "getPaymentConfigs")
        else:
            # With payment error: only 15% complete (vs 65% without)
            if rng.random() < 0.15:
                completed_purchase = True
                add_screen("com.fc.webScreenNewArch", duration_sec=rng.randint(15, 90))
                add_network_span("POST", "validateEntitlement")

    # 9. Notification/settings (scattered)
    if not session_crashed and rng.random() < 0.15:
        m, op_name = GQL_OPS["notification"][rng.randint(0, len(GQL_OPS["notification"]))]
        add_network_span(m, op_name)

    if not session_crashed and rng.random() < 0.08:
        add_screen("com.fc.DeveloperSettings", duration_sec=rng.randint(5, 30))
        maybe_crash("com.fc.DeveloperSettings")
        maybe_non_fatal("com.fc.DeveloperSettings")

    return traces, stack_events


# ═══════════════════════════════════════════════════════════════
# Insert into ClickHouse
# ═══════════════════════════════════════════════════════════════

def insert_data(client, all_traces, all_stack_events):
    """Batch insert into ClickHouse."""

    # Insert traces
    print(f"  Inserting {len(all_traces)} trace spans...")
    col_names = [
        "Timestamp", "TraceId", "SpanId", "ParentSpanId", "SpanName", "SpanKind",
        "ServiceName", "ResourceAttributes", "SpanAttributes", "Duration",
        "StatusCode", "StatusMessage",
        "Events.Timestamp", "Events.Name", "Events.Attributes",
    ]

    # Batch insert in chunks
    chunk_size = 5000
    for i in range(0, len(all_traces), chunk_size):
        chunk = all_traces[i:i + chunk_size]
        client.insert("otel_traces", chunk, column_names=col_names)
        print(f"    Inserted traces {i+1}-{min(i+chunk_size, len(all_traces))}")

    # Insert stack trace events
    if all_stack_events:
        print(f"  Inserting {len(all_stack_events)} stack trace events...")
        stack_cols = [
            "Timestamp", "EventName", "Title",
            "ExceptionStackTrace", "ExceptionStackTraceRaw", "ExceptionMessage", "ExceptionType",
            "Interactions", "ScreenName", "UserId", "SessionId",
            "Platform", "OsVersion", "DeviceModel", "AppVersionCode", "AppVersion",
            "SdkVersion", "BundleId", "TraceId", "SpanId",
            "GroupId", "Signature", "Fingerprint",
            "ScopeAttributes", "LogAttributes", "ResourceAttributes",
        ]

        for i in range(0, len(all_stack_events), chunk_size):
            chunk = all_stack_events[i:i + chunk_size]
            client.insert("stack_trace_events", chunk, column_names=stack_cols)
            print(f"    Inserted stack events {i+1}-{min(i+chunk_size, len(all_stack_events))}")


def main():
    print("=" * 70)
    print("  PULSE - Seed Data Generator")
    print(f"  Target: {NUM_SESSIONS} sessions for project '{PROJECT_ID}'")
    print("=" * 70)

    # Connect to LOCAL ClickHouse
    print("\n[1/4] Connecting to local ClickHouse...")
    client = clickhouse_connect.get_client(
        host="localhost",
        port=8123,
        username="pulse_user",
        password="pulse_password",
        database="otel",
    )
    r = client.query("SELECT 1")
    print("  Connected!")

    # Clean existing seed data
    print(f"\n[2/4] Cleaning existing '{PROJECT_ID}' data...")
    client.command(f"ALTER TABLE otel_traces DELETE WHERE ResourceAttributes['project.id'] = '{PROJECT_ID}'")
    client.command(f"ALTER TABLE stack_trace_events DELETE WHERE ResourceAttributes['project.id'] = '{PROJECT_ID}'")
    print("  Cleaned (mutations may run async)")

    # Generate sessions
    print(f"\n[3/4] Generating {NUM_SESSIONS} sessions...")
    all_traces = []
    all_stack_events = []
    stats = {
        "total_sessions": 0,
        "crashed_sessions": 0,
        "anr_sessions": 0,
        "payment_sessions": 0,
        "purchase_sessions": 0,
        "crash_screens": {},
        "anr_screens": {},
    }

    for i in range(NUM_SESSIONS):
        traces, stack_events = generate_session(i)
        all_traces.extend(traces)
        all_stack_events.extend(stack_events)

        stats["total_sessions"] += 1

        # Count crashes/ANRs
        for se in stack_events:
            pt = se[1]  # EventName = pulse_type
            sn = se[8]  # ScreenName
            if pt == "device.crash":
                stats["crashed_sessions"] += 1
                stats["crash_screens"][sn] = stats["crash_screens"].get(sn, 0) + 1
            elif pt == "device.anr":
                stats["anr_sessions"] += 1
                stats["anr_screens"][sn] = stats["anr_screens"].get(sn, 0) + 1

        # Check if reached payment
        screen_names = set()
        for t in traces:
            sa = t[8]  # SpanAttributes
            if sa.get("pulse.type") in ("screen_session", "screen_load"):
                screen_names.add(sa.get("screen.name", ""))
        if "com.fc.PaymentListing" in screen_names:
            stats["payment_sessions"] += 1
        if "com.fc.webScreenNewArch" in screen_names:
            stats["purchase_sessions"] += 1

        if (i + 1) % 1000 == 0:
            print(f"    Generated {i+1}/{NUM_SESSIONS} sessions ({len(all_traces)} spans)")

    print(f"\n  Total spans:           {len(all_traces)}")
    print(f"  Total stack events:    {len(all_stack_events)}")
    print(f"  Crashed sessions:      {stats['crashed_sessions']} ({stats['crashed_sessions']/NUM_SESSIONS:.1%})")
    print(f"  ANR sessions:          {stats['anr_sessions']} ({stats['anr_sessions']/NUM_SESSIONS:.1%})")
    print(f"  Payment sessions:      {stats['payment_sessions']} ({stats['payment_sessions']/NUM_SESSIONS:.1%})")
    print(f"  Purchase sessions:     {stats['purchase_sessions']} ({stats['purchase_sessions']/NUM_SESSIONS:.1%})")

    print(f"\n  Crash by screen:")
    for sn, cnt in sorted(stats["crash_screens"].items(), key=lambda x: -x[1]):
        print(f"    {sn:40s} {cnt:>5}")

    print(f"\n  ANR by screen:")
    for sn, cnt in sorted(stats["anr_screens"].items(), key=lambda x: -x[1]):
        print(f"    {sn:40s} {cnt:>5}")

    # Insert
    print(f"\n[4/4] Inserting into ClickHouse...")
    insert_data(client, all_traces, all_stack_events)

    # Verify
    print(f"\n  Verifying...")
    r = client.query(f"SELECT count(), uniqCombined64(SessionId) FROM otel_traces WHERE ProjectId = '{PROJECT_ID}'")
    print(f"  otel_traces:        {r.result_rows[0][0]:,} spans, {r.result_rows[0][1]:,} sessions")
    r = client.query(f"SELECT count(), uniqCombined64(SessionId) FROM stack_trace_events WHERE ProjectId = '{PROJECT_ID}'")
    print(f"  stack_trace_events: {r.result_rows[0][0]:,} events, {r.result_rows[0][1]:,} sessions")

    print(f"\n{'='*70}")
    print(f"  Done! Run the analysis with:")
    print(f"  python revenue_impact_prototype.py --project-id {PROJECT_ID}")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
