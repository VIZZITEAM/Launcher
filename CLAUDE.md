# CLAUDE.md - ZombiePlagueMC Launcher

## What This Repo Is

Repository: `VIZZITEAM/Launcher`

This repo contains the GravitLauncher framework fork used by ZombiePlagueMC:
LaunchServer, launcher protocol/API modules, client launcher framework and
server wrapper code.

It does not own the visual JavaFX UI. FXML, CSS, images, fonts and most runtime
scene controllers live in `VIZZITEAM/LauncherRuntime`.

## Stack Role

```text
ZombieSite
  -> accounts, 2FA, bans, launcher profiles, launcher API keys, regions
LaunchServer from this repo
  -> auth through ZombieSite
  -> profile provider through ZombieSite
  -> builds/signs Launcher.jar
LauncherRuntime
  -> JavaFX UI packed into final Launcher.jar
Prestarter
  -> starts the final launcher on player machines
```

## Main Custom Pieces

### Site HTTP Auth

LaunchServer uses the neutral `siteHttp` auth provider. It calls the site API
for password checks, 2FA, bans, email verification and audit correlation.

Old config aliases may still exist in code for backwards compatibility, but new
configs and docs must use `siteHttp`.

### Remote Profile Provider

Launcher profiles are managed in the ZombieSite admin panel and served through
the site API. Public profile list must be available before player login, because
the runtime shows the server list immediately.

### Connection Regions

The launcher has a base URL, then receives connection region data from:

```text
/api/launcher/bootstrap
```

Regions describe LaunchServer endpoints, not alternative site admin URLs.
Runtime can use auto selection, manual selection and cached data when the site
is temporarily unavailable.

## Important Folders

```text
components/launcher-api/       protocol types and API contracts
components/launcher-client/    client-side networking library
components/launcher-core/      shared utilities and serialization
components/launcher-runtime/   RuntimeProvider interfaces only
components/launcher-start/     client process start/JVM module setup
components/launchserver/       LaunchServer and auth/profile providers
components/serverwrapper/      Minecraft server wrapper
modules/                       optional Gravit modules
docs/                          project integration docs
```

## LaunchServer Config Example

Use docs in `docs/launcher-site-integration.md` as the source of truth. Minimal
shape:

```json
{
  "auth": {
    "std": {
      "isDefault": true,
      "core": {
        "type": "siteHttp",
        "url": "https://site.example.com/api/launcher/auth",
        "bearerToken": "zmc-launcher-REPLACE_ME"
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

Do not commit real launcher API keys.

## Local Build

Use JDK 25 for local launcher checks:

```powershell
$env:JAVA_HOME='C:\Users\danil\.jdks\openjdk-25.0.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
./gradlew test
./gradlew build -x test
```

Useful targeted checks:

```powershell
./gradlew :components:launchserver:test
./gradlew :components:launcher:build
```

## Runtime Integration

Build `VIZZITEAM/LauncherRuntime`, then place `JavaRuntime.jar` into the
LaunchServer modules directory before rebuilding the final launcher:

```powershell
Copy-Item C:\Users\danil\OneDrive\Documents\LauncherRuntime\build\libs\JavaRuntime.jar `
  C:\Users\danil\OneDrive\Documents\Launcher\components\launchserver\build\install\launchserver\modules\JavaRuntime.jar -Force
```

## Git Rules

- Source of truth is `VIZZITEAM/Launcher`.
- Keep `origin` pointed at `git@github.com:VIZZITEAM/Launcher.git`.
- Keep upstream Gravit changes separate from ZombiePlagueMC integration changes.
- Do not commit local server configs, generated artifacts, private keys or
  launcher API keys.

## Related Repositories

- `VIZZITEAM/LauncherRuntime` - JavaFX UI runtime.
- `VIZZITEAM/ZombieMCSite` - site, admin panel and launcher API.
- `GravitLauncher/Launcher` - upstream launcher framework.
