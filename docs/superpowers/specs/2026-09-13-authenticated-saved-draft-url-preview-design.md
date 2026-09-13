# 인증형 저장 초안 URL 미리보기 설계

작성일: 2026-09-13

## 목적

현재 관리자 CMS의 대화상자 미리보기는 편집 중인 메모리 상태를 빠르게 확인하는 용도다. 실제 고객 사이트의 라우팅, 반응형 레이아웃, 공통 내비게이션과 동일한 화면에서 저장된 초안을 검토할 수는 없다.

이번 단계는 본사 콘텐츠 담당자가 10분 동안 사용할 수 있는 임시 검토 링크를 발급하고, 실제 고객 사이트가 저장된 한국어·영어 초안을 기존 공개 페이지 렌더러로 표시하게 한다. 링크는 소지자를 인증하는 bearer token으로 동작하지만 공개본이나 발행 상태를 바꾸지 않는다.

## 범위

### 포함

- 활성 `HOME_PAGE`, `HOTEL_LANDING`, `CONTENT_PAGE`의 저장된 최신 초안
- 한국어 초안과 존재하는 영어 초안
- `HQ_ADMIN`, `HQ_EDITOR`, `HQ_PUBLISHER`의 링크 발급
- 10분 동안 링크 소지자가 열 수 있고, 열린 브라우저 탭에서 다시 불러올 수 있는 임시 링크
- 페이지·locale·초안 version에 묶인 DB 기반 opaque token
- 실제 고객 경로와 기존 고객 페이지 렌더러 재사용
- 미리보기 표시, 종료, 만료·폐기·충돌 오류 화면
- 미리보기 중 예약·결제·콘텐츠 CTA 비활성화
- 발급자 또는 `HQ_ADMIN`의 링크 폐기
- 관리자·고객 UI의 키보드와 390px 동작

### 제외

- 저장하지 않은 편집기 메모리 상태 공유
- 영구 또는 익명 공개 공유 링크
- 여러 초안 페이지를 연결한 draft navigation tree
- 댓글, 승인 요청, 공동 검토 이력
- 스크린샷·PDF 생성
- 이미지 variant·CDN 동작 변경
- 초안 미리보기에서 예약·결제·재고 mutation 실행
- 기존 공개 페이지 resolve 계약이나 발행 절차 변경

## 접근 방식 결정

### 선택: DB 기반 opaque grant

서버가 32-byte 암호학적 난수를 URL-safe Base64 padding 없는 token으로 한 번 발급하고 SHA-256 hash만 저장한다. grant는 페이지, locale, 발급 당시 draft version, 정규화된 고객 경로, 발급자와 만료 시각을 기록한다. 이 방식은 개별 폐기, 짧은 만료, version 변경 즉시 무효화와 원문 token 비저장을 함께 만족한다.

### 선택하지 않은 방식

- 서명된 stateless URL은 DB 조회가 없지만 만료 전 개별 폐기가 어렵고, 이미 발급된 링크의 상태를 추적하기 어렵다.
- 직원 세션을 고객 앱까지 전달하는 방식은 고객 사이트와 관리자 인증을 결합하고, 링크 검토자가 별도 직원 로그인을 해야 한다.
- 임시 공개 snapshot을 만드는 방식은 발행되지 않은 콘텐츠를 별도 공개 데이터로 복제하고 정리·일관성 부담을 만든다.

## 핵심 불변 조건

1. 미리보기 대상은 서버에 저장된 최신 초안이다. 편집기에서 저장하지 않은 변경은 포함하지 않는다.
2. grant 발급과 폐기는 공개본, 발행 snapshot, 페이지 lifecycle을 바꾸지 않는다.
3. 고객 미리보기 조회는 읽기 전용이며 DB 정리나 audit mutation도 만들지 않는다.
4. token 원문은 발급 응답 외에 DB, 로그, 분석 이벤트에 저장하지 않는다.
5. token은 발급된 한 페이지·locale·draft version·경로에만 사용할 수 있다.
6. 초안 version 변경, 경로 변경, 페이지 보관, 만료 또는 폐기 뒤에는 같은 token으로 초안을 받을 수 없다.
7. 실패한 미리보기는 공개본으로 조용히 대체하지 않는다.
8. 미리보기 중 예약·결제·콘텐츠 CTA는 실행되지 않는다.
9. 일반 내비게이션으로 다른 경로에 이동하면 미리보기 세션을 종료하고 해당 공개 페이지를 표시한다.
10. 고객 화면은 기존 공개 페이지 renderer와 응답 모델을 재사용한다. 미리보기 전용 콘텐츠 renderer를 복제하지 않는다.

## 데이터 설계

`V24__create_website_preview_grant.sql`에서 `website_preview_grant`를 추가한다.

필수 데이터는 다음과 같다.

- grant UUID
- unique token hash
- page UUID와 외래 키
- `ko` 또는 `en` locale
- 발급 시점 draft version
- 발급 시점의 정규화된 고객 경로
- 발급한 직원 UUID
- 발급 시각과 만료 시각
- 선택적인 폐기 시각과 폐기자 UUID

만료 시각은 발급 시각보다 뒤여야 하며 기본 유효 시간은 서버 시계 기준 정확히 10분이다. token hash, 페이지·locale, 만료·폐기 상태를 조회하기 위한 인덱스를 둔다. 기존 감사 action 제약은 변경하지 않고 grant 행 자체가 발급·폐기 메타데이터를 가진다.

같은 직원이 같은 페이지·locale에 새 grant를 발급하면 아직 만료되지 않고 폐기되지 않은 이전 grant를 같은 트랜잭션에서 폐기한다. 만료·폐기 후 24시간이 지난 행은 새 grant 발급 트랜잭션에서 기회성으로 삭제한다. 별도 scheduler는 두지 않으며 고객 미리보기 GET에서는 정리하지 않는다.

## 서버 구성과 책임

### Preview grant 서비스

전용 서비스가 다음 책임만 가진다.

- 콘텐츠 직원 권한과 활성 페이지 확인
- locale별 현재 저장 초안과 draft version 확인
- 정규화된 실제 고객 경로 계산
- 안전한 token 생성, hash 저장, 기존 활성 grant 폐기
- token hash 조회와 만료·폐기·페이지·locale·version·경로 검증
- 발급자 또는 `HQ_ADMIN`의 명시적 폐기

한국어는 `website_page`의 현재 draft version과 문서를 사용한다. 영어는 `website_page_translation`의 현재 draft version과 문서를 사용하며 번역 초안이 없으면 발급하지 않는다. 서비스는 저장·검토·발행 상태를 변경하지 않는다.

### 초안 응답 조립

검증된 grant가 가리키는 저장 초안을 기존 공개 페이지 응답 모델인 `PublishedWebsitePage`와 같은 형태로 조립한다. 기존 문서 정규화, 미디어 URL 처리, `HOME_PAGE`, `HOTEL_LANDING`, `CONTENT_PAGE`별 응답 조립 규칙을 재사용하되 `PUBLISHED` lifecycle 요구만 grant 검증으로 대체한다.

미리보기 응답은 `Cache-Control: no-store`를 사용한다. 응답 body는 기존 모델을 유지하고 만료 시각은 `X-Website-Preview-Expires-At` 응답 헤더로 전달한다. 미들웨어와 접근 로그는 `X-Website-Preview` 요청 헤더를 마스킹해야 한다.

## API 계약

### 관리자 발급

`POST /api/staff/website/pages/{pageId}/preview-grants`

요청 헤더:

- `X-Staff-Session: <staff session>`

요청 body:

```json
{
  "locale": "ko",
  "expectedDraftVersion": 3
}
```

응답은 `grantId`, 발급 시 한 번만 반환하는 `previewToken`, locale prefix를 포함한 `previewPath`, `expiresAt`을 포함한다. 관리자 앱은 고객 사이트 base URL과 `previewPath`, `#preview=<previewToken>`을 결합한다. token을 query string에 넣지 않는다.

권한은 `HQ_ADMIN`, `HQ_EDITOR`, `HQ_PUBLISHER`에 허용하고 `BRANCH_STAFF`는 `403`으로 거부한다. 페이지가 비활성이거나 없고 영어 초안이 없으면 `404`, `expectedDraftVersion`이 현재 저장 version과 다르면 `409 WEBSITE_PAGE_VERSION_CONFLICT`를 반환한다.

### 관리자 폐기

`DELETE /api/staff/website/preview-grants/{grantId}`

발급자 또는 `HQ_ADMIN`만 폐기할 수 있다. 행이 남아 있는 이미 폐기되었거나 만료된 grant의 반복 요청은 최종 상태를 유지하는 멱등 성공으로 처리한다. 다른 콘텐츠 직원과 지점 직원은 `403`, 존재하지 않거나 정리된 grant는 `404`다. 폐기는 공개본과 초안 version을 바꾸지 않는다.

### 고객 미리보기 조회

`GET /api/website/pages/preview?path={normalizedPath}&locale={ko|en}`

요청 헤더:

- `X-Website-Preview: <preview token>`

서버는 token을 SHA-256으로 hash한 뒤 grant를 찾는다. 요청 path·locale가 grant 값과 다르면 token의 실제 대상을 노출하지 않도록 `404 WEBSITE_PREVIEW_NOT_FOUND`를 반환한다. token을 찾았지만 만료·폐기되었거나, 현재 페이지가 보관되었거나, 현재 경로·draft version이 발급 당시와 달라졌으면 `410 WEBSITE_PREVIEW_UNAVAILABLE`을 반환한다. 알 수 없는 token도 `404 WEBSITE_PREVIEW_NOT_FOUND`다.

성공한 token은 10분 동안 같은 대상에 여러 번 사용할 수 있다. 조회는 초안과 grant를 변경하지 않는다.

## 관리자 UI 흐름

기존 편집 화면의 locale별 action에 `실제 화면 미리보기`를 추가한다. 기존 대화상자 미리보기는 저장 전 빠른 확인 용도로 유지한다.

버튼은 활성 페이지이며 현재 locale 초안이 존재하고, 저장 요청이 진행 중이지 않고, 로컬 편집 상태와 마지막 저장 상태가 같을 때만 활성화한다. 저장하지 않은 변경이 있으면 먼저 저장해야 한다는 안내를 표시한다. 영어 초안이 없으면 영어 탭에서 발급 action을 제공하지 않는다.

클릭하면 현재 draft version으로 grant를 발급하고 실제 고객 경로를 새 탭으로 연다. 새 탭 열기가 차단되면 링크를 복사할 수 있는 대체 UI를 표시한다. 성공 상태에는 만료 시각, `링크 복사`, `링크 폐기`를 제공한다. 링크 원문은 컴포넌트 메모리에만 두며 브라우저 영구 저장소나 분석 이벤트로 보내지 않는다.

발급·복사·폐기 중 중복 action을 막는다. 대화상자 또는 상태 영역을 닫으면 시작 버튼으로 포커스를 돌린다. 390px에서도 만료 안내와 모든 action을 가로 스크롤 없이 사용할 수 있어야 한다.

## 고객 UI 흐름

### 진입과 세션 보관

고객 앱은 최초 route bootstrap에서 fragment의 정확한 `preview` 값을 읽는다. 값을 `sessionStorage`에 현재 path와 함께 저장한 직후 `history.replaceState`로 fragment를 제거한다. 따라서 token은 이후 주소 표시줄, Referer, 일반 navigation URL에 남지 않는다.

현재 path와 저장된 preview path가 같을 때만 preview API를 호출한다. 새로 고침은 같은 브라우저 탭 세션과 10분 유효 시간 안에서 미리보기를 다시 표시한다. path가 달라지면 token과 preview path를 지우고 기존 공개 resolve 흐름을 사용한다.

### 표시와 종료

성공 시 실제 고객 페이지 위에 `저장된 초안 미리보기` 고정 배너, 서버가 반환한 만료 시각과 `미리보기 종료` action을 표시한다. 문서에는 동적으로 `robots=noindex,nofollow`를 적용하고 종료 시 원래 metadata를 복원한다.

콘텐츠 본문과 공통 레이아웃은 기존 고객 renderer를 그대로 사용한다. 예약 가능 여부 검색, 예약 시작·변경·취소, 결제 등 booking action을 비활성화하고 콘텐츠가 제공하는 CTA는 navigation과 mutation을 실행하지 않게 한다. 비활성 control은 시각 상태뿐 아니라 `disabled` 또는 `aria-disabled`와 event 차단을 함께 적용한다.

헤더·푸터의 일반 사이트 내비게이션은 유지한다. 다른 경로를 선택하면 preview session을 지우고 목적지 공개 페이지로 이동한다. `미리보기 종료`는 현재 경로의 token을 지운 뒤 같은 경로의 공개 페이지를 다시 불러온다.

### 오류 상태

`404` 또는 `410`이면 같은 화면에서 전용 `미리보기를 사용할 수 없음` 상태를 표시한다. 공개 콘텐츠로 자동 fallback하지 않는다. 사용자는 `공개 페이지 보기`를 명시적으로 선택할 수 있으며, 이 action이 token을 지운 뒤 공개 resolve를 실행한다.

client-side 만료 시각이 지나도 같은 오류 상태로 전환한다. 서버 판단이 최종 권한이며 client 시계는 배너와 조기 UI 전환에만 사용한다.

## 보안과 개인정보

- token은 최소 256-bit entropy를 가지며 원문은 발급 응답 이후 서버가 복구할 수 없다.
- HTTPS가 아닌 운영 환경에서는 링크를 발급하거나 사용할 수 없다.
- fragment는 브라우저가 HTTP 요청에 보내지 않으며 고객 앱이 즉시 제거한다.
- customer API는 cookie 기반 직원 인증을 사용하지 않고 preview header만 검증한다.
- 요청 header, fragment, 복사된 전체 링크를 서버·클라이언트 로그와 분석 이벤트에서 제외한다.
- `Cache-Control: no-store`와 동적 `noindex,nofollow`로 브라우저·검색 노출을 줄인다.
- path·locale 불일치는 `404`로 처리해 정상 token의 대상 정보를 노출하지 않는다.
- 짧은 만료와 version 결합은 링크 유출 범위를 제한하지만, 유효 시간 안에는 링크 소지자가 접근할 수 있음을 관리자 UI에 안내한다.

## 오류 처리 요약

- `400`: 잘못된 locale, UUID, version 또는 요청 구조
- `401`: 관리자 발급·폐기 요청의 직원 세션 없음 또는 만료
- `403`: 역할 부족 또는 다른 직원의 grant 폐기 시도
- `404`: 관리자 대상 페이지·번역·grant 없음, 고객 token 없음 또는 대상 불일치
- `409 WEBSITE_PAGE_VERSION_CONFLICT`: 발급 요청의 draft version이 오래됨
- `410 WEBSITE_PREVIEW_UNAVAILABLE`: 만료, 폐기, 페이지 보관, 경로 또는 draft version 변경

`409` 발급 오류는 자동 재시도하지 않는다. 관리자가 최신 초안을 다시 확인한 뒤 새 링크를 발급한다. 고객 오류는 공개본으로 자동 fallback하지 않는다.

## 테스트

### 서버 통합

- 세 본사 콘텐츠 역할은 발급할 수 있고 지점 직원은 거부된다.
- 발급 응답에만 원문 token이 있으며 DB와 로그에는 hash만 남는다.
- grant가 올바른 페이지·locale·draft version·경로·10분 만료와 결합된다.
- 같은 발급자·페이지·locale의 새 grant가 이전 활성 grant를 폐기한다.
- 한국어·영어의 세 페이지 유형이 저장된 최신 초안을 기존 응답 형태로 반환한다.
- 영어 초안이 없으면 발급하지 않는다.
- cross-page, cross-path, cross-locale token 재사용은 `404`다.
- 만료·폐기·초안 저장·경로 변경·페이지 보관 뒤 기존 token은 `410`이다.
- 발급자와 `HQ_ADMIN`만 폐기하며 반복 폐기는 멱등이다.
- preview GET 전후 grant, page, translation, audit 행이 동일하다.
- 공개 resolve와 발행 snapshot은 grant 발급·조회·폐기 전후 동일하다.
- preview 응답은 `no-store`와 정확한 만료 header를 가진다.

### 관리자 E2E

- 저장된 깨끗한 초안에서만 실제 화면 미리보기 버튼이 활성화된다.
- 현재 locale과 draft version으로 발급하고 fragment token이 붙은 실제 경로를 새 탭으로 연다.
- 팝업 차단 시 복사 action을 제공한다.
- 만료 시각 표시, 복사, 폐기, 중복 클릭 방지와 오류 복구를 확인한다.
- 닫기·Escape 뒤 포커스 복귀와 390px 배치를 확인한다.

### 고객 테스트

- fragment token을 `sessionStorage`로 옮기고 주소에서 즉시 제거한다.
- 같은 path에서만 preview header를 보내고 다른 route에서는 token을 지운다.
- 세 페이지 유형과 한국어·영어 초안을 기존 renderer로 표시한다.
- 배너, 만료 시각, `noindex,nofollow`, 종료 동작을 확인한다.
- 예약·결제·콘텐츠 CTA가 키보드·포인터 모두에서 실행되지 않는다.
- 일반 내비게이션은 preview를 종료한 뒤 공개 목적지로 이동한다.
- `404`, `410`, client-side 만료에서 공개본으로 fallback하지 않고 전용 오류를 표시한다.
- 새로 고침 재사용과 명시적인 `공개 페이지 보기`를 확인한다.

### 직접 검사

- 변경한 preview grant 서버 통합 테스트를 실행한다.
- 변경한 관리자 Playwright 테스트와 TypeScript 검사를 실행한다.
- 변경한 고객 Vitest·Playwright 테스트와 TypeScript/build 검사를 실행한다.
- 테스트 DB와 API mock만 사용하고 실제 사용자 페이지·예약·결제에는 mutation을 보내지 않는다.
- 전체 서버 suite와 전체 브라우저 회귀는 변경 경계에서 추가 위험이 발견될 때만 실행한다.

## 배포와 롤백

DB migration을 먼저 적용한 뒤 기존 공개 resolve와 독립적인 신규 API·UI를 배포한다. 이전 관리자와 고객 앱은 신규 테이블과 endpoint를 사용하지 않으므로 함께 운영할 수 있다. 고객 앱이 먼저 배포되어도 fragment가 없는 일반 공개 흐름은 변하지 않는다.

롤백은 관리자 발급 action, 고객 preview bootstrap과 신규 API를 제거하는 방식이다. 남은 grant는 짧은 만료 뒤 사용할 수 없고 공개 데이터에는 영향이 없다. 테이블은 즉시 제거하지 않아 rollback 중 schema 호환성을 유지한다.

## 완료 기준

- 본사 콘텐츠 담당자가 저장된 한국어·영어 초안의 10분 실제 고객 사이트 링크를 발급할 수 있다.
- 원문 token은 URL fragment에서 즉시 제거되고 DB·로그·분석에 저장되지 않는다.
- grant는 페이지·locale·draft version·경로에 묶이며 만료·폐기·변경 시 확실히 거부된다.
- 고객 사이트는 기존 renderer로 초안을 표시하면서 예약·결제·CTA를 실행하지 않는다.
- 미리보기 오류는 공개 콘텐츠로 자동 대체되지 않고 사용자가 공개 페이지 전환을 선택한다.
- grant 발급·조회·폐기는 공개본, 발행 snapshot, 초안 문서와 lifecycle을 바꾸지 않는다.
- 관리자·고객의 키보드와 390px 흐름 및 직접 서버·UI·타입 검사가 통과한다.
- 실제 사용자 데이터에는 검증용 mutation을 보내지 않는다.
