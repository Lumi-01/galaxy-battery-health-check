# Bundled dependencies

- Shizuku API 13.1.5 (api, provider, aidl, shared): MIT, RikkaApps.
  - https://github.com/RikkaApps/Shizuku-API
  - Official AARs from https://repo.maven.apache.org/maven2/dev/rikka/shizuku/
  - AAR SHA-256 checked against Maven Central; classes.jar kept in app/libs.
  - License: app/src/main/assets/Shizuku-LICENSE.txt
- AndroidX Annotation 1.3.0: Apache-2.0, Android Open Source Project.
  - Official JAR from Google Maven.
  - License: app/src/main/assets/AndroidX-LICENSE.txt
- Kotlin stdlib 2.1.21: Apache-2.0, JetBrains.
  - https://github.com/JetBrains/kotlin/releases/tag/v2.1.21
  - Manual build uses official compiler distribution; Gradle uses Maven Central.
  - License and notice: app/src/main/assets/Kotlin-LICENSE.txt, Kotlin-NOTICE.txt

app/libs/SHA256SUMS.txt records bundled JAR hashes. Gradle wrapper and distribution checksums are verified against official Gradle checksums. SDK/JDK/compiler tools stay in .tools and are not packaged in the APK.

The Samsung field parser and UI were written independently. Shizuku library code is distributed with its MIT notice.
