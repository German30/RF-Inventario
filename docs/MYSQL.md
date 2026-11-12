# Base de datos

> **Este documento describía la conexión JDBC directa que tenía la app.**
> Ya no aplica: desde que la app pasó a consumir el backend Spring
> (`rf-inventario-panel-api`), **la app no vuelve a tocar MySQL**. Toda la
> configuración de base de datos —host, credenciales, esquema, migraciones—
> vive únicamente en el backend.

## Dónde está todo ahora

| Qué | Dónde |
|---|---|
| Conexión a MySQL (host/puerto/usuario/clave) | `rf-inventario-panel-api/src/main/resources/application.properties` (variables `DB_*`) |
| Esquema y sus cambios | Migraciones versionadas con **Flyway** en `rf-inventario-panel-api/src/main/resources/db/migration/` — ya no son scripts SQL sueltos que se corren a mano |
| Login (código + PIN) | `users.pin_hash` con **BCrypt** (antes SHA-256 sin salt) |
| Historial de stock | Tabla `stock_movements`, quién/cuándo/qué cambió por cada alta, edición, ajuste o borrado |

Repo del backend: `RF-Inventario-Web-Panel-Back/`. Ver su
`docs/` (o el README) para configuración, y `src/main/resources/db/migration/`
para el historial completo del esquema.

## Cómo se conecta la app ahora

La app habla HTTP con el backend vía Retrofit
([`data/remote/`](../app/src/main/java/com/rf/inventario/data/remote/)),
con un caché local (Room) que se sincroniza incrementalmente y una cola de
operaciones offline que se envía sola al volver la red (ver el README). Ver
[`docs/RELEASE.md`](RELEASE.md) para cómo se configura la URL del backend
(fija al compilar, o administrada por el MDM).
