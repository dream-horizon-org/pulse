package org.dreamhorizon.pulseserver.service.interaction.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.dreamhorizon.pulseserver.dao.interaction.InteractionDao;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionDaoResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.DeleteInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionStatus;
import org.dreamhorizon.pulseserver.service.interaction.models.UpdateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetailUploadMetadata;
import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InteractionServiceImplTest {
        InteractionServiceImpl interactionService;

        @Mock
        InteractionDao interactionDao;

        @BeforeEach
        void setUp() {
                interactionService = new InteractionServiceImpl(interactionDao);
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestCreateInteraction {

                final String interactionName = "TestInteraction";
                final String description = "TestDescription";
                final Integer interactionThreshold = 1000;
                final String user = "user@example.com";
                final Integer uptimeLowerLimit = 10;
                final Integer uptimeMidLimit = 20;
                final Integer uptimeUpperLimit = 30;
                final List<Event> globalBlacklistedEvents = List.of();
                final List<Event> eventSequence = List.of(
                                Event.builder()
                                                .name("TestEvent1")
                                                .props(List.of(
                                                                Event.Prop
                                                                                .builder()
                                                                                .name("TestProp1")
                                                                                .value("TestPropValue")
                                                                                .build()))
                                                .build(),
                                Event.builder()
                                                .name("TestEvent2")
                                                .build());

                @Test
                void shouldThrowExceptionIfInteractionAlreadyPresent() {
                        CreateInteractionRequest request = buildCreateRequest();

                        Mockito.when(interactionDao.isInteractionPresent(interactionName))
                                        .thenReturn(Single.just(true));

                        TestObserver<InteractionDetails> expectedValue = interactionService.createInteraction(request)
                                        .test();
                        expectedValue.assertError(IllegalArgumentException.class);

                        Mockito.verify(interactionDao, Mockito.times(1)).isInteractionPresent(interactionName);
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldThrowExceptionIfInteractionPresentCheckFailed() {
                        CreateInteractionRequest request = buildCreateRequest();

                        Mockito.when(interactionDao.isInteractionPresent(interactionName))
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<InteractionDetails> expectedValue = interactionService.createInteraction(request)
                                        .test();
                        expectedValue.assertError(RuntimeException.class);

                        Mockito.verify(interactionDao, Mockito.times(1)).isInteractionPresent(interactionName);
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldThrowExceptionIfCreateInteractionFailed() {
                        CreateInteractionRequest request = buildCreateRequest();

                        Mockito.when(interactionDao.isInteractionPresent(interactionName))
                                        .thenReturn(Single.just(false));
                        Mockito.when(interactionDao.createInteractionAndUploadMetadata(any()))
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<InteractionDetails> expectedValue = interactionService.createInteraction(request)
                                        .test();
                        expectedValue.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1)).isInteractionPresent(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1)).createInteractionAndUploadMetadata(any());
                        verifyNoMoreInteractions(interactionDao);
                }

                @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
                @Test
                void shouldNotThrowErrorIfUploadInteractionDetailsFails() {
                        CreateInteractionRequest request = buildCreateRequest();

                        ArgumentCaptor<InteractionDetails> interactionDetailsArgumentCaptor = ArgumentCaptor
                                        .forClass(InteractionDetails.class);
                        CreateInteractionDaoResponse expectedDaoResponse = Mockito
                                        .mock(CreateInteractionDaoResponse.class);
                        InteractionDetails expectedInteraction = InteractionDetails
                                        .builder()
                                        .name(interactionName)
                                        .description(description)
                                        .thresholdInMs(interactionThreshold)
                                        .createdBy(user)
                                        .updatedBy(user)
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .uptimeUpperLimitInMs(uptimeUpperLimit)
                                        .uptimeLowerLimitInMs(uptimeLowerLimit)
                                        .uptimeMidLimitInMs(uptimeMidLimit)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .build();

                        Mockito.when(expectedDaoResponse.getInteractionDetails()).thenReturn(expectedInteraction);
                        Mockito.when(interactionDao.isInteractionPresent(interactionName))
                                        .thenReturn(Single.just(false));
                        Mockito.when(interactionDao
                                        .createInteractionAndUploadMetadata(interactionDetailsArgumentCaptor.capture()))
                                        .thenReturn(Single.just(expectedDaoResponse));

                        TestObserver<InteractionDetails> actual = interactionService.createInteraction(request).test();
                        Mockito.verify(interactionDao, Mockito.times(1)).isInteractionPresent(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1)).createInteractionAndUploadMetadata(any());
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getName(), interactionName);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getDescription(), description);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getThresholdInMs(),
                                        interactionThreshold);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUptimeLowerLimitInMs(),
                                        uptimeLowerLimit);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUptimeMidLimitInMs(),
                                        uptimeMidLimit);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUptimeUpperLimitInMs(),
                                        uptimeUpperLimit);
                        assertEquals(InteractionStatus.RUNNING,
                                        interactionDetailsArgumentCaptor.getValue().getStatus());
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getCreatedBy(), user);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUpdatedBy(), user);
                        assertEquals(0, interactionDetailsArgumentCaptor.getValue().getGlobalBlacklistedEvents()
                                        .size());
                        assertEquals(2, interactionDetailsArgumentCaptor.getValue().getEvents().size());
                        assertThat(interactionDetailsArgumentCaptor.getValue().getEvents().get(0))
                                        .usingRecursiveComparison()
                                        .isEqualTo(eventSequence.get(0));
                        assertThat(interactionDetailsArgumentCaptor.getValue().getEvents().get(1))
                                        .usingRecursiveComparison()
                                        .isEqualTo(eventSequence.get(1));
                        actual.assertValue(interaction -> {
                                try {
                                        assertThat(interaction).usingRecursiveComparison()
                                                        .isEqualTo(expectedInteraction);
                                        return true;
                                } catch (Exception e) {
                                        return false;
                                }
                        });

                        verifyNoMoreInteractions(interactionDao);
                }

                private CreateInteractionRequest buildCreateRequest() {
                        return CreateInteractionRequest
                                        .builder()
                                        .name(interactionName)
                                        .description(description)
                                        .thresholdInMs(interactionThreshold)
                                        .user(user)
                                        .uptimeUpperLimitInMs(uptimeUpperLimit)
                                        .uptimeLowerLimitInMs(uptimeLowerLimit)
                                        .uptimeMidLimitInMs(uptimeMidLimit)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .build();
                }

                @Test
                void shouldSuccessfullyCreateAndUploadInteraction() {
                        CreateInteractionRequest request = buildCreateRequest();

                        ArgumentCaptor<InteractionDetails> interactionDetailsArgumentCaptor = ArgumentCaptor
                                        .forClass(InteractionDetails.class);
                        CreateInteractionDaoResponse expectedDaoResponse = Mockito
                                        .mock(CreateInteractionDaoResponse.class);
                        InteractionDetails expectedInteraction = InteractionDetails
                                        .builder()
                                        .name(interactionName)
                                        .description(description)
                                        .thresholdInMs(interactionThreshold)
                                        .createdBy(user)
                                        .updatedBy(user)
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .uptimeUpperLimitInMs(uptimeUpperLimit)
                                        .uptimeLowerLimitInMs(uptimeLowerLimit)
                                        .uptimeMidLimitInMs(uptimeMidLimit)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .build();

                        Mockito.when(expectedDaoResponse.getInteractionDetails()).thenReturn(expectedInteraction);
                        Mockito.when(interactionDao.isInteractionPresent(interactionName))
                                        .thenReturn(Single.just(false));
                        Mockito.when(interactionDao
                                        .createInteractionAndUploadMetadata(interactionDetailsArgumentCaptor.capture()))
                                        .thenReturn(Single.just(expectedDaoResponse));

                        TestObserver<InteractionDetails> expectedValue = interactionService.createInteraction(request)
                                        .test();

                        Mockito.verify(interactionDao, Mockito.times(1)).isInteractionPresent(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1)).createInteractionAndUploadMetadata(any());
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getName(), interactionName);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getDescription(), description);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getThresholdInMs(),
                                        interactionThreshold);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUptimeLowerLimitInMs(),
                                        uptimeLowerLimit);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUptimeMidLimitInMs(),
                                        uptimeMidLimit);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUptimeUpperLimitInMs(),
                                        uptimeUpperLimit);
                        assertEquals(InteractionStatus.RUNNING,
                                        interactionDetailsArgumentCaptor.getValue().getStatus());
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getCreatedBy(), user);
                        assertEquals(interactionDetailsArgumentCaptor.getValue().getUpdatedBy(), user);
                        assertEquals(0, interactionDetailsArgumentCaptor.getValue().getGlobalBlacklistedEvents()
                                        .size());
                        assertEquals(2, interactionDetailsArgumentCaptor.getValue().getEvents().size());
                        assertThat(interactionDetailsArgumentCaptor.getValue().getEvents().get(0))
                                        .usingRecursiveComparison()
                                        .isEqualTo(eventSequence.get(0));
                        assertThat(interactionDetailsArgumentCaptor.getValue().getEvents().get(1))
                                        .usingRecursiveComparison()
                                        .isEqualTo(eventSequence.get(1));
                        expectedValue.assertValue(actual -> {
                                try {
                                        assertThat(actual).usingRecursiveComparison().isEqualTo(expectedInteraction);
                                        return true;
                                } catch (Exception e) {
                                        return false;
                                }
                        });

                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestGetInteraction {

                final String interactionName = "TestInteraction";
                final String description = "TestDescription";
                final Integer interactionThreshold = 1000;
                final String user = "user@example.com";
                final Integer uptimeLowerLimit = 10;
                final Integer uptimeMidLimit = 20;
                final Integer uptimeUpperLimit = 30;
                final List<Event> globalBlacklistedEvents = List.of();
                final List<Event> eventSequence = List.of(
                                Event.builder()
                                                .name("TestEvent1")
                                                .props(List.of(
                                                                Event.Prop
                                                                                .builder()
                                                                                .name("TestProp1")
                                                                                .value("TestPropValue")
                                                                                .build()))
                                                .build(),
                                Event.builder()
                                                .name("TestEvent2")
                                                .build());

                @Test
                void shouldThrowExceptionIfGetInteractionFromDaoFailed() {
                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<InteractionDetails> actual = interactionService
                                        .getInteractionDetails(interactionName).test();

                        actual.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldSuccessfullyGetInteraction() {
                        InteractionDetails expectedInteraction = InteractionDetails
                                        .builder()
                                        .name(interactionName)
                                        .description(description)
                                        .thresholdInMs(interactionThreshold)
                                        .createdBy(user)
                                        .updatedBy(user)
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .uptimeUpperLimitInMs(uptimeUpperLimit)
                                        .uptimeLowerLimitInMs(uptimeLowerLimit)
                                        .uptimeMidLimitInMs(uptimeMidLimit)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .build();

                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.just(expectedInteraction));

                        TestObserver<InteractionDetails> actual = interactionService
                                        .getInteractionDetails(interactionName).test();

                        actual.assertNoErrors()
                                        .assertValue(interaction -> {
                                                try {
                                                        assertThat(interaction).usingRecursiveComparison()
                                                                        .isEqualTo(expectedInteraction);
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestDeleteInteraction {

                final String interactionName = "interactionName";
                final String user = "user";

                @Test
                void shouldThrowExceptionIfDeleteInteractionFromDaoFailed() {
                        DeleteInteractionRequest request = buildDeleteRequest();
                        ArgumentCaptor<DeleteInteractionRequest> requestCaptor = ArgumentCaptor
                                        .forClass(DeleteInteractionRequest.class);

                        Mockito.when(interactionDao.deleteInteractionAndCreateUploadMetadata(requestCaptor.capture()))
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<EmptyResponse> actual = interactionService.deleteInteraction(request).test();

                        actual.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .deleteInteractionAndCreateUploadMetadata(any());
                        assertThat(requestCaptor.getValue()).usingRecursiveComparison().isEqualTo(request);

                        verifyNoMoreInteractions(interactionDao);
                }

                private DeleteInteractionRequest buildDeleteRequest() {
                        return DeleteInteractionRequest
                                        .builder()
                                        .name(interactionName)
                                        .userEmail(user)
                                        .build();
                }

                @Test
                void shouldNotThrowExceptionIfUploadInteractionFailed() {
                        DeleteInteractionRequest request = buildDeleteRequest();
                        ArgumentCaptor<DeleteInteractionRequest> deleteRequestCaptor = ArgumentCaptor
                                        .forClass(DeleteInteractionRequest.class);
                        InteractionDetailUploadMetadata expectedUploadMetadata = InteractionDetailUploadMetadata
                                        .builder()
                                        .id(1L)
                                        .interactionId(10L)
                                        .status(InteractionDetailUploadMetadata.Status.PENDING)
                                        .build();
                        Mockito.when(interactionDao
                                        .deleteInteractionAndCreateUploadMetadata(deleteRequestCaptor.capture()))
                                        .thenReturn(Single.just(expectedUploadMetadata));

                        TestObserver<EmptyResponse> result = interactionService.deleteInteraction(request).test();

                        result.assertNoErrors();
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .deleteInteractionAndCreateUploadMetadata(any());
                        assertThat(deleteRequestCaptor.getValue()).usingRecursiveComparison().isEqualTo(request);
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldSuccessfullyDeleteAndUploadInteraction() {
                        DeleteInteractionRequest request = buildDeleteRequest();
                        ArgumentCaptor<DeleteInteractionRequest> deleteRequestCaptor = ArgumentCaptor
                                        .forClass(DeleteInteractionRequest.class);
                        InteractionDetailUploadMetadata expectedUploadMetadata = InteractionDetailUploadMetadata
                                        .builder()
                                        .id(1L)
                                        .interactionId(10L)
                                        .status(InteractionDetailUploadMetadata.Status.PENDING)
                                        .build();
                        Mockito.when(interactionDao
                                        .deleteInteractionAndCreateUploadMetadata(deleteRequestCaptor.capture()))
                                        .thenReturn(Single.just(expectedUploadMetadata));

                        TestObserver<EmptyResponse> result = interactionService.deleteInteraction(request).test();

                        result.assertNoErrors();
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .deleteInteractionAndCreateUploadMetadata(any());
                        assertThat(deleteRequestCaptor.getValue()).usingRecursiveComparison().isEqualTo(request);
                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestUpdateInteraction {

                final String interactionName = "TestInteraction";
                final String description = "TestDescription";
                final Integer interactionThreshold = 1000;
                final String user = "user@example.com";
                final Integer uptimeLowerLimit = 10;
                final Integer uptimeMidLimit = 20;
                final Integer uptimeUpperLimit = 30;
                final List<Event> globalBlacklistedEvents = List.of();
                final List<Event> eventSequence = List.of(
                                Event.builder()
                                                .name("TestEvent1")
                                                .props(List.of(
                                                                Event.Prop
                                                                                .builder()
                                                                                .name("TestProp1")
                                                                                .value("TestPropValue")
                                                                                .build()))
                                                .build(),
                                Event.builder()
                                                .name("TestEvent2")
                                                .build());

                @Test
                void shouldThrowInteractionIfInteractionNotPresent() {
                        UpdateInteractionRequest request = UpdateInteractionRequest.builder()
                                        .name(interactionName)
                                        .description("Updated description")
                                        .user(user)
                                        .build();

                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.error(new RuntimeException("Interaction not found")));

                        TestObserver<EmptyResponse> actual = interactionService.updateInteraction(request).test();

                        actual.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldUpdateInteractionWhenAllFieldsAreProvided() {
                        UpdateInteractionRequest request = UpdateInteractionRequest.builder()
                                        .name(interactionName)
                                        .description("Updated description")
                                        .uptimeLowerLimitInMs(10)
                                        .uptimeMidLimitInMs(20)
                                        .uptimeUpperLimitInMs(30)
                                        .interactionThresholdInMS(5)
                                        .status(InteractionStatus.RUNNING)
                                        .events(List.of(
                                                        Event.builder()
                                                                        .name("event1")
                                                                        .build()))
                                        .globalBlacklistedEvents(List.of(
                                                        Event.builder()
                                                                        .name("event2")
                                                                        .build()))
                                        .user(user)
                                        .build();

                        InteractionDetails existingInteraction = InteractionDetails
                                        .builder()
                                        .id(1L)
                                        .name(interactionName)
                                        .description("Old description")
                                        .uptimeLowerLimitInMs(5)
                                        .uptimeMidLimitInMs(15)
                                        .uptimeUpperLimitInMs(25)
                                        .thresholdInMs(3)
                                        .status(InteractionStatus.STOPPED)
                                        .events(List.of(
                                                        Event.builder()
                                                                        .name("event1")
                                                                        .build()))
                                        .globalBlacklistedEvents(List.of(
                                                        Event.builder()
                                                                        .name("event3")
                                                                        .build()))
                                        .createdBy("user1")
                                        .updatedBy("user1")
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .build();

                        InteractionDetailUploadMetadata uploadMetadata = InteractionDetailUploadMetadata
                                        .builder()
                                        .id(1L)
                                        .interactionId(10L)
                                        .status(InteractionDetailUploadMetadata.Status.PENDING)
                                        .build();

                        ArgumentCaptor<InteractionDetails> interactionDetailsCaptor = ArgumentCaptor
                                        .forClass(InteractionDetails.class);

                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.just(existingInteraction));
                        Mockito.when(interactionDao
                                        .updateInteractionAndCreateUploadMetadata(interactionDetailsCaptor.capture()))
                                        .thenReturn(Single.just(uploadMetadata));

                        TestObserver<EmptyResponse> actual = interactionService.updateInteraction(request).test();

                        actual.assertNoErrors();
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .updateInteractionAndCreateUploadMetadata(any());

                        InteractionDetails capturedInteraction = interactionDetailsCaptor.getValue();
                        assertThat(capturedInteraction.getName()).isEqualTo(interactionName);
                        assertThat(capturedInteraction.getDescription()).isEqualTo("Updated description");
                        assertThat(capturedInteraction.getUptimeLowerLimitInMs()).isEqualTo(10);
                        assertThat(capturedInteraction.getUptimeMidLimitInMs()).isEqualTo(20);
                        assertThat(capturedInteraction.getUptimeUpperLimitInMs()).isEqualTo(30);
                        assertThat(capturedInteraction.getThresholdInMs()).isEqualTo(5);
                        assertThat(capturedInteraction.getStatus()).isEqualTo(InteractionStatus.RUNNING);
                        assertThat(capturedInteraction.getEvents()).hasSize(1);
                        assertThat(capturedInteraction.getEvents().get(0).getName()).isEqualTo("event1");
                        assertThat(capturedInteraction.getGlobalBlacklistedEvents()).hasSize(1);
                        assertThat(capturedInteraction.getGlobalBlacklistedEvents().get(0).getName())
                                        .isEqualTo("event2");
                        assertThat(capturedInteraction.getUpdatedBy()).isEqualTo(user);
                        assertThat(capturedInteraction.getCreatedBy()).isEqualTo("user1");

                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldNotUpdateInteractionWhenNoFieldsAreProvided() {
                        UpdateInteractionRequest request = UpdateInteractionRequest.builder()
                                        .name(interactionName)
                                        .user(user)
                                        .build();

                        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                        InteractionDetails existingInteraction = InteractionDetails
                                        .builder()
                                        .id(1L)
                                        .name(interactionName)
                                        .description("Old description")
                                        .uptimeLowerLimitInMs(5)
                                        .uptimeMidLimitInMs(15)
                                        .uptimeUpperLimitInMs(25)
                                        .thresholdInMs(3)
                                        .status(InteractionStatus.STOPPED)
                                        .createdBy("user1")
                                        .updatedBy("user1")
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .events(List.of(
                                                        Event.builder()
                                                                        .name("event1")
                                                                        .build()))
                                        .globalBlacklistedEvents(List.of(
                                                        Event.builder()
                                                                        .name("event3")
                                                                        .build()))
                                        .build();

                        InteractionDetailUploadMetadata uploadMetadata = InteractionDetailUploadMetadata
                                        .builder()
                                        .id(1L)
                                        .interactionId(10L)
                                        .status(InteractionDetailUploadMetadata.Status.PENDING)
                                        .build();

                        ArgumentCaptor<InteractionDetails> interactionDetailsCaptor = ArgumentCaptor
                                        .forClass(InteractionDetails.class);

                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.just(existingInteraction));
                        Mockito.when(interactionDao
                                        .updateInteractionAndCreateUploadMetadata(interactionDetailsCaptor.capture()))
                                        .thenReturn(Single.just(uploadMetadata));

                        TestObserver<EmptyResponse> actual = interactionService.updateInteraction(request).test();

                        actual.assertNoErrors();
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .updateInteractionAndCreateUploadMetadata(any());

                        InteractionDetails capturedInteraction = interactionDetailsCaptor.getValue();
                        assertThat(capturedInteraction.getName()).isEqualTo(interactionName);
                        assertThat(capturedInteraction.getDescription()).isEqualTo("Old description");
                        assertThat(capturedInteraction.getUptimeLowerLimitInMs()).isEqualTo(5);
                        assertThat(capturedInteraction.getUptimeMidLimitInMs()).isEqualTo(15);
                        assertThat(capturedInteraction.getUptimeUpperLimitInMs()).isEqualTo(25);
                        assertThat(capturedInteraction.getThresholdInMs()).isEqualTo(3);
                        assertThat(capturedInteraction.getStatus()).isEqualTo(InteractionStatus.STOPPED);
                        assertThat(capturedInteraction.getEvents()).hasSize(1);
                        assertThat(capturedInteraction.getEvents().get(0).getName()).isEqualTo("event1");
                        assertThat(capturedInteraction.getGlobalBlacklistedEvents()).hasSize(1);
                        assertThat(capturedInteraction.getGlobalBlacklistedEvents().get(0).getName())
                                        .isEqualTo("event3");
                        assertThat(capturedInteraction.getUpdatedBy()).isEqualTo(user);
                        assertThat(capturedInteraction.getCreatedBy()).isEqualTo("user1");
                        assertThat(capturedInteraction.getCreatedAt()).isEqualTo(now);

                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldUpdateInteractionWhenOnlyFewFieldsAreProvided() {
                        UpdateInteractionRequest request = UpdateInteractionRequest.builder()
                                        .name(interactionName)
                                        .description("New description")
                                        .events(List.of(
                                                        Event.builder()
                                                                        .name("event2")
                                                                        .build(),
                                                        Event.builder()
                                                                        .name("event3")
                                                                        .props(List.of(
                                                                                        Event.Prop
                                                                                                        .builder()
                                                                                                        .name("propName")
                                                                                                        .value("propValue")
                                                                                                        .build()))
                                                                        .build()))
                                        .status(InteractionStatus.RUNNING)
                                        .user(user)
                                        .build();

                        InteractionDetails existingInteraction = InteractionDetails
                                        .builder()
                                        .id(1L)
                                        .name(interactionName)
                                        .description("Old description")
                                        .uptimeLowerLimitInMs(5)
                                        .uptimeMidLimitInMs(15)
                                        .uptimeUpperLimitInMs(25)
                                        .thresholdInMs(3)
                                        .status(InteractionStatus.STOPPED)
                                        .createdBy("user1")
                                        .updatedBy("user1")
                                        .events(List.of(
                                                        Event.builder()
                                                                        .name("event1")
                                                                        .build()))
                                        .globalBlacklistedEvents(List.of(
                                                        Event.builder()
                                                                        .name("event3")
                                                                        .build()))
                                        .build();

                        InteractionDetailUploadMetadata uploadMetadata = InteractionDetailUploadMetadata
                                        .builder()
                                        .id(1L)
                                        .interactionId(10L)
                                        .status(InteractionDetailUploadMetadata.Status.PENDING)
                                        .build();

                        ArgumentCaptor<InteractionDetails> interactionDetailsCaptor = ArgumentCaptor
                                        .forClass(InteractionDetails.class);

                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.just(existingInteraction));
                        Mockito.when(interactionDao
                                        .updateInteractionAndCreateUploadMetadata(interactionDetailsCaptor.capture()))
                                        .thenReturn(Single.just(uploadMetadata));

                        TestObserver<EmptyResponse> actual = interactionService.updateInteraction(request).test();

                        actual.assertNoErrors();
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .updateInteractionAndCreateUploadMetadata(any());

                        InteractionDetails capturedInteraction = interactionDetailsCaptor.getValue();
                        assertThat(capturedInteraction.getName()).isEqualTo(interactionName);
                        assertThat(capturedInteraction.getDescription()).isEqualTo("New description");
                        assertThat(capturedInteraction.getStatus()).isEqualTo(InteractionStatus.RUNNING);
                        assertThat(capturedInteraction.getUptimeLowerLimitInMs()).isEqualTo(5);
                        assertThat(capturedInteraction.getUptimeMidLimitInMs()).isEqualTo(15);
                        assertThat(capturedInteraction.getUptimeUpperLimitInMs()).isEqualTo(25);
                        assertThat(capturedInteraction.getThresholdInMs()).isEqualTo(3);
                        assertThat(capturedInteraction.getEvents()).hasSize(2);
                        assertThat(capturedInteraction.getEvents().get(0).getName()).isEqualTo("event2");
                        assertThat(capturedInteraction.getEvents().get(1).getName()).isEqualTo("event3");
                        assertThat(capturedInteraction.getEvents().get(1).getProps()).hasSize(1);
                        assertThat(capturedInteraction.getEvents().get(1).getProps().get(0).getName())
                                        .isEqualTo("propName");
                        assertThat(capturedInteraction.getEvents().get(1).getProps().get(0).getValue())
                                        .isEqualTo("propValue");
                        assertThat(capturedInteraction.getGlobalBlacklistedEvents()).hasSize(1);
                        assertThat(capturedInteraction.getGlobalBlacklistedEvents().get(0).getName())
                                        .isEqualTo("event3");
                        assertThat(capturedInteraction.getUpdatedBy()).isEqualTo(user);
                        assertThat(capturedInteraction.getCreatedBy()).isEqualTo("user1");

                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldThrowExceptionWhenDaoFailsToUpdateInteraction() {
                        UpdateInteractionRequest request = UpdateInteractionRequest.builder()
                                        .name(interactionName)
                                        .description("Updated description")
                                        .user(user)
                                        .build();

                        InteractionDetails existingInteraction = InteractionDetails
                                        .builder()
                                        .name(interactionName)
                                        .description("Old description")
                                        .build();

                        Mockito.when(interactionDao.getInteractionDetails(interactionName))
                                        .thenReturn(Single.just(existingInteraction));
                        Mockito.when(interactionDao.updateInteractionAndCreateUploadMetadata(any()))
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<EmptyResponse> actual = interactionService.updateInteraction(request).test();

                        actual.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionDetails(interactionName);
                        Mockito.verify(interactionDao, Mockito.times(1))
                                        .updateInteractionAndCreateUploadMetadata(any());
                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestGetInteractions {

                final String interactionName = "TestInteraction";
                final String description = "TestDescription";
                final Integer interactionThreshold = 1000;
                final String user = "user@example.com";
                final Integer uptimeLowerLimit = 10;
                final Integer uptimeMidLimit = 20;
                final Integer uptimeUpperLimit = 30;
                final List<Event> globalBlacklistedEvents = List.of();
                final List<Event> eventSequence = List.of(
                                Event.builder()
                                                .name("TestEvent1")
                                                .props(List.of(
                                                                Event.Prop
                                                                                .builder()
                                                                                .name("TestProp1")
                                                                                .value("TestPropValue")
                                                                                .build()))
                                                .build(),
                                Event.builder()
                                                .name("TestEvent2")
                                                .build());

                @Test
                void shouldReturnInteractionsWhenDaoReturnsValidResponse() {
                        GetInteractionsRequest request = GetInteractionsRequest.builder()
                                        .page(0)
                                        .size(10)
                                        .userEmail(user)
                                        .name(interactionName)
                                        .build();

                        InteractionDetails interaction = InteractionDetails
                                        .builder()
                                        .name(interactionName)
                                        .description(description)
                                        .thresholdInMs(interactionThreshold)
                                        .createdBy(user)
                                        .updatedBy(user)
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .uptimeUpperLimitInMs(uptimeUpperLimit)
                                        .uptimeLowerLimitInMs(uptimeLowerLimit)
                                        .uptimeMidLimitInMs(uptimeMidLimit)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .build();

                        GetInteractionsResponse expectedResponse = GetInteractionsResponse.builder()
                                        .interactions(List.of(interaction))
                                        .totalInteractions(1)
                                        .build();

                        Mockito.when(interactionDao.getInteractions(request))
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<GetInteractionsResponse> actual = interactionService.getInteractions(request)
                                        .test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response).usingRecursiveComparison()
                                                                        .isEqualTo(expectedResponse);
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractions(request);
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldThrowExceptionWhenDaoFailsToGetInteractions() {
                        GetInteractionsRequest request = GetInteractionsRequest.builder()
                                        .page(0)
                                        .size(10)
                                        .userEmail(user)
                                        .name(interactionName)
                                        .build();

                        Mockito.when(interactionDao.getInteractions(request))
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<GetInteractionsResponse> actual = interactionService.getInteractions(request)
                                        .test();

                        actual.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractions(request);
                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestGetAllActiveAndRunningInteractions {

                final String interactionName = "TestInteraction";
                final String description = "TestDescription";
                final Integer interactionThreshold = 1000;
                final String user = "user@example.com";
                final Integer uptimeLowerLimit = 10;
                final Integer uptimeMidLimit = 20;
                final Integer uptimeUpperLimit = 30;
                final List<Event> globalBlacklistedEvents = List.of();
                final List<Event> eventSequence = List.of(
                                Event.builder()
                                                .name("TestEvent1")
                                                .props(List.of(
                                                                Event.Prop
                                                                                .builder()
                                                                                .name("TestProp1")
                                                                                .value("TestPropValue")
                                                                                .build()))
                                                .build(),
                                Event.builder()
                                                .name("TestEvent2")
                                                .build());

                @Test
                void shouldThrowExceptionWhenDaoFailsToGetActiveAndRunningInteractions() {
                        Mockito.when(interactionDao.getAllActiveAndRunningInteractions())
                                        .thenReturn(Single.error(new RuntimeException("Database error")));

                        TestObserver<List<InteractionDetails>> actual = interactionService
                                        .getAllActiveAndRunningInteractions().test();

                        actual.assertError(RuntimeException.class);
                        Mockito.verify(interactionDao, Mockito.times(1)).getAllActiveAndRunningInteractions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldSuccessfullyGetAllActiveAndRunningInteractions() {
                        InteractionDetails interaction1 = InteractionDetails
                                        .builder()
                                        .name(interactionName)
                                        .description(description)
                                        .thresholdInMs(interactionThreshold)
                                        .createdBy(user)
                                        .updatedBy(user)
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .uptimeUpperLimitInMs(uptimeUpperLimit)
                                        .uptimeLowerLimitInMs(uptimeLowerLimit)
                                        .uptimeMidLimitInMs(uptimeMidLimit)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .status(InteractionStatus.RUNNING)
                                        .build();

                        InteractionDetails interaction2 = InteractionDetails
                                        .builder()
                                        .name("TestInteraction2")
                                        .description("TestDescription2")
                                        .thresholdInMs(2000)
                                        .createdBy(user)
                                        .updatedBy(user)
                                        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                                        .uptimeUpperLimitInMs(40)
                                        .uptimeLowerLimitInMs(20)
                                        .uptimeMidLimitInMs(30)
                                        .events(eventSequence)
                                        .globalBlacklistedEvents(globalBlacklistedEvents)
                                        .status(InteractionStatus.RUNNING)
                                        .build();

                        List<InteractionDetails> expectedInteractions = List.of(interaction1, interaction2);

                        Mockito.when(interactionDao.getAllActiveAndRunningInteractions())
                                        .thenReturn(Single.just(expectedInteractions));

                        TestObserver<List<InteractionDetails>> actual = interactionService
                                        .getAllActiveAndRunningInteractions().test();

                        actual.assertNoErrors()
                                        .assertValue(interactions -> {
                                                try {
                                                        assertThat(interactions).usingRecursiveComparison()
                                                                        .isEqualTo(expectedInteractions);
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });
                        Mockito.verify(interactionDao, Mockito.times(1)).getAllActiveAndRunningInteractions();
                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestGetInteractionFilterOptions {

                @Test
                void shouldReturnFilterOptionsSuccessfully() {
                        List<String> statuses = List.of("RUNNING", "STOPPED");
                        List<String> createdByUsers = List.of("user1@example.com", "user2@example.com",
                                        "user3@example.com");

                        InteractionFilterOptionsResponse expectedResponse = InteractionFilterOptionsResponse.builder()
                                        .statuses(statuses)
                                        .createdBy(createdByUsers)
                                        .build();

                        Mockito.when(interactionDao.getInteractionFilterOptions())
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<InteractionFilterOptionsResponse> actual = interactionService
                                        .getInteractionFilterOptions().test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response).usingRecursiveComparison()
                                                                        .isEqualTo(expectedResponse);
                                                        assertThat(response.getStatuses()).hasSize(2);
                                                        assertThat(response.getCreatedBy()).hasSize(3);
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });

                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldReturnEmptyListsWhenNoDataExists() {
                        InteractionFilterOptionsResponse expectedResponse = InteractionFilterOptionsResponse.builder()
                                        .statuses(List.of())
                                        .createdBy(List.of())
                                        .build();

                        Mockito.when(interactionDao.getInteractionFilterOptions())
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<InteractionFilterOptionsResponse> actual = interactionService
                                        .getInteractionFilterOptions().test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response.getStatuses()).isEmpty();
                                                        assertThat(response.getCreatedBy()).isEmpty();
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });

                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldHandleErrorWhenDaoFails() {
                        RuntimeException expectedException = new RuntimeException("Database error");

                        Mockito.when(interactionDao.getInteractionFilterOptions())
                                        .thenReturn(Single.error(expectedException));

                        TestObserver<InteractionFilterOptionsResponse> actual = interactionService
                                        .getInteractionFilterOptions().test();

                        actual.assertError(RuntimeException.class)
                                        .assertError(throwable -> throwable.getMessage().equals("Database error"));

                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldReturnOnlyStatusesWhenNoCreatedByExists() {
                        List<String> statuses = List.of("RUNNING");

                        InteractionFilterOptionsResponse expectedResponse = InteractionFilterOptionsResponse.builder()
                                        .statuses(statuses)
                                        .createdBy(List.of())
                                        .build();

                        Mockito.when(interactionDao.getInteractionFilterOptions())
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<InteractionFilterOptionsResponse> actual = interactionService
                                        .getInteractionFilterOptions().test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response.getStatuses()).hasSize(1);
                                                        assertThat(response.getCreatedBy()).isEmpty();
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });

                        Mockito.verify(interactionDao, Mockito.times(1)).getInteractionFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class TestGetTelemetryFilterOptions {

                @Test
                void shouldReturnTelemetryFilterOptionsSuccessfully() {
                        List<String> appVersions = List.of("1.0.0", "1.1.0", "1.2.0");
                        List<String> deviceModels = List.of("iPhone 14", "OnePlus 11", "Samsung Galaxy S23");
                        List<String> networkProviders = List.of("Airtel", "Jio", "Vodafone");
                        List<String> platforms = List.of("Android", "iOS");
                        List<String> osVersions = List.of("Android 13", "Android 14", "iOS 16.5");
                        List<String> states = List.of("IN-DL", "IN-KA", "IN-MH");

                        TelemetryFilterOptionsResponse expectedResponse = TelemetryFilterOptionsResponse.builder()
                                        .appVersionCodes(appVersions)
                                        .deviceModels(deviceModels)
                                        .networkProviders(networkProviders)
                                        .platforms(platforms)
                                        .osVersions(osVersions)
                                        .states(states)
                                        .build();

                        Mockito.when(interactionDao.getTelemetryFilterOptions())
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<TelemetryFilterOptionsResponse> actual = interactionService
                                        .getTelemetryFilterOptions().test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response).usingRecursiveComparison()
                                                                        .isEqualTo(expectedResponse);
                                                        assertThat(response.getAppVersionCodes()).hasSize(3);
                                                        assertThat(response.getDeviceModels()).hasSize(3);
                                                        assertThat(response.getNetworkProviders()).hasSize(3);
                                                        assertThat(response.getPlatforms()).hasSize(2);
                                                        assertThat(response.getOsVersions()).hasSize(3);
                                                        assertThat(response.getStates()).hasSize(3);
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });

                        Mockito.verify(interactionDao, Mockito.times(1)).getTelemetryFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldReturnEmptyListsWhenNoTelemetryDataExists() {
                        TelemetryFilterOptionsResponse expectedResponse = TelemetryFilterOptionsResponse.builder()
                                        .appVersionCodes(List.of())
                                        .deviceModels(List.of())
                                        .networkProviders(List.of())
                                        .platforms(List.of())
                                        .osVersions(List.of())
                                        .states(List.of())
                                        .build();

                        Mockito.when(interactionDao.getTelemetryFilterOptions())
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<TelemetryFilterOptionsResponse> actual = interactionService
                                        .getTelemetryFilterOptions().test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response.getAppVersionCodes()).isEmpty();
                                                        assertThat(response.getDeviceModels()).isEmpty();
                                                        assertThat(response.getNetworkProviders()).isEmpty();
                                                        assertThat(response.getPlatforms()).isEmpty();
                                                        assertThat(response.getOsVersions()).isEmpty();
                                                        assertThat(response.getStates()).isEmpty();
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });

                        Mockito.verify(interactionDao, Mockito.times(1)).getTelemetryFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldHandleErrorWhenDaoFails() {
                        RuntimeException expectedException = new RuntimeException("ClickHouse query failed");

                        Mockito.when(interactionDao.getTelemetryFilterOptions())
                                        .thenReturn(Single.error(expectedException));

                        TestObserver<TelemetryFilterOptionsResponse> actual = interactionService
                                        .getTelemetryFilterOptions().test();

                        actual.assertError(RuntimeException.class)
                                        .assertError(throwable -> throwable.getMessage()
                                                        .equals("ClickHouse query failed"));

                        Mockito.verify(interactionDao, Mockito.times(1)).getTelemetryFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }

                @Test
                void shouldReturnPartialDataWhenSomeFieldsAreEmpty() {
                        List<String> appVersions = List.of("1.0.0");
                        List<String> platforms = List.of("Android");

                        TelemetryFilterOptionsResponse expectedResponse = TelemetryFilterOptionsResponse.builder()
                                        .appVersionCodes(appVersions)
                                        .deviceModels(List.of())
                                        .networkProviders(List.of())
                                        .platforms(platforms)
                                        .osVersions(List.of())
                                        .states(List.of())
                                        .build();

                        Mockito.when(interactionDao.getTelemetryFilterOptions())
                                        .thenReturn(Single.just(expectedResponse));

                        TestObserver<TelemetryFilterOptionsResponse> actual = interactionService
                                        .getTelemetryFilterOptions().test();

                        actual.assertNoErrors()
                                        .assertValue(response -> {
                                                try {
                                                        assertThat(response.getAppVersionCodes()).hasSize(1);
                                                        assertThat(response.getPlatforms()).hasSize(1);
                                                        assertThat(response.getDeviceModels()).isEmpty();
                                                        assertThat(response.getNetworkProviders()).isEmpty();
                                                        return true;
                                                } catch (Exception e) {
                                                        return false;
                                                }
                                        });

                        Mockito.verify(interactionDao, Mockito.times(1)).getTelemetryFilterOptions();
                        verifyNoMoreInteractions(interactionDao);
                }
        }

}