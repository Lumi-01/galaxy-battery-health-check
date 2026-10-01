# 코드 수정과 빌드

[README로 돌아가기](../README.md)

[화면·블러·하드웨어 표시·방전 기록 변경 및 검증 범위](UI_MONITORING_UPDATE.md)

앱의 화면과 기능은 Kotlin으로 작성되어 있습니다. Android Studio용 Gradle 프로젝트와 Windows용 PowerShell 빌드 스크립트를 제공합니다.

## 코드 위치

주요 소스는 `app/src/main/java/kr/local/galaxybattery/`에 있습니다. Android 프로젝트에서는 `java` 폴더에 Kotlin 소스를 넣어도 됩니다.

| 파일 | 역할 |
|---|---|
| [AppUi.kt](../app/src/main/java/kr/local/galaxybattery/AppUi.kt) | 카드·메뉴 공통 여백, 버튼 높이·배경, 팝업 폭 |
| [DashboardScaffold.kt](../app/src/main/java/kr/local/galaxybattery/DashboardScaffold.kt) | 3개 페이지, 하단 메뉴, 시스템 바 여백, 페이지 전환 |
| [FrostedNavigation.kt](../app/src/main/java/kr/local/galaxybattery/FrostedNavigation.kt) | 둥근 메뉴, 선택 표시, 배경만 캡처해 블러 적용 |
| [FrostedSettingsButton.kt](../app/src/main/java/kr/local/galaxybattery/FrostedSettingsButton.kt) | 페이지 위에 떠 있는 작은 설정 버튼과 배경 블러 |
| [AppSettings.kt](../app/src/main/java/kr/local/galaxybattery/AppSettings.kt) | 테마·간격·블러·방전 표시 설정 저장 |
| [AppPalette.kt](../app/src/main/java/kr/local/galaxybattery/AppPalette.kt) | 라이트·다크 색상 |
| [BatteryLevelView.kt](../app/src/main/java/kr/local/galaxybattery/BatteryLevelView.kt) | 둥근 잔량 막대, 민트 그라데이션, 낮은 잔량 색상과 접근성 |
| [RefreshPolicy.kt](../app/src/main/java/kr/local/galaxybattery/RefreshPolicy.kt) | 간격 검증, 시스템 테마, 그래프 연결 허용 시간 |
| [ZeroPowerTracker.kt](../app/src/main/java/kr/local/galaxybattery/ZeroPowerTracker.kt) | 0W 이후 회복 판정, 구간·횟수, 중단 후 복원 |
| [ThermalStatus.kt](../app/src/main/java/kr/local/galaxybattery/ThermalStatus.kt) | Android 열 제한 0~6단계 이름 |
| [ThermalStatusView.kt](../app/src/main/java/kr/local/galaxybattery/ThermalStatusView.kt) | 열 제한 상태와 단계 배지 표시 |
| [ChargeMonitorService.kt](../app/src/main/java/kr/local/galaxybattery/ChargeMonitorService.kt) | 설정 간격으로 화면 꺼짐 측정, 전면 서비스, Live Update 현재 W 칩, CPU 깨우기 잠금 정리 |
| [ChargePower.kt](../app/src/main/java/kr/local/galaxybattery/ChargePower.kt) | µA·mV에서 W 계산, 미지원 값 검증, 충전 최고·최저 집계 |
| [PowerSampler.kt](../app/src/main/java/kr/local/galaxybattery/PowerSampler.kt) | Android 전류·전압·잔량·온도·기기 열 제한 단계 읽기 |
| [PowerLogStore.kt](../app/src/main/java/kr/local/galaxybattery/PowerLogStore.kt) | 측정별 전력 기록 영구 저장, 중단된 마지막 쓰기 복구, 열람·삭제 |
| [ScreenTimeline.kt](../app/src/main/java/kr/local/galaxybattery/ScreenTimeline.kt) | 화면 이벤트를 그래프 구간으로 변환, 확인 불가 공백 처리 |
| [PowerGraphView.kt](../app/src/main/java/kr/local/galaxybattery/PowerGraphView.kt) | Canvas 전력 그래프와 터치로 시점 선택 |
| [UsageGraphView.kt](../app/src/main/java/kr/local/galaxybattery/UsageGraphView.kt) | CPU·GPU 전체 및 CPU 개별 코어의 단일선 그래프, 누락 구간 처리 |
| [HardwareMonitorView.kt](../app/src/main/java/kr/local/galaxybattery/HardwareMonitorView.kt) | 전체·코어별 그래프 카드, 하단 수치, 센서별 온도 행 |
| [OneUiToggle.kt](../app/src/main/java/kr/local/galaxybattery/OneUiToggle.kt) | 파란 캡슐 스위치와 전체 행 터치, 스위치 접근성 |
| [OptionPicker.kt](../app/src/main/java/kr/local/galaxybattery/OptionPicker.kt) | 테마·간격 선택용 둥근 팝업 |
| [LiquidGlassEdge.kt](../app/src/main/java/kr/local/galaxybattery/LiquidGlassEdge.kt) | 메뉴·설정 버튼 위의 반사 테두리 |
| [ThermalMonitor.kt](../app/src/main/java/kr/local/galaxybattery/ThermalMonitor.kt) | OS 열 상태 이벤트, 10초 열 부하 조회, 화면 종료 후 정리 |
| [HardwareTelemetry.kt](../app/src/main/java/kr/local/galaxybattery/HardwareTelemetry.kt) | CPU 카운터 차이, 그래프용 샘플, 온도 센서 선택·코어 매핑 |
| [HardwareReader.kt](../app/src/main/java/kr/local/galaxybattery/HardwareReader.kt) | 화면 조회, Shizuku 또는 직접 읽기, 시간 제한과 서비스 정리 |
| [HardwareProbe.kt](../app/src/main/java/kr/local/galaxybattery/HardwareProbe.kt) | 고정된 proc/sysfs 경로의 CPU·GPU·온도 읽기 |
| [HistoryStore.kt](../app/src/main/java/kr/local/galaxybattery/HistoryStore.kt) | 조회 결과의 기기 내 영구 저장, 날짜순 읽기, 개별·전체 삭제 |
| [MainActivity.kt](../app/src/main/java/kr/local/galaxybattery/MainActivity.kt) | 카드 내용, 기록 목록, 버튼 동작, 파일 선택, 조회 결과 표시 |
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
3. Gradle JDK를 **JDK 17**로 설정하고, Android SDK Platform 36과 Build Tools 35.0.0을 준비합니다.
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
| compileSdk / targetSdk | 36 / 35 |
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

- APK: `dist/galaxy-battery-0.5.4.apk`
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

토글의 실제 화면 색상과 화면 꺼짐 이벤트 저장은 [기기 회귀 검사](../tests/device/README.md)로 별도 확인합니다. 이 검증용 APK는 배포 앱에 포함하지 않습니다.

PowerShell 빌드는 다음 검증을 실행합니다.

- [BatteryValuesTest.java](../tests/BatteryValuesTest.java): 기본 값 처리 21개
- [DumpParserTest.java](../tests/DumpParserTest.java): 로그 분석 35개
- [HistoryStoreTest.java](../tests/HistoryStoreTest.java): 재실행 후 유지, 정렬, 저장 실패, 개별·전체 삭제 등 11개
- [PowerLogTest.java](../tests/PowerLogTest.java): W 계산, 평균 복원, 화면 이벤트 구간·저장·부분 쓰기 복구·삭제, 이전 기록 호환 등 50개
- [MonitorPolicyTest.java](../tests/MonitorPolicyTest.java): 0W 구간·복원·제외 조건, 이전 파일 호환, 열 단계와 열 부하 해석, 방전 칩, 설정·그래프 간격 등 29개
- [HardwareTelemetryTest.java](../tests/HardwareTelemetryTest.java): CPU 전체·코어별 델타, 클럭·센서 식별·제한 신호, 잘못된 값, 방전 기록 전체 집계 등 40개
- APK 서명·정렬·매니페스트 확인

테스트는 별도 JVM 실행기이며 Gradle의 `test` 작업에 연결되어 있지 않습니다. Android 화면 동작, Shizuku 권한 창, 실제 삼성 펌웨어의 응답은 실기기에서 별도로 확인해야 합니다.

v0.5.2은 Android 15 에뮬레이터에서도 3개 페이지, 라이트·다크 전환, 갱신 간격 저장, Shizuku 미연결 안내, 시스템 열 상태 2단계 표시, 화면 꺼짐 기록과 종료 후 wake lock 해제를 확인했습니다. 테스트용 기록으로 0W 회복 시간대·횟수 표시, 진단 요약과 근거 펼치기, 개별 삭제를 확인했습니다. 에뮬레이터 값과 예시 기록은 S25 실측 결과가 아니며, Android 16/One UI 상단바 칩은 이 에뮬레이터 검증에 포함되지 않습니다.

로그 분석은 최대 90초, 압축 해제 후 512 MiB까지 읽으며, 긴 한 줄은 64 KiB를 넘으면 제외합니다. ZIP 내부 파일은 최대 2,048개로 제한하고 중첩 압축을 재귀 분석하지 않습니다. 원본 로그 전체를 보관하지 않고 지정된 배터리 필드만 추출합니다.

수치 해석을 바꿀 때는 ASOC/BSOH와 잔량·상태 코드의 차이를 유지하고, 누락·미지원·충돌값이 정상 수치로 표시되지 않는지 확인하세요.

## 전력 측정과 Live Update 구현

API 36의 `setShortCriticalText()`에 현재 W만 전달하고, `android.requestPromotedOngoing` extras로 승격을 요청합니다. 이 키는 AndroidX의 `setRequestPromotedOngoing()`과 같은 키이며, framework setter는 36.1 API라 직접 호출하지 않습니다. `POST_PROMOTED_NOTIFICATIONS`를 선언하고, 표준 스타일·ongoing·LOW 채널을 사용합니다. UI의 상단바 설정에서 허용 여부와 실제 `FLAG_PROMOTED_ONGOING` 상태를 확인합니다. 승격 여부는 OEM과 시스템 설정이 결정합니다.

사용자가 시작한 측정은 `specialUse` foreground service에서 실행합니다. 부분 wake lock은 화면을 켜지 않고 CPU만 유지하며, timeout과 갱신, 종료 시 해제를 적용했습니다. 측정에는 Shizuku가 필요하지 않습니다. 알림 종료 작업과 dismiss intent는 측정을 종료합니다. 화면이 꺼져도 설정한 간격(기본 5초)으로 36바이트의 측정 샘플을 앱 내부에 추가 저장하며, 모델 원본 덤프나 외부 저장소를 사용하지 않습니다.

시스템 재시작으로 서비스가 복구되면 이전 세션을 다시 읽어 누적 최고·최저를 유지합니다. 강제 종료와 재부팅 이후에는 자동으로 시작하지 않습니다. 마지막 쓰기가 중단되면 완전한 샘플까지만 읽고 다음 추가 저장 시 불완전한 꼬리를 잘라냅니다. 현재 측정 중인 세션 삭제는 거부하며, 최저 값에는 충전 중 유효한 0W를 포함합니다. 그래프는 최근 최대 600개만 메모리에 유지하지만 최고·최저는 전체 샘플 기준입니다.

새 `.power` 파일의 magic은 `0x47504232`입니다. 헤더는 20바이트, 샘플은 36바이트이며 기존 32바이트 형식 `0x47504231`도 읽기·추가 쓰기를 지원합니다. 예전 형식에는 열 제한 단계가 없어 `-1`(미기록)로 읽습니다. 0W 구간은 파일 전체 샘플을 재생해 계산하며 별도 파일 없이 재실행 후에도 유지합니다. 최근 200개 구간과 전체 횟수를 관리하고, 진행 중인 구간 상태도 복원합니다.

v0.5.3은 같은 세션 ID의 `.screen` 파일에 화면 이벤트를 추가합니다. magic `0x47505331`의 4바이트 헤더와 12바이트(time: Long, state: Int) 레코드이며 상태는 -1(확인 불가), 0(꺼짐), 1(켜짐)입니다. 부분 쓰기는 다음 추가 시 잘라 복구하고, 화면 파일 손상은 전력 기록 열람을 막지 않습니다. 서비스 시작 시 현재 화면 상태를 저장하고 `ACTION_SCREEN_ON/OFF`를 별도로 기록합니다. 프로세스 복원 때 마지막 관측 이후를 확인 불가로 닫습니다. `ScreenTimeline`은 그래프 시간 범위로 구간을 잘라 그립니다. 충방전 평균은 파일 전체에서 산술평균을 계산하고, 서비스 복원 시 전체 유효 개수와 평균을 사용합니다.

기본 배터리 조회와 전력 조회는 별도의 Handler 타이머로 작동합니다. 서비스는 매 주기 설정을 읽고, 기록 간격 변경 시 다음 샘플을 다시 예약해 같은 세션을 유지합니다. 상세 조회는 자동 타이머에 연결하지 않습니다. `PowerSampler`에서 API 29 이상 시스템 열 상태를 읽고, UI·그래프 선택·기록에 전달합니다. 기기 전체 열 상태를 충전 제한 원인으로 단정하지 마세요.

CPU·GPU 조회도 별도 Handler 타이머를 사용합니다. `AppSettings.hardwareSeconds`의 기본값은 2초입니다. 모니터링 화면을 벗어나면 타이머와 연결을 정리하며, 복귀 시 CPU 차이 계산 기준을 다시 잡습니다. `HardwareTelemetry.Frame`의 nullable 사용률을 그래프에 전달하므로 미지원·첫 측정·잘못된 카운터는 0%로 변환되지 않습니다. CPU 전체는 `/proc/stat`의 집계 행을 우선하고, 없으면 모든 온라인 코어의 유효한 시간 차이를 가중 합산합니다. 최근 120개 샘플은 메모리에서만 유지합니다. 코어별 옵션은 기본 꺼짐이며 CPU·GPU·센서 설정을 각각 저장합니다. 현재 GPU 인터페이스는 전체 정보만 제공하여 코어별 옵션에서는 안내를 표시합니다.

CPU 클럭은 `cpuinfo_cur_freq`, 미지원 시 `scaling_cur_freq`를 읽어 kHz에서 MHz로 변환합니다. 후자는 정책의 요청 클럭일 수 있으므로 정확한 실시간 하드웨어 주파수라고 단정하지 않습니다. GPU KGSL/devfreq 클럭은 Hz에서 MHz로 변환합니다. 최대 지원 주파수를 현재 클럭으로 대체하지 않습니다. CPU·GPU 전체 온도 센서를 우선 선택하고, 없으면 해당 센서의 최고값과 이름을 표시합니다. 명시적인 코어 이름만 코어 온도로 매핑합니다. 같은 이름의 서로 다른 thermal zone도 ID를 유지하여 각각 표시합니다.

`ThermalMonitor`는 OS 상태 변경 리스너와 10초 주기의 열 부하 조회를 사용합니다. 앱을 재생성해도 호출 시각을 유지하여 API를 과도하게 조회하지 않으며, 종료 시 리스너와 타이머를 해제합니다. 전력 기록의 마지막 샘플로 실시간 OS 상태를 덮어쓰지 않습니다. 0단계는 OS 제한 보고 없음으로 표현합니다. 커널 제한 장치의 상태는 OS 단계와 구분하고, 열 부하나 클럭만으로 확정 단계를 만들지 않습니다. 기록 파일에는 기존의 OS 단계만 저장하여 이전 파일 형식을 유지합니다.

v0.5.3의 커널 제한 신호는 `HardwareProbe.readCooling()`과 별도의 10초 `HardwareReader(thermalOnly=true)`로 읽습니다. CPU·GPU 그래프 타이머와 독립적이며 `IRemoteBattery.readThermal()`은 고정된 읽기 경로만 제공합니다. Shizuku 서비스는 별도 tag/process와 version 6을 사용해 이전 Binder 프로세스가 새 호출을 받지 않게 합니다. `setChecked()`의 명시적 `invalidate()`는 상태별 Drawable이 없는 커스텀 토글의 필수 갱신 경로입니다.

테마는 Activity 생성 전에 적용하며 기본값은 시스템 설정입니다. 아이콘의 foreground는 launcher 안전 영역 안에 두고, 마스크 모양은 Android 런처에 맡깁니다. 하단 블러는 page host만 캡처하므로 메뉴 자체를 재귀 캡처하지 않으며, 라벨과 아이콘은 블러 위에 선명하게 그립니다.

실기기 검증 항목: 알림 권한 거부/허용, One UI Live Update 승격, 화면 꺼짐 상태에서 5초 기록, 측정 종료 후 wake lock 해제, 음수 전류 기기에서의 표시, 앱 재실행 후 기록 열람과 삭제.

## 버전 배포 시 갱신할 곳

현재 버전 정보는 `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `build.ps1`과 안내 문서에 들어 있습니다. 버전을 올릴 때는 versionCode도 올리고, APK 파일명·README 다운로드 링크·Shizuku 가이드·체크섬을 함께 갱신합니다.

외부 라이브러리 출처와 라이선스는 [THIRD_PARTY.md](../THIRD_PARTY.md)에 정리되어 있습니다.
