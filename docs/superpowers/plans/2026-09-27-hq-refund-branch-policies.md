# 본사 환불 규칙·지점별 정책·감사 통합 계획 (2026-09-27)

> 상태: 구현 완료, 검증 결과 기록. 기준 문서는 `docs/plans/admin-menu-roadmap.md` 5번이다.

## 변경 이유

현재 취소 정책은 체인 전역의 단일 전액 환급 마감만 지원한다. 신규 예약은 마감 일수·시각을
`reservation.policy_snapshot`에 저장하지만, 마감 뒤 부분 환불 규칙과 지점별 예외는 없고 정책
변경 이력도 공통 정책 화면에서만 보여 통합 감사 메뉴에서 확인할 수 없다.

## 설계 결정

1. 지점 재정의 대상은 취소·환불 정책이다. 예약 변경 승인 한도와 승인 TTL은 체인 전역을 유지한다.
2. 환불 정책은 최대 10개의 규칙으로 구성한다. 각 규칙은 `daysBefore`(0~30),
   `cutoffLocalTime`(`HH:mm`), `refundPercent`(0~100)를 가진다.
3. 같은 임계 시각은 중복할 수 없다. 더 일찍 취소할수록 환불률이 낮아질 수 없다. 적어도 한 규칙은
   100%여야 한다.
4. 호텔 현지 체크인 시각을 기준으로 아직 지나지 않은 임계 중 현재 시각과 가장 가까운 규칙을 적용한다.
   일치 규칙이 없으면 취소할 수 없다. 0% 규칙은 외부 환불 없이 취소와 재고 반환만 허용한다.
5. 지점 정책은 필드별 병합하지 않는다. 최신 지점 revision이 `SET`이면 전체 문서를 재정의하고,
   `INHERIT`이거나 revision이 없으면 최신 체인 정책을 사용한다.
6. 기존 예약은 저장된 `policy_snapshot`만 사용한다. 새 snapshot에는 적용 revision ID, scope,
   timezone, 전체 환불 규칙을 저장한다. 새 필드가 없는 기존 snapshot은 기존 전액 환급 마감 규칙으로
   해석한다.
7. 가격·취소 가능 여부·환불액은 Spring Boot만 계산한다. 고객·관리자 UI는 preview 결과만 안내한다.
8. KRW 환불액은 `floor(totalKrw * refundPercent / 100)`이고 0~예약 총액 범위를 벗어날 수 없다.
9. Toss 환불은 계산된 목표액만 기존 환불 가능 거래에 결정적 순서로 배분한다. 잔액 부족은 외부 호출
   전에 거부하고, 0원 환불은 PG를 호출하지 않는다.

## 데이터 모델

- `cancellation_policy_revision`: `id`, nullable `hotel_id`, `action(SET|INHERIT)`, scope별 revision 번호,
  `staff_id`, `idempotency_key`, `request_hash`, `legacy_revision_id`, `created_at`을 저장한다.
- `cancellation_refund_rule`: revision별 규칙 순서, 일수, 현지 마감 시각, 환불률을 저장한다.
- `cancellation_policy_noop_command`: 같은 정책을 다른 멱원 키로 다시 요청한 no-op 결과와 당시 규칙을
  보존한다. 이후 정책이 바뀌어도 해당 키의 재호출은 원래 결과를 반환한다.
- 기존 `policy_revision`과 기존 예약 snapshot은 수정하지 않는다. 새 체인 revision이 없으면 최신 기존
  취소 정책을 100% 단일 규칙으로 변환하고, 그것도 없으면 설정 기본값을 쓴다.
- 정책 변경은 scope별 advisory lock으로 직렬화한다. 같은 scope·멱원 키의 같은 요청은 기존 결과를,
  다른 요청은 409를 반환한다. 새 키의 본문이 현재 정책과 같으면 새 revision 없이 no-op 결과를 준다.
- 기존 단일 정책 요청은 `policy_revision`과 새 revision을 함께 기록하고 `legacy_revision_id`로 연결한다.
  조회·예약 snapshot·감사는 새 revision ID를 기준으로 하며, V65 이전 멱원 키도 같은 의미를 유지한다.

## API 계약

- `GET /api/staff/policies`: 기존 체인 응답을 호환 확장한다.
- `GET /api/staff/policies?hotelId={id}`: 체인 정책, 지점 revision, 최종 유효 정책과 상속 상태를 반환한다.
- `PUT /api/staff/policies/cancellation`: 체인 취소·환불 전체 문서를 변경한다. 기존 두 필드 요청도
  100% 단일 규칙으로 계속 받을 수 있게 한다.
- `PUT /api/staff/policies/hotels/{hotelId}/cancellation`: 지점 전체 정책을 재정의한다.
- `PUT /api/staff/policies/hotels/{hotelId}/cancellation/inherit`: 지점을 체인 상속으로 복귀시키는
  `INHERIT` revision을 append한다.
- 모든 변경은 `HQ_ADMIN`과 `Idempotency-Key`가 필요하다. 없는 지점은 404, 잘못된 규칙은 400이다.

## 구현 순서

1. 정책 API·예약 snapshot·preview/취소·감사·관리자 UI의 실패 테스트를 추가한다.
2. additive Flyway migration과 정책 resolver/계산기를 구현한다.
3. 체인 변경, 지점 재정의, 상속 복귀 API와 멱원 충돌을 구현한다.
4. 예약 생성 snapshot과 고객·직원 preview, fake/Toss 취소 실행을 같은 계산기에 연결한다.
5. 관리자 공통 정책 화면에 지점 선택, 상속 상태, 규칙 편집과 상속 복귀를 추가한다.
6. 감사 메뉴에 `POLICY_CHANGE`를 추가하고 체인·지점·SET·INHERIT 요약을 노출한다.
7. 고객 예약 상세의 정책 안내를 규칙 목록과 서버 preview에 맞춘다.
8. 직접 테스트, 타입·빌드, 키보드·390px 검증 뒤 변경 기록과 현재 상태를 갱신한다.

## 완료 기준

- 체인 기본값, 지점 재정의, 상속 복귀가 신규 예약 snapshot에 정확히 반영된다.
- 정책 변경 전 예약의 snapshot과 환불 결과는 바뀌지 않는다.
- 고객/직원 preview, fake 취소, Toss 환불 명령의 금액이 같은 규칙과 revision에서 나온다.
- 0원 환불은 PG 호출 없이 예약 상태와 재고를 정확히 한 번 변경한다.
- 기존 단일 전액 환급 정책과 기존 API 호출은 동일하게 동작한다.
- 동일 멱원 키 재호출은 중복 revision을 만들지 않고 다른 본문은 409다.
- 통합 감사 메뉴에서 체인·지점 정책 변경과 처리 직원·시각을 확인할 수 있다.
- 관리자 UI는 키보드로 선택·대화상자·저장이 가능하고 390px에서 가로 잘림 없이 사용 가능하다.
- 변경 범위의 Spring 통합 테스트, 관리자 Playwright, TypeScript 검사와 필요한 고객 웹 검사가 통과한다.

## 제외 범위

- 예약 변경 승인 한도·TTL의 지점별 재정의
- 통화별 반올림 정책과 정액 취소 수수료
- 실제 라이브 Toss 거래 생성과 과금
