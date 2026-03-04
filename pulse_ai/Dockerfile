FROM python:3.12-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Copy and install Python dependencies first (layer caching)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy agent source code
COPY . /app/pulse_ai/

# ADK web UI runs on port 8000 by default
EXPOSE 8000

# Run ADK web UI with auto-restart on Python file changes (hot reload)
CMD ["watchmedo", "auto-restart", \
     "--directory", "/app/pulse_ai", \
     "--pattern", "*.py", \
     "--recursive", \
     "--", \
     "adk", "web", "--port", "8000", "--host", "0.0.0.0"]
