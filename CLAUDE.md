# CLAUDE.md — AlaMine Launcher (Gravit fork)

## Что это

**Форк** [`GravitLauncher/Launcher`](https://github.com/GravitLauncher/Launcher). Основная ветка — `master`.

Это **backend/framework Gravit Launcher'а** — мультипроект Gradle. **В этом репо НЕТ UI-ресурсов (FXML, CSS, images)** — они живут в отдельном репо [`GravitLauncher/LauncherRuntime`](https://github.com/GravitLauncher/LauncherRuntime), который мы тоже должны форкнуть (**TODO:** см. раздел «Что ещё нужно форкнуть»).

### Реальная структура (проверено через find/ls)

```
components/
├── launcher-api/         ← API-контракты, protocol types
├── launcher-client/      ← клиентская библиотека для работы с LaunchServer
├── launcher-core/        ← общая логика (utils, helpers, serialization)
├── launcher-runtime/     ← runtime framework — интерфейс `RuntimeProvider`
│                          (НЕ содержит реализации UI! только контракты)
├── launcher-start/       ← стартер процесса клиента (JavaFX module resolution,
│                          запуск Minecraft)
├── launchserver/         ← серверная часть — НЕ трогаем, берём из релизов
└── serverwrapper/        ← обёртка для запуска MC серверов
modules/                  ← опциональные расширения
java24args.txt            ← JVM args для Java 24+
javaargs.txt              ← общие JVM args
Dockerfile                ← эталонный Docker для LaunchServer
```

### UI архитектура: где она на самом деле

`launcher-runtime/src/main/java/.../gui/RuntimeProvider.java` — это **интерфейс**:

```java
public interface RuntimeProvider {
    void run(String[] args);
    void preLoad();
    void init(boolean clientInstance);
}
```

Реализация подставляется извне при сборке. В upstream есть заглушка `NoRuntimeProvider` (молча делает ничего). **Настоящая реализация с JavaFX UI находится в `GravitLauncher/LauncherRuntime`** — отдельный проект, который собирается как assets и упаковывается в Launcher.jar на этапе сборки LaunchServer'а.

JavaFX модули на клиенте добавляются через `launcher-start/ClientLauncherWrapper.java` (проверено):
```java
context.jvmModules.add("javafx.base");
context.jvmModules.add("javafx.graphics");
context.jvmModules.add("javafx.fxml");
context.jvmModules.add("javafx.controls");
context.jvmModules.add("javafx.media");
context.jvmModules.add("javafx.web");
```

То есть JavaFX **используется**, но UI resources (FXML/CSS) живут в **LauncherRuntime** репо, не здесь.

## Что ещё нужно форкнуть

**TODO:** форкнуть [`GravitLauncher/LauncherRuntime`](https://github.com/GravitLauncher/LauncherRuntime) в `AlaMine-Team-Org` как `LauncherRuntime`, добавить submodule `client/launcher-runtime/`.

Без этого форка мы **не можем менять UI лаунчера** — только backend. Brand/тема/цвета/FXML — всё там. Структура LauncherRuntime (проверено через upstream API):

```
runtime/
├── scenes/              ← FXML layout'ы экранов (login, servermenu, options, ...)
├── components/          ← переиспользуемые компоненты (кнопки, панели, нотификации)
│   ├── buttons/         (back, cancel, close, settings, ...)
│   └── panels/          (leftpanel, ...)
├── dialogs/             ← диалоговые окна
├── styles/              ← CSS стили
├── themes/              ← цветовые темы
├── images/              ← иконки, баннеры
├── overlay/             ← overlay/прогресс окна
├── favicon.png
├── runtime_ru.properties, runtime_en.properties, runtime_uk.properties, ... ← локализация
└── src/                 ← Java код UI-слоя (controllers for FXML)
```

**Это то, что на самом деле кастомизирует Danil.** Текущий репо (`client/launcher`) — это фреймворк, на котором эта UI работает.

## Роль в стеке AlaMine

```
[AlaMine LauncherPrestarter (.exe)]  ← client/prestarter
          │
          │ скачивает + запускает
          ▼
[AlaMine Launcher.jar]                ← ЭТО ТЕКУЩЕЕ репо (components/launcher)
          │
          │ показывает UI, логин, выбор сервера, скачку модов
          │ запускает
          ▼
[Minecraft + mods + GravitGuard]      ← client/guard + mods
```

**Launcher.jar — это лицо проекта для игрока.** 99% времени юзер смотрит именно на это окно. Престартер мелькнул и исчез, Minecraft внутри игры. Всё остальное — Launcher. Поэтому брендинг здесь критичен.

## Stack

- **Java 25** (ранее Java 21) — см. [LaunchServer Java 25 migration](../../docker/gravit-java25/)
- **JavaFX 25** — UI (FXML + CSS)
- **Gradle** — сборка (`./gradlew`)
- **Netty** — сетевой слой
- **Log4j2**, **SLF4J** — логирование
- **BouncyCastle** — подпись/проверка jar'ов

## Cross-platform: один jar на все OS

`Launcher.jar` — **один и тот же файл для Windows, macOS и Linux.** JavaFX 25 работает везде, где есть Java 25 + JavaFX runtime. LaunchServer раздаёт один jar, Prestarter (под каждую OS) скачивает этот jar и запускает его через локальный Java-рантайм.

**Что меняется под разные OS:**
- **Рантайм Java** — Prestarter скачивает свой Temurin JRE 25 для Windows/macOS Intel/macOS Apple Silicon/Linux x64 с нашего LaunchServer. Danil отвечает за подготовку этих runtime'ов в LaunchServer's `runtime/` директории.
- **Пути к файлам** — `%APPDATA%\AlaMine` / `~/Library/Application Support/AlaMine/` / `~/.local/share/AlaMine/`. Launcher.jar получает путь через системные env vars, специально ничего делать не надо — JavaFX корректно работает с каждой из схем.
- **Иконка окна** — JavaFX автоматически подхватывает правильный формат (icns/ico/png) из ресурсов jar.

**Что НЕ меняется:**
- FXML/CSS/ресурсы — те же
- Логика, endpoint'ы, интеграции — те же
- Брендинг — применяется один раз, работает везде

Это сильно упрощает работу Danil'у: правишь UI один раз → работает у всех трёх групп игроков.

## Разработка

### Требования

- **JDK 25** (Temurin рекомендован) + **JMODS OpenJFX 25**
- Для кастомизации UI — [Scene Builder](https://gluonhq.com/products/scene-builder/) (визуальный редактор FXML)
- Gradle wrapper уже в репо — отдельно ставить не нужно

### Сборка

```bash
# Сборка только клиента (основная рабочая команда для брендинга)
./gradlew :components:launcher:build

# Сборка launchserver (обычно не нужна — берём из upstream-релизов)
./gradlew :components:launchserver:installDist

# Полная сборка всего проекта
./gradlew build
```

Результат сборки клиента: `components/launcher/build/libs/Launcher-<version>.jar` — это тот самый файл, который LaunchServer подписывает нашим приватным ключом и раздаёт через Prestarter.

### Dev-цикл брендинга

1. Правишь FXML/CSS/assets в `components/launcher/`
2. `./gradlew :components:launcher:build`
3. Положить собранный jar рядом с локальным LaunchServer'ом
4. LaunchServer автоматически подпишет и будет раздавать твой jar
5. Запускаешь Prestarter — он качает твой обновлённый Launcher.jar

Для быстрого цикла отладки UI без LaunchServer: можно запустить `Launcher.jar` напрямую через `java -jar`, передав флаг `--dev` (смотри конкретику в `components/launcher/src/main/java/.../LauncherMain.java`).

## Где что править — кастомизация AlaMine

### Визуал UI (самое важное) — НЕ В ЭТОМ РЕПО!

**Важно:** UI resources (FXML/CSS/images/локализация) находятся в **отдельном репо** `GravitLauncher/LauncherRuntime`, который мы должны форкнуть отдельно как `client/launcher-runtime/`. См. раздел «Что ещё нужно форкнуть» выше.

**В этом репо (`client/launcher`)** правятся:
- Backend-логика в `components/launcher-client/`, `launcher-core/`, `launcher-runtime/` — если нужно добавить новые API, новые типы запросов к LaunchServer, новые события, новые интеграции
- Старт клиентского процесса и JVM args в `components/launcher-start/`
- Protocol/serialization в `components/launcher-api/`

### Backend-кастомизация (то что делается ЗДЕСЬ)

AlaMine-специфичные фичи backend-а, которые понадобятся:

- **Интеграция с AlaCoin** — новый API для баланса кошелька. Нужны новые request/response типы в `launcher-api/`, клиент в `launcher-client/`, и соответствующий endpoint в LaunchServer.
- **Интеграция с WordPress** — news feed парсинг, OAuth через WP (если захотим single sign-on с сайтом).
- **Статус серверов** — запрос списка серверов с онлайн-статусом, kept in sync с серверной частью.
- **Custom events** — новые события `ClientPreGuiPhase`, `ClientProcessBuilderPreLaunchEvent` уже есть для хуков, можно добавить свои.

**UI изменения (лого, цвета, FXML, локализация) делаются в `client/launcher-runtime/` (когда форкнем) — не здесь.**

### AlaMine-специфичные фичи, которые добавим

Что планируется допилить в клиенте (каждое — отдельная задача Danil'у):

1. **Интеграция AlaCoin** — показывать баланс кошелька игрока на главном экране. Endpoint LaunchServer API → Launcher.jar → UI-блок в header.
2. **Новости с сайта** — блок «Последние новости» на стартовом экране, парсит RSS/JSON с `alamine.day/news/feed/`.
3. **Кнопки быстрых ссылок** — Discord, Telegram, форум, донаты. В header или footer главного окна.
4. **Статус серверов** — онлайн/офлайн + количество игроков для каждого сервера из списка. Через LaunchServer API.
5. **Кастомные экраны** — промо-блоки, акции, баннеры с новыми сборками. Опционально.
6. **Skin viewer** — превью скина игрока (интеграция со skin-viewer с сайта, если возможно). Опционально.
7. **Локализация** — убедиться что есть полный русский + украинский перевод всех строк UI.

### Конфиг клиента

`components/launcher/src/main/resources/config.json` (или аналог) — URL LaunchServer, публичный ключ, дефолтные настройки. Меняем под AlaMine инфру.

## Обновление Java 25

**Наше LaunchServer контейнер-изображение уже работает на Java 25** (см. [`docker/gravit-java25/`](../../docker/gravit-java25/) в `platform/infrastructure`). Клиентский `Launcher.jar` тоже должен собираться под Java 25, чтобы у юзеров на Windows не было конфликтов версий.

Проверить в `build.gradle.kts` корневом:
```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

## Sync с upstream

Gravit активно развивает проект. Рекомендуемый workflow:

```bash
# Синхронизация master с апстримом
gh repo sync AlaMine-Team-Org/Launcher --source GravitLauncher/Launcher --branch master

# Для experimental/6.0.0 (следующая мажорка)
gh repo sync AlaMine-Team-Org/Launcher --source GravitLauncher/Launcher --branch experimental/6.0.0
```

**Стратегия:** держать наши бренд-коммиты в отдельных файлах (`components/launcher/src/main/resources/runtime/`). Править только эти файлы, не трогая логику в `src/main/java/` — тогда апстрим-мержи почти никогда не конфликтуют. Если нужна новая фича — добавляем в своих файлах или новых классах, не патчим существующие.

## Owners

- **Danil (Java Lead)** — primary, всё, что касается Launcher.jar, модов, сборок Minecraft
- **@StepanchukYI** — LaunchServer инфра, подпись, CI/CD интеграция, code review
- **@Ma3auka** — дизайн всех экранов (Figma → FXML), бренд-элементы, иконки

## Deployment flow

1. Danil коммитит изменения в `components/launcher/`
2. GitHub Actions собирает `Launcher-<version>.jar`
3. Jar автоматически деплоится на наш LaunchServer (через SSH/rsync или API)
4. LaunchServer подписывает jar своим приватным ключом
5. Юзеры при следующем запуске Prestarter получают новый Launcher.jar автоматически

CI/CD workflow будет в `.github/workflows/build-launcher.yml` (TODO).

## Релатед

- [AlaMine LauncherPrestarter fork](https://github.com/AlaMine-Team-Org/LauncherPrestarter) (`../prestarter` submodule) — bootstrap, качает этот Launcher.jar
- [AlaMine GravitGuard fork](https://github.com/AlaMine-Team-Org/GravitGuard) (`../guard` submodule) — антиинжект для защиты клиента
- [Platform monorepo](https://github.com/AlaMine-Team-Org/platform)
- [Pelican eggs repo](https://github.com/StepanchukYI/pelican-eggs) — Docker образ LaunchServer на Java 25

## Links

- [Upstream GravitLauncher/Launcher](https://github.com/GravitLauncher/Launcher)
- [Gravit Wiki](https://gravitlauncher.com)
- [GravitLauncher Discord](https://discord.gg/b9QG4ygY75)
- [JavaFX 25 docs](https://openjfx.io/javadoc/25/)
- [Scene Builder](https://gluonhq.com/products/scene-builder/)
