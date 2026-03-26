package org.dreamhorizon.pulseserver.dao.tier;

import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.ACTIVATE_TIER;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.CHECK_TIER_EXISTS;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.CHECK_TIER_NAME_EXISTS;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.DEACTIVATE_TIER;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.DELETE_TIER;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.GET_ALL_ACTIVE_TIERS;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.GET_ALL_TIERS;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.GET_TIER_BY_ID;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.GET_TIER_BY_NAME;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.INSERT_TIER;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.UPDATE_TIER;
import static org.dreamhorizon.pulseserver.dao.tier.TierQueries.UPDATE_TIER_DEFAULTS;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TierDao {
  private final MysqlClient mysqlClient;

  public Single<Tier> createTier(Tier tier) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_TIER)
        .rxExecute(
            Tuple.of(
                tier.getName(),
                tier.getDisplayName(),
                tier.getIsCustomLimitsAllowed(),
                tier.getUsageLimitDefaults(),
                tier.getIsActive() != null ? tier.getIsActive() : true))
        .map(result -> {
          long lastInsertId = Long.parseLong(result.property(io.vertx.rxjava3.mysqlclient.MySQLClient.LAST_INSERTED_ID).toString());
          log.info("Created tier: {} with ID: {}", tier.getName(), lastInsertId);
          return Tier.builder()
              .tierId((int) lastInsertId)
              .name(tier.getName())
              .displayName(tier.getDisplayName())
              .isCustomLimitsAllowed(tier.getIsCustomLimitsAllowed())
              .usageLimitDefaults(tier.getUsageLimitDefaults())
              .isActive(tier.getIsActive() != null ? tier.getIsActive() : true)
              .createdAt(Instant.now())
              .build();
        })
        .doOnError(error -> log.error("Failed to create tier: {}", tier.getName(), error));
  }

  public Maybe<Tier> getTierById(Integer tierId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_TIER_BY_ID)
        .rxExecute(Tuple.of(tierId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToTier(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch tier by ID: {}", tierId, error));
  }

  public Maybe<Tier> getTierByName(String name) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_TIER_BY_NAME)
        .rxExecute(Tuple.of(name))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToTier(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch tier by name: {}", name, error));
  }

  public Flowable<Tier> getAllActiveTiers() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_ACTIVE_TIERS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToTier((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all active tiers", error));
  }

  public Flowable<Tier> getAllTiers() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_TIERS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToTier((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all tiers", error));
  }

  public Single<Tier> updateTier(Tier tier) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_TIER)
        .rxExecute(
            Tuple.of(
                tier.getName(),
                tier.getDisplayName(),
                tier.getIsCustomLimitsAllowed(),
                tier.getUsageLimitDefaults(),
                tier.getTierId()))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tier not found: " + tier.getTierId()));
          }
          log.info("Updated tier: {}", tier.getTierId());
          // Return the updated tier (we know the values since we just set them)
          return Single.just(tier);
        })
        .doOnError(error -> log.error("Failed to update tier: {}", tier.getTierId(), error));
  }

  public Single<Tier> updateTierDefaults(Integer tierId, String usageLimitDefaults) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_TIER_DEFAULTS)
        .rxExecute(Tuple.of(usageLimitDefaults, tierId))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tier not found: " + tierId));
          }
          log.info("Updated tier defaults for tier: {}", tierId);
          // Fetch the updated tier to return complete entity
          return getTierById(tierId)
              .switchIfEmpty(Single.error(new RuntimeException("Tier not found after update: " + tierId)));
        })
        .doOnError(error -> log.error("Failed to update tier defaults: {}", tierId, error));
  }

  public Single<Tier> deactivateTier(Integer tierId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_TIER)
        .rxExecute(Tuple.of(tierId))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tier not found: " + tierId));
          }
          log.info("Deactivated tier: {}", tierId);
          return getTierById(tierId)
              .switchIfEmpty(Single.error(new RuntimeException("Tier not found after deactivation: " + tierId)));
        })
        .doOnError(error -> log.error("Failed to deactivate tier: {}", tierId, error));
  }

  public Single<Tier> activateTier(Integer tierId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(ACTIVATE_TIER)
        .rxExecute(Tuple.of(tierId))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tier not found: " + tierId));
          }
          log.info("Activated tier: {}", tierId);
          return getTierById(tierId)
              .switchIfEmpty(Single.error(new RuntimeException("Tier not found after activation: " + tierId)));
        })
        .doOnError(error -> log.error("Failed to activate tier: {}", tierId, error));
  }

  public Completable deleteTier(Integer tierId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DELETE_TIER)
        .rxExecute(Tuple.of(tierId))
        .ignoreElement()
        .doOnComplete(() -> log.info("Deleted tier: {}", tierId))
        .doOnError(error -> log.error("Failed to delete tier: {}", tierId, error));
  }

  public Single<Boolean> tierExists(Integer tierId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_TIER_EXISTS)
        .rxExecute(Tuple.of(tierId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check tier existence: {}", tierId, error));
  }

  public Single<Boolean> tierNameExists(String name) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_TIER_NAME_EXISTS)
        .rxExecute(Tuple.of(name))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check tier name existence: {}", name, error));
  }

  private Tier mapRowToTier(Row row) {
    String usageLimitDefaults = null;
    Object usageLimitDefaultsObj = row.getValue("usage_limit_defaults");
    if (usageLimitDefaultsObj != null) {
      if (usageLimitDefaultsObj instanceof JsonObject) {
        usageLimitDefaults = ((JsonObject) usageLimitDefaultsObj).encode();
      } else {
        usageLimitDefaults = usageLimitDefaultsObj.toString();
      }
    }

    return Tier.builder()
        .tierId(row.getInteger("tier_id"))
        .name(row.getString("name"))
        .displayName(row.getString("display_name"))
        .isCustomLimitsAllowed(row.getBoolean("is_custom_limits_allowed"))
        .usageLimitDefaults(usageLimitDefaults)
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC)
            : null)
        .build();
  }
}
