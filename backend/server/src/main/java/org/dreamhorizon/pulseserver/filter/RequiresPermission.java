package org.dreamhorizon.pulseserver.filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the OpenFGA permission required by an endpoint.
 *
 * When present on a JAX-RS resource method, the {@link AuthorizationFilter}
 * uses this value instead of inferring the permission from the HTTP method.
 * This is useful for POST endpoints that are read-only (e.g. analytics queries).
 *
 * Valid values: "can_view", "can_edit", "can_delete_project"
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
