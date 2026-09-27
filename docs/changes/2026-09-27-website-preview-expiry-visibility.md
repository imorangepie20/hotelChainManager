# 저장 초안 미리보기 만료 시각·영구 E2E (2026-09-27)

## 결정

- 관리자 UI에 미리보기 링크의 만료 시각을 노출한다. 링크를 열거나 복사해 공유하기 전에 검토 가능
  시간을 알아야 하기 때문이다.
- 정책의 단일 권위는 Spring Boot `website.preview.ttl-minutes`와 발급 응답 `expiresAt`이다. 관리자
  화면에서 TTL을 바꾸거나 `발급 후 10분`처럼 설정값을 추론하지 않는다.
- 발급 대화상자는 서버 `expiresAt`을 `<time dateTime>`과 서울 절대 시각으로 표시하고, 이후에는 새
  링크가 필요하다고 안내한다. 초 단위 aria-live countdown은 두지 않는다.

## 구현

- `application.yml`이 `WEBSITE_PREVIEW_TTL_MINUTES`를 `website.preview.ttl-minutes`에 연결한다.
  로컬·Zorin compose와 배포 환경 예제에도 기본 10분을 전달하며 서버가 1~60분 범위를 검증한다.
- grant 발급 만료 시각을 PostgreSQL `TIMESTAMPTZ`와 같은 마이크로초 정밀도로 정규화했다. 발급 JSON과
  저장 후 고객 미리보기 `X-Website-Preview-Expires-At` header가 같은 Instant를 반환한다.
- 코드 리뷰 후 서버가 `issued_at`도 조회해 발급 시각 이전 접근을 거부하도록 보완했다. 이로써 문서화한
  `[issuedAt, expiresAt)` 유효 구간의 하한과 상한을 모두 서버가 강제한다.
- 관리자 Playwright는 고정 비기본 만료 시각을 사용해 하드코딩 10분 문구가 없고 서울 시간과 원본
  ISO datetime이 정확한지 1280px·390px에서 확인한다.
- 고객 preview Playwright는 충돌 없는 별도 포트를 환경변수로 선택할 수 있다. 공개 화면 복귀 시
  robots 계약은 현재 구현대로 `index,follow`를 확인한다.
- 실제 시간 테스트는 TTL 1분 grant의 응답 `expiresAt`까지 기다린 뒤 250ms 간격으로 410을 확인한다.
  고정 65초 sleep과 5초짜리 임의 deadline을 제거하고 발급 직후 200·`no-store`·만료 header 일치도
  함께 단정한다.

## 검증

- Spring `WebsitePreviewGrantIntegrationTest`, `WebsitePreviewExpiryIntegrationTest`: 10건 통과.
  `issuedAt` 직전 410/정확한 경계 200, `expiresAt` 직전 200/정확한 경계 410과 실제 1분 경과 뒤
  410 `WEBSITE_PREVIEW_UNAVAILABLE`를 확인했다. wall-clock 테스트는 외부 테스트 트랜잭션 없이 커밋된
  grant를 조회하고 종료 후 생성 자료를 명시적으로 정리한다.
- 관리자 `website-saved-draft-preview.spec.ts`: Chromium 6건 통과. 1280px·390px, 저장 선행,
  정확한 만료 시각, 링크 복사·폐기, Escape 포커스 복귀를 확인했다.
- 고객 `customer-saved-draft-preview.spec.ts`: Chromium 14건 통과. 만료 전 초안·noindex,
  클라이언트 만료 화면, 410 공개본 fallback 방지, 공개 화면 robots 복구를 확인했다.
- 관리자 TypeScript와 production build, 로컬·Zorin compose config가 통과했다. 대상 ESLint는 오류 0건,
  기존 identity 변경 effect의 상태 초기화·ref cleanup 경고 2건이다.

## 제한

- 브라우저 시계는 표시용이다. 실제 접근 허용은 서버가 결정하며 링크는 만료 전에도 폐기 또는 초안
  변경으로 410이 될 수 있다.
- 실제 API·고객 Vite·Playwright를 동시에 띄우는 공통 full-stack fixture는 없다. 실제 DB/HTTP 시간
  전환은 Spring wall-clock 테스트가, 실제 renderer와 UI는 두 Playwright suite가 각각 담당한다.
