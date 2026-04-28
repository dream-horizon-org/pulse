package org.dreamhorizon.pulseserver.resources.v1.admin;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.InternalRoleMemberDto;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.SuperAdminsListResponse;
import org.dreamhorizon.pulseserver.service.UserService;

/** Builds admin list responses with DB email enrichment. */
public final class AdminRoleListResponseFactory {

  private AdminRoleListResponseFactory() {}

  public static Single<SuperAdminsListResponse> build(Set<String> userIds, UserService userService) {
    List<String> sortedIds = new ArrayList<>(userIds);
    sortedIds.sort(String::compareTo);
    if (sortedIds.isEmpty()) {
      return Single.just(
          SuperAdminsListResponse.builder().userIds(List.of()).members(List.of()).build());
    }
    return userService
        .getUsersByIds(sortedIds)
        .map(
            users -> {
              Map<String, User> byId = new HashMap<>();
              for (User u : users) {
                byId.put(u.getUserId(), u);
              }
              List<InternalRoleMemberDto> members = new ArrayList<>();
              for (String id : sortedIds) {
                User u = byId.get(id);
                members.add(
                    InternalRoleMemberDto.builder()
                        .userId(id)
                        .email(u != null ? u.getEmail() : null)
                        .build());
              }
              members.sort(
                  Comparator.comparing(
                          InternalRoleMemberDto::getEmail,
                          Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                      .thenComparing(InternalRoleMemberDto::getUserId));
              return SuperAdminsListResponse.builder()
                  .userIds(sortedIds)
                  .members(members)
                  .build();
            });
  }

  /**
   * Resolves FGA user id from request fields: exactly one of {@code userId} or {@code email}
   * must be non-blank.
   */
  public static Single<String> resolveTargetUserId(
      String userId, String email, UserService userService) {
    String uid = StringUtils.trimToEmpty(userId);
    String em = StringUtils.trimToEmpty(email);
    if (StringUtils.isNotBlank(uid) && StringUtils.isNotBlank(em)) {
      return Single.error(
          ServiceError.INVALID_REQUEST_PARAM.getCustomException(
              "Provide only one of userId or email"));
    }
    if (StringUtils.isNotBlank(uid)) {
      return userService
          .getUsersByIds(List.of(uid))
          .flatMap(
              users -> {
                if (users.isEmpty()) {
                  return Single.error(
                      ServiceError.NOT_FOUND.getCustomException(
                          "No user registered with this userId"));
                }
                return Single.just(users.get(0).getUserId());
              });
    }
    if (StringUtils.isNotBlank(em)) {
      String normalized = em.toLowerCase(java.util.Locale.ROOT);
      return userService
          .getUserByEmail(normalized)
          .switchIfEmpty(
              Maybe.error(
                  ServiceError.NOT_FOUND.getCustomException(
                      "No user registered with this email")))
          .map(User::getUserId)
          .toSingle();
    }
    return Single.error(
        ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
            "userId or email is required"));
  }
}
