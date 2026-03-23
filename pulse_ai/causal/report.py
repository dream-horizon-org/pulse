"""
Reporting — formatted terminal output for causal analysis results.

Produces human-readable reports including:
  - Revenue impact table (ranked by priority)
  - Detailed significant findings with interpretation
  - Screen graph visualization (top transitions, paths, drop-offs)
  - Frustration vs conversion comparison
"""
from tabulate import tabulate

from .models import IssueAnalysis, ConversionProxy, DropoffEdge


# ─── Revenue Impact Report ────────────────────────────────────────

def print_report(results: list[IssueAnalysis], proxy: ConversionProxy):
    """
    Print the main revenue impact report.

    Includes:
      - Summary table of all analyzed issues
      - Detailed breakdown of statistically significant findings
      - Interpretation guidance for each finding
    """
    if not results:
        print("\n  No issues had enough data for journey-conditioned causal analysis.")
        print("  Possible reasons:")
        print("    - Too few sessions reaching the affected screens")
        print("    - Issues too rare (below minimum affected threshold)")
        print("    - No control group (all sessions at that screen are affected)")
        print("    - Insufficient overlap in propensity scores (common support)")
        return

    print(f"\n{'='*95}")
    print(f"  REVENUE IMPACT REPORT (Journey-Conditioned Causal Analysis)")
    print(f"  Conversion proxy: {proxy.identifier} ({proxy.proxy_type})")
    print(f"{'='*95}\n")

    # ── Summary table ──
    table_data = []
    for r in results:
        sig_marker = "✓ YES" if r.is_significant else "✗ no"
        table_data.append([
            r.issue_label,
            r.funnel_stage,
            f"{r.sessions_reaching_screen}",
            r.affected_count,
            r.control_count,
            f"{r.affected_conversion_rate:.1%}",
            f"{r.control_conversion_rate:.1%}",
            f"{r.conversion_delta:+.1%}",
            f"[{r.ci_lower:+.1%}, {r.ci_upper:+.1%}]",
            sig_marker,
            f"{r.propensity_balance:.3f}",
        ])

    headers = [
        "Issue", "Funnel", "Reached", "Affected", "Control",
        "Aff Conv%", "Ctrl Conv%", "Delta", "95% CI", "Sig?", "PS Bal",
    ]
    print(tabulate(table_data, headers=headers, tablefmt="grid"))

    # ── Detailed significant findings ──
    significant = [r for r in results if r.is_significant]
    if significant:
        print(f"\n{'─'*95}")
        print("  SIGNIFICANT FINDINGS (journey-conditioned causal impact)")
        print(f"{'─'*95}")
        for r in significant:
            _print_finding_detail(r)

    # ── Match quality summary ──
    print(f"\n{'─'*95}")
    print("  MATCH QUALITY")
    print(f"{'─'*95}")
    for r in results:
        caliper_str = "✓" if r.caliper_applied else "✗ relaxed"
        print(f"    {r.issue_label:45s}  "
              f"PS balance: {r.propensity_balance:.4f}  "
              f"Common support: {r.common_support_pct:.0%}  "
              f"Caliper: {caliper_str}")


def _print_finding_detail(r: IssueAnalysis):
    """Print detailed interpretation of a single significant finding."""
    direction = "REDUCES" if r.conversion_delta > 0 else "has REVERSE effect on"

    print(f"""
  {r.issue_label} [{r.funnel_stage}-funnel]
  {'─' * (len(r.issue_label) + len(r.funnel_stage) + 10)}
  Among {r.sessions_reaching_screen} sessions that reached {r.screen_name}:
    {r.affected_count} experienced {r.issue_type}
    {r.control_count} did not (propensity-matched controls)

  This issue {direction} conversion AFTER reaching this screen by
  {abs(r.conversion_delta):.1%} (95% CI: [{r.ci_lower:+.1%}, {r.ci_upper:+.1%}])
  p-value: {r.bootstrap_p_value:.4f}

  Affected conversion (after issue): {r.affected_conversion_rate:.1%}
  Control conversion (after screen): {r.control_conversion_rate:.1%}

  Match quality:
    PS balance: {r.propensity_balance:.4f} (lower = better)
    Common support: {r.common_support_pct:.0%}
    Caliper enforced: {'yes' if r.caliper_applied else 'no (relaxed)'}

  Estimated lost conversions: ~{abs(r.conversion_delta) * r.affected_count:.0f}
  per {r.affected_count} affected sessions.""")

    if r.affected_user_count > 0:
        print(f"  Affected users: {r.affected_user_count}")
    if r.exception_detail:
        print(f"  Top exception: {r.exception_detail}")


# ─── Screen Graph Report ─────────────────────────────────────────

def print_screen_graph_report(
    graph: dict,
    conversion_paths: list[list[str]],
    dropoffs: list[DropoffEdge],
    total_sessions: int,
):
    """
    Print process mining results: top transitions, conversion paths, drop-offs.
    """
    print(f"\n  {'─'*70}")
    print(f"  SCREEN GRAPH: Top Transitions")
    print(f"  {'─'*70}")

    # Top edges by weight
    all_edges = []
    for from_s, targets in graph.items():
        for to_s, weight in targets.items():
            all_edges.append((from_s, to_s, weight))
    all_edges.sort(key=lambda x: -x[2])

    for from_s, to_s, weight in all_edges[:15]:
        pct = weight / total_sessions * 100 if total_sessions > 0 else 0
        bar = "█" * int(pct)
        print(f"    {from_s:35s} → {to_s:35s}  {weight:>4} ({pct:.0f}%) {bar}")

    if conversion_paths:
        print(f"\n  {'─'*70}")
        print(f"  CONVERSION PATHS (entry → conversion)")
        print(f"  {'─'*70}")
        for i, path in enumerate(conversion_paths[:5]):
            print(f"    Path {i+1}: {' → '.join(path)}")

    if dropoffs:
        print(f"\n  {'─'*70}")
        print(f"  DROP-OFF POINTS (leaving conversion path)")
        print(f"  {'─'*70}")
        for d in dropoffs[:10]:
            print(f"    {d.from_screen:35s}  on-path: {d.on_path_count:>4}  "
                  f"off-path: {d.off_path_count:>4}  drop: {d.dropoff_rate:.0%}")
            for to_s, c in d.top_destinations:
                print(f"      └→ {to_s} ({c})")


# ─── Frustration Report ──────────────────────────────────────────

def print_frustration_report(
    converting_mean: float,
    non_converting_mean: float,
    calibrated: bool = False,
):
    """Print frustration vs conversion comparison."""
    print(f"\n  {'─'*70}")
    print(f"  FRUSTRATION vs CONVERSION{'  (calibrated weights)' if calibrated else ''}")
    print(f"  {'─'*70}")
    print(f"    Converting sessions:     {converting_mean:.1f}")
    print(f"    Non-converting sessions: {non_converting_mean:.1f}")

    diff = non_converting_mean - converting_mean
    if diff > 0:
        print(f"    → Non-converting are {diff:.1f} points more frustrated")
    elif diff < 0:
        print(f"    → Selection bias: engaged users hit more issues (diff={diff:.1f})")
    else:
        print(f"    → No difference detected")
