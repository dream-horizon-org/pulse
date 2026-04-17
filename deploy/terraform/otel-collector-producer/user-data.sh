#!/bin/bash
set -e

exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

mkdir -p /var/lib/otelcol-contrib
/usr/bin/aws s3 cp s3://pulse-geo-ip-mmdb/GeoLite2-City.mmdb /var/lib/otelcol-contrib/GeoLite2-City.mmdb || true

# -------------------------------------------------------------------
# 1. Overwrite the otelcol-contrib systemd service file
#    Adds ExecStartPre to pull the config from S3 before starting
# -------------------------------------------------------------------
cat > /lib/systemd/system/otelcol-contrib.service << 'EOF'
[Unit]
Description=OpenTelemetry Collector Contrib
After=network.target

[Service]
EnvironmentFile=/etc/otelcol-contrib/otelcol-contrib.conf
ExecStartPre=+/usr/bin/aws s3 cp s3://puls-otel-config/otel-collector/otel-producer-config.yaml /var/lib/otelcol-contrib/config.yaml
ExecStart=/usr/bin/otelcol-contrib $OTELCOL_OPTIONS
ExecReload=/bin/kill -HUP $MAINPID
KillMode=mixed
Restart=on-failure
Type=simple
User=otelcol-contrib
Group=otelcol-contrib

[Install]
WantedBy=multi-user.target
EOF

echo "Service file written."

# -------------------------------------------------------------------
# 2. Set OTELCOL_OPTIONS to point at the S3-downloaded config
# -------------------------------------------------------------------
mkdir -p /etc/otelcol-contrib
echo 'OTELCOL_OPTIONS=--config /var/lib/otelcol-contrib/config.yaml' > /etc/otelcol-contrib/otelcol-contrib.conf

echo "OTELCOL_OPTIONS configured."

# -------------------------------------------------------------------
# 3. Reload systemd and start the service
# -------------------------------------------------------------------
systemctl daemon-reload
systemctl restart otelcol-contrib

echo "otelcol-contrib started. User-data script completed at $(date)"
