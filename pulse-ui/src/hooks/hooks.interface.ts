export const FILTER_MAPPING: Record<string, string> = {
    PLATFORM: "Platform",
    APP_VERSION: "AppVersion",
    NETWORK_PROVIDER: "NetworkProvider",
    OS_VERSION: "OsVersion",
    STATE: "GeoState",
};


export const EVENT_TYPE = {
    CRASH: "device.crash",
    ANR: "device.anr",
    NETWORK_ERROR: "network_error",
    FROZEN_FRAME: "app.jank.frozen",
    NON_FATAL: "non_fatal",
}