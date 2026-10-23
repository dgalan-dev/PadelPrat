# PadelPrat

App Android de pádel, en Kotlin y Jetpack Compose (Material 3). Paquete: `com.dgalan.padelprat`. Un solo módulo (`:app`).

## Comandos

```bash
./gradlew ktlintCheck lintDebug assembleDebug   # comprobar cualquier cambio antes de darlo por bueno
./gradlew ktlintFormat                          # corregir el estilo automáticamente
```

El CI (`.github/workflows/ci.yml`) ejecuta lo mismo en cada push a `main` y en cada PR.

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
  - `ui/components/`: componentes reutilizables entre pantallas (p. ej. `PillSwitch`, el selector de píldora de los mocks). Antes de crear uno nuevo, mirar si ya existe aquí.
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
- El login no tiene registro.

## Convenciones

- Versiones de librerías y plugins siempre en `gradle/libs.versions.toml`; usar solo versiones estables.
- Estilo de ktlint `android_studio` (`.editorconfig`), con líneas de máximo 120 caracteres. Pasar `./gradlew ktlintFormat` antes de dar un cambio por bueno.
- Textos de la interfaz en `res/values/strings.xml`, en español; nada de textos fijos en el código.
- Tema propio de Material 3 en `ui/theme/` (sin color dinámico), con modo claro y oscuro según el sistema. Usar siempre `MaterialTheme.colorScheme` y `MaterialTheme.typography`, sin colores ni tamaños de texto fijos.
  - Colores (de los mocks de diseño): primario azul `#1E4FA3` y terciario lima de bola `#B8E63A`. `Color.kt` está generado con material-color-utilities y tiene algunos tonos fijados a mano con los valores exactos del mock (ver la cabecera del archivo); para cambiarlos hay que regenerar el esquema completo, no editar tonos sueltos.
  - Tipografía: Montserrat (display, headline y `titleLarge`) e Inter (el resto), incluidas en `res/font/`.
- Comentarios en español y solo cuando aporten algo que el código no dice.

## Decisiones del proyecto

- **Sin tests**: no añadir tests, dependencias de tests ni pasos de tests en el CI.
- **Sin Dependabot ni detekt**.
- **Git**: preguntar antes de cada commit. No hacer nunca `git push`; lo hace el usuario desde Android Studio.
- **Commits sin atribución a Claude**: no añadir `Co-Authored-By: Claude` ni ninguna otra mención a Claude en los mensajes de commit.
