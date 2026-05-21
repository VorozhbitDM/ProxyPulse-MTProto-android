<p align="center">
  <img alt="ProxyPulse — MTProto-прокси для Telegram (Android)" src="docs/banner.jpg" width="900">
</p>

<p align="center">
  <strong>Поиск MTProto-прокси и проверка доступности</strong><br>
  Подключение в Telegram одним тапом · Android · без VPN и API-ключей
</p>

<p align="center">
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-android/releases/latest"><img src="https://img.shields.io/github/v/release/VorozhbitDM/ProxyPulse-MTProto-android?style=flat-square&label=Release&color=brightgreen" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="MIT"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square" alt="Platform">
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-android/releases"><img src="https://img.shields.io/github/downloads/VorozhbitDM/ProxyPulse-MTProto-android/total?label=Downloads&logo=github&style=flat-square&cacheSeconds=600" alt="Downloads"></a>
  <br>
  <a href="https://github.com/VorozhbitDM/ProxyPulse-MTProto-for-TG"><img src="https://img.shields.io/badge/Версия%20для-Windows%2010%2F11-0078D6?style=flat-square" alt="Windows"></a>
</p>

---

## Версия для Windows

Нужен **ПК**? Та же логика поиска и проверки — в десктопном приложении:

**[ProxyPulse-MTProto-for-TG](https://github.com/VorozhbitDM/ProxyPulse-MTProto-for-TG)** · скачать [ProxyPulse.exe](https://github.com/VorozhbitDM/ProxyPulse-MTProto-for-TG/releases/latest)

---

## Возможности

Telegram иногда недоступен напрямую. В каналах и архивах публикуют MTProto-прокси, но вручную искать, проверять задержку и копировать `secret` неудобно.

**ProxyPulse** делает это за вас:

- собирает прокси из общедоступных источников (лимит настраивается, по умолчанию 100);
- проверяет **доступность** (TCP, с таймаутом);
- сортирует по задержке;
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
| **Тема** | Тёмная | Переключатель «Тёмная» / «Светлая». |

Настройки сохраняются на устройстве (DataStore) и подхватываются при следующем запуске.

На экране поиска: **«Сбор… N из 100»** — число справа берётся из настройки. Проверяются все собранные прокси; **«Найдено M»** — сколько из них оказались доступными.

### Требования

| | |
|---|---|
| ОС | Android **8.0+** (API 26) |
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
.\gradlew.bat assembleDebug
```

**Linux / macOS:**

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Установка на устройство: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

### Иконка приложения

```powershell
.\scripts\GenerateLauncherIcons.ps1
```

Генерирует `mipmap-*` из `Desktop/src/ProxyPulse/app.png` (в монорепозитории) или укажите свой PNG в скрипте.

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
