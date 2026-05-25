"""S3 parquet input/output for suggested interaction mining."""

from __future__ import annotations

import json
import re
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Any
from urllib.parse import urlparse

DEFAULT_S3_BUCKET = "pulse-otel-ingestion"
DEFAULT_S3_TABLE = "otel_logs"
DEFAULT_LOOKBACK_DAYS = 7
DEFAULT_PULSE_TYPE_FILTER = "custom_event"
PULSE_TYPE_PROP_KEYS: tuple[str, ...] = ("pulse.type", "pulse_type", "PulseType")

_EVENT_COL_ALIASES = ("event_name", "EventName", "eventName", "SpanName")
_EVENT_FALLBACK_COL_ALIASES = ("PulseType", "pulse_type", "pulse.type")
_SESSION_COL_ALIASES = ("session_id", "SessionId", "sessionId")
_TS_COL_ALIASES = ("timestamp", "Timestamp", "time", "event_timestamp")
_PROPS_COL_ALIASES = ("props", "Props", "attributes", "LogAttributes")
_PROJECT_COL_ALIASES = ("project_id", "ProjectId", "projectId")
# Scalar parquet columns merged into props for profiling / hints.
_EXTRA_PROP_COLUMNS: tuple[str, ...] = (
    "ScreenName",
    "ClickType",
    "Platform",
    "AppVersion",
    "PulseType",
    "ScopeName",
    "ServiceName",
    "GeoCountry",
    "GeoState",
    "DeviceModel",
    "UserId",
    "Rage",
    "WebVitalName",
    "Body",
)


@dataclass(frozen=True)
class S3InputConfig:
    project_id: str
    bucket: str = DEFAULT_S3_BUCKET
    table: str = DEFAULT_S3_TABLE
    lookback_days: int = DEFAULT_LOOKBACK_DAYS
    end_date: date | None = None

    @property
    def base_uri(self) -> str:
        return f"s3://{self.bucket}"

    def table_prefix(self) -> str:
        return f"{self.project_id}/{self.table}/"

    def partition_prefix(self, d: date) -> str:
        return (
            f"{self.table_prefix()}"
            f"year={d.year}/month={d.month:02d}/day={d.day:02d}/"
        )

    def partition_uri(self, d: date) -> str:
        return f"{self.base_uri}/{self.partition_prefix(d)}"


@dataclass(frozen=True)
class S3OutputConfig:
    project_id: str
    bucket: str
    output_prefix: str
    run_timestamp: datetime
    start_date: date
    end_date: date
    suggestion_count: int

    def object_key(self) -> str:
        ts = self.run_timestamp.strftime("%Y%m%dT%H%M%SZ")
        start = self.start_date.isoformat()
        end = self.end_date.isoformat()
        name = (
            f"suggested_interactions_{self.project_id}_{ts}"
            f"_{start}_to_{end}_n{self.suggestion_count}.json"
        )
        prefix = self.output_prefix.rstrip("/")
        if prefix.startswith("s3://"):
            parsed = urlparse(prefix)
            base_key = parsed.path.lstrip("/")
            return f"{base_key}/{name}" if base_key else name
        return f"{prefix}/{name}" if prefix else name

    @property
    def uri(self) -> str:
        return f"s3://{self.bucket}/{self.object_key()}"


def resolve_end_date(end_date: date | None) -> date:
    return end_date or datetime.now(timezone.utc).date()


def iter_lookback_dates(end: date, lookback_days: int) -> list[date]:
    n = max(1, lookback_days)
    return [end - timedelta(days=offset) for offset in range(n - 1, -1, -1)]


def resolve_aws_profile(cli_profile: str | None) -> str | None:
    """CLI flag wins, then AWS_PROFILE env var."""
    import os

    if cli_profile and cli_profile.strip():
        return cli_profile.strip()
    env = os.environ.get("AWS_PROFILE", "").strip()
    return env or None


def create_s3_client(aws_profile: str | None = None) -> Any:
    import boto3

    if aws_profile:
        return boto3.Session(profile_name=aws_profile).client("s3")
    return boto3.client("s3")


def create_pyarrow_s3_filesystem(aws_profile: str | None = None) -> Any:
    """PyArrow filesystem using the same credentials as boto3 profile."""
    import pyarrow.fs as pafs

    if not aws_profile:
        return pafs.S3FileSystem()

    import boto3

    session = boto3.Session(profile_name=aws_profile)
    creds = session.get_credentials()
    if creds is None:
        raise ValueError(f"No AWS credentials found for profile '{aws_profile}'")
    frozen = creds.get_frozen_credentials()
    fs_kwargs: dict[str, Any] = {}
    if frozen.access_key:
        fs_kwargs["access_key"] = frozen.access_key
    if frozen.secret_key:
        fs_kwargs["secret_key"] = frozen.secret_key
    if frozen.token:
        fs_kwargs["session_token"] = frozen.token
    if session.region_name:
        fs_kwargs["region"] = session.region_name
    return pafs.S3FileSystem(**fs_kwargs)


def _paths_for_pyarrow(partition_uris: list[str]) -> list[str]:
    """PyArrow custom S3 filesystem expects paths without s3:// scheme."""
    out: list[str] = []
    for uri in partition_uris:
        out.append(uri[len("s3://"):] if uri.startswith("s3://") else uri)
    return out


def _pick_column(available: set[str], aliases: tuple[str, ...]) -> str | None:
    lower_map = {c.lower(): c for c in available}
    for alias in aliases:
        hit = lower_map.get(alias.lower())
        if hit:
            return hit
    return None


def list_parquet_object_paths(
    bucket: str,
    prefix: str,
    *,
    aws_profile: str | None = None,
    s3_client: Any | None = None,
) -> list[str]:
    """List data object keys under a partition prefix (for PyArrow file dataset)."""
    client = s3_client or create_s3_client(aws_profile)
    paths: list[str] = []
    paginator = client.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        for obj in page.get("Contents") or []:
            key = obj.get("Key")
            if not key or key.endswith("/"):
                continue
            paths.append(f"{bucket}/{key}")
    return paths


def list_existing_partition_uris(
    config: S3InputConfig,
    *,
    aws_profile: str | None = None,
    s3_client: Any | None = None,
) -> tuple[list[str], date, date]:
    """Return partition URIs that contain at least one object."""
    client = s3_client or create_s3_client(aws_profile)
    end = resolve_end_date(config.end_date)
    dates = iter_lookback_dates(end, config.lookback_days)
    uris: list[str] = []
    for d in dates:
        prefix = config.partition_prefix(d)
        resp = client.list_objects_v2(Bucket=config.bucket, Prefix=prefix, MaxKeys=1)
        if resp.get("KeyCount", 0) > 0 or resp.get("Contents"):
            uris.append(config.partition_uri(d))
    if not uris:
        raise FileNotFoundError(
            f"No data under s3://{config.bucket}/{config.table_prefix()} "
            f"for {dates[0]} .. {dates[-1]} ({config.lookback_days} day lookback)"
        )
    return uris, dates[0], dates[-1]


def _parquet_paths_for_partition_uris(
    partition_uris: list[str],
    *,
    aws_profile: str | None = None,
) -> list[str]:
    client = create_s3_client(aws_profile)
    file_paths: list[str] = []
    for uri in partition_uris:
        if uri.startswith("s3://"):
            without_scheme = uri[len("s3://") :]
            bucket, _, prefix = without_scheme.partition("/")
        else:
            bucket, _, prefix = uri.partition("/")
        file_paths.extend(
            list_parquet_object_paths(bucket, prefix, s3_client=client)
        )
    return file_paths


def pulse_type_from_props(props: dict[str, Any] | None) -> str:
    if not props:
        return ""
    for key in PULSE_TYPE_PROP_KEYS:
        val = props.get(key)
        if val is None:
            continue
        text = str(val).strip()
        if text and text.lower() not in ("none", "nan"):
            return text
    return ""


def props_match_pulse_type_filter(
    props: dict[str, Any] | None,
    pulse_type_filter: str | None,
) -> bool:
    if not pulse_type_filter or not str(pulse_type_filter).strip():
        return True
    return pulse_type_from_props(props).lower() == str(pulse_type_filter).strip().lower()


def filter_dataframe_by_pulse_type(
    df: Any,
    pulse_type_filter: str | None,
) -> Any:
    """Keep only rows whose props carry the requested pulse.type."""
    if df.empty or not pulse_type_filter or not str(pulse_type_filter).strip():
        return df
    mask = df["props"].apply(
        lambda p: props_match_pulse_type_filter(p if isinstance(p, dict) else {}, pulse_type_filter)
    )
    return df[mask].reset_index(drop=True)


def _coerce_parquet_attributes(raw: Any) -> dict[str, Any]:
    """Normalize LogAttributes / map columns (list, dict, JSON string) to a flat dict."""
    if raw is None:
        return {}
    if isinstance(raw, dict):
        return {str(k): v for k, v in raw.items()}
    if isinstance(raw, list):
        out: dict[str, Any] = {}
        for item in raw:
            if isinstance(item, (list, tuple)) and len(item) >= 2:
                out[str(item[0])] = item[1]
            elif isinstance(item, dict):
                key = item.get("key") or item.get("Key")
                if key is not None:
                    out[str(key)] = item.get("value") or item.get("Value")
        return out
    if isinstance(raw, str):
        text = raw.strip()
        if not text:
            return {}
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            return {}
        if isinstance(parsed, dict):
            return {str(k): v for k, v in parsed.items()}
        if isinstance(parsed, list):
            return _coerce_parquet_attributes(parsed)
    return {}


def _extra_prop_columns(available: set[str]) -> tuple[str, ...]:
    return tuple(c for c in _EXTRA_PROP_COLUMNS if c in available)


def _event_fallback_column(available: set[str]) -> str | None:
    return _pick_column(available, _EVENT_FALLBACK_COL_ALIASES)


def _resolve_parquet_columns(available: set[str]) -> tuple[str, str, str, str | None, str | None]:
    event_col = _pick_column(available, _EVENT_COL_ALIASES)
    session_col = _pick_column(available, _SESSION_COL_ALIASES)
    ts_col = _pick_column(available, _TS_COL_ALIASES)
    props_col = _pick_column(available, _PROPS_COL_ALIASES)
    project_col = _pick_column(available, _PROJECT_COL_ALIASES)
    if not event_col or not session_col or not ts_col:
        raise ValueError(
            f"Parquet schema missing required columns. Found: {sorted(available)}. "
            f"Need event, session, and timestamp (aliases: {_EVENT_COL_ALIASES}, ...)."
        )
    return event_col, session_col, ts_col, props_col, project_col


def _table_to_events_df(
    table: Any,
    *,
    event_col: str,
    session_col: str,
    ts_col: str,
    props_col: str | None,
    project_col: str | None,
    project_id: str | None,
    parse_props_fn: Any,
    event_fallback_col: str | None = None,
    extra_prop_cols: tuple[str, ...] = (),
    pulse_type_filter: str | None = DEFAULT_PULSE_TYPE_FILTER,
) -> Any:
    import pandas as pd

    df = table.to_pandas()
    if df.empty:
        return pd.DataFrame(columns=["session_id", "timestamp", "event_name", "props"])

    if project_id and project_col:
        want = project_id.strip().lower()
        df = df[df[project_col].astype(str).str.strip().str.lower() == want]
        if df.empty:
            return pd.DataFrame(columns=["session_id", "timestamp", "event_name", "props"])

    def _row_props(row: Any) -> dict[str, Any]:
        merged = _coerce_parquet_attributes(row[props_col]) if props_col else {}
        for col in extra_prop_cols:
            val = row.get(col)
            if val is None:
                continue
            sv = str(val).strip()
            if sv and sv.lower() not in ("none", "nan", ""):
                merged[col] = val
        return parse_props_fn(merged)

    if props_col or extra_prop_cols:
        df["props"] = df.apply(_row_props, axis=1)
    else:
        df["props"] = [{} for _ in range(len(df))]

    event_name = df[event_col].astype(str).str.strip()
    if event_fallback_col:
        fallback = df[event_fallback_col].astype(str).str.strip()
        event_name = event_name.where(event_name != "", fallback)

    out = pd.DataFrame(
        {
            "session_id": df[session_col].astype(str),
            "timestamp": pd.to_datetime(df[ts_col], utc=True),
            "event_name": event_name,
            "props": df["props"],
        }
    )
    out = out.dropna(subset=["session_id", "timestamp"])
    out = filter_dataframe_by_pulse_type(out, pulse_type_filter)
    if out.empty:
        return out
    return out.sort_values(["session_id", "timestamp"]).reset_index(drop=True)


def iter_partition_event_frames(
    partition_uris: list[str],
    *,
    project_id: str | None = None,
    parse_props_fn: Any,
    aws_profile: str | None = None,
    pulse_type_filter: str | None = DEFAULT_PULSE_TYPE_FILTER,
) -> Any:
    """Yield one normalized events DataFrame per parquet object (memory-safe)."""
    import pyarrow.dataset as pds

    if not partition_uris:
        return

    filesystem = create_pyarrow_s3_filesystem(aws_profile)
    file_paths = _parquet_paths_for_partition_uris(partition_uris, aws_profile=aws_profile)
    if not file_paths:
        raise FileNotFoundError(
            f"No parquet objects found under partition URIs: {partition_uris[:3]}..."
        )

    sample = pds.dataset([file_paths[0]], format="parquet", filesystem=filesystem)
    schema_cols = set(sample.schema.names)
    event_col, session_col, ts_col, props_col, project_col = _resolve_parquet_columns(
        schema_cols
    )
    event_fallback_col = _event_fallback_column(schema_cols)
    extra_prop_cols = _extra_prop_columns(schema_cols)
    read_cols = list(
        dict.fromkeys(
            c
            for c in (
                event_col,
                session_col,
                ts_col,
                props_col,
                project_col,
                event_fallback_col,
                *extra_prop_cols,
            )
            if c
        )
    )

    for path in file_paths:
        last_err: Exception | None = None
        table = None
        for attempt in range(1, 4):
            try:
                table = pds.dataset(path, format="parquet", filesystem=filesystem).to_table(
                    columns=read_cols
                )
                last_err = None
                break
            except OSError as exc:
                last_err = exc
                if attempt < 3:
                    time.sleep(min(2 ** attempt, 8))
        if last_err is not None:
            raise last_err
        frame = _table_to_events_df(
            table,
            event_col=event_col,
            session_col=session_col,
            ts_col=ts_col,
            props_col=props_col,
            project_col=project_col,
            project_id=project_id,
            parse_props_fn=parse_props_fn,
            event_fallback_col=event_fallback_col,
            extra_prop_cols=extra_prop_cols,
            pulse_type_filter=pulse_type_filter,
        )
        if not frame.empty:
            yield frame


def load_events_parquet(
    partition_uris: list[str],
    *,
    project_id: str | None = None,
    parse_props_fn: Any,
    aws_profile: str | None = None,
    pulse_type_filter: str | None = DEFAULT_PULSE_TYPE_FILTER,
) -> Any:
    """Load session events from parquet partitions into a pandas DataFrame."""
    import pandas as pd

    if not partition_uris:
        return pd.DataFrame(columns=["session_id", "timestamp", "event_name", "props"])

    frames = list(
        iter_partition_event_frames(
            partition_uris,
            project_id=project_id,
            parse_props_fn=parse_props_fn,
            aws_profile=aws_profile,
            pulse_type_filter=pulse_type_filter,
        )
    )
    if not frames:
        return pd.DataFrame(columns=["session_id", "timestamp", "event_name", "props"])
    file_count = len(_parquet_paths_for_partition_uris(partition_uris, aws_profile=aws_profile))
    print(f">>> Loading {file_count} parquet object(s) from S3")
    if len(frames) == 1:
        return frames[0]
    return pd.concat(frames, ignore_index=True).sort_values(
        ["session_id", "timestamp"]
    ).reset_index(drop=True)


def write_json_to_s3(
    payload: dict[str, Any],
    output: S3OutputConfig,
    *,
    aws_profile: str | None = None,
    s3_client: Any | None = None,
) -> str:
    client = s3_client or create_s3_client(aws_profile)
    body = json.dumps(payload, indent=2, default=str).encode("utf-8")
    client.put_object(
        Bucket=output.bucket,
        Key=output.object_key(),
        Body=body,
        ContentType="application/json",
    )
    return output.uri


def default_output_prefix(project_id: str, table: str = "suggested_interactions") -> str:
    return f"{project_id}/{table}/"


def parse_s3_output_uri(uri: str, project_id: str) -> tuple[str, str]:
    """Parse s3://bucket/prefix/ into (bucket, prefix)."""
    parsed = urlparse(uri)
    if parsed.scheme != "s3" or not parsed.netloc:
        raise ValueError(f"Invalid S3 output URI: {uri}")
    bucket = parsed.netloc
    prefix = parsed.path.lstrip("/")
    if not prefix:
        prefix = default_output_prefix(project_id)
    return bucket, prefix
