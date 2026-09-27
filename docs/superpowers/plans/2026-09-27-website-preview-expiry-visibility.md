# 저장 초안 미리보기 만료 시각·영구 E2E 계획 (2026-09-27)

> 기준: `docs/overview/current-development-context.md`의 `다음 작업` 2번. 요청의 `#step-6` 앵커는
> 현재 문서에 없으므로 저장 초안 미리보기 후속 범위를 기준으로 한다.

## 제품 결정

- 관리자에게 만료 시각을 노출한다. 링크를 열거나 복사하는 시점에 검토 가능 시간을 알아야 하기 때문이다.
- 만료 정책의 단일 권위는 Spring Boot의 `website.preview.ttl-minutes`와 발급 응답 `expiresAt`이다.
  UI는 TTL 분 값을 추론하거나 `발급 후 10분`처럼 고정 문구를 표시하지 않는다.
- 기존 `저장 초안 미리보기` 대화상자의 링크 위에 서버 `expiresAt`을 서울 시간의 절대 시각으로
  표시하고 `<time dateTime>` 의미 구조를 제공한다. 이 시각 이후 새 링크가 필요하다는 설명을 함께 둔다.
- TTL 설정을 관리자 화면에서 변경하는 기능, 활성 grant 목록, 자동 갱신과 알림은 이번 범위가 아니다.

## 시간·API 계약

- 기본 TTL은 10분이고 허용 범위는 1~60분이다. 환경변수 `WEBSITE_PREVIEW_TTL_MINUTES`를
  `website.preview.ttl-minutes`에 연결해 로컬·배포 compose에서도 같은 설정을 전달한다.
- 유효 구간은 `[issuedAt, expiresAt)`이다. `expiresAt` 직전까지 200이고 정확히 그 시각부터
  `410 WEBSITE_PREVIEW_UNAVAILABLE`이며 공개본으로 대체하지 않는다.
- 발급 JSON과 고객 미리보기 성공 header의 `expiresAt`은 UTC ISO-8601 Instant다. UI만
  `Asia/Seoul`로 변환하며 접근 허용 여부는 계속 서버가 결정한다.

## 구현·검증

1. 관리자 Playwright의 비기본 TTL 응답으로 정확한 `<time dateTime>`·서울 시각·고정 10분 문구 부재를
   1280px와 390px에서 검증한다.
2. 실제 시간 통합 테스트는 TTL 1분으로 발급 직후 200과 `no-store`·만료 header를 확인하고,
   발급 응답 `expiresAt`을 기준으로 polling해 410과 오류 코드를 검증한다.
3. 기존 고객 renderer Playwright로 만료 전 초안·`noindex,nofollow`, 만료 화면 전환, 공개본 fallback
   부재를 유지한다.
4. 서버 grant 경계 테스트, 관리자 TypeScript/Playwright, 고객 preview Playwright를 실행한다.

## 완료 기준

- 설정값이 10분이 아니어도 관리자 표시가 서버 응답과 일치하고 하드코딩 TTL이 없다.
- 만료 전 실제 API 접근은 200, 실제 시간이 지난 동일 grant는 410이다.
- 390px에 가로 넘침이 없고 대화상자 Escape·포커스 복귀·링크 복사·폐기가 유지된다.
- 초안·발행본·감사·미디어 사용 위치는 미리보기 발급과 조회로 변경되지 않는다.
