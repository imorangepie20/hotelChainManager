# 저장 초안 미리보기 만료 시간 설정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- 저장 초안 미리보기 grant의 만료 시간이 `WebsitePreviewGrantService`의 `TTL` 상수 10분으로 고정돼 있었다. 10분이 짧은 검토 환경에서는 만료 전에 확인이 끝나고, 긴 검토 환경에서는频繁하게 재발급해야 했다.
- 이전 세션에서 "저장 초안 미리보기의 실제 10분 만료 시간 경과 검증"을 진행하다가 끊겼다. 만료 시간을 코드가 아니라 환경에서 조정할 수 있어야 1~2분으로 줄여서 실제 시간 경과 검증을 실행할 수 있다.

## 구현 범위

### 서버

- `WebsitePreviewGrantService`의 `private static final Duration TTL`을 삭제하고 생성자 주입 필드 `ttl`로 바꿨다.
  - `@Value("${website.preview.ttl-minutes:10}") int ttlMinutes`를 받아 `Duration.ofMinutes(ttlMinutes)`로 계산한다. 기본값은 10으로 기존 동작을 유지한다.
  - 1~60분 밖의 값이면 `IllegalArgumentException("저장 초안 미리보기 만료 시간은 1~60분이어야 합니다.")`로 시작을 거부한다. 검증·대사 화면이 아닌 노출용 기능이므로 1시간을 상한으로 뒀다.
  - grant 발급의 `expiresAt = now.plus(ttl)`만 바꿨다. 폐기·cleanup 보관 24시간은 그대로 둔다.
- 기존 `application-dev.yml`의 `website.preview.require-https: false`와 같은 섹션에 `ttl-minutes`를 별도로 두지 않았다. 기본 10분을 그대로 쓰고 검증 시점에만 `@SpringBootTest(properties = ...)`로 1을 주입한다.

### 테스트

- `WebsitePreviewExpiryIntegrationTest`를 새로 작성했다. `website.preview.ttl-minutes=1`로 띄우고 클럽을 조작하는 기존 테스트와 달리 시스템 클럭을 그대로 둔 채 TTL 1분 + 여유 5초를 실제 시간으로 기다린다. 2초 간격으로 폴링하며 첫 410을 잡으면 종료한다. 이후 응답이 410이고 `code`가 `WEBSITE_PREVIEW_UNAVAILABLE`인지 단정한다.
- 발급·해지 권한이 있는 `HQ_EDITOR` 계정을 시드하고 홈 초안(`homeDraft`)을 가져와 `previewGrants.issue(...)`로 grant를 만든 뒤 `MockMvc`로 `/api/website/pages/preview`를 호출한다.

## 자동 검증

- API: `mvnw test-compile` 종료 코드 0.
- API: `WebsitePreviewExpiryIntegrationTest` 1건 종료 코드 0. `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 72.24 s`.
- 라이브: 루트 `.env`는 건드리지 않고 실행 중인 API(4080, `PAYMENT_PROVIDER=toss-test`, health `UP`)로 확인했다.
  - 본사 세션으로 `GET /api/staff/website/home` 200(draftVersion 5) → `PUT /api/staff/website/home` 200(draftVersion 6). 홈 블록은 `HERO`가 1개 이상 필요하다는 400을 먼저 만나서 기존 `draftContent`의 `HERO` 제목만 바꿔 저장했다.
  - `POST /api/staff/website/pages/{homeId}/preview-grants` 200. 응답이 `expiresAt: 2026-09-19T03:45:01.129705139Z`를 반환했다.
  - 고객 4000에서 `GET /api/website/pages/preview?path=/&locale=ko` 호출. 03:35:43~03:44:43 200 → **03:45:13 410** `WEBSITE_PREVIEW_UNAVAILABLE`. 만료 시각으로부터 12초 안에 전환됐다.
  - 검증용 grant는 `DELETE /api/staff/website/preview-grants/{grantId}` 204로 폐기했다.
  - 홈 초안은 원래 `HERO` 제목으로 복원해 `PUT /api/staff/website/home` 200(draftVersion 7)을 받았다. `draftMetadata`는 `slug: home, path: /, menuLabel: 홈, menuVisible: false`로 유지됐다.
- 이전 세션에서 끊긴 시점의 404 원인도 정리됐다. 초안 저장 엔드포인트는 `PUT /api/staff/website/pages/{pageId}/draft`가 아니라 `PUT /api/staff/website/home`(`WebsitePageManagementController` line 32)이다.

## 미검증 항목

- 만료 시간을 10분이 아닌 값으로 컨테이너에 전달하는 동선. `compose.yaml`은 `WEBSITE_PREVIEW_TTL_MINUTES`를 아직 전달하지 않는다. `website.preview.ttl-minutes`는 RELAXED_BINDING으로 대소문자 구분이 없어 컨테이너 환경 변수를 바로 연결할 수 있다. 실사용 환경에서 10분이 아닌 값이 필요해지면 그때 `compose.yaml`에 추가한다.
- 영구 Playwright 브라우저 suite. 이번 검증은 API·고객 4000 직접 호출로만 수행했다.
- 관리자 UI에서 만료 시간 표시·변경. grant 응답의 `expiresAt`를 화면에 노출하지 않는다.
- 1~60분 경계값 거부의 라이브 확인. 생성자 검증은 서버 테스트가 아닌 시작 시점이므로 별도 테스트를 두지 않았다.
