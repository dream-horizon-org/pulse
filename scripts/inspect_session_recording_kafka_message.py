#!/usr/bin/env python3
"""
Inspect one line from kafka-console-consumer output (session_recording_events).

Usage:
  kafka-console-consumer ... --max-messages 1 > msg.jsonl
  python3 scripts/inspect_session_recording_kafka_message.py msg.jsonl

Or pipe:
  docker exec pulse-kafka kafka-console-consumer ... | python3 scripts/inspect_session_recording_kafka_message.py -
"""
from __future__ import annotations

import json
import sys


def main() -> None:
    if len(sys.argv) > 1 and sys.argv[1] in ("-h", "--help"):
        print(__doc__ or "", end="")
        return
    path = sys.argv[1] if len(sys.argv) > 1 else "-"
    if path == "-":
        line = sys.stdin.readline()
    else:
        with open(path, encoding="utf-8") as f:
            line = f.readline()
    line = line.strip()
    if not line:
        print("No input", file=sys.stderr)
        sys.exit(1)

    outer = json.loads(line)
    print("=== Outer ===")
    print("keys:", sorted(outer.keys()))
    print("event:", outer.get("event"))
    print("user_id:", outer.get("user_id"))
    print("uuid:", outer.get("uuid"))

    data_str = outer["data"]
    if not isinstance(data_str, str):
        print("ERROR: outer['data'] must be JSON string", file=sys.stderr)
        sys.exit(2)

    inner = json.loads(data_str)
    props = inner.get("properties") or {}
    print("\n=== Inner (parsed from data) ===")
    print("session_id:", props.get("session_id"))
    print("snapshot_source:", props.get("snapshot_source"))
    items = props.get("snapshot_items") or []
    print("snapshot_items count:", len(items))

    types_seen: dict[int, int] = {}
    for it in items:
        t = it.get("type")
        if isinstance(t, int):
            types_seen[t] = types_seen.get(t, 0) + 1
    print("type histogram:", dict(sorted(types_seen.items())))
    # ReplayEventType: 0 DOM, 1 LOAD, 2 FULL, 3 INCREMENTAL, 4 META, ...

    meta = [it for it in items if it.get("type") == 4]
    print("\nMETA (type==4) count:", len(meta))
    for i, m in enumerate(meta[:5]):
        d = m.get("data") or {}
        print(
            f"  META[{i}] ts={m.get('timestamp')} href={d.get('href')!r} "
            f"width={d.get('width')} height={d.get('height')} aspectRatio={d.get('aspectRatio')!r}"
        )


if __name__ == "__main__":
    main()
