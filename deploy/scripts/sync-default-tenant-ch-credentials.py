#!/usr/bin/env python3
"""
One-time script to sync the default tenant's ClickHouse credentials in MySQL
with the current OTEL_CLICKHOUSE_* and VAULT_ENCRYPTION_MASTER_KEY from .env.

Use this after deploy when the password in MySQL (from mysql-init.sql) does not
match the ClickHouse container's password, which causes "Authentication failed"
when the backend runs tenant queries (e.g. AI Root Cause Analysis).

Encryption matches the backend's PasswordEncryptionUtil (AES/GCM, 12-byte IV,
128-bit tag, salt + digest for verification).

Usage:
  # From repo root, with deploy/.env and MySQL reachable:
  cd deploy && python3 scripts/sync-default-tenant-ch-credentials.py

  # Or with env vars set (e.g. by deploy stack):
  export $(grep -v '^#' deploy/.env | xargs)
  python3 deploy/scripts/sync-default-tenant-ch-credentials.py

  # Using Docker (no local Python deps):
  docker run --rm --network pulse_deploy_pulse-network \
    -e MYSQL_HOST=mysql -e MYSQL_PORT=3306 \
    -e MYSQL_USER=pulse_user -e MYSQL_PASSWORD=pulse_password -e MYSQL_DB=pulse_db \
    -e VAULT_ENCRYPTION_MASTER_KEY="..." -e OTEL_CLICKHOUSE_PASSWORD="..." \
    -e OTEL_CLICKHOUSE_USER=pulse_user \
    -v $(pwd)/deploy/scripts:/scripts:ro python:3.12-slim \
    bash -c "pip install -q pymysql cryptography && python3 /scripts/sync-default-tenant-ch-credentials.py"
"""

from __future__ import annotations

import base64
import hashlib
import os
import sys

# Optional: load deploy/.env when run from repo root or deploy/
def _load_dotenv(paths=None):
    if paths is None:
        paths = [
            os.path.join(os.path.dirname(__file__), "..", ".env"),
            os.path.join(os.getcwd(), ".env"),
            os.path.join(os.getcwd(), "deploy", ".env"),
        ]
    for p in paths:
        if os.path.isfile(p):
            with open(p, "r") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    if "=" in line:
                        k, v = line.split("=", 1)
                        k, v = k.strip(), v.strip()
                        if k and k not in os.environ:
                            # Remove surrounding quotes if present
                            if len(v) >= 2 and v[0] == v[-1] and v[0] in '"\'':
                                v = v[1:-1]
                            os.environ.setdefault(k, v)
            return
    return


def _encrypt_password(plain_password: str, master_key_b64: str) -> tuple[str, str, str]:
    """Produce (encrypted_base64, salt_base64, digest_base64) matching Java PasswordEncryptionUtil."""
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError:
        print("Missing dependency: pip install cryptography", file=sys.stderr)
        sys.exit(1)

    key = base64.b64decode(master_key_b64)
    if len(key) not in (16, 24, 32):
        raise ValueError("VAULT_ENCRYPTION_MASTER_KEY must decode to 16/24/32 bytes for AES")

    iv = os.urandom(12)
    salt = os.urandom(16)
    salt_b64 = base64.b64encode(salt).decode("ascii")

    aes = AESGCM(key)
    plain_bytes = plain_password.encode("utf-8")
    ciphertext_with_tag = aes.encrypt(iv, plain_bytes, None)

    encrypted_b64 = base64.b64encode(iv + ciphertext_with_tag).decode("ascii")
    digest_input = plain_password + salt_b64
    digest_b64 = base64.b64encode(hashlib.sha256(digest_input.encode("utf-8")).digest()).decode("ascii")

    return encrypted_b64, salt_b64, digest_b64


def _update_mysql(host: str, port: int, user: str, password: str, db: str,
                  tenant_id: str, ch_username: str, encrypted: str, salt: str, digest: str) -> None:
    try:
        import pymysql
    except ImportError:
        print("Missing dependency: pip install pymysql", file=sys.stderr)
        sys.exit(1)

    sql = (
        "UPDATE clickhouse_tenant_credentials SET "
        "clickhouse_username = %s, "
        "clickhouse_password_encrypted = %s, "
        "encryption_salt = %s, "
        "password_digest = %s, "
        "updated_at = CURRENT_TIMESTAMP "
        "WHERE tenant_id = %s"
    )
    conn = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=db,
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with conn.cursor() as cur:
            n = cur.execute(sql, (ch_username, encrypted, salt, digest, tenant_id))
        conn.commit()
        if n == 0:
            print("No row updated: no clickhouse_tenant_credentials row for tenant_id =", repr(tenant_id), file=sys.stderr)
            sys.exit(1)
        print("Updated ClickHouse credentials for tenant_id =", repr(tenant_id))
    finally:
        conn.close()


def main() -> None:
    _load_dotenv()

    master_key = os.environ.get("VAULT_ENCRYPTION_MASTER_KEY")
    ch_password = os.environ.get("OTEL_CLICKHOUSE_PASSWORD")
    ch_username = os.environ.get("OTEL_CLICKHOUSE_USER", "pulse_user")

    if not master_key or not ch_password:
        print(
            "Set VAULT_ENCRYPTION_MASTER_KEY and OTEL_CLICKHOUSE_PASSWORD (e.g. from deploy/.env).",
            file=sys.stderr,
        )
        sys.exit(1)

    encrypted, salt, digest = _encrypt_password(ch_password, master_key)

    mysql_host = os.environ.get("MYSQL_HOST", "127.0.0.1")
    mysql_port = int(os.environ.get("MYSQL_PORT", "3307"))
    mysql_user = os.environ.get("MYSQL_USER", "pulse_user")
    mysql_password = os.environ.get("MYSQL_PASSWORD", "pulse_password")
    mysql_db = os.environ.get("MYSQL_DATABASE", os.environ.get("MYSQL_DB", "pulse_db"))
    tenant_id = os.environ.get("TENANT_ID", "default")

    _update_mysql(
        mysql_host, mysql_port, mysql_user, mysql_password, mysql_db,
        tenant_id, ch_username, encrypted, salt, digest,
    )


if __name__ == "__main__":
    main()
