#!/bin/bash
set -euxo pipefail

CLUSTER_NAME="${cluster_name}"
PRIVATE_ZONE="${private_zone}"
KEEPER_COUNT="${keeper_count}"
KEEPER_ID="${keeper_id}"          # 1..N
KEEPER_PORT="${keeper_port}"
RAFT_PORT="${raft_port}"


systemctl stop clickhouse-keeper

rm -rf /var/lib/clickhouse/coordination/state
rm -rf /var/lib/clickhouse/coordination

mkdir -p /var/lib/clickhouse/coordination/log
mkdir -p /var/lib/clickhouse/coordination/snapshots
chown -R clickhouse:clickhouse /var/lib/clickhouse/coordination

mkdir -p /var/lib/clickhouse-keeper
mkdir -p /var/log/clickhouse-keeper
mkdir -p /etc/clickhouse-keeper

chown -R clickhouse:clickhouse /var/lib/clickhouse-keeper /var/lib/clickhouse /var/log/clickhouse-keeper /etc/clickhouse-keeper
chmod 0755 /var/lib/clickhouse-keeper /var/log/clickhouse-keeper

cat > /etc/clickhouse-keeper/keeper_config.xml <<EOF
<clickhouse>
  <logger>
    <level>information</level>
    <console>1</console>
    <log>/var/log/clickhouse-keeper/clickhouse-keeper.log</log>
    <errorlog>/var/log/clickhouse-keeper/clickhouse-keeper.err.log</errorlog>
  </logger>

  <path>/var/lib/clickhouse-keeper</path>
  <listen_host>0.0.0.0</listen_host>

  <keeper_server>
    <tcp_port>$KEEPER_PORT</tcp_port>
    <server_id>$KEEPER_ID</server_id>

    <log_storage_path>/var/lib/clickhouse/coordination/log</log_storage_path>
    <snapshot_storage_path>/var/lib/clickhouse/coordination/snapshots</snapshot_storage_path>

    <raft_configuration>
$(for k in $(seq 1 "$KEEPER_COUNT"); do
cat <<NODE
      <server>
        <id>$${k}</id>
        <hostname>keeper-$${k}.$${CLUSTER_NAME}.$${PRIVATE_ZONE}</hostname>
        <port>$${RAFT_PORT}</port>
      </server>
NODE
done)
    </raft_configuration>
  </keeper_server>
</clickhouse>
EOF

systemctl start clickhouse-keeper
