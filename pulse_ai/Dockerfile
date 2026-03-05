FROM python:3.12.13-slim

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

EXPOSE 8000

CMD ["uvicorn", "pulse_ai.server:app", "--host", "0.0.0.0", "--port", "8000"]
