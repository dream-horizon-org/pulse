package com.pulse.utils

public object PulseMathUtils {
    public fun gcd(
        a: Int,
        b: Int,
    ): Int = if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)

    public fun gcd(
        a: Long,
        b: Long,
    ): Long = if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)
}
