# AdvancedWayPoints

Waypoints personales para Minecraft con etiquetas flotantes (TextDisplay) que siempre miran al jugador, menús configurables, compartir, favoritos, colores, iconos y una API para desarrolladores.

Personal waypoints for Minecraft with floating labels that always face the player, configurable menus, sharing, favorites, colors, icons and a developer API. *(English summary at the end.)*

**Autor:** XDiez ([Obed-source](https://github.com/Obed-source)) · **Licencia:** Apache 2.0 (hay que dar crédito, ver [NOTICE](NOTICE))

---

## Compatibilidad

| | |
|---|---|
| Minecraft | 1.19.4 en adelante |
| Servidores | Spigot, Paper, Purpur, Folia |
| Java | 17 o superior |
| Dependencias opcionales | WorldGuard, Vault, PlaceholderAPI |

Los datos se guardan en SQLite (el driver ya viene en el servidor, el jar pesa poco). Todo acceso a disco y a internet corre en hilos aparte para no afectar los TPS.

## Cómo funciona

- `/wp create casa de amigo` guarda tu posición actual con ese nombre (letras con tildes y ñ, números y espacios; 32 caracteres por defecto).
- Cada waypoint recibe un ID de 4 caracteres (`A-Z a-z 0-9`) único por jugador. El jugador no lo ve: en el chat se hace clic sobre el waypoint (o shift + clic para escribir su ID).
- Al crearlo, el icono y su color son aleatorios. El fondo es negro con transparencia.
- Los waypoints solo los ve su dueño. Si está en otro mundo no se dibuja, pero sigue seleccionado y aparece al entrar a ese mundo.
- **Modo de vista:** *uno a la vez* (elegir otro oculta el anterior; elegir el mismo lo apaga) o *acumular* (se suman).
- Compartir crea una **copia** para el otro jugador, que se le muestra al instante. No queda registro de con quién lo compartiste.
- Favoritos salen primero en la lista y en el menú; el resto va por fecha.
- La etiqueta muestra la distancia, por ejemplo `❤ casa (30m)`. Los waypoints lejanos se dibujan a una distancia fija en su dirección y se agrandan para que se lean.

## Comandos

El comando base es `/waypoints` con alias `/wp` (ambos se cambian en `config.yml`). `<id>` también acepta el nombre exacto.

| Comando | Descripción |
|---|---|
| `/wp` | Abre el menú (o la ayuda, según config) |
| `/wp help [página]` | Ayuda paginada |
| `/wp menu` | Menú interactivo |
| `/wp create <nombre>` | Crea un waypoint donde estás |
| `/wp list [página]` | Lista clicable (clic = mostrar/ocultar) |
| `/wp view <id>` | Muestra u oculta un waypoint |
| `/wp hide <id>` / `/wp hide all` | Oculta de la vista (sigue en la lista) |
| `/wp mode` | Cambia entre uno a la vez y acumular |
| `/wp rename <id> <nombre>` | Renombra |
| `/wp color <id> <text\|icon\|background> <color>` | Cambia un color |
| `/wp favorite <id>` | Marca/desmarca favorito |
| `/wp share <id> <jugador>` | Regala una copia |
| `/wp delete <id>` | Elimina |
| `/wp restart` | Recarga la configuración (no la base de datos) |
| `/wp admin help [página]` | Todos los comandos, con colores y paginación |
| `/wp admin list <jugador> [página]` | Waypoints de cualquier jugador (online u offline) |
| `/wp admin menu <jugador>` | Edita los waypoints de un jugador desde el menú |
| `/wp admin info <jugador>` | Cantidad, límite, modo |
| `/wp admin tp <jugador> <id>` | Teletransporte al waypoint |
| `/wp admin create <jugador> <nombre>` | Crea un waypoint para otro jugador |
| `/wp admin rename <jugador> <id> <nombre>` | Renombra |
| `/wp admin color <jugador> <id> <tipo> <color>` | Cambia colores |
| `/wp admin delete <jugador> <id>` | Elimina |
| `/wp admin clear <jugador>` | Elimina todos |

El icono solo se elige desde el menú.

## Permisos

El plugin no gestiona permisos: usa el plugin de permisos que prefieras.

| Permiso | Por defecto | Uso |
|---|---|---|
| `wp.help`, `wp.menu`, `wp.create`, `wp.list`, `wp.view`, `wp.hide`, `wp.mode`, `wp.rename`, `wp.delete`, `wp.share`, `wp.favorite`, `wp.icon` | todos | Funciones del jugador (niega `wp.help` para quitar la ayuda) |
| `wp.limit.<n>` | — | Máximo de waypoints; gana el número más alto. Sin permiso se usa `default-limit` |
| `wp.limit.unlimited` | op | Sin límite |
| `wp.color.text` / `wp.color.icon` / `wp.color.background` | todos | Abrir cada selector de color |
| `wp.color.<tipo>.<color>` | op | Usar ese color (p. ej. `wp.color.background.red`). Los colores por defecto siempre están permitidos |
| `wp.color.<tipo>.custom` | op | Colores hex propios por comando (`#FF8800`, `&c`, `&#FF8800`) |
| `wp.name.format` | op | Usar códigos `&` en los nombres |
| `wp.bypass.restrictions` | op | Ignorar listas de mundos/regiones |
| `wp.bypass.cost` | op | Crear y compartir gratis |
| `wp.admin` | op | Todas las acciones admin (`wp.admin.list`, `.menu`, `.info`, `.tp`, `.create`, `.rename`, `.color`, `.delete`, `.clear`, `.restart`, `.update`, `.help`) |

En los menús, los botones sin permiso se muestran con un aviso en el lore (o se ocultan con `menus.hide-items-without-permission: true`).

## Configuración

| Archivo | Contenido |
|---|---|
| `config.yml` | Comando y alias, prefijo, límites, nombres, iconos, colores, listas blancas/negras de mundos y regiones WorldGuard, estilo de las etiquetas, entrada de texto, precios Vault, auto-update |
| `menus.yml` | Tamaño, materiales, posición de cada botón y desde qué casilla empieza la lista de cada menú |
| `sounds.yml` | Sonido, volumen y tono por acción (la misma acción suena igual por comando o por menú) |
| `lang/es.yml`, `lang/en.yml` | Todos los textos: mensajes, ayuda, lista, menús y nombres de colores |

Todos los archivos tienen comentarios y **se actualizan solos**: al subir de versión se agregan las opciones nuevas y se conservan tus valores (antes se guarda una copia `.bak`).

**Entrada de texto** (renombrar, compartir, crear desde el menú), en `input.type`:
`ANVIL` (yunque, por defecto) → `SIGN` (cartel temporal) → `COMMAND` (mensaje clicable que escribe el comando) → `CHAT` (el siguiente mensaje). Si el elegido no está disponible en el servidor se usa el siguiente. El yunque necesita Paper/Purpur/Folia o Spigot 1.21+.

**Vault:** precio por crear, por compartir y reembolso al eliminar; los tres desactivados por defecto.

**Auto-update:** activado por defecto. Lee [`update.json`](update.json) de este repositorio y descarga la versión nueva en la carpeta `update` del servidor; se instala al reiniciar.

## PlaceholderAPI

Identificador `awp`:

| Placeholder | Valor |
|---|---|
| `%awp_count%` / `%awp_limit%` / `%awp_remaining%` | Cantidad, límite, restantes |
| `%awp_visible_count%` / `%awp_favorites%` / `%awp_mode%` | Visibles, favoritos, modo de vista |
| `%awp_selected_name%` / `%awp_selected_icon%` / `%awp_selected_label%` | Último seleccionado |
| `%awp_selected_distance%` | Distancia en bruto, p. ej. `30m` |
| `%awp_selected_distance_formatted%` | Con el estilo de config, p. ej. ` (30m)` |
| `%awp_selected_distance_raw%` | Solo el número |
| `%awp_selected_world%` / `_x` / `_y` / `_z` / `_code` | Datos del seleccionado |
| `%awp_visible_list%` / `%awp_visible_distances%` | Todos los visibles |
| `%awp_nearest_name%` / `%awp_nearest_distance%` | El más cercano |
| `%awp_name_<id>%` / `%awp_distance_<id>%` / `%awp_visible_<id>%` | Por ID |

## API para desarrolladores

```java
AdvancedWaypointsAPI api = AdvancedWaypointsAPI.get();
api.getWaypoints(player.getUniqueId()).thenAccept(list -> { /* hilo en segundo plano */ });
api.createWaypoint(player.getUniqueId(), "Base", player.getLocation());
api.showWaypoint(player, "aB3x");
int limit = api.getLimit(player);
```

Eventos: `WaypointCreateEvent`, `WaypointDeleteEvent`, `WaypointShareEvent` (cancelables), `WaypointUpdateEvent`, `WaypointVisibilityEvent`.
Declara `softdepend: [AdvancedWayPoints]` en tu `plugin.yml`.

## Compilar

```
./gradlew build
```

El jar queda en `build/libs/AdvancedWayPoints-<versión>.jar`. Las pruebas (`./gradlew test`) cubren los YAML, la base de datos SQLite y las utilidades.

## Publicar una versión

1. Sube `version` en `build.gradle` y compila.
2. Crea un release `v<versión>` en GitHub con el jar.
3. Actualiza `update.json` (versión, enlace de descarga y `sha256` del jar). Los servidores la descargan solos.

---

## English summary

AdvancedWayPoints gives every player personal waypoints, drawn as floating text that faces them, with the distance (`❤ home (30m)`). Create one with `/wp create <name>` at your position; list them with `/wp list` (click to show) or manage them in `/wp menu` (left click show, right click edit: rename, text/icon/background color, icon, favorite, share, delete). View mode is *one at a time* or *accumulate*. Sharing gives the other player a copy. Admins (`/wp admin ...`) see and edit everyone's waypoints. Works on Spigot, Paper, Purpur and Folia 1.19.4+, stores data in SQLite, supports WorldGuard (world/region rules), Vault (optional prices), PlaceholderAPI (`%awp_...%`) and ships a developer API. Every file is commented, auto-updating, and translated into Spanish and English (`language: en`).

## Licencia

Apache License 2.0. Puedes usar, modificar y redistribuir el plugin, también gratis o en servidores públicos, siempre que mantengas el aviso de copyright y el archivo [NOTICE](NOTICE) que da crédito a **XDiez (Obed-source)**.
