# Artifact Service Approach (Previous)

This documents the previous approach where tools wrote chart/table configs to ADK's Artifact Service, and the server read them back to send via SSE. This was replaced with the current approach where chart/table configs are extracted directly from `function_response` events.

## Why it was replaced

The artifact write was redundant — ADK already stores tool return values as `function_response` events in the session, and our server extracts chart/table data from those events. The artifact service was a write-only dead end (nothing ever called `load_artifact`). See [Current Approach](#current-approach) at the bottom for how it works today.

## How to restore if needed

### 1. Add artifact-saving utility to `pulse_ai/agents/report/utils.py`

```python
import json
from google.adk.tools import ToolContext
from google.genai import types


def _sanitize_filename(title: str) -> str:
    return "".join(c if c.isalnum() or c in (" ", "_", "-") else "_" for c in title)


async def save_tool_artifact(
    tool_context: ToolContext | None,
    prefix: str,
    title: str,
    config: dict,
) -> None:
    """Persist a tool's output as a JSON artifact in the ADK artifact store."""
    if not tool_context:
        return
    artifact = types.Part(
        inline_data=types.Blob(
            mime_type="application/json",
            data=json.dumps(config).encode("utf-8"),
        )
    )
    safe_title = _sanitize_filename(title)
    await tool_context.save_artifact(
        filename=f"{prefix}_{safe_title[:50]}.json",
        artifact=artifact,
    )
```

### 2. Call it from each tool

In `pulse_ai/agents/report/tools/create_chart.py`:

```python
from ..utils import save_tool_artifact

async def create_chart(
    chart_type: str, title: str, data: str,
    description: str = None, tool_context: ToolContext = None,
) -> dict:
    # ... existing parsing and normalization ...

    chart_config = {
        "type": chart_type,
        "title": title,
        "data": parsed_data,
        "description": description,
    }

    # Write to artifact service
    await save_tool_artifact(tool_context, "chart", title, chart_config)

    return {"success": True, "chart": chart_config}
```

Same pattern in `create_table.py`:

```python
from ..utils import save_tool_artifact

async def create_table(
    title: str, columns: str, rows: str,
    description: str = None, tool_context: ToolContext = None,
) -> dict:
    # ... existing parsing and normalization ...

    table_config = { ... }

    # Write to artifact service
    await save_tool_artifact(tool_context, "table", title, table_config)

    return {"success": True, "table": table_config}
```

### 3. Read artifacts back (if switching to artifact-based SSE delivery)

If you want the SSE stream to read from the artifact service instead of from `function_response` events, you would modify `event_stream()` in `pulse_ai/server/routes.py`:

```python
from pulse_ai.server.app import artifact_service

async def event_stream():
    content_blocks = []
    tracker = DeltaTracker()

    try:
        async for event in runner.run_async(
            user_id=request.user_id,
            session_id=request.session_id,
            new_message=new_message,
        ):
            if not event.content or not event.content.parts:
                continue

            texts, blocks = extract_content_blocks(
                event.content.parts, event.author,
            )

            # Stream text as before
            for text in texts:
                delta = tracker.push(text)
                if delta:
                    yield f"data: {json.dumps({'type': 'text', 'content': delta})}\n\n"

    except Exception as e:
        logger.exception("Error during agent execution")
        yield f"data: {json.dumps({'type': 'error', 'message': str(e)})}\n\n"

    # --- Artifact-based approach ---
    # After agent finishes, read all artifacts from the session
    artifact_names = await artifact_service.list_artifact_keys(
        app_name=APP_NAME,
        user_id=request.user_id,
        session_id=request.session_id,
    )
    for name in artifact_names:
        artifact = await artifact_service.load_artifact(
            app_name=APP_NAME,
            user_id=request.user_id,
            session_id=request.session_id,
            filename=name,
        )
        if artifact and artifact.inline_data:
            config = json.loads(artifact.inline_data.data.decode("utf-8"))
            if name.startswith("chart_"):
                content_blocks.append({"block_type": "chart", **config})
            elif name.startswith("table_"):
                content_blocks.append({"block_type": "table", **config})

    if content_blocks:
        yield f"data: {json.dumps({'type': 'content_blocks', 'blocks': content_blocks})}\n\n"
    yield "data: [DONE]\n\n"
```

### 4. For persistent artifact storage

Replace `InMemoryArtifactService` in `pulse_ai/server/app.py` with a database-backed implementation:

```python
# In-memory (current, lost on restart)
from google.adk.artifacts import InMemoryArtifactService
artifact_service = InMemoryArtifactService()

# Database-backed (persistent)
# ADK supports GcsArtifactService for Google Cloud Storage.
# For custom storage, implement the ArtifactService protocol.
```

## When the artifact approach makes sense

- **Large binary artifacts** (images, PDFs, CSV exports) that shouldn't be embedded in session events
- **Independently addressable artifacts** where you need a URL to download/share a specific chart
- **Cross-session artifacts** where the same chart is referenced from multiple sessions
- **Artifact versioning** where you need history of how a chart changed

For JSON chart/table configs under 100KB, the `function_response` approach is simpler and sufficient.

---

## Current Approach (what we use today)

Tools return chart/table configs as their return value:

```
create_chart() → return {"success": True, "chart": chart_config}
```

ADK automatically stores this as a `function_response` event in the session. The server reads it from the event stream:

```
runner.run_async() yields event with part.function_response.response = {"chart": {...}}
  → extract_content_blocks() picks up the "chart" key
  → Collected into content_blocks list
  → Sent as SSE: {"type": "content_blocks", "blocks": [...]}
```

For session history, `events_to_messages()` in `serializers.py` walks stored events and extracts charts/tables from `function_response` parts — no artifact service involved.
