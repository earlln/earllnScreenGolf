# EarllnScreenGolf ⛳

스크린골프 모임의 **방 편성**을 한 번에 끝내는 안드로이드 앱. GolfZo(HSGA 전용 앱)를 Play 스토어 배포용으로 다시 만든 것입니다.

회원 명단을 등록해 두고, 그날 오는 사람만 체크한 뒤 방을 고르면
슬로우 골퍼가 한 방에 몰리지 않게 조를 짜서 **공유하기 좋은 이미지**로 만들어 줍니다.

## GolfZo 에서 바뀐 점

| | GolfZo | EarllnScreenGolf |
|---|---|---|
| 타이틀 | `GolfZo` + `HSGA` 배지 | `EarllnScreenGolf` + `v0.1.0` 버전 배지 (앱 화면, 결과 이미지 모두) |
| 따로 세는 음식 | 김밥 고정 | **설정에서 고름** — 김밥 · 햄버거 · 샌드위치 · 도시락 · 짜장면 · 치킨 · 커피 · 맥주, 또는 이름·이모지·단위를 직접 입력 |
| 결과 이미지 공유 | 무제한 | **5번까지 무료**, 그 뒤로는 `공유 무제한`(한 번 구매) |
| 패키지 | `com.earlln.golfzo` | `com.earlln.screengolf` |
| 서명 | 저장소에 넣은 키 | 테스트 APK는 저장소의 테스트 키, 스토어용은 GitHub Secrets 의 업로드 키 |

나머지(참가 체크, 방 격자 선택, 조 추첨 화면, 슬로우 분산, 같은 조 / 다른 조 조건, 핸디캡, 게임 참여, 명단 내보내기/가져오기)는 그대로입니다.
GolfZo 에서 내보낸 `members.json` 도 그대로 가져올 수 있습니다 (`kimbap` 표시는 음식 표시로 들어옵니다).

## 음식 설정

메인 화면 오른쪽 위 `설정` → **따로 세는 음식**.

- 칩을 누르면 바로 바뀝니다. `직접 입력`을 고르면 이름(최대 8자), 이모지, 단위(개 / 잔 / 그릇 …)를 넣고 `적용`
- 메인 화면 카드, 회원 줄의 칩 이름, 추첨 화면, 결과 이미지(꼬리표·하단 카드·범례)에 모두 반영됩니다
- 회원별 표시는 음식을 바꿔도 그대로 남습니다 (김밥 먹던 사람이 그대로 햄버거로 넘어감)
- 명단 파일에는 `"food": true` 로 저장됩니다

## 공유 횟수와 결제

- 결과 화면의 `공유` 버튼에 `무료 N회 남음` 이 표시됩니다. 공유 창을 띄울 때마다 1회씩 줄어듭니다
- 5회를 다 쓰면 `공유 무제한 구매` 안내가 뜨고, 구매하면 바로 공유가 이어집니다
- `이미지 저장`(갤러리)은 횟수와 상관없이 무료입니다
- 설정 화면에서 사용 횟수, `공유 무제한 구매`, `구매 복원`(폰을 바꿨을 때)을 볼 수 있습니다
- 결제는 Google Play 결제(인앱 상품 ID **`unlimited_share`**, 한 번 사면 끝나는 상품)를 씁니다
- 무료 횟수는 폰에만 저장되므로 앱을 지웠다 깔면 0부터 다시 셉니다. 구매는 Google 계정에 남아 `구매 복원`으로 돌아옵니다

## 테스트하기

빌드는 두 가지입니다.

| | 테스트 APK (`EarllnScreenGolf-test.apk`) | 스토어용 AAB (`EarllnScreenGolf.aab`) |
|---|---|---|
| 용도 | 폰에 바로 깔아서 기능 확인 | Play Console 업로드 (내부 테스트 → 프로덕션) |
| 결제 | **가짜 구매 창** (돈이 나가지 않음) | 실제 Google Play 결제 (테스터는 테스트 카드) |
| 설정 화면 | `테스트 빌드 전용` 카드: `공유 횟수 0으로`, `구매 취소(테스트)` | 없음 |
| 앱 이름 / 패키지 | `ScreenGolf 테스트` / `com.earlln.screengolf.test` | `EarllnScreenGolf` / `com.earlln.screengolf` |

테스트 APK 와 스토어 앱은 패키지가 달라서 **한 폰에 나란히** 깔립니다.

### 1단계 — 폰에서 바로 테스트 (지금 가능)

1. `main` 에 push 하면 **Actions** 가 빌드합니다 (약 3~5분)
2. 폰 브라우저로 GitHub 에 로그인한 뒤 **Releases → latest → `EarllnScreenGolf-test.apk`** 다운로드 후 설치
3. 확인할 것
   - 타이틀이 `EarllnScreenGolf v0.1.0-test` 로 보이는지
   - `설정`에서 음식을 바꾸면 메인 카드·칩·결과 이미지가 바뀌는지
   - 공유를 5번 하면 6번째에 구매 안내가 뜨는지 → `구매 처리` 후 무제한이 되는지
   - `공유 횟수 0으로` / `구매 취소(테스트)` 로 처음부터 다시 시험

### 2단계 — Play 내부 테스트 (실제 결제 흐름)

**업로드 키 만들기 (한 번만).** 이 키를 잃어버리면 Play Console 에서 키 재설정 요청을 해야 하니 안전한 곳에 백업하세요.

```bash
keytool -genkeypair -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

GitHub Secrets 에 넣기 (`gh` CLI 기준, 비밀번호는 위에서 정한 값):

```bash
base64 -w0 upload.keystore | gh secret set UPLOAD_KEYSTORE_BASE64 -R earlln/earllnScreenGolf
gh secret set UPLOAD_KEYSTORE_PASSWORD -R earlln/earllnScreenGolf
gh secret set UPLOAD_KEY_ALIAS -R earlln/earllnScreenGolf -b upload
gh secret set UPLOAD_KEY_PASSWORD -R earlln/earllnScreenGolf
```

그 다음 push(또는 Actions → `Build` → `Run workflow`)하면 Releases 에 `EarllnScreenGolf.aab` 가 함께 올라옵니다.

**Play Console 설정.**

1. [Play Console](https://play.google.com/console) → **앱 만들기** (이름 `EarllnScreenGolf`, 무료 앱, 인앱 상품 있음)
2. **테스트 → 내부 테스트 → 새 버전 만들기** → `EarllnScreenGolf.aab` 업로드 (Play 앱 서명 사용에 동의)
   - 결제 상품은 AAB 를 한 번 올린 뒤에야 만들 수 있습니다
3. **수익 창출 → 제품 → 인앱 상품 → 상품 만들기**
   - 상품 ID: **`unlimited_share`** (코드와 정확히 같아야 함)
   - 이름: `공유 무제한`, 가격은 원하는 대로 → **활성화**
4. **테스터** 탭에서 테스터 이메일 목록을 만들고, 나오는 참여 링크를 폰에서 열어 설치
5. **설정 → 라이선스 테스트**에 같은 Gmail 을 추가하면 실제 돈 대신 테스트 카드로 결제됩니다
6. 앱에서 공유 5번 → 구매 → 무제한 적용, 앱 삭제 후 재설치 → `설정 → 구매 복원` 으로 되돌아오는지 확인

내부 테스트에서 이상이 없으면 같은 AAB 를 **프로덕션**으로 승격합니다.
(2023년 11월 이후 만든 개인 개발자 계정은 프로덕션 전에 **테스터 12명 이상으로 14일 비공개 테스트**를 거쳐야 합니다.)

### 스토어 등록 전에 준비할 것

- **개인정보처리방침 URL** — 이 앱은 명단을 폰 안에만 저장하고 서버로 보내지 않습니다. 그 내용을 earlln.com 등에 한 페이지로 올려 주소를 넣으면 됩니다
- **데이터 보안 설문** — 수집·공유하는 데이터 없음, 결제는 Google Play 가 처리
- 앱 아이콘 512×512, 그래픽 이미지 1024×500, 휴대전화 스크린샷 2장 이상
- 콘텐츠 등급 설문, 타겟 연령층

## 버전 올리기

스토어에 새 버전을 올릴 때는 `app/build.gradle` 에서 **`versionCode` 를 1 올리고** `versionName` 을 바꿉니다 (예: `0.1.0` → `0.1.1`).
Play 는 같은 `versionCode` 를 두 번 받지 않습니다.

## 로컬 빌드

- compileSdk / targetSdk 36 · minSdk 26 · JDK 17 · Kotlin 2.1.21 · AGP 8.11.1 · Gradle 8.13 · Play Billing 8.0.0
- 테스트 APK: `gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- 조편성 로직 테스트: `gradle testDebugUnitTest`

조편성 규칙(방 인원 균등 분배, 큰 조를 가운데로, 2인 방은 슬로우 먼저, 같은 조 / 다른 조 조건과 우선순위)은 GolfZo 2.4 와 같고,
`app/src/test/java/com/earlln/screengolf/GroupingTest.kt` 로 CI 에서 매번 검증됩니다.

## 변경 내역

| 버전 | 내용 |
|---|---|
| **0.1.0** | GolfZo 2.4 기반 첫 버전 — 타이틀 `EarllnScreenGolf` + 버전 배지, 음식 설정, 결과 이미지 공유 5회 무료 후 `공유 무제한` 인앱 결제, Android 15+ 전체 화면(edge-to-edge) 대응, Play 업로드용 AAB 빌드 |
