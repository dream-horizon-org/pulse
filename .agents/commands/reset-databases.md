Reset all databases to a clean state. This is destructive -- confirm before proceeding.

1. Ask for explicit confirmation that the user wants to wipe all data
2. Change to `deploy/`
3. Run `./scripts/reset-databases.sh`
4. Wait for MySQL and ClickHouse to reinitialize
5. Read credentials from `deploy/.env` for verification commands
6. Verify tables exist:
   - MySQL: `docker exec pulse-mysql mysql -u<MYSQL_USER from .env> -p<MYSQL_PASSWORD from .env> pulse_db -e "SHOW TABLES;"`
   - ClickHouse: `docker exec pulse-clickhouse clickhouse-client -u <OTEL_CLICKHOUSE_USER from .env> --password <OTEL_CLICKHOUSE_PASSWORD from .env> --query "SHOW TABLES FROM otel"`
7. Report the result
