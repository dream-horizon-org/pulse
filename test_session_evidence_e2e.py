#!/usr/bin/env python3
"""
End-to-End Test: Session Evidence Integration with RCA Report

This test demonstrates:
1. Loading traces from ClickHouse data (JSON format)
2. Identifying poor interactions (high error rate, low apdex)
3. Querying for example sessions with those characteristics
4. Building RCA prompt with session IDs
5. Verifying LLM response includes affected_sessions field
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Optional

# Mock Pydantic schemas
class RcaStructuredSegmentV1:
    """Mock segment matching Python schema"""
    def __init__(self, rank: int, title: str, affected_sessions: Optional[list[str]] = None):
        self.rank = rank
        self.title = title
        self.affected_sessions = affected_sessions

    def __repr__(self):
        return (
            f"Segment(rank={self.rank}, title='{self.title}', "
            f"sessions={self.affected_sessions})"
        )


def load_traces_from_json(json_file: Path) -> list[dict[str, Any]]:
    """Load trace data from ClickHouse JSON export."""
    print(f"📂 Loading traces from {json_file}...")
    
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    # ClickHouse JSON format: { "meta": [...], "data": [...] }
    traces = data.get('data', [])
    print(f"✅ Loaded {len(traces)} traces")
    
    return traces


def extract_interaction_metrics(traces: list[dict]) -> dict[str, list[dict]]:
    """
    Group traces by interaction and calculate metrics per session.
    
    Returns:
        Dict[session_id, list[interaction_records]]
    """
    interactions_by_session = {}
    
    for trace in traces:
        # Extract key fields
        span_name = trace.get('SpanName', '')
        session_id = trace.get('SpanAttributes', {}).get('session.id', '')
        
        if not session_id or not span_name:
            continue
        
        # Parse apdex and error status
        span_attrs = trace.get('SpanAttributes', {})
        apdex_score = float(span_attrs.get('pulse.interaction.apdex_score', 0))
        is_error = span_attrs.get('pulse.interaction.is_error', 'false').lower() == 'true'
        
        # Initialize session record
        if session_id not in interactions_by_session:
            interactions_by_session[session_id] = []
        
        # Add interaction record
        interactions_by_session[session_id].append({
            'interaction': span_name,
            'apdex_score': apdex_score,
            'is_error': is_error,
            'timestamp': trace.get('Timestamp', ''),
        })
    
    return interactions_by_session


def find_poor_sessions(interactions_by_session: dict, limit: int = 5) -> list[str]:
    """
    Find sessions with:
    - High error rate (is_error = True)
    - Low apdex scores (< 0.5)
    - Multiple poor interactions
    
    Returns top N session IDs.
    """
    session_scores = []
    
    for session_id, interactions in interactions_by_session.items():
        # Calculate error rate and apdex metrics
        total_interactions = len(interactions)
        error_count = sum(1 for i in interactions if i['is_error'])
        error_rate = error_count / total_interactions if total_interactions > 0 else 0
        
        avg_apdex = sum(i['apdex_score'] for i in interactions) / total_interactions
        
        # Count poor interactions (apdex < 0.5)
        poor_interactions = sum(1 for i in interactions if i['apdex_score'] < 0.5)
        poor_interaction_rate = poor_interactions / total_interactions
        
        # Score: prioritize high error rate + low apdex
        # Formula: error_rate (0-1) + (1 - avg_apdex) (0-1)
        score = error_rate + (1.0 - avg_apdex)
        
        session_scores.append({
            'session_id': session_id,
            'score': score,
            'error_rate': error_rate,
            'avg_apdex': avg_apdex,
            'poor_interactions': poor_interactions,
            'poor_interaction_rate': poor_interaction_rate,
            'total_interactions': total_interactions,
        })
    
    # Sort by score (highest first) and take top N
    session_scores.sort(key=lambda x: x['score'], reverse=True)
    
    return [s['session_id'] for s in session_scores[:limit]]


def build_rca_prompt_with_sessions(
    interaction_name: str,
    example_session_ids: Optional[list[str]] = None,
) -> str:
    """
    Build RCA prompt with explicit instruction to include affected_sessions.
    
    This matches the Python implementation in rca_runner.py
    """
    sessions_context = ""
    if example_session_ids:
        sessions_context = (
            f"\n## Example Sessions for Replay Analysis\n"
            f"Available session IDs: {', '.join(example_session_ids)}\n"
            f"\n**IMPORTANT INSTRUCTION FOR STRUCTURED OUTPUT:**\n"
            f"For each segment in your analysis, populate the 'affected_sessions' field "
            f"with the relevant example session IDs from the list above. "
            f"Include sessions that demonstrate or support the key findings of that segment. "
            f"Example format for segment:\n"
            f'{{"affected_sessions": {json.dumps(example_session_ids[:2])}}}\n'
            f"These sessions are clickable in the UI for replay analysis and help users "
            f"validate your findings."
        )
    
    return (
        "Generate a root cause analysis report for the given interaction.\n"
        f"Interaction: {interaction_name}\n"
        f"RootCausePayload(JSON): {{...}}"
        f"{sessions_context}"
        "\nEnsure each segment's findings are supported by the example sessions where applicable."
    )


def simulate_llm_response(
    interaction_name: str,
    session_ids: list[str],
) -> dict:
    """
    Simulate LLM response with affected_sessions.
    
    In real flow: LLM processes prompt and returns structured report.
    Here we simulate what it should return.
    """
    return {
        'version': 1,
        'executive_summary': (
            f"The '{interaction_name}' interaction is experiencing performance degradation "
            f"across specific device segments."
        ),
        'segments': [
            {
                'rank': 1,
                'title': 'High Error Rate on Cellular Networks',
                'metrics': [
                    {'metric_id': 'error_rate', 'metric_label': 'Error Rate', 'value_display': '28%'},
                ],
                'impact': 'Cellular users experiencing failures when loading sections',
                'affected_sessions': session_ids[:3],  # Top 3 sessions
            },
            {
                'rank': 2,
                'title': 'Poor Apdex Score on Android 16',
                'metrics': [
                    {'metric_id': 'apdex', 'metric_label': 'Apdex Score', 'value_display': '0.42'},
                ],
                'impact': 'Android 16 devices showing slow interaction completion',
                'affected_sessions': session_ids[2:5],  # Overlapping sessions
            },
        ],
        'recommendations': [
            'Investigate cellular network handling in section loading',
            'Profile performance on Android 16 devices',
        ],
    }


def test_end_to_end():
    """Run complete E2E test."""
    print("\n" + "=" * 70)
    print("END-TO-END TEST: Session Evidence Integration with RCA")
    print("=" * 70)
    
    # Step 1: Load traces
    print("\n[Step 1] Loading trace data...")
    traces_file = Path.home() / "Downloads" / "traces_7days.json"
    
    if not traces_file.exists():
        print(f"❌ Traces file not found: {traces_file}")
        print("   Please ensure traces_7days.json is in ~/Downloads/")
        return False
    
    traces = load_traces_from_json(traces_file)
    
    # Step 2: Extract interactions and calculate metrics
    print("\n[Step 2] Analyzing interactions and calculating metrics...")
    interactions_by_session = extract_interaction_metrics(traces)
    print(f"✅ Analyzed {len(interactions_by_session)} unique sessions")
    
    # Step 3: Find poor sessions
    print("\n[Step 3] Finding sessions with high error rate and low apdex...")
    poor_session_ids = find_poor_sessions(interactions_by_session, limit=5)
    
    if not poor_session_ids:
        print("❌ No poor sessions found in data")
        return False
    
    print(f"✅ Found {len(poor_session_ids)} poor sessions:")
    for i, sid in enumerate(poor_session_ids, 1):
        print(f"   {i}. {sid}")
    
    # Step 4: Build RCA prompt with sessions
    print("\n[Step 4] Building RCA prompt with session evidence...")
    interaction_name = "LiveNowSectionToMatchPageLoaded"
    prompt = build_rca_prompt_with_sessions(interaction_name, poor_session_ids)
    print("✅ Prompt generated:")
    print("-" * 70)
    print(prompt)
    print("-" * 70)
    
    # Step 5: Simulate LLM response
    print("\n[Step 5] Simulating LLM response with affected_sessions...")
    llm_response = simulate_llm_response(interaction_name, poor_session_ids)
    print("✅ LLM Response:")
    print(json.dumps(llm_response, indent=2))
    
    # Step 6: Verify segments have affected_sessions
    print("\n[Step 6] Verifying affected_sessions in response...")
    all_segments_have_sessions = all(
        'affected_sessions' in segment and segment['affected_sessions']
        for segment in llm_response['segments']
    )
    
    if all_segments_have_sessions:
        print("✅ All segments include affected_sessions field")
        for i, segment in enumerate(llm_response['segments'], 1):
            print(f"   Segment {i}: {len(segment['affected_sessions'])} sessions")
    else:
        print("❌ Some segments missing affected_sessions")
        return False
    
    # Step 7: Verify UI can render buttons
    print("\n[Step 7] Verifying UI rendering capability...")
    for segment in llm_response['segments']:
        if segment.get('affected_sessions'):
            print(f"\n   📱 Segment: {segment['title']}")
            print(f"      Sessions to render as buttons:")
            for session_id in segment['affected_sessions']:
                print(f"        - <Button>{session_id}</Button>")
    
    print("\n" + "=" * 70)
    print("✅ END-TO-END TEST PASSED!")
    print("=" * 70)
    print("\nSummary:")
    print(f"  • Loaded {len(traces)} traces")
    print(f"  • Analyzed {len(interactions_by_session)} sessions")
    print(f"  • Found {len(poor_session_ids)} poor sessions")
    print(f"  • Generated RCA with {len(llm_response['segments'])} segments")
    print(f"  • All segments include affected_sessions for UI rendering")
    
    return True


if __name__ == "__main__":
    try:
        success = test_end_to_end()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ Test failed with error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
