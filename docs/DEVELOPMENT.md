# 코드 수정과 빌드

[README로 돌아가기](../README.md)

앱의 화면과 기능은 Kotlin으로 작성되어 있습니다. Android Studio용 Gradle 프로젝트와 Windows용 PowerShell 빌드 스크립트를 제공합니다.

## 코드 위치

주요 소스는 `app/src/main/java/kr/local/galaxybattery/`에 있습니다. Android 프로젝트에서는 `java` 폴더에 Kotlin 소스를 넣어도 됩니다.

| 파일 | 역할 |
|---|---|
| [HistoryStore.kt](../app/src/main/java/kr/local/galaxybattery/HistoryStore.kt) | 조회 결과의 기기 내 영구 저장, 날짜순 읽기, 개별·전체 삭제 |
| [MainActivity.kt](../app/src/main/java/kr/local/galaxybattery/MainActivity.kt) | 화면, 문구, 색상, 버튼, 파일 선택, 2초 간격 조회, 결과 표시 |
| [ShizukuReader.kt](../app/src/main/java/kr/local/galaxybattery/ShizukuReader.kt) | Shizuku 연결, 권한 요청, 서비스 연결, 시간 제한 및 정리 |
| [RemoteBatteryService.kt](../app/src/main/java/kr/local/galaxybattery/RemoteBatteryService.kt) | Shizuku 권한으로 고정된 배터리 조회 명령 실행 |
| [DumpParser.kt](../app/src/main/java/kr/local/galaxybattery/DumpParser.kt) | 로그·압축 파일 읽기, ASOC/BSOH/사용량 추출, 충돌값 처리 |
| [BatteryValues.kt](../app/src/main/java/kr/local/galaxybattery/BatteryValues.kt) | 기본 배터리 값 검증과 단위 변환 |
| [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) | 앱 이름, 액티비티, Shizuku Provider, 권한 |
| [styles.xml](../app/src/main/res/values/styles.xml) | 기본 테마와 시스템 바 색상 |

화면은 `MainActivity.kt`에서 Android View로 구성합니다. Jetpack Compose나 별도 레이아웃 XML은 사용하지 않습니다.

`IRemoteBattery.java`는 [AIDL 정의](../app/src/main/aidl/kr/local/galaxybattery/IRemoteBattery.aidl)에서 생성한 Binder 통신 코드입니다. Windows 한글 경로의 AIDL 생성기 문제를 피하기 위해 생성 결과를 포함했고, Gradle의 AIDL 자동 생성은 꺼 두었습니다. 통신 인터페이스를 바꿀 때는 정의와 생성 코드를 함께 갱신해야 합니다. 일반적인 화면·조회·파서 수정에는 이 파일을 바꿀 필요가 없습니다.

## Android Studio에서 빌드

1. 저장소를 clone하거나 **Code → Download ZIP**으로 내려받아 압축을 풉니다.
2. Android Studio의 **Open**에서 `settings.gradle.kts`가 있는 최상위 폴더를 선택합니다.
3. Gradle JDK를 **JDK 17**로 설정하고, Android SDK Platform 35와 Build Tools 35.0.0을 준비합니다.
4. Gradle 동기화를 마친 뒤 `:app:assembleDebug`를 실행합니다.

터미널에서도 실행할 수 있습니다.

```powershell
# Windows: JDK 17 및 Android SDK가 설정된 환경
.\gradlew.bat :app:assembleDebug
```

출력은 `app/build/outputs/apk/debug/app-debug.apk`입니다. SDK 위치는 Android Studio가 생성하는 `local.properties` 또는 로컬 SDK 설정을 사용합니다. `local.properties`는 저장소에 포함하지 않습니다.

### 빌드 환경

| 구성 요소 | 버전 |
|---|---|
| Kotlin | 2.1.21 |
| Android Gradle Plugin | 8.9.2 |
| Gradle Wrapper | 8.11.1 |
| JDK | 17 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 |
| Shizuku API | 13.1.5 |

## Windows에서 도구 준비부터 빌드까지

**새로 내려받은 저장소에는 개발 도구가 포함되어 있지 않습니다.** 프로젝트 폴더에서 먼저 다음 명령을 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-tools.ps1
```

이 스크립트는 JDK, Kotlin 컴파일러, Android SDK 도구와 D8을 프로젝트의 `.tools` 폴더에 내려받습니다. 시스템 PATH는 변경하지 않습니다. 최초 준비에는 인터넷 연결과 다운로드 시간이 필요합니다.

도구가 준비되면 다음 명령으로 빌드합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

이후 소스만 수정했다면 `build.ps1`만 다시 실행하면 됩니다.

- APK: `dist/galaxy-battery-0.3.3.apk`
- 체크섬: `dist/SHA256SUMS.txt`
- 로컬 서명키: `.tools/diagnostic.keystore`

PowerShell 빌드는 Kotlin 컴파일러와 D8 8.9.35를 직접 사용합니다. Gradle 빌드와 출력 위치가 다릅니다.

## VS Code에서 수정

프로젝트의 **배터리 상태.code-workspace**를 VS Code로 열거나 **파일 → 폴더 열기**에서 프로젝트 폴더를 선택합니다. 도구 준비를 마친 후 **Ctrl+Shift+B**를 누르면 PowerShell 빌드가 실행됩니다.

VS Code에서는 파일 편집과 빌드 작업을 사용할 수 있습니다. Android 전용 코드 탐색·디버깅이 필요하면 Android Studio로 프로젝트를 여세요.

## 서명키와 업데이트

GitHub에 올라온 APK는 프로젝트 관리자의 로컬 키로 서명되어 있습니다. **이 키는 저장소에 포함되지 않습니다.** 새 환경에서 직접 빌드하면 다른 키로 서명되므로, 공개 APK 위에 그대로 덮어 설치할 수 없습니다.

자체 빌드를 처음 설치할 때는 기존 APK를 제거하거나 개발용 applicationId를 별도로 사용하세요. 이후 자체 빌드를 계속 업데이트하려면 생성한 키를 보관해야 합니다. `.tools/diagnostic.keystore`가 있으면 Gradle의 debug 설정도 같은 키를 사용합니다. 키가 없으면 Gradle의 기본 debug 서명을 사용합니다.

## 값 처리와 검증

PowerShell 빌드는 다음 검증을 실행합니다.

- [BatteryValuesTest.java](../tests/BatteryValuesTest.java): 기본 값 처리 21개
- [DumpParserTest.java](../tests/DumpParserTest.java): 로그 분석 35개
- [HistoryStoreTest.java](../tests/HistoryStoreTest.java): 재실행 후 유지, 정렬, 저장 실패, 개별·전체 삭제 등 11개
- APK 서명·정렬·매니페스트 확인

테스트는 별도 JVM 실행기이며 Gradle의 `test` 작업에 연결되어 있지 않습니다. Android 화면 동작, Shizuku 권한 창, 실제 삼성 펌웨어의 응답은 실기기에서 별도로 확인해야 합니다.

로그 분석은 최대 90초, 압축 해제 후 512 MiB까지 읽으며, 긴 한 줄은 64 KiB를 넘으면 제외합니다. ZIP 내부 파일은 최대 2,048개로 제한하고 중첩 압축을 재귀 분석하지 않습니다. 원본 로그 전체를 보관하지 않고 지정된 배터리 필드만 추출합니다.

수치 해석을 바꿀 때는 ASOC/BSOH와 잔량·상태 코드의 차이를 유지하고, 누락·미지원·충돌값이 정상 수치로 표시되지 않는지 확인하세요.

## 버전 배포 시 갱신할 곳

현재 버전 정보는 `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `build.ps1`과 안내 문서에 들어 있습니다. 버전을 올릴 때는 versionCode도 올리고, APK 파일명·README 다운로드 링크·Shizuku 가이드·체크섬을 함께 갱신합니다.

외부 라이브러리 출처와 라이선스는 [THIRD_PARTY.md](../THIRD_PARTY.md)에 정리되어 있습니다.
