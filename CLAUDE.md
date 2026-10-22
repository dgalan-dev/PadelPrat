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
  - La navegación se pasa como lambdas (`onLoginSuccess: () -> Unit`); las pantallas no conocen la pila de navegación.
- **Navegación**: Navigation 3 (no Navigation Compose).
  - Rutas en `navigation/Routes.kt`: `@Serializable data object/class XxxRoute : NavKey`.
  - Entradas en `navigation/AppNavigation.kt` dentro del `entryProvider`.
- **Inyección de dependencias**: Hilt con KSP. `PadelPratApp` (`@HiltAndroidApp`) y `MainActivity` (`@AndroidEntryPoint`).
- **Carpetas por funcionalidad**: `feature/<nombre>/` contiene todo lo de esa funcionalidad. El tema está en `ui/theme/`.
- **Backend**: solo hay un entorno (producción). La URL está en `BuildConfig.BASE_URL` (`app/build.gradle.kts`). No hay variantes de compilación (*flavors*).
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
