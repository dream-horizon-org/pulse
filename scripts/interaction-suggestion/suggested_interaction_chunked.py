"""Memory-safe partition-by-partition processing for S3 parquet input."""

from __future__ import annotations

import gc
import math
import random
from collections import Counter, defaultdict
from typing import Any

import numpy as np
import pandas as pd

from suggested_interaction_s3 import iter_partition_event_frames, load_events_parquet


class SessionReservoirSampler:
    """Uniform reservoir sample of unique session_ids over a streaming pass."""

    def __init__(self, max_sessions: int, *, seed: int = 42) -> None:
        self.max_sessions = max(1, int(max_sessions))
        self._rng = random.Random(seed)
        self._seen: set[str] = set()
        self._reservoir: list[str] = []
        self._stream_count = 0

    def update_from_df(self, df: pd.DataFrame) -> None:
        if df.empty:
            return
        for sid in df["session_id"].astype(str).unique():
            if sid in self._seen:
                continue
            self._seen.add(sid)
            self._stream_count += 1
            if len(self._reservoir) < self.max_sessions:
                self._reservoir.append(sid)
            else:
                j = self._rng.randint(0, self._stream_count - 1)
                if j < self.max_sessions:
                    self._reservoir[j] = sid

    @property
    def sessions(self) -> frozenset[str]:
        return frozenset(self._reservoir)

    @property
    def sessions_seen(self) -> int:
        return self._stream_count


class ActionInferenceAccumulator:
    """Merge action-event scoring state across dataframe chunks."""

    def __init__(self) -> None:
        self.all_sessions: set[str] = set()
        self.event_sessions: dict[str, set[str]] = defaultdict(set)
        self.next_counts: dict[str, Counter[str]] = defaultdict(Counter)
        self.prev_counts: dict[str, Counter[str]] = defaultdict(Counter)
        self.gap_samples: dict[str, list[float]] = defaultdict(list)
        self._max_gap_samples = 5000

    def update_from_df(self, df: pd.DataFrame) -> None:
        if df.empty:
            return
        self.all_sessions.update(df["session_id"].astype(str).unique())
        for sid, g in df.groupby("session_id", sort=False):
            g = g.sort_values("timestamp")
            names = g["event_name"].tolist()
            ts = g["timestamp"].tolist()
            m = len(names)
            for i in range(m):
                en = str(names[i]) if names[i] is not None else "?"
                self.event_sessions[en].add(str(sid))
            for i in range(m - 1):
                a = str(names[i]) if names[i] is not None else "?"
                b = str(names[i + 1]) if names[i + 1] is not None else "?"
                gap = (ts[i + 1] - ts[i]).total_seconds()
                if gap < 0:
                    continue
                self.next_counts[a][b] += 1
                self.prev_counts[b][a] += 1
                if len(self.gap_samples[a]) < self._max_gap_samples:
                    self.gap_samples[a].append(gap)


class RuntimeGapAccumulator:
    """Samples inter-event gaps for --auto-thresholds across chunks."""

    def __init__(self, max_samples: int = 300_000) -> None:
        self.max_samples = max_samples
        self.gaps: list[float] = []
        self.action_to_next_gaps: list[float] = []
        self.action_spans: list[float] = []

    def update_from_df(self, df: pd.DataFrame, action_events: set[str]) -> None:
        if df.empty:
            return
        for _, g in df.groupby("session_id", sort=False):
            g = g.sort_values("timestamp")
            names = g["event_name"].tolist()
            ts = g["timestamp"].tolist()
            m = len(ts)
            if m < 2:
                continue
            action_indices = [i for i, n in enumerate(names) if str(n) in action_events]
            for i in range(m - 1):
                gap = (ts[i + 1] - ts[i]).total_seconds()
                if gap < 0:
                    continue
                if len(self.gaps) < self.max_samples:
                    self.gaps.append(gap)
                if str(names[i]) in action_events and str(names[i + 1]) not in action_events:
                    if len(self.action_to_next_gaps) < self.max_samples:
                        self.action_to_next_gaps.append(gap)
            for idx, ai in enumerate(action_indices):
                nxt = action_indices[idx + 1] if idx + 1 < len(action_indices) else (m - 1)
                if nxt <= ai:
                    continue
                span = (ts[nxt] - ts[ai]).total_seconds()
                if span >= 0 and len(self.action_spans) < self.max_samples:
                    self.action_spans.append(span)


class EventSessionCounter:
    def __init__(self) -> None:
        self._by_event: dict[str, set[str]] = defaultdict(set)

    def update_from_df(self, df: pd.DataFrame) -> None:
        if df.empty:
            return
        for en, grp in df.groupby("event_name", sort=False):
            self._by_event[str(en)].update(grp["session_id"].astype(str).unique())

    def counts(self) -> dict[str, int]:
        return {en: len(sids) for en, sids in self._by_event.items()}


class PatternDiscoveryAccumulator:
    """Incremental n-gram / pattern stats (merged across daily chunks)."""

    def __init__(self) -> None:
        self.pat_spans: dict[tuple[str, ...], list[float]] = defaultdict(list)
        self.pat_sessions: dict[tuple[str, ...], set[str]] = defaultdict(set)
        self.pat_edge_gaps: dict[tuple[str, ...], dict[int, list[float]]] = defaultdict(
            lambda: defaultdict(list)
        )
        self.pat_step_props: dict[
            tuple[str, ...], dict[int, dict[str, Counter[str]]]
        ] = defaultdict(lambda: defaultdict(lambda: defaultdict(Counter)))
        self.chains_ingested = 0

    def ingest_chains(
        self,
        chains: list[tuple[str, list[Any], list[str], list[dict[str, Any]]]],
        *,
        action_events: set[str],
        min_len: int,
        max_len: int,
        blacklisted_prop_keys: frozenset[str] | set[str],
        is_single_action_at_start_pattern: Any,
        is_user_device_specific_prop_key: Any,
        max_sample_value_len: int,
    ) -> None:
        bl = blacklisted_prop_keys or frozenset()
        self.chains_ingested += len(chains)
        for sid, tsb, namesb, propsb in chains:
            m = len(namesb)
            upto = min(max_len, m)
            for n in range(min_len, upto + 1):
                pat = tuple(namesb[:n])
                if any(pat[j] == pat[j + 1] for j in range(len(pat) - 1)):
                    continue
                if not is_single_action_at_start_pattern(pat, action_events):
                    continue
                total = (tsb[n - 1] - tsb[0]).total_seconds()
                self.pat_spans[pat].append(total)
                self.pat_sessions[pat].add(sid)
                for e in range(n - 1):
                    gap = (tsb[e + 1] - tsb[e]).total_seconds()
                    self.pat_edge_gaps[pat][e].append(gap)
                for step_idx in range(n):
                    step_props = propsb[step_idx] if step_idx < len(propsb) else {}
                    for k, v in step_props.items():
                        if not isinstance(k, str):
                            continue
                        if k in bl:
                            continue
                        if is_user_device_specific_prop_key(k):
                            continue
                        if v is None or v == "":
                            continue
                        sv = str(v)
                        if len(sv) > max_sample_value_len:
                            continue
                        self.pat_step_props[pat][step_idx][k][sv[:max_sample_value_len]] += 1


def iter_partition_frames(
    partition_uri: str,
    *,
    project_id: str,
    parse_props_fn: Any,
    aws_profile: str | None,
    pulse_type_filter: str | None = None,
) -> Any:
    """Yield per-parquet-file frames for one day partition."""
    yield from iter_partition_event_frames(
        [partition_uri],
        project_id=project_id,
        parse_props_fn=parse_props_fn,
        aws_profile=aws_profile,
        pulse_type_filter=pulse_type_filter,
    )


def release_df(df: pd.DataFrame | None) -> None:
    if df is not None:
        del df
    gc.collect()


def _collect_profile_session_frames(
    partition_uris: list[str],
    *,
    profile_sessions: frozenset[str],
    project_id: str,
    parse_props_fn: Any,
    aws_profile: str | None,
    pulse_type_filter: str | None = None,
) -> pd.DataFrame:
    """Gather all events for reservoir-sampled sessions (full timelines per session)."""
    cols = ["session_id", "timestamp", "event_name", "props"]
    parts: list[pd.DataFrame] = []
    for uri in partition_uris:
        for chunk in iter_partition_frames(
            uri,
            project_id=project_id,
            parse_props_fn=parse_props_fn,
            aws_profile=aws_profile,
            pulse_type_filter=pulse_type_filter,
        ):
            sub = chunk[chunk["session_id"].astype(str).isin(profile_sessions)]
            if sub.empty:
                release_df(chunk)
                continue
            parts.append(sub[cols].copy())
            release_df(chunk)
    if not parts:
        return pd.DataFrame(columns=cols)
    out = pd.concat(parts, ignore_index=True)
    del parts
    return out.sort_values(["session_id", "timestamp"]).reset_index(drop=True)


def run_chunked_s3_workflow(
    partition_uris: list[str],
    *,
    args: Any,
    project_id: str,
    aws_profile: str | None,
    parse_props_fn: Any,
) -> dict[str, Any]:
    """
    Two-pass partition processing:
      Pass 1a — action inference + reservoir sample of profile sessions
      Pass 1b — all events from sampled sessions → prop blacklist + prop keys
      Pass 2 — extract chains into PatternDiscoveryAccumulator, one day at a time
    Returns metadata and state needed for run_pipeline (chunked).
    """
    from suggested_interaction_v2 import (
        CASCADE_MAX_LEN,
        PROP_BLACKLIST_SEED,
        _is_single_action_at_start_pattern,
        _is_user_device_specific_prop_key,
        build_event_key,
        build_global_prop_blacklist_from_sessions,
        derive_runtime_params,
        derive_runtime_params_from_gap_accumulator,
        extract_action_reaction_chains,
        infer_action_events_from_accumulator,
        profile_and_select_prop_keys,
        rows_from_sessions_df,
        MAX_SAMPLE_VALUE_LEN,
    )

    from suggested_interaction_s3 import DEFAULT_PULSE_TYPE_FILTER

    n_parts = len(partition_uris)
    profile_session_cap = max(1, int(getattr(args, "profile_sessions", 10_000)))
    pulse_type_filter = getattr(args, "pulse_type_filter", None)
    if pulse_type_filter is None:
        pulse_type_filter = DEFAULT_PULSE_TYPE_FILTER
    pulse_type_filter = (str(pulse_type_filter).strip() or None) if pulse_type_filter is not None else None
    if pulse_type_filter:
        print(f">>> Pulse type filter: {pulse_type_filter!r} (global)")
    action_acc = ActionInferenceAccumulator()
    gap_acc = RuntimeGapAccumulator()
    event_sess_counter = EventSessionCounter()
    session_sampler = SessionReservoirSampler(
        profile_session_cap,
        seed=PROP_BLACKLIST_SEED,
    )
    prop_blacklist: set[str] = set()
    prop_blacklist_stats: dict[str, Any] = {}

    print(
        f"\n>>> Chunked pass 1a/{3}: scan {n_parts} partition(s) "
        f"(stats + reservoir {profile_session_cap:,} sessions)"
    )
    for idx, uri in enumerate(partition_uris, start=1):
        print(f"  [{idx}/{n_parts}] {uri}")
        part_events = 0
        part_sessions: set[str] = set()
        for chunk in iter_partition_frames(
            uri,
            project_id=project_id,
            parse_props_fn=parse_props_fn,
            aws_profile=aws_profile,
            pulse_type_filter=pulse_type_filter,
        ):
            part_events += len(chunk)
            part_sessions.update(chunk["session_id"].astype(str).unique())
            action_acc.update_from_df(chunk)
            event_sess_counter.update_from_df(chunk)
            session_sampler.update_from_df(chunk)
            release_df(chunk)
        if part_events:
            print(
                f"       events={part_events:,} sessions={len(part_sessions):,}"
            )
        del part_sessions

    total_sessions = len(action_acc.all_sessions)
    profile_sessions = session_sampler.sessions
    print(
        f">>> Pass 1a complete: {total_sessions:,} unique sessions; "
        f"profile sample={len(profile_sessions):,} "
        f"(from {session_sampler.sessions_seen:,} seen)"
    )

    profile_samples: list[tuple[str, str, dict[str, Any]]] = []
    print(
        f"\n>>> Chunked pass 1b/{3}: collect events for {len(profile_sessions):,} "
        f"profile session(s)"
    )
    if profile_sessions:
        profile_df = _collect_profile_session_frames(
            partition_uris,
            profile_sessions=profile_sessions,
            project_id=project_id,
            parse_props_fn=parse_props_fn,
            aws_profile=aws_profile,
            pulse_type_filter=pulse_type_filter,
        )
        print(
            f"       profile_events={len(profile_df):,} "
            f"profile_sessions={profile_df['session_id'].nunique() if not profile_df.empty else 0:,}"
        )
        if not args.no_prop_session_blacklist and not profile_df.empty:
            bl_part, bl_stats = build_global_prop_blacklist_from_sessions(
                profile_df,
                sessions_per_event=max(1, args.prop_blacklist_sessions_per_event),
                max_event_types=max(0, args.prop_blacklist_max_event_types),
                low_card_frac=float(args.prop_blacklist_low_frac),
                min_key_samples=max(1, args.prop_blacklist_min_samples),
                seed=PROP_BLACKLIST_SEED,
            )
            prop_blacklist = set(bl_part)
            prop_blacklist_stats = {
                "chunked": True,
                "session_based": True,
                "profile_sessions_target": profile_session_cap,
                "profile_sessions_collected": int(profile_df["session_id"].nunique()),
                "profile_events_collected": len(profile_df),
                "partitions": n_parts,
                **bl_stats,
            }
        profile_samples = rows_from_sessions_df(profile_df, profile_sessions)
        release_df(profile_df)
    else:
        prop_blacklist_stats = {
            "chunked": True,
            "session_based": True,
            "reason": "no_profile_sessions_sampled",
        }

    prop_blacklist_frozen = frozenset(prop_blacklist)
    if not prop_blacklist_stats:
        prop_blacklist_stats = {
            "chunked": True,
            "session_based": True,
            "n_blacklisted": len(prop_blacklist_frozen),
            "profile_sessions": len(profile_sessions),
            "profile_events": len(profile_samples),
            "partitions": n_parts,
        }

    prop_keys_by_event = profile_and_select_prop_keys(
        sampled_rows=profile_samples,
        max_profile_events=max(1, len(profile_samples)),
        max_cardinality=args.max_prop_cardinality,
        max_keys=max(0, args.max_prop_keys),
        spread=False,
        blacklisted_prop_keys=prop_blacklist_frozen,
    )
    print(
        f">>> Pass 1b complete: {len(prop_blacklist_frozen)} blacklisted keys, "
        f"{len(prop_keys_by_event)} event types with prop keys "
        f"({len(profile_samples):,} profile events)"
    )

    inferred_action_events, inferred_actions_top, inferred_noise_events = (
        infer_action_events_from_accumulator(
            action_acc,
            top_k=max(1, args.action_top_k),
            min_sessions=max(1, args.action_min_sessions),
        )
    )

    if args.auto_thresholds:
        for idx, uri in enumerate(partition_uris, start=1):
            for chunk in iter_partition_frames(
                uri,
                project_id=project_id,
                parse_props_fn=parse_props_fn,
                aws_profile=aws_profile,
                pulse_type_filter=pulse_type_filter,
            ):
                gap_acc.update_from_df(chunk, inferred_action_events)
                release_df(chunk)
        runtime_params = derive_runtime_params_from_gap_accumulator(
            gap_acc,
            inferred_action_events,
            args,
            total_sessions=total_sessions,
        )
    else:
        min_sessions = max(1, int(args.min_sessions))
        if args.min_session_pct > 0:
            min_sessions = max(
                min_sessions,
                int(math.ceil((args.min_session_pct / 100.0) * total_sessions)),
            )
        runtime_params = {
            "min_sessions": min_sessions,
            "min_edge_gap": float(args.min_edge_gap),
            "max_edge_gap": float(args.max_edge_gap),
            "max_chain_span": float(args.max_chain_span),
            "auto_derived": False,
        }

    pattern_acc = PatternDiscoveryAccumulator()
    min_edge_gap = float(runtime_params["min_edge_gap"])
    max_edge_gap = float(runtime_params["max_edge_gap"])
    max_chain_span = float(runtime_params["max_chain_span"])

    print(f"\n>>> Chunked pass 2/{3}: extract chains from {n_parts} partition(s)")
    for idx, uri in enumerate(partition_uris, start=1):
        print(f"  [{idx}/{n_parts}] {uri}")
        part_chains = 0
        for chunk in iter_partition_frames(
            uri,
            project_id=project_id,
            parse_props_fn=parse_props_fn,
            aws_profile=aws_profile,
            pulse_type_filter=pulse_type_filter,
        ):
            chunk["event_key"] = [
                build_event_key(
                    str(en) if en is not None else None,
                    props if isinstance(props, dict) else {},
                    prop_keys_by_event,
                )
                for en, props in zip(chunk["event_name"], chunk["props"])
            ]
            chains = extract_action_reaction_chains(
                chunk,
                inferred_action_events,
                min_edge_gap,
                max_edge_gap,
                max_chain_span,
                CASCADE_MAX_LEN,
            )
            part_chains += len(chains)
            pattern_acc.ingest_chains(
                chains,
                action_events=inferred_action_events,
                min_len=2,
                max_len=CASCADE_MAX_LEN,
                blacklisted_prop_keys=prop_blacklist_frozen,
                is_single_action_at_start_pattern=_is_single_action_at_start_pattern,
                is_user_device_specific_prop_key=_is_user_device_specific_prop_key,
                max_sample_value_len=MAX_SAMPLE_VALUE_LEN,
            )
            del chains
            release_df(chunk)
        print(f"       chains={part_chains:,}")

    meta = {
        "chunked": True,
        "partitions_processed": n_parts,
        "profile_sessions": len(profile_sessions),
        "profile_events": len(profile_samples),
        "total_sessions": total_sessions,
        "chains_ingested": pattern_acc.chains_ingested,
        "prop_keys_by_event": prop_keys_by_event,
        "prop_blacklist": prop_blacklist_frozen,
        "prop_blacklist_stats": prop_blacklist_stats,
        "inferred_actions_top": inferred_actions_top,
        "inferred_noise_events": inferred_noise_events,
        "runtime_params": runtime_params,
        "pattern_accumulator": pattern_acc,
        "event_session_counts": event_sess_counter.counts(),
        "pulse_type_filter": pulse_type_filter,
    }
    return meta
