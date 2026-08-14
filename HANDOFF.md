# TeSing 개발 인수인계

최종 정리일: 2026-08-14

## 프로젝트 개요

TeSing은 휴대폰 또는 태블릿에서 실행하고 차량 Bluetooth로 소리를 출력하는 Android용 YouTube 노래방 앱이다. 저장소는 비공개 GitHub 저장소이며 현재 기본 브랜치는 `main`이다.

- 저장소: `https://github.com/hamseunghak/tesla-karaoke`
- Android 모듈: `tesla-karaoke`
- 패키지/애플리케이션 ID: `com.seunghak.teslasing`
- 표시 이름: `TeSing`
- 현재 버전: `0.2.0` (`versionCode = 2`)
- 최소 Android: API 26
- 현재 `compileSdk`/`targetSdk`: API 34
- 개발 언어/UI: Kotlin, Jetpack Compose
- Java/JDK: 17

패키지 ID와 기존 SharedPreferences 이름에는 예전 `teslasing`/`tesla_sing` 문자열이 남아 있다. 설치된 앱의 업데이트 호환성과 기존 API 키·즐겨찾기 보존을 위해 의도적으로 유지한다. 사용자에게 보이는 브랜드는 `TeSing`으로 변경했다.

## 현재 구현된 기능

- YouTube Data API v3 검색
- 검색어에 `금영 노래방 KY karaoke` 자동 추가
- 노래방 관련 결과만 남기고 임베드 재생을 막는 경우가 많은 TJ 관련 결과 제외
- 사용자가 입력한 YouTube API 키를 기기 로컬에 저장
- API 키 발급 페이지 연결
- 검색 결과 선택 후 하단 썸네일/재생 패널 표시
- 사용자 재생 동작 후 전체화면 `YouTubePlayerActivity` 실행
- YouTube IFrame Player 기반 앱 내부 재생
- 재생 화면에서 ±10초, 볼륨, YouTube 지원 재생 속도 조절
- 가로/세로 화면 지원
- 즐겨찾기, 최근 재생, 최대 30곡 예약 목록 로컬 저장
- 예약곡 연속 재생
- 재생 오류 코드 감지 후 재생 불가 영상을 자동으로 건너뛰고 마지막 곡 종료 처리
- 예약목록과 약 2초 단위 재생 위치를 로컬에 저장해 닫기·뒤로가기·강제 종료 후 복원
- 예약자 사전 등록·삭제, 예약 시 예약자 선택, 사람별 공정 순서 자동 배치
- 전체화면 플레이어 상단에 현재 예약자와 다음 예약곡 제목 표시
- 기기 내부 재생 횟수를 집계하는 `내 애창곡`
- TJ미디어 공식 응답을 이용한 최근 7일 `TJ 가요 TOP100`
- TJ 차트 곡의 제목·가수로 재생 가능한 금영 YouTube 영상을 연결
- YouTube 앱으로 열기 대체 동작
- 주차/주행 상태 UI와 주행 중 조작 잠금

## 중요 파일

- `tesla-karaoke/src/main/java/com/seunghak/teslasing/MainActivity.kt`
  - Compose 메인 UI, 검색 화면, 설정, 즐겨찾기/최근/예약 화면
- `tesla-karaoke/src/main/java/com/seunghak/teslasing/YouTubeClient.kt`
  - 검색 쿼리 생성, YouTube Data API 호출, 결과 필터링
- `tesla-karaoke/src/main/java/com/seunghak/teslasing/YouTubeLibrary.kt`
  - 즐겨찾기, 최근 재생, 예약 목록 직렬화와 SharedPreferences 저장
- `tesla-karaoke/src/main/java/com/seunghak/teslasing/YouTubePlayerActivity.kt`
  - 전체화면 WebView/IFrame 플레이어, 오류 자동 건너뛰기, 재생 위치 저장, 예약곡 자동 재생
- `tesla-karaoke/src/main/java/com/seunghak/teslasing/TjChartClient.kt`
  - TJ 공식 차트 응답에서 최근 7일·가요 TOP100 조회
- `tesla-karaoke/src/main/AndroidManifest.xml`
  - 앱 이름, 액티비티, 인터넷 권한
- `tesla-karaoke/build.gradle.kts`
  - Android 버전, 앱 버전, Compose 의존성

## 빌드 및 실행

```bash
./gradlew :tesla-karaoke:assembleDebug
```

디버그 APK:

```text
tesla-karaoke/build/outputs/apk/debug/tesla-karaoke-debug.apk
```

2026-08-14에 버전 `0.2.0` 변경 사항을 포함한 `assembleDebug` 빌드 성공을 확인했다. Android Studio 실행 구성은 `tesla-karaoke` 모듈을 선택한다.

## 현재 제한 및 알려진 사항

1. 사용자가 YouTube Data API 키를 직접 입력해야 검색할 수 있다.
2. 게시자가 외부 임베드를 막은 영상은 앱 내부에서 재생할 수 없다.
3. Android System WebView 버전에 따라 흰 화면이나 재생 실패가 발생할 수 있다.
4. YouTube IFrame Player로는 영상의 실제 음정을 변경할 수 없다.
5. `SynthEngine`, `VocalAnalyzer`, `KaraokeModels` 및 Demo 관련 분기는 예전 데모 기능의 잔여 코드다. 현재 YouTube 기본 화면에서는 접근되지 않으므로 정리 대상이다.
6. `YouTubePlayerActivity`의 시스템 UI 숨김 API는 deprecated 경고가 있으며 최신 WindowInsets API로 교체할 수 있다.
7. Play 스토어용 출시 서명과 AAB 빌드는 아직 구성하지 않았다.
8. 기존 GitHub Release `v0.1.0`의 APK는 TeSing 이름 변경 전 빌드다.
9. TJ 가요 TOP100은 TJ미디어 홈페이지의 내부 차트 응답 형식에 의존하므로 사이트 개편 시 수정이 필요하며, 상용 배포 전 데이터 이용 조건 확인이 필요하다.

## Play 스토어 준비 상태

- 새 개인 개발자 계정 생성 및 주소 인증 서류 검토 진행 중
- 앱 가격은 2,000원 단일 유료 판매를 검토 중
- 신규 개인 계정이면 비공개 테스트에서 최소 12명의 테스터가 14일 연속 참여해야 함
- 2026-08-31 이후 신규 앱 제출을 고려해 `compileSdk`/`targetSdk`를 API 36으로 올리는 것이 우선
- 개인정보처리방침, 이용약관, YouTube 약관/Google 개인정보처리방침 연결 필요
- Play Console 데이터 보안, 콘텐츠 등급, 스토어 등록정보와 이미지 준비 필요
- 현재 디버그 APK가 아니라 서명된 Android App Bundle(`.aab`) 필요
- 앱이 API 키 없이 심사 가능하도록 공용 검색 서버 또는 심사용 접근 방법 결정 필요

## API 키 없는 검색에 대한 결정 사항

아직 구현하지 않았다. 검토 중인 권장 구조는 다음과 같다.

1. 앱이 TeSing 백엔드에 검색 요청
2. 백엔드가 제한된 개발자 YouTube API 키로 검색
3. 기기별/사용자별 속도 제한, 사용량 모니터링, 반복 검색 최적화 적용
4. 할당량 부족 시 YouTube 앱 검색으로 대체
5. 고급 사용자의 개인 API 키 입력은 선택 기능으로 유지 가능

YouTube API 할당량과 정책은 변경될 수 있으므로 구현 시 최신 공식 문서를 다시 확인해야 한다. API 키를 소스, Git, APK에 하드코딩하지 않는다.

## 다음 작업 우선순위

1. Android API 36으로 빌드 환경과 `targetSdk` 업그레이드 후 실제 기기 회귀 테스트
2. 사용하지 않는 Demo/Synth/Vocal 코드와 `MediaSource.Demo` 분기 제거
3. 충전 세션 타이머와 예약곡 총 재생시간 비교 기능 검토
4. 동승자 휴대전화 QR 예약용 백엔드와 익명 세션 설계
5. 공용 검색 백엔드 사용 여부와 할당량 운영 방안 확정
6. 앱 아이콘, 스플래시, 온보딩, 오류 화면을 TeSing 브랜드로 마무리
7. 개인정보처리방침/이용약관 작성 및 앱 설정 화면에 링크 추가
8. Play App Signing용 릴리스 서명 구성 및 AAB 생성
9. 가로/세로 회전, 전체화면 닫기/뒤로가기, WebView 재생, 예약 연속 재생 실제 기기 테스트

## 다른 컴퓨터에서 시작하기

```bash
git clone git@github.com:hamseunghak/tesla-karaoke.git
cd tesla-karaoke
./gradlew :tesla-karaoke:assembleDebug
```

비공개 저장소이므로 새 컴퓨터의 SSH 공개키를 GitHub 계정에 등록하거나 HTTPS 인증을 사용해야 한다. Android Studio, JDK 17, 필요한 Android SDK도 설치한다.

새 Codex 작업에서 사용할 시작 요청 예시:

```text
이 저장소는 Android용 차량 노래방 앱 TeSing이다.
HANDOFF.md와 README.md, 최근 Git 기록을 먼저 확인하고 현재 상태를 요약한 뒤
다음 작업 우선순위의 첫 번째 미완료 항목부터 진행해줘.
```

## 보안 및 백업

- YouTube API 키, Google 인증 정보, Play Console 정보는 커밋하지 않는다.
- `.jks`, `.keystore`, `keystore.properties`, `.aab`, `.apk`는 `.gitignore` 대상이다.
- 출시 서명 키를 만들면 암호화된 별도 저장소에 백업한다. GitHub에 올리지 않는다.
- 새 기기에서는 앱 설정의 API 키와 로컬 즐겨찾기/최근 재생 데이터가 자동 복원되지 않는다.
