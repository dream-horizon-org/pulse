# Locust Load Testing Setup for Vector

This guide explains how to use Locust to load test your Vector OTLP endpoint.

## Installation

### 1. Install Dependencies

```bash
pip install locust opentelemetry-proto
```

**Important:** The Vector endpoint expects **protobuf-encoded** data, not JSON. The `opentelemetry-proto` package is required for encoding.

Or using uvx (alternative):
```bash
# Install uv first, then:
uvx locust -V
```

**Reference:** [Locust Installation Guide](https://docs.locust.io/en/stable/installation.html)

### 2. Verify Installation

```bash
locust -V
```

Should output something like:
```
locust 2.43.1 from /usr/local/lib/python/3.12/site-packages/locust (Python 3.12.5)
```

## Quick Start

### Option 1: Web UI (Recommended for First Time)

```bash
cd /Users/abhishekkumar/Desktop/pulse/deploy/terraform/vector
locust --host http://pulse-vector.delivr.local:4318
```

Then open your browser to: **http://localhost:8089**

In the web UI:
- **Number of users**: Number of concurrent connections (e.g., 100, 1000, 10000)
- **Spawn rate**: Users to start per second (e.g., 10)
- Click **Start swarming**

### Option 2: Headless Mode (No UI)

```bash
# 100 concurrent users, spawn 10/sec, run for 5 minutes
locust --headless \
    -u 100 \
    -r 10 \
    -t 5m \
    --host http://pulse-vector.delivr.local:4318
```

### Option 3: High Concurrency (100k requests)

```bash
# 1000 concurrent users, spawn 50/sec, run for 10 minutes
locust --headless \
    -u 1000 \
    -r 50 \
    -t 10m \
    --host http://pulse-vector.delivr.local:4318
```

## Common Usage Examples

### Quick Test (1000 requests)
```bash
locust --headless -u 10 -r 2 -t 2m --host http://pulse-vector.delivr.local:4318
```

### Medium Load (10k requests)
```bash
locust --headless -u 100 -r 10 -t 5m --host http://pulse-vector.delivr.local:4318
```

### High Load (100k+ requests)
```bash
locust --headless -u 500 -r 50 -t 10m --host http://pulse-vector.delivr.local:4318
```

### Maximum Load (1M+ requests)
```bash
locust --headless -u 2000 -r 100 -t 30m --host http://pulse-vector.delivr.local:4318
```

## Command Line Options

| Option | Description | Example |
|-------|-------------|---------|
| `--host` | Target URL | `http://pulse-vector.delivr.local:4318` |
| `-u, --users` | Number of concurrent users | `1000` |
| `-r, --spawn-rate` | Users spawned per second | `50` |
| `-t, --run-time` | Test duration | `10m`, `1h`, `300s` |
| `--headless` | Run without web UI | |
| `-f, --locustfile` | Custom locustfile | `locustfile.py` |
| `--csv` | Export results to CSV | `--csv results` |
| `--html` | Generate HTML report | `--html report.html` |

## Understanding the Results

### Web UI Metrics

- **Total Requests**: Total HTTP requests sent
- **Requests/s**: Requests per second (RPS)
- **Failures**: Number of failed requests
- **Response Times**: Min, Median, 95th percentile, Max
- **Users**: Current number of concurrent users

### Response Time Percentiles

- **50% (Median)**: Half of requests completed in this time
- **95%**: 95% of requests completed in this time
- **99%**: 99% of requests completed in this time

## Performance Tips

### 1. Use FastHttpUser for Better Performance

Edit `locustfile.py` and uncomment the `FastHttpUser` class:

```python
# Install fast HTTP client
pip install locust[fast]

# Then use FastHttpUser instead of HttpUser
```

### 2. Increase OS File Descriptor Limits

Locust opens many connections. Increase your OS limits:

**macOS:**
```bash
# Check current limit
ulimit -n

# Increase limit (add to ~/.zshrc or ~/.bash_profile)
ulimit -n 65536
```

**Linux:**
```bash
# Edit /etc/security/limits.conf
* soft nofile 65536
* hard nofile 65536
```

**Reference:** [Locust - Increasing Maximum Number of Open Files Limit](https://docs.locust.io/en/stable/installation.html#increasing-maximum-number-of-open-files-limit)

### 3. Distributed Load Testing

For very high loads, run Locust in distributed mode:

**Master:**
```bash
locust --master --host http://pulse-vector.delivr.local:4318
```

**Workers (on other machines):**
```bash
locust --worker --master-host=<master-ip>
```

## Exporting Results

### CSV Export
```bash
locust --headless -u 100 -r 10 -t 5m \
    --host http://pulse-vector.delivr.local:4318 \
    --csv results
```

This creates:
- `results_stats.csv` - Request statistics
- `results_failures.csv` - Failed requests
- `results_stats_history.csv` - Time series data

### HTML Report
```bash
locust --headless -u 100 -r 10 -t 5m \
    --host http://pulse-vector.delivr.local:4318 \
    --html report.html
```

## Troubleshooting

### Connection Errors

If you see connection errors:
1. Check Vector is running: `nc -vz pulse-vector.delivr.local 4318`
2. Verify endpoint: `curl http://pulse-vector.delivr.local:4318/v1/logs`
3. Check firewall/security groups

### "Too many open files" Error

Increase file descriptor limits (see Performance Tips #2 above).

### Low Throughput

1. Use `FastHttpUser` instead of `HttpUser`
2. Increase `--spawn-rate` gradually
3. Check Vector instance CPU/memory
4. Check network bandwidth

## What the Script Does

The `locustfile.py` script:

1. **Creates OTLP log events** matching your Pulse schema
2. **Sends requests** to Vector's `/v1/logs` endpoint
3. **Simulates different patterns**:
   - Single event requests (most common)
   - Batches of 10 events
   - Batches of 100 events
4. **Tracks metrics**: Response times, success/failure rates, RPS

## Expected Results

After running the test, check your S3 bucket:
```
s3://puls-otel-config/year=YYYY/month=MM/day=DD/hour=HH/*.parquet
```

The parquet files should contain the transformed events with your Pulse schema.

## References

- [Locust Documentation](https://docs.locust.io/en/stable/)
- [Locust Installation](https://docs.locust.io/en/stable/installation.html)
- [Writing a locustfile](https://docs.locust.io/en/stable/writing-a-locustfile.html)
- [Configuration](https://docs.locust.io/en/stable/configuration.html)
