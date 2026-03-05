"""
Pulse AI FastAPI Server package.

Re-exports ``app`` so that ``uvicorn pulse_ai.server:app`` continues to work
without any change to the Dockerfile or local dev commands.
"""

from .app import app  # noqa: F401

# Import routes so they register on the app at import time.
from . import routes  # noqa: F401

__all__ = ["app"]
