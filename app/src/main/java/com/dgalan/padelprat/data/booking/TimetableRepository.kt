package com.dgalan.padelprat.data.booking

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disponibilidad de pistas. No guarda nada ni consulta en bucle: se pide al abrir la pantalla, al cambiar de día y
 * al tirar para refrescar (ver docs/syltek-api.md, apartado 7.4).
 */
@Singleton
class TimetableRepository @Inject constructor(private val remote: TimetableRemoteDataSource) {

    suspend fun timetable(date: LocalDate): TimetableResult = remote.timetable(date)
}
