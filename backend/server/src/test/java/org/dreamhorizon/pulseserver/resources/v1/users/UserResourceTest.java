package org.dreamhorizon.pulseserver.resources.v1.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.ws.rs.WebApplicationException;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.UserProjectsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserResourceTest {

  @Mock
  private UserProjectsService userProjectsService;

  @Mock
  private JwtService jwtService;

  private UserResource userResource;

  @BeforeEach
  void setUp() {
    userResource = new UserResource(userProjectsService, jwtService);
  }

  @Test
  void getUserProjects_shouldReturn401WhenAuthorizationMissing() {
    assertThatThrownBy(() -> userResource.getUserProjects(null))
        .isInstanceOf(WebApplicationException.class)
        .satisfies(ex ->
            assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(401));
  }

  @Test
  void getUserProjects_shouldReturn401WhenJwtExpired() {
    when(jwtService.verifyToken(anyString()))
        .thenThrow(new ExpiredJwtException(null, null, "expired"));

    assertThatThrownBy(() -> userResource.getUserProjects("Bearer token"))
        .isInstanceOf(WebApplicationException.class)
        .satisfies(ex ->
            assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(401));
  }
}
