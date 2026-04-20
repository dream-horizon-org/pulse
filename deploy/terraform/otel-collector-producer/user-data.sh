#!/bin/bash
set -e

exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

sudo /usr/bin/aws s3 cp s3://puls-otel-config/otel-collector/otel-producer-config.yaml /etc/otelcol-contrib/config.yaml

systemctl restart otelcol-contrib
echo "otelcol-contrib started. User-data script completed at $(date)"