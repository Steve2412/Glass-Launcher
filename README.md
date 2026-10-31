# Glass Launcher

Launcher estilo iOS (Liquid Glass) para pantallas Android de auto de 1280×720.

- Reloj grande, clima y estado del Bluetooth/WiFi.
- Widget de música que muestra la canción del iPhone por Bluetooth (Spotify, YouTube Music o cualquier app), con controles y carátula.
- Widget del teléfono con nombre y batería.
- Inicio con apps y widgets de Android en cuadrícula de 10×2 por página.
- Dock grande tipo CarPlay con botón de lista de apps y control de volumen.
- 8 fondos + foto propia, iconos Glass, originales o de packs de terceros (Nova/ADW/Apex).

## Descargar el APK

Cada push a `main` compila el APK en **Actions › Build APK › Artifacts** (`GlassLauncher-apk`).
Además, cada build de `main` publica el APK en **Releases** (el más reciente queda como "Latest").

El APK está firmado con una llave fija (`app/glass-debug.keystore`), así que las versiones nuevas se instalan encima de la anterior sin perder la configuración.

## Primer uso en la pantalla

1. Instala el APK (USB o descarga) y pulsa Inicio: elige **Glass Launcher** → **Siempre**.
   Si no pregunta: *Personalizar › Launcher predeterminado*.
2. Acepta los permisos de Bluetooth y ubicación (ubicación solo se usa para el clima).
3. En el widget de música pulsa **Activar** y habilita *Glass Launcher* en "Acceso a notificaciones".
   Si la pantalla oculta ese menú, se puede activar por ADB:
   ```
   adb shell cmd notification allow_listener com.glass.launcher/com.glass.launcher.media.MediaListenerService
   ```

## Cómo se usa

- **Mantener pulsado** un espacio vacío del inicio: añadir widget, añadir app, fondos e iconos.
- **Mantener pulsado** una app o widget del inicio: mover, cambiar tamaño, cambiar icono o quitar.
- **Mantener pulsado** un icono del dock: cambiar la app o quitarla.
- **Lista de apps** (botón de cuadrícula del dock): mantener pulsada una app para añadirla al inicio, al dock o cambiar su icono.
- **Personalizar**: fondos, packs de iconos, descarga de carátulas (con opción "Solo con WiFi") y pantalla completa.

## Carátulas

1. Primero usa la portada que manda el teléfono por Bluetooth (Android 11+ con AVRCP 1.6).
2. Si no llega, la busca en el catálogo público de iTunes por título y artista (se puede desactivar en Personalizar).

## Compilar localmente

Android Studio (JDK 17) → abrir la carpeta → `./gradlew assembleDebug`.
