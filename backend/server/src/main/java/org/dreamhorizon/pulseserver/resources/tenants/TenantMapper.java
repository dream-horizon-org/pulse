package org.dreamhorizon.pulseserver.resources.tenants;

import java.util.List;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.resources.tenants.models.AuditListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.AuditLogRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateCredentialsRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.CredentialsRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateCredentialsRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantRestRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.TenantInfo;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateCredentialsRequest;
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


  @Mapping(target = "tenantId", source = "tenantId")
  @Mapping(target = "clickhousePassword", source = "request.clickhousePassword")
  public abstract CreateCredentialsRequest toCreateCredentialsRequest(String tenantId, CreateCredentialsRestRequest request);

  @Mapping(target = "tenantId", source = "tenantId")
  @Mapping(target = "newPassword", source = "request.newPassword")
  @Mapping(target = "reason", source = "request.reason")
  public abstract UpdateCredentialsRequest toUpdateCredentialsRequest(String tenantId, UpdateCredentialsRestRequest request);

  public abstract CredentialsRestResponse toCredentialsRestResponse(TenantInfo info);

  @Mapping(target = "clickhousePassword", ignore = true)
  @Mapping(target = "message", ignore = true)
  public abstract CredentialsRestResponse toCredentialsRestResponse(ClickhouseCredentials credentials);


  public abstract AuditLogRestResponse toAuditLogRestResponse(ClickhouseTenantCredentialAudit audit);

  public abstract List<AuditLogRestResponse> toAuditLogRestResponseList(List<ClickhouseTenantCredentialAudit> audits);

  public AuditListRestResponse toAuditListRestResponse(List<ClickhouseTenantCredentialAudit> audits) {
    List<AuditLogRestResponse> responses = toAuditLogRestResponseList(audits);
    return AuditListRestResponse.builder()
        .auditLogs(responses)
        .totalCount(responses.size())
        .build();
  }
}
