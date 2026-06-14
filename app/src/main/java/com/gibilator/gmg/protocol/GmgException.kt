package com.gibilator.gmg.protocol

/** Exception hierarchy for the GMG protocol library. Port of `api/exceptions.py`. */
sealed class GmgException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Socket-level failure or unreachable host. */
open class GmgConnectionException(message: String, cause: Throwable? = null) :
    GmgException(message, cause)

/** No reply received within the configured retry budget. */
class GmgTimeoutException(message: String, cause: Throwable? = null) :
    GmgConnectionException(message, cause)

/** Retries exhausted to a reachable grill; controller likely in Server Mode. */
class GmgServerModeException(message: String, cause: Throwable? = null) :
    GmgConnectionException(message, cause)

/** Malformed frame, wrong header, or truncated response. */
class GmgProtocolException(message: String, cause: Throwable? = null) :
    GmgException(message, cause)

/** Out-of-range or otherwise invalid setpoint argument. */
class GmgInvalidValueException(message: String, cause: Throwable? = null) :
    GmgException(message, cause)
