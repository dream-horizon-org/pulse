#!/bin/bash
# Real-time CPU and Resource Monitoring Script
# Run this on Vector instance during load test

echo "=========================================="
echo "VECTOR CPU & RESOURCE MONITORING"
echo "=========================================="
echo "Press Ctrl+C to stop"
echo ""

VECTOR_PID=$(pgrep vector)

while true; do
    clear
    echo "=========================================="
    echo "VECTOR CPU & RESOURCE MONITORING"
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "=========================================="
    echo ""
    
    # CPU Usage
    echo "=== CPU USAGE ==="
    echo "System Load:"
    uptime
    echo ""
    echo "Vector Process CPU:"
    ps -p $VECTOR_PID -o %cpu,time,cmd --no-headers 2>/dev/null || echo "Vector not running"
    echo ""
    echo "Top CPU Processes:"
    ps aux --sort=-%cpu | head -6
    echo ""
    
    # Memory Usage
    echo "=== MEMORY USAGE ==="
    free -h
    echo ""
    echo "Vector Memory:"
    ps -p $VECTOR_PID -o %mem,rss,vsz --no-headers 2>/dev/null | awk '{printf "  Memory: %.1f%% (RSS: %.1f MB, VSZ: %.1f MB)\n", $1, $2/1024, $3/1024}'
    echo ""
    
    # Connections
    echo "=== CONNECTIONS ==="
    ESTAB=$(ss -tnp | grep :4318 | grep ESTAB | wc -l)
    TOTAL=$(ss -tnp | grep :4318 | wc -l)
    echo "  Port 4318: $ESTAB established, $TOTAL total"
    echo ""
    
    # File Descriptors
    echo "=== FILE DESCRIPTORS ==="
    FD_COUNT=$(lsof -p $VECTOR_PID 2>/dev/null | wc -l)
    FD_LIMIT=$(cat /proc/$VECTOR_PID/limits 2>/dev/null | grep "open files" | awk '{print $4}')
    echo "  Used: $FD_COUNT / $FD_LIMIT"
    echo ""
    
    # Network Stats
    echo "=== NETWORK STATISTICS ==="
    ss -s | head -10
    echo ""
    
    # Vector Metrics (if available)
    echo "=== VECTOR METRICS (if available) ==="
    curl -s http://localhost:8686/metrics 2>/dev/null | grep -E "vector_events|vector_components.*events" | head -5 || echo "Metrics not available"
    echo ""
    
    echo "=========================================="
    echo "Refreshing in 2 seconds... (Ctrl+C to stop)"
    sleep 2
done
