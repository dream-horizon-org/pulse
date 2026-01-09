#!/bin/bash
set -euxo pipefail

REPLICA_INDEX="${replica_index}"
REPLICA_COUNT="${replica_count}"
KEEPER_COUNT="${keeper_count}"
KEEPER_PORT="${keeper_port}"

USER_NAME="${user_name}"
PASSWORD="${password}"

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

# 2) Macros
cat > "$CONFIG_DIR/config.d/macros.xml" <<EOF
<yandex>
    <macros>
        <cluster>$CLUSTER_NAME</cluster>
        <shard>$SHARD_INDEX</shard>
        <replica>$REPLICA_INDEX</replica>
    </macros>
</yandex>
EOF

THIS_HOST="shard-$SHARD_INDEX-replica-$REPLICA_INDEX.$CLUSTER_NAME.$PRIVATE_ZONE"

cat > "$CONFIG_DIR/config.d/interserver_http.xml" <<EOF
<yandex>
    <interserver_http_host>$THIS_HOST</interserver_http_host>
    <interserver_http_port>9009</interserver_http_port>
</yandex>
EOF

cat > "$CONFIG_DIR/config.d/zookeeper.xml" <<EOF
<yandex>
    <zookeeper>
$(for k in $(seq 1 "$KEEPER_COUNT"); do
cat <<NODE
        <node>
            <host>keeper-$k.$CLUSTER_NAME.$PRIVATE_ZONE</host>
            <port>$KEEPER_PORT</port>
        </node>
NODE
done)
    </zookeeper>
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
$(for s in $(seq 1 "$SHARD_COUNT"); do
cat <<SHARD
            <shard>
$(for r in $(seq 1 "$REPLICA_COUNT"); do
cat <<REPL
                <replica>
                    <host>shard-$s-replica-$r.$CLUSTER_NAME.$PRIVATE_ZONE</host>
                    <port>9000</port>
                    <user>$USER_NAME</user>
                    <password>$PASSWORD</password>
                </replica>
REPL
done)
            </shard>
SHARD
done)
        </$CLUSTER_NAME>
    </remote_servers>
</yandex>
EOF

mkdir -p /etc/clickhouse-server/users.d
cat > /etc/clickhouse-server/users.d/10-$USER_NAME.xml <<EOF
<clickhouse>
  <users>
    <$USER_NAME>
      <password>$PASSWORD</password>
      <profile>default</profile>
      <quota>default</quota>
      <networks>
        <ip>::/0</ip>
      </networks>
      <access_management>1</access_management>
    </$USER_NAME>
  </users>
</clickhouse>
EOF


# Restart ClickHouse to pick up new config
if systemctl is-enabled clickhouse-server >/dev/null 2>&1; then
  systemctl restart clickhouse-server
fi
