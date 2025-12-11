/**
 * Matches backend: AlertSeverityResponseDto.java
 * Note: 'name' is Integer (severity level), not String
 * Backend returns: List<AlertSeverityResponseDto>
 */
export type AlertSeverityItem = {
  severity_id: number;
  name: number;  // Severity level (1=Critical, 2=Warning, 3=Info)
  description: string;
};

// Backend returns an array directly
export type GetAlertSeveritiesResponse = AlertSeverityItem[];
