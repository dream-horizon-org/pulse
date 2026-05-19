package org.dreamhorizon.pulseserver.service.serviceowner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.service.ServiceDao;
import org.dreamhorizon.pulseserver.dao.service.models.ServiceRow;
import org.dreamhorizon.pulseserver.resources.services.models.CreateServiceRequest;
import org.dreamhorizon.pulseserver.resources.services.models.ServiceResponseDto;
import org.dreamhorizon.pulseserver.resources.services.models.UpdateServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceOwnerServiceTest {

  @Mock ServiceDao serviceDao;

  ServiceOwnerService service;

  @BeforeEach
  void setUp() {
    service = new ServiceOwnerService(serviceDao);
  }

  private ServiceRow buildRow(String name) {
    return ServiceRow.builder()
        .id(1L)
        .serviceName(name)
        .serviceGroup("core")
        .displayName("Display " + name)
        .ownerEmail("owner@test.com")
        .ownerSlackId("U123")
        .goalertServiceId("goalert-id")
        .description("desc")
        .createdAt("2026-01-01T00:00")
        .updatedAt("2026-01-01T00:00")
        .build();
  }

  @Nested
  class ListServices {

    @Test
    void shouldReturnMappedDtos() {
      when(serviceDao.getAllActive())
          .thenReturn(Single.just(List.of(buildRow("svc-a"), buildRow("svc-b"))));

      List<ServiceResponseDto> result = service.listServices().blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getServiceName()).isEqualTo("svc-a");
      assertThat(result.get(1).getServiceName()).isEqualTo("svc-b");
    }

    @Test
    void shouldReturnEmptyList() {
      when(serviceDao.getAllActive()).thenReturn(Single.just(List.of()));

      List<ServiceResponseDto> result = service.listServices().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetByServiceName {

    @Test
    void shouldReturnDtoWhenFound() {
      when(serviceDao.getByServiceName("payment")).thenReturn(Maybe.just(buildRow("payment")));

      ServiceResponseDto result = service.getByServiceName("payment").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getServiceName()).isEqualTo("payment");
      assertThat(result.getOwnerEmail()).isEqualTo("owner@test.com");
      assertThat(result.getGoalertServiceId()).isEqualTo("goalert-id");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      when(serviceDao.getByServiceName("missing")).thenReturn(Maybe.empty());

      service.getByServiceName("missing")
          .test()
          .assertComplete()
          .assertNoValues();
    }
  }

  @Nested
  class CreateService {

    @Test
    void shouldCreateAndReturnDto() {
      CreateServiceRequest request = CreateServiceRequest.builder()
          .serviceName("new-svc")
          .serviceGroup("platform")
          .displayName("New Service")
          .ownerEmail("new@test.com")
          .ownerSlackId("U999")
          .goalertServiceId("goalert-new")
          .description("new desc")
          .build();

      ServiceRow createdRow = ServiceRow.builder()
          .id(42L)
          .serviceName("new-svc")
          .serviceGroup("platform")
          .displayName("New Service")
          .ownerEmail("new@test.com")
          .ownerSlackId("U999")
          .goalertServiceId("goalert-new")
          .description("new desc")
          .isActive(true)
          .build();

      when(serviceDao.create(any(ServiceRow.class))).thenReturn(Single.just(createdRow));

      ServiceResponseDto result = service.createService(request).blockingGet();

      assertThat(result.getId()).isEqualTo(42L);
      assertThat(result.getServiceName()).isEqualTo("new-svc");
      assertThat(result.getOwnerEmail()).isEqualTo("new@test.com");
    }
  }

  @Nested
  class UpdateService {

    @Test
    void shouldUpdateAndReturnDto() {
      UpdateServiceRequest request = UpdateServiceRequest.builder()
          .serviceGroup("updated-group")
          .displayName("Updated Name")
          .ownerEmail("updated@test.com")
          .ownerSlackId("U111")
          .goalertServiceId("goalert-upd")
          .description("updated desc")
          .build();

      ServiceRow updatedRow = ServiceRow.builder()
          .serviceName("payment")
          .serviceGroup("updated-group")
          .displayName("Updated Name")
          .ownerEmail("updated@test.com")
          .ownerSlackId("U111")
          .goalertServiceId("goalert-upd")
          .description("updated desc")
          .build();

      when(serviceDao.update(eq("payment"), any(ServiceRow.class)))
          .thenReturn(Single.just(updatedRow));

      ServiceResponseDto result = service.updateService("payment", request).blockingGet();

      assertThat(result.getServiceName()).isEqualTo("payment");
      assertThat(result.getServiceGroup()).isEqualTo("updated-group");
    }
  }

  @Nested
  class DeleteService {

    @Test
    void shouldDelegateToDao() {
      when(serviceDao.softDelete("payment")).thenReturn(Completable.complete());

      service.deleteService("payment")
          .test()
          .assertComplete();

      verify(serviceDao).softDelete("payment");
    }
  }
}
