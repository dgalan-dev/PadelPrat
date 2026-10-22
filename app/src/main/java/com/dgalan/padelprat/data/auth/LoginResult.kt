package com.dgalan.padelprat.data.auth

sealed interface LoginResult {
    data object Success : LoginResult

    data object InvalidCredentials : LoginResult

    /** Sin conexión, servidor inaccesible o tiempo de espera agotado. */
    data object NetworkError : LoginResult

    /** Respuesta inesperada del servidor. */
    data object UnknownError : LoginResult
}
