package org.dreamhorizon.pulseserver.dao.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class UserDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;

  UserDao userDao;

  @BeforeEach
  void setup() {
    userDao = new UserDao(mysqlClient);
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

  private Row createMockUserRow() {
    return createMockUserRow("user-123", "user@example.com", "Test User", "active");
  }

  private Row createMockUserRow(String userId, String email, String name, String status) {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("user_id")).thenReturn(userId);
    when(mockRow.getString("email")).thenReturn(email);
    when(mockRow.getString("name")).thenReturn(name);
    when(mockRow.getString("status")).thenReturn(status);
    when(mockRow.getString("firebase_uid")).thenReturn("firebase-uid-123");
    when(mockRow.getLocalDateTime("last_login_at")).thenReturn(now);
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  private Row createMockUserRowWithNullDates() {
    Row mockRow = mock(Row.class);
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("user_id")).thenReturn("user-456");
    when(mockRow.getString("email")).thenReturn("null@example.com");
    when(mockRow.getString("name")).thenReturn("Null User");
    when(mockRow.getString("status")).thenReturn("pending");
    when(mockRow.getString("firebase_uid")).thenReturn(null);
    when(mockRow.getLocalDateTime("last_login_at")).thenReturn(null);
    when(mockRow.getBoolean("is_active")).thenReturn(false);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(null);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(null);
    return mockRow;
  }

  @Nested
  class CreateUser {

    @Test
    void shouldCreateUserSuccessfully() {
      User user = User.builder()
          .userId("user-123")
          .email("test@example.com")
          .name("Test User")
          .status("active")
          .firebaseUid("firebase-uid")
          .isActive(true)
          .build();

      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.createUser(user).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo("user-123");
      assertThat(result.getEmail()).isEqualTo("test@example.com");
      assertThat(result.getName()).isEqualTo("Test User");
      assertThat(result.getStatus()).isEqualTo("active");
      assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void shouldDefaultStatusToPendingWhenNull() {
      User user = User.builder()
          .userId("user-456")
          .email("pending@example.com")
          .name("Pending User")
          .build();

      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.createUser(user).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo("pending");
    }

    @Test
    void shouldDefaultIsActiveToTrueWhenNull() {
      User user = User.builder()
          .userId("user-789")
          .email("default@example.com")
          .name("Default User")
          .status("active")
          .build();

      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.createUser(user).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      User user = User.builder()
          .userId("user-999")
          .email("error@example.com")
          .name("Error User")
          .build();

      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB connection failed")));

      assertThatThrownBy(() -> userDao.createUser(user).blockingGet())
          .hasMessageContaining("DB connection failed");
    }
  }

  @Nested
  class GetUserByEmail {

    @Test
    void shouldGetUserSuccessfully() {
      setupReaderPreparedQuery();
      Row userRow = createMockUserRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(userRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.getUserByEmail("user@example.com").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo("user-123");
      assertThat(result.getEmail()).isEqualTo("user@example.com");
      assertThat(result.getName()).isEqualTo("Test User");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.getUserByEmail("notfound@example.com").blockingGet();

      assertThat(result).isNull();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Query failed")));

      assertThatThrownBy(() -> userDao.getUserByEmail("error@example.com").blockingGet())
          .hasMessageContaining("Query failed");
    }
  }

  @Nested
  class GetUserById {

    @Test
    void shouldGetUserSuccessfully() {
      setupReaderPreparedQuery();
      Row userRow = createMockUserRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(userRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.getUserById("user-123").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo("user-123");
      assertThat(result.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      User result = userDao.getUserById("user-nonexistent").blockingGet();

      assertThat(result).isNull();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Connection error")));

      assertThatThrownBy(() -> userDao.getUserById("user-123").blockingGet())
          .hasMessageContaining("Connection error");
    }
  }

  @Nested
  class GetUsersByIds {

    @Test
    void shouldReturnEmptyListWhenNull() {
      List<User> result = userDao.getUsersByIds(null).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenEmpty() {
      List<User> result = userDao.getUsersByIds(new ArrayList<>()).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldGetSingleUserSuccessfully() {
      setupReaderPreparedQuery();
      Row userRow = createMockUserRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(userRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<User> result = userDao.getUsersByIds(Collections.singletonList("user-123")).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getUserId()).isEqualTo("user-123");
    }

    @Test
    void shouldGetMultipleUsersSuccessfully() {
      setupReaderPreparedQuery();
      Row userRow1 = createMockUserRow("user-1", "user1@example.com", "User One", "active");
      Row userRow2 = createMockUserRow("user-2", "user2@example.com", "User Two", "active");
      RowIterator<Row> iterator = createMockRowIterator(List.of(userRow1, userRow2));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<User> result = userDao.getUsersByIds(List.of("user-1", "user-2")).blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getUserId()).isEqualTo("user-1");
      assertThat(result.get(1).getUserId()).isEqualTo("user-2");
    }

    @Test
    void shouldMapRowWithNullDatesCorrectly() {
      setupReaderPreparedQuery();
      Row userRow = createMockUserRowWithNullDates();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(userRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<User> result = userDao.getUsersByIds(Collections.singletonList("user-456")).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getUserId()).isEqualTo("user-456");
      assertThat(result.get(0).getLastLoginAt()).isNull();
      assertThat(result.get(0).getCreatedAt()).isNull();
      assertThat(result.get(0).getUpdatedAt()).isNull();
      assertThat(result.get(0).getIsActive()).isFalse();
    }

    @Test
    void shouldReturnEmptyListWhenNoUsersFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<User> result = userDao.getUsersByIds(List.of("user-nonexistent")).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Batch fetch failed")));

      assertThatThrownBy(() -> userDao.getUsersByIds(List.of("user-1")).blockingGet())
          .hasMessageContaining("Batch fetch failed");
    }
  }

  @Nested
  class ActivateUser {

    @Test
    void shouldActivateUserSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userDao.activateUser("user-123", "firebase-uid-456", "John Doe").blockingAwait();
    }

    @Test
    void shouldThrowWhenUserNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThatThrownBy(() -> userDao.activateUser("user-nonexistent", "firebase-uid", "Name")
          .blockingAwait())
          .hasMessageContaining("User not found");
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Update failed")));

      assertThatThrownBy(() -> userDao.activateUser("user-123", "firebase-uid", "Name").blockingAwait())
          .hasMessageContaining("Update failed");
    }
  }

  @Nested
  class UpdateLastLogin {

    @Test
    void shouldUpdateLastLoginSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userDao.updateLastLogin("user-123").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenNoUserFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userDao.updateLastLogin("user-nonexistent").blockingAwait();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Update failed")));

      assertThatThrownBy(() -> userDao.updateLastLogin("user-123").blockingAwait())
          .hasMessageContaining("Update failed");
    }
  }

  @Nested
  class UpdateUser {

    @Test
    void shouldUpdateUserSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userDao.updateUser("user-123", "Updated Name").blockingAwait();
    }

    @Test
    void shouldThrowWhenUserNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThatThrownBy(() -> userDao.updateUser("user-nonexistent", "Name").blockingAwait())
          .hasMessageContaining("User not found");
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Update failed")));

      assertThatThrownBy(() -> userDao.updateUser("user-123", "Name").blockingAwait())
          .hasMessageContaining("Update failed");
    }
  }

  @Nested
  class DeactivateUser {

    @Test
    void shouldDeactivateUserSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userDao.deactivateUser("user-123").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenNoUserFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userDao.deactivateUser("user-nonexistent").blockingAwait();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Deactivate failed")));

      assertThatThrownBy(() -> userDao.deactivateUser("user-123").blockingAwait())
          .hasMessageContaining("Deactivate failed");
    }
  }
}
