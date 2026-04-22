package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import dev.openfga.sdk.api.client.model.ClientListObjectsResponse;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.client.model.ClientWriteResponse;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenFgaServiceTest {

  OpenFgaService service;

  @Mock
  OpenFgaClient mockClient;

  @Mock
  TenantDao mockTenantDao;

  @Mock
  ProjectDao mockProjectDao;

  @BeforeEach
  void setUp() throws Exception {
    OpenFgaConfig config = OpenFgaConfig.builder()
        .enabled(false)
        .build();
    service = new OpenFgaService(config, null, null);
  }

  @Nested
  class CheckPermission {

    @Test
    void shouldReturnTrueWhenDisabled() {
      Single<Boolean> result = service.checkPermission("user1", "can_view", "project", "proj_123");
      result.test()
          .assertValue(true);
    }
  }

  @Nested
  class AssignTenantRole {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.assignTenantRole("user1", "tenant1", "admin");
      result.test().assertComplete();
    }
  }

  @Nested
  class AssignProjectRole {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.assignProjectRole("user1", "proj_123", "editor");
      result.test().assertComplete();
    }
  }

  @Nested
  class UpdateProjectRole {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.updateProjectRole("user1", "proj_123", "viewer");
      result.test().assertComplete();
    }
  }

  @Nested
  class UpdateTenantRole {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.updateTenantRole("user1", "tenant1", "member");
      result.test().assertComplete();
    }
  }

  @Nested
  class GetUserRoleInProject {

    @Test
    void shouldReturnEmptyWhenDisabled() {
      Single<Optional<String>> result = service.getUserRoleInProject("user1", "proj_123");
      result.test()
          .assertValue(Optional.empty());
    }
  }

  @Nested
  class GetUserTenantRole {

    @Test
    void shouldReturnEmptyWhenDisabled() {
      Single<Optional<String>> result = service.getUserTenantRole("user1", "tenant1");
      result.test()
          .assertValue(Optional.empty());
    }
  }

  @Nested
  class GetUserTenants {

    @Test
    void shouldReturnEmptyListWhenDisabled() {
      Single<java.util.List<String>> result = service.getUserTenants("user1");
      result.test()
          .assertValue(list -> list != null && list.isEmpty());
    }

    @Test
    void shouldReturnNewArrayListInstance() {
      Single<java.util.List<String>> result = service.getUserTenants("user1");
      assertThat(result.blockingGet()).isInstanceOf(ArrayList.class);
    }
  }

  @Nested
  class GetTenantMembers {

    @Test
    void shouldReturnEmptySetWhenDisabled() {
      Single<java.util.Set<String>> result = service.getTenantMembers("tenant1");
      result.test()
          .assertValue(set -> set != null && set.isEmpty());
    }

    @Test
    void shouldReturnNewHashSetInstance() {
      Single<java.util.Set<String>> result = service.getTenantMembers("tenant1");
      assertThat(result.blockingGet()).isInstanceOf(HashSet.class);
    }
  }

  @Nested
  class GetProjectMembers {

    @Test
    void shouldReturnEmptySetWhenDisabled() {
      Single<java.util.Set<String>> result = service.getProjectMembers("proj_123");
      result.test()
          .assertValue(set -> set != null && set.isEmpty());
    }

    @Test
    void shouldReturnNewHashSetInstance() {
      Single<java.util.Set<String>> result = service.getProjectMembers("proj_123");
      assertThat(result.blockingGet()).isInstanceOf(HashSet.class);
    }
  }

  @Nested
  class RemoveProjectMember {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.removeProjectMember("user1", "proj_123");
      result.test().assertComplete();
    }
  }

  @Nested
  class RemoveTenantMember {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.removeTenantMember("user1", "tenant1");
      result.test().assertComplete();
    }
  }

  @Nested
  class CountTenantOwners {

    @Test
    void shouldReturnZeroWhenDisabled() {
      Single<Integer> result = service.countTenantOwners("tenant1");
      result.test()
          .assertValue(0);
    }
  }

  @Nested
  class GetProjectAdmins {

    @Test
    void shouldReturnEmptySetWhenDisabled() {
      Single<java.util.Set<String>> result = service.getProjectAdmins("proj_123");
      result.test()
          .assertValue(set -> set != null && set.isEmpty());
    }

    @Test
    void shouldReturnNewHashSetInstance() {
      Single<java.util.Set<String>> result = service.getProjectAdmins("proj_123");
      assertThat(result.blockingGet()).isInstanceOf(HashSet.class);
    }
  }

  @Nested
  class CountProjectAdmins {

    @Test
    void shouldReturnZeroWhenDisabled() {
      Single<Integer> result = service.countProjectAdmins("proj_123");
      result.test()
          .assertValue(0);
    }
  }

  @Nested
  class IsTenantOwner {

    @Test
    void shouldReturnFalseWhenDisabled() {
      Single<Boolean> result = service.isTenantOwner("user1", "tenant1");
      result.test()
          .assertValue(false);
    }
  }

  @Nested
  class IsProjectAdmin {

    @Test
    void shouldReturnFalseWhenDisabled() {
      Single<Boolean> result = service.isProjectAdmin("user1", "proj_123");
      result.test()
          .assertValue(false);
    }
  }

  @Nested
  class LinkProjectToTenant {

    @Test
    void shouldCompleteWhenDisabled() {
      Completable result = service.linkProjectToTenant("proj_123", "tenant1");
      result.test().assertComplete();
    }
  }

  @Nested
  class IsTenantAdmin {

    @Test
    void shouldReturnFalseWhenDisabled() {
      Single<Boolean> result = service.isTenantAdmin("user1", "tenant1");
      result.test()
          .assertValue(false);
    }
  }

  @Nested
  class GetUserProjects {

    @Test
    void shouldReturnEmptyListWhenDisabled() {
      Single<java.util.List<String>> result = service.getUserProjects("user1");
      result.test()
          .assertValue(list -> list != null && list.isEmpty());
    }

    @Test
    void shouldReturnNewArrayListInstance() {
      Single<java.util.List<String>> result = service.getUserProjects("user1");
      assertThat(result.blockingGet()).isInstanceOf(ArrayList.class);
    }
  }

  @Nested
  class SuperadminApisWhenDisabled {

    @Test
    void shouldReturnFalseForIsSuperAdmin() {
      service.isSuperAdmin("any").test().assertValue(false);
    }

    @Test
    void shouldReturnEmptySuperAdmins() {
      service.getSuperAdmins().test().assertValue(Set::isEmpty);
    }

    @Test
    void shouldCompleteLinkTenantToSystemWithoutClient() {
      service.linkTenantToSystem("tenant-x").test().assertComplete();
    }

    @Test
    void shouldCompleteAssignAndRevokeSuperAdmin() {
      service.assignSuperAdmin("u1").test().assertComplete();
      service.revokeSuperAdmin("u1").test().assertComplete();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // ENABLED MODE TESTS (mocked OpenFgaClient)
  // ═══════════════════════════════════════════════════════════════════════════════

  @Nested
  class EnabledMode {

    OpenFgaService enabledService;

    @BeforeEach
    void setUpEnabled() throws Exception {
      OpenFgaConfig config = OpenFgaConfig.builder().enabled(false).build();
      enabledService = new OpenFgaService(config, mockTenantDao, mockProjectDao);
      java.lang.reflect.Field clientField = OpenFgaService.class.getDeclaredField("client");
      clientField.setAccessible(true);
      clientField.set(enabledService, mockClient);
      java.lang.reflect.Field enabledField = OpenFgaService.class.getDeclaredField("enabled");
      enabledField.setAccessible(true);
      enabledField.set(enabledService, true);

      ClientCheckResponse defaultDeny = mock(ClientCheckResponse.class);
      lenient().when(defaultDeny.getAllowed()).thenReturn(false);
      lenient().when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(defaultDeny));
    }

    @Nested
    class CheckPermissionEnabled {

      @Test
      void shouldReturnTrueWhenClientAllows() throws Exception {
        ClientCheckResponse checkResponse = mock(ClientCheckResponse.class);
        when(checkResponse.getAllowed()).thenReturn(true);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(checkResponse));

        Single<Boolean> result = enabledService.checkPermission("user1", "can_view", "project", "proj_123");
        result.test().assertValue(true);
      }

      @Test
      void shouldReturnFalseWhenClientDenies() throws Exception {
        ClientCheckResponse checkResponse = mock(ClientCheckResponse.class);
        when(checkResponse.getAllowed()).thenReturn(false);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(checkResponse));

        Single<Boolean> result = enabledService.checkPermission("user1", "can_edit", "project", "proj_123");
        result.test().assertValue(false);
      }
    }

    @Nested
    class AssignTenantRoleEnabled {

      @Test
      void shouldCompleteWhenWriteSucceeds() throws Exception {
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.assignTenantRole("user1", "tenant1", "admin");
        result.test().assertComplete();
      }
    }

    @Nested
    class AssignProjectRoleEnabled {

      @Test
      void shouldCompleteWhenWriteSucceeds() throws Exception {
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.assignProjectRole("user1", "proj_123", "editor");
        result.test().assertComplete();
      }
    }

    @Nested
    class GetUserRoleInProjectEnabled {

      @Test
      void shouldReturnRoleWhenTupleExists() throws Exception {
        Tuple tuple = new Tuple();
        TupleKey key = new TupleKey().user("user:user1").relation("editor")._object("project:proj_123");
        tuple.setKey(key);
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Optional<String>> result = enabledService.getUserRoleInProject("user1", "proj_123");
        result.test().assertValue(Optional.of("editor"));
      }

      @Test
      void shouldReturnEmptyWhenNoTuple() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(Collections.emptyList());
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Optional<String>> result = enabledService.getUserRoleInProject("user1", "proj_123");
        result.test().assertValue(Optional.empty());
      }
    }

    @Nested
    class GetTenantMembersEnabled {

      @Test
      void shouldReturnMembersFromTuples() throws Exception {
        Tuple tuple = new Tuple();
        TupleKey key = new TupleKey().user("user:user1").relation("admin")._object("tenant:tenant1");
        tuple.setKey(key);
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getTenantMembers("tenant1");
        assertThat(result.blockingGet()).containsExactly("user1");
      }

      @Test
      void shouldReturnEmptySetWhenNoTuples() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(Collections.emptyList());
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getTenantMembers("tenant1");
        assertThat(result.blockingGet()).isEmpty();
      }
    }

    @Nested
    class GetProjectMembersEnabled {

      @Test
      void shouldReturnMembersFromTuples() throws Exception {
        Tuple tuple = new Tuple();
        TupleKey key = new TupleKey().user("user:user1").relation("editor")._object("project:proj_123");
        tuple.setKey(key);
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getProjectMembers("proj_123");
        assertThat(result.blockingGet()).containsExactly("user1");
      }
    }

    @Nested
    class GetUserTenantsEnabled {

      @Test
      void shouldReturnTenantIdsFromListObjects() throws Exception {
        ClientListObjectsResponse listResponse = mock(ClientListObjectsResponse.class);
        when(listResponse.getObjects()).thenReturn(List.of("tenant:tenant1"));
        when(mockClient.listObjects(any())).thenReturn(CompletableFuture.completedFuture(listResponse));

        Single<java.util.List<String>> result = enabledService.getUserTenants("user1");
        assertThat(result.blockingGet()).contains("tenant1");
      }
    }

    @Nested
    class GetUserProjectsEnabled {

      @Test
      void shouldReturnProjectIdsFromListObjects() throws Exception {
        ClientListObjectsResponse listResponse = mock(ClientListObjectsResponse.class);
        when(listResponse.getObjects()).thenReturn(List.of("project:proj_123"));
        when(mockClient.listObjects(any())).thenReturn(CompletableFuture.completedFuture(listResponse));

        Single<java.util.List<String>> result = enabledService.getUserProjects("user1");
        assertThat(result.blockingGet()).contains("proj_123");
      }
    }

    @Nested
    class CountTenantOwnersEnabled {

      @Test
      void shouldReturnCountFromRead() throws Exception {
        Tuple tuple1 = new Tuple();
        tuple1.setKey(new TupleKey().user("user:u1").relation("admin")._object("tenant:t1"));
        Tuple tuple2 = new Tuple();
        tuple2.setKey(new TupleKey().user("user:u2").relation("admin")._object("tenant:t1"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple1, tuple2));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Integer> result = enabledService.countTenantOwners("t1");
        result.test().assertValue(2);
      }
    }

    @Nested
    class IsTenantOwnerEnabled {

      @Test
      void shouldReturnTrueWhenCheckAllows() throws Exception {
        ClientCheckResponse checkResponse = mock(ClientCheckResponse.class);
        when(checkResponse.getAllowed()).thenReturn(true);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(checkResponse));

        Single<Boolean> result = enabledService.isTenantOwner("user1", "tenant1");
        result.test().assertValue(true);
      }
    }

    @Nested
    class IsProjectAdminEnabled {

      @Test
      void shouldReturnTrueWhenCheckAllows() throws Exception {
        ClientCheckResponse checkResponse = mock(ClientCheckResponse.class);
        when(checkResponse.getAllowed()).thenReturn(true);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(checkResponse));

        Single<Boolean> result = enabledService.isProjectAdmin("user1", "proj_123");
        result.test().assertValue(true);
      }
    }

    @Nested
    class LinkProjectToTenantEnabled {

      @Test
      void shouldCompleteWhenWriteSucceeds() throws Exception {
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.linkProjectToTenant("proj_123", "tenant1");
        result.test().assertComplete();
      }
    }

    @Nested
    class LinkTenantToSystemEnabled {

      @Test
      void shouldWriteSystemParentTuple() throws Exception {
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        enabledService.linkTenantToSystem("acme").blockingAwait();

        ArgumentCaptor<ClientWriteRequest> captor = ArgumentCaptor.forClass(ClientWriteRequest.class);
        verify(mockClient).write(captor.capture());
        var writes = captor.getValue().getWrites();
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).getUser()).isEqualTo(Constants.OPENFGA_OBJECT_SYSTEM_PULSE);
        assertThat(writes.get(0).getRelation()).isEqualTo(Constants.RELATION_SYSTEM_PARENT);
        assertThat(writes.get(0).getObject()).isEqualTo("tenant:acme");
      }
    }

    @Nested
    class IsSuperAdminEnabled {

      @Test
      void shouldReturnTrueWhenCheckAllows() throws Exception {
        ClientCheckResponse allow = mock(ClientCheckResponse.class);
        when(allow.getAllowed()).thenReturn(true);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(allow));

        assertThat(enabledService.isSuperAdmin("u1").blockingGet()).isTrue();
      }

      @Test
      void shouldReturnFalseWhenCheckDenies() {
        assertThat(enabledService.isSuperAdmin("u1").blockingGet()).isFalse();
      }
    }

    @Nested
    class AssignAndRevokeSuperAdminEnabled {

      @Test
      void shouldWriteSuperadminTupleOnAssign() throws Exception {
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        enabledService.assignSuperAdmin("alice").blockingAwait();

        ArgumentCaptor<ClientWriteRequest> captor = ArgumentCaptor.forClass(ClientWriteRequest.class);
        verify(mockClient).write(captor.capture());
        var writes = captor.getValue().getWrites();
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).getUser()).isEqualTo("user:alice");
        assertThat(writes.get(0).getRelation()).isEqualTo(Constants.RELATION_SUPERADMIN);
        assertThat(writes.get(0).getObject()).isEqualTo(Constants.OPENFGA_OBJECT_SYSTEM_PULSE);
      }

      @Test
      void shouldInvokeWriteOnRevoke() throws Exception {
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        enabledService.revokeSuperAdmin("bob").blockingAwait();

        verify(mockClient).write(any());
      }
    }

    @Nested
    class GetSuperAdminsEnabled {

      @Test
      void shouldParseUserIdsFromReadTuples() throws Exception {
        Tuple t1 = new Tuple();
        t1.setKey(new TupleKey().user("user:u1").relation("superadmin")._object(Constants.OPENFGA_OBJECT_SYSTEM_PULSE));
        Tuple t2 = new Tuple();
        t2.setKey(new TupleKey().user("user:u2").relation("superadmin")._object(Constants.OPENFGA_OBJECT_SYSTEM_PULSE));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(t1, t2));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        assertThat(enabledService.getSuperAdmins().blockingGet()).containsExactlyInAnyOrder("u1", "u2");
      }
    }

    @Nested
    class GetUserTenantsSuperadminFallback {

      @Test
      void shouldReturnAllTenantIdsFromDbWhenSuperadmin() throws Exception {
        ClientCheckResponse allow = mock(ClientCheckResponse.class);
        when(allow.getAllowed()).thenReturn(true);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(allow));
        when(mockTenantDao.getAllTenants()).thenReturn(Flowable.just(
            Tenant.builder().tenantId("t1").name("A").build(),
            Tenant.builder().tenantId("t2").name("B").build()));

        List<String> result = enabledService.getUserTenants("sa1").blockingGet();

        assertThat(result).containsExactlyInAnyOrder("t1", "t2");
        verify(mockClient, never()).listObjects(any());
      }
    }

    @Nested
    class GetUserProjectsSuperadminFallback {

      @Test
      void shouldReturnAllActiveProjectIdsFromDbWhenSuperadmin() throws Exception {
        ClientCheckResponse allow = mock(ClientCheckResponse.class);
        when(allow.getAllowed()).thenReturn(true);
        when(mockClient.check(any())).thenReturn(CompletableFuture.completedFuture(allow));
        when(mockProjectDao.getAllActiveProjectIds()).thenReturn(Single.just(List.of("p1", "p2")));

        List<String> result = enabledService.getUserProjects("sa1").blockingGet();

        assertThat(result).containsExactlyInAnyOrder("p1", "p2");
        verify(mockClient, never()).listObjects(any());
      }
    }

    @Nested
    class RemoveProjectMemberEnabled {

      @Test
      void shouldCompleteWhenMemberRemoved() throws Exception {
        Tuple tuple = new Tuple();
        tuple.setKey(new TupleKey().user("user:user1").relation("editor")._object("project:proj_123"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.removeProjectMember("user1", "proj_123");
        result.test().assertComplete();
      }
    }

    @Nested
    class UpdateProjectRoleEnabled {

      @Test
      void shouldWriteNewRoleWhenNoCurrentRole() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(Collections.emptyList());
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.updateProjectRole("user1", "proj_123", "viewer");
        result.test().assertComplete();
      }

      @Test
      void shouldSkipWriteWhenRoleUnchanged() throws Exception {
        Tuple tuple = new Tuple();
        tuple.setKey(new TupleKey().user("user:user1").relation("editor")._object("project:proj_123"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Completable result = enabledService.updateProjectRole("user1", "proj_123", "editor");
        result.test().assertComplete();

        verify(mockClient, never()).write(any());
      }

      @Test
      void shouldDeleteAndWriteWhenRoleChanges() throws Exception {
        Tuple tuple = new Tuple();
        tuple.setKey(new TupleKey().user("user:user1").relation("viewer")._object("project:proj_123"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.updateProjectRole("user1", "proj_123", "admin");
        result.test().assertComplete();

        verify(mockClient).write(any());
      }
    }

    @Nested
    class UpdateTenantRoleEnabled {

      @Test
      void shouldWriteNewRoleWhenNoCurrentRole() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(Collections.emptyList());
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.updateTenantRole("user1", "tenant1", "member");
        result.test().assertComplete();
      }

      @Test
      void shouldSkipWriteWhenTenantRoleUnchanged() throws Exception {
        Tuple tuple = new Tuple();
        tuple.setKey(new TupleKey().user("user:user1").relation("member")._object("tenant:tenant1"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Completable result = enabledService.updateTenantRole("user1", "tenant1", "member");
        result.test().assertComplete();

        verify(mockClient, never()).write(any());
      }
    }

    @Nested
    class GetUserTenantRoleEnabled {

      @Test
      void shouldReturnRoleWhenTupleExists() throws Exception {
        Tuple tuple = new Tuple();
        tuple.setKey(new TupleKey().user("user:user1").relation("admin")._object("tenant:tenant1"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Optional<String>> result = enabledService.getUserTenantRole("user1", "tenant1");
        result.test().assertValue(Optional.of("admin"));
      }
    }

    @Nested
    class GetProjectAdminsEnabled {

      @Test
      void shouldReturnAdminUserIdsFromRead() throws Exception {
        Tuple tuple1 = new Tuple();
        tuple1.setKey(new TupleKey().user("user:u1").relation("admin")._object("project:proj_123"));
        Tuple tuple2 = new Tuple();
        tuple2.setKey(new TupleKey().user("user:u2").relation("admin")._object("project:proj_123"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple1, tuple2));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getProjectAdmins("proj_123");
        assertThat(result.blockingGet()).containsExactlyInAnyOrder("u1", "u2");
      }

      @Test
      void shouldReturnEmptySetWhenNoAdmins() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(Collections.emptyList());
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getProjectAdmins("proj_123");
        assertThat(result.blockingGet()).isEmpty();
      }
    }

    @Nested
    class CountProjectAdminsEnabled {

      @Test
      void shouldReturnCountFromRead() throws Exception {
        Tuple tuple1 = new Tuple();
        tuple1.setKey(new TupleKey().user("user:u1").relation("admin")._object("project:proj_123"));
        Tuple tuple2 = new Tuple();
        tuple2.setKey(new TupleKey().user("user:u2").relation("admin")._object("project:proj_123"));
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(List.of(tuple1, tuple2));
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Integer> result = enabledService.countProjectAdmins("proj_123");
        result.test().assertValue(2);
      }

      @Test
      void shouldReturnZeroWhenNoAdmins() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(Collections.emptyList());
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Integer> result = enabledService.countProjectAdmins("proj_123");
        result.test().assertValue(0);
      }

      @Test
      void shouldReturnZeroWhenTuplesNull() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(null);
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<Integer> result = enabledService.countProjectAdmins("proj_123");
        result.test().assertValue(0);
      }
    }

    @Nested
    class GetTenantMembersNullTuples {

      @Test
      void shouldReturnEmptySetWhenTuplesNull() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(null);
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getTenantMembers("tenant1");
        assertThat(result.blockingGet()).isEmpty();
      }
    }

    @Nested
    class GetProjectMembersNullTuples {

      @Test
      void shouldReturnEmptySetWhenTuplesNull() throws Exception {
        ClientReadResponse readResponse = mock(ClientReadResponse.class);
        when(readResponse.getTuples()).thenReturn(null);
        when(mockClient.read(any())).thenReturn(CompletableFuture.completedFuture(readResponse));

        Single<java.util.Set<String>> result = enabledService.getProjectMembers("proj_123");
        assertThat(result.blockingGet()).isEmpty();
      }
    }

    @Nested
    class RemoveTenantMemberEnabled {

      @Test
      void shouldCompleteWhenRemovingTenantMemberWithCascade() throws Exception {
        Tuple tenantTuple = new Tuple();
        tenantTuple.setKey(new TupleKey().user("user:user1").relation("admin")._object("tenant:tenant1"));
        ClientReadResponse tenantReadResponse = mock(ClientReadResponse.class);
        when(tenantReadResponse.getTuples()).thenReturn(List.of(tenantTuple));

        ClientListObjectsResponse projectsResponse = mock(ClientListObjectsResponse.class);
        when(projectsResponse.getObjects()).thenReturn(List.of("project:proj_1", "project:proj_2"));

        Tuple projectTuple = new Tuple();
        projectTuple.setKey(new TupleKey().user("user:user1").relation("viewer")._object("project:proj_1"));
        ClientReadResponse projectReadResponse = mock(ClientReadResponse.class);
        when(projectReadResponse.getTuples()).thenReturn(List.of(projectTuple));

        when(mockClient.read(any())).thenReturn(
            CompletableFuture.completedFuture(tenantReadResponse),
            CompletableFuture.completedFuture(projectReadResponse));
        when(mockClient.listObjects(any())).thenReturn(CompletableFuture.completedFuture(projectsResponse));
        when(mockClient.write(any())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Completable result = enabledService.removeTenantMember("user1", "tenant1");
        result.test().assertComplete();
      }
    }

    @Nested
    class GetUserTenantsEdgeCases {

      @Test
      void shouldHandleNullObjectsInResponse() throws Exception {
        ClientListObjectsResponse listResponse = mock(ClientListObjectsResponse.class);
        when(listResponse.getObjects()).thenReturn(null);
        when(mockClient.listObjects(any())).thenReturn(CompletableFuture.completedFuture(listResponse));

        Single<java.util.List<String>> result = enabledService.getUserTenants("user1");
        assertThat(result.blockingGet()).isEmpty();
      }

      @Test
      void shouldStripTenantPrefixFromIds() throws Exception {
        ClientListObjectsResponse listResponse = mock(ClientListObjectsResponse.class);
        when(listResponse.getObjects()).thenReturn(List.of("tenant:tenant1", "tenant:tenant2"));
        when(mockClient.listObjects(any())).thenReturn(CompletableFuture.completedFuture(listResponse));

        Single<java.util.List<String>> result = enabledService.getUserTenants("user1");
        assertThat(result.blockingGet()).containsExactlyInAnyOrder("tenant1", "tenant2");
      }
    }

    @Nested
    class GetUserProjectsEdgeCases {

      @Test
      void shouldHandleNullObjectsInResponse() throws Exception {
        ClientListObjectsResponse listResponse = mock(ClientListObjectsResponse.class);
        when(listResponse.getObjects()).thenReturn(null);
        when(mockClient.listObjects(any())).thenReturn(CompletableFuture.completedFuture(listResponse));

        Single<java.util.List<String>> result = enabledService.getUserProjects("user1");
        assertThat(result.blockingGet()).isEmpty();
      }
    }
  }
}
