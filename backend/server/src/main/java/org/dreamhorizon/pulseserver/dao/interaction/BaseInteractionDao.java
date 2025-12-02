package org.dreamhorizon.pulseserver.dao.interaction;

import static org.dreamhorizon.pulseserver.dao.interaction.Queries.ARCHIVE_JOB;
import static org.dreamhorizon.pulseserver.dao.interaction.Queries.AUDIT_INTERACTION_CHANGES;
import static org.dreamhorizon.pulseserver.dao.interaction.Queries.GET_COUNT_OF_INTERACTION_NAME_NAME;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class BaseInteractionDao {
  final MysqlClient d11MysqlClient;

  public Single<Boolean> archiveJob(SqlConnection conn, String interactionName, String user) {
    return conn
        .preparedQuery(ARCHIVE_JOB)
        .rxExecute(Tuple.of(user, interactionName))
        .flatMap(rowSet -> Single.just(true));
  }

  public Single<EmptyResponse> auditInteractionChanges(SqlConnection conn, Long interactionId) {
    return conn.preparedQuery(AUDIT_INTERACTION_CHANGES)
        .rxExecute(Tuple.of(interactionId))
        .map(res -> EmptyResponse.emptyResponse);
  }


  public Single<Boolean> isInteractionPresent(String interactionName) {
    return d11MysqlClient
        .getReaderPool()
        .preparedQuery(GET_COUNT_OF_INTERACTION_NAME_NAME)
        .rxExecute(Tuple.of(interactionName))
        .map(rows -> rows.iterator().next().getInteger("count") > 0)
        .onErrorResumeNext(error -> {
          log.error("Error checking critical interaction name presence: {}", error.getMessage());
          return Single.just(false);
        });
  }
}
