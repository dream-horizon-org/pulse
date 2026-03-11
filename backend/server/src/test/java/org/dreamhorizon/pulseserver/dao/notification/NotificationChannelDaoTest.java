package org.dreamhorizon.pulseserver.dao.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.EmailChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
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
class NotificationChannelDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock ObjectMapper objectMapper;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;
  @Mock Row row;

  NotificationChannelDao notificationChannelDao;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    notificationChannelDao = new NotificationChannelDao(mysqlClient, objectMapper);
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

  private void setupRowSetForEach(RowSet<Row> rs, List<Row> rows) {
    doAnswer(invocation -> {
      java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
      rows.forEach(consumer);
      return null;
    }).when(rs).forEach(any());
  }

  private Row createMockChannelRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    JsonObject configJson = new JsonObject().put("type", "EMAIL").put("fromAddress", "test@example.com");
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getString("channel_type")).thenReturn("EMAIL");
    when(mockRow.getString("name")).thenReturn("Email Channel");
    when(mockRow.getValue("config")).thenReturn(configJson);
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  private Row createMockChannelRowWithStringConfig() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(2L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getString("channel_type")).thenReturn("SLACK");
    when(mockRow.getString("name")).thenReturn("Slack Channel");
    when(mockRow.getValue("config")).thenReturn("{\"webhook\":\"url\"}");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class GetChannelById {

    @Test
    void shouldGetChannelSuccessfully() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(channelRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result = notificationChannelDao.getChannelById(1L).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getChannelType()).isEqualTo(ChannelType.EMAIL);
      assertThat(result.getConfig()).isInstanceOf(EmailChannelConfig.class);
      assertThat(((EmailChannelConfig) result.getConfig()).getFromAddress()).isEqualTo("test@example.com");
    }

    @Test
    void shouldMapJsonObjectConfig() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(channelRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result = notificationChannelDao.getChannelById(1L).blockingGet();

      assertThat(result.getConfig()).isInstanceOf(EmailChannelConfig.class);
      assertThat(((EmailChannelConfig) result.getConfig()).getFromAddress()).isEqualTo("test@example.com");
    }

    @Test
    void shouldMapStringConfig() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRowWithStringConfig();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(channelRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result = notificationChannelDao.getChannelById(2L).blockingGet();

      assertThat(result.getConfig()).isNull();
    }

    @Test
    void shouldReturnEmptyWhenChannelNotFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result = notificationChannelDao.getChannelById(999L).blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class GetChannelsByProject {

    @Test
    void shouldGetChannelsSuccessfully() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRow();
      List<Row> rows = Collections.singletonList(channelRow);
      setupRowSetForEach(rowSet, rows);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationChannel> result =
          notificationChannelDao.getChannelsByProject("proj-1").blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void shouldReturnEmptyListWhenNoChannels() {
      setupReaderPreparedQuery();
      setupRowSetForEach(rowSet, new ArrayList<>());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationChannel> result =
          notificationChannelDao.getChannelsByProject("proj-empty").blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetActiveChannelsByType {

    @Test
    void shouldGetChannelsSuccessfully() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRow();
      List<Row> rows = Collections.singletonList(channelRow);
      setupRowSetForEach(rowSet, rows);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationChannel> result =
          notificationChannelDao.getActiveChannelsByType("proj-1", ChannelType.EMAIL).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.EMAIL);
    }
  }

  @Nested
  class GetActiveChannelByType {

    @Test
    void shouldGetChannelSuccessfully() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(channelRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result =
          notificationChannelDao.getActiveChannelByType("proj-1", ChannelType.EMAIL).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void shouldReturnEmptyWhenNoChannel() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result =
          notificationChannelDao.getActiveChannelByType("proj-1", ChannelType.SLACK).blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class GetActiveChannelByProjectAndType {

    @Test
    void shouldGetChannelSuccessfully() {
      setupReaderPreparedQuery();
      Row channelRow = createMockChannelRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(channelRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result =
          notificationChannelDao
              .getActiveChannelByProjectAndType("proj-1", ChannelType.EMAIL)
              .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void shouldReturnEmptyWhenNoChannel() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationChannel result =
          notificationChannelDao
              .getActiveChannelByProjectAndType("proj-1", ChannelType.TEAMS)
              .blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class CreateChannel {

    @Test
    void shouldCreateChannelSuccessfully() {
      NotificationChannel channel =
          NotificationChannel.builder()
              .projectId("proj-1")
              .channelType(ChannelType.EMAIL)
              .name("Email")
              .config(EmailChannelConfig.builder().fromAddress("test@example.com").build())
              .isActive(true)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(5L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result = notificationChannelDao.createChannel(channel).blockingGet();

      assertThat(result).isEqualTo(5L);
    }
  }

  @Nested
  class UpdateChannel {

    @Test
    void shouldUpdateChannelSuccessfully() {
      NotificationChannel channel =
          NotificationChannel.builder()
              .name("Updated")
              .config(SlackChannelConfig.builder().accessToken("xoxb-token").build())
              .isActive(false)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationChannelDao.updateChannel(1L, channel).blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenNoRowsUpdated() {
      NotificationChannel channel =
          NotificationChannel.builder()
              .name("X")
              .config(EmailChannelConfig.builder().fromAddress("x@example.com").build())
              .isActive(true)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationChannelDao.updateChannel(999L, channel).blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }

  @Nested
  class DeleteChannel {

    @Test
    void shouldDeleteChannelSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationChannelDao.deleteChannel(1L).blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenChannelNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationChannelDao.deleteChannel(999L).blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }
}
