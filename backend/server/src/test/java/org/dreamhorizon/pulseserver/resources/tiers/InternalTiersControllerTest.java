package org.dreamhorizon.pulseserver.resources.tiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.dreamhorizon.pulseserver.resources.tiers.models.CreateTierRestRequest;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierListRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.UpdateTierRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.tier.models.TierInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTiersControllerTest {

  @Mock
  private TierService tierService;

  private InternalTiersController controller;

  private static TierInfo buildTierInfo(int id, String name, boolean active) {
    return TierInfo.builder()
        .tierId(id)
        .name(name)
        .displayName(name + " Display")
        .isCustomLimitsAllowed(false)
        .usageLimitDefaults(Collections.emptyMap())
        .isActive(active)
        .createdAt(Instant.now())
        .build();
  }

  @BeforeEach
  void setUp() {
    controller = new InternalTiersController(tierService);
  }

  // ==================== createTier ====================

  @Nested
  class CreateTier {

    @Test
    void shouldReturnCreatedTierOnSuccess() throws Exception {
      TierInfo info = buildTierInfo(1, "free", true);
      when(tierService.createTier(any())).thenReturn(Single.just(info));

      Response<TierRestResponse> response =
          controller.createTier(new CreateTierRestRequest()).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTierId()).isEqualTo(1);
      assertThat(response.getData().getName()).isEqualTo("free");
      assertThat(response.getData().getIsActive()).isTrue();
    }

    @Test
    void shouldPropagateErrorWhenServiceFails() {
      when(tierService.createTier(any()))
          .thenReturn(Single.error(new IllegalArgumentException("Tier name already exists")));

      try {
        controller.createTier(new CreateTierRestRequest()).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Tier name already exists");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== updateTier ====================

  @Nested
  class UpdateTier {

    @Test
    void shouldReturnUpdatedTierOnSuccess() throws Exception {
      TierInfo info = buildTierInfo(2, "enterprise", true);
      when(tierService.updateTier(any())).thenReturn(Single.just(info));

      Response<TierRestResponse> response =
          controller.updateTier(2, new UpdateTierRestRequest()).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTierId()).isEqualTo(2);
      assertThat(response.getData().getName()).isEqualTo("enterprise");
    }

    @Test
    void shouldPropagateErrorWhenTierNotFound() {
      when(tierService.updateTier(any()))
          .thenReturn(Single.error(new RuntimeException("Tier not found: 99")));

      try {
        controller.updateTier(99, new UpdateTierRestRequest()).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("Tier not found: 99");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== deactivateTier ====================

  @Nested
  class DeactivateTier {

    @Test
    void shouldReturnDeactivatedTierOnSuccess() throws Exception {
      TierInfo info = buildTierInfo(3, "pro", false);
      when(tierService.deactivateTier(3)).thenReturn(Single.just(info));

      Response<TierRestResponse> response =
          controller.deactivateTier(3).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTierId()).isEqualTo(3);
      assertThat(response.getData().getIsActive()).isFalse();
    }

    @Test
    void shouldPropagateErrorWhenTierNotFound() {
      when(tierService.deactivateTier(99))
          .thenReturn(Single.error(new RuntimeException("Tier not found: 99")));

      try {
        controller.deactivateTier(99).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("Tier not found: 99");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== activateTier ====================

  @Nested
  class ActivateTier {

    @Test
    void shouldReturnActivatedTierOnSuccess() throws Exception {
      TierInfo info = buildTierInfo(4, "pro", true);
      when(tierService.activateTier(4)).thenReturn(Single.just(info));

      Response<TierRestResponse> response =
          controller.activateTier(4).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTierId()).isEqualTo(4);
      assertThat(response.getData().getIsActive()).isTrue();
    }

    @Test
    void shouldPropagateErrorWhenTierNotFound() {
      when(tierService.activateTier(99))
          .thenReturn(Single.error(new RuntimeException("Tier not found: 99")));

      try {
        controller.activateTier(99).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("Tier not found: 99");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== getAllTiers ====================

  @Nested
  class GetAllTiers {

    @Test
    void shouldReturnAllTiersWhenActiveOnlyFalse() throws Exception {
      TierInfo active = buildTierInfo(1, "free", true);
      TierInfo inactive = buildTierInfo(2, "pro", false);
      when(tierService.getAllTiers()).thenReturn(Flowable.fromIterable(List.of(active, inactive)));

      Response<TierListRestResponse> response =
          controller.getAllTiers(false).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTiers()).hasSize(2);
      assertThat(response.getData().getTotalCount()).isEqualTo(2);
    }

    @Test
    void shouldReturnOnlyActiveTiersWhenActiveOnlyTrue() throws Exception {
      TierInfo active = buildTierInfo(1, "free", true);
      TierInfo inactive = buildTierInfo(2, "pro", false);
      when(tierService.getAllTiers()).thenReturn(Flowable.fromIterable(List.of(active, inactive)));

      Response<TierListRestResponse> response =
          controller.getAllTiers(true).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTiers()).hasSize(1);
      assertThat(response.getData().getTiers().get(0).getIsActive()).isTrue();
    }

    @Test
    void shouldReturnEmptyListWhenNoTiers() throws Exception {
      when(tierService.getAllTiers()).thenReturn(Flowable.empty());

      Response<TierListRestResponse> response =
          controller.getAllTiers(false).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTiers()).isEmpty();
      assertThat(response.getData().getTotalCount()).isEqualTo(0);
    }
  }

  // ==================== getTier ====================

  @Nested
  class GetTier {

    @Test
    void shouldReturnTierWhenFound() throws Exception {
      TierInfo info = buildTierInfo(1, "free", true);
      when(tierService.getTierById(1)).thenReturn(Maybe.just(info));

      Response<TierRestResponse> response =
          controller.getTier(1).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getTierId()).isEqualTo(1);
      assertThat(response.getData().getName()).isEqualTo("free");
    }

    @Test
    void shouldPropagateErrorWhenTierNotFound() {
      when(tierService.getTierById(99)).thenReturn(Maybe.empty());

      try {
        controller.getTier(99).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("Tier not found: 99");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }
}
