#!/bin/bash
set -euxo pipefail

REGION="${region}"

# Ensure config directory exists for otelcol-contrib
mkdir -p /etc/otelcol-contrib

# Write OTEL config from template variable
cat > /etc/otelcol-contrib/config.yaml <<EOF
${otel_config}
EOF

############################################
# Give this instance a unique name
############################################
INSTANCE_ID="$(curl -s http://169.254.169.254/latest/meta-data/instance-id || echo "")"

if [ -n "$INSTANCE_ID" ]; then
  NEW_NAME="pulse-otel-collector-1-$INSTANCE_ID"

  # Set Linux hostname (best-effort)
  if command -v hostnamectl >/dev/null 2>&1; then
    hostnamectl set-hostname "$NEW_NAME" || true
  else
    echo "$NEW_NAME" > /etc/hostname || true
  fi

  # Tag the EC2 instance with Name = otel-collector-<instance-id>
  # Requires the instance profile to have ec2:CreateTags permission
  if command -v aws >/dev/null 2>&1; then
    aws ec2 create-tags \
      --region "$REGION" \
      --resources "$INSTANCE_ID" \
      --tags Key=Name,Value="$NEW_NAME" Key=service,Value="pulse" || true
  fi
fi

############################################
# Optional firewall opening
############################################
if command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --add-port=${collector_port}/tcp --permanent || true
  firewall-cmd --reload || true
elif command -v iptables >/dev/null 2>&1; then
  iptables -I INPUT -p tcp --dport ${collector_port} -j ACCEPT || true
fi

############################################
# Restart OTEL collector contrib service
############################################
if systemctl is-enabled otelcol-contrib >/dev/null 2>&1; then
  systemctl restart otelcol-contrib
else
  systemctl start otelcol-contrib || true
fi
