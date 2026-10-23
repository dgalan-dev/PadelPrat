# Plan: Confirmar reserva

Basado en el apartado 5 de `docs/syltek-api.md` y en el segundo mock ("Confirmar reserva"). **Pendiente de revisar y decidir** (ver el final).

## Cómo funciona en la web del club

Reservar son dos pasos:

1. **Pre-reserva** (`POST /customerZone/newReservation`): **bloquea la pista 8 minutos** para los demás socios y devuelve un formulario con las duraciones posibles (60 o 90). Si el hueco ya no es válido, responde con una redirección sin ningún mensaje.
2. **Confirmación** (`POST /customerZone/newReservationPost`): reenvía ese formulario. Si va bien, redirige a `/booking/confirm?…`, que es la página de "Reserva confirmada".

Si se quiere una duración distinta de la que marca el formulario, antes hay que llamar a `updateduration`.

## Plan

### 1. Elegir hueco (pantalla de reservar)

Al seleccionar una hora aparece una barra fija abajo: **"Pista 2 · 18:30 · 90 min · Continuar"**. Pulsar un hueco solo lo selecciona; nada se bloquea hasta pulsar **Continuar**.

### 2. Pantalla "Confirmar reserva" (como en el mock)

- **Navegación**: se abre por encima de las pestañas, sin barra inferior, y con flecha atrás.
- **Al entrar**: hace la pre-reserva y muestra "Reservando la pista…" mientras tanto. Si la duración elegida no es la que viene marcada, llama a `updateduration`.
- **Tarjeta azul del mock**: etiqueta "Disponible", la hora en grande ("18:30"), "hasta las 20:00 · 90 min", el día y la pista.
- **Cuenta atrás**: "Tienes 7:43 para confirmar". Si llega a cero, el botón se desactiva y se avisa de que hay que volver a elegir hora.
- **Botones**: **Confirmar reserva** y **Cambiar horario**, que vuelve atrás. Al volver, la pista sigue bloqueada hasta que pasen los 8 minutos, porque la web no tiene forma de liberarla antes.

### 3. Resultado

- **Si va bien**: pantalla de "¡Reserva confirmada!" con el día, el horario y el importe (leídos de la página de confirmación), y un botón para volver. La parrilla de ese día se recarga.
- **Si el hueco ya no es válido**: se vuelve a la parrilla con un aviso y se recarga.
- **Si se corta la conexión al confirmar**: no se sabe si la reserva entró o no, así que **no se reintenta nunca solo**. Se avisa de que hay que comprobarlo en Mis reservas.
- **El botón de confirmar** se desactiva mientras se envía, para que no se pueda reservar dos veces.

### 4. Técnica

- **Jsoup**: se añade ahora, porque hay que leer el formulario y la página de confirmación. Cada página con su propio parser aislado, como la parrilla.
- **Repositorio de reserva** en `data/booking/`: `preReserve`, `changeDuration` y `confirm`. Formatea las fechas como pide la web (hora local en la pre-reserva) y reenvía el formulario tal como llega.
- **Ruta nueva** `ConfirmRoute` en la navegación raíz, con la pista, la hora y la duración.

## Decisiones pendientes

1. **Precio.** El mock muestra el desglose (pista, iluminación y total), pero según el documento, con la cuenta de socio el formulario no muestra precio y la confirmación da 0,00 €. Propuesta: **no mostrar precio** en la confirmación y enseñar solo el importe final en la pantalla de éxito.
2. **"Añadir jugadores (opcional)"** (sale en el mock): la web lo tiene, pero no está documentado ni probado. Propuesta: **dejarlo fuera** por ahora.
3. **Cómo probarlo.** Esto crea **reservas reales** en el club, y la pre-reserva bloquea la pista 8 minutos a otros socios aunque no se confirme. Propuesta:
   - Claude prueba con la sesión del usuario **una sola pre-reserva sin confirmar**, en un hueco de madrugada o de un día lejano, para no molestar a nadie, y así comprueba que se lee bien el formulario.
   - **La confirmación de verdad la hace el usuario**, con una reserva que realmente quiera.
