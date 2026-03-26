package org.dreamhorizon.pulseserver.resources.tenants;

import java.util.List;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantRestRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class TenantMapper {

  public static final TenantMapper INSTANCE = Mappers.getMapper(TenantMapper.class);


  @Mapping(target = "clickhousePassword", ignore = true)
  public abstract CreateTenantRequest toCreateTenantRequest(CreateTenantRestRequest request);

  @Mapping(target = "tenantId", source = "tenantId")
  @Mapping(target = "name", source = "request.name")
  @Mapping(target = "description", source = "request.description")
  public abstract UpdateTenantRequest toUpdateTenantRequest(String tenantId, UpdateTenantRestRequest request);

  public abstract TenantRestResponse toTenantRestResponse(Tenant tenant);

  public abstract List<TenantRestResponse> toTenantRestResponseList(List<Tenant> tenants);

  public TenantListRestResponse toTenantListRestResponse(List<Tenant> tenants) {
    List<TenantRestResponse> responses = toTenantRestResponseList(tenants);
    return TenantListRestResponse.builder()
        .tenants(responses)
        .totalCount(responses.size())
        .build();
  }
}
