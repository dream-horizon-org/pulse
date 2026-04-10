package org.dreamhorizon.pulseserver.service.notification.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Slf4j
@Singleton
public class NotificationProviderFactory {

  private final Map<ChannelType, NotificationProvider> providers = new ConcurrentHashMap<>();

  @Inject
  public NotificationProviderFactory(Set<NotificationProvider> providerSet) {
    providerSet.forEach(
        provider -> {
          providers.put(provider.getChannelType(), provider);
          log.info("Registered notification provider for channel: {}", provider.getChannelType());
        });
  }

  public Optional<NotificationProvider> getProvider(ChannelType channelType) {
    return Optional.ofNullable(providers.get(channelType));
  }

  public void registerProvider(NotificationProvider provider) {
    providers.put(provider.getChannelType(), provider);
  }
}
