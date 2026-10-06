# ScreenMirror

Sistema **propio** para ver la pantalla de un teléfono Android desde otro, en la **misma red Wi‑Fi**, sin depender de terceros.

- **Emisor (el teléfono que se muestra):** una sola app, la tuya, que captura la pantalla con la API oficial `MediaProjection` y la transmite.
- **Receptor (el que mira):** **no instala nada**. Solo abre una página web en su navegador.

No usa servidores externos: el video viaja por tu red local.

---

## 1. Cómo funciona

```
 [ Teléfono emisor ]                          [ Teléfono receptor ]
  App ScreenMirror      --- Wi-Fi LAN --->     Navegador (Chrome, etc.)
  - MediaProjection                            http://IP-DEL-EMISOR:8080
  - Servidor HTTP + MJPEG
```

La app emisora levanta un servidor HTTP en el puerto **8080**:

| Ruta      | Qué sirve                                   |
|-----------|---------------------------------------------|
| `/`       | Página web con el video en vivo (`<img>`)   |
| `/stream` | Stream MJPEG (`multipart/x-mixed-replace`)  |

El receptor solo abre `http://IP-DEL-EMISOR:8080`.

---

## 2. Requisitos

- Dos dispositivos en la **misma red Wi‑Fi** (mismo router).
- En el emisor: Android **7.0 (API 24)** o superior.
- El emisor instala **una** app (esta, compilada por ti). El receptor no instala nada.

> Nota: el emisor **siempre** debe aceptar el permiso de captura de pantalla una vez y
> la notificación queda visible mientras transmite. Es el comportamiento obligatorio de
> Android: no existe forma de capturar la pantalla en silencio, y eso protege tu privacidad.

---

## 3. Compilar

El proyecto ya está compilado y el APK generado en:

```
/home/save/Descargas/ScreenMirror-debug.apk
```

Para recompilar por tu cuenta:

```bash
cd /home/save/screen-mirror
export ANDROID_HOME=/home/save/Android/Sdk
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

También puedes abrir la carpeta `screen-mirror` directamente con **Android Studio**
(File > Open) y pulsar Run.

---

## 4. Instalar y usar

1. **Emisor:** copia `ScreenMirror-debug.apk` al teléfono e instálalo
   (acepta "instalar apps de origen desconocido" para tu gestor de archivos).
2. **Emisor:** abre **ScreenMirror** y pulsa **"Iniciar transmisión"**.
   Acepta el diálogo de captura de pantalla. La app mostrará una URL, por ejemplo:
   ```
   http://192.168.1.42:8080
   ```
3. **Receptor:** conéctate a la **misma Wi‑Fi**, abre el navegador y entra a esa URL.
   Verás la pantalla del emisor en vivo.
4. Para terminar: pulsa **"Detener"** en la app emisora.

---

## 5. Ajustes que puedes tocar

En `CaptureManager.kt`:

- `targetWidth = 720` (en `ScreenMirrorService.kt`): ancho de la transmisión.
  Bájalo (p. ej. 540) si tu Wi‑Fi va lento.
- `quality = 60`: calidad JPEG (1–100). Menos = menos datos.
- `minFrameIntervalMs = 100L`: intervalo entre cuadros (~10 fps). Súbelo para consumir menos.

En `ScreenMirrorService.kt`:

- `PORT = 8080`: puerto del servidor.

---

## 6. Problemas frecuentes

- **"Sin Wi-Fi. Conéctate a una red."** → el emisor no tiene IP local; asegúrate de estar
  en Wi‑Fi (no solo datos móviles) y que no esté en una red de invitados aislada.
- **El receptor no abre la página** → revisa la IP, que ambos estén en la misma red, y
  que el router no tenga "aislamiento de clientes" (AP isolation) activado.
- **Se ve lento o con saltos** → reduce `targetWidth` a 540 y/o sube `minFrameIntervalMs` a 150.
- **Se detiene al bloquear pantalla** → la app usa un foreground service; mantén la
  notificación. En algunos fabricantes (Xiaomi, Huawei, Samsung) hay que desactivar la
  optimización de batería para que no la mate.
- **El emisor es Android 14+** → la primera vez pedirá permiso de notificaciones; concédelo
  para que el servicio en primer plano no se detenga.

---

## 7. Estructura

```
screen-mirror/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/save/screenmirror/
│   │   ├── MainActivity.kt        # pide permiso, arranca/para, muestra la URL
│   │   ├── ScreenMirrorService.kt # foreground service con MediaProjection
│   │   ├── CaptureManager.kt      # captura pantalla -> JPEG
│   │   ├── StreamServer.kt        # servidor HTTP + MJPEG + página web
│   │   └── NetworkUtils.kt        # obtiene la IP local
│   └── res/values/                # textos y tema
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

---

## 8. Alcance y límites (importante)

- Es **solo ver**, no controlar. No inyecta toques ni teclas.
- Requiere **consentimiento visible** en el emisor (permiso de captura). No es sigiloso:
  es una herramienta de transmisión legítima para tus propios dispositivos.
- **No** usa exploits ni vulnerabilidades. Usa las APIs públicas de Android.
