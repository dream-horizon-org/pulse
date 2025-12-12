package org.dreamhorizon.pulsealertscron.client.mysql;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlClientImpl implements MysqlClient {

    private final Vertx vertx;
    private io.vertx.rxjava3.mysqlclient.MySQLPool mysqlPool;

    public MysqlClientImpl(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Completable rxConnect() {
        return Completable.fromAction(() -> {
            // Load MySQL configuration from environment variables (Docker-friendly)
            String host = getEnvOrDefault("MYSQL_WRITER_HOST", "localhost");
            int port = Integer.parseInt(getEnvOrDefault("MYSQL_PORT", "3306"));
            String database = getEnvOrDefault("MYSQL_DATABASE", "pulse_db");
            String user = getEnvOrDefault("MYSQL_USER", "pulse_user");
            String password = getEnvOrDefault("MYSQL_PASSWORD", "pulse_password");

            MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                    .setHost(host)
                    .setPort(port)
                    .setDatabase(database)
                    .setUser(user)
                    .setPassword(password);

            PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(10);

            MySQLPool pool = MySQLPool.pool(vertx, connectOptions, poolOptions);
            this.mysqlPool = io.vertx.rxjava3.mysqlclient.MySQLPool.newInstance(pool);
            
            log.info("MySQL client connected to {}:{}/{}", host, port, database);
        });
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    @Override
    public io.vertx.rxjava3.mysqlclient.MySQLPool getMasterMysqlClient() {
        if (mysqlPool == null) {
            throw new IllegalStateException("MySQL client not connected");
        }
        return mysqlPool;
    }

    @Override
    public void close() {
        if (mysqlPool != null) {
            mysqlPool.close();
            log.info("MySQL client closed");
        }
    }
}

