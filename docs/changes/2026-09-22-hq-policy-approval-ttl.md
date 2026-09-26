# 본사 공통 정책 - 예약 변경 승인 TTL 런타임 변경 (2026-09-22)

> 상태: 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

`admin-menu-roadmap.md` 5번 `공통 정책 관리`에서 남은 "승인 TTL 런타임 변경"을 구현했다. 지점이 본사 승인이 필요한 예약 변경을 요청하면 `reservation_change_request.approval_expires_at`에 승인 만료 시각이 저장되는데, **이 시간을 결정하는 `reservation.change.approval-ttl`이 `application.yml`에 고정돼 있어서 재배포하지 않으면 바꿀 수 없었다.** 취소 정책과 승인 한도는 이미 런타임 변경이 가능했으므로 같은 패턴으로 통일했다.

## 서버 변경

- `PUT /api/staff/policies/change-approval-ttl`이 본문 `{"approvalTtlSeconds": ...}`로 승인 TTL을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400이다.
- **TTL은 60초 이상 7일(604,800초) 이하**다. 위반은 400이고 revision을 남기지 않는다. 60초보다 짧으면 승인자가 화면을 떠나는 동안 만료되고, 7일보다 길면 고객이 응답을 사실상 기다릴 수 없다.
- **진행 중인 변경 요청은 영향을 받지 않는다.** `approval_expires_at`은 요청 생성 시점에 한 번 저장되므로, TTL을 바꿔도 이미 만들어진 요청의 만료 시각은 그대로다. 신규 요청부터 새 TTL이 적용된다.
- `ReservationChangePolicy.approvalTtl()`이 `application.yml` 고정값 대신 `CurrentPolicy.changeApprovalTtlSeconds()`를 읽는다. 본사가 한 번도 변경하지 않았으면 `policy_revision`에 해당 키의 행이 없으므로 `application.yml`의 24h를 그대로 쓴다.
- `CurrentPolicy`가 빈 주입 시점에 `application.yml`의 `approval-ttl`을 한 번만 읽는다. `ReservationChangePolicy`가 호출마다 DB를 조회하므로 TTL 변경이 즉시 반영된다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·정책 키·`value_seconds`의 SHA-256 지문이 같아 같은 결과를 돌려준다. 정책 키 단위 `pg_advisory_xact_lock`으로 동시 변경을 직렬화한다.
- V57 additive 마이그레이션이 `policy_revision.value_seconds BIGINT`를 추가한다. 기존 행은 모두 NULL이므로 기존 동작을 변경하지 않는다.
- `PolicyView`에 `changeApprovalTtlSeconds`가 추가됐고, `GET /api/staff/policies/revisions`가 TTL 변경을 "예약 변경 승인 TTL 1시간 3,600초" 형태로 요약한다.

## 관리자 UI 변경

- `/dashboard/policies`의 예약 변경 승인 카드에 `본사 승인 대기 시간` 행과 `TTL 변경` 버튼을 추가했다.
- **본사는 시간 단위로 입력하고 저장은 초 단위**다. 24시간 = 86,400초. 서버가 초 단위로 검증하므로 시간→초 변환만 클라이언트가 한다.
- 요금·한도 입력과 같은 이유로 `type="text"` + `inputMode="numeric"`을 썼다. 브라우저가 `max` 초과로 폼 제출을 막으면 서버 검증 응답이 도달하지 않으므로 범위 판단은 서버에 뒀다.
- 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- 이력 표의 정책 유형 표시가 하드코딩된 두 가지에서 `policyRevisionLabels` 매핑으로 바뀌어 TTL 행이 "예약 변경 승인 TTL"로 표시된다.

## 검증 결과

- API `mvnw -Dtest=PolicyIntegrationTest` **30건**(기존 22 + 신규 8)이 종료 코드 0이다. 새 검증은 TTL 조회 기본 86,400·변경 201·즉시 현재값 반영·`approvalExpiresAt`이 120초 TTL을 따르는지·같은 키 재호출 200 `created=false`·새 키 같은 내용 200·59초·604,801초 400·키 누락 400·지점 직원 403·이력 요약 3종이다.
- 인접 영역 `ReservationChangeApprovalIntegrationTest` 3건이 종료 코드 0이다. TTL이 예약 변경 승인 동선에 영향을 주지 않는다.
- 관리자 `tsc --noEmit`이 종료 코드 0이고, `eslint`는 경고만 낸다(`react-hooks/set-state-in-effect`, 기존 컴포넌트와 같은 패턴).
- Playwright `policies` **13건**(기존 11 + 신규 2)이 종료 코드 0이다.

## 발견·수정한 결함

1. `PolicyQueryService.revisions`의 SELECT가 `value_seconds` 컬럼을 선택하지 않았다. V57로 컬럼이 추가된 뒤 TTL revision이 생기면 `BadSqlGrammarException`으로 500이 났다. 테스트를 먼저 통과시키고 원인을 확인했다. SELECT에 컬럼을 추가해서 고쳤다.
2. `ReservationChangePolicy.approvalExpiresAt`가 바꾼 TTL이 아니라 빈 주입 시점의 고정 필드를 여전히 읽었다. `approvalTtl()` 메서드를 호출하게 바꿔 DB의 현재값을 읽도록 고쳤다. 이것이 빠졌으면 TTL 변경이 예약 변경 요청의 만료 시각에 반영되지 않았을 것이다.
3. `directLimitKrw()`의 여는 괄호가 다음 줄과 병합돼 있었다. 컴파일은 되지만 diff가 불분명해져 분리했다.

## 미검증 항목

- 라이브 DB·compose `api` 재빌드·브라우저 확인은 실행하지 않았다. V57 마이그레이션이 라이브 DB에 적용되는지, TTL 변경이 진짜 예약 변경 요청의 만료 시각에 반영되는지는 사용자가 확인한다.
- `hold-ttl`(예약 홀드 15분)은 여전히 `application.yml` 고정값이다. 홀드는 고객 결제 대기 시간이라 본사 승인 정책과 성격이 달라 이번 범위에서 뺐다.
- 환불 규칙·지점별 정책·정책 변경의 감사 이력(7번 메뉴) 통합은 다음 단계다.
