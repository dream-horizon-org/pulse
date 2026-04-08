#!/bin/bash
# Comprehensive Vector Diagnostic Script
# Run this on the Vector instance during load test

echo "=========================================="
echo "VECTOR COMPREHENSIVE DIAGNOSTIC"
echo "=========================================="
echo "Timestamp: $(date)"
echo ""

# 1. Vector Process Info
echo "=== 1. VECTOR PROCESS ==="
VECTOR_PID=$(pgrep vector)
if [ -z "$VECTOR_PID" ]; then
    echo "ERROR: Vector process not found!"
    exit 1
fi
echo "Vector PID: $VECTOR_PID"
ps aux | grep vector | grep -v grep
echo ""

# 2. File Descriptors
echo "=== 2. FILE DESCRIPTOR LIMITS ==="
echo "Process limits:"
cat /proc/$VECTOR_PID/limits | grep "open files"
echo "Current usage:"
lsof -p $VECTOR_PID 2>/dev/null | wc -l
echo "System-wide:"
cat /proc/sys/fs/file-nr
echo ""

# 3. Network Connections
echo "=== 3. NETWORK CONNECTIONS ==="
echo "Total connections to port 4318:"
ss -tnp | grep :4318 | wc -l
echo "Connection states breakdown:"
ss -ant | grep :4318 | awk '{print $1}' | sort | uniq -c
echo "Established connections:"
ss -tnp | grep :4318 | grep ESTAB | wc -l
echo "SYN-RECV (half-open):"
ss -ant | grep :4318 | grep SYN-RECV | wc -l
echo "TIME-WAIT:"
ss -ant | grep :4318 | grep TIME-WAIT | wc -l
echo ""

# 4. Network Stack Limits
echo "=== 4. NETWORK STACK LIMITS ==="
echo "SYN backlog:"
sysctl net.ipv4.tcp_max_syn_backlog
echo "Connection queue:"
sysctl net.core.somaxconn
echo "TCP memory:"
sysctl net.ipv4.tcp_mem
echo "TCP connection tracking:"
sysctl net.netfilter.nf_conntrack_max 2>/dev/null || echo "Not available"
echo ""

# 5. System Resources
echo "=== 5. SYSTEM RESOURCES ==="
echo "CPU Load:"
uptime
echo "Memory:"
free -h
echo "Vector CPU/Memory:"
ps -p $VECTOR_PID -o %cpu,%mem,rss,vsz
echo ""

# 6. Network Statistics
echo "=== 6. NETWORK STATISTICS ==="
ss -s
echo ""

# 7. Vector Logs (Recent Errors)
echo "=== 7. RECENT VECTOR ERRORS (last 20 lines) ==="
journalctl -u vector -n 20 --no-pager | grep -i "error\|fail\|warn\|refused\|timeout" || echo "No errors found"
echo ""

# 8. Listening Ports
echo "=== 8. LISTENING PORTS ==="
ss -tlnp | grep vector
echo ""

# 9. Connection Rate
echo "=== 9. CONNECTION RATE ==="
echo "Connections per second (last 5 seconds):"
for i in {1..5}; do
    COUNT=$(ss -tnp | grep :4318 | wc -l)
    echo "  Second $i: $COUNT connections"
    sleep 1
done
echo ""

# 10. Load Balancer Health (if applicable)
echo "=== 10. LOAD BALANCER INFO ==="
echo "Check AWS Console for:"
echo "  - Target Group health"
echo "  - Connection count metrics"
echo "  - 5xx error rates"
echo ""

# 11. Vector Internal Metrics
echo "=== 11. VECTOR METRICS (if available) ==="
curl -s http://localhost:8686/metrics 2>/dev/null | grep -E "vector_events|vector_components|vector_internal" | head -20 || echo "Metrics endpoint not accessible"
echo ""

echo "=========================================="
echo "DIAGNOSTIC COMPLETE"
echo "=========================================="
