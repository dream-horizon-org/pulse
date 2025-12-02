package org.dreamhorizon.pulseserver.dao.interaction;

import static org.dreamhorizon.pulseserver.dao.interaction.Queries.GET_INTERACTION_FILTER_OPTIONS;
import static org.dreamhorizon.pulseserver.dao.interaction.Queries.GET_TELEMETRY_FILTER_VALUES;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.interaction.models.InteractionDetailRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionStatus;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetailUploadMetadata;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Transaction;
import io.vertx.rxjava3.sqlclient.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@ExtendWith(MockitoExtension.class)
class InteractionDaoTest {
    InteractionDao interactionDao;

    @Mock
    MysqlClient mysqlClient;

    @Mock
    ClickhouseQueryService clickhouseQueryService;

    @Mock
    BaseInteractionDao baseInteractionDao;

    @Mock
    private SqlConnection sqlConnection;

    @Mock
    private MySQLPool mySqlPool;

    @Mock
    private Transaction transaction;

    @Mock
    private RowSet<Row> rowSet;

    final ObjectMapperUtil objectMapper = new ObjectMapperUtil();

    @BeforeEach
    void setUp() {
        interactionDao = new InteractionDao(mysqlClient, clickhouseQueryService, objectMapper, baseInteractionDao);
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    public class TestUpdateInteractionAndCreateUploadMetadata {

        private final String UPDATE_INTERACTION_QUERY = "UPDATE interaction SET "
                + "status = ?, details = ?, last_updated_at = ?, updated_by = ? "
                + " WHERE interaction_id = ?";


        @Test
        void shouldUpdateInteractionAndCreateUploadMetadata() {
            // Given
            var now = LocalDateTime.now();
            var interaction = InteractionDetails.builder()
                    .id(1L)
                    .name("test-interaction")
                    .description("test description")
                    .uptimeLowerLimitInMs(10)
                    .uptimeMidLimitInMs(20)
                    .uptimeUpperLimitInMs(30)
                    .thresholdInMs(100)
                    .status(InteractionStatus.RUNNING)
                    .events(List.of(
                            Event.builder().name("event1").build(),
                            Event.builder().name("event2").build()
                    ))
                    .globalBlacklistedEvents(List.of())
                    .createdAt(Timestamp.valueOf(now.minusDays(1)))
                    .createdBy("creator")
                    .updatedAt(Timestamp.valueOf(now))
                    .updatedBy("updater")
                    .build();

            var expectedUploadMetadata = InteractionDetailUploadMetadata.builder()
                    .interactionId(1L)
                    .status(InteractionDetailUploadMetadata.Status.PENDING)
                    .build();

            PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

            when(mysqlClient.getWriterPool()).thenReturn(mySqlPool);
            when(mysqlClient.getWriterPool().rxGetConnection()).thenReturn(Single.just(sqlConnection));
            when(sqlConnection.preparedQuery(UPDATE_INTERACTION_QUERY)).thenReturn(preparedQuery);

            // Capture the Tuple being passed to rxExecute
            var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
            when(preparedQuery.rxExecute(tupleCaptor.capture()))
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.updateInteractionAndCreateUploadMetadata(interaction).blockingGet();

            // Then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedUploadMetadata);
            verify(sqlConnection, times(1)).close();

            // Verify the Tuple values
            var capturedTuple = tupleCaptor.getValue();
            assertThat(capturedTuple.getString(0)).isEqualTo(interaction.getStatus().toString());

            String interactionDetails = capturedTuple.getString(1);
            InteractionDetailRow actualInteractionDetailRow = objectMapper.readValue(interactionDetails, InteractionDetailRow.class);
            InteractionDetailRow expectedInteractionDetailRow = InteractionDetailRow.builder()
                    .description(interaction.getDescription())
                    .uptimeLowerLimitInMs(interaction.getUptimeLowerLimitInMs())
                    .uptimeMidLimitInMs(interaction.getUptimeMidLimitInMs())
                    .uptimeUpperLimitInMs(interaction.getUptimeUpperLimitInMs())
                    .thresholdInMs(interaction.getThresholdInMs())
                    .events(interaction.getEvents())
                    .globalBlacklistedEvents(interaction.getGlobalBlacklistedEvents())
                    .build();

            assertThat(actualInteractionDetailRow).usingRecursiveComparison().isEqualTo(expectedInteractionDetailRow);
            assertThat(capturedTuple.getLocalDateTime(2)).isEqualTo(interaction.getUpdatedAt().toLocalDateTime());
            assertThat(capturedTuple.getString(3)).isEqualTo(interaction.getUpdatedBy());
            assertThat(capturedTuple.getLong(4)).isEqualTo(interaction.getId());
            verifyNoMoreInteractions(baseInteractionDao);
            verifyNoMoreInteractions(mysqlClient);
        }

        @Test
        void shouldPropagateErrorIfUpdateFails() {
            // Given
            var now = java.time.LocalDateTime.now();
            var interaction = InteractionDetails.builder()
                    .id(1L)
                    .name("test-interaction")
                    .description("test description")
                    .uptimeLowerLimitInMs(10)
                    .uptimeMidLimitInMs(20)
                    .uptimeUpperLimitInMs(30)
                    .thresholdInMs(100)
                    .status(InteractionStatus.RUNNING)
                    .events(java.util.List.of(
                            Event.builder().name("event1").build(),
                            Event.builder().name("event2").build()
                    ))
                    .globalBlacklistedEvents(java.util.List.of())
                    .createdAt(java.sql.Timestamp.valueOf(now.minusDays(1)))
                    .createdBy("creator")
                    .updatedAt(java.sql.Timestamp.valueOf(now))
                    .updatedBy("updater")
                    .build();

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
            var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);

            when(mysqlClient.getWriterPool()).thenReturn(mySqlPool);
            when(mysqlClient.getWriterPool().rxGetConnection()).thenReturn(Single.just(sqlConnection));
            when(sqlConnection.preparedQuery(UPDATE_INTERACTION_QUERY)).thenReturn(preparedQuery);
            when(preparedQuery.rxExecute(tupleCaptor.capture()))
                    .thenReturn(Single.error(new RuntimeException("Database error")));

            // When
            var testObserver = interactionDao.updateInteractionAndCreateUploadMetadata(interaction).test();

            // Then
            testObserver.assertError(RuntimeException.class);

            // Verify the Tuple values that were attempted
            var capturedTuple = tupleCaptor.getValue();
            assertThat(capturedTuple.getString(0)).isEqualTo(interaction.getStatus().toString());

            String interactionDetails = capturedTuple.getString(1);
            InteractionDetailRow actualInteractionDetailRow = objectMapper.readValue(interactionDetails, InteractionDetailRow.class);
            InteractionDetailRow expectedInteractionDetailRow = InteractionDetailRow.builder()
                    .description(interaction.getDescription())
                    .uptimeLowerLimitInMs(interaction.getUptimeLowerLimitInMs())
                    .uptimeMidLimitInMs(interaction.getUptimeMidLimitInMs())
                    .uptimeUpperLimitInMs(interaction.getUptimeUpperLimitInMs())
                    .thresholdInMs(interaction.getThresholdInMs())
                    .events(interaction.getEvents())
                    .globalBlacklistedEvents(interaction.getGlobalBlacklistedEvents())
                    .build();

            assertThat(actualInteractionDetailRow).usingRecursiveComparison().isEqualTo(expectedInteractionDetailRow);
            assertThat(capturedTuple.getLocalDateTime(2)).isEqualTo(interaction.getUpdatedAt().toLocalDateTime());
            assertThat(capturedTuple.getString(3)).isEqualTo(interaction.getUpdatedBy());
            assertThat(capturedTuple.getLong(4)).isEqualTo(interaction.getId());

            // Verify connection handling
            verify(sqlConnection, times(1)).close();

            // Verify no other operations were attempted
            verifyNoMoreInteractions(baseInteractionDao);
            verifyNoMoreInteractions(mysqlClient);
        }

    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    public class TestIsInteractionPresent {
        @Test
        void shouldReturnTrueIfInteractionExists() {
            // Given
            String interactionName = "test-interaction";
            when(baseInteractionDao.isInteractionPresent(interactionName))
                    .thenReturn(Single.just(true));

            // When
            var result = interactionDao.isInteractionPresent(interactionName).blockingGet();

            // Then
            assertThat(result).isTrue();
            verify(baseInteractionDao, times(1)).isInteractionPresent(interactionName);
            verifyNoMoreInteractions(baseInteractionDao);
        }

        @Test
        void shouldReturnFalseIfInteractionDoesNotExists() {
            // Given
            String interactionName = "non-existent-interaction";
            when(baseInteractionDao.isInteractionPresent(interactionName))
                    .thenReturn(Single.just(false));

            // When
            var result = interactionDao.isInteractionPresent(interactionName).blockingGet();

            // Then
            assertThat(result).isFalse();
            verify(baseInteractionDao, times(1)).isInteractionPresent(interactionName);
            verifyNoMoreInteractions(baseInteractionDao);
        }

        @Test
        void shouldPropagateErrorIfDaoFails() {
            // Given
            String interactionName = "test-interaction";
            RuntimeException expectedError = new RuntimeException("Database error");
            when(baseInteractionDao.isInteractionPresent(interactionName))
                    .thenReturn(Single.error(expectedError));

            // When
            var testObserver = interactionDao.isInteractionPresent(interactionName).test();

            // Then
            testObserver.assertError(RuntimeException.class);
            verify(baseInteractionDao, times(1)).isInteractionPresent(interactionName);
            verifyNoMoreInteractions(baseInteractionDao);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    public class TestGetInteractionDetails {

        private final String GET_INTERACTION_DETAILS = "SELECT "
                + "interaction_id, name, status, details, created_at, created_by, last_updated_at, updated_by "
                + " from interaction where name = ? and is_archived = 0";

        @Test
        void shouldThrowExceptionWhenInteractionIsNotPresent() {
            // Given
            String useCaseId = "non-existent-interaction";
            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
            var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_DETAILS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute(tupleCaptor.capture()))
                    .thenReturn(Single.just(rowSet));
            when(rowSet.size()).thenReturn(0); // Simulate no rows returned

            // When
            var testObserver = interactionDao.getInteractionDetails(useCaseId).test();

            // Then
            testObserver.assertError(RuntimeException.class);

            // Verify the Tuple values
            var capturedTuple = tupleCaptor.getValue();
            assertThat(capturedTuple.getString(0)).isEqualTo(useCaseId);

            // Verify no other operations were attempted
            verifyNoMoreInteractions(mysqlClient);
        }

        @Test
        void shouldSuccessfullyGetInteractionDetails() {
            // Given
            String useCaseId = "test-interaction";
            var now = java.time.LocalDateTime.now();
            var expectedInteraction = InteractionDetails.builder()
                    .id(1L)
                    .name(useCaseId)
                    .description("test description")
                    .uptimeLowerLimitInMs(10)
                    .uptimeMidLimitInMs(20)
                    .uptimeUpperLimitInMs(30)
                    .thresholdInMs(100)
                    .status(InteractionStatus.RUNNING)
                    .events(java.util.List.of(
                            Event.builder().name("event1").build(),
                            Event.builder().name("event2").build()
                    ))
                    .globalBlacklistedEvents(java.util.List.of())
                    .createdAt(java.sql.Timestamp.valueOf(now.minusDays(1)))
                    .createdBy("creator")
                    .updatedAt(java.sql.Timestamp.valueOf(now))
                    .updatedBy("updater")
                    .build();

            InteractionDetailRow interactionDetailRow = InteractionDetailRow.builder()
                    .description(expectedInteraction.getDescription())
                    .uptimeLowerLimitInMs(expectedInteraction.getUptimeLowerLimitInMs())
                    .uptimeMidLimitInMs(expectedInteraction.getUptimeMidLimitInMs())
                    .uptimeUpperLimitInMs(expectedInteraction.getUptimeUpperLimitInMs())
                    .thresholdInMs(expectedInteraction.getThresholdInMs())
                    .events(expectedInteraction.getEvents())
                    .globalBlacklistedEvents(expectedInteraction.getGlobalBlacklistedEvents())
                    .build();

            Row mockRow = Mockito.mock(Row.class);
            when(mockRow.getLong("interaction_id")).thenReturn(expectedInteraction.getId());
            when(mockRow.getString("name")).thenReturn(expectedInteraction.getName());
            when(mockRow.getString("status")).thenReturn(expectedInteraction.getStatus().toString());
            when(mockRow.getString("created_by")).thenReturn(expectedInteraction.getCreatedBy());
            when(mockRow.getLocalDateTime("created_at")).thenReturn(expectedInteraction.getCreatedAt().toLocalDateTime());
            when(mockRow.getString("updated_by")).thenReturn(expectedInteraction.getUpdatedBy());
            when(mockRow.getLocalDateTime("last_updated_at")).thenReturn(expectedInteraction.getUpdatedAt().toLocalDateTime());
            when(mockRow.getValue("details")).thenReturn(objectMapper.writeValueAsString(interactionDetailRow));

            RowSet<Row> mockRowSet = Mockito.mock(RowSet.class);
            when(mockRowSet.size()).thenReturn(1);
            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(mockRowSet.iterator()).thenReturn(iterator);
            when(iterator.next()).thenReturn(mockRow);

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
            var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_DETAILS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute(tupleCaptor.capture()))
                    .thenReturn(Single.just(mockRowSet));

            // When
            var result = interactionDao.getInteractionDetails(useCaseId).blockingGet();

            // Then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedInteraction);

            // Verify the Tuple values
            var capturedTuple = tupleCaptor.getValue();
            assertThat(capturedTuple.getString(0)).isEqualTo(useCaseId);

            // Verify no other operations were attempted
            verifyNoMoreInteractions(mysqlClient);
        }

        @Test
        void shouldPropagateErrorIfDatabaseCallFails() {
            // Given
            String useCaseId = "test-interaction";
            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
            var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
            RuntimeException expectedError = new RuntimeException("Database connection failed");

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_DETAILS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute(tupleCaptor.capture()))
                    .thenReturn(Single.error(expectedError));

            // When
            var testObserver = interactionDao.getInteractionDetails(useCaseId).test();

            // Then
            testObserver.assertError(RuntimeException.class);

            // Verify the Tuple values
            var capturedTuple = tupleCaptor.getValue();
            assertThat(capturedTuple.getString(0)).isEqualTo(useCaseId);

            // Verify no other operations were attempted
            verifyNoMoreInteractions(mysqlClient);
        }
    }


    @Nested
    @ExtendWith(MockitoExtension.class)
    public class TestGetInteractions {

        String GET_INTERACTIONS_BASE_QUERY = "SELECT interaction_id, name, "
                + " created_by, updated_by, created_at, last_updated_at, status, details, "
                + " COUNT(*) OVER() AS total_interactions FROM interaction "
                + " WHERE is_archived = 0 ";

        @Test
        void shouldGetInteractionsWithPagination() {
            // Given
            var request = GetInteractionsRequest.builder()
                    .page(0)
                    .size(10)
                    .build();

            var now = java.time.LocalDateTime.now();
            var expectedInteraction = InteractionDetails.builder()
                    .id(1L)
                    .name("test-interaction")
                    .description("test description")
                    .uptimeLowerLimitInMs(10)
                    .uptimeMidLimitInMs(20)
                    .uptimeUpperLimitInMs(30)
                    .thresholdInMs(100)
                    .status(InteractionStatus.RUNNING)
                    .events(java.util.List.of(
                            Event.builder().name("event1").build(),
                            Event.builder().name("event2").build()
                    ))
                    .globalBlacklistedEvents(java.util.List.of())
                    .createdAt(java.sql.Timestamp.valueOf(now.minusDays(1)))
                    .createdBy("creator")
                    .updatedAt(java.sql.Timestamp.valueOf(now))
                    .updatedBy("updater")
                    .build();

            InteractionDetailRow interactionDetailRow = InteractionDetailRow.builder()
                    .description(expectedInteraction.getDescription())
                    .uptimeLowerLimitInMs(expectedInteraction.getUptimeLowerLimitInMs())
                    .uptimeMidLimitInMs(expectedInteraction.getUptimeMidLimitInMs())
                    .uptimeUpperLimitInMs(expectedInteraction.getUptimeUpperLimitInMs())
                    .thresholdInMs(expectedInteraction.getThresholdInMs())
                    .events(expectedInteraction.getEvents())
                    .globalBlacklistedEvents(expectedInteraction.getGlobalBlacklistedEvents())
                    .build();

            Row mockRow = Mockito.mock(Row.class);
            lenient().when(mockRow.getLong("interaction_id")).thenReturn(expectedInteraction.getId());
            lenient().when(mockRow.getString("name")).thenReturn(expectedInteraction.getName());
            lenient().when(mockRow.getString("status")).thenReturn(expectedInteraction.getStatus().toString());
            lenient().when(mockRow.getString("created_by")).thenReturn(expectedInteraction.getCreatedBy());
            lenient().when(mockRow.getLocalDateTime("created_at")).thenReturn(expectedInteraction.getCreatedAt().toLocalDateTime());
            lenient().when(mockRow.getString("updated_by")).thenReturn(expectedInteraction.getUpdatedBy());
            lenient().when(mockRow.getLocalDateTime("last_updated_at")).thenReturn(expectedInteraction.getUpdatedAt().toLocalDateTime());
            lenient().when(mockRow.getValue("details")).thenReturn(objectMapper.writeValueAsString(interactionDetailRow));
            lenient().when(mockRow.getInteger("total_interactions")).thenReturn(1);



            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(rowSet.iterator()).thenReturn(iterator);
            when(iterator.next()).thenReturn(mockRow);
            when(iterator.hasNext()).thenReturn(true, false);

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTIONS_BASE_QUERY + " limit 10 offset 0;"))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.getInteractions(request).blockingGet();

            // Then
            assertThat(result.getTotalInteractions()).isEqualTo(1);
//      assertThat(result.getInteractions().size()).isEqualTo(1);
//      assertThat(result.getInteractions().get(0)).usingRecursiveComparison().isEqualTo(expectedInteraction);

            // Verify no other operations were attempted
            verifyNoMoreInteractions(mysqlClient);
        }

        @Test
        void shouldGetInteractionsWithFilters() {
            // Given
            var request = GetInteractionsRequest.builder()
                    .page(0)
                    .size(10)
                    .userEmail("test@example.com")
                    .name("test")
                    .build();

            var now = java.time.LocalDateTime.now();
            var expectedInteraction = InteractionDetails.builder()
                    .id(1L)
                    .name("test-interaction")
                    .description("test description")
                    .uptimeLowerLimitInMs(10)
                    .uptimeMidLimitInMs(20)
                    .uptimeUpperLimitInMs(30)
                    .thresholdInMs(100)
                    .status(InteractionStatus.RUNNING)
                    .events(java.util.List.of(
                            Event.builder().name("event1").build(),
                            Event.builder().name("event2").build()
                    ))
                    .globalBlacklistedEvents(java.util.List.of())
                    .createdAt(java.sql.Timestamp.valueOf(now.minusDays(1)))
                    .createdBy("test@example.com")
                    .updatedAt(java.sql.Timestamp.valueOf(now))
                    .updatedBy("updater")
                    .build();

            InteractionDetailRow interactionDetailRow = InteractionDetailRow.builder()
                    .description(expectedInteraction.getDescription())
                    .uptimeLowerLimitInMs(expectedInteraction.getUptimeLowerLimitInMs())
                    .uptimeMidLimitInMs(expectedInteraction.getUptimeMidLimitInMs())
                    .uptimeUpperLimitInMs(expectedInteraction.getUptimeUpperLimitInMs())
                    .thresholdInMs(expectedInteraction.getThresholdInMs())
                    .events(expectedInteraction.getEvents())
                    .globalBlacklistedEvents(expectedInteraction.getGlobalBlacklistedEvents())
                    .build();

            Row mockRow = Mockito.mock(Row.class);
            lenient().when(mockRow.getLong("interaction_id")).thenReturn(expectedInteraction.getId());
            lenient().when(mockRow.getString("name")).thenReturn(expectedInteraction.getName());
            lenient().when(mockRow.getString("status")).thenReturn(expectedInteraction.getStatus().toString());
            lenient().when(mockRow.getString("created_by")).thenReturn(expectedInteraction.getCreatedBy());
            lenient().when(mockRow.getLocalDateTime("created_at")).thenReturn(expectedInteraction.getCreatedAt().toLocalDateTime());
            lenient().when(mockRow.getString("updated_by")).thenReturn(expectedInteraction.getUpdatedBy());
            lenient().when(mockRow.getLocalDateTime("last_updated_at")).thenReturn(expectedInteraction.getUpdatedAt().toLocalDateTime());
            lenient().when(mockRow.getValue("details")).thenReturn(objectMapper.writeValueAsString(interactionDetailRow));
            lenient().when(mockRow.getInteger("total_interactions")).thenReturn(1);

            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(rowSet.iterator()).thenReturn(iterator);
            when(iterator.next()).thenReturn(mockRow);
            when(iterator.hasNext()).thenReturn(true, false);

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(
                    GET_INTERACTIONS_BASE_QUERY +
                            " AND created_by = 'test@example.com'" +
                            " AND name LIKE '%test%'" +
                            " limit 10 offset 0;"))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.getInteractions(request).blockingGet();

            // Then
            assertThat(result.getTotalInteractions()).isEqualTo(1);
//      assertThat(result.getInteractions().size()).isEqualTo(1);
//      assertThat(result.getInteractions().get(0)).usingRecursiveComparison().isEqualTo(expectedInteraction);

            // Verify no other operations were attempted
            verifyNoMoreInteractions(mysqlClient);
        }

        @Test
        void shouldPropagateErrorIfDatabaseCallFails() {
            // Given
            var request = GetInteractionsRequest.builder()
                    .page(0)
                    .size(10)
                    .build();

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
            RuntimeException expectedError = new RuntimeException("Database connection failed");

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTIONS_BASE_QUERY + " limit 10 offset 0;"))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.error(expectedError));

            // When
            var testObserver = interactionDao.getInteractions(request).test();

            // Then
            testObserver.assertError(RuntimeException.class);

            // Verify no other operations were attempted
            verifyNoMoreInteractions(mysqlClient);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    public class TestGetInteractionFilterOptions {

        @Test
        void shouldReturnFilterOptionsSuccessfully() {
            // Given
            Row mockRow1 = Mockito.mock(Row.class);
            Row mockRow2 = Mockito.mock(Row.class);
            Row mockRow3 = Mockito.mock(Row.class);

            lenient().when(mockRow1.getString("status")).thenReturn("RUNNING");
            lenient().when(mockRow1.getString("created_by")).thenReturn("user1@example.com");

            lenient().when(mockRow2.getString("status")).thenReturn("STOPPED");
            lenient().when(mockRow2.getString("created_by")).thenReturn("user2@example.com");

            lenient().when(mockRow3.getString("status")).thenReturn("RUNNING");
            lenient().when(mockRow3.getString("created_by")).thenReturn("user3@example.com");

            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(iterator.hasNext()).thenReturn(true, true, true, false);
            when(iterator.next()).thenReturn(mockRow1, mockRow2, mockRow3);
            when(rowSet.iterator()).thenReturn(iterator);

            // Mock forEach to actually iterate
            doAnswer(invocation -> {
                java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
                consumer.accept(mockRow1);
                consumer.accept(mockRow2);
                consumer.accept(mockRow3);
                return null;
            }).when(rowSet).forEach(any());

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_FILTER_OPTIONS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.getInteractionFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatuses().size()).isEqualTo(2); // RUNNING and STOPPED (unique)
            assertThat(result.getCreatedBy().size()).isEqualTo(3); // 3 unique users
            assertThat(result.getStatuses().contains("RUNNING")).isTrue();
            assertThat(result.getStatuses().contains("STOPPED")).isTrue();
        }

        @Test
        void shouldReturnEmptyListsWhenNoDataExists() {
            // Given
            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(rowSet.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(false);

            // Mock forEach to do nothing (no rows)
            doAnswer(invocation -> null).when(rowSet).forEach(any());

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_FILTER_OPTIONS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.getInteractionFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatuses().size()).isEqualTo(0);
            assertThat(result.getCreatedBy().size()).isEqualTo(0);
        }

        @Test
        void shouldHandleNullValuesGracefully() {
            // Given
            Row mockRow1 = Mockito.mock(Row.class);
            Row mockRow2 = Mockito.mock(Row.class);

            lenient().when(mockRow1.getString("status")).thenReturn("RUNNING");
            lenient().when(mockRow1.getString("created_by")).thenReturn(null); // null created_by

            lenient().when(mockRow2.getString("status")).thenReturn(null); // null status
            lenient().when(mockRow2.getString("created_by")).thenReturn("user1@example.com");

            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(iterator.hasNext()).thenReturn(true, true, false);
            when(iterator.next()).thenReturn(mockRow1, mockRow2);
            when(rowSet.iterator()).thenReturn(iterator);

            // Mock forEach to actually iterate
            doAnswer(invocation -> {
                java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
                consumer.accept(mockRow1);
                consumer.accept(mockRow2);
                return null;
            }).when(rowSet).forEach(any());

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_FILTER_OPTIONS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.getInteractionFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatuses().size()).isEqualTo(1); // Only RUNNING
            assertThat(result.getCreatedBy().size()).isEqualTo(1); // Only user1
        }

        @Test
        void shouldPropagateErrorIfDatabaseCallFails() {
            // Given
            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
            RuntimeException expectedError = new RuntimeException("Database connection failed");

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_FILTER_OPTIONS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.error(expectedError));

            // When
            var testObserver = interactionDao.getInteractionFilterOptions().test();

            // Then
            testObserver.assertError(RuntimeException.class);
            testObserver.assertError(throwable -> throwable.getMessage().equals("Database connection failed"));
        }

        @Test
        void shouldReturnSortedResults() {
            // Given
            Row mockRow1 = Mockito.mock(Row.class);
            Row mockRow2 = Mockito.mock(Row.class);
            Row mockRow3 = Mockito.mock(Row.class);

            lenient().when(mockRow1.getString("status")).thenReturn("STOPPED");
            lenient().when(mockRow1.getString("created_by")).thenReturn("zebra@example.com");

            lenient().when(mockRow2.getString("status")).thenReturn("RUNNING");
            lenient().when(mockRow2.getString("created_by")).thenReturn("alpha@example.com");

            lenient().when(mockRow3.getString("status")).thenReturn("DELETED");
            lenient().when(mockRow3.getString("created_by")).thenReturn("beta@example.com");

            RowIterator<Row> iterator = Mockito.mock(RowIterator.class);
            when(iterator.hasNext()).thenReturn(true, true, true, false);
            when(iterator.next()).thenReturn(mockRow1, mockRow2, mockRow3);
            when(rowSet.iterator()).thenReturn(iterator);

            // Mock forEach to actually iterate
            doAnswer(invocation -> {
                java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
                consumer.accept(mockRow1);
                consumer.accept(mockRow2);
                consumer.accept(mockRow3);
                return null;
            }).when(rowSet).forEach(any());

            PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);

            when(mysqlClient.getReaderPool()).thenReturn(mySqlPool);
            when(mysqlClient.getReaderPool().preparedQuery(GET_INTERACTION_FILTER_OPTIONS))
                    .thenReturn(preparedQuery);
            when(preparedQuery.rxExecute())
                    .thenReturn(Single.just(rowSet));

            // When
            var result = interactionDao.getInteractionFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatuses().size()).isEqualTo(3);
            assertThat(result.getCreatedBy().size()).isEqualTo(3);
            // Verify results are sorted
            assertThat(result.getStatuses().get(0)).isEqualTo("DELETED");
            assertThat(result.getStatuses().get(1)).isEqualTo("RUNNING");
            assertThat(result.getStatuses().get(2)).isEqualTo("STOPPED");

            assertThat(result.getCreatedBy().get(0)).isEqualTo("alpha@example.com");
            assertThat(result.getCreatedBy().get(1)).isEqualTo("beta@example.com");
            assertThat(result.getCreatedBy().get(2)).isEqualTo("zebra@example.com");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    public class TestGetTelemetryFilterOptions {

        @Test
        void shouldReturnTelemetryFilterOptionsSuccessfully() {
            // Given - Create mock response data
            GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(
                List.of(
                    new GetRawUserEventsResponseDto.Field("appVersionCodes"),
                    new GetRawUserEventsResponseDto.Field("deviceModels"),
                    new GetRawUserEventsResponseDto.Field("networkProviders"),
                    new GetRawUserEventsResponseDto.Field("states"),
                    new GetRawUserEventsResponseDto.Field("osVersions"),
                    new GetRawUserEventsResponseDto.Field("platforms")
                )
            );

            List<String> appVersions = List.of("1.0.0", "1.1.0", "1.2.0");
            List<String> deviceModels = List.of("iPhone 14", "OnePlus 11", "Samsung Galaxy S23");
            List<String> networkProviders = List.of("Airtel", "Jio", "Vodafone");
            List<String> states = List.of("IN-DL", "IN-KA", "IN-MH");
            List<String> osVersions = List.of("Android 13", "Android 14", "iOS 16.5");
            List<String> platforms = List.of("Android", "iOS");

            GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row(
                List.of(
                    new GetRawUserEventsResponseDto.RowField(appVersions),
                    new GetRawUserEventsResponseDto.RowField(deviceModels),
                    new GetRawUserEventsResponseDto.RowField(networkProviders),
                    new GetRawUserEventsResponseDto.RowField(states),
                    new GetRawUserEventsResponseDto.RowField(osVersions),
                    new GetRawUserEventsResponseDto.RowField(platforms)
                )
            );

            GetRawUserEventsResponseDto data = GetRawUserEventsResponseDto.builder()
                .schema(schema)
                .rows(List.of(row))
                .build();
            GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
                GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                .jobComplete(true)
                .data(data)
                .build();

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                .thenReturn(Single.just(response));

            // When
            var result = interactionDao.getTelemetryFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAppVersionCodes()).isEqualTo(appVersions);
            assertThat(result.getDeviceModels()).isEqualTo(deviceModels);
            assertThat(result.getNetworkProviders()).isEqualTo(networkProviders);
            assertThat(result.getStates()).isEqualTo(states);
            assertThat(result.getOsVersions()).isEqualTo(osVersions);
            assertThat(result.getPlatforms()).isEqualTo(platforms);

            verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(any(QueryConfiguration.class));
        }

        @Test
        void shouldReturnEmptyListsWhenNoDataExists() {
            // Given - Empty response
            GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(
                List.of(
                    new GetRawUserEventsResponseDto.Field("appVersionCodes"),
                    new GetRawUserEventsResponseDto.Field("deviceModels"),
                    new GetRawUserEventsResponseDto.Field("networkProviders"),
                    new GetRawUserEventsResponseDto.Field("states"),
                    new GetRawUserEventsResponseDto.Field("osVersions"),
                    new GetRawUserEventsResponseDto.Field("platforms")
                )
            );

            GetRawUserEventsResponseDto data = GetRawUserEventsResponseDto.builder()
                .schema(schema)
                .rows(new ArrayList<>())
                .build();
            GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
                GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                .jobComplete(true)
                .data(data)
                .build();

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                .thenReturn(Single.just(response));

            // When
            var result = interactionDao.getTelemetryFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAppVersionCodes()).isNotNull();
            assertThat(result.getDeviceModels()).isNotNull();
            assertThat(result.getNetworkProviders()).isNotNull();
            assertThat(result.getStates()).isNotNull();
            assertThat(result.getOsVersions()).isNotNull();
            assertThat(result.getPlatforms()).isNotNull();

            verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(any(QueryConfiguration.class));
        }

        @Test
        void shouldHandleNullValuesGracefully() {
            // Given - Response with some null values
            GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(
                List.of(
                    new GetRawUserEventsResponseDto.Field("appVersionCodes"),
                    new GetRawUserEventsResponseDto.Field("deviceModels"),
                    new GetRawUserEventsResponseDto.Field("networkProviders"),
                    new GetRawUserEventsResponseDto.Field("states"),
                    new GetRawUserEventsResponseDto.Field("osVersions"),
                    new GetRawUserEventsResponseDto.Field("platforms")
                )
            );

            List<String> appVersions = List.of("1.0.0");
            List<String> platforms = List.of("Android");

            GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row(
                List.of(
                    new GetRawUserEventsResponseDto.RowField(appVersions),
                    new GetRawUserEventsResponseDto.RowField(null),
                    new GetRawUserEventsResponseDto.RowField(null),
                    new GetRawUserEventsResponseDto.RowField(null),
                    new GetRawUserEventsResponseDto.RowField(null),
                    new GetRawUserEventsResponseDto.RowField(platforms)
                )
            );

            GetRawUserEventsResponseDto data = GetRawUserEventsResponseDto.builder()
                .schema(schema)
                .rows(List.of(row))
                .build();
            GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
                GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                .jobComplete(true)
                .data(data)
                .build();

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                .thenReturn(Single.just(response));

            // When
            var result = interactionDao.getTelemetryFilterOptions().blockingGet();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAppVersionCodes()).isEqualTo(appVersions);
            assertThat(result.getPlatforms()).isEqualTo(platforms);
            assertThat(result.getDeviceModels()).isNotNull();
            assertThat(result.getNetworkProviders()).isNotNull();
            assertThat(result.getStates()).isNotNull();
            assertThat(result.getOsVersions()).isNotNull();

            verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(any(QueryConfiguration.class));
        }

        @Test
        void shouldPropagateErrorIfClickHouseQueryFails() {
            // Given
            RuntimeException expectedError = new RuntimeException("ClickHouse connection failed");

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                .thenReturn(Single.error(expectedError));

            // When
            var testObserver = interactionDao.getTelemetryFilterOptions().test();

            // Then
            testObserver.assertError(RuntimeException.class);
            testObserver.assertError(throwable -> throwable.getMessage().equals("ClickHouse connection failed"));

            verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(any(QueryConfiguration.class));
        }

        @Test
        void shouldReturnErrorWhenJobNotComplete() {
            // Given - Job not complete
            GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(List.of());
            GetRawUserEventsResponseDto data = GetRawUserEventsResponseDto.builder()
                .schema(schema)
                .rows(new ArrayList<>())
                .build();
            GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
                GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                .jobComplete(false)
                .data(data)
                .build();

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                .thenReturn(Single.just(response));

            // When
            var testObserver = interactionDao.getTelemetryFilterOptions().test();

            // Then
            testObserver.assertError(RuntimeException.class);
            testObserver.assertError(throwable -> throwable.getMessage().equals("Failed to fetch telemetry filter data"));

            verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(any(QueryConfiguration.class));
        }

        @Test
        void shouldUseCorrectQueryConfiguration() {
            // Given
            ArgumentCaptor<QueryConfiguration> configCaptor = ArgumentCaptor.forClass(QueryConfiguration.class);
            
            GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(List.of());
            GetRawUserEventsResponseDto data = GetRawUserEventsResponseDto.builder()
                .schema(schema)
                .rows(new ArrayList<>())
                .build();
            GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
                GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                .jobComplete(true)
                .data(data)
                .build();

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                .thenReturn(Single.just(response));

            // When
            interactionDao.getTelemetryFilterOptions().blockingGet();

            // Then
            verify(clickhouseQueryService, times(1)).executeQueryOrCreateJob(configCaptor.capture());
            
            QueryConfiguration capturedConfig = configCaptor.getValue();
            assertThat(capturedConfig).isNotNull();
            assertThat(capturedConfig.getQuery()).isEqualTo(GET_TELEMETRY_FILTER_VALUES);
        }
    }
}