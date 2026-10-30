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
- El 30/10/2026, desde al menos las 8:52 y hasta las 9:00 (± unos segundos), el día ya salía abierto (con
  parrilla), pero sin pistas: la web mostraba *"No hay instalaciones configuradas con horario disponible"*. Por
  eso la reserva programada no da el día por abierto hasta que la parrilla trae alguna pista con horario
  (`DayTimetable.hasSchedule`), y en los reintentos una parrilla sin pistas no cuenta. En Reservar, ese día se
  enseña como cerrado (con el formulario de programar) hasta un minuto después de la apertura, y se vuelve a
  mirar al elegirlo o refrescar; pasado ese minuto, un día sin pistas se enseña tal cual.

## Funcionamiento

1. **Programar**: elegir día (de los aún cerrados, hasta 14 días por delante), hora de inicio, duración
   (90/60), **hasta 3 pistas por orden de preferencia** y, si se quiere, **una hora alternativa** (la segunda
   hora que se toca; va con borde en lugar de rellena). Se guarda en el móvil (DataStore) y sobrevive a
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
   parrilla aparezca y su `nowUtcMaxAvailability` llegue a la hora pedida (si la parrilla saliera antes de que se
   actualice el límite, sus huecos se descartarían y se daría el día por perdido justo al abrir): cada 250 ms hasta las 9:00:05 y luego cada segundo. Se empieza 2 s antes porque
   `nowUtc` viene sin milisegundos y el desfase medido puede quedarse corto hasta un segundo; un día aún
   cerrado responde sin parrilla, así que consultar antes no reserva nada. Las consultas van de una en una.
5. **Reservar** en cuanto el día aparece: el flujo de siempre (pre-reserva, duración, confirmación con "Pagar
   en el club" y comprobación del mensaje de éxito). Si la primera pista no está libre a esa hora, la segunda,
   y luego la tercera; si no sale ninguna, cualquier otra pista libre a esa hora, en el orden de la parrilla
   (siempre, sin opción para desactivarlo). Si a esa hora no sale ninguna, lo mismo a la hora alternativa: primero
   la hora y luego la pista (19:00 en las elegidas → 19:00 en cualquier otra → 19:30 en las elegidas → 19:30 en
   cualquier otra). Nunca más de una reserva por programación. Tarda 1–2 s tras la apertura.
   **Reintentos de la pre-reserva:** la web responde igual (303 a la parrilla, sin mensaje) a un hueco ocupado y
   a uno que aún no deja reservar. El 29/10/2026 la primera parrilla salió a las 8:59:59,7 del club, las dos
   pre-reservas se rechazaron así y las pistas siguieron libres unos minutos (por los ids, las cogieron otros
   socios hacia las 9:06). Una de ellas (la 10) dejaba un hueco de 30 min, que la web no permite (ver
   docs/syltek-api.md, apartado 4.6); ahora la parrilla ya no la da como libre y, si la web da un motivo, se lee
   y no se reintenta. Por eso, si la web rechaza la pre-reserva sin motivo, se vuelve a leer la parrilla y
   se prueban otra vez todas las que sigan libres, por orden, durante 30 s desde que el día sale abierto: con
   250 ms de pausa los primeros 5 s y luego 1 s (unas 15–25 vueltas). Una pre-reserva rechazada no bloquea nada,
   así que reintentarla no puede duplicar la reserva; los demás pasos siguen sin reintentarse nunca.
   Si se corta al confirmar, se mira en Mis reservas (hasta 3 veces, cada 3 s) si sale hecha a esa hora, con esa
   duración y en esa pista: si sale, se avisa de que está reservada; si no, de que hay que comprobarlo. Solo
   cambia el aviso: nunca se prueba otra pista, porque el listado puede tardar en mostrar la reserva.
6. **Avisar** con una notificación del resultado ("Reservada la Pista 2 el jueves 5 a las 18:30" o el motivo
   de que no se haya podido). Si no se consigue pista, el aviso desplegado dice qué pasó con cada pista elegida:
   ya ocupada en la primera parrilla tras la apertura, perdida al pre-reservar (otro socio se adelantó) o
   rechazada por la web con su mensaje, o rechazada sin motivo mientras seguía libre, con la hora del club (con
   décimas) a la que se leyó la parrilla, el número de intentos y a qué hora respondió el primero y el último. Lo
   mismo con cada una de las demás pistas que llegaron a salir libres.

- Antes de intentarlo se comprueba el límite de 2 reservas activas; si ya hay 2, no se intenta y se avisa.
- **Batería:** prácticamente nada. Hasta el día de apertura solo hay una alarma del sistema; ese día, uno o dos
  minutos de red.
- **Condición:** el móvil encendido y con internet a las 9:00, y la app **sin restricciones de batería** (si no,
  Samsung puede bloquear la alarma). Si otro socio reserva la misma hora en el mismo segundo, puede quedársela;
  para eso están la segunda y la tercera pista.

## Pantallas

- **Reservar**: tras el último día abierto, más días (hasta 14) en el selector con un icono de reloj; al elegir
  uno se ve "Este día se abre el jueves a las 9:00" y el formulario para programar.
- **Mis reservas → Programadas**: en su propia pestaña (al principio iban encima de Próximas), distintas de las
  reservas reales: borde discontinuo,
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
- Las programadas en Próximas, con un estilo distinto (después, en su propia pestaña, Programadas).
- Pedir los permisos al iniciar la app.
