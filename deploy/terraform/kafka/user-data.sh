#!/bin/bash
set -euo pipefail

# Log all output
exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

ROLE="${role}"
NODE_ID="${node_id}"
NUM_CONTROLLERS="${num_controllers}"
NUM_BROKERS="${num_brokers}"
KRAFT_CLUSTER_ID="${kraft_cluster_id}"
KAFKA_DIR="/opt/kafka"
DATA_DIR="${kafka_data_dir}"          # mount point for broker data disk (e.g. /var/lib/kafka)
ZONE_NAME="${route53_zone_name}"

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nvme-cli util-linux e2fsprogs

echo "Setting up Prometheus JMX Exporter..."
mkdir -p /opt/prometheus
chmod +rx /opt/prometheus
cd /opt/prometheus

wget -q https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/1.0.1/jmx_prometheus_javaagent-1.0.1.jar

cat << 'EOF' | base64 -d > /opt/prometheus/kafka_broker.yml
bG93ZXJjYXNlT3V0cHV0TmFtZTogdHJ1ZQoKcnVsZXM6CiMgU3BlY2lhbCBjYXNlcyBhbmQgdmVyeSBzcGVjaWZpYyBydWxlcwotIHBhdHRlcm4gOiBrYWZrYS5zZXJ2ZXI8dHlwZT0oLispLCBuYW1lPSguKyksIGNsaWVudElkPSguKyksIHRvcGljPSguKyksIHBhcnRpdGlvbj0oLiopPjw+VmFsdWUKICBuYW1lOiBrYWZrYV9zZXJ2ZXJfJDFfJDIKICB0eXBlOiBHQVVHRQogIGxhYmVsczoKICAgIGNsaWVudElkOiAiJDMiCiAgICB0b3BpYzogIiQ0IgogICAgcGFydGl0aW9uOiAiJDUiCi0gcGF0dGVybiA6IGthZmthLnNlcnZlcjx0eXBlPSguKyksIG5hbWU9KC4rKSwgY2xpZW50SWQ9KC4rKSwgYnJva2VySG9zdD0oLispLCBicm9rZXJQb3J0PSguKyk+PD5WYWx1ZQogIG5hbWU6IGthZmthX3NlcnZlcl8kMV8kMgogIHR5cGU6IEdBVUdFCiAgbGFiZWxzOgogICAgY2xpZW50SWQ6ICIkMyIKICAgIGJyb2tlcjogIiQ0OiQ1IgotIHBhdHRlcm4gOiBrYWZrYS5jb29yZGluYXRvci4oXHcrKTx0eXBlPSguKyksIG5hbWU9KC4rKT48PlZhbHVlCiAgbmFtZToga2Fma2FfY29vcmRpbmF0b3JfJDFfJDJfJDMKICB0eXBlOiBHQVVHRQojIEtyYWZ0IGN1cnJlbnQgc3RhdGUgaW5mbyBtZXRyaWMgcnVsZQotIHBhdHRlcm46ICJrYWZrYS5zZXJ2ZXI8dHlwZT1yYWZ0LW1ldHJpY3M+PD5jdXJyZW50LXN0YXRlOiAoW2Etel0rKSIKICBuYW1lOiBrYWZrYV9zZXJ2ZXJfcmFmdF9tZXRyaWNzX2N1cnJlbnRfc3RhdGVfaW5mbwogIHR5cGU6IEdBVUdFCiAgdmFsdWU6IDEKICBsYWJlbHM6CiAgICAic3RhdGUiOiAiJDEiCiMgS3JhZnQgc3BlY2lmaWMgcnVsZXMgZm9yIHJhZnQtbWV0cmljcywgcmFmdC1jaGFubmVsLW1ldHJpY3MsIGJyb2tlci1tZXRhZGF0YS1tZXRyaWNzCi0gcGF0dGVybjoga2Fma2Euc2VydmVyPHR5cGU9KC4rKT48PihbYS16LV0rKS10b3RhbAogIG5hbWU6IGthZmthX3NlcnZlcl8kMV8kMl90b3RhbAogIHR5cGU6IENPVU5URVIKLSBwYXR0ZXJuOiBrYWZrYS5zZXJ2ZXI8dHlwZT0oLispPjw+KFthLXotXSspCiAgbmFtZToga2Fma2Ffc2VydmVyXyQxXyQyCiAgdHlwZTogR0FVR0UKCiMgR2VuZXJpYyBwZXItc2Vjb25kIGNvdW50ZXJzIHdpdGggMC0yIGtleS92YWx1ZSBwYWlycwotIHBhdHRlcm46IGthZmthLihcdyspPHR5cGU9KC4rKSwgbmFtZT0oLispUGVyU2VjXHcqLCAoLispPSguKyksICguKyk9KC4rKT48PkNvdW50CiAgbmFtZToga2Fma2FfJDFfJDJfJDNfdG90YWwKICB0eXBlOiBDT1VOVEVSCiAgbGFiZWxzOgogICAgIiQ0IjogIiQ1IgogICAgIiQ2IjogIiQ3IgotIHBhdHRlcm46IGthZmthLihcdyspPHR5cGU9KC4rKSwgbmFtZT0oLispUGVyU2VjXHcqLCAoLispPSguKyk+PD5Db3VudAogIG5hbWU6IGthZmthXyQxXyQyXyQzX3RvdGFsCiAgdHlwZTogQ09VTlRFUgogIGxhYmVsczoKICAgICIkNCI6ICIkNSIKLSBwYXR0ZXJuOiBrYWZrYS4oXHcrKTx0eXBlPSguKyksIG5hbWU9KC4rKVBlclNlY1x3Kj48PkNvdW50CiAgbmFtZToga2Fma2FfJDFfJDJfJDNfdG90YWwKICB0eXBlOiBDT1VOVEVSCgojIFF1b3RhIHNwZWNpZmljIHJ1bGVzCi0gcGF0dGVybjoga2Fma2Euc2VydmVyPHR5cGU9KC4rKSwgdXNlcj0oLispLCBjbGllbnQtaWQ9KC4rKT48PihbYS16LV0rKQogIG5hbWU6IGthZmthX3NlcnZlcl9xdW90YV8kNAogIHR5cGU6IEdBVUdFCiAgbGFiZWxzOgogICAgcmVzb3VyY2U6ICIkMSIKICAgIHVzZXI6ICIkMiIKICAgIGNsaWVudElkOiAiJDMiCi0gcGF0dGVybjoga2Fma2Euc2VydmVyPHR5cGU9KC4rKSwgY2xpZW50LWlkPSguKyk+PD4oW2Etei1dKykKICBuYW1lOiBrYWZrYV9zZXJ2ZXJfcXVvdGFfJDMKICB0eXBlOiBHQVVHRQogIGxhYmVsczoKICAgIHJlc291cmNlOiAiJDEiCiAgICBjbGllbnRJZDogIiQyIgotIHBhdHRlcm46IGthZmthLnNlcnZlcjx0eXBlPSguKyksIHVzZXI9KC4rKT48PihbYS16LV0rKQogIG5hbWU6IGthZmthX3NlcnZlcl9xdW90YV8kMwogIHR5cGU6IEdBVUdFCiAgbGFiZWxzOgogICAgcmVzb3VyY2U6ICIkMSIKICAgIHVzZXI6ICIkMiIKCiMgR2VuZXJpYyBnYXVnZXMgd2l0aCAwLTIga2V5L3ZhbHVlIHBhaXJzCi0gcGF0dGVybjoga2Fma2EuKFx3Kyk8dHlwZT0oLispLCBuYW1lPSguKyksICguKyk9KC4rKSwgKC4rKT0oLispPjw+VmFsdWUKICBuYW1lOiBrYWZrYV8kMV8kMl8kMwogIHR5cGU6IEdBVUdFCiAgbGFiZWxzOgogICAgIiQ0IjogIiQ1IgogICAgIiQ2IjogIiQ3IgotIHBhdHRlcm46IGthZmthLihcdyspPHR5cGU9KC4rKSwgbmFtZT0oLispLCAoLispPSguKyk+PD5WYWx1ZQogIG5hbWU6IGthZmthXyQxXyQyXyQzCiAgdHlwZTogR0FVR0UKICBsYWJlbHM6CiAgICAiJDQiOiAiJDUiCi0gcGF0dGVybjoga2Fma2EuKFx3Kyk8dHlwZT0oLispLCBuYW1lPSguKyk+PD5WYWx1ZQogIG5hbWU6IGthZmthXyQxXyQyXyQzCiAgdHlwZTogR0FVR0UKCiMgRW11bGF0ZSBQcm9tZXRoZXVzICdTdW1tYXJ5JyBtZXRyaWNzIGZvciB0aGUgZXhwb3J0ZWQgJ0hpc3RvZ3JhbSdzLgojCiMgTm90ZSB0aGF0IHRoZXNlIGFyZSBtaXNzaW5nIHRoZSAnX3N1bScgbWV0cmljIQotIHBhdHRlcm46IGthZmthLihcdyspPHR5cGU9KC4rKSwgbmFtZT0oLispLCAoLispPSguKyksICguKyk9KC4rKT48PkNvdW50CiAgbmFtZToga2Fma2FfJDFfJDJfJDNfY291bnQKICB0eXBlOiBDT1VOVEVSCiAgbGFiZWxzOgogICAgIiQ0IjogIiQ1IgogICAgIiQ2IjogIiQ3IgotIHBhdHRlcm46IGthZmthLihcdyspPHR5cGU9KC4rKSwgbmFtZT0oLispLCAoLispPSguKiksICguKyk9KC4rKT48PihcZCspdGhQZXJjZW50aWxlCiAgbmFtZToga2Fma2FfJDFfJDJfJDMKICB0eXBlOiBHQVVHRQogIGxhYmVsczoKICAgICIkNCI6ICIkNSIKICAgICIkNiI6ICIkNyIKICAgIHF1YW50aWxlOiAiMC4kOCIKLSBwYXR0ZXJuOiBrYWZrYS4oXHcrKTx0eXBlPSguKyksIG5hbWU9KC4rKSwgKC4rKT0oLispPjw+Q291bnQKICBuYW1lOiBrYWZrYV8kMV8kMl8kM19jb3VudAogIHR5cGU6IENPVU5URVIKICBsYWJlbHM6CiAgICAiJDQiOiAiJDUiCi0gcGF0dGVybjoga2Fma2EuKFx3Kyk8dHlwZT0oLispLCBuYW1lPSguKyksICguKyk9KC4qKT48PihcZCspdGhQZXJjZW50aWxlCiAgbmFtZToga2Fma2FfJDFfJDJfJDMKICB0eXBlOiBHQVVHRQogIGxhYmVsczoKICAgICIkNCI6ICIkNSIKICAgIHF1YW50aWxlOiAiMC4kNiIKLSBwYXR0ZXJuOiBrYWZrYS4oXHcrKTx0eXBlPSguKyksIG5hbWU9KC4rKT48PkNvdW50CiAgbmFtZToga2Fma2FfJDFfJDJfJDNfY291bnQKICB0eXBlOiBDT1VOVEVSCi0gcGF0dGVybjoga2Fma2EuKFx3Kyk8dHlwZT0oLispLCBuYW1lPSguKyk+PD4oXGQrKXRoUGVyY2VudGlsZQogIG5hbWU6IGthZmthXyQxXyQyXyQzCiAgdHlwZTogR0FVR0UKICBsYWJlbHM6CiAgICBxdWFudGlsZTogIjAuJDQiCgojIEdlbmVyaWMgZ2F1Z2VzIGZvciBNZWFuUmF0ZSBQZXJjZW50CiMgRXgpIGthZmthLnNlcnZlcjx0eXBlPUthZmthUmVxdWVzdEhhbmRsZXJQb29sLCBuYW1lPVJlcXVlc3RIYW5kbGVyQXZnSWRsZVBlcmNlbnQ+PD5NZWFuUmF0ZQotIHBhdHRlcm46IGthZmthLihcdyspPHR5cGU9KC4rKSwgbmFtZT0oLispUGVyY2VudFx3Kj48Pk1lYW5SYXRlCiAgbmFtZToga2Fma2FfJDFfJDJfJDNfcGVyY2VudAogIHR5cGU6IEdBVUdFCi0gcGF0dGVybjoga2Fma2EuKFx3Kyk8dHlwZT0oLispLCBuYW1lPSguKylQZXJjZW50XHcqPjw+VmFsdWUKICBuYW1lOiBrYWZrYV8kMV8kMl8kM19wZXJjZW50CiAgdHlwZTogR0FVR0UKLSBwYXR0ZXJuOiBrYWZrYS4oXHcrKTx0eXBlPSguKyksIG5hbWU9KC4rKVBlcmNlbnRcdyosICguKyk9KC4rKT48PlZhbHVlCiAgbmFtZToga2Fma2FfJDFfJDJfJDNfcGVyY2VudAogIHR5cGU6IEdBVUdFCiAgbGFiZWxzOgogICAgIiQ0IjogIiQ1Ig==
EOF

# ------------------------------------------------------------
# Controllers: use ROOT disk for metadata logs
# Brokers: mount secondary EBS disk at $DATA_DIR and use $DATA_DIR/data
# ------------------------------------------------------------
if [[ "$ROLE" == "controller" ]]; then
  KAFKA_LOG_DIR="/var/lib/kafka-metadata"
  mkdir -p "$KAFKA_LOG_DIR"
else
  mkdir -p "$DATA_DIR"

  # ----------------------------
  # Find non-root disk (EBS data)
  # ----------------------------
  ROOT_SRC="$(findmnt -n -o SOURCE /)"
  ROOT_DISK="$(lsblk -no PKNAME "$ROOT_SRC" 2>/dev/null || true)"
  if [[ -z "$ROOT_DISK" ]]; then
    ROOT_DISK="$(basename "$ROOT_SRC" | sed 's/p[0-9]\+$//' || true)"
  fi

  DATA_DISK=""
  while read -r name type; do
    [[ "$type" != "disk" ]] && continue
    [[ "$name" == "$ROOT_DISK" ]] && continue
    DATA_DISK="/dev/$name"
    break
  done < <(lsblk -ndo NAME,TYPE)

  if [[ -z "$DATA_DISK" ]]; then
    echo "ERROR: Could not find non-root data disk for broker. lsblk:"
    lsblk
    exit 1
  fi

  # Format disk if needed
  if ! blkid "$DATA_DISK" >/dev/null 2>&1; then
    mkfs.ext4 -F "$DATA_DISK"
  fi

  # Mount + persist
  UUID="$(blkid -s UUID -o value "$DATA_DISK")"
  grep -q "$UUID" /etc/fstab || echo "UUID=$UUID $DATA_DIR ext4 defaults,nofail 0 2" >> /etc/fstab
  mount -a

  # IMPORTANT: log.dirs must not be filesystem root (ext4 creates lost+found)
  KAFKA_LOG_DIR="$DATA_DIR/data"
  mkdir -p "$KAFKA_LOG_DIR"
fi

# ---------------------------------
# Build controller.quorum.voters (id@host:port)
# and controller.quorum.bootstrap.servers (host:port)
# ---------------------------------
QUORUM=""
CONTROLLER_BOOTSTRAP=""
i=1
while [[ $i -le $NUM_CONTROLLERS ]]; do
  IDX="$(printf "%02d" "$i")"
  HOST="pulse-kafka-controller-$IDX.$ZONE_NAME"

  if [[ -z "$QUORUM" ]]; then
    QUORUM="$i@$HOST:9093"
  else
    QUORUM="$QUORUM,$i@$HOST:9093"
  fi

  if [[ -z "$CONTROLLER_BOOTSTRAP" ]]; then
    CONTROLLER_BOOTSTRAP="$HOST:9093"
  else
    CONTROLLER_BOOTSTRAP="$CONTROLLER_BOOTSTRAP,$HOST:9093"
  fi

  i=$((i+1))
done

# ---------------------------------
# Wait for Route53 private DNS to resolve (avoid early startup race)
# ---------------------------------
for j in $(seq 1 60); do
  ok=1
  i=1
  while [[ $i -le $NUM_CONTROLLERS ]]; do
    IDX="$(printf "%02d" "$i")"
    HOST="pulse-kafka-controller-$IDX.$ZONE_NAME"
    getent hosts "$HOST" >/dev/null 2>&1 || ok=0
    i=$((i+1))
  done
  [[ $ok -eq 1 ]] && break
  sleep 2
done

# ---------------------------------
# Stable advertised FQDN (Route53)
# ---------------------------------
if [[ "$ROLE" == "controller" ]]; then
  IDX="$(printf "%02d" "$NODE_ID")"
  ADVERTISED_FQDN="pulse-kafka-controller-$IDX.$ZONE_NAME"
else
  BROKER_INDEX=$(( NODE_ID - NUM_CONTROLLERS ))
  IDX="$(printf "%02d" "$BROKER_INDEX")"
  ADVERTISED_FQDN="pulse-kafka-broker-$IDX.$ZONE_NAME"
fi

CONFIG_DIR="$KAFKA_DIR/config/kraft"
CONF_FILE="$CONFIG_DIR/server.properties"
mkdir -p "$CONFIG_DIR"

# ---------------------------------
# Auto-adjust replication / ISR (based on number of brokers)
# ---------------------------------
RF="$NUM_BROKERS"
if (( RF > 3 )); then RF=3; fi
MIN_ISR=$(( RF - 1 ))
if (( MIN_ISR < 1 )); then MIN_ISR=1; fi

# ---------------------------------
# Write Kafka KRaft config
# ---------------------------------
if [[ "$ROLE" == "controller" ]]; then
cat > "$CONF_FILE" <<EOF
process.roles=controller
node.id=$NODE_ID

controller.quorum.voters=$QUORUM

listener.security.protocol.map=CONTROLLER:PLAINTEXT
controller.listener.names=CONTROLLER
listeners=CONTROLLER://0.0.0.0:9093

log.dirs=$KAFKA_LOG_DIR
EOF
else
cat > "$CONF_FILE" <<EOF
process.roles=broker
node.id=$NODE_ID

controller.quorum.voters=$QUORUM
controller.quorum.bootstrap.servers=$CONTROLLER_BOOTSTRAP

# Required on broker: name used for controller connections
controller.listener.names=CONTROLLER

# Map includes CONTROLLER even though broker doesn't bind it
listener.security.protocol.map=INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
inter.broker.listener.name=INTERNAL

# Broker binds only INTERNAL for client + inter-broker traffic
listeners=INTERNAL://0.0.0.0:9092
advertised.listeners=INTERNAL://$ADVERTISED_FQDN:9092

log.dirs=$KAFKA_LOG_DIR

default.replication.factor=$RF
min.insync.replicas=$MIN_ISR
offsets.topic.replication.factor=$RF
transaction.state.log.replication.factor=$RF
transaction.state.log.min.isr=$MIN_ISR
EOF
fi

# ---------------------------------
# Format storage only once (based on the log dir we actually use)
# ---------------------------------
FORMAT_MARKER="$KAFKA_LOG_DIR/.kraft_formatted"
if [[ ! -f "$FORMAT_MARKER" ]]; then
  "$KAFKA_DIR/bin/kafka-storage.sh" format -t "$KRAFT_CLUSTER_ID" -c "$CONF_FILE"
  touch "$FORMAT_MARKER"
fi

# ---------------------------------
# systemd service
# ---------------------------------
cat > /etc/systemd/system/kafka.service <<SERVICE
[Unit]
Description=Apache Kafka (KRaft) - $ROLE
After=network.target

[Service]
Type=simple
WorkingDirectory=$KAFKA_DIR
Environment="KAFKA_OPTS=-javaagent:/opt/prometheus/jmx_prometheus_javaagent-1.0.1.jar=1234:/opt/prometheus/kafka_broker.yml"
ExecStart=$KAFKA_DIR/bin/kafka-server-start.sh $CONF_FILE
ExecStop=$KAFKA_DIR/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=5
LimitNOFILE=100000

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl enable kafka
systemctl restart kafka
