# PadelPrat

App Android de pádel, en Kotlin y Jetpack Compose (Material 3). Paquete: `com.dgalan.padelprat`. Un solo módulo (`:app`).

## Comandos

```bash
./gradlew ktlintCheck lintDebug assembleDebug   # comprobar cualquier cambio antes de darlo por bueno
./gradlew ktlintFormat                          # corregir el estilo automáticamente
```

El CI (`.github/workflows/ci.yml`) ejecuta lo mismo en cada push a `main` y en cada PR.

### Releases

Se publican en GitHub Releases (no en Google Play). Para sacar una versión: subir `versionCode` y `versionName` en `app/build.gradle.kts`, commitear y subir un tag `vX.Y.Z` igual a `versionName`. El workflow `.github/workflows/release.yml` comprueba que coinciden, compila la release firmada y la publica con el APK (`PadelPrat-X.Y.Z.apk`).

- La clave de firma (`padelprat-release.jks`, alias `padelprat`) **no está en el repositorio**: está en los secretos de GitHub `RELEASE_KEYSTORE_BASE64` y `RELEASE_KEYSTORE_PASSWORD`, y en una copia del usuario fuera del proyecto. Si se pierde, las nuevas versiones no se pueden instalar encima de las anteriores.
- **Cada release es una actualización obligatoria**: al abrir, la app consulta la última release de GitHub y, si es más nueva, bloquea todo con `ForceUpdateScreen` hasta instalarla (ver "Actualización obligatoria" en Arquitectura). Una release rota obliga a todos a instalarla, y solo se arregla publicando otra más nueva. El asset tiene que seguir siendo un `.apk`.
- Sin la clave, `assembleRelease` genera `app-release-unsigned.apk`; para probarla en un móvil se firma a mano con la clave de depuración (apksigner). Una app firmada con una clave no se instala encima de otra firmada con la otra: hay que desinstalar y se pierden la sesión y las programaciones.

## Arquitectura

- **MVVM**: cada pantalla tiene un `XxxScreen` (Composable) y un `XxxViewModel` (`@HiltViewModel`) que expone un `StateFlow<XxxUiState>`.
  - `XxxScreen` obtiene el ViewModel con `hiltViewModel()`, recoge el estado con `collectAsStateWithLifecycle()` y delega en un `private XxxContent` sin ViewModel, que es el que tiene `@Preview`.
  - La navegación se pasa como lambdas (`onXxxClick: () -> Unit`); las pantallas no conocen la pila de navegación.
- **Navegación**: Navigation 3 (no Navigation Compose).
  - Rutas en `navigation/Routes.kt`: `@Serializable data object/class XxxRoute : NavKey`.
  - Entradas en `navigation/AppNavigation.kt` dentro del `entryProvider`.
  - La pantalla raíz la decide la sesión (`MainViewModel.sessionState`): con sesión, `MainRoute`; sin ella, `LoginRoute`. Login y logout no navegan: cambian la sesión y la navegación reacciona sola. La pantalla de arranque (core-splashscreen) se mantiene mientras se lee la sesión.
  - `MainRoute` es `MainScaffold`: la barra inferior con las pestañas de `TopLevelDestination` (Reservar, Mis reservas, Perfil). Las pestañas cambian dentro de esa entrada y conservan su estado; las pantallas de detalle (p. ej. confirmar reserva) se añaden como rutas nuevas en la pila raíz, por encima de la barra.
- **Inyección de dependencias**: Hilt con KSP. `PadelPratApp` (`@HiltAndroidApp`) y `MainActivity` (`@AndroidEntryPoint`).
- **Carpetas**:
  - `feature/<nombre>/`: pantallas y ViewModels de cada funcionalidad.
  - `data/<área>/`: repositorios y fuentes de datos remotas (`XxxRemoteDataSource`), con resultados como `sealed interface` (p. ej. `LoginResult`).
  - `core/network/`: cliente HTTP y sesión. `ui/theme/`: tema.
  - `ui/components/`: componentes reutilizables entre pantallas (`PillSwitch`, el selector de píldora de los mocks; `PadelCard`, `Tag`, `CourtName`, `ShimmerBox`, `MessageState`). Antes de crear uno nuevo, mirar si ya existe aquí.
  - `ui/format/`: formato de fechas y horas de la interfaz (`hourText`, `longDate`, `openingText`, `shortName`).
- **Backend**: no hay API propia; es la web de reservas del club (Syltek, `https://prat.syltek.com/`), replicada por ingeniería inversa. **Antes de tocar cualquier petición, leer `docs/syltek-api.md`**: rutas, formularios, selectores HTML y reglas (p. ej. la pre-reserva bloquea la pista 8 minutos, anular es un GET que no se puede reintentar).
  - Respuestas en HTML y formularios `x-www-form-urlencoded`: OkHttp directo, sin Retrofit. El HTML se lee con Jsoup, en parsers aislados (`TimetableParser`, `ReservationParsers`).
  - **Reservar** (`ReservationRepository.book`): pre-reserva, cambio de duración y confirmación seguidos, solo al pulsar "Confirmar reserva" en la hoja de la parrilla. Abrir la hoja no bloquea nada. Ningún paso se reintenta solo; si se corta al confirmar, el resultado es dudoso y se avisa. Ver `docs/plan-confirmar-reserva.md`.
  - **Al probar en un móvil, no confirmar ni pre-reservar nunca una reserva real**: crea reservas de verdad y bloquea pistas a otros socios. Las pruebas de reserva las hace el usuario.
  - Sesión: solo una cookie (`Syltek.SESSION_COOKIE`, 6 meses) guardada cifrada con Tink en DataStore (`PersistentCookieJar`). **No se guarda la contraseña.** La sesión está excluida de las copias de seguridad (`res/xml`).
  - `followRedirects(false)`: un 303 a `/customer/login` significa sesión caducada; `SessionExpiredInterceptor` borra la sesión y la app vuelve al login.
  - Logs de red solo en debug y a nivel `BASIC` (método, URL y código). Nunca registrar cabeceras, cookies ni contraseñas.
  - Un solo entorno (producción), en `BuildConfig.BASE_URL`. No hay variantes de compilación (*flavors*).
  - Fechas y horas con `java.time` (`minSdk 26`) y siempre en la zona del club, `Syltek.ZONE` (`Europe/Madrid`). De la web, usar los campos `*Utc`.
  - La parrilla (`data/booking/`) se lee con `TimetableParser` y los huecos los calcula `SlotCalculator` (lógica pura, sin red). Si la web cambia y algo deja de leerse, el arreglo va ahí y en `docs/syltek-api.md`.
  - Mis reservas (`data/reservations/`): `ReservationsParser` lee el listado y el detalle (la pista solo viene en el detalle; se pide solo para las próximas). **Anular es un GET que borra datos: una sola petición, nunca reintentada**, y el resultado se comprueba recargando el listado. `ReservationsChangeNotifier` avisa de reservas y anulaciones para que la parrilla y Mis reservas se recarguen solas.
  - Perfil (`data/profile/`): `ProfileParser` lee "Mi Cuenta". `ProfileRepository` guarda los datos **solo en memoria** (son personales; no van a disco) y los comparte con el saludo de la parrilla; se borran al cerrar sesión.
  - Reserva programada (`data/scheduled/`, ver `docs/plan-reserva-programada.md`): cada día se abre a las 9:00 de Madrid 7 días antes (`Syltek.BOOKING_OPENS_*`); el último día abierto lo da `nowUtcMaxAvailability`. Las programaciones se guardan en DataStore como JSON (`ScheduledBookingRepository`, que también pone y quita su alarma), sin cifrar porque no son datos sensibles. El motor es `ScheduledBookingRunner`, lanzado por una alarma `setAlarmClock` a las 8:58 → `ScheduledBookingReceiver` → `ScheduledBookingService` (primer plano). `ScheduledBookingRescheduler.reconcile()` (al abrir la app y desde `RescheduleReceiver` al reiniciar, actualizar o cambiar la hora) vuelve a poner las alarmas, lanza las que se perdieron si aún hay tiempo y avisa de las que no. En debug, "Probar ahora" en la tarjeta programada lo ejecuta en 15 s sin reservar (modo de prueba).
  - Actualización obligatoria (`data/update/`, `feature/update/`): `UpdateRepository.check()` consulta `releases/latest` de la API pública de GitHub (cliente propio `@GitHubClient`, sin cookies; unos 6 KB y máximo 5 s, con la pantalla de arranque esperando). Si `tag_name` es más nueva que `versionName`, `MainActivity` muestra `ForceUpdateScreen` en lugar de la app, sin opción de saltarla. Si no se puede comprobar, se entra igual. En debug no se comprueba (otra firma). El APK se descarga a `cacheDir/update`, se comprueba el tamaño y el SHA-256 (`digest` de GitHub) y se instala con `PackageInstaller` (`AppInstaller` + `UpdateInstallReceiver`). Hace falta el permiso "Instalar apps desconocidas" (`UnknownSources`). No se instala mientras haya una reserva programada en curso (`ScheduledRunRegistry.runningIds`). Al actualizar, `RescheduleReceiver` vuelve a poner las alarmas y borra el APK descargado (`UpdateRepository.deleteDownloads()`; `check()` también lo borra al ver la app al día, por si acaso).
  - **Al investigar páginas nuevas de la web con la sesión del usuario: registrar solo la estructura (etiquetas, tipos de elemento, longitudes), nunca los valores**, y quitar el log y borrar el registro del móvil al terminar.
- El login no tiene registro.
- Solo en vertical: `MainActivity` lleva `screenOrientation="portrait"` en el manifest. En móviles se respeta; en pantallas grandes (ancho mínimo ≥ 600 dp) Android 16 lo ignora al apuntar a la API 36 o más, así que en tablets y plegables abiertos gira igual.

## Convenciones

- Versiones de librerías y plugins siempre en `gradle/libs.versions.toml`; usar solo versiones estables.
- Estilo de ktlint `android_studio` (`.editorconfig`), con líneas de máximo 120 caracteres. Pasar `./gradlew ktlintFormat` antes de dar un cambio por bueno.
- Textos de la interfaz en `res/values/strings.xml`, en español; nada de textos fijos en el código.
- Tema propio de Material 3 en `ui/theme/` (sin color dinámico), con modo claro y oscuro según el sistema. Usar siempre `MaterialTheme.colorScheme` y `MaterialTheme.typography`, sin colores ni tamaños de texto fijos.
  - Colores (de los mocks de diseño): primario azul `#1E4FA3` y terciario lima de bola `#B8E63A`. `Color.kt` está generado con material-color-utilities y tiene algunos tonos fijados a mano con los valores exactos del mock (ver la cabecera del archivo); para cambiarlos hay que regenerar el esquema completo, no editar tonos sueltos.
  - Colores propios fuera del esquema de Material 3 en `PadelColors` (`MaterialTheme.padelColors`), sacados de sus tonos: huecos libres (`available`), lima intenso (`accent`), tarjetas (`card`, `cardBorder`, `cardMuted`) y cabecera fija (`pinnedHeader`). En oscuro las tarjetas van más claras que el fondo y sin borde. Si un color tiene que cambiar entre claro y oscuro, va aquí y no con `isSystemInDarkTheme()` en la pantalla (solo queda la cabecera del login).
  - Formas en `Shape.kt` (`MaterialTheme.shapes`): `extraSmall` etiquetas, `small` chips, `medium` avisos, `large` tarjetas. Nada de `RoundedCornerShape` sueltos.
  - La barra de estado la pinta cada pestaña (`MainScaffold` no aplica el margen superior): Reservar la pone del color de su cabecera fija y las demás usan `statusBarsPadding()`.
  - Tipografía: Montserrat (display, headline y `titleLarge`) e Inter (el resto), incluidas en `res/font/`.
- Comentarios en español y solo cuando aporten algo que el código no dice.

## Decisiones del proyecto

- **Sin tests**: no añadir tests, dependencias de tests ni pasos de tests en el CI.
- **Sin Dependabot ni detekt**.
- **Git**: preguntar antes de cada commit. No hacer nunca `git push`; lo hace el usuario desde Android Studio.
- **Commits sin atribución a Claude**: no añadir `Co-Authored-By: Claude` ni ninguna otra mención a Claude en los mensajes de commit.
