# 고객 요청 감사 통합·XLSX 내보내기 계획 (2026-09-27)

## 배경

고객 요청은 `guest_request_event`에 생성과 상태 전환을 남기지만 통합 감사 메뉴에서는 보이지 않는다. 담당자 변경은 현재 요청 행에만 남고, 우선순위 변경 명령과 전후값 이력은 없다. 기존 값만 감사 조회에 붙이면 과거 시점의 담당자·우선순위를 현재값으로 왜곡하게 된다.

감사 CSV는 현재 화면의 최대 20건을 10개 열로 내려준다. XLSX도 같은 필터·마스킹·페이지·행 순서·열 순서를 사용해 두 파일의 의미를 일치시킨다. 전체 결과 내보내기로 범위를 넓히지 않는다.

## 사용자 여정

1. 고객이 요청을 만들면 본사 감사 메뉴에서 고객 행위자의 생성 이력을 확인한다.
2. 직원이 담당자·우선순위·상태를 바꾸면 각 변경을 독립된 감사 이력으로 확인한다.
3. 본사 관리자는 기존 예약·객실·정책 감사와 고객 요청 감사를 한 최신순 목록에서 조회하고 페이지를 이동한다.
4. 본사 관리자가 개인정보 마스킹을 켜면 고객 이름은 가려지고, 제목·본문·연락처·비고는 요약과 파일에 포함되지 않는다.
5. 본사 관리자는 현재 페이지를 기존 CSV 또는 XLSX로 내려받고 같은 10개 열과 값을 확인한다.
6. 지점 직원은 기존처럼 통합 감사 조회와 내보내기를 사용할 수 없다.

## 데이터·API 설계

- 적용된 `V58__guest_request.sql`은 수정하지 않는다.
- V66은 `guest_request_event`에 이전·이후 담당자와 우선순위 snapshot 열을 additive하게 추가하고 `PRIORITY_CHANGED` 이벤트를 허용한다.
- V66은 `(request_id, idempotency_key)` 명령 영수증도 추가한다. 요청 hash가 같은 재전송은 변경을 반복하지 않고, 같은 키의 다른 본문은 409로 거부한다.
- 마이그레이션 이후 생성되는 이벤트부터 담당자·우선순위의 완전한 전후값을 보장한다. 기존 이벤트를 현재 요청값으로 거짓 backfill하지 않는다.
- 고객 요청 명령은 상태가 같아도 담당자나 우선순위가 바뀌면 요청과 이벤트를 같은 트랜잭션에서 갱신한다. 같은 값 재요청은 중복 이벤트를 만들지 않는다.
- 변경 전 요청 행을 `FOR UPDATE`로 잠가 동시 명령이 같은 과거값에서 갈라지지 않게 한다.
- 담당자 변경은 `ASSIGNED`, 우선순위 변경은 `PRIORITY_CHANGED`, 상태 변경은 `STATUS_CHANGED`로 각각 기록한다.
- 통합 감사에는 `GUEST_REQUEST_EVENT` 유형을 추가한다. 생성 actor는 `CUSTOMER`, 직원 변경은 실제 직원으로 표현한다.
- 감사 요약에는 요청 ID·요청 유형·담당자·우선순위·상태 enum만 넣고 고객 제목·본문·이메일·전화번호·비고는 넣지 않는다.
- 각 UNION 분기의 원본 UUID를 내부 정렬 키로 투영해 `created_at DESC` 뒤에도 총순서를 보장한다. 공개 CSV 열은 늘리지 않는다.
- V66은 전역 감사 조회용 `guest_request_event(created_at DESC, id DESC)` covering index를 추가한다. 기존 요청 상세용 인덱스는 유지한다.
- `GET /api/staff/audit/export.xlsx`는 조회 API와 같은 `reservationId`·`hotelId`·`from`·`to`·`masked`·`limit`·`offset`을 받는다. SELECT만 사용하고 `no-store`로 응답한다.

## CSV·XLSX 계약

- 열 순서는 기존과 동일하다: 발생 시각, 유형, 처리 직원, 처리 직원 이메일, 역할, 지점, 예약 id, 고객, 객실, 내용.
- CSV의 UTF-8 BOM·CRLF·파일명 규칙을 유지한다.
- XLSX도 현재 페이지의 동일 행만 내보낸다.
- `=`, `+`, `-`, `@`로 시작하는 외부 입력은 CSV 수식으로 실행되지 않도록 작은따옴표를 붙이고 XLSX에도 같은 안전값을 기록한다. 일반 값은 바꾸지 않는다.
- 모든 XLSX 셀은 명시적인 문자열 셀이고 수식 셀을 만들지 않는다.

## TDD 순서와 완료 기준

1. `GuestRequestIntegrationTest`에 담당자·우선순위·상태 독립 이력, 같은 값 no-op, 잘못된 담당자·우선순위 롤백 테스트를 먼저 추가하고 RED를 확인한다.
2. `AuditIntegrationTest`에 고객 생성·담당자·우선순위·상태 이력, actor·마스킹·필터·최신순·동일 시각 안정 정렬·읽기 전용·XLSX 권한/필터 테스트를 추가하고 RED를 확인한다.
3. `AuditEventsXlsxServiceTest`에 실제 OOXML, 기존 CSV 10열, 행 순서·빈값·한글·수식 주입 방어 일치 테스트를 추가하고 RED를 확인한다.
4. 관리자 `audit.spec.ts`에 네 이벤트 라벨, `offset=20`, 페이지 이동 중 마스킹 유지, CSV 계약, XLSX 다운로드와 390px 동작을 추가하고 RED를 확인한다.
5. 최소 구현 후 같은 대상 테스트를 GREEN으로 만든다.
6. 조회 쿼리와 V66 인덱스를 PostgreSQL 16에서 확인하고 서버·관리자 빌드를 완료한다.
7. TDD 증거와 검증 결과를 변경 기록에 남긴다.

## 검증 명령

```powershell
services\api\mvnw.cmd -f services/api/pom.xml "-Dtest=GuestRequestIntegrationTest,AuditIntegrationTest,AuditEventsXlsxServiceTest" test
```

```powershell
cd SDTPL_ADM
node node_modules/typescript/bin/tsc --noEmit
npm.cmd run test:e2e -- e2e/audit.spec.ts e2e/guest-requests.spec.ts --project=chromium
npm.cmd run build
```

변경 파일 ESLint는 프로젝트 flat config가 정상 로드될 때 실행한다. 설정 자체가 실패하면 코드 검증과 구분해 기록한다.
