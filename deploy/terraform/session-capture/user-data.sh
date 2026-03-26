#!/bin/bash
set -euo pipefail

exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting session-capture user-data at $(date)"

# -------------------------------------------------------------------
# Write environment config
# -------------------------------------------------------------------
cat > /etc/pulse/capture.env <<EOF
PORT=${port}
KAFKA_BROKERS=${kafka_brokers}
KAFKA_TOPIC=${kafka_topic}
RUST_LOG=${rust_log}
EOF

chmod 644 /etc/pulse/capture.env

# -------------------------------------------------------------------
# Start the service
# -------------------------------------------------------------------
echo "Starting pulse-session-capture service..."
systemctl restart pulse-session-capture

sleep 3

if systemctl is-active --quiet pulse-session-capture; then
    echo "Service started successfully"
else
    echo "WARNING: Service may not have started. Checking logs:"
    journalctl -u pulse-session-capture --no-pager -n 30
fi

echo "User-data complete at $(date)"
echo "View logs: journalctl -u pulse-session-capture -f"
