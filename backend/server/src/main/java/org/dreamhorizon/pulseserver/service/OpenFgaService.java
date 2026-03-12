package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.Tuple;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;

/**
 * OpenFGA Service for Relationship-Based Access Control (ReBAC).
 *
 * <p>Manages user-tenant-project relationships via OpenFGA tuples and evaluates
 * permissions using the OpenFGA check API against a stored authorization model.</p>
 *
 * <p>When OpenFGA is disabled (via config), all methods degrade gracefully:
 * permission checks return true, writes are no-ops, and reads return empty results.</p>
 */
@Slf4j
@Singleton
public class OpenFgaService {

  private static final String USER_PREFIX = "user:";
  private static final String TENANT_PREFIX = "tenant:";
  private static final String PROJECT_PREFIX = "project:";
  private static final Set<String> TENANT_ROLES = Set.of("admin", "member");
  private static final Set<String> PROJECT_ROLES = Set.of("admin", "editor", "viewer");

  private final OpenFgaClient client;
  private final boolean enabled;

  @Inject
  public OpenFgaService(OpenFgaConfig config) throws Exception {
    if (config != null && config.isEnabled()) {
      ClientConfiguration configuration = new ClientConfiguration()
          .apiUrl(config.getApiUrl())
          .storeId(config.getStoreId())
          .authorizationModelId(config.getAuthorizationModelId());
      this.client = new OpenFgaClient(configuration);
      this.enabled = true;
      log.info("OpenFGA service initialized - store: {}, model: {}",
          config.getStoreId(), config.getAuthorizationModelId());
    } else {
      this.client = null;
      this.enabled = false;
      log.warn("OpenFGA service is disabled - all permission checks will be permissive");
    }
  }

  /**
   * Check if a user has a specific permission on a resource.
   *
   * @param userId       User ID
   * @param action       Action/relation to check (e.g., "can_view", "can_edit")
   * @param resourceType Resource type (e.g., "project", "tenant")
   * @param resourceId   Resource ID
   * @return Single emitting true if allowed, false otherwise
   */
  public Single<Boolean> checkPermission(String userId, String action, String resourceType, String resourceId) {
    if (!enabled) {
      log.debug("[DISABLED] Permission check: user={}, action={}, resource={}:{} -> ALLOWED",
          userId, action, resourceType, resourceId);
      return Single.just(true);
    }
    return Single.fromCallable(() -> {
      ClientCheckRequest request = new ClientCheckRequest()
          .user(USER_PREFIX + userId)
          .relation(action)
          ._object(resourceType + ":" + resourceId);
      var response = client.check(request).get();
      boolean allowed = Boolean.TRUE.equals(response.getAllowed());
      log.debug("Permission check: user={}, action={}, resource={}:{} -> {}",
          userId, action, resourceType, resourceId, allowed);
      return allowed;
    });
  }

  /**
   * Assign a role to a user on a tenant.
   *
   * @param userId   User ID
   * @param tenantId Tenant ID
   * @param role     Role (e.g., "admin", "member")
   * @return Completable that completes when the tuple is written
   */
  public Completable assignTenantRole(String userId, String tenantId, String role) {
    if (!enabled) {
      log.debug("[DISABLED] Assigning tenant role: user={}, tenant={}, role={}", userId, tenantId, role);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Assigning tenant role: user={}, tenant={}, role={}", userId, tenantId, role);
      ClientWriteRequest request = new ClientWriteRequest()
          .writes(List.of(new ClientTupleKey()
              .user(USER_PREFIX + userId)
              .relation(role)
              ._object(TENANT_PREFIX + tenantId)));
      client.write(request).get();
    });
  }

  /**
   * Assign a role to a user on a project.
   *
   * @param userId    User ID
   * @param projectId Project ID
   * @param role      Role (e.g., "admin", "editor", "member")
   * @return Completable that completes when the tuple is written
   */
  public Completable assignProjectRole(String userId, String projectId, String role) {
    if (!enabled) {
      log.debug("[DISABLED] Assigning project role: user={}, project={}, role={}", userId, projectId, role);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Assigning project role: user={}, project={}, role={}", userId, projectId, role);
      ClientWriteRequest request = new ClientWriteRequest()
          .writes(List.of(new ClientTupleKey()
              .user(USER_PREFIX + userId)
              .relation(role)
              ._object(PROJECT_PREFIX + projectId)));
      client.write(request).get();
    });
  }

  /**
   * Update a user's role on a project. Deletes the old tuple and writes the new one
   * in a single transactional request.
   *
   * @param userId    User ID
   * @param projectId Project ID
   * @param newRole   New role
   * @return Completable that completes when the role is updated
   */
  public Completable updateProjectRole(String userId, String projectId, String newRole) {
    if (!enabled) {
      log.debug("[DISABLED] Updating project role: user={}, project={}, newRole={}", userId, projectId, newRole);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Updating project role: user={}, project={}, newRole={}", userId, projectId, newRole);
      String currentRole = findUserRoleOnObject(USER_PREFIX + userId, PROJECT_PREFIX + projectId, PROJECT_ROLES);

      if (currentRole != null && currentRole.equals(newRole)) {
        log.debug("Role unchanged, skipping update");
        return;
      }

      if (currentRole != null) {
        ClientWriteRequest request = new ClientWriteRequest()
            .writes(List.of(new ClientTupleKey()
                .user(USER_PREFIX + userId)
                .relation(newRole)
                ._object(PROJECT_PREFIX + projectId)))
            .deletes(List.of(new ClientTupleKeyWithoutCondition()
                .user(USER_PREFIX + userId)
                .relation(currentRole)
                ._object(PROJECT_PREFIX + projectId)));
        client.write(request).get();
      } else {
        ClientWriteRequest request = new ClientWriteRequest()
            .writes(List.of(new ClientTupleKey()
                .user(USER_PREFIX + userId)
                .relation(newRole)
                ._object(PROJECT_PREFIX + projectId)));
        client.write(request).get();
      }
    });
  }

  /**
   * Update a user's role on a tenant. Deletes the old tuple and writes the new one
   * in a single transactional request.
   *
   * @param userId   User ID
   * @param tenantId Tenant ID
   * @param newRole  New role
   * @return Completable that completes when the role is updated
   */
  public Completable updateTenantRole(String userId, String tenantId, String newRole) {
    if (!enabled) {
      log.debug("[DISABLED] Updating tenant role: user={}, tenant={}, newRole={}", userId, tenantId, newRole);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Updating tenant role: user={}, tenant={}, newRole={}", userId, tenantId, newRole);
      String currentRole = findUserRoleOnObject(USER_PREFIX + userId, TENANT_PREFIX + tenantId, TENANT_ROLES);

      if (currentRole != null && currentRole.equals(newRole)) {
        log.debug("Role unchanged, skipping update");
        return;
      }

      if (currentRole != null) {
        ClientWriteRequest request = new ClientWriteRequest()
            .writes(List.of(new ClientTupleKey()
                .user(USER_PREFIX + userId)
                .relation(newRole)
                ._object(TENANT_PREFIX + tenantId)))
            .deletes(List.of(new ClientTupleKeyWithoutCondition()
                .user(USER_PREFIX + userId)
                .relation(currentRole)
                ._object(TENANT_PREFIX + tenantId)));
        client.write(request).get();
      } else {
        ClientWriteRequest request = new ClientWriteRequest()
            .writes(List.of(new ClientTupleKey()
                .user(USER_PREFIX + userId)
                .relation(newRole)
                ._object(TENANT_PREFIX + tenantId)));
        client.write(request).get();
      }
    });
  }

  /**
   * Get a user's role in a project by reading stored tuples.
   *
   * @param userId    User ID
   * @param projectId Project ID
   * @return Single emitting the role if assigned, empty otherwise
   */
  public Single<Optional<String>> getUserRoleInProject(String userId, String projectId) {
    if (!enabled) {
      log.debug("[DISABLED] getUserRoleInProject: user={}, project={}", userId, projectId);
      return Single.just(Optional.empty());
    }
    return Single.fromCallable(() -> {
      String role = findUserRoleOnObject(USER_PREFIX + userId, PROJECT_PREFIX + projectId, PROJECT_ROLES);
      log.debug("User role in project: user={}, project={} -> {}", userId, projectId, role);
      return Optional.ofNullable(role);
    });
  }

  /**
   * Get a user's role in a tenant by reading stored tuples.
   *
   * @param userId   User ID
   * @param tenantId Tenant ID
   * @return Single emitting the role if assigned, empty otherwise
   */
  public Single<Optional<String>> getUserTenantRole(String userId, String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] getUserTenantRole: user={}, tenant={}", userId, tenantId);
      return Single.just(Optional.empty());
    }
    return Single.fromCallable(() -> {
      String role = findUserRoleOnObject(USER_PREFIX + userId, TENANT_PREFIX + tenantId, TENANT_ROLES);
      log.debug("User role in tenant: user={}, tenant={} -> {}", userId, tenantId, role);
      return Optional.ofNullable(role);
    });
  }

  /**
   * Get all tenants a user belongs to.
   *
   * @param userId User ID
   * @return Single emitting list of tenant IDs
   */
  public Single<List<String>> getUserTenants(String userId) {
    if (!enabled) {
      log.debug("[DISABLED] getUserTenants: user={}", userId);
      return Single.just(new ArrayList<>());
    }
    return Single.fromCallable(() -> {
      Set<String> tenantIds = new HashSet<>();
      for (String role : TENANT_ROLES) {
        ClientListObjectsRequest request = new ClientListObjectsRequest()
            .user(USER_PREFIX + userId)
            .relation(role)
            .type("tenant");
        var response = client.listObjects(request).get();
        var objects = response.getObjects();
        if (objects != null) {
          objects.stream()
              .map(obj -> obj.startsWith(TENANT_PREFIX) ? obj.substring(TENANT_PREFIX.length()) : obj)
              .forEach(tenantIds::add);
        }
      }
      log.debug("getUserTenants: user={} -> {} tenant(s)", userId, tenantIds.size());
      return new ArrayList<>(tenantIds);
    });
  }

  /**
   * Get all members of a tenant.
   *
   * @param tenantId Tenant ID
   * @return Single emitting set of user IDs
   */
  public Single<Set<String>> getTenantMembers(String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] getTenantMembers: tenant={}", tenantId);
      return Single.just(new HashSet<>());
    }
    return Single.fromCallable(() -> {
      ClientReadRequest request = new ClientReadRequest()
          ._object(TENANT_PREFIX + tenantId);
      var response = client.read(request).get();
      var tuples = response.getTuples();

      if (tuples == null || tuples.isEmpty()) {
        log.debug("getTenantMembers: tenant={} -> empty set", tenantId);
        return new HashSet<String>();
      }

      Set<String> members = tuples.stream()
          .map(Tuple::getKey)
          .filter(key -> key.getUser() != null && key.getUser().startsWith(USER_PREFIX))
          .filter(key -> TENANT_ROLES.contains(key.getRelation()))
          .map(key -> key.getUser().substring(USER_PREFIX.length()))
          .collect(Collectors.toSet());

      log.debug("getTenantMembers: tenant={} -> {} member(s)", tenantId, members.size());
      return members;
    });
  }

  /**
   * Get all members of a project.
   *
   * @param projectId Project ID
   * @return Single emitting set of user IDs
   */
  public Single<Set<String>> getProjectMembers(String projectId) {
    if (!enabled) {
      log.debug("[DISABLED] getProjectMembers: project={}", projectId);
      return Single.just(new HashSet<>());
    }
    return Single.fromCallable(() -> {
      ClientReadRequest request = new ClientReadRequest()
          ._object(PROJECT_PREFIX + projectId);
      var response = client.read(request).get();
      var tuples = response.getTuples();

      if (tuples == null || tuples.isEmpty()) {
        log.debug("getProjectMembers: project={} -> empty set", projectId);
        return new HashSet<String>();
      }

      Set<String> members = tuples.stream()
          .map(Tuple::getKey)
          .filter(key -> key.getUser() != null && key.getUser().startsWith(USER_PREFIX))
          .filter(key -> PROJECT_ROLES.contains(key.getRelation()))
          .map(key -> key.getUser().substring(USER_PREFIX.length()))
          .collect(Collectors.toSet());

      log.debug("getProjectMembers: project={} -> {} member(s)", projectId, members.size());
      return members;
    });
  }

  /**
   * Remove a user from a project by deleting their role tuple.
   *
   * @param userId    User ID
   * @param projectId Project ID
   * @return Completable that completes when the member is removed
   */
  public Completable removeProjectMember(String userId, String projectId) {
    if (!enabled) {
      log.debug("[DISABLED] removeProjectMember: user={}, project={}", userId, projectId);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Removing user from project: user={}, project={}", userId, projectId);
      String role = findUserRoleOnObject(USER_PREFIX + userId, PROJECT_PREFIX + projectId, PROJECT_ROLES);
      if (role == null) {
        log.debug("User has no role in project, nothing to remove");
        return;
      }
      ClientWriteRequest request = new ClientWriteRequest()
          .deletes(List.of(new ClientTupleKeyWithoutCondition()
              .user(USER_PREFIX + userId)
              .relation(role)
              ._object(PROJECT_PREFIX + projectId)));
      client.write(request).get();
    });
  }

  /**
   * Remove a user from a tenant and cascade-delete from all projects in that tenant.
   *
   * @param userId   User ID
   * @param tenantId Tenant ID
   * @return Completable that completes when the member is removed
   */
  public Completable removeTenantMember(String userId, String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] removeTenantMember: user={}, tenant={}", userId, tenantId);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Removing user from tenant (with cascade): user={}, tenant={}", userId, tenantId);

      // 1. Delete tenant role tuple
      String tenantRole = findUserRoleOnObject(USER_PREFIX + userId, TENANT_PREFIX + tenantId, TENANT_ROLES);
      if (tenantRole != null) {
        ClientWriteRequest deleteRequest = new ClientWriteRequest()
            .deletes(List.of(new ClientTupleKeyWithoutCondition()
                .user(USER_PREFIX + userId)
                .relation(tenantRole)
                ._object(TENANT_PREFIX + tenantId)));
        client.write(deleteRequest).get();
      }

      // 2. Cascade: remove from all projects in this tenant
      List<String> projectsInTenant = readProjectsInTenant(tenantId);
      for (String projectId : projectsInTenant) {
        String projectRole = findUserRoleOnObject(USER_PREFIX + userId, PROJECT_PREFIX + projectId, PROJECT_ROLES);
        if (projectRole != null) {
          ClientWriteRequest deleteRequest = new ClientWriteRequest()
              .deletes(List.of(new ClientTupleKeyWithoutCondition()
                  .user(USER_PREFIX + userId)
                  .relation(projectRole)
                  ._object(PROJECT_PREFIX + projectId)));
          client.write(deleteRequest).get();
        }
      }
    });
  }

  /**
   * Count users with the admin role on a tenant.
   * The authorization model uses "admin" as the highest tenant role.
   *
   * @param tenantId Tenant ID
   * @return Single emitting the number of admins
   */
  public Single<Integer> countTenantOwners(String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] countTenantOwners: tenant={}", tenantId);
      return Single.just(0);
    }
    return Single.fromCallable(() -> {
      ClientReadRequest request = new ClientReadRequest()
          .relation("admin")
          ._object(TENANT_PREFIX + tenantId);
      var response = client.read(request).get();
      var tuples = response.getTuples();

      if (tuples == null || tuples.isEmpty()) {
        return 0;
      }

      int count = (int) tuples.stream()
          .map(Tuple::getKey)
          .filter(key -> key.getUser() != null && key.getUser().startsWith(USER_PREFIX))
          .count();

      log.debug("countTenantOwners: tenant={} -> {} admin(s)", tenantId, count);
      return count;
    });
  }

  /**
   * Count users with the admin role on a project.
   *
   * @param projectId Project ID
   * @return Single emitting the number of admins
   */
  public Single<Integer> countProjectAdmins(String projectId) {
    if (!enabled) {
      log.debug("[DISABLED] countProjectAdmins: project={}", projectId);
      return Single.just(0);
    }
    return Single.fromCallable(() -> {
      ClientReadRequest request = new ClientReadRequest()
          .relation("admin")
          ._object(PROJECT_PREFIX + projectId);
      var response = client.read(request).get();
      var tuples = response.getTuples();

      if (tuples == null || tuples.isEmpty()) {
        return 0;
      }

      int count = (int) tuples.stream()
          .map(Tuple::getKey)
          .filter(key -> key.getUser() != null && key.getUser().startsWith(USER_PREFIX))
          .count();

      log.debug("countProjectAdmins: project={} -> {} admin(s)", projectId, count);
      return count;
    });
  }

  /**
   * Check if a user is a tenant owner (admin — the highest role in the authorization model).
   *
   * @param userId   User ID
   * @param tenantId Tenant ID
   * @return Single emitting true if the user is an admin of the tenant
   */
  public Single<Boolean> isTenantOwner(String userId, String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] isTenantOwner: user={}, tenant={}", userId, tenantId);
      return Single.just(false);
    }
    return Single.fromCallable(() -> {
      ClientCheckRequest request = new ClientCheckRequest()
          .user(USER_PREFIX + userId)
          .relation("admin")
          ._object(TENANT_PREFIX + tenantId);
      var response = client.check(request).get();
      boolean isOwner = Boolean.TRUE.equals(response.getAllowed());
      log.debug("isTenantOwner: user={}, tenant={} -> {}", userId, tenantId, isOwner);
      return isOwner;
    });
  }

  /**
   * Check if a user is a project admin.
   * Uses the computed "effective_admin" relation which includes both direct project
   * admins and tenant admins (via parent relationship).
   *
   * @param userId    User ID
   * @param projectId Project ID
   * @return Single emitting true if the user is an effective admin of the project
   */
  public Single<Boolean> isProjectAdmin(String userId, String projectId) {
    if (!enabled) {
      log.debug("[DISABLED] isProjectAdmin: user={}, project={}", userId, projectId);
      return Single.just(false);
    }
    return Single.fromCallable(() -> {
      ClientCheckRequest request = new ClientCheckRequest()
          .user(USER_PREFIX + userId)
          .relation("effective_admin")
          ._object(PROJECT_PREFIX + projectId);
      var response = client.check(request).get();
      boolean isAdmin = Boolean.TRUE.equals(response.getAllowed());
      log.debug("isProjectAdmin: user={}, project={} -> {}", userId, projectId, isAdmin);
      return isAdmin;
    });
  }

  /**
   * Link a project to a tenant by writing the parent relationship tuple.
   *
   * @param projectId Project ID
   * @param tenantId  Tenant ID
   * @return Completable that completes when the tuple is written
   */
  public Completable linkProjectToTenant(String projectId, String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] linkProjectToTenant: project={}, tenant={}", projectId, tenantId);
      return Completable.complete();
    }
    return Completable.fromAction(() -> {
      log.info("Linking project to tenant: project={}, tenant={}", projectId, tenantId);
      ClientWriteRequest request = new ClientWriteRequest()
          .writes(List.of(new ClientTupleKey()
              .user(TENANT_PREFIX + tenantId)
              .relation("parent")
              ._object(PROJECT_PREFIX + projectId)));
      client.write(request).get();
    });
  }

  /**
   * Check if a user is a tenant admin.
   * Uses the computed "can_manage_tenant" relation from the authorization model.
   *
   * @param userId   User ID
   * @param tenantId Tenant ID
   * @return Single emitting true if the user can manage the tenant
   */
  public Single<Boolean> isTenantAdmin(String userId, String tenantId) {
    if (!enabled) {
      log.debug("[DISABLED] isTenantAdmin: user={}, tenant={}", userId, tenantId);
      return Single.just(false);
    }
    return Single.fromCallable(() -> {
      ClientCheckRequest request = new ClientCheckRequest()
          .user(USER_PREFIX + userId)
          .relation("can_manage_tenant")
          ._object(TENANT_PREFIX + tenantId);
      var response = client.check(request).get();
      boolean isAdmin = Boolean.TRUE.equals(response.getAllowed());
      log.debug("isTenantAdmin: user={}, tenant={} -> {}", userId, tenantId, isAdmin);
      return isAdmin;
    });
  }

  /**
   * Get all project IDs a user has access to (any role).
   *
   * @param userId User ID
   * @return Single emitting list of project IDs
   */
  public Single<List<String>> getUserProjects(String userId) {
    if (!enabled) {
      log.debug("[DISABLED] getUserProjects: user={}", userId);
      return Single.just(new ArrayList<>());
    }
    return Single.fromCallable(() -> {
      Set<String> projectIds = new HashSet<>();
      for (String role : PROJECT_ROLES) {
        ClientListObjectsRequest request = new ClientListObjectsRequest()
            .user(USER_PREFIX + userId)
            .relation(role)
            .type("project");
        var response = client.listObjects(request).get();
        var objects = response.getObjects();
        if (objects != null) {
          objects.stream()
              .map(obj -> obj.startsWith(PROJECT_PREFIX) ? obj.substring(PROJECT_PREFIX.length()) : obj)
              .forEach(projectIds::add);
        }
      }
      log.debug("getUserProjects: user={} -> {} project(s)", userId, projectIds.size());
      return new ArrayList<>(projectIds);
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // INTERNAL HELPERS
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Read stored tuples to find a user's direct role on an object.
   * Only considers relations in the given allowedRoles set.
   */
  private String findUserRoleOnObject(String user, String object, Set<String> allowedRoles) throws Exception {
    ClientReadRequest request = new ClientReadRequest()
        .user(user)
        ._object(object);
    var response = client.read(request).get();
    var tuples = response.getTuples();

    if (tuples == null || tuples.isEmpty()) {
      return null;
    }

    return tuples.stream()
        .map(Tuple::getKey)
        .map(key -> key.getRelation())
        .filter(allowedRoles::contains)
        .findFirst()
        .orElse(null);
  }

  /**
   * Read all project IDs linked to a tenant via the "parent" relation.
   */
  private List<String> readProjectsInTenant(String tenantId) throws Exception {
    ClientListObjectsRequest request = new ClientListObjectsRequest()
        .user(TENANT_PREFIX + tenantId)
        .relation("parent")
        .type("project");
    var response = client.listObjects(request).get();
    var objects = response.getObjects();

    if (objects == null || objects.isEmpty()) {
      return new ArrayList<>();
    }

    return objects.stream()
        .map(obj -> obj.startsWith(PROJECT_PREFIX) ? obj.substring(PROJECT_PREFIX.length()) : obj)
        .collect(Collectors.toList());
  }
}
