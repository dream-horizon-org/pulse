# Vector 10K Concurrent Users Diagnostic Guide

## Step 1: Run Comprehensive Diagnostic

On Vector instance, run:
```bash
chmod +x diagnose_vector.sh
./diagnose_vector.sh > diagnostic_output.txt
```

## Step 2: Check Each Bottleneck

### A. Network Stack Limits (Most Common)

```bash
# Check current values
sysctl net.ipv4.tcp_max_syn_backlog
sysctl net.core.somaxconn
sysctl net.ipv4.tcp_mem
sysctl net.core.netdev_max_backlog

# Increase if low
sudo sysctl -w net.ipv4.tcp_max_syn_backlog=16384
sudo sysctl -w net.core.somaxconn=16384
sudo sysctl -w net.core.netdev_max_backlog=5000
sudo sysctl -w net.ipv4.tcp_mem='262144 524288 1048576'
sudo sysctl -w net.ipv4.tcp_rmem='4096 87380 16777216'
sudo sysctl -w net.ipv4.tcp_wmem='4096 65536 16777216'

# Make permanent
sudo tee -a /etc/sysctl.conf << EOF
net.ipv4.tcp_max_syn_backlog = 16384
net.core.somaxconn = 16384
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_mem = 262144 524288 1048576
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
EOF

sudo sysctl -p
```

### B. Connection Tracking (if using NAT/firewall)

```bash
# Check connection tracking
sysctl net.netfilter.nf_conntrack_max

# Increase if needed
sudo sysctl -w net.netfilter.nf_conntrack_max=262144
echo "net.netfilter.nf_conntrack_max = 262144" | sudo tee -a /etc/sysctl.conf
```

### C. Load Balancer Limits (NLB)

**NLB Limits:**
- **Per-target connection limit**: ~55,000 connections per target
- **Connection rate**: ~1,000 new connections per second per target
- **Active flow limit**: ~1 million flows per NLB

**Check AWS Console:**
1. Go to EC2 → Target Groups → `pulse-vector-tg`
2. Check **Health** tab - are targets healthy?
3. Check **Monitoring** tab:
   - `ActiveConnectionCount` - should be < 55,000
   - `NewConnectionCount` - should be < 1,000/sec
   - `TargetConnectionErrorCount` - should be 0
   - `UnHealthyHostCount` - should be 0

**If hitting NLB limits:**
- Add more Vector instances to the target group
- Use multiple target groups
- Increase instance count in ASG

### D. Vector Configuration Limits

Check Vector config for connection limits:

```bash
# On Vector instance
cat /var/lib/vector/vector.yaml | grep -A 10 "sources:"
```

Vector might have connection limits in the source configuration. Check for:
- `max_connections`
- `connection_limit`
- `backpressure`

### E. System Resource Limits

```bash
# Check all limits
ulimit -a

# Check system-wide limits
cat /etc/security/limits.conf | grep -v "^#"

# Check Vector service limits
systemctl show vector | grep Limit
```

### F. Monitor During Load Test

Run these in separate terminals while load testing:

**Terminal 1: Connection States**
```bash
watch -n 1 'ss -ant | grep :4318 | awk '\''{print $1}'\'' | sort | uniq -c'
```

**Terminal 2: File Descriptors**
```bash
watch -n 1 'lsof -p $(pgrep vector) 2>/dev/null | wc -l'
```

**Terminal 3: Network Stats**
```bash
watch -n 1 'ss -s'
```

**Terminal 4: Vector Logs**
```bash
journalctl -u vector -f | grep -i "error\|fail\|refused\|timeout"
```

**Terminal 5: System Resources**
```bash
watch -n 1 'top -p $(pgrep vector) -b -n 1'
```

## Step 3: Identify the Bottleneck

### Symptom: Many SYN-RECV connections
**Cause**: `tcp_max_syn_backlog` too low
**Fix**: Increase to 16384+

### Symptom: Connection refused errors
**Cause**: File descriptor limit or connection queue full
**Fix**: Increase `LimitNOFILE` and `somaxconn`

### Symptom: Timeouts
**Cause**: Vector CPU/memory saturation or slow processing
**Fix**: Check Vector CPU/memory, optimize config

### Symptom: 5xx errors from load balancer
**Cause**: NLB connection limits or unhealthy targets
**Fix**: Add more instances, check target health

### Symptom: Connections drop after establishing
**Cause**: Connection tracking limits or memory pressure
**Fix**: Increase `nf_conntrack_max`, check memory

## Step 4: Vector-Specific Optimizations

### Check Vector Buffer Settings

In Vector config, ensure buffers are large enough:

```yaml
sources:
  otlp_logs:
    type: opentelemetry
    http:
      address: "0.0.0.0:4318"
      # Add these if available:
      # max_connections: 10000
      # backpressure: true
```

### Check Vector Sink Settings

Ensure S3 sink can handle the load:

```yaml
sinks:
  s3_events:
    type: aws_s3
    # Ensure these are optimized:
    buffer:
      max_size: 10737418240  # 10GB
    request:
      concurrency: adaptive
      timeout_secs: 60
    batch:
      max_bytes: 268435456  # 256MB
      timeout_secs: 30
```

## Step 5: Load Balancer Optimization

### NLB Connection Draining

If connections are hanging:
- Check target group `deregistration_delay.timeout_seconds`
- Should be 0-300 seconds

### Health Check Settings

```bash
# Check health check configuration
aws elbv2 describe-target-health \
  --target-group-arn <your-target-group-arn>
```

Health checks should be:
- Frequent enough (every 30s)
- Not too aggressive (don't overwhelm Vector)
- Using correct port (8686 for Vector API)

## Step 6: Scaling Solution

If single instance can't handle 10K:

1. **Horizontal Scaling**: Add more Vector instances
   - Update ASG: `min_size = 3, max_size = 10`
   - NLB will distribute load

2. **Vertical Scaling**: Larger instance type
   - Current: `t3.large` (2 vCPU, 8GB RAM)
   - Upgrade to: `t3.xlarge` (4 vCPU, 16GB RAM) or `c5.2xlarge`

3. **Connection Pooling**: Use connection reuse
   - Locust already does this, but verify

## Quick Fix Checklist

Run these commands on Vector instance:

```bash
# 1. Increase all network limits
sudo sysctl -w net.ipv4.tcp_max_syn_backlog=16384
sudo sysctl -w net.core.somaxconn=16384
sudo sysctl -w net.core.netdev_max_backlog=5000
sudo sysctl -w net.ipv4.tcp_mem='262144 524288 1048576'

# 2. Make permanent
sudo tee -a /etc/sysctl.conf << EOF
net.ipv4.tcp_max_syn_backlog = 16384
net.core.somaxconn = 16384
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_mem = 262144 524288 1048576
EOF
sudo sysctl -p

# 3. Restart Vector
sudo systemctl restart vector

# 4. Verify
cat /proc/$(pgrep vector)/limits | grep "open files"
sysctl net.ipv4.tcp_max_syn_backlog net.core.somaxconn
```

## Expected Results After Fixes

- **SYN-RECV connections**: Should be < 1000 (not stuck at 512)
- **Established connections**: Should match your user count
- **File descriptors**: Should be < 65536 limit
- **CPU/Memory**: Should be reasonable (< 80%)
- **Error rate**: Should be < 1%

## If Still Failing

1. **Check AWS CloudWatch** for NLB metrics
2. **Add more Vector instances** to distribute load
3. **Upgrade instance type** for more resources
4. **Check Vector logs** for specific error messages
5. **Test incrementally**: 1K → 2K → 5K → 10K users
