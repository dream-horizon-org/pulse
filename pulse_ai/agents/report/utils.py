"""Normalization utilities for LLM-generated chart and table data."""

from typing import Any

# ── Chart constants ──────────────────────────────────────────

VALID_CHART_TYPES = {"line", "bar", "pie", "area"}

ALLOWED_ECHART_KEYS = {
    "title", "tooltip", "legend", "grid", "xAxis", "yAxis",
    "series", "color", "textStyle", "animationDuration",
}

# ── Table constants ──────────────────────────────────────────

ALLOWED_COLUMN_KEYS = {"key", "label", "type"}
VALID_COLUMN_TYPES = {"string", "number"}


# ── Chart helpers ────────────────────────────────────────────

def coerce_number(val: Any) -> Any:
    """Try to convert a value to a number, return as-is if impossible."""
    if isinstance(val, (int, float)):
        return val
    if isinstance(val, str):
        try:
            return float(val) if "." in val else int(val)
        except (ValueError, TypeError):
            return val
    return val


def normalize_chart_data(chart_type: str, data: dict) -> dict:
    """
    Sanitize and normalize LLM-generated ECharts options so the frontend
    can render them reliably regardless of minor LLM formatting mistakes.
    """
    if not isinstance(data, dict):
        return {}

    normalized = {k: v for k, v in data.items() if k in ALLOWED_ECHART_KEYS}

    series_list = normalized.get("series")
    if not isinstance(series_list, list):
        series_list = [series_list] if series_list else []

    if chart_type == "pie":
        for s in series_list:
            if not isinstance(s, dict):
                continue
            s["type"] = "pie"
            pie_data = s.get("data")
            if isinstance(pie_data, list):
                coerced = []
                for item in pie_data:
                    if isinstance(item, dict) and "name" in item and "value" in item:
                        coerced.append({
                            "name": str(item["name"]),
                            "value": coerce_number(item["value"]),
                        })
                    elif isinstance(item, (list, tuple)) and len(item) >= 2:
                        coerced.append({
                            "name": str(item[0]),
                            "value": coerce_number(item[1]),
                        })
                s["data"] = coerced
        normalized.pop("xAxis", None)
        normalized.pop("yAxis", None)
    else:
        series_type = "line" if chart_type == "area" else chart_type
        for s in series_list:
            if not isinstance(s, dict):
                continue
            s.setdefault("type", series_type)
            if chart_type == "area":
                s.setdefault("areaStyle", {})
            s_data = s.get("data")
            if isinstance(s_data, list):
                s["data"] = [coerce_number(v) for v in s_data]

        x = normalized.get("xAxis", {})
        if not isinstance(x, dict):
            x = {}
        x.setdefault("type", "category")
        normalized["xAxis"] = x

        y = normalized.get("yAxis", {})
        if not isinstance(y, dict):
            y = {}
        y.setdefault("type", "value")
        normalized["yAxis"] = y

    normalized["series"] = series_list
    return normalized


# ── Table helpers ────────────────────────────────────────────

def coerce_value(val: Any, col_type: str) -> Any:
    """Coerce a cell value to match the declared column type."""
    if col_type == "number":
        if isinstance(val, (int, float)):
            return val
        if isinstance(val, str):
            try:
                return float(val) if "." in val else int(val)
            except (ValueError, TypeError):
                return val
    return str(val) if val is not None else ""


def normalize_table_data(
    columns: list, rows: list,
) -> tuple[list[dict], list[dict]]:
    """
    Sanitize LLM-generated table data so the frontend can render it
    reliably regardless of minor LLM formatting mistakes.
    """
    if not isinstance(columns, list):
        columns = []
    if not isinstance(rows, list):
        rows = []

    norm_columns = []
    for col in columns:
        if not isinstance(col, dict) or "key" not in col:
            continue
        norm_col = {k: v for k, v in col.items() if k in ALLOWED_COLUMN_KEYS}
        norm_col.setdefault("label", norm_col["key"])
        if norm_col.get("type") not in VALID_COLUMN_TYPES:
            norm_col["type"] = "string"
        norm_columns.append(norm_col)

    if not norm_columns:
        return [], []

    col_keys = {c["key"] for c in norm_columns}
    col_type_map = {c["key"]: c.get("type", "string") for c in norm_columns}

    norm_rows = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        norm_row = {}
        for key in col_keys:
            raw = row.get(key)
            norm_row[key] = coerce_value(raw, col_type_map[key])
        norm_rows.append(norm_row)

    return norm_columns, norm_rows
