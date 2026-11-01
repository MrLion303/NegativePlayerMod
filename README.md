# NegativePlayerMod

Mod para **Minecraft Forge 1.20.1** (Forge 47.4.28). Reproduce videos a pantalla completa para los jugadores seleccionados desde el servidor.

## Reproducción

```mcfunction
/negativeplayer <URL> <fadein> <fadeout> <targets> <volumen>
```

Ejemplo:

```mcfunction
/negativeplayer "https://www.youtube.com/watch?v=WGDLCsLLxkw" 5 5 @a 50
```

- `fadein` y `fadeout`: ticks (20 ticks = 1 segundo).
- `targets`: selectores de jugadores vanilla como `@a`, `@p`, `@s` o `@r`.
- `volumen`: entero de 1 a 100.
- El comando requiere permiso de operador nivel 2.
- No se envían confirmaciones normales al chat; los diagnósticos técnicos se guardan en los logs.

## Controles

```mcfunction
/negativeplayer pause @a
/negativeplayer resume @a
/negativeplayer volume 35 @a
/negativeplayer stop @a
```

También puedes cerrar la pantalla con ESC. El cierre respeta el fade out configurado.

## Fuentes

**YouTube:** usa el reproductor oficial incrustado de YouTube. Se admiten videos públicos o no listados que permitan inserción. No se omiten restricciones de edad, región, autenticación, privacidad ni inserción.

**Google Drive:** usa la vista previa oficial de archivos compartidos. Cada jugador debe tener permiso de visualización y el archivo debe ser compatible con el reproductor de Google Drive.

Enlaces HTTPS aceptados:
- YouTube: `youtube.com/watch?v=...`, `youtu.be/...`, `/shorts/`, `/live/` y `/embed/`.
- Drive: `drive.google.com/file/d/ID/view`, `/preview`, `/open?id=...` y `/uc?id=...`.

## Reproductor integrado y primer inicio

NegativePlayer distribuye su motor de navegador integrado en el propio JAR mediante Rinku 3.0.4. No necesitas descargar ni instalar otro archivo de mod para utilizar NegativePlayer.

En el primer inicio del cliente, el motor descarga el runtime nativo de Chromium correspondiente al sistema operativo y lo guarda en la carpeta de datos de Minecraft. Esa descarga inicial es grande, requiere internet y puede tardar; no es una descarga de video. El runtime queda almacenado para los siguientes inicios.

La página del reproductor se sirve desde una dirección local de loopback (127.0.0.1). En YouTube, esto proporciona el contexto HTTP Referer que requiere el reproductor integrado y permite controlar la reproducción mediante la API oficial de YouTube. Google Drive se muestra dentro de la misma página local.

Plataformas de escritorio soportadas por el motor integrado: Windows 10/11, macOS 11+ y Linux glibc 2.31+ (x86_64/ARM64 según el runtime publicado). Android no es compatible.

Nota técnica: el usuario instala un único JAR de NegativePlayer; internamente ese JAR incluye el componente de navegador y el motor descarga sus binarios nativos en el primer inicio.

## Sincronización y rendimiento

El servidor envía la solicitud con 60 ticks de anticipación. Cada cliente prepara Chromium y espera el tick de inicio del mundo para pedir la reproducción. La sincronización puede variar por carga de red, buffering y restricciones de cada proveedor.

El navegador inicia el video silenciado para permitir la reproducción automática de Chromium y restaura el volumen configurado cuando comienza. El audio de Minecraft se silencia temporalmente durante la reproducción y se restaura al cerrar el reproductor. No se extraen streams privados ni se intenta evadir controles de las plataformas.

Si un video no permite inserción, requiere autenticación o no está compartido, no se puede reproducir desde el mod. Revisa `logs/latest.log` cuando aparezca un error.

## Compilación

- Java 17
- Gradle 8.8
- Minecraft 1.20.1
- Forge 47.4.28

`gradle clean build jarJar` genera el JAR de distribución `build/libs/NegativePlayer-0.3.3-all.jar`.

Rinku está licenciado bajo LGPL-2.1-or-later y se distribuye sin modificar como biblioteca separada dentro del JAR. Se incluye la licencia en `LICENSE-RINKU.txt`; su código fuente está en https://github.com/Keksuccino/Rinku.
