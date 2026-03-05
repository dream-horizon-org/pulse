import json

from google.adk.tools import ToolContext

from ..utils import VALID_CHART_TYPES, normalize_chart_data


async def create_chart(
    chart_type: str,
    title: str,
    data: str,
    description: str = None,
    tool_context: ToolContext = None,
) -> dict:
    """Create a visual chart for the user.

    Args:
        chart_type: One of "line", "bar", "pie", "area"
        title: Chart title
        data: JSON string of an ECharts-compatible option object with xAxis, yAxis, series etc.
              Example for line: '{"xAxis": {"type": "category", "data": ["Mon","Tue"]}, "yAxis": {"type": "value"}, "series": [{"name": "Errors", "data": [10,20]}]}'
              Example for pie: '{"series": [{"type": "pie", "data": [{"name": "A", "value": 10}]}]}'
        description: Optional text description of what the chart shows
    """
    if chart_type not in VALID_CHART_TYPES:
        chart_type = "line"

    try:
        parsed_data = json.loads(data) if isinstance(data, str) else data
    except (json.JSONDecodeError, TypeError):
        parsed_data = {}

    parsed_data = normalize_chart_data(chart_type, parsed_data)

    chart_config = {
        "type": chart_type,
        "title": title,
        "data": parsed_data,
        "description": description,
    }

    return {"success": True, "chart": chart_config}
