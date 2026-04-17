package com.pulse.utils

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

public object RedactionUtils {
    public fun classifyError(throwable: Throwable): String =
        when (throwable) {
            is SocketTimeoutException -> "timeout"
            is UnknownHostException -> "dns_resolution_failed"
            is ConnectException -> "connection_refused"
            is SSLException -> "ssl_error"
            is IOException -> "io_error"
            is retrofit2.HttpException -> {
                val code = throwable.code()
                when {
                    code in 400..499 -> "http_$code"
                    code in 500..599 -> "http_$code"
                    else -> "http_$code"
                }
            }
            else -> "unknown"
        }
}
