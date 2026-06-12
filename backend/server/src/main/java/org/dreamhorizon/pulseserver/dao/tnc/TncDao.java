package org.dreamhorizon.pulseserver.dao.tnc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncAcceptance;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncVersion;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TncDao {

  private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final MysqlClient mysqlClient;

  public Maybe<TncVersion> getActiveVersion() {
    return mysqlClient.getReaderPool()
        .preparedQuery(TncQueries.GET_ACTIVE_VERSION)
        .rxExecute()
        .flatMapMaybe(rows -> {
          if (rows.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToVersion(rows.iterator().next()));
        });
  }

  public Maybe<TncAcceptance> getAcceptance(String tenantId, Long versionId) {
    return mysqlClient.getReaderPool()
        .preparedQuery(TncQueries.GET_ACCEPTANCE)
        .rxExecute(Tuple.of(tenantId, versionId))
        .flatMapMaybe(rows -> {
          if (rows.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToAcceptance(rows.iterator().next()));
        });
  }

  public Single<TncAcceptance> insertAcceptance(String tenantId, Long versionId,
      String email, String userAgent) {
    return mysqlClient.getWriterPool()
        .preparedQuery(TncQueries.INSERT_ACCEPTANCE)
        .rxExecute(Tuple.of(tenantId, versionId, email, userAgent))
        .flatMap(result -> {
          log.info("TnC acceptance recorded: tenant={}, version={}, email={}", tenantId, versionId, email);
          // Reload on writer pool: reader replica may not have the row yet after INSERT.
          return mysqlClient.getWriterPool()
              .preparedQuery(TncQueries.GET_ACCEPTANCE)
              .rxExecute(Tuple.of(tenantId, versionId))
              .flatMap(rows -> {
                if (rows.size() == 0) {
                  log.error("TnC acceptance not visible on writer after insert: tenant={}, version={}",
                      tenantId, versionId);
                  return Single.error(new IllegalStateException(
                      "TnC acceptance could not be reloaded after insert"));
                }
                return Single.just(mapRowToAcceptance(rows.iterator().next()));
              });
        });
  }

  public Flowable<TncAcceptance> getAcceptanceHistory(String tenantId) {
    return mysqlClient.getReaderPool()
        .preparedQuery(TncQueries.GET_ACCEPTANCE_HISTORY)
        .rxExecute(Tuple.of(tenantId))
        .flatMapPublisher(rows -> {
          return Flowable.fromIterable(rows)
              .map(this::mapRowToAcceptance);
        });
  }

  public Single<TncVersion> publishVersion(String version, String tosUrl, String aupUrl,
      String ppUrl, String summary, String createdBy) {
    return mysqlClient.getWriterPool()
        .preparedQuery(TncQueries.INSERT_VERSION)
        .rxExecute(Tuple.of(version, tosUrl, aupUrl, ppUrl, summary, createdBy))
        .flatMap(insertResult -> {
          log.info("Inserted TnC version: {}", version);
          return mysqlClient.getWriterPool()
              .preparedQuery(TncQueries.DEACTIVATE_OTHER_VERSIONS)
              .rxExecute(Tuple.of(version));
        })
        .flatMap(deactivated -> {
          log.info("Published TnC version: {}, deactivated previous versions", version);
          // Must read from writer pool: reader replica can lag behind the insert/update above,
          // which would make getActiveVersion() empty and .toSingle() throw NoSuchElementException.
          return mysqlClient.getWriterPool()
              .preparedQuery(TncQueries.GET_ACTIVE_VERSION_BY_VERSION_STRING)
              .rxExecute(Tuple.of(version))
              .flatMap(rows -> {
                if (rows.size() == 0) {
                  log.error("TnC version {} not visible on writer after publish", version);
                  return Single.error(new IllegalStateException(
                      "Published TnC version could not be reloaded; verify tnc_versions data"));
                }
                return Single.just(mapRowToVersion(rows.iterator().next()));
              });
        });
  }

  private TncVersion mapRowToVersion(Row row) {
    return TncVersion.builder()
        .id(row.getLong("id"))
        .version(row.getString("version"))
        .tosS3Url(row.getString("tos_s3_url"))
        .aupS3Url(row.getString("aup_s3_url"))
        .privacyPolicyS3Url(row.getString("privacy_policy_s3_url"))
        .summary(row.getString("summary"))
        .active(row.getBoolean("is_active"))
        .publishedAt(formatDateTime(row.getLocalDateTime("published_at")))
        .createdBy(row.getString("created_by"))
        .createdAt(formatDateTime(row.getLocalDateTime("created_at")))
        .build();
  }

  private TncAcceptance mapRowToAcceptance(Row row) {
    return TncAcceptance.builder()
        .id(row.getLong("id"))
        .tenantId(row.getString("tenant_id"))
        .tncVersionId(row.getLong("tnc_version_id"))
        .acceptedByEmail(row.getString("accepted_by_email"))
        .acceptedAt(formatDateTime(row.getLocalDateTime("accepted_at")))
        .userAgent(row.getString("user_agent"))
        .build();
  }

  private String formatDateTime(LocalDateTime dt) {
    return dt != null ? dt.format(DT_FORMAT) : null;
  }
}
