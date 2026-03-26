#!/bin/bash
set -euo pipefail

exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting session-ingestion user-data at $(date)"

# Golden AMI supplies built app + systemd unit; only inject env-specific config.
cat > /etc/pulse/ingestion.env <<EOF
KAFKA_BROKERS=${kafka_brokers}
KAFKA_TOPIC=${kafka_topic}
KAFKA_METADATA_TOPIC=${kafka_metadata_topic}
KAFKA_GROUP_ID=${kafka_group_id}
S3_ENDPOINT=${s3_endpoint}
S3_BUCKET=${s3_bucket}
S3_REGION=${s3_region}
S3_PREFIX=${s3_prefix}
MAX_BATCH_SIZE_KB=${max_batch_size_kb}
MAX_BATCH_AGE_MS=${max_batch_age_ms}
FETCH_BATCH_SIZE=${fetch_batch_size}
S3_TIMEOUT_MS=${s3_timeout_ms}
EOF

chmod 644 /etc/pulse/ingestion.env

echo "Starting pulse-session-replay-ingestion service..."
systemctl restart pulse-session-replay-ingestion

sleep 5

if systemctl is-active --quiet pulse-session-replay-ingestion; then
  echo "Service started successfully"
else
  echo "WARNING: Service may not have started. Checking logs:"
  journalctl -u pulse-session-replay-ingestion --no-pager -n 30
fi

echo "User-data complete at $(date)"
echo "View logs: journalctl -u pulse-session-replay-ingestion -f"
