# Clear Database Command

This custom Cursor command resets all databases (MySQL and ClickHouse) by removing volumes and reinitializing them with fresh data.

## Usage

Open the Command Palette (Cmd+Shift+P) and search for "Clear Database".

## Available Commands

### 1. Clear Database
Interactive reset with confirmation prompt. This is the safest option.

**Command:**
```bash
cd deploy && ./scripts/reset-databases.sh
```

### 2. Clear Database (Force)
Quick reset without confirmation prompt. Useful for automation.

**Command:**
```bash
cd deploy && docker-compose down && docker volume rm deploy_mysql-data deploy_clickhouse-data 2>/dev/null || true && docker-compose up -d && echo '✅ Databases cleared and restarted'
```

### 3. View Database Status
Check what tables exist in both MySQL and ClickHouse databases.

**Command:**
```bash
echo '=== MySQL Tables ===' && docker exec pulse-mysql mysql -uroot -ppulse_root_password pulse_db -e 'SHOW TABLES;' 2>/dev/null && echo '' && echo '=== ClickHouse Tables ===' && docker exec pulse-clickhouse clickhouse-client --query 'SHOW TABLES FROM otel' 2>/dev/null
```

### 4. Clear MySQL Only
Reset only the MySQL database without affecting ClickHouse.

**Command:**
```bash
docker-compose -f deploy/docker-compose.yml stop mysql && docker volume rm deploy_mysql-data 2>/dev/null || true && docker-compose -f deploy/docker-compose.yml up -d mysql && echo '✅ MySQL database cleared'
```

### 5. Clear ClickHouse Only
Reset only the ClickHouse database without affecting MySQL.

**Command:**
```bash
docker-compose -f deploy/docker-compose.yml stop clickhouse && docker volume rm deploy_clickhouse-data 2>/dev/null || true && docker-compose -f deploy/docker-compose.yml up -d clickhouse clickhouse-init && echo '✅ ClickHouse database cleared'
```

## What Gets Cleared

### MySQL Database
- All user data
- All project data
- All organization data
- All authentication/session data

### ClickHouse Database
- All OTEL traces
- All OTEL logs
- All OTEL metrics
- All custom events

## Notes

⚠️ **Warning:** These commands will permanently delete all data in the specified databases. Make sure you have backups if needed.

✅ After clearing, the databases will be automatically reinitialized using the init scripts located at:
- MySQL: `deploy/db/mysql-init.sql`
- ClickHouse: `backend/ingestion/clickhouse-otel-schema-PROJECT_BASED.sql`

⏳ After running a clear command, wait 10-30 seconds for the databases to fully initialize before using the application.
