# CLAUDE.md — AlaMine Launcher (Gravit fork)

## Что это

**Форк** [`GravitLauncher/Launcher`](https://github.com/GravitLauncher/Launcher). Основная ветка — `master`.

Это **весь исходный код GravitLauncher** — мультипроект Gradle со всеми компонентами (клиент + сервер + модули). Мы форкаем целиком, но **работаем только с клиентской частью** — серверную берём из официальных релизов Gravit.

```
components/
├── launcher/       ← ЭТО мы кастомизируем — клиентский Launcher.jar (JavaFX UI)
├── launchserver/   ← НЕ трогаем — используем официальный релиз v5.7.x
└── launcher-core/  ← общие классы, трогать только если нужно править протокол
modules/            ← опциональные расширения (OptionalModifications, BuilderBackendAPI, ...)
```

**Почему не только `components/launcher/` форк:**
Gradle-проект монолитный — нельзя форкнуть только один модуль. Поэтому берём весь репозиторий, но в AlaMine-нужные изменения идут только в `components/launcher/`.

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

### Визуал UI (самое важное)

```
components/launcher/src/main/resources/
├── runtime/
│   ├── scenes/          ← FXML-разметка экранов (login, serverMenu, options, ...)
│   │   ├── login.fxml
│   │   ├── servermenu.fxml
│   │   ├── options.fxml
│   │   └── ...
│   ├── styles.css       ← CSS стили (цвета, шрифты, размеры)
│   ├── images/          ← лого, иконки серверов, баннеры, background
│   ├── fonts/           ← кастомные шрифты (.ttf, .otf)
│   └── strings/         ← локализация (ru/en/uk)
```

**Основные точки кастомизации:**

- **`runtime/images/logo.png`** — главное лого AlaMine в окне лаунчера
- **`runtime/styles.css`** — цвета бренда (primary, accent, bg, text), шрифты, анимации
- **`runtime/scenes/*.fxml`** — разметка экранов (можно открыть в Scene Builder для визуального редактирования)
- **`runtime/images/background.jpg`** — фоновая картинка
- **`runtime/strings/ru.json`** — все русские строки UI

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
