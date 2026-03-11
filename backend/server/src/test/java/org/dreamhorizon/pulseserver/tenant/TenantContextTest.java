package org.dreamhorizon.pulseserver.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TenantContext.
 * TenantContext uses Vert.x Context when available, falling back to ThreadLocal when not.
 * In unit tests, Vertx.currentContext() returns null, so ThreadLocal is used.
 */
class TenantContextTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class TenantIdOperations {

    @Test
    void shouldSetAndGetTenantId() {
      TenantContext.setTenantId("tenant-123");

      assertThat(TenantContext.getTenantId()).isEqualTo("tenant-123");
    }

    @Test
    void shouldReturnNullWhenTenantIdNotSet() {
      assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldGetCurrentTenantIdAsOptional() {
      TenantContext.setTenantId("tenant-456");

      Optional<String> result = TenantContext.getCurrentTenantId();

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo("tenant-456");
    }

    @Test
    void shouldReturnEmptyOptionalWhenTenantIdNotSet() {
      Optional<String> result = TenantContext.getCurrentTenantId();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldRequireTenantIdWhenSet() {
      TenantContext.setTenantId("tenant-789");

      String result = TenantContext.requireTenantId();

      assertThat(result).isEqualTo("tenant-789");
    }

    @Test
    void shouldThrowWhenRequireTenantIdCalledWithoutTenant() {
      assertThatThrownBy(TenantContext::requireTenantId)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No tenant context is set");
    }
  }

  @Nested
  class TenantOperations {

    @Test
    void shouldSetAndGetTenant() {
      Tenant tenant = Tenant.builder()
          .tenantId("tenant-abc")
          .name("Test Tenant")
          .build();

      TenantContext.setTenant(tenant);

      assertThat(TenantContext.getTenantId()).isEqualTo("tenant-abc");
    }

    @Test
    void shouldHandleNullTenant() {
      TenantContext.setTenantId("initial-tenant");
      TenantContext.setTenant(null);

      assertThat(TenantContext.getTenantId()).isEqualTo("initial-tenant");
    }
  }

  @Nested
  class UserIdOperations {

    @Test
    void shouldSetAndGetUserId() {
      TenantContext.setUserId("user-123");

      assertThat(TenantContext.getUserId()).isEqualTo("user-123");
    }

    @Test
    void shouldReturnNullWhenUserIdNotSet() {
      assertThat(TenantContext.getUserId()).isNull();
    }
  }

  @Nested
  class ClearOperations {

    @Test
    void shouldClearAllContext() {
      TenantContext.setTenantId("tenant-1");
      TenantContext.setUserId("user-1");

      TenantContext.clear();

      assertThat(TenantContext.getTenantId()).isNull();
      assertThat(TenantContext.getUserId()).isNull();
      assertThat(TenantContext.getCurrentTenantId()).isEmpty();
    }

    @Test
    void shouldAllowSettingAfterClear() {
      TenantContext.setTenantId("tenant-1");
      TenantContext.clear();
      TenantContext.setTenantId("tenant-2");

      assertThat(TenantContext.getTenantId()).isEqualTo("tenant-2");
    }
  }
}
