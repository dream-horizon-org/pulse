"""
Process Mining — screen-graph analysis for conversion path discovery.

Builds a directed weighted graph of screen transitions from session-level
screen visit data. Unlike depth-based funnels, this correctly handles:
  - Back-navigation (user goes A → B → A → C)
  - Multiple entry points
  - Non-linear user journeys

Key outputs:
  - Screen transition graph with edge weights
  - Conversion paths (BFS from entry screens to conversion-adjacent screens)
  - Drop-off edges (where users leave the conversion path)
"""
from collections import defaultdict, deque
from dataclasses import field

import pandas as pd

from .config import CausalConfig
from .models import DropoffEdge


# ─── Graph Construction ───────────────────────────────────────────

def build_screen_graph(screen_visits_df: pd.DataFrame) -> dict[str, dict[str, int]]:
    """
    Build a directed weighted graph of screen transitions.

    For each session, creates edges between consecutive screen visits
    (ordered by first_visit_ts). Edge weight = number of sessions
    that made that transition.

    Args:
        screen_visits_df: Must have columns: session_id, screen_name, first_visit_ts.

    Returns:
        Nested dict: graph[from_screen][to_screen] = transition_count
    """
    graph = defaultdict(lambda: defaultdict(int))
    sorted_visits = screen_visits_df.sort_values(["session_id", "first_visit_ts"])

    for _, group in sorted_visits.groupby("session_id"):
        screens = group["screen_name"].tolist()
        for i in range(len(screens) - 1):
            graph[screens[i]][screens[i + 1]] += 1

    # Convert defaultdict to regular dict for serialization
    return {k: dict(v) for k, v in graph.items()}


# ─── Entry & Conversion Screen Discovery ─────────────────────────

def find_entry_screens(screen_visits_df: pd.DataFrame, top_n: int = 3) -> list[str]:
    """
    Identify the most common first screens across all sessions.

    Args:
        screen_visits_df: Per-session screen visits with timestamps.
        top_n: Number of entry screens to return.

    Returns:
        List of screen names, ordered by frequency.
    """
    first_screens = (
        screen_visits_df
        .sort_values("first_visit_ts")
        .groupby("session_id")
        .first()
    )
    return first_screens["screen_name"].value_counts().head(top_n).index.tolist()


def find_conversion_adjacent_screens(
    screen_visits_df: pd.DataFrame,
    conversion_session_ids: set,
    min_rate: float = 0.3,
    max_screens: int = 5,
) -> set[str]:
    """
    Find screens disproportionately visited by converting sessions.

    A screen is "conversion-adjacent" if the fraction of its visitors
    who converted exceeds min_rate.

    Args:
        screen_visits_df: Per-session screen visits.
        conversion_session_ids: Set of sessions that converted.
        min_rate: Minimum conversion rate among screen visitors.
        max_screens: Maximum number of screens to return.

    Returns:
        Set of conversion-adjacent screen names.
    """
    total_by_screen = screen_visits_df["screen_name"].value_counts()
    conv_visits = screen_visits_df[
        screen_visits_df["session_id"].isin(conversion_session_ids)
    ]
    conv_by_screen = conv_visits["screen_name"].value_counts()

    conv_rate = (conv_by_screen / total_by_screen).dropna()
    return set(conv_rate[conv_rate > min_rate].nlargest(max_screens).index.tolist())


# ─── Conversion Path Discovery ────────────────────────────────────

def find_conversion_paths(
    graph: dict[str, dict[str, int]],
    entry_screens: list[str],
    conversion_screens: set[str],
    max_depth: int = 10,
    max_paths: int = 20,
) -> list[list[str]]:
    """
    BFS to find the most common paths from entry to conversion screens.

    Explores the graph breadth-first, following high-weight edges first.
    Paths are deduplicated and limited to max_paths.

    Args:
        graph: Screen transition graph (from build_screen_graph).
        entry_screens: Starting screens for path search.
        conversion_screens: Target screens (conversion-adjacent).
        max_depth: Maximum path length (BFS depth limit).
        max_paths: Maximum number of paths to return.

    Returns:
        List of paths, where each path is a list of screen names.
    """
    paths = []
    queue = deque()

    for entry in entry_screens:
        queue.append(([entry], 0))

    visited_paths = set()  # Avoid duplicate paths

    while queue and len(paths) < max_paths:
        path, depth = queue.popleft()
        if depth >= max_depth:
            continue

        current = path[-1]
        if current in conversion_screens and depth > 0:
            path_key = tuple(path)
            if path_key not in visited_paths:
                visited_paths.add(path_key)
                paths.append(path)
            continue

        # Follow edges sorted by weight (most common first)
        neighbors = sorted(
            graph.get(current, {}).items(),
            key=lambda x: -x[1],
        )[:5]  # Limit branching factor

        for next_screen, weight in neighbors:
            if next_screen not in path:  # No cycles
                queue.append((path + [next_screen], depth + 1))

    return paths


# ─── Drop-off Analysis ────────────────────────────────────────────

def find_dropoff_edges(
    graph: dict[str, dict[str, int]],
    conversion_paths: list[list[str]],
    min_edge_weight: int = 10,
) -> list[DropoffEdge]:
    """
    Find edges where users leave the conversion path.

    For each screen on a conversion path, compute how many transitions
    stay on-path vs. go off-path. High off-path rates indicate
    drop-off points worth investigating.

    Args:
        graph: Screen transition graph.
        conversion_paths: Discovered conversion paths.
        min_edge_weight: Minimum total transitions from a screen to include it.

    Returns:
        List of DropoffEdge, sorted by off_path_count descending.
    """
    # Collect all on-path edges
    on_path_edges = set()
    for path in conversion_paths:
        for i in range(len(path) - 1):
            on_path_edges.add((path[i], path[i + 1]))

    dropoffs = []
    for from_screen, targets in graph.items():
        on_path_count = sum(
            count for to_s, count in targets.items()
            if (from_screen, to_s) in on_path_edges
        )
        off_path_count = sum(
            count for to_s, count in targets.items()
            if (from_screen, to_s) not in on_path_edges
        )
        total = on_path_count + off_path_count

        if total >= min_edge_weight and off_path_count > 0:
            # Top off-path destinations
            top_destinations = sorted(
                [
                    (to_s, count)
                    for to_s, count in targets.items()
                    if (from_screen, to_s) not in on_path_edges
                ],
                key=lambda x: -x[1],
            )[:3]

            dropoffs.append(DropoffEdge(
                from_screen=from_screen,
                on_path_count=on_path_count,
                off_path_count=off_path_count,
                total=total,
                dropoff_rate=off_path_count / total,
                top_destinations=top_destinations,
            ))

    dropoffs.sort(key=lambda d: d.off_path_count, reverse=True)
    return dropoffs
