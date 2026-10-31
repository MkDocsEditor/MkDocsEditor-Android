package de.markusressel.mkdocsrestclient

/**
 * Supplies the value of the Authorization header, evaluated for every request and every
 * websocket connection, so a new session applies right away.
 */
fun interface AuthorizationProvider {

    /**
     * @return the header value, like `Bearer mds_…`, or null to send no Authorization header
     */
    fun authorization(): String?

    companion object {
        val NONE = AuthorizationProvider { null }

        /**
         * @param token the bearer token, null when signed out
         */
        fun bearer(token: () -> String?) = AuthorizationProvider { token()?.let { "Bearer $it" } }
    }
}
