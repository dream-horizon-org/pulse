import json

from google.adk.tools import ToolContext

from ..utils import normalize_table_data


async def create_table(
    title: str,
    columns: str,
    rows: str,
    description: str = None,
    tool_context: ToolContext = None,
) -> dict:
    """Create a data table for the user.

    Args:
        title: Table title
        columns: JSON array of column definitions. Each column has "key" (field name),
                 "label" (display header), and optional "type" ("string" or "number").
                 Example: '[{"key": "screen", "label": "Screen Name", "type": "string"},
                            {"key": "load_time", "label": "Load Time (ms)", "type": "number"}]'
        rows: JSON array of row objects matching the column keys.
              Example: '[{"screen": "Home", "load_time": 450}, {"screen": "Feed", "load_time": 820}]'
        description: Optional text description of what the table shows
    """
    try:
        parsed_columns = json.loads(columns) if isinstance(columns, str) else columns
    except (json.JSONDecodeError, TypeError):
        parsed_columns = []

    try:
        parsed_rows = json.loads(rows) if isinstance(rows, str) else rows
    except (json.JSONDecodeError, TypeError):
        parsed_rows = []

    parsed_columns, parsed_rows = normalize_table_data(parsed_columns, parsed_rows)

    table_config = {
        "title": title,
        "columns": parsed_columns,
        "rows": parsed_rows,
        "description": description,
    }

    return {"success": True, "table": table_config}
