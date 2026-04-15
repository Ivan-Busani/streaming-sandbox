# StreamingSandbox — handoff para retomar

**Fecha de referencia:** 2026-04-15

## Objetivo general

Sandbox Android (Kotlin + Compose + Hilt + Media3 ExoPlayer) orientado a OTT: reproducción HLS/DASH, MVVM, drawer de canales, abstracción multi-vendor (`PlayerEngine` + factory), base hacia DRM / EPG / Cast.

## Fuente de temas (roadmap / checklist)

Esta lista es la referencia de alcance del sandbox; el código actual cubre solo parte (ver secciones siguientes).

### Gestión de Players

- Configuración y optimización de ExoPlayer, Bitmovin y Castlabs.

### Reproducción fluida

- Reproducción fluida en protocolos DASH y HLS.

### Seguridad (DRM)

- Implementación de protección mediante Widevine.
- Soluciones multi-DRM para contenido premium.

### Funciones de TV (OTT)

- Manejo de EPG (guía de canales) y Live TV.
- Funciones de Timeshift (pausa en vivo) y Catchup (grabaciones).

### Ecosistema de Casteo

- Integración con Google Cast.
- Sincronización hacia Chromecast y Smart TVs.

## Estructura actual (feature-first)

- `com.mivan.streamingsandbox.feature.channels.*`
  - `domain/model/Channel.kt` (+ `StreamType`)
  - `domain/repository/ChannelRepository.kt`
  - `domain/usecase/GetChannelsUseCase.kt`
  - `data/repository/ChannelRepositoryImpl.kt`
- `com.mivan.streamingsandbox.feature.player.*`
  - `domain/PlayerEngine.kt` (interfaz + `PlayerEngineState` sealed)
  - `domain/PlaybackMetrics.kt` (si se completó el bloque QoE)
  - `domain/PlayerVendor.kt`, `PlayerEngineFactory.kt`, `PlayerVendorProvider.kt`
  - `data/ExoPlayerEngine.kt`
  - `data/DefaultPlayerEngineFactory.kt`, `DefaultPlayerVendorProvider.kt`
  - `data/BitmovinPlayerEngine.kt`, `CastlabsPlayerEngine.kt` (stubs o fallback)
  - `presentation/PlayerViewModel.kt`, `PlayerUiState.kt` (`PlaybackUiState`)
- `com.mivan.streamingsandbox.di.*`
  - `RepositoryModule.kt`
  - `PlayerEngineModule.kt` — **revisar:** si el engine solo sale del factory, evitar `@Binds` duplicado a `PlayerEngine`
  - `PlayerFactoryModule.kt` (Binds de factory + vendor provider)
- Raíz: `MainActivity.kt`, `StreamingSandboxApp.kt`
- `ui/theme/*`

## Player / arquitectura

- `PlayerViewModel`: `GetChannelsUseCase` + motor vía `PlayerEngineFactory` + `PlayerVendorProvider` (según implementación final).
- UI: `ModalNavigationDrawer`, `PlayerView` en `AndroidView`, overlay de título ligado a visibilidad de controles del reproductor.
- `PlayerEngine`: `attachView`, `prepare`, `play`, `pause`, `currentPositionMs`, `release`; `state` en `StateFlow`; `metrics` en `StateFlow` si se añadió QoE.

## Optimización profunda (QoE) — pasos guiados

Retomar desde el primer paso no aplicado en código:

1. `PlaybackMetrics.kt`
2. `PlayerEngine` + `metrics: StateFlow<PlaybackMetrics>`
3. `ExoPlayerEngine`: startup, rebuffers, errores fatales en listeners
4. `PlayerUiState` + `collect` de métricas en `PlayerViewModel`
5. Mostrar métricas en drawer y/o Logcat

## Vendors (Bitmovin / Castlabs)

- Factory: `EXOPLAYER` | `BITMOVIN` | `CASTLABS`
- Stubs o fallback a Exo hasta SDK + licencias

## Pendiente (resumen frente a «Fuente de temas»)

Detalle arriba en **Fuente de temas**. Resumen: DRM (Widevine + multi-DRM), EPG / Live / Timeshift / Catchup, Cast y sync con Chromecast / Smart TV.

## Gradle / IDE

Si hay errores masivos (`Unresolved reference: androidx`, metadata Kotlin incompatible), alinear **Kotlin / KSP / AGP / Hilt**.

## Cómo retomar en un chat nuevo

Adjunta este archivo o pega su contenido y pide: *«Continúa desde SESSION_HANDOFF.md»*.
