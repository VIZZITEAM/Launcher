# Launcher Site Integration

This document describes the current Launcher, LaunchServer, LauncherRuntime, and site API integration.

## Components

- `LaunchServer` is still the authoritative launcher backend.
- ZombieSite is the source of truth for accounts, passwords, 2FA, bans, email verification, launcher profiles, launcher API keys, and connection regions.
- `LauncherRuntime` shows the server list before login. Playing still requires authorization.
- The launcher contains only the base LaunchServer URL and optional site bootstrap URL. Region lists are fetched from the site.

Source repositories:

- `VIZZITEAM/Launcher` - LaunchServer and launcher framework.
- `VIZZITEAM/LauncherRuntime` - JavaFX runtime UI.
- `VIZZITEAM/ZombieMCSite` - site API and admin panel.

## LaunchServer Config

Use the `siteHttp` auth core provider:

```json
{
  "auth": {
    "std": {
      "isDefault": true,
      "core": {
        "type": "siteHttp",
        "url": "https://site.example.com/api/launcher/auth",
        "bearerToken": "zmc-launcher-REPLACE_ME",
        "expireSeconds": 3600,
        "connectTimeoutMillis": 5000,
        "requestTimeoutMillis": 10000
      }
    }
  },
  "profilesProvider": {
    "type": "remote",
    "baseUrl": "https://site.example.com/api",
    "accessToken": "zmc-launcher-REPLACE_ME"
  },
  "launcherApi": {
    "siteApiBaseUrl": "https://site.example.com/api",
    "launcherBootstrapUrl": "https://site.example.com/api/launcher/bootstrap"
  }
}
```

The same launcher API key is used by:

- `siteHttp.bearerToken`
- `profilesProvider.accessToken`
- site admin "Launcher API key"

Do not commit real keys.

Example file:

```text
docs/examples/launchserver-site-http-auth.json
```

## Auth Flow

`siteHttp` sends login requests to:

```text
POST /api/launcher/auth
```

Request:

```json
{
  "login": "Player",
  "password": "plain password",
  "otp": "123456",
  "minecraftAccess": true
}
```

Handled statuses:

| Site status | Launcher behavior |
| --- | --- |
| `ok` | Creates OAuth and Minecraft session |
| `two_factor_required` | Opens the TOTP step |
| `invalid_credentials` | Shows wrong password |
| `invalid_otp` | Shows invalid 2FA code |
| `banned` | Shows blocked account |
| `email_not_verified` | Shows email verification error |
| `rate_limited` | Shows temporary retry-later error |

Every site response may contain `requestId`. Use it to find the exact request in site logs.

## Session Restore

After a LaunchServer restart, memory caches are empty. The provider now restores a user from the site by UUID:

```text
GET /api/launcher/users/{uuid}
Authorization: Bearer zmc-launcher-REPLACE_ME
```

This keeps access-token restore from depending only on the in-memory user map.

## Public Profiles

Profiles are served before authorization:

```text
GET /api/profile/list
```

Protected profile details still require the launcher API key:

```text
GET /api/profile/by/uuid/{uuid}
GET /api/profile/by/name/{name}
```

Profile editing belongs to the site admin panel. Client downloading is not moved to S3 yet; the current downloader still uses LaunchServer update providers.

## Connection Regions

The launcher can resolve LaunchServer endpoints from the site:

```text
GET /api/launcher/bootstrap
```

Payload:

```json
{
  "version": 1,
  "defaultMode": "auto",
  "healthTimeoutMs": 2500,
  "regions": [
    {
      "id": "ua",
      "name": "Ukraine",
      "apiUrl": "https://ua-launcher.example.com/api",
      "healthUrl": "https://ua-launcher.example.com/webapi/status",
      "priority": 10,
      "enabled": true,
      "recommended": true,
      "playerVisible": true,
      "fallbackOnly": false
    }
  ]
}
```

Launcher selection rules:

- Manual region is used if it is healthy.
- If manual region is unavailable, launcher falls back to auto.
- Auto mode checks enabled regions and picks the healthy endpoint by latency, priority, and last successful region.
- If the site bootstrap is unavailable, the launcher can use cached regions.
- Cache is stored in `%USERPROFILE%/.gravitlauncher/connection-cache.json`.

The cache is versioned and written atomically to reduce broken JSON after interrupted writes.

## Runtime Module

Build runtime:

```powershell
$env:JAVA_HOME='C:\Users\danil\.jdks\openjdk-25.0.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
./gradlew build
```

Copy the module into LaunchServer:

```powershell
Copy-Item C:\Users\danil\OneDrive\Documents\LauncherRuntime\build\libs\JavaRuntime.jar `
  C:\Users\danil\OneDrive\Documents\Launcher\components\launchserver\build\install\launchserver\modules\JavaRuntime.jar -Force
```

Rebuild launcher:

```powershell
@('build','stop') | .\bin\launchserver.bat
```

## Health Checks

Expected local endpoints:

```text
http://127.0.0.1:4173/api/health
http://127.0.0.1:4173/api/launcher/bootstrap
http://127.0.0.1:9274/webapi/status
```

Recommended checks:

```powershell
Invoke-WebRequest http://127.0.0.1:4173/api/health -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:4173/api/launcher/bootstrap -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:9274/webapi/status -UseBasicParsing
```

## Test Commands

Launcher:

```powershell
$env:JAVA_HOME='C:\Users\danil\.jdks\openjdk-25.0.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
./gradlew test
./gradlew build -x test
```

LauncherRuntime:

```powershell
$env:JAVA_HOME='C:\Users\danil\.jdks\openjdk-25.0.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
./gradlew test build --rerun-tasks
```

On Windows with OneDrive, `./gradlew clean` may fail if Java/Gradle or a browser keeps files open under `build`. Stop running launcher processes and Gradle daemons before clean.
