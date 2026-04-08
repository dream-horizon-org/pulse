# Fix for 5000 Users - Events Dropping Issue

## Problem Identified

From your diagnostic:
1. ✅ **Connections**: 5925 established - working fine
2. ❌ **Events Dropped**: Vector is dropping events (critical!)
3. ❌ **Connection Errors**: "connection closed before message completed"
4. ⚠️ **TCP Memory**: May be too low for 5925 connections

## Root Cause

**Vector is dropping events** because:
- Internal buffers are full
- S3 sink can't flush fast enough
- TCP buffers may be insufficient for 5925 concurrent connections

## Immediate Fixes

### 1. Increase TCP Memory Buffers (CRITICAL)

On Vector instance, run:

```bash
chmod +x fix_tcp_memory.sh
sudo ./fix_tcp_memory.sh
```

Or manually:

```bash
# Increase TCP memory (for 10K connections: ~1.5GB min, 3GB pressure, 6GB max)
sudo sysctl -w net.ipv4.tcp_mem='393216 786432 1572864'

# Increase TCP receive/send buffers
sudo sysctl -w net.ipv4.tcp_rmem='4096 87380 33554432'
sudo sysctl -w net.ipv4.tcp_wmem='4096 65536 33554432'
sudo sysctl -w net.core.rmem_max=33554432
sudo sysctl -w net.core.wmem_max=33554432

# Make permanent
sudo tee -a /etc/sysctl.conf << EOF
net.ipv4.tcp_mem = 393216 786432 1572864
net.ipv4.tcp_rmem = 4096 87380 33554432
net.ipv4.tcp_wmem = 4096 65536 33554432
net.core.rmem_max = 33554432
net.core.wmem_max = 33554432
EOF

sudo sysctl -p
```

### 2. Check Vector Configuration

On Vector instance, check the config:

```bash
cat /var/lib/vector/vector.yaml
```

Look for the S3 sink configuration. It should have:

```yaml
sinks:
  s3_events:
    type: aws_s3
    buffer:
      type: "disk"
      max_size: 10737418240  # 10 GB - ensure this is large enough
      when_full: "block"     # Important: block when full, don't drop
    request:
      concurrency: "adaptive"  # Let Vector scale connections
      timeout_secs: 60
    batch:
      max_bytes: 268435456  # 256 MB
      timeout_secs: 30      # Flush every 30 seconds max
```

**Key setting**: `when_full: "block"` - This prevents events from being dropped.

### 3. Check S3 Sink Performance

The S3 sink might be the bottleneck. Check:

```bash
# Watch Vector metrics during load test
curl -s http://localhost:8686/metrics | grep -E "vector_events|vector_components|s3"
```

Look for:
- `vector_events_dropped_total` - should be 0
- `vector_components_events_in_total` - incoming events
- `vector_components_events_out_total` - outgoing to S3

If `events_out` is much lower than `events_in`, S3 is the bottleneck.

### 4. Optimize Vector Source Configuration

If Vector config allows, add connection limits:

```yaml
sources:
  otlp_logs:
    type: opentelemetry
    http:
      address: "0.0.0.0:4318"
      # If Vector supports these (check docs):
      # max_connections: 10000
      # keepalive: true
      # keepalive_timeout_secs: 60
```

### 5. Monitor During Test

Run these while testing:

**Terminal 1: Events Dropped**
```bash
watch -n 1 'curl -s http://localhost:8686/metrics 2>/dev/null | grep "events_dropped" || echo "No drops"'
```

**Terminal 2: Connection States**
```bash
watch -n 1 'ss -ant | grep :4318 | awk '\''{print $1}'\'' | sort | uniq -c'
```

**Terminal 3: Vector Logs**
```bash
journalctl -u vector -f | grep -i "drop\|error\|fail"
```

## Expected Results After Fixes

- **Events Dropped**: Should be 0 (not flooding)
- **Connection Errors**: Should decrease significantly
- **TCP Memory**: Should handle 10K connections comfortably
- **Success Rate**: Should be > 99%

## If Still Dropping Events

### Option 1: Increase S3 Batch Size

Make batches larger to reduce S3 API calls:

```yaml
batch:
  max_bytes: 536870912  # 512 MB (double the current)
  timeout_secs: 60       # Wait longer before flushing
```

### Option 2: Add More Vector Instances

If single instance can't handle it:
- Add more instances to ASG
- NLB will distribute load
- Each instance handles fewer connections

### Option 3: Upgrade Instance Type

Current: `t3.large` (2 vCPU, 8GB RAM)
Upgrade to: `t3.xlarge` (4 vCPU, 16GB RAM) or `c5.2xlarge` (8 vCPU, 16GB RAM)

## Verification

After applying fixes, test:

```bash
# Test with 5000 users
locust --headless -u 5000 -r 500 -t 2m --host http://pulse-vector.delivr.local:4318
```

Check:
1. Events dropped = 0
2. Connection errors minimal
3. Success rate > 99%

## Summary

**Priority fixes:**
1. ✅ Increase TCP memory buffers (fix_tcp_memory.sh)
2. ✅ Ensure `when_full: "block"` in Vector config
3. ✅ Monitor events_dropped metric
4. ✅ Check S3 sink performance

The main issue is **events being dropped** - this is likely due to:
- TCP buffers too small (fix with script)
- S3 sink too slow (optimize batch settings)
- Vector buffers full (ensure `when_full: "block"`)
