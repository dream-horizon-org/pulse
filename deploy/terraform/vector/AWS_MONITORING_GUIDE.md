# AWS CloudWatch Monitoring Guide for Vector

## 1. View CPU Metrics in AWS Console

### Quick Access:
1. **EC2 Console** → **Instances** → Select Vector instance
2. Click **Monitoring** tab
3. View graphs for:
   - **CPU Utilization** (should be < 80%)
   - **Network In/Out**
   - **Status Check Failed**

### Detailed CloudWatch Metrics:
1. **CloudWatch Console** → **Metrics** → **EC2**
2. Select **Per-Instance Metrics**
3. Find your Vector instance
4. Key metrics to monitor:
   - `CPUUtilization` - Should be < 80%
   - `NetworkIn` / `NetworkOut` - Traffic volume
   - `StatusCheckFailed` - Instance health

## 2. Create CloudWatch Dashboard

### Step-by-Step:
1. **CloudWatch Console** → **Dashboards** → **Create Dashboard**
2. Add widgets for:
   - **CPU Utilization** (Line graph)
   - **Network In/Out** (Line graph)
   - **Status Check** (Number widget)
   - **Connection Count** (if available)

### CloudWatch Query (CLI):
```bash
# Get CPU utilization for last hour
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=i-xxxxxxxxx \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum \
  --region ap-south-1
```

## 3. Real-Time Monitoring on Instance

### Option 1: Use the monitoring script
```bash
chmod +x monitor_cpu.sh
./monitor_cpu.sh
```

### Option 2: Manual monitoring
```bash
# Watch CPU in real-time
watch -n 1 'top -p $(pgrep vector) -b -n 1'

# Or use htop (if installed)
htop -p $(pgrep vector)

# System-wide CPU
top
```

## 4. CPU Bottleneck Analysis

### Check what's consuming CPU:

```bash
# Top processes by CPU
ps aux --sort=-%cpu | head -10

# Vector-specific CPU breakdown
top -p $(pgrep vector) -H -b -n 1 | head -20

# Per-thread CPU usage
ps -T -p $(pgrep vector) -o pid,tid,class,rtprio,ni,pri,psr,pcpu,stat,wchan:14,comm
```

### Common CPU Consumers:
1. **Vector processing** - Event transforms, encoding
2. **S3 uploads** - Compression, network I/O
3. **Network stack** - TCP processing for 5000+ connections
4. **System overhead** - Context switching between connections

## 5. CPU Optimization Strategies

### A. Reduce Vector Processing Load

Check Vector config for CPU-intensive operations:

```bash
# On Vector instance
cat /var/lib/vector/vector.yaml | grep -A 20 "transforms:"
```

Optimize transforms:
- Simplify remap expressions
- Reduce regex operations
- Minimize string operations

### B. Optimize S3 Sink

Reduce S3 API calls (each call uses CPU):

```yaml
sinks:
  s3_events:
    batch:
      max_bytes: 536870912  # 512 MB (larger batches = fewer API calls)
      timeout_secs: 60       # Wait longer before flushing
    compression: "zstd"      # Fast compression
```

### C. Reduce Connection Overhead

With 5000 connections, each connection has overhead:
- **Solution**: Use connection pooling/reuse
- **Locust**: Already does this, but verify
- **Alternative**: Reduce concurrent users, increase batch size

### D. Upgrade Instance Type

Current: `t3.large` (2 vCPU, 8GB RAM)
- **CPU Credits**: Burstable, can throttle under sustained load

**Recommended Upgrades:**
- `t3.xlarge` (4 vCPU, 16GB RAM) - 2x CPU
- `c5.2xlarge` (8 vCPU, 16GB RAM) - 4x CPU, dedicated
- `c5.4xlarge` (16 vCPU, 32GB RAM) - 8x CPU, for 10K+ users

### E. Horizontal Scaling (Best for 10K users)

Add more Vector instances:
- Update ASG: `min_size = 3, max_size = 10`
- Each instance handles ~3K-5K connections
- NLB distributes load automatically

## 6. CloudWatch Alarms

### Create CPU Alarm:

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name vector-high-cpu \
  --alarm-description "Vector CPU utilization too high" \
  --metric-name CPUUtilization \
  --namespace AWS/EC2 \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=InstanceId,Value=i-xxxxxxxxx \
  --region ap-south-1
```

### Create via Console:
1. **CloudWatch** → **Alarms** → **Create Alarm**
2. Select metric: `CPUUtilization`
3. Set threshold: **80%**
4. Actions: Send SNS notification

## 7. Monitor During Load Test

### On Vector Instance:
```bash
# Terminal 1: CPU monitoring
./monitor_cpu.sh

# Terminal 2: Vector logs
journalctl -u vector -f | grep -i "cpu\|slow\|error"

# Terminal 3: System resources
watch -n 1 'free -h && uptime'
```

### In AWS Console:
- **EC2** → **Instances** → Vector instance → **Monitoring** tab
- Watch CPU graph in real-time
- Check if CPU credits are being exhausted (for t3 instances)

## 8. CPU Credit Exhaustion (t3 instances)

If using `t3.large`:
- **Baseline**: 20% CPU (40% of 2 vCPU)
- **Burst**: Up to 100% CPU using credits
- **Problem**: Sustained 97% CPU will exhaust credits → throttling

**Check CPU Credits:**
```bash
# In CloudWatch, check:
# - CPUCreditBalance (remaining credits)
# - CPUSurplusCreditBalance (borrowed credits)
# - CPUSurplusCreditsCharged (charges for borrowed)
```

**If credits exhausted:**
- Upgrade to `t3.xlarge` (more baseline)
- Or switch to `c5` instance (dedicated CPU, no credits)

## 9. Quick CPU Reduction Tips

### Immediate:
1. **Reduce concurrent users** to 2000-3000
2. **Increase batch sizes** (fewer requests = less CPU)
3. **Simplify Vector transforms** (if complex)

### Long-term:
1. **Add more Vector instances** (horizontal scaling)
2. **Upgrade instance type** (vertical scaling)
3. **Optimize Vector config** (reduce processing)

## 10. Expected CPU Usage

| Load | Expected CPU | Action if Higher |
|------|--------------|------------------|
| 100 users | 5-10% | Normal |
| 1000 users | 20-30% | Normal |
| 5000 users | 50-70% | Acceptable |
| 5000 users | 80-97% | **Optimize or scale** |
| 10000 users | 90-100% | **Must scale** |

## Summary

**For 97% CPU with 5000 users:**
1. ✅ Monitor in CloudWatch (see above)
2. ✅ Check CPU credits (if t3 instance)
3. ✅ Optimize Vector config (larger batches)
4. ✅ Consider upgrading instance type
5. ✅ Add more Vector instances (best solution)

**Immediate action**: Reduce to 2000-3000 users OR add more instances to distribute load.
