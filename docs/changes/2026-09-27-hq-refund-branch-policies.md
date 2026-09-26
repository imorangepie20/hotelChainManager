# 본사 환불 규칙·지점별 정책·감사 통합

## 변경 이유

기존 취소 정책은 체인 전역의 단일 전액 환급 마감만 지원했다. 마감 뒤 부분·0원 환불,
지점별 예외, 정책 변경의 통합 감사가 없어 실제 취소 결과와 운영 정책을 함께 관리하기 어려웠다.

## 구현 내용

- V65 additive migration으로 범위별 정책 revision, 환불 규칙, no-op 멱원 영수증을 추가했다.
- 체인 기본 정책과 지점별 `SET` 전체 재정의, append-only `INHERIT` 복귀 모델을 구현했다.
- 규칙은 1~10개이며 일수 0~30, 현지 마감 시각, 환불률 0~100을 검증한다. 임계 시각은
  엄격한 `이전` 조건이고 환불액은 `floor(예약 총액 × 환불률 / 100)`이다.
- 예약 생성 시 revision·scope·호텔 시간대·전체 규칙을 snapshot한다. 기존 snapshot은 기존
  단일 100% 규칙으로 해석해 호환한다.
- 고객·직원 취소 preview와 실제 fake/Toss 환불을 같은 서버 계산기로 통합했다. 0%는 PG 호출 없이
  취소·재고 반환만 수행하고, Toss 부분 환불은 최신 성공 거래부터 목표 금액까지만 배분한다.
- 정책 쓰기는 `HQ_ADMIN`과 `Idempotency-Key`를 요구한다. scope별 직렬화, 같은 키의 같은 요청 replay,
  다른 요청 409, 동일 정책 no-op 영수증을 구현했다. V65 이전 legacy 키도 같은 의미를 유지한다.
- 기존 단일 정책 요청이 만드는 legacy/new revision을 `legacy_revision_id`로 연결했다. 조회·예약
  snapshot·감사는 새 revision ID 하나를 사용하고 이력 중복을 제거한다.
- 관리자 공통 정책 화면에 지점 선택, 상속/재정의 상태, 환불 규칙 편집, 상속 복귀를 추가했다.
  저장 재시도는 같은 UUID 멱원 키를 유지한다.
- 통합 감사에 `POLICY_CHANGE`를 추가해 체인·지점, `SET`·`INHERIT`, 처리 직원과 시각을 조회한다.

## 검증 결과

- API: `mvn -DskipTests compile` 통과.
- API 대상 Spring 테스트: 5개 suite, 97건 통과(실패·오류·건너뜀 0건).
  `PolicyIntegrationTest` 40건, `SavedCancellationPolicyTest` 2건,
  `CancellationIntegrationTest` 15건, `TossPaymentAdjustmentIntegrationTest` 23건,
  `AuditIntegrationTest` 17건이다.
- PostgreSQL 16 테스트 DB에서 Flyway migration 65개 검증과 V65 적용을 완료했다.
- 관리자: `npx tsc --noEmit` 통과. 변경 파일 대상 ESLint는 오류 0건이며
  기존 effect 안 상태 갱신 패턴 경고 3건이 남아 있다.
- 관리자 정책 Playwright: 16건 통과. 지점 선택, 재정의·상속, 멱원 재시도, Escape·키보드,
  390px 화면을 포함한다.
- 고객 웹: `npm run build`와 변경된 예약 조회·취소·Toss Playwright 24건이 통과했다.
- `git diff --check` 통과(LF→CRLF 안내만 존재).
- planner·architect·TDD 관점 검토 후 code reviewer가 발견한 legacy 멱원 경계, 이력 중복,
  no-op replay, 부분 환불 재호출, revision 정렬·식별자 문제를 수정하고 회귀 테스트를 추가했다.

## 검증 환경 메모

- 최초 Maven 실행은 Codex 샌드박스가 작업공간 밖 사용자 Maven 캐시의 JAR 정리 접근을 제한해
  testCompile 종료 단계에서 실패했다. Maven 캐시 접근이 허용된 외부 실행에서는 같은 JAR로
  컴파일·테스트가 정상 동작해 의존성 손상이나 프로젝트 설정 오류가 아님을 확인했다.
- 첫 실제 테스트에서 기존 Toss fixture 3건이 추가 결제 10만 원을 만들고 예약 총액은 20만 원으로
  남겨 둔 불일치를 발견했다. 전액 환불 시나리오의 예약 총액을 30만 원으로 수정한 뒤 모두 통과했다.

## 미검증 항목

- 실제 Toss 라이브 결제와 운영 DB migration 적용은 이번 로컬 구현 검증 범위에 포함하지 않았다.

## 다음 작업

- 운영 배포 전 staging에서 기존 예약 snapshot, 부분 환불, 정책 감사의 실제 DB/API 흐름을 확인한다.
