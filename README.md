<p align="center">
  <img alt="ProxyPulse — MTProto-прокси для Telegram (Android)" src="docs/banner.jpg" width="900">
</p>

<p align="center">
  <strong>Поиск MTProto-прокси и проверка доступности</strong><br>
  Подключение в Telegram одним тапом · Android · без VPN
</p>

<p align="center">
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-android/releases/latest"><img src="https://img.shields.io/github/v/release/VorozhbitDM/ProxyPulse-MTProto-android?style=flat-square&label=Release&color=brightgreen" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="MIT"></a>
  <img src="https://img.shields.io/badge/Android-8.0+%20(API%2026)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-android/releases"><img src="https://img.shields.io/github/downloads/VorozhbitDM/ProxyPulse-MTProto-android/total?label=Downloads&logo=github&style=flat-square&cacheSeconds=600" alt="Downloads"></a>
  <br>
</p>

---

[💻 Версия для Windows](https://github.com/VorozhbitDM/ProxyPulse-MTProto-for-TG)

---

## Возможности

- собирает прокси из ленты @ProxyMTProto (лимит настраивается, по умолчанию 100);
- два режима источника: **прямой** (t.me с пагинацией) и **обходной** (TGStat + web.archive.org);
- вход в **TGStat** через Telegram-бота @tg_analytics_bot для полной ленты;
- проверяет **доступность**;
- сортирует по задержке;
- показывает дату публикации поста;
- открывает выбранный прокси в **Telegram** одним тапом.

---

## Быстрый старт

1. Установите APK из [релизов](https://github.com/VorozhbitDM/ProxyPulse-MTProto-android/releases/latest) или соберите сами (см. ниже).
2. Запустите **ProxyPulse** и нажмите **«Начать поиск»**.
3. Тап по прокси в списке — Telegram предложит подключение.

### Настройки

Кнопка **«Настройки»** на стартовом экране и в шапке во время поиска.

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| **Лимит прокси** | `100` | Сколько уникальных прокси собрать за один поиск (от 10 до 500, шаг 10). |
| **Источник ленты** | Обходной | **Прямой (t.me)** — только живая лента Telegram. **Обходной (TGStat + Archive)** — сначала TGStat, затем добор из archive.org. |
| **TGStat** | не входили | Для обходного режима: вход через бота @tg_analytics_bot открывает пагинацию (без входа ~30 прокси). |
| **Тема** | Тёмная | Переключатель «Тёмная» / «Светлая». |

Настройки сохраняются на устройстве (DataStore) и подхватываются при следующем запуске.

На экране поиска: **«Загрузка… N из 100»** — число справа берётся из настройки. Проверяются все загруженные прокси; **«Найдено M»** — сколько из них оказались доступными.

### Требования

| | |
|---|---|
| ОС | Android **8.0+** |
| Telegram | Установленное приложение Telegram |
| Сеть | Доступ в интернет для сбора ленты и проверки TCP |

---

## Сборка из исходников

```bash
git clone https://github.com/VorozhbitDM/ProxyPulse-MTProto-android.git
cd ProxyPulse-MTProto-android
```

Нужны **JDK 17+** и Android SDK (API 35). Подробности — в комментариях к `local.properties` и в [docs/signing.md](docs/signing.md) для release.

**Windows (PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
cd android
.\gradlew.bat assembleDebug
```

**Linux / macOS:**

```bash
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

Release-сборка (подписанный APK, можно ставить на телефон):

```powershell
cd android
.\gradlew.bat assembleRelease
```

Файл: `app\build\outputs\apk\release\app-release.apk` (не `app-release-unsigned.apk`).

Для GitHub Releases скопируйте и переименуйте, например: `ProxyPulse-v2.9-android.apk` (версию возьмите из `versionName` в `app/build.gradle.kts`).

Без `keystore.properties` release подписывается debug-ключом — для своего телефона это нормально. Для GitHub/Play создайте keystore — см. [docs/signing.md](docs/signing.md).

Быстрая установка для теста: `app\build\outputs\apk\debug\app-debug.apk`

Установка: `adb install -r app\build\outputs\apk\release\app-release.apk`

### Иконка приложения

Положите исходник как **`android/Newicon333.jpg`** или **`android/icon.png`**, затем:

```powershell
cd android
.\scripts\GenerateLauncherIcons.ps1
```

Скрипт создаёт `mipmap-*/ic_launcher*.png` (для `Newicon333*` — масштаб **0.68** и фон `#2AABEE` в `colors.xml`; для логотипа без фона: `-Scale 0.52`). Приоритет: `Newicon333.*` → `android/icon.*` → корень репозитория → Desktop. После смены иконки: пересборка APK и переустановка (лаунчер кэширует иконки).

---

## Поддержка и развитие проекта

<p align="center">
  <a href="https://yoomoney.ru/to/4100119536071248">
    <img src="docs/yoomoney-support.png" alt="Перевести на ЮMoney" width="140" height="36">
  </a>
</p>

Проект с открытым исходным кодом. Если ProxyPulse вам помог — буду благодарен любой сумме.

---

## Лицензия

[MIT](LICENSE) — используйте и распространяйте свободно с указанием авторства.

---

<p align="center">
  <a href="https://github.com/VorozhbitDM">Denis Vorozhbit</a> ·
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-android">ProxyPulse-MTProto-android</a> ·
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-for-TG">Windows</a>
</p>
