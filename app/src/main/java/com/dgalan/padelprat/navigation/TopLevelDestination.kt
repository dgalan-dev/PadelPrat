package com.dgalan.padelprat.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dgalan.padelprat.R

/**
 * Pestañas de la barra inferior, en el orden en que aparecen. La primera es la de inicio. La activa muestra su icono
 * relleno ([selectedIcon]) y las demás el contorno ([icon]).
 */
enum class TopLevelDestination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int
) {
    Booking(R.string.nav_booking, R.drawable.ic_nav_booking, R.drawable.ic_nav_booking_selected),
    Reservations(R.string.nav_reservations, R.drawable.ic_nav_reservations, R.drawable.ic_nav_reservations_selected),
    Profile(R.string.nav_profile, R.drawable.ic_nav_profile, R.drawable.ic_nav_profile_selected)
}
