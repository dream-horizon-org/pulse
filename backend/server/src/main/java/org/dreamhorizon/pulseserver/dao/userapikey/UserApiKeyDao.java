package org.dreamhorizon.pulseserver.dao.userapikey;

import static org.dreamhorizon.pulseserver.dao.userapikey.UserApiKeyQueries.FIND_ACTIVE_BY_HASH;
import static org.dreamhorizon.pulseserver.dao.userapikey.UserApiKeyQueries.FIND_ACTIVE_BY_USER;
import static org.dreamhorizon.pulseserver.dao.userapikey.UserApiKeyQueries.INSERT;
import static org.dreamhorizon.pulseserver.dao.userapikey.UserApiKeyQueries.REVOKE;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.userapikey.models.UserApiKey;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserApiKeyDao {

  private final MysqlClient mysqlClient;

  public Single<UserApiKey> createApiKey(String userId, String displayName, String hash, String prefix) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT)
        .rxExecute(Tuple.of(userId, displayName, hash, prefix))
        .map(result -> {
          long id = Long.parseLong(result.property(MySQLClient.LAST_INSERTED_ID).toString());
          log.info("Created user API key {} for user: {}", id, userId);
          return UserApiKey.builder()
              .id(id)
              .userId(userId)
              .displayName(displayName)
              .apiKeyHash(hash)
              .keyPrefix(prefix)
              .isActive(true)
              .build();
        })
        .doOnError(e -> log.error("Failed to create user API key for user: {}", userId, e));
  }

  public Maybe<UserApiKey> findActiveByHash(String hash) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(FIND_ACTIVE_BY_HASH)
        .rxExecute(Tuple.of(hash))
        .flatMapMaybe(rows -> {
          if (rows.size() == 0) return Maybe.empty();
          return Maybe.just(mapRow(rows.iterator().next()));
        })
        .doOnError(e -> log.error("Failed to find user API key by hash", e));
  }

  public Single<List<UserApiKey>> findActiveByUser(String userId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(FIND_ACTIVE_BY_USER)
        .rxExecute(Tuple.of(userId))
        .toFlowable()
        .flatMap(rows -> Flowable.fromIterable(rows).map(row -> mapRow((Row) row)))
        .toList()
        .doOnError(e -> log.error("Failed to list user API keys for user: {}", userId, e));
  }

  public Completable revoke(Long id, String userId, String revokedBy) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(REVOKE)
        .rxExecute(Tuple.of(revokedBy, id, userId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            return Completable.error(new RuntimeException("API key not found: " + id));
          }
          log.info("Revoked user API key {} for user: {}", id, userId);
          return Completable.complete();
        })
        .doOnError(e -> log.error("Failed to revoke user API key {} for user: {}", id, userId, e));
  }

  private UserApiKey mapRow(Row row) {
    LocalDateTime revokedAt = row.getLocalDateTime("revoked_at");
    LocalDateTime createdAt = row.getLocalDateTime("created_at");
    return UserApiKey.builder()
        .id(row.getLong("id"))
        .userId(row.getString("user_id"))
        .displayName(row.getString("display_name"))
        .apiKeyHash(row.getString("api_key_hash"))
        .keyPrefix(row.getString("key_prefix"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(createdAt != null ? createdAt.toInstant(ZoneOffset.UTC) : null)
        .revokedAt(revokedAt != null ? revokedAt.toInstant(ZoneOffset.UTC) : null)
        .revokedBy(row.getString("revoked_by"))
        .build();
  }
}
