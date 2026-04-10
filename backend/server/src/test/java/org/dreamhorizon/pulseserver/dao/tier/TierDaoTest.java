package org.dreamhorizon.pulseserver.dao.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Query;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
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
class TierDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock Query<RowSet<Row>> query;
  @Mock RowSet<Row> rowSet;
  @Mock Row row;

  TierDao tierDao;

  @BeforeEach
  void setup() {
    tierDao = new TierDao(mysqlClient);
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

  private void setupReaderQuery() {
    setupReaderPool();
    when(readerPool.query(anyString())).thenReturn(query);
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

  private Row createMockTierRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getInteger("tier_id")).thenReturn(1);
    when(mockRow.getString("name")).thenReturn("basic");
    when(mockRow.getString("display_name")).thenReturn("Basic Tier");
    when(mockRow.getBoolean("is_custom_limits_allowed")).thenReturn(true);
    when(mockRow.getValue("usage_limit_defaults")).thenReturn(new JsonObject("{\"events\":100}"));
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    return mockRow;
  }

  private Row createMockTierRowWithStringUsageLimits() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getInteger("tier_id")).thenReturn(2);
    when(mockRow.getString("name")).thenReturn("pro");
    when(mockRow.getString("display_name")).thenReturn("Pro Tier");
    when(mockRow.getBoolean("is_custom_limits_allowed")).thenReturn(false);
    when(mockRow.getValue("usage_limit_defaults")).thenReturn("{\"events\":500}");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class CreateTier {

    @Test
    void shouldCreateTierSuccessfully() {
      Tier tier = Tier.builder()
          .name("basic")
          .displayName("Basic Tier")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults("{\"events\":100}")
          .isActive(true)
          .build();

      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(1L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.createTier(tier).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(1);
      assertThat(result.getName()).isEqualTo("basic");
      assertThat(result.getDisplayName()).isEqualTo("Basic Tier");
      assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      Tier tier = Tier.builder().name("basic").displayName("Basic").build();
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThatThrownBy(() -> tierDao.createTier(tier).blockingGet())
          .hasMessageContaining("DB Error");
    }
  }

  @Nested
  class GetTierById {

    @Test
    void shouldGetTierSuccessfully() {
      setupReaderPreparedQuery();
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.getTierById(1).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(1);
      assertThat(result.getName()).isEqualTo("basic");
    }

    @Test
    void shouldReturnEmptyWhenTierNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.getTierById(999).blockingGet();

      assertThat(result).isNull();
    }

    @Test
    void shouldMapJsonObjectUsageLimits() {
      setupReaderPreparedQuery();
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.getTierById(1).blockingGet();

      assertThat(result.getUsageLimitDefaults()).isEqualTo("{\"events\":100}");
    }

    @Test
    void shouldMapStringUsageLimits() {
      setupReaderPreparedQuery();
      Row tierRow = createMockTierRowWithStringUsageLimits();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.getTierById(2).blockingGet();

      assertThat(result.getUsageLimitDefaults()).isEqualTo("{\"events\":500}");
    }
  }

  @Nested
  class GetTierByName {

    @Test
    void shouldGetTierSuccessfully() {
      setupReaderPreparedQuery();
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.getTierByName("basic").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("basic");
    }

    @Test
    void shouldReturnEmptyWhenTierNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.getTierByName("nonexistent").blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class GetAllActiveTiers {

    @Test
    void shouldGetAllActiveTiersSuccessfully() {
      setupReaderQuery();
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tier> result = tierDao.getAllActiveTiers().toList().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("basic");
    }

    @Test
    void shouldReturnEmptyListWhenNoTiers() {
      setupReaderQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tier> result = tierDao.getAllActiveTiers().toList().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetAllTiers {

    @Test
    void shouldGetAllTiersSuccessfully() {
      setupReaderQuery();
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tier> result = tierDao.getAllTiers().toList().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("basic");
    }

    @Test
    void shouldReturnEmptyListWhenNoTiers() {
      setupReaderQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tier> result = tierDao.getAllTiers().toList().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class UpdateTier {

    @Test
    void shouldUpdateTierSuccessfully() {
      Tier tier = Tier.builder()
          .tierId(1)
          .name("basic")
          .displayName("Basic Tier")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults("{\"events\":200}")
          .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tier result = tierDao.updateTier(tier).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result).isSameAs(tier);
    }

    @Test
    void shouldThrowWhenTierNotFound() {
      Tier tier = Tier.builder().tierId(999).name("x").build();
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThatThrownBy(() -> tierDao.updateTier(tier).blockingGet())
          .hasMessageContaining("Tier not found");
    }
  }

  @Nested
  class UpdateTierDefaults {

    @Test
    void shouldUpdateTierDefaultsSuccessfully() {
      setupWriterPreparedQuery();
      setupReaderPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet))
          .thenReturn(Single.just(rowSet));

      Tier result = tierDao.updateTierDefaults(1, "{\"events\":300}").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenTierNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThatThrownBy(() -> tierDao.updateTierDefaults(999, "{}").blockingGet())
          .hasMessageContaining("Tier not found");
    }
  }

  @Nested
  class DeactivateTier {

    @Test
    void shouldDeactivateTierSuccessfully() {
      setupWriterPreparedQuery();
      setupReaderPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      Row tierRow = createMockTierRow();
      when(tierRow.getBoolean("is_active")).thenReturn(false);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet))
          .thenReturn(Single.just(rowSet));

      Tier result = tierDao.deactivateTier(1).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowWhenTierNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThatThrownBy(() -> tierDao.deactivateTier(999).blockingGet())
          .hasMessageContaining("Tier not found");
    }
  }

  @Nested
  class ActivateTier {

    @Test
    void shouldActivateTierSuccessfully() {
      setupWriterPreparedQuery();
      setupReaderPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      Row tierRow = createMockTierRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(tierRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet))
          .thenReturn(Single.just(rowSet));

      Tier result = tierDao.activateTier(1).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowWhenTierNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertThatThrownBy(() -> tierDao.activateTier(999).blockingGet())
          .hasMessageContaining("Tier not found");
    }
  }

  @Nested
  class DeleteTier {

    @Test
    void shouldDeleteTierSuccessfully() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      tierDao.deleteTier(1).blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenTierNotFound() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      tierDao.deleteTier(999).blockingAwait();
    }
  }

  @Nested
  class TierExists {

    @Test
    void shouldReturnTrueWhenTierExists() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(1L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = tierDao.tierExists(1).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTierDoesNotExist() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = tierDao.tierExists(999).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class TierNameExists {

    @Test
    void shouldReturnTrueWhenTierNameExists() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(1L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = tierDao.tierNameExists("basic").blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTierNameDoesNotExist() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = tierDao.tierNameExists("nonexistent").blockingGet();

      assertThat(result).isFalse();
    }
  }
}
