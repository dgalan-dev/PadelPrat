package com.dgalan.padelprat.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute : NavKey

/** Contenedor con la barra inferior y las pestañas (ver [TopLevelDestination]). */
@Serializable
data object MainRoute : NavKey
