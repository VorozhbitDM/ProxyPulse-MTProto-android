# Подпись release APK / AAB

Секреты keystore **не хранятся** в репозитории.

## Локальная подпись

1. Создайте keystore (один раз):

```bash
keytool -genkey -v -keystore proxypulse-release.keystore -alias proxypulse -keyalg RSA -keysize 2048 -validity 10000
```

2. Создайте `android/keystore.properties` (добавьте в `.gitignore`):

```properties
storeFile=../proxypulse-release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=proxypulse
keyPassword=YOUR_KEY_PASSWORD
```

3. В `app/build.gradle.kts` подключите signing config (по необходимости) и соберите:

```bash
./gradlew assembleRelease
```

Для публикации в Google Play используйте `bundleRelease` (AAB).

## CI

В GitHub Actions задайте secrets: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
