package org.dreamhorizon.pulseserver.service.notification.provider;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;

public interface NotificationProvider {

  ChannelType getChannelType();

  Single<NotificationResult> send(NotificationMessage message, NotificationTemplate template);
}
