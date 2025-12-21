#!/bin/bash
set -euxo pipefail

DEVICE="${data_device}"
MOUNT="${data_mount_path}"
CLUSTER_NAME="${cluster_name}"
SHARD_INDEX="${shard_index}"
SHARD_COUNT="${shard_count}"
PRIVATE_ZONE="${private_zone}"

# Wait a bit for the EBS volume to appear
sleep 10

# Create filesystem if needed
if ! blkid "$DEVICE" >/dev/null 2>&1; then
  mkfs.ext4 "$DEVICE"
fi

mkdir -p "$MOUNT"

# Add to fstab if not already present
if ! grep -q "$DEVICE" /etc/fstab; then
  echo "$DEVICE $MOUNT ext4 defaults,nofail 0 2" >> /etc/fstab
fi

mount -a

chown -R clickhouse:clickhouse "$MOUNT" || true

# -------------------------------
# ClickHouse configuration
# Assumes config dir: /etc/clickhouse-server
# -------------------------------
CONFIG_DIR="/etc/clickhouse-server"
mkdir -p "$CONFIG_DIR/config.d"

cat > "$CONFIG_DIR/config.d/listen_host.xml" <<EOF
<yandex>
    <listen_host>0.0.0.0</listen_host>
</yandex>
EOF

# 1) Storage path
cat > "$CONFIG_DIR/config.d/storage.xml" <<EOF
<yandex>
    <path>$MOUNT/</path>
</yandex>
EOF

# 2) Macros (no replica macro, since we don't use replication yet)
cat > "$CONFIG_DIR/config.d/macros.xml" <<EOF
<yandex>
    <macros>
        <cluster>$CLUSTER_NAME</cluster>
        <shard>$SHARD_INDEX</shard>
    </macros>
</yandex>
EOF

# 3) Cluster definition
#    We assume hostnames:
#      shard-1.$CLUSTER_NAME.$PRIVATE_ZONE
#      shard-2.$CLUSTER_NAME.$PRIVATE_ZONE
#      ...
#    which Route53 is configured to point at each instance's private IP.
cat > "$CONFIG_DIR/config.d/cluster.xml" <<EOF
<yandex>
    <remote_servers>
        <$CLUSTER_NAME>
$(for i in $(seq 1 "$SHARD_COUNT"); do
cat <<SHARD
            <shard>
                <replica>
                    <host>shard-$i.$CLUSTER_NAME.$PRIVATE_ZONE</host>
                    <port>9000</port>
                    <user>default</user>
                    <password>default</password>
                </replica>
            </shard>
SHARD
done)
        </$CLUSTER_NAME>
    </remote_servers>
</yandex>
EOF

# Restart ClickHouse to pick up new config
if systemctl is-enabled clickhouse-server >/dev/null 2>&1; then
  systemctl restart clickhouse-server
fi
