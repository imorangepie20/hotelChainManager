package team.hotelchain.hotel;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/hotels")
public class HotelCatalogController {

    private final HotelCatalogQueryService catalog;
    private final RoomTypeCommandService commands;

    public HotelCatalogController(HotelCatalogQueryService catalog, RoomTypeCommandService commands) {
        this.catalog = catalog;
        this.commands = commands;
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
}
