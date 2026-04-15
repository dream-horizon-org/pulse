#!/bin/bash
set -euo pipefail

exec > >(tee /var/log/user-data.log) 2>&1
echo "=== Kafka user-data start: $(date) ==="

# ---------------------------------------------------------------
# Variables injected by Terraform templatefile
# ---------------------------------------------------------------
NODE_ID="${node_id}"
NUM_BROKERS="${num_brokers}"
KRAFT_CLUSTER_ID="${kraft_cluster_id}"
KAFKA_VERSION="${kafka_version}"
DATA_DIR="${kafka_data_dir}"
ZONE_NAME="${route53_zone_name}"
REPLICATION_FACTOR="${replication_factor}"
MIN_ISR="${min_insync_replicas}"
RETENTION_MS="${retention_ms}"
COMPRESSION_TYPE="${compression_type}"

KAFKA_DIR="/opt/kafka"

# ---------------------------------------------------------------
# 1. Mount dedicated EBS data disk
#    AWS NVMe: root is nvme0n1, data disk is nvme1n1 (or similar).
#    We find it by excluding the root device.
# ---------------------------------------------------------------
echo "--- Mounting data disk ---"
mkdir -p "$DATA_DIR"

ROOT_SRC="$(findmnt -n -o SOURCE /)"
ROOT_DISK="$(lsblk -no PKNAME "$ROOT_SRC" 2>/dev/null || basename "$ROOT_SRC" | sed 's/p[0-9]*$//')"

DATA_DISK=""
while read -r name type; do
  [[ "$type" != "disk" ]] && continue
  [[ "$name" == "$ROOT_DISK" ]] && continue
  DATA_DISK="/dev/$name"
  break
done < <(lsblk -ndo NAME,TYPE)

if [[ -z "$DATA_DISK" ]]; then
  echo "ERROR: no secondary data disk found. lsblk output:"
  lsblk
  exit 1
fi

if ! blkid "$DATA_DISK" >/dev/null 2>&1; then
  echo "Formatting $DATA_DISK as ext4..."
  mkfs.ext4 -F "$DATA_DISK"
fi

UUID="$(blkid -s UUID -o value "$DATA_DISK")"
grep -q "$UUID" /etc/fstab || echo "UUID=$UUID $DATA_DIR ext4 defaults,nofail 0 2" >> /etc/fstab
mount -a

# Kafka log.dirs must not be the filesystem root (ext4 creates lost+found there)
KAFKA_LOG_DIR="$DATA_DIR/data"
mkdir -p "$KAFKA_LOG_DIR"

# ---------------------------------------------------------------
# 4. Build controller.quorum.voters
#    Each node is both broker and controller (combined mode).
#    Node IDs: 1..NUM_BROKERS  →  DNS: pulse-kafka-01.ZONE, pulse-kafka-02.ZONE
# ---------------------------------------------------------------
QUORUM=""
i=1
while [[ $i -le $NUM_BROKERS ]]; do
  IDX="$(printf "%02d" "$i")"
  HOST="pulse-kafka-$IDX.$ZONE_NAME"
  if [[ -z "$QUORUM" ]]; then
    QUORUM="$i@$HOST:9093"
  else
    QUORUM="$QUORUM,$i@$HOST:9093"
  fi
  i=$((i+1))
done

# ---------------------------------------------------------------
# 5. Wait for Route53 DNS to resolve for all nodes
#    (Terraform creates records, but propagation takes a few seconds)
# ---------------------------------------------------------------
echo "--- Waiting for DNS resolution ---"
for attempt in $(seq 1 60); do
  all_ok=1
  i=1
  while [[ $i -le $NUM_BROKERS ]]; do
    IDX="$(printf "%02d" "$i")"
    HOST="pulse-kafka-$IDX.$ZONE_NAME"
    getent hosts "$HOST" >/dev/null 2>&1 || all_ok=0
    i=$((i+1))
  done
  if [[ $all_ok -eq 1 ]]; then
    echo "DNS resolved for all $NUM_BROKERS brokers."
    break
  fi
  echo "Attempt $attempt: DNS not ready, retrying in 5s..."
  sleep 5
done

# ---------------------------------------------------------------
# 6. Write KRaft config
#    Combined mode: process.roles = broker,controller
# ---------------------------------------------------------------
IDX="$(printf "%02d" "$NODE_ID")"
FQDN="pulse-kafka-$IDX.$ZONE_NAME"
CONFIG_DIR="$KAFKA_DIR/config/kraft"
CONF_FILE="$CONFIG_DIR/server.properties"
mkdir -p "$CONFIG_DIR"

cat > "$CONF_FILE" <<KAFKACONF
# KRaft combined mode — this node is both broker and controller
process.roles=broker,controller
node.id=$NODE_ID

# Cluster membership
controller.quorum.voters=$QUORUM

# Listeners
listener.security.protocol.map=INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
controller.listener.names=CONTROLLER
inter.broker.listener.name=INTERNAL

listeners=INTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
advertised.listeners=INTERNAL://$FQDN:9092

# Storage
log.dirs=$KAFKA_LOG_DIR

# Replication defaults
default.replication.factor=$REPLICATION_FACTOR
min.insync.replicas=$MIN_ISR
offsets.topic.replication.factor=$REPLICATION_FACTOR
transaction.state.log.replication.factor=$REPLICATION_FACTOR
transaction.state.log.min.isr=$MIN_ISR

# Retention
log.retention.ms=$RETENTION_MS
log.retention.check.interval.ms=300000
log.segment.bytes=1073741824

# Producer defaults
compression.type=$COMPRESSION_TYPE

# Performance (tuned for m7i.large: 2 vCPU, 8 GB RAM)
num.network.threads=3
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
KAFKACONF

# ---------------------------------------------------------------
# 7. Format KRaft storage (runs only once — marker file guards it)
# ---------------------------------------------------------------
FORMAT_MARKER="$KAFKA_LOG_DIR/.kraft_formatted"
if [[ ! -f "$FORMAT_MARKER" ]]; then
  echo "--- Formatting KRaft storage ---"
  # Kafka requires a base64-encoded UUID, not the standard hyphenated form.
  # Convert: strip hyphens → raw bytes → URL-safe base64 without padding.
  KAFKA_CLUSTER_ID=$(python3 -c "
import uuid, base64
print(base64.urlsafe_b64encode(uuid.UUID('$KRAFT_CLUSTER_ID').bytes).rstrip(b'=').decode())
")
  "$KAFKA_DIR/bin/kafka-storage.sh" format -t "$KAFKA_CLUSTER_ID" -c "$CONF_FILE"
  touch "$FORMAT_MARKER"
else
  echo "KRaft storage already formatted, skipping."
fi

# ---------------------------------------------------------------
# 8. Systemd service
# ---------------------------------------------------------------
cat > /etc/systemd/system/kafka.service <<SERVICE
[Unit]
Description=Apache Kafka $KAFKA_VERSION (KRaft combined) — node $NODE_ID
After=network.target

[Service]
Type=simple
WorkingDirectory=$KAFKA_DIR
Environment="KAFKA_HEAP_OPTS=-Xmx4g -Xms4g"
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
systemctl start kafka

# ---------------------------------------------------------------
# 9. Create topics — runs only on node 1, after cluster is ready
#    Uses --if-not-exists so it is safe to re-run on replacement nodes.
# ---------------------------------------------------------------
if [[ "$NODE_ID" == "1" ]]; then
  echo "--- Waiting for Kafka to accept connections ---"
  for attempt in $(seq 1 60); do
    if "$KAFKA_DIR/bin/kafka-topics.sh" --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
      echo "Kafka is ready."
      break
    fi
    echo "Attempt $attempt: not ready yet, retrying in 5s..."
    sleep 5
  done

  # Give the second broker time to join the cluster before we create
  # topics with replication-factor=2
  echo "Waiting 30s for all brokers to join the cluster..."
  sleep 30

  echo "--- Creating topics ---"
  %{ for topic in kafka_topics ~}
  "$KAFKA_DIR/bin/kafka-topics.sh" \
    --bootstrap-server localhost:9092 \
    --create --if-not-exists \
    --topic "${topic.name}" \
    --partitions ${topic.partitions} \
    --replication-factor "$REPLICATION_FACTOR" \
    --config retention.ms="$RETENTION_MS" \
    --config compression.type="$COMPRESSION_TYPE" \
    --config min.insync.replicas="$MIN_ISR"
  echo "Topic created: ${topic.name} (partitions=${topic.partitions})"
  %{ endfor ~}

  echo "--- Listing all topics ---"
  "$KAFKA_DIR/bin/kafka-topics.sh" --bootstrap-server localhost:9092 --list
fi

echo "=== Kafka user-data complete: $(date) ==="
