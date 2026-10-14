# Ayuda de JellyVault

[English](en.md) · [Français](fr.md) · **Español** · [Deutsch](de.md) · [Volver al README](../../README.es.md)

- [1. Instalación](#1-instalación)
- [2. Inicia sesión en Jellyfin](#2-inicia-sesión-en-jellyfin)
- [3. Añade tu clave de TMDB](#3-añade-tu-clave-de-tmdb)
- [4. Conecta Radarr y Sonarr](#4-conecta-radarr-y-sonarr)
- [5. Solicita una película o una serie](#5-solicita-una-película-o-una-serie)
- [6. Vincula tu cuenta de TMDB (valoraciones y lista de seguimiento)](#6-vincula-tu-cuenta-de-tmdb-valoraciones-y-lista-de-seguimiento)
- [7. Idioma](#7-idioma)
- [8. Actualizaciones](#8-actualizaciones)
- [9. Solución de problemas](#9-solución-de-problemas)

## 1. Instalación

1. En la tele, permite las aplicaciones de orígenes desconocidos para la aplicación desde la que vayas a instalar (por ejemplo *Downloader* o un gestor de archivos).
2. Descarga `JellyVault.apk` desde la página de [Releases](https://github.com/romainio/JellyVault/releases), o el APK que corresponda a tu dispositivo (`JellyVault-armeabi-v7a.apk` para la mayoría de las teles, `JellyVault-arm64-v8a.apk` para los dispositivos recientes).
3. Abre el archivo para instalarlo. Desde un ordenador también puedes ejecutar `adb install JellyVault.apk`.

JellyVault necesita Android TV o Fire TV con Android 6.0 o posterior, y un servidor Jellyfin `10.10.x` o `10.11.x`.

Al iniciar la aplicación se reproduce un breve logotipo brillante y después se abre la página de inicio.

## 2. Inicia sesión en Jellyfin

En el primer arranque, añade la dirección de tu servidor Jellyfin e inicia sesión con tu cuenta de Jellyfin. JellyVault recuerda el servidor e inicia sesión automáticamente la próxima vez (*Configuración › Sign in automatically*).

Sin los pasos siguientes, JellyVault ya es un cliente de Jellyfin completo: bibliotecas, reproducción, subtítulos, TV en directo y música.

## 3. Añade tu clave de TMDB

TMDB alimenta las filas de descubrimiento (tendencias, géneros, estudios, recomendaciones) y las páginas de título de las películas y series que no tienes.

1. Crea una cuenta gratuita en [themoviedb.org](https://www.themoviedb.org), abre <https://www.themoviedb.org/settings/api> y solicita una clave de API.
2. En la tele, abre *Configuración › TMDB & IMDb ratings* y selecciona *TMDB API key*.
3. Escribe la *API key* (32 caracteres) o el *API read access token* (un texto largo que empieza por `eyJ`). Ambos sirven.
4. Reinicia la aplicación: las filas de descubrimiento se cargan en el siguiente arranque.

![Clave de TMDB](../screenshots/tmdb-key.jpg)

La clave se queda en la tele y no se envía a ningún sitio que no sea TMDB. Una clave con un espacio o con un carácter de menos se rechaza con *This key looks invalid*. Para eliminar la clave, guarda el campo vacío.

*Built into this version* significa que la compilación ya incluye una clave (solo en las compilaciones hechas con `tmdb.readToken` en `local.properties`). Una clave que escribas la sustituye.

En un teclado de tele, valida con la tecla ✓ del teclado y después selecciona *Guardar*.

![Escribir la clave](../screenshots/tmdb-key-dialog.jpg)

## 4. Conecta Radarr y Sonarr

Las solicitudes son opcionales. Necesitan [Radarr](https://radarr.video) para las películas y [Sonarr](https://sonarr.tv) para las series.

1. Abre *Configuración*. La sección *Requests and ratings* muestra *Radarr* y *Sonarr*.
2. En cada uno, rellena:
   - **Server URL**: la dirección que abres en un navegador, con su ruta base si la hay, por ejemplo `https://radarr.example.com` o `http://192.168.1.10:7878`.
   - **API key**: en Radarr o Sonarr, *Settings › General › Security › API Key*.
   - **Access username / password** (opcional): solo si Radarr o Sonarr están detrás de un proxy inverso con autenticación HTTP básica.
3. Selecciona **Test connection**. Muestra la versión de Radarr o Sonarr con ✓ cuando todo es correcto.
4. Elige el **Quality profile** y el **Root folder** que usarán las nuevas solicitudes.

![Configuración](../screenshots/settings.jpg)

![Configuración de Radarr](../screenshots/radarr.jpg)

## 5. Solicita una película o una serie

Abre cualquier título que no tengas, desde la página de inicio, una página de descubrimiento o la búsqueda.

- **Película**: *Request* la envía a Radarr de inmediato.
- **Serie**: *Request* pregunta qué enviar a Sonarr: *All seasons*, *Latest season* o *Choose…* para temporadas concretas.

![Solicitar una serie](../screenshots/request.jpg)

Después, el botón sigue la descarga: *Requested*, *Downloading 42%*, *Available soon* y, por último, *Watch* cuando el archivo ya está en Jellyfin. *Refresh* pide a Jellyfin que analice su biblioteca cuando una descarga ha terminado pero el título aún no aparece. En una serie de la que ya tienes algunas temporadas, el botón muestra *2/5 seasons · Request more*.

Si el botón dice *Set up Radarr* o *Set up Sonarr*, el servicio aún no está configurado: al seleccionarlo se abre su configuración.

## 6. Vincula tu cuenta de TMDB (valoraciones y lista de seguimiento)

Vincular tu cuenta de TMDB mejora las recomendaciones:

- tus valoraciones alimentan las filas *Because you watched…*, *Movies for you* y *Series for you*,
- los títulos que has valorado se marcan como vistos,
- tu lista de seguimiento de TMDB aparece como *Your list* en la página de inicio.

Abre *Configuración › TMDB & IMDb ratings › Link my TMDB account*, escanea el código con el móvil, inicia sesión en TMDB y pulsa *Approve*; después selecciona *Done, it's approved* en la tele. *Unlink* elimina la vinculación.

*Rate from the TV* (en *Configuración*) añade un botón *Rate* en las páginas de título. Cuando está desactivado, las valoraciones provienen solo de tu cuenta de TMDB.

**Valoraciones de IMDb**: JellyVault lee las valoraciones de TMDB. Para usar tus valoraciones de IMDb, cópialas a tu cuenta de TMDB con una herramienta de sincronización IMDb → TMDB que ejecutes tú mismo.

## 7. Idioma

*Configuración › User interface language* cambia el idioma de la aplicación. Los títulos, sinopsis, géneros, logotipos y tráilers de TMDB lo siguen.

- Las pantallas de JellyVault están traducidas al inglés y al francés. En otros idiomas, esas pantallas se muestran en inglés.
- Las descripciones de los títulos que posees provienen de tu servidor Jellyfin, en el *Preferred metadata language* (idioma preferido de los metadatos) de cada biblioteca. Cámbialo en el servidor y actualiza los metadatos de la biblioteca para tenerlas en otro idioma.
- El descubrimiento usa Francia como región: listas de tendencias, clasificaciones por edades y los estantes franceses.

## 8. Actualizaciones

El APK de la página de Releases comprueba en GitHub si hay versiones nuevas al iniciarse y muestra *Update available* cuando la hay. La sección *Acerca de* de *Configuración* también indica si hay una actualización disponible. Puedes desactivar la comprobación automática en *Configuración › Configuración avanzada › Buscar actualizaciones automáticamente*.

Instalar una versión nueva sobre la anterior conserva tu configuración, siempre que ambas estén firmadas con la misma clave. Una compilación que hayas hecho tú mismo (firmada con tu propia clave) no se puede actualizar con el APK de Releases: desinstálala primero, lo que borra la configuración de la aplicación.

## 9. Solución de problemas

**No hay filas de descubrimiento, o aparece "TMDB not configured: add your key in Settings › TMDB & IMDb ratings"**
Añade una clave de TMDB ([paso 3](#3-añade-tu-clave-de-tmdb)) y reinicia la aplicación.

**Errores de Radarr / Sonarr**

| Mensaje | Qué comprobar |
|---|---|
| *Radarr is unreachable* | La URL del servidor, y que la tele pueda acceder a esa dirección |
| *API key refused by Radarr* | La clave de API |
| *Access refused by the server in front of Radarr* | El usuario y la contraseña de acceso de tu proxy inverso |
| *Invalid Radarr settings (URL or key)* | Un error al escribir la URL (por ejemplo `htts://`) o la clave |
| *Not found in Sonarr* | Sonarr no encontró la serie, a menudo porque TMDB no tiene un id de TVDB para ella |

**El teclado de la tele pierde caracteres o el D-pad no puede salir de un campo de texto**
Algunos teclados de tele se comportan así. Escribe despacio, revisa el campo antes de guardar y valida con la tecla ✓ del teclado.

**El salvapantallas o el canal "Agregado recientemente" desapareció tras una actualización**
Vuelve a elegir el salvapantallas de JellyVault en los ajustes de Android TV y vuelve a permitir el canal desde los ajustes de canales de la pantalla de inicio.

**Recopilar los registros para un informe de error**
Consulta [DEVELOPMENT.md](../../DEVELOPMENT.md#gathering-app-logs) y luego [abre una incidencia](https://github.com/romainio/JellyVault/issues) con los registros, la versión de la aplicación (*Configuración › Versión instalada*) y el modelo de tu tele.
