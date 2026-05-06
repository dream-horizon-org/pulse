package com.pulse.utils

/**
 * SDK log verbosity. Declaration order matches iOS `PulseLogLevel` and JS `PulseLogLevel`
 * (ordinal / numeric value 0 = most verbose, 5 = none).
 */
public enum class PulseLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE,
}
