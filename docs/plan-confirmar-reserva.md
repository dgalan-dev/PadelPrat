# Plan: Confirmar reserva

Basado en el apartado 5 de `docs/syltek-api.md`. Primera versión: pantalla completa como en el segundo mock. **Versión actual: bottom sheet** desde la parrilla.

## Cómo funciona en la web del club

Reservar son dos pasos:

1. **Pre-reserva** (`POST /customerZone/newReservation`): **bloquea la pista 8 minutos** para los demás socios y devuelve un formulario con las duraciones posibles (60 o 90). Si el hueco ya no es válido, responde con una redirección sin ningún mensaje.
2. **Confirmación** (`POST /customerZone/newReservationPost`): reenvía ese formulario. Si va bien, redirige a `/booking/confirm?…`, que es la página de "Reserva confirmada".

Si se quiere una duración distinta de la que marca el formulario, antes hay que llamar a `updateduration`.

## Enfoque: bottom sheet y bloqueo solo al confirmar

- **Al tocar una hora libre** se abre una hoja desde abajo con la información, **sin bloquear nada**: hora de inicio y fin, duración, día, pista, patrocinador y si es cubierta o descubierta. La parrilla sigue visible detrás; cerrar la hoja y elegir otra hora es inmediato.
- **Al pulsar "Confirmar reserva"** la app hace seguidas la pre-reserva, el cambio de duración (si hace falta) y la confirmación, eligiendo **"Pagar en el club"** (`paymentPending=true`, ver `docs/syltek-api.md` 5.4 bis). La pista queda bloqueada solo unos milisegundos en lugar de 8 minutos, así que **no hay cuenta atrás**.
- **Mientras se envía**, el botón muestra un indicador y la hoja no se puede cerrar, para que no se pueda reservar dos veces.

## Resultado

- **Si va bien** (la página del club muestra su mensaje de éxito): la hoja muestra "¡Reserva confirmada!" con una pequeña animación y el importe (leído de la página de confirmación). Al cerrarla, la parrilla del día se recarga y la hora aparece como propia.
- **Si la web pide un paso más** (la página no muestra el mensaje de éxito): "reserva incompleta", sin reintento.
- **Si el hueco ya no es válido** (otro socio lo ha cogido): aviso en la hoja y la parrilla se recarga.
- **Si se corta la conexión antes de confirmar**: no se ha reservado nada; se puede volver a intentar.
- **Si se corta la conexión al confirmar**: no se sabe si la reserva entró o no, así que **no se reintenta nunca solo** y se avisa de que hay que comprobarlo en Mis reservas.

## Técnica

- **Jsoup** para leer el formulario de la pre-reserva y la página de confirmación, cada uno con su parser aislado (como la parrilla).
- En `data/booking/`: `ReservationRemoteDataSource` (las tres peticiones), `ReservationRepository.book(pista, inicio, duración)` (las encadena) y `ReservationResult`. El formulario se reenvía tal como llega, y las fechas se formatean como pide la web (hora local en la pre-reserva).

## Decisiones (aplicadas como propuesta; se pueden cambiar)

1. **Precio**: no se muestra antes de confirmar; solo el importe final en el mensaje de éxito.
2. **"Añadir jugadores"**: fuera por ahora (no documentado ni probado).
3. **Pruebas**: confirmar crea reservas reales, así que la primera reserva de verdad la hace el usuario, con una hora que realmente quiera.
