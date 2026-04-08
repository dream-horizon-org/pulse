#!/bin/bash
# Fix TCP Memory and Buffer Limits for High Concurrency

echo "Applying TCP memory and buffer optimizations..."

# Increase TCP memory (in pages, 4KB each)
# Format: min pressure max
# For 10K connections: ~1.5GB min, 3GB pressure, 6GB max
sudo sysctl -w net.ipv4.tcp_mem='393216 786432 1572864'

# Increase TCP receive buffers
sudo sysctl -w net.ipv4.tcp_rmem='4096 87380 33554432'

# Increase TCP send buffers  
sudo sysctl -w net.ipv4.tcp_wmem='4096 65536 33554432'

# Increase max buffer sizes
sudo sysctl -w net.core.rmem_max=33554432
sudo sysctl -w net.core.wmem_max=33554432
sudo sysctl -w net.core.rmem_default=87380
sudo sysctl -w net.core.wmem_default=65536

# Make permanent
sudo tee -a /etc/sysctl.conf << EOF

# TCP Memory and Buffer Optimizations for High Concurrency
net.ipv4.tcp_mem = 393216 786432 1572864
net.ipv4.tcp_rmem = 4096 87380 33554432
net.ipv4.tcp_wmem = 4096 65536 33554432
net.core.rmem_max = 33554432
net.core.wmem_max = 33554432
net.core.rmem_default = 87380
net.core.wmem_default = 65536
EOF

# Apply changes
sudo sysctl -p

echo "TCP memory optimizations applied!"
echo "Current values:"
sysctl net.ipv4.tcp_mem net.ipv4.tcp_rmem net.ipv4.tcp_wmem net.core.rmem_max net.core.wmem_max
