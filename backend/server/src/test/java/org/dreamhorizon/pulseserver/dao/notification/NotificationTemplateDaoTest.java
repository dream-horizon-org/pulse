package org.dreamhorizon.pulseserver.dao.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
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
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.EmailTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
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
class NotificationTemplateDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;
  @Mock Row row;

  NotificationTemplateDao notificationTemplateDao;

  @BeforeEach
  void setup() {
    notificationTemplateDao = new NotificationTemplateDao(mysqlClient, new ObjectMapper());
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

  private Row createMockTemplateRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    String bodyJson = "{\"type\":\"EMAIL\",\"subject\":\"Alert\",\"html\":\"<p>Hello {{name}}</p>\"}";
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("event_name")).thenReturn("alert_created");
    when(mockRow.getString("channel_type")).thenReturn("EMAIL");
    when(mockRow.getInteger("version")).thenReturn(1);
    when(mockRow.getValue("body")).thenReturn(bodyJson);
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class GetTemplateById {

    @Test
    void shouldGetTemplateSuccessfully() {
      setupReaderPreparedQuery();
      Row templateRow = createMockTemplateRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(templateRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationTemplate result = notificationTemplateDao.getTemplateById(1L).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getEventName()).isEqualTo("alert_created");
      assertThat(result.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void shouldReturnEmptyWhenTemplateNotFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationTemplate result = notificationTemplateDao.getTemplateById(999L).blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class GetTemplatesByChannelType {

    @Test
    void shouldGetTemplatesSuccessfully() {
      setupReaderPreparedQuery();
      Row templateRow = createMockTemplateRow();
      List<Row> rows = Collections.singletonList(templateRow);
      setupRowSetForEach(rowSet, rows);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationTemplate> result =
          notificationTemplateDao.getTemplatesByChannelType(ChannelType.EMAIL).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getEventName()).isEqualTo("alert_created");
      assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void shouldReturnEmptyListWhenNoTemplates() {
      setupReaderPreparedQuery();
      setupRowSetForEach(rowSet, new ArrayList<>());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationTemplate> result =
          notificationTemplateDao.getTemplatesByChannelType(ChannelType.SLACK).blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetTemplateByEventNameAndChannel {

    @Test
    void shouldGetTemplateSuccessfully() {
      setupReaderPreparedQuery();
      Row templateRow = createMockTemplateRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(templateRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationTemplate result =
          notificationTemplateDao
              .getTemplateByEventNameAndChannel("alert_created", ChannelType.EMAIL)
              .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getEventName()).isEqualTo("alert_created");
      assertThat(result.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void shouldReturnEmptyWhenTemplateNotFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationTemplate result =
          notificationTemplateDao
              .getTemplateByEventNameAndChannel("unknown", ChannelType.SLACK)
              .blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class GetLatestVersion {

    @Test
    void shouldReturnLatestVersion() {
      setupReaderPreparedQuery();
      Row versionRow = mock(Row.class);
      when(versionRow.getInteger(0)).thenReturn(3);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(versionRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          notificationTemplateDao
              .getLatestVersion("alert_created", ChannelType.EMAIL)
              .blockingGet();

      assertThat(result).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroWhenNoTemplates() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          notificationTemplateDao
              .getLatestVersion("new_event", ChannelType.EMAIL)
              .blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }

  @Nested
  class CreateTemplate {

    @Test
    void shouldCreateTemplateSuccessfully() {
      NotificationTemplate template =
          NotificationTemplate.builder()
              .eventName("alert_created")
              .channelType(ChannelType.EMAIL)
              .version(1)
              .body(EmailTemplateBody.builder().subject("Subject").html("<p>Hello</p>").build())
              .isActive(true)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(10L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result = notificationTemplateDao.createTemplate(template).blockingGet();

      assertThat(result).isEqualTo(10L);
    }
  }

  @Nested
  class UpdateTemplate {

    @Test
    void shouldUpdateTemplateSuccessfully() {
      NotificationTemplate template =
          NotificationTemplate.builder()
              .eventName("alert_updated")
              .channelType(ChannelType.EMAIL)
              .body(
                  EmailTemplateBody.builder()
                      .subject("Updated")
                      .html("<p>Updated body</p>")
                      .build())
              .isActive(true)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationTemplateDao.updateTemplate(1L, template).blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenNoRowsUpdated() {
      NotificationTemplate template =
          NotificationTemplate.builder()
              .eventName("x")
              .body(EmailTemplateBody.builder().subject("x").html("<p>x</p>").build())
              .isActive(true)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationTemplateDao.updateTemplate(999L, template).blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }

  @Nested
  class DeleteTemplate {

    @Test
    void shouldDeleteTemplateSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationTemplateDao.deleteTemplate(1L).blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenTemplateNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = notificationTemplateDao.deleteTemplate(999L).blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }
}
