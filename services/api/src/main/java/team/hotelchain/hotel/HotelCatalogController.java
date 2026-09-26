package team.hotelchain.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import team.hotelchain.web.ApiError;

@RestController
@RequestMapping("/api/staff/hotels")
public class HotelCatalogController {

    private final HotelCatalogQueryService catalog;
    private final RoomTypeCommandService commands;
    private final RoomTypeUpdateService updates;
    private final RoomTypeDeletionService deletions;
    private final RatePlanQueryService ratePlanQueries;
    private final RatePlanCommandService ratePlanCommands;
    private final RateQueryService rateQueries;
    private final RateCommandService rateCommands;
    private final HotelQueryService hotelQueries;
    private final HotelCommandService hotelCommands;
    private final HotelUpdateService hotelUpdates;
    private final HotelActivationService hotelActivation;

    public HotelCatalogController(HotelCatalogQueryService catalog, RoomTypeCommandService commands,
            RoomTypeUpdateService updates, RoomTypeDeletionService deletions,
            RatePlanQueryService ratePlanQueries, RatePlanCommandService ratePlanCommands,
            RateQueryService rateQueries, RateCommandService rateCommands,
            HotelQueryService hotelQueries, HotelCommandService hotelCommands,
            HotelUpdateService hotelUpdates, HotelActivationService hotelActivation) {
        this.catalog = catalog;
        this.commands = commands;
        this.updates = updates;
        this.deletions = deletions;
        this.ratePlanQueries = ratePlanQueries;
        this.ratePlanCommands = ratePlanCommands;
        this.rateQueries = rateQueries;
        this.rateCommands = rateCommands;
        this.hotelQueries = hotelQueries;
        this.hotelCommands = hotelCommands;
        this.hotelUpdates = hotelUpdates;
        this.hotelActivation = hotelActivation;
    }

    // 본사가 지점 목록을 읽는다. 카탈로그·재고·보고서 화면의 지점 선택기가 쓴다. SELECT만 사용한다.
    @GetMapping
    public List<HotelSummary> hotels(
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return hotelQueries.list(token);
    }

    // 본사가 새 지점을 만든다. 응답은 201이고 멱원 재호출은 200에 created=false로 같은 지점을 돌려준다.
    @PostMapping
    public ResponseEntity<HotelCreateResponse> createHotel(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody HotelCreateRequest request) {
        HotelCreateResponse response = hotelCommands.create(token, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HotelNameConflictException.class)
    public ResponseEntity<ApiError> hotelNameConflict(HotelNameConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("HOTEL_NAME_CONFLICT", exception.getMessage()));
    }

    // 본사가 지점의 이름·지역·시간대를 바꾼다.
    // 응답은 200이고 멱원 재호출은 changed=false로 같은 결과를 돌려준다.
    @PatchMapping("/{hotelId}")
    public HotelUpdateResponse updateHotel(
            @PathVariable UUID hotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody HotelUpdateRequest request) {
        return hotelUpdates.update(token, hotelId, idempotencyKey, request);
    }

    // 본사가 지점의 판매를 중지·재개한다.
    // 응답은 200이고 멱원 재호출은 changed=false로 같은 결과를 돌려준다.
    @PatchMapping("/{hotelId}/active")
    public HotelActivationResponse setHotelActive(
            @PathVariable UUID hotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody HotelActivationRequest request) {
        return hotelActivation.setActive(token, hotelId, idempotencyKey, request);
    }

    @GetMapping("/{hotelId}/room-types")
    public RoomTypeCatalogResponse roomTypes(
            @PathVariable UUID hotelId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return catalog.listRoomTypes(token, hotelId, limit, offset);
    }

    // 본사가 객실 유형을 만든다. 응답은 201이고 멱원 재호출은 같은 객실 유형을 돌려준다.
    @PostMapping("/{hotelId}/room-types")
    public ResponseEntity<RoomTypeCreateResponse> createRoomType(
            @PathVariable UUID hotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody RoomTypeCreateRequest request) {
        RoomTypeCreateResponse response = commands.create(token, hotelId, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사가 객실 유형의 이름·최대 인원·조식 포함 여부·기본 요금을 바꾼다.
    // 응답은 200이고 멱원 재호출은 같은 결과를 돌려준다.
    @PatchMapping("/{hotelId}/room-types/{roomTypeId}")
    public RoomTypeUpdateResponse updateRoomType(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody RoomTypeUpdateRequest request) {
        return updates.update(token, hotelId, roomTypeId, idempotencyKey, request);
    }

    // 본사가 수정 화면에 미리 채울 기본 요금제의 현재값을 읽는다. SELECT만 사용한다.
    @GetMapping("/{hotelId}/room-types/{roomTypeId}/defaults")
    public RoomTypeCatalogView.RoomDefaults roomTypeDefaults(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return catalog.roomDefaults(hotelId, roomTypeId, token);
    }

    // 본사가 객실 유형을 지운다.
    // 응답은 200이고 멱원 재호출은 deleted=false로 같은 결과를 돌려준다.
    @DeleteMapping("/{hotelId}/room-types/{roomTypeId}")
    public RoomTypeDeletionResponse deleteRoomType(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return deletions.delete(token, hotelId, roomTypeId, idempotencyKey);
    }

    // 본사가 객실 유형의 일자별 요금을 읽는다. SELECT만 사용한다.
    // ratePlanId를 명시하면 그 요금제를, 명시하지 않으면 기본 요금제를 읽는다.
    @GetMapping("/{hotelId}/room-types/{roomTypeId}/rates")
    public RoomTypeRatesView roomTypeRates(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestParam(required = false) UUID ratePlanId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return rateQueries.listRates(token, hotelId, roomTypeId, ratePlanId, from, to);
    }

    // 본사가 객실 유형의 일자별 요금을 바꾼다. 새 결과는 201, 멱원 재호출은 200이다.
    // ratePlanId를 명시하면 그 요금제를, 명시하지 않으면 기본 요금제를 바꾼다.
    @PatchMapping("/{hotelId}/room-types/{roomTypeId}/rates")
    public ResponseEntity<RateAdjustResponse> adjustRoomTypeRates(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) UUID ratePlanId,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody RateAdjustRequest request) {
        // 경로와 본문의 객실 유형이 달라도 본문을 믿지 않고 경로를 따른다.
        RateAdjustRequest scoped = request.withRoomTypeId(roomTypeId);
        if (ratePlanId != null) {
            // 쿼리 파라미터로 요금제를 지정한 경우에 본문보다 경로를 따른다.
            scoped = scoped.withRatePlanId(ratePlanId);
        }
        RateAdjustResponse response = rateCommands.adjust(token, hotelId, idempotencyKey, scoped);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사·지점 직원이 객실 유형의 전체 요금제를 읽는다. SELECT만 사용한다.
    @GetMapping("/{hotelId}/room-types/{roomTypeId}/rate-plans")
    public RatePlanListView roomTypeRatePlans(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return ratePlanQueries.list(token, hotelId, roomTypeId);
    }

    // 본사가 객실 유형에 새 요금제를 만든다.
    // 응답은 201이고 멱원 재호출은 200에 created=false로 같은 요금제를 돌려준다.
    @PostMapping("/{hotelId}/room-types/{roomTypeId}/rate-plans")
    public ResponseEntity<RatePlanCreateResponse> createRatePlan(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody RatePlanCreateRequest request) {
        RatePlanCreateResponse response = ratePlanCommands.create(
                token, hotelId, roomTypeId, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사가 요금제의 이름을 바꾼다.
    // 응답은 200이고 멱원 재호출은 changed=false로 같은 결과를 돌려준다.
    @PatchMapping("/{hotelId}/room-types/{roomTypeId}/rate-plans/{ratePlanId}")
    public RatePlanCreateResponse renameRatePlan(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @PathVariable UUID ratePlanId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody RatePlanRenameRequest request) {
        return ratePlanCommands.rename(token, hotelId, roomTypeId, ratePlanId, idempotencyKey, request);
    }

    @ExceptionHandler(RatePlanNameConflictException.class)
    public ResponseEntity<ApiError> ratePlanNameConflict(RatePlanNameConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("RATE_PLAN_NAME_CONFLICT", exception.getMessage()));
    }
}
