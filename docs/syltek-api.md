# API de reservas Syltek – Padel Barcelona el Prat

Ingeniería inversa de la web de reservas del club para construir una app Android nativa.
Verificado con peticiones reales el 22/10/2026, con una cuenta de socio sin pago por reserva.

## 1. Introducción

Se puede reservar pista replicando 9 peticiones HTTP de la web del club. No hay API REST, pero el
flujo completo está verificado de punta a punta.

- **Club:** Padel Barcelona – el Prat, `https://prat.syltek.com`.
- **Plataforma:** Syltek (Matchpoint), CRM de clubes en marca blanca. Otros clubes Syltek
  probablemente usan las mismas rutas.
- **Tipo de web:** HTML renderizado en servidor y formularios `application/x-www-form-urlencoded`.
  No hay JSON salvo un objeto JavaScript embebido en la parrilla de horarios.
- **Autenticación:** una cookie de sesión de 6 meses. No hay tokens ni CSRF.

| Paso | Método y ruta | Respuesta |
| --- | --- | --- |
| Login | `POST /bookings/customer/login` | 200 + cookie |
| Logout | `GET /customer/logout` | Redirección a `/reservas` |
| Disponibilidad | `GET` o `POST /customerzone/timetable?type=56` | 200 HTML con `var timetable` |
| Pre-reserva | `POST /customerZone/newReservation` | 200 formulario (bloqueo 8 min) |
| Cambiar duración | `POST /customerzone/updateduration` | 200 formulario |
| Confirmar | `POST /customerZone/newReservationPost` | 303 a `/booking/confirm` |
| Mis reservas | `GET /customerzone/reservations` | 200 HTML |
| Detalle | `GET /customerzone/showreservation/{id}` | 200 HTML |
| Anular | `GET /customerzone/cancelReservation/{id}` | 303 al listado |

Las rutas no distinguen mayúsculas: `customerZone` y `customerzone` son equivalentes.

---

## 2. Sesión y cookies

La sesión es una sola cookie, `4358260989987`, que dura 6 meses. Basta con un `CookieJar`
persistente que guarde todo lo que envía el servidor.

| Cookie | Para qué sirve | Detalles |
| --- | --- | --- |
| `4358260989987` | Sesión del usuario | Nombre fijo (igual en todos los logins). `HttpOnly; Secure; SameSite=None; path=/`. Caduca a los 6 meses. |
| `PCRMBKLBK` | Afinidad del balanceador (probable) | Valor numérico (5, 8…). La pone el servidor aunque no haya login. Guardarla junto a la de sesión. |
| `padelclickCookieLaw` | Banner de cookies | Irrelevante. |
| `_dd_s` | Telemetría Datadog | Irrelevante. |

**Sesión caducada o ausente:** cualquier página bajo `/customerzone/` responde `303 See Other` con
`Location: /customer/login?url=<ruta original>`. La app debe usar `followRedirects(false)` para
detectarlo, volver a hacer login y repetir la petición solo si es de lectura.

No hacen falta cabeceras especiales: un `User-Agent` de navegador y, en los POST, `Origin` y
`Referer` del propio dominio, por prudencia.

---

## 3. Login y logout

### 3.1 Login

```http
POST /bookings/customer/login
Content-Type: application/x-www-form-urlencoded

email=<email o móvil>&password=<contraseña>&safari=&url=%2Fcustomerzone%2Findex
```

| Campo | Valor |
| --- | --- |
| `email` | Email o teléfono móvil (el formulario dice "MÓVIL O EMAIL"). |
| `password` | Contraseña en claro; viaja por HTTPS. |
| `safari` | Siempre vacío. Apaño de la web para Safari dentro de un iframe. |
| `url` | Ruta a la que ir después; usar `/customerzone/index`. |

No hace falta cargar antes la página de login: el formulario no tiene campos ocultos.

| Resultado | Status | Cookie de sesión | Cuerpo |
| --- | --- | --- | --- |
| Correcto | 200 | `Set-Cookie: 4358260989987=…` (6 meses) | HTML que redirige solo a `/customerzone/index`, sin `Location` |
| Incorrecto | 200 | No llega | Página de login con el error |

Mensaje de error: `.c-message-indicator--danger .c-message-indicator__content` →
"Nombre de usuario o contraseña incorrectos". Úsalo solo para mostrarlo; **decide el resultado por
la presencia de la cookie**, que no depende del idioma.

### 3.2 Logout

```http
GET /customer/logout
Cookie: 4358260989987=<sesión>
```

Redirección a `/reservas`, cuerpo vacío y `Set-Cookie: 4358260989987=; expires=<fecha pasada>`.
**Invalida la sesión en el servidor**: una cookie usada después del logout recibe el 303 al login.

---

## 4. Disponibilidad

La parrilla de un día llega como HTML con un objeto `var timetable = {…}` que trae las 14 pistas y
todas las reservas. Los huecos libres se calculan en la app. Requiere sesión.

### 4.1 Petición

```http
# Hoy
GET  /customerzone/timetable?type=56

# Un día concreto (lo que hace el selector de fecha)
POST /customerzone/timetable?type=56
Content-Type: application/x-www-form-urlencoded

type=56&date=2026-10-23

# Equivalente por GET (enlaces Hoy / Mañana / Pasado mañana)
GET  /customerZone/timetable?encodedDate=MjMvMTAvMjAyNg==&type=56
```

- `type=56` = tipo de recurso "pista de pádel".
- `date` en ISO `yyyy-MM-dd`.
- `encodedDate` = Base64 de `dd/MM/yyyy` (`MjMvMTAvMjAyNg==` = `23/10/2026`).

### 4.2 Extraer los datos

El objeto está en un `<script>` justo antes de
`timetableTable.init(timetable, "czTimetable", …)`. Es JavaScript, no JSON: claves sin comillas,
comillas simples y `new Date(año, mes, día, h, m, s)` con el mes empezando en 0.

Receta: extraerlo con una regex hasta `};`, cambiar cada `new Date(…)` por `null` y leerlo con
`org.json.JSONObject` (incluido en Android, acepta claves sin comillas y comillas simples). El modo
`isLenient` de kotlinx.serialization no admite comillas simples. Usar siempre los campos `*Utc`.

### 4.3 Campos de `timetable`

| Campo | Ejemplo | Para qué sirve |
| --- | --- | --- |
| `idCustomer` | número | Id del usuario logueado. Reconoce sus propias reservas. |
| `dateUtc` | `'2026-10-22T22:00:00Z'` | Inicio del día consultado (00:00 Madrid). |
| `nowUtc` | `'2026-10-22T15:22:54Z'` | Hora del servidor. |
| `nowUtcMaxAvailability` | `'2026-10-29T21:59:53Z'` | Límite para reservar (~7 días). |
| `startTime` / `endTime` | `'09:00'` / `'23:00'` | Horario del club. |
| `allowCreate` | `true` | Si el usuario puede crear reservas. |
| `createReservationAction` | `'/customerZone/newReservation'` | Ruta de la pre-reserva. |
| `groups` | `[{id:56, resources:[1477, …, 1490]}]` | Pistas del tipo. |
| `resources` | objeto por id | Datos de cada pista. |
| `reservations` | lista | Huecos ocupados del día. |

También aparece `webservice:'/timetableService/timetable'`, pero el JS de la parrilla no lo usa.

### 4.4 Pistas (`resources`)

```js
1477: {
  id: '1477',
  name: 'Pista 1: Solán de Cabras',
  subtype: 'Descub.',          // 'Descub.' o 'Cubierta'
  image: '/public/data/prat/Resources/1477/….jpg',
  periods: [{start:'09:00', end:'15:00', interval:30}, …]
}
```

| Ids | Pistas | Tipo |
| --- | --- | --- |
| 1477–1486 | Pista 1 a Pista 10 | Descubiertas |
| 1487–1490 | Pista 11 a Pista 14 | Cubiertas |

### 4.5 Reservas (`reservations`)

| Campo | Significado |
| --- | --- |
| `id` | Id de la reserva. |
| `idResource` | Lista con el id de la pista, p. ej. `[1488]`. |
| `startUtc` / `endUtc` | Intervalo ocupado (ISO UTC). |
| `idCustomer` | Titular. Igual a `timetable.idCustomer` = reserva propia. |
| `total` | Importe en euros. |
| `reservationsCount` | Número de jugadores. |
| `description` | "Reserva Internet", "Escuela", clases, ligas… |

### 4.6 Calcular los huecos libres

1. Para cada pista, generar los inicios posibles recorriendo sus `periods` en pasos de `interval`
   minutos (30).
2. Descartar los inicios que caen dentro de una reserva de esa pista.
3. Descartar los anteriores a `nowUtc` y los posteriores a `nowUtcMaxAvailability`.
4. La duración máxima es el tiempo hasta la siguiente reserva o el cierre. La web ofrece 60 o 90 min.

Lista de espera (no probada): `GET /customerzone/waitinglist?date=2026-10-23&idresourcetype=56`.

---

## 5. Flujo de reserva

Reservar son dos POST: la pre-reserva bloquea el hueco 8 minutos y devuelve un formulario; la
confirmación lo convierte en reserva definitiva con el mismo id.

```
 Pre-reserva ──200──▶ Formulario ──Reservar──▶ Confirmar ──303──▶ Confirmada
 newReservation      (8 min)  ▲│              newReservationPost   (mismo idReservation)
      │                       │▼ otra duración
      ▼ 303            Cambiar duración
 A la parrilla         updateduration
 (hueco no válido)
```

### 5.1 Pre-reserva

```http
POST /customerZone/newReservation
Content-Type: application/x-www-form-urlencoded

idResource=1477&localDatetime=28%2F10%2F2026+10%3A30&fromWeekTimeTable=false&calendarDate=2026%2F10%2F28
```

| Campo | Formato | Ejemplo |
| --- | --- | --- |
| `idResource` | Id de la pista | `1477` |
| `localDatetime` | Hora **local**, `dd/MM/yyyy HH:mm` | `28/10/2026 10:30` |
| `fromWeekTimeTable` | Siempre `false` | `false` |
| `calendarDate` | `yyyy/M/d`, sin ceros a la izquierda | `2026/10/28` |

| Resultado | Respuesta |
| --- | --- |
| Hueco bloqueado | 200, página "Nueva Reserva" con "Tienes 8 minutos para validar la reserva" |
| Hueco no válido | 303 a `/customerZone/timetable?encodedDate=…`, sin mensaje de error |

> **Esta llamada ya crea una reserva provisional.** No la lances para consultar precios ni
> duraciones: bloquea la pista a otros socios hasta que caduca.

### 5.2 Leer el formulario

```html
<form id="newreservation" method="post" action="/customerZone/newReservationPost">
  <input type="hidden" name="date"           value="28/10/2026 9:30:00" />
  <input type="hidden" name="idResource"     value="1477" />
  <input type="hidden" name="idReservation"  value="489334" />
  <input type="hidden" name="paymentPending" />
  <!-- duración: ver abajo -->
</form>
```

- `date` viene en **UTC** (10:30 Madrid = 09:30 UTC tras el cambio de hora). Reenviarlo tal cual.
- `idReservation` es el id de la pre-reserva y será el de la reserva final.
- Lo más robusto: reenviar todos los `input` del formulario tal y como vengan.

Duración, dos variantes de HTML:

| Caso | HTML |
| --- | --- |
| Caben 60 y 90 min | `<select name="duration">` con `<option value="60" selected>` y `<option value="90">` |
| Solo cabe una | Sin `select`: texto `readonly` ("1 hora") + `<input type="hidden" name="duration" value="60" />` |

Parseo: buscar `[name=duration]`; si es `select`, las opciones son sus `option`; si es `input`, la
única duración es su `value`.

Con esta cuenta no aparecieron jugadores, promociones ni pago. La página incluye autocompletado de
jugadores 2–4 contra `GET /Bookings/Booking/AutosuggestCustomer/56/?dateTicks=<ticks .NET>`, pero
esos campos no se mostraron.

### 5.3 Cambiar la duración (si no es la marcada por defecto)

Al cambiar el desplegable, `minutesChanged()` reenvía el mismo formulario a otra ruta y la página se
vuelve a pintar:

```http
POST /customerzone/updateduration
Content-Type: application/x-www-form-urlencoded

date=28%2F10%2F2026+9%3A00%3A00&idResource=1478&idReservation=489338&paymentPending=&duration=90
```

No verificado si la confirmación respeta `duration=90` sin pasar por aquí. Hacer lo mismo que el
navegador: `updateduration` y después confirmar con el formulario que devuelve.

### 5.4 Confirmar

```http
POST /customerZone/newReservationPost
Content-Type: application/x-www-form-urlencoded

date=28%2F10%2F2026+9%3A00%3A00&idResource=1478&idReservation=489338&paymentPending=&duration=90
```

Respuesta: `303` a `/booking/confirm?prereservationCode=<token>` → `200` "Reserva Confirmada".
El `prereservationCode` es un token opaco en Base64: no se construye, se lee del `Location`.

### 5.5 Página de confirmación

`GET /booking/confirm?prereservationCode=<token>` (recargable, solo lectura).

- Éxito: `.c-message-indicator--success` → "¡Enhorabuena! La reserva está confirmada".
- Filas `.o-form-row` (etiqueta + `span.o-form__text`): Fecha (`28/10/2026`),
  Horario (`10:00 a 11:30`), Nombre, Teléfono, Email.
- Total: `.c-reservation-resume__total .o-form__text` → `0,00 €`.
- Jugadores: `.c-slider--players .c-slide` → `.c-slide__title` (nombre), `.c-slide__subtitle` (nivel).
- **No dice la pista** (la app ya la conoce; si no, está en el detalle).

No probados: comentario `GET /booking/addcomment?idReservation=<id>&observations=<texto ≤500>` y
recordatorio `GET /bookings/booking/sendReminder/<token>`.

---

## 6. Mis reservas, detalle y anulación

El listado da fecha, hora, duración, importe y estado; la pista solo está en el detalle. Anular es
un GET que hay que verificar después en el listado.

### 6.1 Listado

`GET /customerzone/reservations` (también responde en `/bookings/customerzone/reservations`).

Dos bloques, cada uno con `h1.c-page-title__text` seguido de `div.c-page-reservations`:

1. "Próximas reservas" (incluye también las anuladas).
2. "Histórico de Reservas" (últimos 6 meses).

Cada reserva es una tarjeta `.c-card--game`:

| Dato | Selector | Ejemplo |
| --- | --- | --- |
| Estado | texto de `.c-card__status span` | `Reservada` (clase `green`), `Anulada` (clase `red`) |
| Importe | `.c-card__text-total` | `0,00 €` |
| Fecha | `.c-card__day` | `miércoles, 28 de octubre de 2026` |
| Hora y duración | `.c-card__hour` | `10:00, 90 min` |
| Ver | `a[href^='/customerzone/showreservation/']` | da el id |
| Anular | `a[href^='/customerzone/cancelReservation/']` | solo si se puede anular |

- Nombres de jugadores: solo en un comentario HTML dentro de `.playerslayout` (`<!--<span>Nombre</span>-->`).
- La fecha va en texto largo y en el idioma de la sesión. Mejor tomarla del detalle o cruzar el id
  con la parrilla.

### 6.2 Detalle

`GET /customerzone/showreservation/{id}`

| Etiqueta | Ejemplo |
| --- | --- |
| Pista | `Pista 2: San Miguel Magna` |
| Inicio | `miércoles, 28 de octubre de 2026 - 10:00` |
| Fin | `miércoles, 28 de octubre de 2026 - 11:30` |
| TOTAL CON DESCUENTO | `0,00 €` |

Las clases de las etiquetas están mal escritas en el HTML (`class="o-form-row label-text"`,
comillas rotas). Buscar la etiqueta por su **texto** y tomar el `span.o-form__text` siguiente.

### 6.3 Anular

```http
GET /customerzone/cancelReservation/{id}
```

- Respuesta: `303 See Other` → `/bookings/customerzone/reservations`, sin mensaje.
- La tarjeta sigue en "Próximas reservas" con estado `Anulada` y sin botón Anular.
- **Verificar siempre**: tras el 303, cargar el listado y comprobar `Anulada`.
- La web pide confirmación ("¿Anular la reserva?"); la app debe hacer lo mismo.
- **GET que borra datos**: nunca precargarlo, cachearlo ni repetirlo en reintentos automáticos.

---

## 7. Guía de implementación Android

Cliente HTTP con cookies persistentes, parser HTML y un repositorio que convierte cada página en
modelos de dominio. Retrofit aporta poco porque casi todo es HTML.

### 7.1 Stack

| Pieza | Librería | Notas |
| --- | --- | --- |
| HTTP | OkHttp | `followRedirects(false)` para ver los 303. |
| Cookies | `CookieJar` propio sobre DataStore | Guardar `4358260989987` y `PCRMBKLBK`. Cifrar con Tink o Keystore. |
| HTML | Jsoup | Selectores CSS de este documento. |
| Objeto `timetable` | `org.json.JSONObject` | Tras cambiar `new Date(…)` por `null`. |
| Concurrencia | Coroutines + Flow | Llamadas en `Dispatchers.IO`. |
| UI | Jetpack Compose | Parrilla por pista y hora, lista de reservas. |
| Credenciales | Android Keystore | Solo si se quiere relogin automático. |

### 7.2 Modelos

```kotlin
data class Court(
    val id: Int,
    val name: String,
    val covered: Boolean,
    val periods: List<Period>,
)

data class Period(val start: LocalTime, val end: LocalTime, val intervalMinutes: Int)

data class Booking(
    val id: Long,
    val courtId: Int,
    val start: Instant,
    val end: Instant,
    val customerId: Long?,
    val total: BigDecimal,
    val description: String,
)

data class DayTimetable(
    val customerId: Long,
    val now: Instant,
    val maxAvailability: Instant,
    val courts: List<Court>,
    val bookings: List<Booking>,
)

data class PreReservation(
    val reservationId: Long,
    val formFields: Map<String, String>,
    val durations: List<Int>,
    val selectedDuration: Int,
    val expiresAt: Instant,
)

enum class ReservationStatus { Booked, Cancelled, Unknown }
```

`PreReservation.formFields` guarda todos los `input` ocultos tal cual para reenviarlos.
`expiresAt` = hora de la respuesta + 8 minutos.

### 7.3 Repositorio

```kotlin
interface PadelRepository {
    suspend fun login(user: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun timetable(date: LocalDate): Result<DayTimetable>
    suspend fun preReserve(courtId: Int, start: LocalDateTime): Result<PreReservation>
    suspend fun changeDuration(pre: PreReservation, minutes: Int): Result<PreReservation>
    suspend fun confirm(pre: PreReservation): Result<Long>
    suspend fun myReservations(): Result<List<ReservationCard>>
    suspend fun reservationDetail(id: Long): Result<ReservationDetail>
    suspend fun cancel(id: Long): Result<Unit>
}
```

### 7.4 Reglas de comportamiento

1. **Sesión:** ante un 303 a `/customer/login`, relogin y repetir solo si la petición es de lectura.
   Sin credenciales guardadas → pantalla de login.
2. **Pre-reserva:** solo cuando el usuario pulsa un hueco. Cuenta atrás de 8 minutos, sin reintentos.
3. **Duración:** si se elige una distinta de la marcada, `updateduration` y confirmar con el
   formulario devuelto.
4. **Confirmación:** éxito = 303 con `Location` hacia `/booking/confirm`. Cualquier otra respuesta =
   error; refrescar la parrilla.
5. **Anulación:** pedir confirmación, lanzar el GET una vez y verificar `Anulada` en el listado.
6. **Zona horaria:** todo con `ZoneId.of("Europe/Madrid")`. La pre-reserva va en hora local; el
   `date` del formulario viene en UTC y se reenvía sin tocar.
7. **Ritmo:** nada de consultar la parrilla en bucle. Refresco al abrir la pantalla y al tirar hacia abajo.

### 7.5 Formatos de fecha

| Uso | Patrón | Ejemplo |
| --- | --- | --- |
| `date` en la parrilla (POST) | `yyyy-MM-dd` | `2026-10-28` |
| `encodedDate` | Base64 de `dd/MM/yyyy` | `MjgvMTAvMjAyNg==` |
| `localDatetime` | `dd/MM/yyyy HH:mm` (local) | `28/10/2026 10:30` |
| `calendarDate` | `yyyy/M/d` | `2026/10/28` |
| `date` del formulario | `dd/MM/yyyy H:mm:ss` (UTC) | `28/10/2026 9:30:00` |

---

## 8. Limitaciones y casos no probados

### Riesgos

- **API no oficial:** cualquier cambio en las páginas rompe el parseo. Aislar el parseo en clases
  pequeñas con tests sobre HTML guardado.
- **Textos en castellano:** fechas y estados dependen del idioma de la sesión
  (`/system/culture/switchlanguage/es`).
- **Uso personal:** app para uso propio con tu cuenta. No publicarla con la marca del club ni
  automatizar reservas al abrirse el plazo.
- **Credenciales:** nunca la contraseña en claro ni cookies en los logs.

### No probado

- [ ] Error al confirmar con la pre-reserva caducada (más de 8 minutos).
- [ ] Error al confirmar si otro socio ocupa el hueco.
- [ ] Confirmar con `duration=90` sin pasar por `updateduration`.
- [ ] Anular fuera de plazo o con penalización.
- [ ] Reservas de pago, con jugadores obligatorios o con promociones.
- [ ] Comentario (`/booking/addcomment`) y recordatorio (`/bookings/booking/sendReminder/…`).
- [ ] Lista de espera (`/customerzone/waitinglist`).
- [ ] Servicio `/timetableService/timetable` (declarado en la parrilla, sin uso).
- [ ] Significado exacto de la cookie `PCRMBKLBK` y si se puede omitir.
