# StreamingSandbox — handoff para retomar

**Fecha de referencia:** 2026-04-24

## Objetivo general

Sandbox Android (Kotlin + Compose + Hilt + Media3 ExoPlayer) orientado a OTT: reproducción HLS/DASH, MVVM, drawer de canales, abstracción multi-vendor (`PlayerEngine` + factory), base hacia DRM / EPG / Cast.

## Fuente de temas (roadmap / checklist)

Casilla **☐** = pendiente, **☑** = hecho (cámbialo en el texto al cerrar un ítem). Así se ve como checklist en cualquier vista previa, con o sin soporte de tareas GFM (`- [ ]`).

### Gestión de Players

- ☑ Configuración y optimización de ExoPlayer (`ExoPlayerEngine`, métricas básicas, `PlayerView` en Compose)
- ☐ Configuración y optimización de Bitmovin (solo stub + factory)
- ☐ Configuración y optimización de Castlabs (solo stub + factory)

### Reproducción fluida

- ☑ Reproducción fluida en protocolos DASH y HLS (`StreamType` + ExoPlayer; tuning ABR/CDN pendiente)

### Seguridad (DRM)

- ☑ Base de integración Widevine (dominio + engine + ViewModel)
- ☑ Flujo completo de licencia Widevine en entorno real (E2E)
- ☐ Soluciones multi-DRM para contenido premium

### Funciones de TV (OTT)

- ☑ EPG + Live TV base (timeline en drawer, horarios, progreso, carga de canales por M3U con fallback)
- ☑ EPG remoto España (`iptv-epg.org` gzip → `XmlTvParser`; match `tvg-id` M3U + fallback nombre; lista mapeada cacheada con TTL + invalidación por epoch de canales; simulado solo si mapping vacío o fallo fetch)
- ☑ Persistencia EPG diaria en disco (cache por `dayKey`; reutiliza programación en reinicios del mismo día)
- ☑ Precarga de EPG al abrir app + filtrado local por canal (sin esperar descarga al cambiar canal)
- ☑ Selector de canales overlay (carrusel + cierre por tap fuera + selección directa)
- ☑ Overlays de reproducción custom (play/pause + acción LIVE + indicador de carga de canal/programación)
- ☐ Afinar cobertura EPG vs lista M3U (IDs distintos entre fuentes → % match bajo; curated M3U o mejor matching p. ej. `<channel>` XMLTV)
- ☐ Timeshift (pausa en vivo) y Catchup (grabaciones)

### Ecosistema de Casteo

- ☐ Integración con Google Cast
- ☐ Sincronización hacia Chromecast y Smart TVs

## Estructura actual (feature-first)

- `com.mivan.streamingsandbox.feature.channels.*`
  - `domain/model/Channel.kt` (+ `StreamType`)
  - `domain/repository/ChannelRepository.kt`
  - `domain/usecase/GetChannelsUseCase.kt`, `GetProgramsForChannelUseCase.kt`
  - `data/repository/ChannelRepositoryImpl.kt` (M3U + mapa `tvg-id`, EPG mapeado cacheado)
  - `data/m3u/M3uParser.kt`, `data/epg/EpgRemoteDataSource.kt`, `data/epg/XmlTvParser.kt`
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
  - `PlayerFactoryModule.kt` (Binds de factory + vendor provider) ✅
- Raíz: `MainActivity.kt`, `StreamingSandboxApp.kt`
- `ui/theme/*`

## Player / arquitectura

- `PlayerViewModel`: `GetChannelsUseCase` + motor vía `PlayerEngineFactory` + `PlayerVendorProvider` (según implementación final).
- UI: `ModalNavigationDrawer`, `PlayerView` en `AndroidView`, overlay de título ligado a visibilidad de controles del reproductor.
- `PlayerEngine`: `attachView`, `prepare`, `play`, `pause`, `currentPositionMs`, `release`; `state` en `StateFlow`; `metrics` en `StateFlow` si se añadió QoE.

## Optimización profunda (QoE) — pasos guiados

Estado actual:

- ☑ `PlaybackMetrics.kt`
- ☑ `PlayerEngine` + `metrics: StateFlow<PlaybackMetrics>`
- ☑ `ExoPlayerEngine`: startup, rebuffers, errores fatales en listeners
- ☑ `PlayerUiState` + `collect` de métricas en `PlayerViewModel`
- ☑ Métricas en drawer y Logcat

## Estado actual (resumen)

- ☑ QoE completo en app: métricas de arranque/rebuffer/error en `PlayerViewModel`, drawer y Logcat.
- ☑ DRM Widevine E2E validado: con URL de licencia real reproduce; con placeholder falla como prueba negativa esperada.
- ☑ Errores de reproducción/DRM mejorados: mensaje amigable en UI + detalle técnico en Logcat.
- ☑ DI del player verificado: wiring vía `PlayerFactoryModule` sin bind directo de `PlayerEngine`.
- ☑ Configuración de licencia externalizada en `BuildConfig` (`WIDEVINE_LICENSE_URL`) por build type.
- ☑ Live TV base completado: catálogo remoto `iptv-org` + cache TTL + fallback local.
- ☑ Timeline básico en UI (programa actual/siguientes + rango horario + progreso).
- ☑ EPG remoto integrado + caché del resultado mapeado (evita remapear todo el XML en cada consulta por canal).
- ☑ EPG persistida por día en almacenamiento local; al reiniciar en el mismo día reutiliza cache sin refetch completo.
- ☑ Flujo de canal optimizado: reproduce al seleccionar y carga/programa en segundo plano con refresh periódico de EPG.
- ☑ UI de overlays extendida: loading central, timeline con programa en vivo, selector de canales tipo carrusel.
- ☐ Estabilidad al minimizar/restaurar: mitigaciones aplicadas en `ExoPlayerEngine` (`ensurePlayer` + `runOnMain`), validar que no reaparezca `Handler on a dead thread`.
- ☐ Afinar match EPG↔canales, Timeshift/Catchup, Cast y multi-DRM siguen pendientes.

## Vendors (Bitmovin / Castlabs)

- Factory: `EXOPLAYER` | `BITMOVIN` | `CASTLABS`
- Stubs o fallback a Exo hasta SDK + licencias

## Pendiente (resumen frente a «Fuente de temas»)

Detalle arriba en **Fuente de temas**. Resumen: tuning cobertura EPG↔M3U, DRM multi-DRM, Timeshift/Catchup, Cast y sync con Chromecast / Smart TV.

Nota DI: no existe `PlayerEngineModule`; el wiring actual está limpio en `PlayerFactoryModule` (sin bind directo de `PlayerEngine`).

## Gradle / IDE

Si hay errores masivos (`Unresolved reference: androidx`, metadata Kotlin incompatible), alinear **Kotlin / KSP / AGP / Hilt**.

## Cómo retomar en un chat nuevo

Adjunta este archivo o pega su contenido y pide: *«Continúa desde SESSION_HANDOFF.md»*.
