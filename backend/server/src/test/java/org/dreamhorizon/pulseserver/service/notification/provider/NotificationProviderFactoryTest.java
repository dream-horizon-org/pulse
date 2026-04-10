package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationProviderFactoryTest {

  @Nested
  class GetProvider {

  @Test
  void shouldReturnEmptyWhenNoProvidersRegistered() {
    NotificationProviderFactory factory = new NotificationProviderFactory(Collections.emptySet());

    assertThat(factory.getProvider(ChannelType.EMAIL)).isEmpty();
    assertThat(factory.getProvider(ChannelType.SLACK)).isEmpty();
    assertThat(factory.getProvider(ChannelType.TEAMS)).isEmpty();
  }

  @Test
  void shouldReturnProviderWhenRegistered() {
    NotificationProvider emailProvider = createMockProvider(ChannelType.EMAIL);
    NotificationProviderFactory factory = new NotificationProviderFactory(Set.of(emailProvider));

    assertThat(factory.getProvider(ChannelType.EMAIL)).contains(emailProvider);
  }

  @Test
  void shouldReturnEmptyForUnregisteredChannelType() {
    NotificationProvider emailProvider = createMockProvider(ChannelType.EMAIL);
    NotificationProviderFactory factory = new NotificationProviderFactory(Set.of(emailProvider));

    assertThat(factory.getProvider(ChannelType.SLACK)).isEmpty();
  }

  @Test
  void shouldAllowRegisteringProviderDynamically() {
    NotificationProviderFactory factory = new NotificationProviderFactory(Collections.emptySet());
    NotificationProvider slackProvider = createMockProvider(ChannelType.SLACK);

    factory.registerProvider(slackProvider);

    assertThat(factory.getProvider(ChannelType.SLACK)).contains(slackProvider);
  }

  @Test
  void shouldOverwriteProviderWhenRegisteringSameChannelType() {
    NotificationProvider firstProvider = createMockProvider(ChannelType.EMAIL);
    NotificationProvider secondProvider = createMockProvider(ChannelType.EMAIL);
    NotificationProviderFactory factory = new NotificationProviderFactory(Set.of(firstProvider));

    factory.registerProvider(secondProvider);

    assertThat(factory.getProvider(ChannelType.EMAIL)).contains(secondProvider);
  }

  @Test
  void shouldRegisterMultipleProvidersFromConstructor() {
    NotificationProvider emailProvider = createMockProvider(ChannelType.EMAIL);
    NotificationProvider slackProvider = createMockProvider(ChannelType.SLACK);
    NotificationProviderFactory factory = new NotificationProviderFactory(Set.of(emailProvider, slackProvider));

    assertThat(factory.getProvider(ChannelType.EMAIL)).contains(emailProvider);
    assertThat(factory.getProvider(ChannelType.SLACK)).contains(slackProvider);
  }
  }

  private static NotificationProvider createMockProvider(ChannelType type) {
    return new NotificationProvider() {
      @Override
      public ChannelType getChannelType() {
        return type;
      }

      @Override
      public io.reactivex.rxjava3.core.Single<org.dreamhorizon.pulseserver.service.notification.models.NotificationResult> send(
          org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage message,
          org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate template) {
        return io.reactivex.rxjava3.core.Single.never();
      }
    };
  }
}
