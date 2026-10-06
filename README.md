# ScreenMirror

Ver la pantalla de un teléfono Android desde otro, en la **misma red Wi‑Fi**, sin depender de terceros.

El proyecto tiene **tres aplicaciones** y un módulo de código compartido:

| Módulo | Tipo | Qué hace |
|--------|------|----------|
| `:app` | App | **Unificada (recomendada)**: menú para *compartir* o *ver*. Con esta app, **dos personas comparten en ambos sentidos**. |
| `:emitter` | App | **Emisora**: solo emite (ligera). |
| `:viewer` | App | **Receptora**: solo ve. |
| `:core` | Librería | Captura, servidor HTTP/MJPEG, descubrimiento y QR compartidos. |

- **Emisora:** captura con la API oficial `MediaProjection`, sirve un stream MJPEG por HTTP (puerto **8080**) y **anuncia su presencia en la red** (UDP 8888). Muestra un **QR** con la URL.
- **Receptora:** **busca emisoras en la red automáticamente**, **escanea el QR** con la cámara, o acepta la URL a mano; y muestra la pantalla en un WebView.
- **Navegador (sin instalar nada):** cualquiera en la misma Wi‑Fi puede abrir `http://IP-DE-LA-EMISORA:8080`.

No usa servidores externos: el video viaja por tu red local.

---

## Cómo se conectan (dos vías)

1. **Descubrimiento en la red:** la Receptora pulsa *"Buscar emisoras en la red"* y la Emisora responde (UDP). Aparece la lista; tocas una y conecta.
2. **QR:** la Emisora muestra un QR con `http://IP:8080`; la Receptora lo escanea con la cámara y conecta.

```
 [ App Emisora ]                              [ App Receptora ]
  MediaProjection                              Descubrimiento UDP / QR
  HTTP :8080  +  UDP :8888  ─── Wi-Fi LAN ───>  WebView con el stream
                                               (o cualquier navegador)
```

---

## Requisitos

- Dos dispositivos en la **misma red Wi‑Fi** (mismo router, sin "aislamiento de clientes").
- Android **7.0 (API 24)** o superior.
- La Emisora **siempre** acepta el permiso de captura y muestra una notificación mientras transmite. Es obligatorio por diseño de Android: no existe captura en silencio.

---

## Compilar

```bash
cd screen-mirror
export ANDROID_HOME=/ruta/a/Android/Sdk
./gradlew assembleRelease
```

Genera:
- `emitter/build/outputs/apk/release/emitter-release.apk`
- `viewer/build/outputs/apk/release/viewer-release.apk`

También puedes abrir la carpeta con **Android Studio**.

### Firma (release)

Copia `keystore.properties.example` a `keystore.properties` y genera tu keystore:

```bash
keytool -genkeypair -v -keystore keystore/release.jks -alias screenmirror \
  -keyalg RSA -keysize 2048 -validity 10000
```

Sin `keystore.properties`, la release se genera **sin firmar**.

---

## Uso

### 1) Emisora (el teléfono que se muestra)

1. Abre **ScreenMirror Emisora** y pulsa **"Iniciar transmisión"**.
2. Acepta el permiso de captura.
3. La app muestra un **QR** y la URL (`http://IP:8080`).

### 2) Receptora (el que mira)

1. Abre **ScreenMirror Receptora**, en la **misma Wi‑Fi**.
2. Elige una opción:
   - **Buscar emisoras en la red** → toca la que aparezca.
   - **Escanear QR** → apunta al QR de la Emisora.
   - Escribe la URL y pulsa **Ver**.
3. Verás la pantalla en vivo.

---

## Instalar la otra app (QR desde la propia app)

Cada app incluye un botón que muestra un **código QR** apuntando a la APK de la **otra** versión (última release publicada en GitHub):

- **Emisora → "Descargar la app Receptora"**
- **Receptora → "Descargar la app Emisora"**

El otro teléfono escanea el QR, descarga la APK y la instala (debe permitir "instalar apps de origen desconocido"). Es una instalación **voluntaria y visible**.

---

## Conexión prolongada (pantalla/CPU activas)

Mientras la Emisora transmite:

- Mantiene la **pantalla encendida** (`FLAG_KEEP_SCREEN_ON`).
- Mantiene la **CPU despierta** con un `WakeLock`, para que el stream no se corte.
- **Sigue transmitiendo aunque la app pase a segundo plano o se cierre** (servicio en primer plano con `android:stopWithTask="false"`).

El envío se detiene **solo** al pulsar **"Detener"**, o al desinstalar/forzar la app.

> Nota: Android no permite que una app en segundo plano fuerce por sí sola la pantalla física a quedarse encendida (por seguridad). El `WakeLock` de pantalla es un *best effort*; lo garantizado es que **la CPU y la transmisión siguen activas**. Si quieres la pantalla siempre encendida, deja la app Emisora en primer plano (ahí sí se mantiene).

---

## Ajustes

En `core/.../CaptureManager.kt`:
- `quality = 60` (calidad JPEG), `minFrameIntervalMs = 100L` (~10 fps).
En `emitter/.../EmitterService.kt`:
- `targetWidth = 720`, `PORT = 8080`.
En `core/.../Discovery.kt`:
- `PORT = 8888` (puerto UDP de descubrimiento).

---

## Problemas frecuentes

- **No aparece ninguna emisora** → misma Wi‑Fi, y el router no debe tener "aislamiento de clientes" (AP isolation). Revisa que la Emisora esté **transmitiendo**.
- **La Receptora no abre la página** → revisa la IP y que uses `http://`.
- **Va lento** → baja `targetWidth` a 540 y sube `minFrameIntervalMs` a 150.
- **Se detiene al bloquear pantalla** → desactiva la optimización de batería para la app Emisora (Xiaomi/Huawei/Samsung).
- **Android 14+** → concede el permiso de notificaciones la primera vez.

---

## Estructura

```
screen-mirror/
├── core/                       # libreria compartida
│   └── src/main/java/com/save/screenmirror/core/
│       ├── CaptureManager.kt   # MediaProjection -> JPEG
│       ├── StreamServer.kt     # HTTP + MJPEG + pagina web
│       ├── Discovery.kt        # descubrimiento UDP (emisor/receptor)
│       └── NetworkUtils.kt     # IP local
├── emitter/                    # app EMISORA
│   └── src/main/java/com/save/screenmirror/emitter/
│       ├── MainActivity.kt     # permiso, QR, URL
│       └── EmitterService.kt   # foreground service (captura + HTTP + UDP)
├── viewer/                     # app RECEPTORA
│   └── src/main/java/com/save/screenmirror/viewer/
│       ├── MainActivity.kt     # descubrimiento + QR + WebView
│       └── QrScanActivity.kt   # escaner de QR (CameraX + ZXing)
└── README.md
```

---

## Alcance y límites

- Es **solo ver**, no controlar.
- **Consentimiento visible obligatorio** en la Emisora (permiso de captura + notificación). No es sigiloso.
- **No** usa exploits ni vulnerabilidades: solo APIs públicas de Android.
- Un **navegador de móvil no puede compartir su pantalla** (la API `getDisplayMedia` no existe en navegadores móviles). Por eso el emisor debe ser la app, no una web.
