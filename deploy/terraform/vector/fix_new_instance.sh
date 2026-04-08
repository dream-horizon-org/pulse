#!/bin/bash
# Fix Vector on New Instance - Run this on the Vector instance

echo "=========================================="
echo "FIXING VECTOR ON NEW INSTANCE"
echo "=========================================="
echo ""

# 1. Check Vector service status
echo "=== 1. Vector Service Status ==="
systemctl status vector --no-pager | head -15
echo ""

# 2. Check if port 4318 is listening
echo "=== 2. Port 4318 Status ==="
if ss -tlnp 2>/dev/null | grep -q ":4318"; then
    echo "✅ Port 4318 is LISTENING"
    ss -tlnp | grep 4318
else
    echo "❌ Port 4318 is NOT listening"
    echo "Vector may not be running or configured incorrectly"
fi
echo ""

# 3. Check file descriptor limits
echo "=== 3. File Descriptor Limits ==="
VECTOR_PID=$(pgrep vector)
if [ -z "$VECTOR_PID" ]; then
    echo "❌ Vector process not found!"
else
    echo "Vector PID: $VECTOR_PID"
    cat /proc/$VECTOR_PID/limits | grep "open files"
    echo "Current usage: $(lsof -p $VECTOR_PID 2>/dev/null | wc -l) file descriptors"
fi
echo ""

# 4. Fix file descriptor limit (increased for long-running high load)
echo "=== 4. Applying File Descriptor Fix (High Capacity) ==="
sudo mkdir -p /etc/systemd/system/vector.service.d/
sudo tee /etc/systemd/system/vector.service.d/limits.conf << EOF
[Service]
LimitNOFILE=262144
LimitNPROC=65536
EOF
echo "✅ Set file descriptor limit to 262144 (4x increase for long-running load)"
echo ""

# 5. Apply TCP optimizations for long-running connections
echo "=== 5. Applying TCP Optimizations ==="
sudo sysctl -w net.ipv4.tcp_max_syn_backlog=16384
sudo sysctl -w net.core.somaxconn=16384
sudo sysctl -w net.core.netdev_max_backlog=5000
sudo sysctl -w net.ipv4.tcp_mem='393216 786432 1572864'
sudo sysctl -w net.ipv4.tcp_rmem='4096 87380 33554432'
sudo sysctl -w net.ipv4.tcp_wmem='4096 65536 33554432'
sudo sysctl -w net.core.rmem_max=33554432
sudo sysctl -w net.core.wmem_max=33554432

# TCP connection state optimizations (prevent TIME-WAIT accumulation)
sudo sysctl -w net.ipv4.tcp_tw_reuse=1
sudo sysctl -w net.ipv4.tcp_fin_timeout=30
sudo sysctl -w net.ipv4.tcp_keepalive_time=600
sudo sysctl -w net.ipv4.tcp_keepalive_probes=3
sudo sysctl -w net.ipv4.tcp_keepalive_intvl=15

# Make permanent
sudo tee -a /etc/sysctl.conf << EOF

# Vector High-Load Optimizations
net.ipv4.tcp_max_syn_backlog = 16384
net.core.somaxconn = 16384
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_mem = 393216 786432 1572864
net.ipv4.tcp_rmem = 4096 87380 33554432
net.ipv4.tcp_wmem = 4096 65536 33554432
net.core.rmem_max = 33554432
net.core.wmem_max = 33554432
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_keepalive_intvl = 15
EOF

sudo sysctl -p
echo "✅ TCP optimizations applied"
echo ""

# 6. Reload systemd and restart Vector
echo "=== 6. Restarting Vector with New Limits ==="
sudo systemctl daemon-reload
sudo systemctl restart vector
sleep 3
systemctl status vector --no-pager | head -10
echo ""

# 7. Verify new limits
echo "=== 7. Verifying New Limits ==="
VECTOR_PID=$(pgrep vector)
if [ ! -z "$VECTOR_PID" ]; then
    echo "New file descriptor limit:"
    cat /proc/$VECTOR_PID/limits | grep "open files"
else
    echo "❌ Vector not running after restart"
fi
echo ""

# 8. Check port 4318 again
echo "=== 8. Final Port Check ==="
sleep 2
if ss -tlnp 2>/dev/null | grep -q ":4318"; then
    echo "✅ Port 4318 is now LISTENING"
    ss -tlnp | grep 4318
else
    echo "❌ Port 4318 still not listening"
    echo "Check Vector logs: journalctl -u vector -n 50"
fi
echo ""

# 9. Test local connection
echo "=== 9. Testing Local Connection ==="
curl -v http://localhost:4318/v1/logs 2>&1 | head -10
echo ""

echo "=========================================="
echo "FIX COMPLETE"
echo "=========================================="
