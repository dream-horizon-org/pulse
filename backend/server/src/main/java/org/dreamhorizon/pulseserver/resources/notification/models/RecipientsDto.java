package org.dreamhorizon.pulseserver.resources.notification.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientsDto {
  private List<String> emails;
  private List<String> slackChannelIds;
  private List<String> slackUserIds;
  private List<String> slackWebhookUrls;
  private List<String> teamsWorkflowUrls;
}
