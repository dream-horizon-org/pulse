package org.dreamhorizon.pulseserver.service.serviceowner;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.service.ServiceDao;
import org.dreamhorizon.pulseserver.dao.service.models.ServiceRow;
import org.dreamhorizon.pulseserver.resources.services.models.CreateServiceRequest;
import org.dreamhorizon.pulseserver.resources.services.models.ServiceResponseDto;
import org.dreamhorizon.pulseserver.resources.services.models.UpdateServiceRequest;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceOwnerService {

  private final ServiceDao serviceDao;

  public Single<List<ServiceResponseDto>> listServices() {
    return serviceDao.getAllActive()
        .map(rows -> rows.stream().map(this::toDto).collect(Collectors.toList()));
  }

  public Maybe<ServiceResponseDto> getByServiceName(String serviceName) {
    return serviceDao.getByServiceName(serviceName).map(this::toDto);
  }

  public Single<ServiceResponseDto> createService(CreateServiceRequest request) {
    ServiceRow row = ServiceRow.builder()
        .serviceName(request.getServiceName())
        .serviceGroup(request.getServiceGroup())
        .displayName(request.getDisplayName())
        .ownerEmail(request.getOwnerEmail())
        .ownerSlackId(request.getOwnerSlackId())
        .goalertServiceId(request.getGoalertServiceId())
        .description(request.getDescription())
        .build();
    return serviceDao.create(row).map(this::toDto);
  }

  public Single<ServiceResponseDto> updateService(String serviceName, UpdateServiceRequest request) {
    ServiceRow row = ServiceRow.builder()
        .serviceGroup(request.getServiceGroup())
        .displayName(request.getDisplayName())
        .ownerEmail(request.getOwnerEmail())
        .ownerSlackId(request.getOwnerSlackId())
        .goalertServiceId(request.getGoalertServiceId())
        .description(request.getDescription())
        .build();
    return serviceDao.update(serviceName, row).map(this::toDto);
  }

  public Completable deleteService(String serviceName) {
    return serviceDao.softDelete(serviceName);
  }

  private ServiceResponseDto toDto(ServiceRow row) {
    return ServiceResponseDto.builder()
        .id(row.getId())
        .serviceName(row.getServiceName())
        .serviceGroup(row.getServiceGroup())
        .displayName(row.getDisplayName())
        .ownerEmail(row.getOwnerEmail())
        .ownerSlackId(row.getOwnerSlackId())
        .goalertServiceId(row.getGoalertServiceId())
        .description(row.getDescription())
        .createdAt(row.getCreatedAt())
        .updatedAt(row.getUpdatedAt())
        .build();
  }
}
