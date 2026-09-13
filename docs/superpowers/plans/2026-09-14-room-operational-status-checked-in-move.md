# 실제 객실 점검·판매 중지와 투숙 중 객실 이동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제 객실의 점검 필요·판매 중지를 청소 상태와 분리하고, 체크인한 고객을 같은 객실 유형의 안전한 객실로 원자적으로 이동한다.

**Architecture:** `physical_room`에는 현재 운영 상태와 낙관적 version을 두고 모든 전환은 append-only 감사 테이블에 기록한다. 객실 운영 API는 지점 권한과 영향 배정을 서버에서 판정하며, 투숙 중 이동 서비스는 예약과 두 객실을 잠가 배정 교체·기존 객실 청소/점검 전환·감사를 한 트랜잭션으로 처리한다. 관리자에서는 기존 오늘의 운영 화면과 예약 상세에만 기능을 연결한다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, JdbcTemplate, PostgreSQL 16, Flyway, JUnit 5, AssertJ, MockMvc, Next.js 16.2.7, React 19, TypeScript, shadcn/ui, Playwright

**Spec:** `docs/superpowers/specs/2026-09-14-room-operational-status-checked-in-move-design.md`

## Global Constraints

- 고객 웹 `4000`, 관리자 `4001`, API `4080`, AI `9000`의 확정 포트를 바꾸지 않는다.
- 구현은 `codex/room-operational-status` 전용 worktree에서 수행하고 완료 후 로컬 `main`에 merge한다.
- 현재 실행 중인 관리자 `4001` 프로세스를 중지하거나 다른 관리자 포트·프로세스로 대체하지 않는다.
- 객실 유형별 `inventory_night`와 실제 객실 `physical_room` 상태를 분리하며 운영 상태 전환으로 판매 재고를 자동 증감하지 않는다.
- `BRANCH_STAFF`는 자기 지점만, `HQ_ADMIN`은 모든 지점을 서버에서 검증한다.
- 운영 불가 상태의 사유와 투숙 중 이동 사유는 필수다.
- 실제 사용자 예약·객실에는 명시적 승인 없이 mutation을 실행하지 않는다.
- 기존 `.tmp/`와 사용자 변경은 열거나 수정하거나 삭제하지 않는다.

## File Structure

- `services/api/src/main/resources/db/migration/V36__physical_room_operational_status.sql`: legacy `OUT_OF_SERVICE` 이관, 현재 상태 컬럼과 두 감사 테이블.
- `services/api/src/main/java/team/hotelchain/operations/RoomOperationsService.java`: 객실 운영 조회·상태 전환·영향 배정 판정.
- `services/api/src/main/java/team/hotelchain/operations/RoomOperationsView.java`: 객실 상태, 영향 예약과 최근 이벤트 읽기 계약.
- `services/api/src/main/java/team/hotelchain/operations/RoomOperationalTransitionRequest.java`: 상태 전환 입력.
- `services/api/src/main/java/team/hotelchain/operations/RoomOperationalTransitionResult.java`: 상태 전환 결과.
- `services/api/src/main/java/team/hotelchain/operations/RoomHasActiveAssignmentsException.java`: 영향 예약을 포함하는 409 오류.
- `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveService.java`: 후보 조회와 투숙 중 원자 이동.
- `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveOptions.java`: 현재 객실·후보 계약.
- `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveRequest.java`: 기존·신규 객실과 사유 입력.
- `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveResult.java`: 이동 감사 결과.
- `services/api/src/main/java/team/hotelchain/operations/StaffOperationsController.java`: 네 개 신규 endpoint 연결.
- `services/api/src/main/java/team/hotelchain/operations/StaffOperationsService.java`: 신규 배정·체크인에서 운영 상태 재검증.
- `services/api/src/main/java/team/hotelchain/operations/StaffRoomReassignmentService.java`: 체크인 전 후보·실행에서 운영 상태 재검증.
- `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java`: 영향 배정 409 body 매핑.
- `services/api/src/test/java/team/hotelchain/operations/RoomOperationalStatusIntegrationTest.java`: 상태·영향·권한·경합 계약.
- `services/api/src/test/java/team/hotelchain/operations/CheckedInRoomMoveIntegrationTest.java`: 투숙 중 이동·롤백·멱등·경합 계약.
- `services/api/src/test/java/team/hotelchain/operations/StaffOperationsIntegrationTest.java`: 기존 배정·체크인·청소 회귀 보강.
- `services/api/src/test/java/team/hotelchain/operations/StaffRoomReassignmentIntegrationTest.java`: 운영 불가 후보 제외 회귀.
- `SDTPL_ADM/src/lib/staff-api.ts`: 신규 TypeScript 계약과 fetch 함수.
- `SDTPL_ADM/src/components/hotel-admin/room-operations-panel.tsx`: 객실 운영 요약·목록·전환 dialog.
- `SDTPL_ADM/src/components/hotel-admin/checked-in-room-move.tsx`: 투숙 중 객실 이동 form.
- `SDTPL_ADM/src/components/hotel-admin/daily-operations.tsx`: 객실 운영 panel 연결.
- `SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx`: `CHECKED_IN` 상세에 이동 연결.
- `SDTPL_ADM/e2e/room-operational-status.spec.ts`: 운영 상태 UI와 영향 차단 회귀.
- `SDTPL_ADM/e2e/checked-in-room-move.spec.ts`: 투숙 중 이동 UI 회귀.
- `docs/changes/2026-09-14-room-operational-status-checked-in-move.md`: 구현·검증·미검증·롤백 기록.
- `docs/overview/current-development-context.md`: 현재 상태와 다음 작업 갱신.

---

### Task 1: Isolated worktree와 V36 상태 기반

**Files:**
- Create: `.worktrees/room-operational-status/`
- Create: `services/api/src/main/resources/db/migration/V36__physical_room_operational_status.sql`
- Create: `services/api/src/test/java/team/hotelchain/operations/RoomOperationalStatusIntegrationTest.java`

**Interfaces:**
- Consumes: V7 `physical_room`, V31 `reservation_room_assignment_change`, 기존 `staff_member`·`reservation`.
- Produces: `physical_room.operational_status`, `operational_reason`, `expected_recovery_at`, `operational_version`, `operational_updated_at`; `physical_room_operational_event`; `checked_in_room_move`.

- [ ] **Step 1: 실행용 worktree 생성**

`using-git-worktrees` skill을 읽고 루트가 깨끗하며 `.worktrees`가 ignore되는지 확인한 뒤 실행한다.

```powershell
git check-ignore .worktrees
git worktree add .worktrees/room-operational-status -b codex/room-operational-status main
git -C .worktrees/room-operational-status status --short --branch
```

Expected: 새 worktree는 `codex/room-operational-status`, 출력에 사용자 파일 변경이 없다.

- [ ] **Step 2: V36 schema가 없어서 실패하는 통합 테스트 작성**

`RoomOperationalStatusIntegrationTest`에 fresh migration 뒤 컬럼·제약·기본값을 검사하는 테스트를 작성한다.

```java
@Test
void exposesSeparateHousekeepingAndOperationalStateSchema() {
    UUID roomId = UUID.randomUUID();
    jdbc.update("insert into physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status) values (?, ?, ?, '901', 'CLEAN')",
            roomId, HOTEL, ROOM_TYPE);

    assertThat(jdbc.queryForMap("""
            select housekeeping_status, operational_status, operational_reason,
                   expected_recovery_at, operational_version
            from physical_room where id = ?
            """, roomId))
            .containsEntry("housekeeping_status", "CLEAN")
            .containsEntry("operational_status", "AVAILABLE")
            .containsEntry("operational_version", 0L)
            .containsEntry("operational_reason", null)
            .containsEntry("expected_recovery_at", null);
}
```

같은 클래스에 빈 사유의 운영 불가 상태와 legacy `housekeeping_status='OUT_OF_SERVICE'` 신규 입력이 DB 제약으로 거부되는 테스트를 추가한다.

- [ ] **Step 3: 테스트가 V36 부재로 실패하는지 확인**

```powershell
Set-Location services/api
.\mvnw.cmd -q '-Dtest=RoomOperationalStatusIntegrationTest' test
```

Expected: `operational_status` 컬럼 또는 새 테스트 클래스의 schema assertion 때문에 FAIL.

- [ ] **Step 4: V36 migration 작성**

`V36__physical_room_operational_status.sql`에 다음 구조를 구현한다.

```sql
ALTER TABLE physical_room
    ADD COLUMN operational_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN operational_reason VARCHAR(500),
    ADD COLUMN expected_recovery_at TIMESTAMPTZ,
    ADD COLUMN operational_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN operational_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE physical_room
SET operational_status = 'OUT_OF_SERVICE',
    operational_reason = '기존 판매 중지 상태 이관',
    housekeeping_status = 'NEEDS_CLEANING'
WHERE housekeeping_status = 'OUT_OF_SERVICE';

ALTER TABLE physical_room DROP CONSTRAINT physical_room_housekeeping_status_check;
ALTER TABLE physical_room ADD CONSTRAINT physical_room_housekeeping_status_check
    CHECK (housekeeping_status IN ('CLEAN', 'NEEDS_CLEANING'));
ALTER TABLE physical_room ADD CONSTRAINT physical_room_operational_status_check
    CHECK (operational_status IN ('AVAILABLE', 'INSPECTION_REQUIRED', 'OUT_OF_SERVICE'));
ALTER TABLE physical_room ADD CONSTRAINT physical_room_operational_reason_check CHECK (
    (operational_status = 'AVAILABLE' AND operational_reason IS NULL AND expected_recovery_at IS NULL)
    OR (operational_status <> 'AVAILABLE' AND NULLIF(BTRIM(operational_reason), '') IS NOT NULL)
);

CREATE TABLE physical_room_operational_event (
    id UUID PRIMARY KEY,
    physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    previous_status VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    previous_reason VARCHAR(500),
    reason VARCHAR(500),
    previous_expected_recovery_at TIMESTAMPTZ,
    expected_recovery_at TIMESTAMPTZ,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (physical_room_id, idempotency_key)
);

CREATE INDEX physical_room_operational_event_room_created_idx
    ON physical_room_operational_event (physical_room_id, created_at DESC);

CREATE TABLE checked_in_room_move (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    previous_physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    previous_room_number VARCHAR(30) NOT NULL,
    physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    room_number VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (NULLIF(BTRIM(reason), '') IS NOT NULL),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key)
);
```

- [ ] **Step 5: migration 직접 테스트 통과 확인**

```powershell
.\mvnw.cmd -q '-Dtest=RoomOperationalStatusIntegrationTest' test
```

Expected: PASS, 테스트 DB Flyway version 36.

- [ ] **Step 6: schema 작업 커밋**

```powershell
git add src/main/resources/db/migration/V36__physical_room_operational_status.sql src/test/java/team/hotelchain/operations/RoomOperationalStatusIntegrationTest.java
git commit -m "feat(operations): add physical room operational state"
```

---

### Task 2: 객실 운영 조회와 상태 전환 API

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/operations/RoomOperationsView.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/RoomOperationalTransitionRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/RoomOperationalTransitionResult.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/RoomHasActiveAssignmentsException.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/RoomOperationsService.java`
- Modify: `services/api/src/main/java/team/hotelchain/operations/StaffOperationsController.java`
- Modify: `services/api/src/main/java/team/hotelchain/web/ApiExceptionHandler.java`
- Modify: `services/api/src/test/java/team/hotelchain/operations/RoomOperationalStatusIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 schema, `StaffAccessService.current/requireHotel`, `ReservationAccess.sha256`.
- Produces: `RoomOperationsService.list(String, UUID)`, `transition(String, UUID, String, RoomOperationalTransitionRequest)`와 두 HTTP endpoint.

- [ ] **Step 1: 조회·전환·409 계약의 실패 테스트 작성**

다음 이름과 assertion으로 테스트 메서드를 추가한다.

- `listsRoomStateImpactAndRecentEventsForTheAuthorizedHotel`: 같은 지점 객실만 반환하고 summary 3개, 현재/미래 영향 예약과 최신 이벤트의 시간순 정렬을 검증한다.
- `marksInspectionRequiredAndRestoresOnlyAfterCleaning`: `AVAILABLE→INSPECTION_REQUIRED` 뒤 version 1·이벤트 1건을 확인하고, `NEEDS_CLEANING` 복구는 `ROOM_NOT_CLEAN`, 청소 완료 뒤 복구는 `AVAILABLE`·현재 사유 null·version 2인지 확인한다.
- `blocksOutOfServiceWhenCheckedInOrFutureConfirmedAssignmentsExist`: `CHECKED_IN`과 미래 `CONFIRMED`를 각각 seed해 `ROOM_HAS_ACTIVE_ASSIGNMENTS`와 DB 불변을 확인하며 과거 `CHECKED_OUT` 배정은 차단하지 않는지 확인한다.
- `replaysTheSameTransitionAndRejectsChangedPayloadActorOrVersion`: 동일 요청은 같은 결과·이벤트 1건, 다른 target/reason/직원은 `IDEMPOTENCY_CONFLICT`, 오래된 version은 `ROOM_OPERATIONAL_VERSION_CONFLICT`인지 확인한다.
- `rejectsAnotherHotelAndInvalidStatusWithoutWritingAnEvent`: 타 지점은 `StaffAccessDeniedException`, 허용 목록 밖 status·빈 사유·과거 예상 복구 시각은 `IllegalArgumentException`이며 event 0건인지 확인한다.
- `exposesRoomOperationsTransitionAndImpactConflictOverHttp`: GET 200의 summary/rooms와 POST 성공 200 및 영향 POST 409 body를 확인한다.

HTTP 충돌 테스트는 다음 body를 요구한다.

```java
mockMvc.perform(post("/api/staff/rooms/{roomId}/operational-transitions", ROOM)
        .header("X-Staff-Session", token)
        .header("Idempotency-Key", "stop-room-1")
        .contentType("application/json")
        .content("""
                {"targetStatus":"OUT_OF_SERVICE","reason":"누수 점검","expectedVersion":0}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_HAS_ACTIVE_ASSIGNMENTS"))
        .andExpect(jsonPath("$.assignments[0].reservationId").value(RESERVATION.toString()));
```

- [ ] **Step 2: 새 API가 없어 실패하는지 확인**

```powershell
.\mvnw.cmd -q '-Dtest=RoomOperationalStatusIntegrationTest' test
```

Expected: 신규 타입·endpoint 또는 service bean 부재로 FAIL.

- [ ] **Step 3: API record와 영향 오류 타입 구현**

```java
public record RoomOperationalTransitionRequest(
        String targetStatus,
        String reason,
        Instant expectedRecoveryAt,
        long expectedVersion) {}

public record RoomOperationalTransitionResult(
        UUID physicalRoomId,
        String roomNumber,
        String housekeepingStatus,
        String operationalStatus,
        String operationalReason,
        Instant expectedRecoveryAt,
        long operationalVersion) {}
```

`RoomOperationsView`에는 `hotelId`, `summary`, `rooms`를 두고 `RoomItem`에 `impactedAssignments`와 최근 `events`를 포함한다. `RoomHasActiveAssignmentsException`은 `BusinessConflictException`을 상속하고 `List<RoomOperationsView.ImpactedAssignment>`를 보관한다. `ApiExceptionHandler`는 이 타입을 일반 conflict handler보다 먼저 받아 HTTP 409의 `code`, `message`, `assignments`를 반환한다.

- [ ] **Step 4: 조회와 상태 전환 service 구현**

`RoomOperationsService.transition`은 다음 순서를 그대로 사용한다.

```java
StaffPrincipal staff = staffAccess.current(token);
LockedRoom room = lockRoom(roomId);
staffAccess.requireHotel(staff, room.hotelId());
String requestHash = requestHash(staff.id(), request);
ExistingEvent existing = existingEvent(roomId, idempotencyKey);
if (existing != null) return replayOrConflict(existing, requestHash);
requireVersion(room, request.expectedVersion());
requireTransition(room, request);
List<ImpactedAssignment> impacts = impactedAssignments(roomId);
if ("OUT_OF_SERVICE".equals(request.targetStatus()) && !impacts.isEmpty()) {
    throw new RoomHasActiveAssignmentsException(impacts);
}
updateRoomAndInsertEvent(room, staff, idempotencyKey, requestHash, request);
return result(roomId);
```

`AVAILABLE`은 `housekeeping_status='CLEAN'`과 trim된 필수 사유를 확인하되 DB에는 사유·예상 복구 시각을 `NULL`로 저장한다. `INSPECTION_REQUIRED`와 `OUT_OF_SERVICE`는 사유 1~500자, 과거가 아닌 선택적 예상 복구 시각을 받는다. 같은 상태로의 새 전환은 `ROOM_OPERATIONAL_STATUS_UNCHANGED`로 거부한다.

- [ ] **Step 5: controller endpoint 연결**

```java
@GetMapping("/hotels/{hotelId}/room-operations")
public RoomOperationsView roomOperations(@PathVariable UUID hotelId,
        @RequestHeader("X-Staff-Session") String token) {
    return roomOperations.list(token, hotelId);
}

@PostMapping("/rooms/{roomId}/operational-transitions")
public RoomOperationalTransitionResult transitionRoom(
        @PathVariable UUID roomId,
        @RequestHeader("X-Staff-Session") String token,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody RoomOperationalTransitionRequest request) {
    return roomOperations.transition(token, roomId, idempotencyKey, request);
}
```

- [ ] **Step 6: 대상 테스트 통과 확인**

```powershell
.\mvnw.cmd -q '-Dtest=RoomOperationalStatusIntegrationTest' test
```

Expected: 모든 상태·권한·409·멱등 테스트 PASS.

- [ ] **Step 7: 객실 운영 API 커밋**

```powershell
git add src/main/java/team/hotelchain/operations src/main/java/team/hotelchain/web/ApiExceptionHandler.java src/test/java/team/hotelchain/operations/RoomOperationalStatusIntegrationTest.java
git commit -m "feat(operations): manage physical room status"
```

---

### Task 3: 기존 배정·체크인 경로의 운영 상태 방어

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/operations/StaffOperationsService.java`
- Modify: `services/api/src/main/java/team/hotelchain/operations/StaffRoomReassignmentService.java`
- Modify: `services/api/src/test/java/team/hotelchain/operations/StaffOperationsIntegrationTest.java`
- Modify: `services/api/src/test/java/team/hotelchain/operations/StaffRoomReassignmentIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 `physical_room.operational_status`.
- Produces: 모든 신규 배정·체크인 전 재배정·체크인이 `AVAILABLE`만 허용하는 불변조건.

- [ ] **Step 1: 운영 불가 객실 방어 실패 테스트 작성**

```java
@Test
void excludesAndRejectsOperationallyUnavailableRooms() {
    jdbc.update("update physical_room set operational_status='INSPECTION_REQUIRED', operational_reason='소음 점검' where id=?", ROOM);
    assertThat(operations.assignableRooms(token, RESERVATION)).noneMatch(room -> room.id().equals(ROOM));
    assertThatThrownBy(() -> operations.assign(token, RESERVATION, ROOM))
            .isInstanceOf(BusinessConflictException.class)
            .extracting(error -> ((BusinessConflictException) error).code())
            .isEqualTo("ROOM_NOT_OPERATIONALLY_AVAILABLE");
}
```

`StaffRoomReassignmentIntegrationTest`에는 `INSPECTION_REQUIRED`와 `OUT_OF_SERVICE` 후보가 목록에서 빠지고 직접 실행도 같은 code로 거부되는 검사를 추가한다. `StaffOperationsIntegrationTest`에는 배정 뒤 객실이 점검 필요로 바뀌면 체크인이 거부되는 검사를 추가한다.

- [ ] **Step 2: 기존 경로가 운영 상태를 무시해 실패하는지 확인**

```powershell
.\mvnw.cmd -q '-Dtest=StaffOperationsIntegrationTest,StaffRoomReassignmentIntegrationTest' test
```

Expected: 운영 불가 객실이 후보에 남거나 배정/체크인이 성공해서 FAIL.

- [ ] **Step 3: 조회와 잠금 뒤 검증을 최소 수정**

후보 SQL마다 다음 조건을 추가한다.

```sql
AND p.housekeeping_status = 'CLEAN'
AND p.operational_status = 'AVAILABLE'
```

`StaffOperationsService.Room`과 `StaffRoomReassignmentService.Room`에 `operationalStatus`를 추가하고 객실 행을 잠근 뒤 다음 검증을 실행한다.

```java
if (!"AVAILABLE".equals(room.operationalStatus())) {
    throw new BusinessConflictException(
            "ROOM_NOT_OPERATIONALLY_AVAILABLE", "점검 또는 판매 중지 중인 객실은 배정할 수 없습니다.");
}
```

체크인은 배정된 모든 객실의 `housekeeping_status='CLEAN' AND operational_status='AVAILABLE'` 개수가 예약 객실 수와 같을 때만 허용한다. 청소 완료는 `housekeeping_status`만 갱신하고 운영 상태는 바꾸지 않는다.

- [ ] **Step 4: 직접·기존 회귀 통과 확인**

```powershell
.\mvnw.cmd -q '-Dtest=StaffOperationsIntegrationTest,StaffRoomReassignmentIntegrationTest' test
```

Expected: 신규 방어와 기존 배정·체크인·체크아웃·청소·재배정 테스트 PASS.

- [ ] **Step 5: 배정 방어 커밋**

```powershell
git add src/main/java/team/hotelchain/operations/StaffOperationsService.java src/main/java/team/hotelchain/operations/StaffRoomReassignmentService.java src/test/java/team/hotelchain/operations/StaffOperationsIntegrationTest.java src/test/java/team/hotelchain/operations/StaffRoomReassignmentIntegrationTest.java
git commit -m "fix(operations): exclude unavailable physical rooms"
```

---

### Task 4: 투숙 중 객실 이동 backend

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveOptions.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveResult.java`
- Create: `services/api/src/main/java/team/hotelchain/operations/CheckedInRoomMoveService.java`
- Modify: `services/api/src/main/java/team/hotelchain/operations/StaffOperationsController.java`
- Create: `services/api/src/test/java/team/hotelchain/operations/CheckedInRoomMoveIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 schema, Task 3의 `CLEAN + AVAILABLE` 후보 조건, `StaffAccessService`, `ReservationAccess.sha256`.
- Produces: `options(String, UUID)`, `move(String, UUID, String, CheckedInRoomMoveRequest)`와 GET/POST endpoint.

- [ ] **Step 1: 이동 성공·실패·경합의 실패 테스트 작성**

다음 이름과 assertion으로 테스트를 구현한다.

- `listsOnlyCleanAvailableSameTypeCandidates`: 같은 호텔·유형의 `CLEAN + AVAILABLE`만 후보이며 현재 배정, 다른 유형, 타 지점, dirty, 점검 필요, 판매 중지, 충돌 객실은 모두 제외한다.
- `movesOneCheckedInRoomAndMarksThePreviousRoomDirtyAndInspectionRequired`: 배정이 신규 객실 하나로 바뀌고 기존 객실의 두 상태, 운영 event 1건과 move audit 1건을 확인한다.
- `replaysTheSameMoveAndRejectsChangedRoomReasonOrActor`: 동일 요청은 같은 결과·감사 1건, 다른 room/reason/직원은 `IDEMPOTENCY_CONFLICT`인지 확인한다.
- `rejectsConfirmedCheckedOutWrongTypeDirtyUnavailableOrOccupiedTargets`: 예약 상태와 각 후보 오류 code를 확인하고 배정·객실·감사가 모두 불변인지 확인한다.
- `rollsBackAssignmentRoomStateAndAuditsWhenAnyWriteFails`: 중복 운영 event를 미리 seed해 감사 insert를 실패시키고 배정, 두 객실 상태와 move audit 0건을 확인한다.
- `allowsOnlyOneOfTwoCheckedInReservationsToClaimTheSameRoom`: latch로 동시에 시작해 결과가 `SUCCESS`와 `ROOM_ALREADY_ASSIGNED` 하나씩인지 확인한다.
- `rejectsAStaleCurrentAssignmentAfterAnotherMove`: 첫 이동 뒤 이전 current ID로 새 key 요청 시 `ROOM_ASSIGNMENT_NOT_FOUND`인지 확인한다.
- `checksOutAfterMoveAndMarksOnlyTheCurrentRoomForCleaning`: 이동 뒤 체크아웃해 신규 객실은 `NEEDS_CLEANING`, 기존 객실은 `NEEDS_CLEANING + INSPECTION_REQUIRED`를 유지하는지 확인한다.
- `exposesMoveOptionsAndMoveOverHttp`: GET 후보와 POST body/header 계약, 성공 JSON을 MockMvc로 확인한다.

성공 assertion은 다음 세 상태를 함께 확인한다.

```java
assertThat(jdbc.queryForObject("select physical_room_id from reservation_room_assignment where reservation_id=?", UUID.class, RESERVATION))
        .isEqualTo(NEW_ROOM);
assertThat(jdbc.queryForMap("select housekeeping_status, operational_status from physical_room where id=?", CURRENT_ROOM))
        .containsEntry("housekeeping_status", "NEEDS_CLEANING")
        .containsEntry("operational_status", "INSPECTION_REQUIRED");
assertThat(jdbc.queryForObject("select count(*) from checked_in_room_move where reservation_id=?", Integer.class, RESERVATION))
        .isEqualTo(1);
```

- [ ] **Step 2: 신규 service 부재로 실패 확인**

```powershell
.\mvnw.cmd -q '-Dtest=CheckedInRoomMoveIntegrationTest' test
```

Expected: 신규 타입 또는 bean 부재로 FAIL.

- [ ] **Step 3: 이동 계약 record 작성**

```java
public record CheckedInRoomMoveRequest(
        UUID currentPhysicalRoomId,
        UUID newPhysicalRoomId,
        String reason) {}

public record CheckedInRoomMoveResult(
        UUID reservationId,
        UUID previousPhysicalRoomId,
        String previousRoomNumber,
        UUID physicalRoomId,
        String roomNumber,
        String reason,
        Instant movedAt) {}
```

`CheckedInRoomMoveOptions`는 `reservationId`, `assignments`, `candidates`를 가지며 두 목록은 기존 `AssignableRoom`을 재사용한다.

- [ ] **Step 4: 예약·객실 잠금과 원자 이동 구현**

`CheckedInRoomMoveService.move`의 핵심 순서를 다음으로 고정한다.

```java
StaffPrincipal staff = staffAccess.current(token);
UUID newRoomId = validate(idempotencyKey, request);
LockedReservation reservation = lockReservation(reservationId);
staffAccess.requireHotel(staff, reservation.hotelId());
String hash = requestHash(staff.id(), request);
ExistingMove existing = existingMove(reservationId, idempotencyKey);
if (existing != null) return replayOrConflict(existing, hash);
requireCheckedIn(reservation);
Map<UUID, LockedRoom> rooms = lockRoomsInUuidOrder(request.currentPhysicalRoomId(), newRoomId);
requireCurrentAssignment(reservationId, request.currentPhysicalRoomId());
requireSameHotelAndType(reservation, rooms.get(newRoomId));
requireCleanAvailableAndUnoccupied(reservation, rooms.get(newRoomId));
replaceAssignment(reservationId, request.currentPhysicalRoomId(), newRoomId);
markPreviousRoomDirtyAndInspectionRequired(request.currentPhysicalRoomId(), staff, idempotencyKey, hash, request.reason());
insertMoveAudit(reservationId, rooms, staff, idempotencyKey, hash, request.reason());
return loadResult(reservationId, idempotencyKey);
```

객실 잠금 SQL은 `WHERE id IN (?, ?) ORDER BY id FOR UPDATE`를 사용한다. 기존 객실 운영 이벤트의 멱등 키는 `checked-in-move:<reservationId>:<idempotencyKey>`로 namespace해 수동 상태 전환 키와 충돌하지 않게 한다. 실패 주입 테스트는 이동용 운영 이벤트와 같은 `(physical_room_id, idempotency_key)`를 먼저 넣어 감사 insert 유일 제약 오류를 발생시키고, 배정과 객실 상태가 모두 원복되는지 확인한다.

- [ ] **Step 5: controller endpoint 연결**

```java
@GetMapping("/reservations/{reservationId}/checked-in-room-move-options")
public CheckedInRoomMoveOptions checkedInRoomMoveOptions(
        @PathVariable UUID reservationId,
        @RequestHeader("X-Staff-Session") String token) {
    return checkedInRoomMove.options(token, reservationId);
}

@PostMapping("/reservations/{reservationId}/checked-in-room-moves")
public CheckedInRoomMoveResult moveCheckedInRoom(
        @PathVariable UUID reservationId,
        @RequestHeader("X-Staff-Session") String token,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody CheckedInRoomMoveRequest request) {
    return checkedInRoomMove.move(token, reservationId, idempotencyKey, request);
}
```

- [ ] **Step 6: 이동과 기존 체크아웃 회귀 통과 확인**

```powershell
.\mvnw.cmd -q '-Dtest=CheckedInRoomMoveIntegrationTest,StaffOperationsIntegrationTest' test
```

Expected: 신규 이동·체크아웃 회귀 PASS.

- [ ] **Step 7: 투숙 중 이동 backend 커밋**

```powershell
git add src/main/java/team/hotelchain/operations src/test/java/team/hotelchain/operations/CheckedInRoomMoveIntegrationTest.java
git commit -m "feat(operations): move checked-in guests safely"
```

---

### Task 5: 관리자 API 계약

**Files:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`

**Interfaces:**
- Consumes: Task 2·4 HTTP JSON.
- Produces: `getRoomOperations`, `transitionRoomOperationalStatus`, `getCheckedInRoomMoveOptions`, `moveCheckedInRoom`, 선택적 `StaffApiError.details`.

- [ ] **Step 1: 정확한 TypeScript 타입과 함수 signature 추가**

```ts
export type RoomOperationalStatus = "AVAILABLE" | "INSPECTION_REQUIRED" | "OUT_OF_SERVICE";
export type RoomOperationsView = {
  hotelId: string;
  summary: { inspectionRequired: number; outOfService: number; overdueRecovery: number };
  rooms: Array<{
    physicalRoomId: string; roomNumber: string; roomTypeName: string;
    housekeepingStatus: "CLEAN" | "NEEDS_CLEANING";
    operationalStatus: RoomOperationalStatus; operationalReason: string | null;
    expectedRecoveryAt: string | null; operationalVersion: number;
    impactedAssignments: Array<{ reservationId: string; guestName: string; status: string; checkIn: string; checkOut: string }>;
  }>;
};
export type CheckedInRoomMoveOptions = { reservationId: string; assignments: AssignableRoom[]; candidates: AssignableRoom[] };
export type CheckedInRoomMoveResult = RoomReassignmentResult & { reason: string; movedAt: string };
export type RoomOperationalTransitionResult = {
  physicalRoomId: string; roomNumber: string;
  housekeepingStatus: "CLEAN" | "NEEDS_CLEANING";
  operationalStatus: RoomOperationalStatus; operationalReason: string | null;
  expectedRecoveryAt: string | null; operationalVersion: number;
};
```

- [ ] **Step 2: API 함수 구현 후 TypeScript 검사**

```ts
type RoomOperationsErrorPayload = ApiErrorPayload & {
  assignments?: RoomOperationsView["rooms"][number]["impactedAssignments"];
};

async function operationsRequest<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "X-Staff-Session": token,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as RoomOperationsErrorPayload;
    throw new StaffApiError(
      error.message ?? "객실 운영 요청을 처리하지 못했습니다.",
      response.status,
      error.code,
      { assignments: error.assignments ?? [] },
    );
  }
  return response.json() as Promise<T>;
}

export function getRoomOperations(token: string, hotelId: string) {
  return operationsRequest<RoomOperationsView>(`/api/staff/hotels/${hotelId}/room-operations`, token);
}

export function transitionRoomOperationalStatus(token: string, roomId: string, idempotencyKey: string, input: {
  targetStatus: RoomOperationalStatus; reason: string; expectedRecoveryAt: string | null; expectedVersion: number;
}) {
  return operationsRequest<RoomOperationalTransitionResult>(`/api/staff/rooms/${roomId}/operational-transitions`, token, {
    method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(input),
  });
}

export function getCheckedInRoomMoveOptions(token: string, reservationId: string) {
  return operationsRequest<CheckedInRoomMoveOptions>(
    `/api/staff/reservations/${reservationId}/checked-in-room-move-options`, token,
  );
}

export function moveCheckedInRoom(token: string, reservationId: string, idempotencyKey: string, input: {
  currentPhysicalRoomId: string; newPhysicalRoomId: string; reason: string;
}) {
  return operationsRequest<CheckedInRoomMoveResult>(
    `/api/staff/reservations/${reservationId}/checked-in-room-moves`, token,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(input) },
  );
}
```

기존 `StaffApiError` 생성자의 네 번째 선택 인자로 `readonly details?: { assignments?: RoomOperationsView["rooms"][number]["impactedAssignments"] }`를 추가한다. 기존 세 인자 호출은 그대로 동작해야 한다.

```powershell
Set-Location ..\SDTPL_ADM
pnpm exec tsc --noEmit
```

Expected: exit 0.

- [ ] **Step 3: 관리자 API 계약 커밋**

```powershell
git add src/lib/staff-api.ts
git commit -m "feat(admin): add room operations contracts"
```

---

### Task 6: 오늘의 운영 객실 상태 UI

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/room-operations-panel.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/daily-operations.tsx`
- Create: `SDTPL_ADM/e2e/room-operational-status.spec.ts`

**Interfaces:**
- Consumes: Task 5 room operations API.
- Produces: `/dashboard/operations`의 요약·필터·상태 전환·영향 차단 UI.

- [ ] **Step 1: UI가 없어 실패하는 Playwright 작성**

테스트는 branch session과 room operations API를 mock하고 다음을 검사한다.

```ts
await expect(page.getByRole("region", { name: "객실 운영 상태" })).toBeVisible();
await expect(page.getByText("점검 필요 1건")).toBeVisible();
await page.getByRole("button", { name: "702호 판매 중지" }).click();
await expect(page.getByText("영향 예약 1건")).toBeVisible();
await expect(page.getByRole("button", { name: "판매 중지 확정" })).toBeDisabled();
await expect(page.getByRole("link", { name: "김하늘 예약 보기" }))
  .toHaveAttribute("href", "/dashboard/reservations?date=2026-09-14&reservationId=reservation-1");
```

두 번째 테스트는 영향 없는 객실의 점검 요청이 `targetStatus`, trim된 사유, `expectedVersion`, 동일 재시도 key를 전송하고 성공 뒤 목록을 다시 조회하는지 검사한다. 세 번째는 390×844에서 dialog 가로 넘침, Escape 닫기와 trigger focus 복귀를 검사한다.

- [ ] **Step 2: UI 부재로 실패 확인**

```powershell
pnpm exec playwright test e2e/room-operational-status.spec.ts
```

Expected: `객실 운영 상태` region을 찾지 못해 FAIL. 실행은 기존 관리자 4001을 대체하지 않도록 아직 server를 띄우지 말고, branch 단계에서는 `pnpm exec tsc --noEmit`만 수행해도 된다. 실제 Playwright 실패/통과는 Task 8의 main merge 후 동일 4001에서 확정한다.

- [ ] **Step 3: 독립 panel 구현**

`RoomOperationsPanel`은 `hotelId`를 prop으로 받고 자체 조회 상태를 관리한다. 세 요약 badge, 운영 상태 filter, 객실 목록을 렌더링한다. 전환 dialog는 `Label`, `Textarea`, `Input type="datetime-local"`, `AlertDialog`를 재사용한다.

```tsx
<Card role="region" aria-label="객실 운영 상태" className="min-w-0">
  <CardHeader>
    <CardTitle>객실 운영 상태</CardTitle>
    <CardDescription>청소 상태와 별도로 점검·판매 중지를 관리합니다.</CardDescription>
  </CardHeader>
  <CardContent className="space-y-4">
    <div className="flex flex-wrap gap-2" aria-label="객실 운영 요약">
      <Badge variant="outline">점검 필요 {data?.summary.inspectionRequired ?? 0}건</Badge>
      <Badge variant="outline">판매 중지 {data?.summary.outOfService ?? 0}건</Badge>
      <Badge variant="outline">예상 복구 지연 {data?.summary.overdueRecovery ?? 0}건</Badge>
    </div>
    <ul className="space-y-2">
      {filteredRooms.map((room) => (
        <li key={room.physicalRoomId} className="rounded-xl border p-3">
          <p className="font-medium">{room.roomNumber}호 · {room.roomTypeName}</p>
          <p className="text-sm text-muted-foreground">{room.housekeepingStatus} · {room.operationalStatus}</p>
          <Button type="button" variant="outline" onClick={() => openTransition(room)}>상태 변경</Button>
        </li>
      ))}
    </ul>
  </CardContent>
</Card>
```

`data`는 `getRoomOperations` 응답, `filteredRooms`는 선택한 운영 상태와 일치하는 `data.rooms`의 `useMemo` 결과다. `openTransition(room)`은 선택 객실·target·빈 사유·새 UUID key를 state에 저장하고 dialog를 연다.

입력 또는 target이 바뀔 때 `crypto.randomUUID()`로 새 키를 만들고, 네트워크 실패에는 입력과 기존 키를 유지한다. 영향 예약이 있으면 `OUT_OF_SERVICE` 확정 버튼을 disabled하고 `/dashboard/reservations?reservationId=<id>` 링크를 제공한다.

- [ ] **Step 4: 기존 오늘의 운영 화면에 panel 연결**

`DailyOperations`의 지점 선택 아래, 기존 세 카드 위에 다음을 추가한다.

```tsx
{hotelId && <RoomOperationsPanel hotelId={hotelId} />}
```

기존 도착·출발·청소 카드의 API와 동작은 변경하지 않는다.

- [ ] **Step 5: 정적 검사 통과 확인**

```powershell
pnpm exec eslint src/components/hotel-admin/room-operations-panel.tsx src/components/hotel-admin/daily-operations.tsx e2e/room-operational-status.spec.ts
pnpm exec tsc --noEmit
```

Expected: exit 0, lint error 0.

- [ ] **Step 6: 객실 상태 UI 커밋**

```powershell
git add src/components/hotel-admin/room-operations-panel.tsx src/components/hotel-admin/daily-operations.tsx e2e/room-operational-status.spec.ts
git commit -m "feat(admin): manage room operational status"
```

---

### Task 7: 예약 상세 투숙 중 객실 이동 UI

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/checked-in-room-move.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx`
- Create: `SDTPL_ADM/e2e/checked-in-room-move.spec.ts`

**Interfaces:**
- Consumes: Task 5 checked-in move API, `StaffReservationSummary.assignedRoomNumbers`.
- Produces: `CHECKED_IN` 예약 상세의 후보·사유·명시적 확인·안전 재시도 흐름.

- [ ] **Step 1: 투숙 중 이동 UI 실패 테스트 작성**

```ts
await page.getByRole("button", { name: "김하늘 예약 상세" }).click();
await expect(page.getByRole("heading", { name: "투숙 중 객실 이동" })).toBeVisible();
await page.getByRole("button", { name: "이동 후보 조회" }).click();
await page.getByLabel("현재 객실").selectOption("room-701");
await page.getByLabel("새 객실").selectOption("room-702");
await page.getByLabel("이동 사유").fill("에어컨 소음");
await page.getByRole("button", { name: "객실 이동 확인" }).click();
await page.getByRole("button", { name: "객실 이동 확정" }).click();
await expect(page.getByText("702호로 이동했습니다.")).toBeVisible();
```

요청 body의 세 필드와 동일 key 재시도, 선택·사유 변경 시 새 key, 빈 사유 확정 비활성화, 후보 선점 409 뒤 form 유지, Escape·focus 복귀와 390px overflow를 함께 검사한다. 객실 운영 화면의 `/dashboard/reservations?date=2026-09-14&reservationId=reservation-1` 링크로 진입하면 해당 날짜를 조회하고 일치하는 예약 상세가 자동으로 열리는 deep-link도 검사한다.

- [ ] **Step 2: UI 부재 실패 확인**

Task 8의 동일 4001 merge 전에는 server를 추가로 띄우지 않는다. 우선 test discovery만 확인한다.

```powershell
pnpm exec playwright test e2e/checked-in-room-move.spec.ts --list
```

Expected: 테스트가 수집되고 exit 0. 실제 실패/통과는 Task 8에서 확인한다.

- [ ] **Step 3: `CheckedInRoomMove` 구현**

컴포넌트는 `reservation`, `onMoved`를 받고 후보 조회 전에는 button만 표시한다. 조회 후 현재·신규 객실 select, 필수 사유와 최종 `AlertDialog`를 표시한다.

```tsx
<section aria-labelledby="checked-in-room-move-title" className="space-y-3 border-b pb-4">
  <div>
    <h3 id="checked-in-room-move-title" className="text-sm font-medium">투숙 중 객실 이동</h3>
    <p className="mt-1 text-xs text-muted-foreground">같은 객실 유형의 청결하고 운영 가능한 객실로 이동합니다.</p>
  </div>
  {!options ? (
    <Button type="button" variant="outline" onClick={() => void loadOptions()}>이동 후보 조회</Button>
  ) : (
    <form className="space-y-3" onSubmit={(event) => void confirmMove(event)}>
      <Label htmlFor="checked-in-current-room">현재 객실</Label>
      <select id="checked-in-current-room" value={currentRoomId} onChange={onCurrentRoomChange} />
      <Label htmlFor="checked-in-new-room">새 객실</Label>
      <select id="checked-in-new-room" value={newRoomId} onChange={onNewRoomChange} />
      <Label htmlFor="checked-in-room-move-reason">이동 사유</Label>
      <Textarea id="checked-in-room-move-reason" value={reason} onChange={onReasonChange} />
      <Button type="submit" disabled={!currentRoomId || !newRoomId || !reason.trim()}>객실 이동 확인</Button>
    </form>
  )}
</section>
```

`loadOptions`는 Task 5 후보 API를 호출한다. 세 change handler는 해당 state를 갱신하고 새 UUID key를 발급하며, `confirmMove`는 submit 기본 동작을 막고 최종 `AlertDialog`만 연다. 확정 action에서만 `moveCheckedInRoom`을 호출한다.

성공하면 `onMoved(result)`를 호출하고 기존 객실이 청소·점검 필요로 바뀐다는 안내를 `role="status"`로 제공한다. 응답 유실은 동일 form과 key를 보존하며, 입력 변경만 새 key를 만든다.

- [ ] **Step 4: `CHECKED_IN` 예약 상세 연결**

`ReservationDetail`에서 기존 `CONFIRMED` 작업 영역과 별도로 다음을 렌더링한다.

```tsx
{reservation.status === "CHECKED_IN" && (
  <div className="flex flex-col gap-4 border-t pt-4">
    <CheckedInRoomMove reservation={reservation} onMoved={onCheckedInRoomMoved} />
  </div>
)}
```

callback은 선택 예약과 목록의 `assignedRoomNumbers`에서 이전 번호 하나를 새 번호로 교체한다. 일정·객실 유형·가격·결제는 바꾸지 않는다. 같은 파일에서 `useSearchParams()`의 `date`와 `reservationId`를 최초 조회 조건에 반영한다. 목록 응답에서 예약 ID가 일치하면 한 번만 상세를 열고, 사용자가 dialog를 닫은 뒤에는 같은 query로 자동 재개방하지 않는다.

- [ ] **Step 5: 정적 검사 통과 확인**

```powershell
pnpm exec eslint src/components/hotel-admin/checked-in-room-move.tsx src/components/hotel-admin/reservation-management.tsx e2e/checked-in-room-move.spec.ts
pnpm exec tsc --noEmit
```

Expected: exit 0, lint error 0.

- [ ] **Step 6: 투숙 중 이동 UI 커밋**

```powershell
git add src/components/hotel-admin/checked-in-room-move.tsx src/components/hotel-admin/reservation-management.tsx e2e/checked-in-room-move.spec.ts
git commit -m "feat(admin): move checked-in guests between rooms"
```

---

### Task 8: 회귀·문서·동일 4001 검증과 main merge

**Files:**
- Create: `docs/changes/2026-09-14-room-operational-status-checked-in-move.md`
- Modify: `docs/overview/current-development-context.md`
- Modify only if a regression proves necessary: Task 1~7 files.

**Interfaces:**
- Consumes: Task 1~7 전체 결과.
- Produces: 검증 증거, 운영/롤백 문서, 로컬 `main` merge와 계속 살아 있는 관리자 4001.

- [ ] **Step 1: backend 직접·인접 회귀 실행**

```powershell
Set-Location services/api
.\mvnw.cmd -q '-Dtest=RoomOperationalStatusIntegrationTest,CheckedInRoomMoveIntegrationTest,StaffOperationsIntegrationTest,StaffRoomReassignmentIntegrationTest,StaffReservationStayChangeIntegrationTest,ReservationChangeMutationGuardIntegrationTest,ReservationChangeApplyIntegrationTest,CancellationIntegrationTest' test
```

Expected: failures 0, errors 0. XML report에서 실제 tests 합계를 기록한다.

- [ ] **Step 2: 관리자 정적 release gate 실행**

```powershell
Set-Location ..\..\SDTPL_ADM
pnpm exec eslint src/components/hotel-admin/room-operations-panel.tsx src/components/hotel-admin/checked-in-room-move.tsx src/components/hotel-admin/daily-operations.tsx src/components/hotel-admin/reservation-management.tsx e2e/room-operational-status.spec.ts e2e/checked-in-room-move.spec.ts
pnpm exec tsc --noEmit
pnpm run build
```

Expected: lint error 0, TypeScript exit 0, Next production build 성공.

- [ ] **Step 3: worktree 변경 검토와 첫 main merge**

```powershell
git diff --check
git status --short --branch
git log --oneline main..HEAD
Set-Location ..\..
git status --short --branch
git merge --no-ff codex/room-operational-status -m "merge: room operational status and in-stay moves"
```

Expected: worktree clean, root에는 기존 `.tmp/`만 untracked, merge conflict 없음.

- [ ] **Step 4: 기존 관리자 4001을 재사용한 Chromium 회귀**

관리자 endpoint를 먼저 확인하고 기존 프로세스만 사용한다.

```powershell
$response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:4001/dashboard/operations' -TimeoutSec 10
$response.StatusCode
Set-Location SDTPL_ADM
pnpm exec playwright test e2e/room-operational-status.spec.ts e2e/checked-in-room-move.spec.ts e2e/daily-operations-actions.spec.ts e2e/staff-reservations.spec.ts
```

Expected: HTTP 200, 모든 Chromium test PASS. `playwright.config.ts`의 `reuseExistingServer`가 현재 4001을 사용하며 4101 또는 다른 관리자 port를 열지 않는다.

- [ ] **Step 5: E2E 수정이 필요하면 worktree에서만 수정·검증·재merge**

실패 원인을 `systematic-debugging`으로 확인하고 제품 또는 테스트의 가장 좁은 책임 계층만 `apply_patch`로 수정한다.

```powershell
$roomChangedFiles = @(git -C .worktrees/room-operational-status diff --name-only)
$roomAllowedFiles = @(
  'SDTPL_ADM/src/components/hotel-admin/room-operations-panel.tsx',
  'SDTPL_ADM/src/components/hotel-admin/checked-in-room-move.tsx',
  'SDTPL_ADM/src/components/hotel-admin/daily-operations.tsx',
  'SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx',
  'SDTPL_ADM/src/lib/staff-api.ts',
  'SDTPL_ADM/e2e/room-operational-status.spec.ts',
  'SDTPL_ADM/e2e/checked-in-room-move.spec.ts'
)
$roomUnexpectedFiles = @($roomChangedFiles | Where-Object { $_ -notin $roomAllowedFiles })
if ($roomUnexpectedFiles.Count -gt 0) { throw "Unexpected regression files: $($roomUnexpectedFiles -join ', ')" }
git -C .worktrees/room-operational-status add -- $roomChangedFiles
git -C .worktrees/room-operational-status commit -m "fix(operations): correct verified browser regression"
git merge --no-ff codex/room-operational-status -m "merge: verified room operations regression fix"
```

수정 후 Step 4 전체를 다시 실행해 PASS를 확인한다. backend 수정이 필요한 실패라면 같은 방식으로 실제 diff를 확인하고 `services/api/src/main/java/team/hotelchain/operations/` 아래의 수정 파일과 대응 테스트 파일을 허용 목록에 정확히 추가한 뒤 commit한다.

- [ ] **Step 6: 기존 Compose API 4080 재빌드와 V36 확인**

```powershell
docker compose up -d --build api
$ready = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:4080/actuator/health/readiness' -TimeoutSec 10
$ready.StatusCode
docker compose logs api --since 5m | Select-String -Pattern 'V36|Successfully applied|Started HotelApplication'
```

Expected: API 4080 readiness 200, Flyway V36 성공. 관리자 4001은 계속 200이어야 한다.

- [ ] **Step 7: 실제 브라우저 읽기 검증**

열려 있는 in-app browser의 `http://localhost:4001/dashboard/operations`를 새로고침하고 다음을 읽기 전용으로 확인한다.

```text
오늘의 운영 heading
객실 운영 상태 region
점검 필요·판매 중지·예상 복구 지연 summary
기존 도착 예정·출발 예정·청소 필요 객실 card
```

실제 상태 전환, 객실 이동, 체크인·체크아웃 button은 누르지 않는다.

- [ ] **Step 8: 검증 기록 작성과 최종 merge**

`docs/changes/2026-09-14-room-operational-status-checked-in-move.md`에 정확히 다음 heading을 사용한다.

```markdown
# 실제 객실 점검·판매 중지와 투숙 중 객실 이동

## 구현 범위
## 상태·권한 정책
## 검증 결과
## 미검증·후속 범위
## 배포·롤백
```

실제 backend tests 수, Playwright tests 수, lint·TypeScript·build, V36와 브라우저 결과를 기록한다. `current-development-context.md` 상단에 기능 요약을 추가하고 다음 작업에서 이번 항목을 실제 PG 다음 순위에서 제거한다.

```powershell
git -C .worktrees/room-operational-status add docs/changes/2026-09-14-room-operational-status-checked-in-move.md docs/overview/current-development-context.md
git -C .worktrees/room-operational-status commit -m "docs(operations): record verified room status rollout"
git merge --no-ff codex/room-operational-status -m "merge: document verified room operations"
```

- [ ] **Step 9: 완료 상태 증명**

```powershell
git diff --check
git status --short --branch
git log --oneline main..codex/room-operational-status
git -C .worktrees/room-operational-status status --short --branch
$admin = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:4001/dashboard/operations' -TimeoutSec 10
$api = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:4080/actuator/health/readiness' -TimeoutSec 10
"admin=$($admin.StatusCode) api=$($api.StatusCode)"
```

Expected: feature branch의 미merge commit 없음, worktree clean, root에는 기존 `.tmp/`만 남음, admin 200, API readiness 200.
