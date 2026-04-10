package org.dreamhorizon.pulseserver.dao.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class ProjectDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  SqlConnection sqlConnection;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  @Mock
  Row row;

  ProjectDao projectDao;

  @BeforeEach
  void setup() {
    projectDao = new ProjectDao(mysqlClient);
  }

  private void setupWriterPool() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
  }

  private void setupReaderPool() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
  }

  private void setupWriterPreparedQuery() {
    setupWriterPool();
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupReaderPreparedQuery() {
    setupReaderPool();
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> createMockRowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] index = {0};
      when(iterator.hasNext()).thenAnswer(invocation -> index[0] < rows.size());
      when(iterator.next()).thenAnswer(invocation -> {
        if (index[0] < rows.size()) {
          return rows.get(index[0]++);
        }
        throw new java.util.NoSuchElementException();
      });
    }
    return iterator;
  }

  private Row createMockProjectRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getInteger("id")).thenReturn(1);
    when(mockRow.getString("project_id")).thenReturn("proj-123");
    when(mockRow.getString("tenant_id")).thenReturn("tenant-1");
    when(mockRow.getString("name")).thenReturn("My Project");
    when(mockRow.getString("description")).thenReturn("Description");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getString("created_by")).thenReturn("user-1");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class CreateProject {

    @Test
    void shouldCreateProjectSuccessfully() {
      Project project = Project.builder()
          .projectId("proj-new")
          .tenantId("tenant-1")
          .name("New Project")
          .description("Desc")
          .createdBy("user-1")
          .build();

      when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      Project result = projectDao.createProject(sqlConnection, project).blockingGet();

      assertNotNull(result);
      assertEquals(42, result.getId());
      assertEquals("proj-new", result.getProjectId());
      assertEquals("tenant-1", result.getTenantId());
      assertEquals("New Project", result.getName());
      assertTrue(result.getIsActive());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      Project project = Project.builder()
          .projectId("proj-1")
          .tenantId("t1")
          .name("P")
          .createdBy("u1")
          .build();

      when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectDao.createProject(sqlConnection, project).blockingGet());
    }
  }

  @Nested
  class GetProjectByProjectId {

    @Test
    void shouldGetProjectSuccessfully() {
      setupReaderPreparedQuery();
      Row projectRow = createMockProjectRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(projectRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Project result = projectDao.getProjectByProjectId("proj-123").blockingGet();

      assertNotNull(result);
      assertEquals("proj-123", result.getProjectId());
      assertEquals("My Project", result.getName());
    }

    @Test
    void shouldReturnEmptyWhenProjectNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Project result = projectDao.getProjectByProjectId("nonexistent").blockingGet();

      assertNull(result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectDao.getProjectByProjectId("proj-1").blockingGet());
    }
  }

  @Nested
  class GetProjectsByTenantId {

    @Test
    void shouldGetProjectsSuccessfully() {
      setupReaderPreparedQuery();
      Row projectRow = createMockProjectRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(projectRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<Project> result = projectDao.getProjectsByTenantId("tenant-1").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("proj-123", result.get(0).getProjectId());
    }

    @Test
    void shouldReturnEmptyListWhenNoProjects() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<Project> result = projectDao.getProjectsByTenantId("tenant-empty").toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class,
          () -> projectDao.getProjectsByTenantId("tenant-1").toList().blockingGet());
    }
  }

  @Nested
  class UpdateProject {

    @Test
    void shouldUpdateProjectSuccessfully() {
      Project project = Project.builder()
          .projectId("proj-123")
          .name("Updated Name")
          .description("Updated Desc")
          .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Project result = projectDao.updateProject(project).blockingGet();

      assertNotNull(result);
      assertEquals(project, result);
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
      Project project = Project.builder().projectId("nonexistent").name("X").build();
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RuntimeException ex = assertThrows(RuntimeException.class, () -> projectDao.updateProject(project).blockingGet());
      assertTrue(ex.getMessage().contains("Project not found"));
    }

    @Test
    void shouldThrowOnDatabaseError() {
      Project project = Project.builder().projectId("proj-1").name("X").build();
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectDao.updateProject(project).blockingGet());
    }
  }

  @Nested
  class DeactivateProject {

    @Test
    void shouldDeactivateProjectSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      projectDao.deactivateProject("proj-123").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenProjectNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      projectDao.deactivateProject("nonexistent").blockingAwait();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectDao.deactivateProject("proj-1").blockingAwait());
    }
  }

  @Nested
  class ProjectExists {

    @Test
    void shouldReturnTrueWhenProjectExists() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(1L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = projectDao.projectExists("proj-123").blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenProjectDoesNotExist() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = projectDao.projectExists("nonexistent").blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectDao.projectExists("proj-1").blockingGet());
    }
  }

  @Nested
  class GetActiveProjectCount {

    @Test
    void shouldReturnCountSuccessfully() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(5L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = projectDao.getActiveProjectCount("tenant-1").blockingGet();

      assertEquals(5, result);
    }

    @Test
    void shouldReturnZeroWhenNoProjects() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = projectDao.getActiveProjectCount("tenant-empty").blockingGet();

      assertEquals(0, result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class,
          () -> projectDao.getActiveProjectCount("tenant-1").blockingGet());
    }
  }
}
