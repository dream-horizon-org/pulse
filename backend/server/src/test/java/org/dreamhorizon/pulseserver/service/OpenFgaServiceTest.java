package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenFgaServiceTest {

  OpenFgaService service;

  @BeforeEach
  void setUp() throws Exception {
    OpenFgaConfig config = OpenFgaConfig.builder()
        .enabled(false)
        .build();
    service = new OpenFgaService(config);
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
}
