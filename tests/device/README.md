# 기기 회귀 검사

`RegressionChecks.java`는 배포 APK에 넣지 않는 별도 instrumentation입니다. 에뮬레이터나 테스트 기기에서만 실행하세요. 테스트용 기록을 만들고 화면 상태를 관측합니다.

1. 저장소 루트에서 `./build.ps1`로 앱과 순수 테스트를 빌드합니다.
2. `powershell -NoProfile -ExecutionPolicy Bypass -File tests/device/build.ps1`를 실행합니다. `setup-tools.ps1`로 준비한 JDK·SDK와 동일한 개발 서명키를 사용합니다.
3. `adb -s <기기 ID> install -r dist/galaxy-battery-0.5.3.apk`와 `adb -s <기기 ID> install -r -t build/device-tests/visual-checks.apk`로 설치합니다.
4. `adb -s <기기 ID> shell am instrument -w -e mode switch kr.local.galaxybattery.visualtests/kr.local.galaxybattery.visualtests.RegressionChecks`를 실행합니다. 실제 화면 픽셀의 OFF → ON → OFF 변화, 행·스위치 클릭, 코어 표시와 저장 설정의 일치, Binder 열 조회를 확인합니다.

`mode screen`은 알림 권한을 허용한 테스트 기기에서 실행합니다. 전력 측정을 60초 간격으로 시작하고 `ready=screen`을 기다립니다. 이때 기기에서 약 2초 동안 화면을 껐다 켠 뒤 `adb shell am broadcast -a kr.local.galaxybattery.REGRESSION_DONE -p kr.local.galaxybattery`를 보냅니다. 60초 샘플 사이의 화면 꺼짐이 별도 이벤트로 저장됐는지 확인하고 측정을 종료합니다. 테스트가 끝나면 기록 간격을 5초로 돌립니다.

`mode history`는 화면 검증용 충전·방전 샘플과 화면 꺼짐 이벤트를 생성해 기록 상세를 엽니다. 10초 뒤 자신이 만든 예시 세션을 삭제합니다. 예시 값은 기기 실측값이 아닙니다.

`mode thermal`은 CPU·GPU 갱신 간격을 60초로 둔 상태에서 커널 제한 신호와 OS 폴링 시각이 10초 후 바뀌는지 확인합니다. 열 조회가 CPU·GPU 그래프에 추가 샘플을 만들지 않는지도 확인하고, 하드웨어 갱신 간격을 2초로 복원합니다.

OS 열 이벤트는 Android 에뮬레이터의 `adb shell cmd thermalservice override-status 3`과 `override-status 0`으로 비교하고, 검증 후 `adb shell cmd thermalservice reset`으로 복원합니다. 이 검사는 실제 삼성 펌웨어의 온도별 쓰로틀링 임계값을 검증하지 않습니다.
