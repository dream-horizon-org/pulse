#!/bin/bash
set -e

exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

mkdir -p /var/lib/otelcol-contrib
sudo /usr/bin/aws s3 cp s3://pulse-geo-ip-mmdb/GeoLite2-City.mmdb /var/lib/otelcol-contrib/GeoLite2-City.mmdb || true

sudo /usr/bin/aws s3 cp s3://puls-otel-config/otel-collector/otel-producer-config.yaml /etc/otelcol-contrib/config.yaml

systemctl restart otelcol-contrib
echo "otelcol-contrib started. User-data script completed at $(date)"