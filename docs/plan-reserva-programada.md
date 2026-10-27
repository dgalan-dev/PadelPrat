# Plan: Reserva programada

Reservar automáticamente una pista de un día que todavía no se puede reservar, en cuanto la web abra ese día.
Decisiones tomadas al final.

La app **no se salta el plazo** (espera a que la web abra el día), **consulta poco** y solo alrededor de la
apertura, y **respeta el límite** de 2 reservas activas del club.

## Cómo abre la web cada día

- **Confirmado por el club (23/10/2026): cada día se abre para reservar a las 9:00, hora de Madrid, 7 días antes.**
- Lo que se ve en la web (medido el 23/10/2026, solo lectura): la parrilla de un día aún cerrado responde 200
  **sin `var timetable`** y con el aviso *"Lo sentimos, sólo se permiten reservas hasta 7 días, 11 horas,
  14 minutos"*. Ese tiempo es lo que falta hasta `nowUtcMaxAvailability`, que es el cierre (23:00) del último
  día abierto; va bajando y salta unas 24 h cuando se abre el día siguiente.
- El primer día cerrado se deduce de `nowUtcMaxAvailability`: es el día siguiente al de esa fecha.

## Funcionamiento

1. **Programar**: elegir día (de los aún cerrados, hasta 14 días por delante), hora de inicio, duración
   (90/60) y **hasta 3 pistas por orden de preferencia**. Se guarda en el móvil (DataStore) y sobrevive a
   reinicios.
2. **Esperar** con `AlarmManager.setAlarmClock` (la alarma de los despertadores: el sistema no la retrasa ni
   en reposo profundo) a las **8:58** del día de apertura (D − 7), según el reloj del móvil. Dos minutos de
   margen cubren un móvil atrasado hasta 2 minutos y solo cuestan un minuto más de espera. Se vuelven a programar
   al reiniciar el móvil.
3. **Preparar (8:58)**: servicio en primer plano corto con su notificación ("Esperando a que abra el jueves
   5…"). Una consulta para leer `nowUtc` y calcular el desfase entre el reloj del móvil y el del club. Con
   "Fecha y hora automáticas" suele ser de menos de un segundo; si pasa de 30 s, la notificación del resultado
   avisa de que el reloj del móvil está desajustado (la reserva se hace igual, esperando según la hora del club).
4. **Esperar a las 8:59:58 del club** y desde ahí consultar el día, como mucho 2 minutos, hasta que la
   parrilla aparezca: cada 250 ms hasta las 9:00:05 y luego cada segundo. Se empieza 2 s antes porque
   `nowUtc` viene sin milisegundos y el desfase medido puede quedarse corto hasta un segundo; un día aún
   cerrado responde sin parrilla, así que consultar antes no reserva nada. Las consultas van de una en una.
5. **Reservar** en cuanto el día aparece: el flujo de siempre (pre-reserva, duración, confirmación con "Pagar
   en el club" y comprobación del mensaje de éxito). Si la primera pista no está libre a esa hora, la segunda,
   y luego la tercera. Nunca más de una reserva por programación. Tarda 1–2 s tras la apertura.
   Si se corta al confirmar, se mira en Mis reservas (hasta 3 veces, cada 3 s) si sale hecha a esa hora, con esa
   duración y en esa pista: si sale, se avisa de que está reservada; si no, de que hay que comprobarlo. Solo
   cambia el aviso: nunca se prueba otra pista, porque el listado puede tardar en mostrar la reserva.
6. **Avisar** con una notificación del resultado ("Reservada la Pista 2 el jueves 5 a las 18:30" o el motivo
   de que no se haya podido).

- Antes de intentarlo se comprueba el límite de 2 reservas activas; si ya hay 2, no se intenta y se avisa.
- **Batería:** prácticamente nada. Hasta el día de apertura solo hay una alarma del sistema; ese día, uno o dos
  minutos de red.
- **Condición:** el móvil encendido y con internet a las 9:00, y la app **sin restricciones de batería** (si no,
  Samsung puede bloquear la alarma). Si otro socio reserva la misma hora en el mismo segundo, puede quedársela;
  para eso están la segunda y la tercera pista.

## Pantallas

- **Reservar**: tras el último día abierto, más días (hasta 14) en el selector con un icono de reloj; al elegir
  uno se ve "Este día se abre el jueves a las 9:00" y el formulario para programar.
- **Mis reservas → Próximas**: las programadas arriba, distintas de las reservas reales: borde discontinuo,
  icono de reloj y etiqueta "Programada" (no lima), "Se intentará reservar el jueves 5 a las 9:00", las pistas
  por orden ("Pista 2 → Pista 5 → Pista 1") y "Cancelar programación".
- **Permisos**: al entrar en la pantalla principal la primera vez, se piden las notificaciones y quitar la
  optimización de batería.

## Fases

1. **Programar y ver** — hecha (23/10/2026): días cerrados en Reservar hasta 14 días, formulario (horas sacadas de
   la parrilla del mismo día de la semana anterior, hasta 3 pistas por orden), programadas en Próximas y
   cancelarlas. Se guardan en DataStore como JSON. Todavía no se ejecutan.
2. **El motor** — hecha (23/10/2026): `ScheduledBookingAlarms` (`setAlarmClock` a las 8:58), `ScheduledBookingReceiver`,
   `ScheduledBookingService` (primer plano `dataSync` + wake lock de 6 min como máximo), `ScheduledBookingRunner`
   (sesión, límite, desfase del reloj, espera a las 8:59:58 del club, consulta cada 250 ms y luego cada segundo
   2 min, reserva por orden de pistas) y `ScheduledBookingNotifications`. Probado en el móvil con el modo de prueba de debug ("Probar ahora" en
   la tarjeta programada: usa el día de la semana anterior y para antes de la pre-reserva): alarma exacta
   registrada, servicio, salto a la siguiente pista y notificaciones. **Falta la primera reserva real a las 9:00.**
3. **Permisos y robustez** — hecha (23/10/2026):
   - Al entrar en la pantalla principal se piden las notificaciones y, una sola vez, quitar la optimización de
     batería (`StartupPermissions`, `BatteryOptimization`). Si sigue restringida, el formulario de programar muestra
     un aviso con "Quitar restricción", que se vuelve a comprobar al volver a la app.
   - `ScheduledBookingRescheduler.reconcile()` repasa las programaciones al abrir la app (`MainViewModel`) y en
     `RescheduleReceiver` (reinicio, actualización de la app, cambio de hora o de zona y permiso de alarmas exactas):
     - antes de las 8:58 del día de apertura: vuelve a poner la alarma;
     - pasada la apertura pero antes del partido (el móvil estaba apagado o la app detenida): lo intenta en 5 s;
     - con el partido ya empezado: la quita y avisa de que no se pudo intentar (`Missed`);
     - si el servicio murió con la reserva a medias (`bookingStarted`): la quita y avisa de comprobarlo en Mis
       reservas (`Uncertain`), sin reintentar para no reservar dos veces.
   - `ScheduledRunRegistry` evita que la alarma y `reconcile` lancen la misma programación a la vez.

## Decisiones tomadas (23/10/2026)

- Seguir adelante, respetando el límite de 2 reservas, y publicarla en GitHub con la función incluida.
- Hasta 3 pistas por programación, a la hora elegida.
- Programar hasta 14 días por delante.
- Las programadas en Próximas, con un estilo distinto.
- Pedir los permisos al iniciar la app.
