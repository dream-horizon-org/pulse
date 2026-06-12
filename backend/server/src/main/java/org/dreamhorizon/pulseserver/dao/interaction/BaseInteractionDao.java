package org.dreamhorizon.pulseserver.dao.interaction;

import static org.dreamhorizon.pulseserver.dao.interaction.Queries.ARCHIVE_JOB;
import static org.dreamhorizon.pulseserver.dao.interaction.Queries.GET_COUNT_OF_INTERACTION_NAME_NAME;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class BaseInteractionDao {
  final MysqlClient d11MysqlClient;

  /**
   * Gets the current project ID from the ProjectContext.
   */
  private String getProjectId() {
    return ProjectContext.getProjectId();
  }

  public Single<Boolean> archiveJob(SqlConnection conn, String interactionName, String user) {
    return conn
        .preparedQuery(ARCHIVE_JOB)
        .rxExecute(Tuple.of(user, getProjectId(), interactionName))
        .flatMap(rowSet -> Single.just(true));
  }


  public Single<Boolean> isInteractionPresent(String interactionName) {
    return d11MysqlClient
        .getReaderPool()
        .preparedQuery(GET_COUNT_OF_INTERACTION_NAME_NAME)
        .rxExecute(Tuple.of(getProjectId(), interactionName))
        .map(rows -> rows.iterator().next().getInteger("count") > 0)
        .onErrorResumeNext(error -> {
          log.error("Error checking critical interaction name presence: {}", error.getMessage());
          return Single.just(false);
        });
  }
}
