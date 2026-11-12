# GeeFlow profile library sync

This folder defines the v1 catalog API used by the app and the service hosted on the HK VPS. The catalog is the shared source for recipes and favorite state. App database IDs, selected users, and list positions stay on each device.

## Catalog shape

`catalog.json` is the versioned catalog format. Each item has a stable UUID, an item revision, a favorite flag, a deletion tombstone, and a portable profile body. Keep tombstones in the catalog so another device cannot restore a deleted item.

The app sends the catalog revision in `If-Match`. The server returns HTTP 409 with the current catalog if another write happened since the client read it. The server increments the catalog revision and changed item revisions on each accepted write.

## API

- `GET /profile-library/api/v1/health` returns service status without profile data.
- `GET /profile-library/api/v1/catalog` returns the current catalog. Send `Authorization: Bearer <device token>`.
- `PUT /profile-library/api/v1/catalog` replaces the catalog. Include the same bearer token, the current revision in `If-Match`, and the full catalog body. Keep all old IDs and represent deletion with `deleted: true`.

The service binds to localhost only. Nginx proxies the API path to it behind the existing HK hosting tunnel. Tokens and request bodies are not written to access logs. `index.html` is a private read view that asks for the token in the current browser session and does not save it. The catalog is currently empty; the first app sync can upload existing custom profiles, while unchanged built-in defaults stay local.

## Editing with Codex

The catalog will live at `/var/lib/geeflow-profile-library/catalog.json` on HK. Codex can edit that file over the verified `hk-vps` SSH route. After a manual edit, increment the top-level `revision` by one and increment each changed record's `revision`. Preserve stable IDs and tombstones. Validate the JSON before replacing the live file. The service uses an atomic file replacement for app writes, and its process lock protects revision checks from concurrent requests. Codex should take the same lock while replacing the file: `flock /var/lib/geeflow-profile-library/catalog.lock ...`.

The app remains usable offline and retries sync on launch, after local profile or favorite changes, and every 15 minutes. Its bearer token is kept in the app's existing preferences store, which this feature does not encrypt; use a dedicated library token and do not reuse an account password.

## Deployment settings

The service reads these environment variables from a root-owned mode-600 file:

- `PROFILE_LIBRARY_TOKEN`: random bearer token shared with the user's devices.
- `PROFILE_LIBRARY_DATA_FILE`: catalog path, default `/var/lib/geeflow-profile-library/catalog.json`.
- `PROFILE_LIBRARY_BIND`: loopback address, default `127.0.0.1`.
- `PROFILE_LIBRARY_PORT`: port, default `8093`.

Do not commit the live bearer token.

`geeflow-profile-library.service` contains the hardened systemd unit. Include `geeflow-profile-library-nginx.conf` inside the hosting HTTPS origin server block, then run `nginx -t` before reloading. Copy `index.html` and `catalog.schema.json` to `/var/www/hk-site/profile-library/` and keep the catalog and bearer token outside the web root.
