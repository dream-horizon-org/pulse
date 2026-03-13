package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

import static org.dreamhorizon.pulseserver.dao.session.FilterCategory.*;
import static org.dreamhorizon.pulseserver.dao.session.Operator.*;

@Getter
public enum FilterField {

    // Session Properties
    DURATION("dateDiff('millisecond', min(startTime), max(endTime))", ClauseType.HAVING, true,
            "Duration (ms)", "integer", SESSION, EnumSet.of(GT, LT, GTE, LTE, BETWEEN)),
    QUALITY_SCORE("if(sum(apdexCount) > 0, sum(apdexSum) / sum(apdexCount), null)", ClauseType.HAVING, true,
            "Quality Score", "float", SESSION, EnumSet.of(GT, LT, GTE, LTE, BETWEEN)),
    SPAN_COUNT("sum(spanCount)", ClauseType.HAVING, true,
            "Span Count", "integer", SESSION, EnumSet.of(GT, LT, EQ)),
    HAS_USER_ID("anyIf(userId, userId != '')", ClauseType.HAVING, true,
            "Has User ID", "string", SESSION, EnumSet.of(EMPTY, NOT_EMPTY)),

    // User Properties
    USER_ID("anyIf(userId, userId != '')", ClauseType.HAVING, true,
            "User ID", "string", USER, EnumSet.of(EQ)),

    // Device
    PLATFORM("anyIf(platform, platform != '')", ClauseType.HAVING, true,
            "Platform", "string", DEVICE, EnumSet.of(EQ, IN)),
    APP_VERSION("anyIf(appVersion, appVersion != '')", ClauseType.HAVING, true,
            "App Version", "string", DEVICE, EnumSet.of(EQ, IN)),
    OS_VERSION("anyIf(osVersion, osVersion != '')", ClauseType.HAVING, true,
            "OS Version", "string", DEVICE, EnumSet.of(EQ, IN)),
    DEVICE_MODEL("anyIf(deviceModel, deviceModel != '')", ClauseType.HAVING, true,
            "Device Model", "string", DEVICE, EnumSet.of(EQ, IN)),
    NETWORK_PROVIDER("anyIf(networkProvider, networkProvider != '')", ClauseType.HAVING, true,
            "Network Provider", "string", DEVICE, EnumSet.of(EQ, IN)),

    // UI Interactions
    FAILED_INTERACTIONS("sum(interactionErrors)", ClauseType.HAVING, true,
            "Failed Interactions", "integer", UI_INTERACTIONS, EnumSet.of(GT, EQ)),
    SLOW_INTERACTIONS("sum(slowInteractionCount)", ClauseType.HAVING, true,
            "Slow Interactions", "integer", UI_INTERACTIONS, EnumSet.of(GT, EQ)),
    FROZEN_FRAMES("sum(frozenFrameCount)", ClauseType.HAVING, true,
            "Frozen Frames", "float", UI_INTERACTIONS, EnumSet.of(GT, EQ)),
    INTERACTION_NAME("SpanAttributes['pulse.interaction.name']", ClauseType.WHERE, false,
            "Interaction Name", "string", UI_INTERACTIONS, EnumSet.of(EQ, IN)),
    SCREEN_NAME("SpanAttributes['screen.name']", ClauseType.WHERE, false,
            "Screen Name", "string", SESSION, EnumSet.of(EQ, IN)),

    // Stability / Errors
    CRASHES("sum(crashCount)", ClauseType.HAVING, true,
            "Crashes", "integer", STABILITY, EnumSet.of(GT, EQ)),
    ANRS("sum(anrCount)", ClauseType.HAVING, true,
            "ANRs", "integer", STABILITY, EnumSet.of(GT, EQ)),
    NON_FATALS("sum(nonFatal)", ClauseType.HAVING, true,
            "Non-Fatals", "integer", STABILITY, EnumSet.of(GT, EQ)),
    NETWORK_ERRORS("sum(networkErrors)", ClauseType.HAVING, true,
            "Network Errors", "integer", STABILITY, EnumSet.of(GT, EQ)),

    // Geography
    COUNTRY("anyIf(geoCountry, geoCountry != '')", ClauseType.HAVING, true,
            "Country", "string", GEOGRAPHY, EnumSet.of(EQ, IN)),
    REGION("anyIf(geoRegion, geoRegion != '')", ClauseType.HAVING, true,
            "Region / State", "string", GEOGRAPHY, EnumSet.of(EQ, IN));

    private final String expression;
    private final ClauseType clauseType;
    private final boolean inMV;
    private final String displayName;
    private final String dataType;
    private final FilterCategory category;
    private final Set<Operator> allowedOperators;

    FilterField(String expression, ClauseType clauseType, boolean inMV,
                String displayName, String dataType, FilterCategory category,
                Set<Operator> allowedOperators) {
        this.expression = expression;
        this.clauseType = clauseType;
        this.inMV = inMV;
        this.displayName = displayName;
        this.dataType = dataType;
        this.category = category;
        this.allowedOperators = allowedOperators;
    }

    public enum ClauseType { WHERE, HAVING }
}
