# Plan: Ver las reservas del amigo

Ver en Mis reservas las próximas reservas del otro usuario de la app y recibir un aviso cuando reserve o anule.
Decisiones tomadas al final.

La web del club solo enseña las reservas de quien tiene la sesión iniciada, así que **cada app publica sus próximas
reservas en Firestore y lee las del otro**. Ni las contraseñas ni la cookie de Syltek salen nunca del móvil: solo se
suben fecha, hora, duración y pista. Sin servidor propio (plan Spark de Firebase, sin Cloud Functions).

```
Mi app ── lee el listado de Syltek ── publica mis próximas ──► Firestore: reservations/{miUid}
                                    └─ si han cambiado, push de datos (FCM) ──► su app
Su app ◄── al recibir la push o al abrir Mis reservas ── lee reservations/{miUid}
```

## Firebase (configurado el 29/10/2026)

- Proyecto `padelprat`, plan **Spark** (sin tarjeta: no se puede cobrar nada). Google Analytics vinculado, pero la
  app no lleva su SDK: no recoge nada.
- App Android `com.dgalan.padelprat` con **cuatro huellas**: SHA-1 y SHA-256 de la clave de depuración de este Mac y
  de `padelprat-release.jks`. Sin la de release, Google Sign-In falla en los APK de GitHub (`DEVELOPER_ERROR`). Otro
  ordenador de desarrollo necesitaría añadir su huella de depuración.
- **Authentication**: solo Google. **Firestore**: base de datos `(default)`, edición Standard, en `nam5` (EE. UU.:
  la ubicación venía fijada; se deja ahí porque la cuota gratuita es para `(default)` y la latencia no importa aquí).
- **Cloud Messaging**: API HTTP v1 habilitada.
- **Cuenta de servicio `padelprat-fcm`** con un único rol, *Firebase Cloud Messaging API Admin*: solo puede enviar
  mensajes de FCM, sin acceso a Firestore ni al resto del proyecto. Su clave está fuera del repositorio
  (`padelprat-signing/padelprat-fcm.json`, ruta en `local.properties` como `padelprat.fcmServiceAccount`).
- El repositorio es público: **ninguno de los dos archivos se versiona**. `app/google-services.json` está en
  `.gitignore`. Secretos de GitHub: `GOOGLE_SERVICES_JSON_BASE64` (CI y release) y `FCM_SERVICE_ACCOUNT_BASE64` (solo
  release). Si cambia `google-services.json` (p. ej. al añadir una huella), hay que actualizar su secreto.

## Datos en Firestore

```
members/{uid}           ← solo desde la consola; la app no puede escribir
  name: "Marc"

reservations/{uid}      ← cada uno el suyo
  updatedAt: <hora del servidor>
  fcmToken: "…"         ← para que el otro le envíe la push
  upcoming: [ { id, startUtc, durationMinutes, court, sponsor } ]
```

- Solo las **próximas activas**: ni pasadas, ni anuladas, ni importes. Horas en UTC; se muestran en `Syltek.ZONE`.
- **Las reservas programadas no se publican** mientras están pendientes (se quedan solo en el móvil). Cuando una
  sale reservada, el servicio relee el listado (`refreshActiveCount()`) y la reserva real se publica con lo demás.
- Un documento por persona: leer las del otro es una sola lectura. Muy por debajo de la cuota gratuita (50 000
  lecturas y 20 000 escrituras al día).
- Se leen y se escriben a mano desde `Map` (sin `toObject()`), para no necesitar reglas de R8.

### Reglas (`firestore.rules`, versionadas y desplegadas con la CLI)

```
function isMember() {
  return request.auth != null && exists(/databases/$(db)/documents/members/$(request.auth.uid));
}
match /members/{uid}      { allow read: if isMember(); allow write: if false; }
match /reservations/{uid} { allow read: if isMember(); allow write: if isMember() && request.auth.uid == uid; }
```

Quién es miembro se decide en la consola: no hay correos ni UID en el repositorio. Tras el primer inicio de sesión
de cada uno, se crea `members/{uid}` con su `name`.

## Funcionamiento

1. **Activar** (Perfil → "Reservas compartidas"): inicio de sesión con Google (Credential Manager). Es opcional; sin
   activarlo la app funciona como hasta ahora. Si aún no está en `members`, se ve "Pendiente de alta".
2. **Publicar**: cada vez que se lee el listado de Syltek (`ReservationsRepository.list()`, junto a
   `reminders.sync`), lo que cubre abrir Mis reservas, anular, los avisos y la reserva programada; y justo después
   de reservar desde la parrilla. Las pistas solo vienen en el detalle: se conservan las ya conocidas por `id`.
   Solo se escribe si algo ha cambiado. Si falla, no afecta al resto de la app. No hace falta refresco periódico:
   las reservas se hacen y se anulan siempre desde la app. Reservar desde la parrilla ya provoca una lectura del
   listado (`BookingViewModel`), igual que la reserva programada (`ScheduledBookingService`), así que no hay más
   enganches. Con cada listado también se comprueba el token de FCM y se publica si ha cambiado.
3. **Avisar al otro**: si han cambiado las reservas publicadas (alguna nueva o alguna que ya no está), se envía al
   `fcmToken` del otro un **mensaje de datos de prioridad alta, sin contenido** (solo "han cambiado").
   - El token OAuth se saca de la clave de `padelprat-fcm`: JWT RS256 firmado con `java.security` y pedido a
     `oauth2.googleapis.com/token` con OkHttp (sin `google-auth-library`); dura 1 h y se reutiliza.
   - `POST https://fcm.googleapis.com/v1/projects/padelprat/messages:send`, una sola vez, sin reintentos (si se
     pierde, el otro lo verá al abrir Mis reservas).
   - La clave entra en el APK al compilar (desde `local.properties` o, en el CI, desde el secreto). Sin ella la app
     compila igual y no envía pushes.
4. **Recibir** (`FirebaseMessagingService`): lee `reservations/{suUid}`, lo compara con lo último visto (DataStore,
   fuera de las copias de seguridad) y, si hay cambios reales, muestra una **notificación local** ("Marc ha
   reservado: sábado 31, 20:00 – 21:30, Pista 5" / "Marc ha anulado…"). Al tocarla se abre Mis reservas. Canal
   propio, desactivable en Perfil como los avisos. La primera vez (o con un amigo nuevo) solo se guarda lo que
   tiene, para no avisar de todas sus reservas; también al activar en Perfil. Las que desaparecen por haber
   terminado no se avisan como anuladas.
   - FCM 26 marca como obsoletos `getToken()` y `onNewToken` en favor de `register()` con el Firebase Installation
     ID, que hay que activar aparte. Se sigue usando el token; en vez de `onNewToken`, se comprueba con cada listado.
5. **Cerrar sesión** en la app: se borra `reservations/{miUid}`, lo último visto y la sesión de Firebase.

### Por qué la clave en el APK es aceptable

Google desaconseja meter claves de cuentas de servicio en una app, y el APK es público. Se limita así:

- La cuenta solo tiene el rol de FCM: con la clave no se puede leer ni tocar Firestore.
- Para enviar hace falta el token del móvil, que solo está en Firestore (protegido por las reglas). La app no se
  suscribe a ningún *topic*.
- La push no lleva datos: el aviso se construye con lo que hay en Firestore. Una push falsa, como mucho, hace que la
  app compruebe y no muestre nada.
- Si se filtra: se revoca la clave en Google Cloud, se crea otra, se actualiza el secreto y se publica una release.

Alternativa sin clave en el APK si algún día hace falta: un Cloudflare Worker (gratuito) que reciba la petición con
el token de Firebase, lo compruebe y envíe la push.

## Pantallas

Diseño elegido de la maqueta (ya borrada): **A, todo junto**, con la **opción 1** (sin borde lima en las del
amigo).

- **Mis reservas → Próximas**: las mías y las suyas mezcladas por fecha. Cada tarjeta lleva un círculo (azul "Yo",
  lima con la inicial del amigo) y el nombre. Las suyas solo tienen "Compartir", sin "Anular". Filtros
  "Todas · Mías · De Marc" y "Actualizado hace X" con su `updatedAt`. Pasadas y Anuladas, solo las mías.
- Si Firestore falla o no está activado, se ven solo las mías, sin pantalla de error.
- **Perfil**: sección "Reservas compartidas" (activar/desactivar, estado) y el interruptor del aviso.

## Fases

Las cuatro, hechas el 29/10/2026 y probadas en un Pixel 2 XL con una sola cuenta (inicio de sesión, alta, publicar,
pantallas). **Falta probar con los dos móviles** lo que necesita al amigo: ver sus reservas y la push.

1. **Base**: Firebase BoM (Auth, Firestore, Messaging), Credential Manager y `googleid`, plugin de google-services
   en `libs.versions.toml`; los dos workflows generan `google-services.json` desde el secreto; `firestore.rules`;
   `data/friends/` (`FirebaseModule`, `FriendAuthRepository`); sección de Perfil e inicio de sesión. Alta en
   `members` desde la consola.
2. **Publicar y ver**: `SharedReservationsRepository` (publicar, escuchar las del otro), enganche en
   `ReservationsRepository` y tras reservar, `ReservationsViewModel` y las tarjetas con el diseño elegido.
3. **Push**: clave en el build (`local.properties` / secreto en `release.yml`), `FcmSender`, servicio de mensajes,
   notificación y su canal, interruptor en Perfil.
4. **Cierre**: borrar la maqueta, actualizar `CLAUDE.md` y este documento, release. Cada fase pasa
   `./gradlew ktlintCheck lintDebug assembleDebug`. La push se prueba con los dos móviles.

## Más adelante

- **Crashlytics** (sin pasos en la consola; desactivado en debug; el plugin sube el *mapping* de R8 desde
  `release.yml`) y quizá **Analytics** (con los dos, los errores llevan las pantallas previas). Nunca se registrarán
  cookies, datos del perfil ni ids de reserva.

## Decisiones tomadas (29/10/2026)

- Firebase en plan gratuito, con Google Sign-In y la lista de miembros en la consola.
- Solo las próximas reservas, mezcladas con las mías (diseño A, opción 1).
- De momento no se comparten las reservas programadas pendientes; solo las reales. Si más adelante se quiere,
  se valoró publicarlas, mostrarlas con el estilo de programada y avisar al programar la misma hora que el otro.
- Sin refresco periódico de Syltek: se reserva y se anula siempre desde la app.
- Push enviada desde la propia app con la cuenta de servicio limitada a FCM, sin datos en el mensaje.
- `google-services.json` y la clave de FCM fuera del repositorio, como secretos de GitHub.
