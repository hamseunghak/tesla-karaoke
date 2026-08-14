# Tesla Karaoke

테슬라 Bluetooth 오디오와 함께 사용하는 Android용 YouTube 노래방 앱입니다. 휴대폰이나 태블릿에서 실행하고 차량 Bluetooth로 소리를 출력하는 구성을 전제로 합니다.

## 주요 기능

- YouTube Data API v3를 이용한 노래방 영상 검색
- 가수 또는 노래 제목 입력 시 `금영 노래방` 우선 검색
- 임베드 재생을 차단한 TJ 관련 검색 결과 제외
- 앱 내부 YouTube 전체화면 재생
- 영상 화면에서 노래 볼륨, 재생 속도, ±10초 이동 제어
- 즐겨찾기와 최근 재생 목록을 기기에 저장
- 여러 곡 예약 및 현재 곡 종료 후 자동 연속 재생
- API 키를 앱 설정에 안전하게 저장
- 가로·세로 화면 지원
- 주행 중 조작을 막는 주차 상태 UI
- YouTube 앱으로 열기 대체 기능

## 개발 환경

- Android Studio
- JDK 17
- Android SDK 34
- 최소 Android 버전: Android 8.0 (API 26)

## 실행

1. 저장소를 Android Studio에서 엽니다.
2. Gradle 동기화가 끝나면 실행 구성을 `tesla-karaoke`로 선택합니다.
3. Android 기기에서 앱을 실행합니다.
4. 앱의 `⚙ 설정`에서 YouTube Data API 키를 입력합니다.

명령줄 빌드:

```bash
./gradlew :tesla-karaoke:assembleDebug
```

디버그 APK는 다음 경로에 생성됩니다.

```text
tesla-karaoke/build/outputs/apk/debug/tesla-karaoke-debug.apk
```

## YouTube API 키 발급

1. [Google Cloud Console의 YouTube Data API v3](https://console.cloud.google.com/apis/library/youtube.googleapis.com)를 엽니다.
2. 프로젝트에서 YouTube Data API v3를 활성화합니다.
3. 사용자 인증 정보에서 API 키를 생성합니다.
4. 앱의 `⚙ 설정`에 키를 저장합니다.

API 키는 저장소나 APK 소스에 포함되지 않으며 앱을 실행하는 기기의 로컬 저장소에만 보관됩니다. 배포용 키에는 Android 앱 제한과 YouTube Data API 제한을 설정하는 것을 권장합니다.

## YouTube 재생 참고사항

영상은 YouTube IFrame Player API를 이용해 재생하며 다운로드하거나 음원을 추출하지 않습니다. 게시자가 외부 임베드를 차단한 영상은 앱에서 재생할 수 없습니다.

일부 Android 기기에서 영상이 흰 화면으로 보인다면 Play 스토어에서 Android System WebView를 최신 버전으로 업데이트하세요.
