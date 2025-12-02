package in.horizonos.pulseserver.service.session;

import com.google.inject.Inject;
import in.horizonos.pulseserver.client.chclient.ClickhouseQueryService;
import in.horizonos.pulseserver.dao.query.UserExperienceCategoriesQuery;
import in.horizonos.pulseserver.model.QueryConfiguration;
import in.horizonos.pulseserver.resources.session.models.GetSessionRequest;
import in.horizonos.pulseserver.resources.session.models.GetSessionResponse;
import io.reactivex.rxjava3.core.Single;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionService {
  private final ClickhouseQueryService clickhouseQueryService;
  private final DateTimeFormatter output = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


  private Map<String, Object> getSessionReportSubstitutionMap(GetSessionRequest request) {
    Map<String, Object> substitutionValueMap = new HashMap<>();
    substitutionValueMap.put("start_time", ZonedDateTime.parse(request.getStartTime()).format(output));
    substitutionValueMap.put("end_time", ZonedDateTime.parse(request.getEndTime()).format(output));
    substitutionValueMap.put("span_name", request.getSpanName());
    String appVersionFilter = StringUtils.EMPTY;
    String platformFilter = StringUtils.EMPTY;
    String osVersionFilter = StringUtils.EMPTY;
    String networkProviderFilter = StringUtils.EMPTY;
    String stateFilter = StringUtils.EMPTY;

    if (request.getFilters().getAppVersionFilters() != null && !request.getFilters().getAppVersionFilters().isEmpty()) {
      appVersionFilter = String.format(" AND AppVersion in (%s)", format(request.getFilters().getAppVersionFilters()));
    }

    if (request.getFilters().getPlatformFilters() != null && !request.getFilters().getPlatformFilters().isEmpty()) {
      platformFilter = String.format(" AND Platform in (%s)", format(request.getFilters().getPlatformFilters()));
    }

    if (request.getFilters().getOsVersionFilters() != null && !request.getFilters().getOsVersionFilters().isEmpty()) {
      osVersionFilter = String.format(" AND OsVersion in (%s)", format(request.getFilters().getOsVersionFilters()));
    }

    if (request.getFilters().getNetworkProviderFilters() != null && !request.getFilters().getNetworkProviderFilters().isEmpty()) {
      networkProviderFilter = String.format(" AND NetworkProvider in (%s)", format(request.getFilters().getNetworkProviderFilters()));
    }

    if (request.getFilters().getStateFilters() != null && !request.getFilters().getStateFilters().isEmpty()) {
      stateFilter = String.format(" AND State in (%s)", format(request.getFilters().getStateFilters()));
    }

    substitutionValueMap.put("app_version_filter", appVersionFilter);
    substitutionValueMap.put("platform_filter", platformFilter);
    substitutionValueMap.put("os_version_filter", osVersionFilter);
    substitutionValueMap.put("network_provider_filter", networkProviderFilter);
    substitutionValueMap.put("state_filter", stateFilter);

    return substitutionValueMap;
  }

  private String format(List<String> filters) {
    String substitute = "";
    List<String> formattedfilters = filters.stream()
        .map(id -> String.format("'%s'", id))
        .collect(Collectors.toList());

    substitute = StringUtils.join(formattedfilters, ',');
    return substitute;
  }

  public Single<GetSessionResponse> getSessions(GetSessionRequest request) {
    Map<String, Object> substitutionValueMap = getSessionReportSubstitutionMap(request);
    String formattedQuery = new StringSubstitutor(substitutionValueMap).replace(UserExperienceCategoriesQuery.GET_SESSIONS_QUERY);
    QueryConfiguration configuration = QueryConfiguration
        .newQuery(formattedQuery)
        .timeoutMs(2000)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(configuration, GetSessionResponse.Session.class)
        .map(result -> {
          return GetSessionResponse.builder()
              .sessions(result.getRows())
              .build();
        })
        .onErrorResumeNext(err -> {
          return Single.error(new Exception("Failed to fetch sessions", err));
        });
  }

}
