# 갤럭시 배터리 수명 확인

Galaxy S25에서 배터리 성능과 충전 사이클을 확인하기 위한 **Kotlin Android 앱**입니다. 현재 버전은 **0.3.2**입니다.

### [⬇ 앱 설치 파일 다운로드 · v0.3.2](https://raw.githubusercontent.com/Lumi-01/galaxy-battery-health-check/main/dist/galaxy-battery-0.3.2.apk)

휴대폰에서 링크를 눌러 APK를 다운로드한 뒤 실행하면 설치할 수 있습니다.

잔량·온도·전압·전류 등 기본 정보는 화면을 보고 있는 동안 2초마다 다시 조회합니다. 화면을 벗어나면 자동 갱신을 멈춥니다. 실제 센서 값의 갱신 주기는 기기에서 제공하는 주기를 따르며, Shizuku의 성능·사이클과 불러온 로그는 별도 조회 결과입니다.

## APK 설치

1. 휴대폰에서 **[앱 설치 파일 다운로드](https://raw.githubusercontent.com/Lumi-01/galaxy-battery-health-check/main/dist/galaxy-battery-0.3.2.apk)**를 누릅니다.
2. 다운로드가 끝나면 알림 또는 **내 파일 → 다운로드**에서 APK를 엽니다.
3. 설치를 진행합니다. 설치 권한 안내가 나오면 파일을 연 앱의 설치 권한을 허용합니다.

기존 버전을 사용 중이라면 앱을 삭제하지 않고 업데이트할 수 있습니다. 설치 후에는 **[Shizuku 연결 가이드](docs/SHIZUKU_SETUP_KO.md)**를 따라 설정하세요.

## Shizuku로 조회

처음 사용한다면 **[Shizuku 설치·설정 가이드](docs/SHIZUKU_SETUP_KO.md)**를 따라 진행하세요.

1. [Shizuku 공식 사이트](https://shizuku.rikka.app/download/)에서 Shizuku를 설치합니다.
2. 휴대폰 개발자 옵션에서 무선 디버깅을 켜고, Shizuku 안내에 따라 페어링한 뒤 시작합니다.
3. 이 앱의 **배터리 상태 확인**을 누릅니다.
4. 처음 표시되는 Shizuku 권한 요청을 허용합니다.
5. 다음부터는 **배터리 상태 확인**을 누르면 됩니다. 재부팅 후 Shizuku를 다시 시작해야 할 수 있습니다.

앱은 Shizuku 권한으로 `dumpsys -t 12 battery`를 읽습니다. 배터리 관련 필드만 추출하며 시스템 설정 변경·배터리 통계 초기화·루팅은 하지 않습니다. S25 One UI 9.0 베타에서 해당 필드가 실제 제공되는지는 기기에서 확인해야 합니다. Shizuku가 연결되어도 펌웨어가 정보를 공개하지 않으면 파일 분석을 이용해야 합니다.

## SysDump 파일 분석

1. 삼성 전화에서 `*#9900#`을 입력합니다.
2. **Run dumpstate/logcat**을 실행합니다.
3. 완료되면 **Copy to sdcard** 또는 이에 해당하는 복사 메뉴를 실행합니다.
4. 앱의 **로그 불러오기 → 파일 선택**을 누릅니다.
5. 내부 저장소의 `log` 폴더에서 새 `dumpstate*.log` 등의 파일을 선택합니다.

메뉴와 경로는 펌웨어마다 다릅니다. 선택기에서 보이지 않으면 **내 파일**에서 `Download` 폴더로 복사하세요. **덤프 생성은 사용자가 직접 하고, 검색과 환산은 앱이 자동 처리**합니다.

TXT/LOG, ZIP 안의 텍스트 로그, GZIP을 지원합니다. ZIP에 과거와 현재의 서로 다른 값이 있으면 임의로 하나를 고르지 않습니다. 압축을 풀어 원하는 최신 로그 한 개를 선택하세요. 중첩 ZIP/GZIP은 재귀 분석하지 않습니다. 압축 해제 후 512 MiB, 최대 90초로 제한합니다.

## 값의 의미

| 항목 | 처리 방식 |
|---|---|
| ASOC | `mSavedBatteryAsoc`의 1~100 값을 성능 추정치로 표시. 독립적인 용량 실측값이 아닙니다. |
| 추정 사이클 | `mSavedBatteryUsage / 100`. 삼성의 공개 API 계약이 보장하는 공식 수치가 아닌 로그 해석 기반 추정치입니다. |
| BSOH | `mSavedBatteryBsoh`. ASOC와 섞지 않고 **측정 근거**에 별도 표시합니다. |
| 공식 API | 기본 사이클과 플랫폼 속성 ID 10의 SOH도 조회. 상세 조회 후에는 출처가 표시된 상세 결과를 우선 표시합니다. |
| 기본 정보 | 잔량·온도·전압·순간 전류·남은 전하량·상태. 남은 전하량은 완충 용량이 아닙니다. |

누락·미지원·범위 밖·충돌값을 건강한 배터리처럼 표시하지 않습니다. 사이클 0도 실제 0회인지 미지원인지 확정하지 않습니다. 파일 결과는 생성 당시 기록이며 화면의 읽은 시각은 로그 생성 시각이 아닙니다. 배터리 교체·펌웨어 변경 후에는 실제 이력과 일치하는지 확인이 필요합니다.

## 코드 열기

### Visual Studio Code

1. **배터리 상태.code-workspace**를 VS Code로 열거나 **파일 → 폴더 열기**에서 이 프로젝트를 선택합니다.
2. 탐색기에서 `app/src/main/java/kr/local/galaxybattery`를 펼칩니다.
3. 수정 후 **Ctrl+Shift+B**를 누르면 APK 빌드 작업이 실행됩니다.

| 파일 | 내용 |
|---|---|
| MainActivity.kt | 제목·문구·화면·버튼·파일 선택·결과 표시 |
| ShizukuReader.kt | 연결·권한·서비스 연결·시간 제한 |
| RemoteBatteryService.kt | Shizuku 권한으로 실행하는 고정 배터리 조회 |
| DumpParser.kt | 필드 추출·압축 파일 읽기·모호한 값 처리 |
| BatteryValues.kt | 기본 값 검사·단위 변환 |

직접 작성한 앱 소스는 모두 Kotlin입니다. `java` 폴더에는 Kotlin 소스를 넣어도 됩니다. AIDL에서 생성한 Binder 연결 코드와 검증용 테스트 실행기는 Java입니다. Windows 한글 경로의 AIDL 생성기 문제를 피하기 위해 생성된 `IRemoteBattery.java`를 소스에 포함했습니다. 일반적인 UI·조회·파서 수정에는 이 파일을 수정할 필요가 없습니다.

### Android Studio

**Open**에서 최상위 폴더를 선택합니다. Kotlin 2.1.21 / AGP 8.9.2 / Gradle 8.11.1 / JDK 17 / SDK 35 프로젝트입니다. Gradle의 `:app:assembleDebug` 빌드도 이 작업 폴더에서 성공했습니다. IDE가 SDK 위치를 물으면 설치된 Android SDK를 선택하세요. 다른 Windows 환경에서 한글 경로 문제가 생기면 영문 경로의 복사본이나 PowerShell 빌드를 이용하세요.

### PowerShell

이 폴더에는 도구가 `.tools`에 준비되어 있습니다. 시스템 PATH는 변경하지 않았습니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

새 PC에서는 먼저 `setup-tools.ps1`을 실행합니다. Kotlin 컴파일러, Kotlin 2.1 호환 D8 8.9.35, Android 빌드 도구로 APK를 만듭니다. 빌드 시 이전 클래스와 생성 코드를 정리하므로 Java 소스의 잔여물이 섞이지 않습니다.

APK는 `dist/galaxy-battery-0.3.2.apk`, SHA-256은 `dist/SHA256SUMS.txt`입니다. 기존 앱 업데이트에 쓰이는 `.tools/diagnostic.keystore`를 보관하세요. 공개 배포용 서명은 별도로 관리해야 합니다.

## 개인정보와 검증

인터넷 권한, 광고·분석 SDK, 자동 전송 기능이 없습니다. 결과와 선택 파일은 기기 안에서 분석합니다. 전체 파일, 일련번호, IMEI, 계정, 무관한 로그 행은 결과에 포함하지 않습니다. **측정 근거 → 공유/복사**에는 배터리 필드·모델·OS 빌드·조회 시각·연결 진단만 담습니다. 파일의 지속 접근 권한은 보관하지 않습니다.

PowerShell 빌드에서 값 검증 21개와 파서 검증 35개를 실행합니다. 일반/배열, 중복·충돌, 미지원·잘못된 숫자, UTF-8/UTF-16, ZIP/GZIP, 큰 줄, 취소, 손상 압축, 개인정보 제외를 확인합니다. APK 서명과 정렬도 검사합니다. 실제 S25 베타의 Shizuku 연결과 값 조회는 실기기 확인이 필요합니다.

## 출처

- [Shizuku API](https://github.com/RikkaApps/Shizuku-API)
- [Android 파일 선택기](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)
- [MyBattery 개발자의 로그 필드 설명](https://github.com/Alyaqdhans/MyBattery)
- [사용자가 제공한 SysDump 안내](https://www.reddit.com/r/samsunggalaxy/comments/1qvv3y1/guide_how_to_check_your_true_samsung_battery/)

SDK 라이선스는 APK assets에 포함됩니다. 포럼의 충전 보정이나 교체 기준은 앱의 판단 근거로 사용하지 않습니다.
