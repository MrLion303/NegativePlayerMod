# NegativePlayerMod

Mod para **Minecraft Forge 1.20.1**. El objetivo es reproducir videos de Google Drive y YouTube a pantalla completa para jugadores seleccionados desde un comando del servidor.

## Comando previsto

```mcfunction
/negativeplayer <URL> <fadein> <fadeout> <targets> <volumen>
```

Ejemplo:

```mcfunction
/negativeplayer "https://youtu.be/VIDEO_ID" 40 40 @a 100
```

- `fadein` y `fadeout`: ticks (20 ticks = 1 segundo).
- `targets`: selectores de jugadores de Minecraft, por ejemplo `@a`, `@p`, `@s` y `@r`.
- `volumen`: entero de 1 a 100.
- El comando no envía mensajes de confirmación ni avisos de reproducción al chat. Los errores de sintaxis o de URL sí se notifican como errores normales del comando.

## Plataformas

- Google Drive: enlaces HTTPS de archivos de video compartidos con los jugadores.
- YouTube: videos públicos y no listados que permitan reproducción integrada. No se admitirán videos privados ni videos cuya inserción esté desactivada.

La compatibilidad definitiva depende de las restricciones que aplique cada plataforma y de los permisos del video.

## Estado del proyecto

**Etapa inicial de infraestructura.** Esta primera base incluye el proyecto Forge, registro del comando, validación de dominios, canal de red servidor-cliente y compilación automatizada. El motor que descarga/decodifica Drive y la integración del reproductor integrado de YouTube todavía no están implementados; por tanto, esta versión inicial no reproduce videos todavía.

No se usa WaterMedia. La arquitectura multimedia se desarrollará por separado para no bloquear el hilo principal del cliente. La sincronización inicial reserva un margen de preparación entre clientes, pero la sincronización exacta tendrá que ajustarse con el reproductor real.

## Requisitos de desarrollo

- Java 17
- Gradle 8.8 o compatible con ForgeGradle 6
- Minecraft 1.20.1
- Forge 47.4.28

## Compilar

```bash
gradle build
```

El JAR se genera en `build/libs/`. GitHub Actions compila el proyecto y publica el JAR como artefacto de cada ejecución.

## Diseño técnico

- Comando registrado en el servidor y restringido a nivel de permiso 2.
- Un paquete S2C contiene URL, fades, volumen y tick de inicio previsto.
- No se envían mensajes de éxito al chat.
- El cliente recibirá las solicitudes a través de un manejador aislado en el lado cliente.
- Los errores y diagnósticos técnicos se reservan para los registros, no para mensajes de reproducción en el chat.
