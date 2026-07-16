# ZombiePlagueMC Launcher

Repository: `VIZZITEAM/Launcher`

This project is the ZombiePlagueMC fork of the GravitLauncher framework. It
contains LaunchServer, launcher protocol modules, client framework code and
server wrapper components.

The JavaFX visual runtime is maintained separately in
`VIZZITEAM/LauncherRuntime`.

## Project Layout

```text
components/launcher-api       request/response contracts and protocol types
components/launcher-client    client networking and request logic
components/launcher-core      shared serialization, profiles and helpers
components/launcher-runtime   RuntimeProvider framework interfaces
components/launcher-start     client process bootstrap and JVM setup
components/launchserver       LaunchServer, auth and profile providers
components/serverwrapper      Minecraft server wrapper
modules/                      optional Gravit modules
docs/                         ZombiePlagueMC integration docs
```

## ZombieSite Integration

Current production integration uses:

- `siteHttp` auth provider for account login, 2FA, bans and email verification;
- remote profile provider backed by ZombieSite admin-managed launcher profiles;
- `/api/launcher/bootstrap` for LaunchServer connection regions;
- launcher API key generated in the ZombieSite admin panel.

See [docs/launcher-site-integration.md](docs/launcher-site-integration.md).

## Build

Use JDK 25 for current local checks:

```powershell
$env:JAVA_HOME='C:\Users\danil\.jdks\openjdk-25.0.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
./gradlew test
./gradlew build -x test
```

Targeted LaunchServer tests:

```powershell
./gradlew :components:launchserver:test
```

## Runtime

Build `VIZZITEAM/LauncherRuntime` and copy `JavaRuntime.jar` into the
LaunchServer modules directory before rebuilding the final launcher.

## Security

Do not commit:

- real launcher API keys;
- signing keys;
- `.env` files;
- local LaunchServer configs with secrets;
- generated build artifacts.

## Related

- [VIZZITEAM/LauncherRuntime](https://github.com/VIZZITEAM/LauncherRuntime)
- [VIZZITEAM/ZombieMCSite](https://github.com/VIZZITEAM/ZombieMCSite)
- [GravitLauncher/Launcher](https://github.com/GravitLauncher/Launcher)
