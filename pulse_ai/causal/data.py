"""
Data Extraction — temporal-aware ClickHouse queries.

Two modes of operation:
  - **MV mode** (use_mvs=True): Reads from pre-aggregated materialized views.
    Required for production at scale (>1M sessions). MVs must be created
    first — see backend/ingestion/clickhouse-causal-mvs.sql.

  - **Legacy mode** (use_mvs=False): Scans raw event tables with GROUP BY.
    Works without MVs but does full table scans. Fine for <100K sessions,
    will OOM or timeout at scale.

All queries return individual events with timestamps (not aggregates)
to support temporal ordering and journey conditioning.

Sampling:
  When max_sessions is set, queries use deterministic sampling via
  cityHash64(SessionId) to cap the working set. PSM only needs ~50K
  sessions for statistical validity (see benchmark.py for proof).
  This reduces memory from terabytes to megabytes at PB scale.
"""
import os
import logging
from typing import Optional

import pandas as pd

logger = logging.getLogger(__name__)


def get_ch_client():
    """Create ClickHouse client from environment variables."""
    import clickhouse_connect

    ch_host = os.getenv("CLICKHOUSE_HOST", "localhost")
    ch_host = ch_host.replace("https://", "").replace("http://", "").rstrip("/")

    return clickhouse_connect.get_client(
        host=ch_host,
        port=int(os.getenv("CLICKHOUSE_PORT", "8123")),
        username=os.getenv("CLICKHOUSE_USER", "default"),
        password=os.getenv("CLICKHOUSE_PASSWORD", ""),
        database=os.getenv("CLICKHOUSE_DATABASE", "otel"),
    )


def _detect_mvs(client) -> bool:
    """Check if causal materialized views exist."""
    try:
        r = client.query("EXISTS TABLE otel.causal_session_profiles")
        return r.result_rows[0][0] == 1
    except Exception:
        return False


# ═══════════════════════════════════════════════════════════════════
# Session Profiles
# ═══════════════════════════════════════════════════════════════════

def get_session_profiles(
    client,
    project_id: str,
    lookback_days: int,
    max_sessions: Optional[int] = None,
    use_mvs: Optional[bool] = None,
) -> pd.DataFrame:
    """
    Session-level device context for propensity matching.

    Note: unique_screens / net_error_count are for REPORTING only,
    NOT included in matching features (they are post-treatment).

    Args:
        max_sessions: Cap the result set via deterministic sampling.
            At PB scale, set to 50000-100000 for PSM (statistically sufficient).
        use_mvs: Force MV mode (True) or legacy mode (False).
            If None, auto-detects whether MVs exist.
    """
    if use_mvs is None:
        use_mvs = _detect_mvs(client)

    if use_mvs:
        return _get_session_profiles_mv(client, project_id, lookback_days, max_sessions)
    else:
        return _get_session_profiles_legacy(client, project_id, lookback_days, max_sessions)


def _get_session_profiles_mv(client, project_id, lookback_days, max_sessions):
    """Read from causal_session_profiles materialized view."""
    limit_clause = f"LIMIT {max_sessions}" if max_sessions else ""
    # For AggregatingMergeTree, use -Merge combinators in the final SELECT
    query = f"""
    SELECT
        SessionId AS session_id,
        anyMerge(UserId) AS user_id,
        anyMerge(DeviceModel) AS device_model,
        anyMerge(OsVersion) AS os_version,
        anyMerge(AppVersion) AS app_version,
        anyMerge(Platform) AS platform,
        anyMerge(GeoCountry) AS geo_country,
        anyMerge(NetworkProvider) AS network_provider,
        toHour(min(SessionStart)) AS session_hour,
        min(SessionStart) AS session_start,
        max(SessionEnd) AS session_end,
        dateDiff('second', min(SessionStart), max(SessionEnd)) AS session_duration_sec,
        length(groupUniqArrayMerge(ScreenNames)) AS unique_screens,
        sum(TotalNetworkCalls) AS total_network_calls,
        sum(NetErrorCount) AS net_error_count,
        sum(NetTimeoutCount) AS net_timeout_count
    FROM causal_session_profiles
    WHERE ProjectId = %(pid)s
      AND SessionStart >= now() - INTERVAL %(days)s DAY
    GROUP BY SessionId
    HAVING unique_screens > 0
    ORDER BY cityHash64(SessionId)
    {limit_clause}
    """
    logger.info("get_session_profiles: using MV (max_sessions=%s)", max_sessions)
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "user_id", "device_model", "os_version", "app_version",
        "platform", "geo_country", "network_provider", "session_hour",
        "session_start", "session_end", "session_duration_sec",
        "unique_screens", "total_network_calls", "net_error_count", "net_timeout_count",
    ])


def _get_session_profiles_legacy(client, project_id, lookback_days, max_sessions):
    """Full table scan fallback (fine for <100K sessions)."""
    limit_clause = f"LIMIT {max_sessions}" if max_sessions else ""
    query = f"""
    SELECT
        SessionId AS session_id,
        any(UserId) AS user_id,
        any(DeviceModel) AS device_model,
        any(OsVersion) AS os_version,
        any(AppVersion) AS app_version,
        any(Platform) AS platform,
        any(GeoCountry) AS geo_country,
        any(NetworkProvider) AS network_provider,
        toHour(min(Timestamp)) AS session_hour,
        min(Timestamp) AS session_start,
        max(Timestamp) AS session_end,
        dateDiff('second', min(Timestamp), max(Timestamp)) AS session_duration_sec,
        count(DISTINCT CASE
            WHEN PulseType IN ('screen_session', 'screen_load')
            THEN SpanAttributes['screen.name'] END) AS unique_screens,
        countIf(PulseType LIKE 'network.%%') AS total_network_calls,
        countIf(PulseType LIKE 'network.4%%' OR PulseType LIKE 'network.5%%') AS net_error_count,
        countIf(PulseType = 'network.0') AS net_timeout_count
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY SessionId
    HAVING unique_screens > 0
    ORDER BY cityHash64(SessionId)
    {limit_clause}
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "user_id", "device_model", "os_version", "app_version",
        "platform", "geo_country", "network_provider", "session_hour",
        "session_start", "session_end", "session_duration_sec",
        "unique_screens", "total_network_calls", "net_error_count", "net_timeout_count",
    ])


# ═══════════════════════════════════════════════════════════════════
# Screen Visits
# ═══════════════════════════════════════════════════════════════════

def get_screen_visits(
    client,
    project_id: str,
    lookback_days: int,
    session_ids: Optional[set] = None,
    use_mvs: Optional[bool] = None,
) -> pd.DataFrame:
    """
    Per-session screen visits with first-visit timestamps.

    Args:
        session_ids: If provided, only return visits for these sessions.
            This is the key optimization — after sampling session profiles,
            only fetch screen visits for the sampled sessions.
    """
    if use_mvs is None:
        use_mvs = _detect_mvs(client)

    if use_mvs:
        return _get_screen_visits_mv(client, project_id, lookback_days, session_ids)
    else:
        return _get_screen_visits_legacy(client, project_id, lookback_days, session_ids)


def _get_screen_visits_mv(client, project_id, lookback_days, session_ids):
    """Read from causal_screen_visits MV."""
    session_filter = ""
    params = {"pid": project_id, "days": lookback_days}
    if session_ids:
        session_filter = "AND SessionId IN %(sids)s"
        params["sids"] = list(session_ids)

    query = f"""
    SELECT
        SessionId AS session_id,
        ScreenName AS screen_name,
        min(FirstVisitTs) AS first_visit_ts
    FROM causal_screen_visits
    WHERE ProjectId = %(pid)s
      AND FirstVisitTs >= now() - INTERVAL %(days)s DAY
      {session_filter}
    GROUP BY SessionId, ScreenName
    ORDER BY SessionId, first_visit_ts
    """
    r = client.query(query, parameters=params)
    return pd.DataFrame(r.result_rows, columns=["session_id", "screen_name", "first_visit_ts"])


def _get_screen_visits_legacy(client, project_id, lookback_days, session_ids):
    """Full table scan fallback."""
    session_filter = ""
    params = {"pid": project_id, "days": lookback_days}
    if session_ids:
        session_filter = "AND SessionId IN %(sids)s"
        params["sids"] = list(session_ids)

    query = f"""
    SELECT
        SessionId AS session_id,
        SpanAttributes['screen.name'] AS screen_name,
        min(Timestamp) AS first_visit_ts
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND PulseType IN ('screen_session', 'screen_load')
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
      AND SpanAttributes['screen.name'] != ''
      {session_filter}
    GROUP BY SessionId, screen_name
    ORDER BY SessionId, first_visit_ts
    """
    r = client.query(query, parameters=params)
    return pd.DataFrame(r.result_rows, columns=["session_id", "screen_name", "first_visit_ts"])


# ═══════════════════════════════════════════════════════════════════
# Issue Events (stack_trace_events — always direct, table is small)
# ═══════════════════════════════════════════════════════════════════

def get_issue_events(
    client,
    project_id: str,
    lookback_days: int,
    session_ids: Optional[set] = None,
) -> pd.DataFrame:
    """
    Individual crash/ANR/non-fatal events WITH timestamps.

    stack_trace_events is small (~0.1% of sessions have crashes),
    so no MV needed. Session filter applied when sampling is active.
    """
    session_filter = ""
    params = {"pid": project_id, "days": lookback_days}
    if session_ids:
        session_filter = "AND SessionId IN %(sids)s"
        params["sids"] = list(session_ids)

    query = f"""
    SELECT
        SessionId AS session_id,
        PulseType AS pulse_type,
        ScreenName AS screen_name,
        Timestamp AS issue_timestamp,
        ExceptionType AS exception_type
    FROM stack_trace_events
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
      {session_filter}
    ORDER BY SessionId, issue_timestamp
    """
    r = client.query(query, parameters=params)
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "pulse_type", "screen_name", "issue_timestamp", "exception_type",
    ])


# ═══════════════════════════════════════════════════════════════════
# Conversion Events
# ═══════════════════════════════════════════════════════════════════

def get_conversion_events(
    client,
    project_id: str,
    lookback_days: int,
    op_name: str,
    session_ids: Optional[set] = None,
    use_mvs: Optional[bool] = None,
) -> pd.DataFrame:
    """Individual conversion events WITH timestamps (for temporal ordering)."""
    if use_mvs is None:
        use_mvs = _detect_mvs(client)

    session_filter = ""
    params = {"pid": project_id, "days": lookback_days, "op": op_name}
    if session_ids:
        session_filter = "AND SessionId IN %(sids)s"
        params["sids"] = list(session_ids)

    if use_mvs:
        query = f"""
        SELECT
            SessionId AS session_id,
            ConversionTimestamp AS conversion_timestamp
        FROM causal_conversion_events
        WHERE ProjectId = %(pid)s
          AND OperationName = %(op)s
          AND ConversionTimestamp >= now() - INTERVAL %(days)s DAY
          {session_filter}
        ORDER BY SessionId, conversion_timestamp
        """
    else:
        query = f"""
        SELECT
            SessionId AS session_id,
            Timestamp AS conversion_timestamp
        FROM otel_traces
        WHERE ProjectId = %(pid)s
          AND PulseType LIKE 'network.2%%'
          AND coalesce(
              nullIf(SpanAttributes['graphql.operation.name'], ''),
              SpanAttributes['http.request.header.operation_name']
          ) = %(op)s
          AND Timestamp >= now() - INTERVAL %(days)s DAY
          AND SessionId != ''
          {session_filter}
        ORDER BY SessionId, conversion_timestamp
        """

    r = client.query(query, parameters=params)
    return pd.DataFrame(r.result_rows, columns=["session_id", "conversion_timestamp"])


# ═══════════════════════════════════════════════════════════════════
# Jank Events by Screen
# ═══════════════════════════════════════════════════════════════════

def get_jank_events_by_screen(
    client,
    project_id: str,
    lookback_days: int,
    session_ids: Optional[set] = None,
    use_mvs: Optional[bool] = None,
) -> pd.DataFrame:
    """Jank events with timestamps grouped by session+screen."""
    if use_mvs is None:
        use_mvs = _detect_mvs(client)

    session_filter = ""
    params = {"pid": project_id, "days": lookback_days}
    if session_ids:
        session_filter = "AND SessionId IN %(sids)s"
        params["sids"] = list(session_ids)

    if use_mvs:
        query = f"""
        SELECT
            SessionId AS session_id,
            PulseType AS pulse_type,
            ScreenName AS screen_name,
            min(FirstTimestamp) AS issue_timestamp,
            sum(EventCount) AS event_count
        FROM causal_jank_by_screen
        WHERE ProjectId = %(pid)s
          AND FirstTimestamp >= now() - INTERVAL %(days)s DAY
          {session_filter}
        GROUP BY SessionId, PulseType, ScreenName
        """
    else:
        query = f"""
        SELECT
            SessionId AS session_id,
            PulseType AS pulse_type,
            LogAttributes['screen.name'] AS screen_name,
            min(Timestamp) AS issue_timestamp,
            count() AS event_count
        FROM otel_logs
        WHERE ProjectId = %(pid)s
          AND PulseType IN ('app.jank.slow', 'app.jank.frozen')
          AND Timestamp >= now() - INTERVAL %(days)s DAY
          AND SessionId != ''
          AND LogAttributes['screen.name'] != ''
          {session_filter}
        GROUP BY session_id, pulse_type, screen_name
        """

    r = client.query(query, parameters=params)
    if not r.result_rows:
        return pd.DataFrame(columns=["session_id", "pulse_type", "screen_name", "issue_timestamp", "event_count"])
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "pulse_type", "screen_name", "issue_timestamp", "event_count",
    ])


# ═══════════════════════════════════════════════════════════════════
# Log Signals (frustration scoring)
# ═══════════════════════════════════════════════════════════════════

def get_log_signals(
    client,
    project_id: str,
    lookback_days: int,
    session_ids: Optional[set] = None,
    use_mvs: Optional[bool] = None,
) -> pd.DataFrame:
    """Aggregated log signals per session (for frustration scoring, NOT matching)."""
    if use_mvs is None:
        use_mvs = _detect_mvs(client)

    session_filter = ""
    params = {"pid": project_id, "days": lookback_days}
    if session_ids:
        session_filter = "AND SessionId IN %(sids)s"
        params["sids"] = list(session_ids)

    if use_mvs:
        query = f"""
        SELECT
            SessionId AS session_id,
            sum(JankSlowCount) AS jank_slow_count,
            sum(JankFrozenCount) AS jank_frozen_count,
            sum(ClickCount) AS click_count,
            sum(NetworkChangeCount) AS network_change_count
        FROM causal_log_signals
        WHERE ProjectId = %(pid)s
          {session_filter}
        GROUP BY SessionId
        """
    else:
        query = f"""
        SELECT
            SessionId AS session_id,
            countIf(PulseType = 'app.jank.slow') AS jank_slow_count,
            countIf(PulseType = 'app.jank.frozen') AS jank_frozen_count,
            countIf(PulseType = 'app.click') AS click_count,
            countIf(PulseType = 'network.change') AS network_change_count
        FROM otel_logs
        WHERE ProjectId = %(pid)s
          AND Timestamp >= now() - INTERVAL %(days)s DAY
          AND SessionId != ''
          {session_filter}
        GROUP BY session_id
        """

    r = client.query(query, parameters=params)
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "jank_slow_count", "jank_frozen_count", "click_count", "network_change_count",
    ])


# ═══════════════════════════════════════════════════════════════════
# Conversion Proxy Discovery
# ═══════════════════════════════════════════════════════════════════

def discover_conversion_proxies(
    client,
    project_id: str,
    lookback_days: int,
    total_sessions: int,
    use_mvs: Optional[bool] = None,
) -> list:
    """
    Auto-discover conversion signals from GraphQL operation names.

    With MVs: reads from causal_network_operations (a few thousand rows).
    Without MVs: scans all network events and extracts Map values per row.
    """
    from .models import ConversionProxy

    if use_mvs is None:
        use_mvs = _detect_mvs(client)

    CONVERSION_KEYWORDS = [
        "payment", "pay", "purchase", "order", "checkout", "subscribe",
        "entitlement", "transaction", "billing", "cart", "buy",
        "redeem", "coupon", "promo", "reward",
    ]
    ENGAGEMENT_KEYWORDS = [
        "watchlist", "follow", "preference", "notification", "profile",
        "review", "feedback", "share", "invite",
    ]

    if use_mvs:
        query = """
        SELECT
            OperationName AS op_name,
            HttpMethod AS method,
            uniqCombined64Merge(UniqueSessions) AS unique_sessions,
            sum(TotalCalls) AS total_calls
        FROM causal_network_operations
        WHERE ProjectId = %(pid)s
          AND Day >= today() - %(days)s
        GROUP BY OperationName, HttpMethod
        HAVING unique_sessions >= 3
        ORDER BY unique_sessions DESC
        """
    else:
        query = """
        SELECT
            coalesce(
                nullIf(SpanAttributes['graphql.operation.name'], ''),
                SpanAttributes['http.request.header.operation_name']
            ) AS op_name,
            SpanAttributes['http.method'] AS method,
            uniqCombined64(SessionId) AS unique_sessions,
            count() AS total_calls
        FROM otel_traces
        WHERE ProjectId = %(pid)s
          AND PulseType LIKE 'network.%%'
          AND Timestamp >= now() - INTERVAL %(days)s DAY
          AND (
              SpanAttributes['graphql.operation.name'] != ''
              OR SpanAttributes['http.request.header.operation_name'] != ''
          )
        GROUP BY op_name, method
        HAVING unique_sessions >= 3
        ORDER BY unique_sessions DESC
        """

    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    ops = pd.DataFrame(r.result_rows, columns=["op_name", "method", "unique_sessions", "total_calls"])

    proxies = []
    for _, row in ops.iterrows():
        op_lower = row["op_name"].lower()
        is_conv = any(kw in op_lower for kw in CONVERSION_KEYWORDS)
        is_eng = any(kw in op_lower for kw in ENGAGEMENT_KEYWORDS)
        if is_conv or is_eng:
            proxies.append(ConversionProxy(
                proxy_type="graphql_conversion" if is_conv else "graphql_engagement",
                identifier=f"{row['method']} {row['op_name']}",
                sessions_reached=int(row["unique_sessions"]),
                total_sessions=total_sessions,
                conversion_rate=row["unique_sessions"] / total_sessions if total_sessions > 0 else 0,
            ))

    # Rank proxies: prefer actual transaction operations (createOrder, purchase,
    # verifyPurchase) over check/validation operations (validateEntitlement,
    # getPaymentConfigs). High-intent keywords get priority 0, general payment
    # keywords get priority 1, engagement gets priority 2.
    HIGH_INTENT_KEYWORDS = [
        "create", "place", "complete", "confirm", "process", "submit",
        "buy", "purchase", "verify", "redeem",
    ]

    def _proxy_sort_key(p):
        type_pri = {"graphql_conversion": 0, "url_conversion": 1, "graphql_engagement": 2}
        op_lower = p.identifier.lower()
        is_high_intent = any(kw in op_lower for kw in HIGH_INTENT_KEYWORDS)
        intent_pri = 0 if is_high_intent else 1
        # Within same tier, prefer moderate reach (2-20%) over too broad (>50%)
        # or too narrow (<1%). Ideal conversion rate is 2-20%.
        rate = p.conversion_rate
        if 0.02 <= rate <= 0.20:
            rate_pri = 0
        elif rate < 0.02:
            rate_pri = 2  # Too narrow — might be noise
        else:
            rate_pri = 1  # Too broad — likely a check, not a conversion
        return (type_pri.get(p.proxy_type, 99), intent_pri, rate_pri, -p.sessions_reached)

    proxies.sort(key=_proxy_sort_key)
    return proxies
