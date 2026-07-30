<p align="center">
  <img alt="ProxyPulse Lite — MTProto-прокси для Telegram (Android)" src="docs/banner.jpg" width="900">
</p>

<p align="center">
  <strong>ProxyPulse Lite</strong><br>
  Первая страница TGStat · проверка · подключение в Telegram одним тапом
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

## Возможности (Lite)

- загружает прокси с **первой страницы TGStat** (@ProxyMTProto) — без входа и пагинации;
- проверяет **доступность** и измеряет ping;
- показывает **реакции ★** с поста TGStat (лайки пользователей);
- сортировка: **по пингу** или **по реакциям**;
- открывает выбранный прокси в **Telegram** одним тапом;
- минимум настроек (только тема).

---

## Быстрый старт

1. Установите APK из [релизов](https://github.com/VorozhbitDM/ProxyPulse-MTProto-android/releases/latest) или соберите сами (см. ниже).
2. Запустите **ProxyPulse Lite** и нажмите **«Начать поиск»**.
3. Тап по прокси в списке — Telegram предложит подключение.
4. При необходимости переключите сортировку: «По рейтингу» / «По пингу» (по умолчанию — по рейтингу).

### Требования

| | |
|---|---|
| ОС | Android **8.0+** |
| Telegram | Установленное приложение Telegram |
| Сеть | Доступ в интернет (TGStat + проверка прокси) |

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
.\gradlew.bat assembleDebug
```

**Linux / macOS:**

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Release-сборка:

```powershell
.\gradlew.bat assembleRelease
```

Файл: `app\build\outputs\apk\release\app-release.apk`.

Для GitHub Releases: `ProxyPulse-v3.0-lite-android.apk` (версию возьмите из `versionName` в `app/build.gradle.kts`).

Без `keystore.properties` release подписывается debug-ключом — для своего телефона это нормально. Для GitHub/Play создайте keystore — см. [docs/signing.md](docs/signing.md).

Установка: `adb install -r app\build\outputs\apk\release\app-release.apk`

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
