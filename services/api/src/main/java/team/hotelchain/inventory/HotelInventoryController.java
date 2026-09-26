package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/hotels")
public class HotelInventoryController {

    private final InventoryQueryService inventory;
    private final InventoryCommandService commands;
    private final SalesStatusCommandService salesStatusCommands;
    private final InventoryImportCommandService importCommands;
    private final InventoryCsvService csv;

    public HotelInventoryController(InventoryQueryService inventory, InventoryCommandService commands,
            SalesStatusCommandService salesStatusCommands, InventoryImportCommandService importCommands,
            InventoryCsvService csv) {
        this.inventory = inventory;
        this.commands = commands;
        this.salesStatusCommands = salesStatusCommands;
        this.importCommands = importCommands;
        this.csv = csv;
    }

    @GetMapping("/{hotelId}/inventory")
    public InventoryView inventory(
            @PathVariable UUID hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return inventory.list(token, hotelId, from, to);
    }

    // 본사가 객실 유형의 일자 재고 총량을 바꾼다. 응답은 200이고
    // 멱원 재호출은 같은 결과를 돌려준다.
    @PatchMapping("/{hotelId}/inventory")
    public ResponseEntity<InventoryAdjustResponse> adjustInventory(
            @PathVariable UUID hotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody InventoryAdjustRequest request) {
        InventoryAdjustResponse response = commands.adjust(token, hotelId, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사가 객실 유형의 날짜 구간 판매를 중지·재개한다. 총량은 건드리지 않는다.
    // 응답은 201이고 멱원 재호출은 200에 created=false로 같은 구간을 돌려준다.
    @PatchMapping("/{hotelId}/room-types/{roomTypeId}/sales-status")
    public ResponseEntity<SalesStatusResponse> setSalesStatus(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody SalesStatusRequest request) {
        SalesStatusResponse response = salesStatusCommands.set(token, hotelId, roomTypeId, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 본사·지점 직원이 객실 유형의 판매 중지 구간을 확인한다. SELECT만 사용한다.
    @GetMapping("/{hotelId}/room-types/{roomTypeId}/sales-status")
    public SalesStatusView salesStatus(
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return salesStatusCommands.list(token, hotelId, roomTypeId);
    }

    // 본사가 여러 객실 유형의 여러 날짜 재고를 CSV로 내려받는다.
    // SELECT만 사용하고 재고를 변경하지 않는다.
    @GetMapping("/{hotelId}/inventory/export")
    public ResponseEntity<String> exportInventory(
            @PathVariable UUID hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        InventoryView view = inventory.list(token, hotelId, from, to);
        // 엑셀이 UTF-8을 인식하게 하려고 BOM을 붙인다.
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv;charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"inventory.csv\"")
                .body("\ufeff" + csv.export(view));
    }

    // 본사가 CSV로 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾼다.
    // 응답은 201이고 멱원 재호출은 200에 created=false로 같은 결과를 돌려준다.
    @PostMapping("/{hotelId}/inventory/import")
    public ResponseEntity<InventoryImportResponse> importInventory(
            @PathVariable UUID hotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Staff-Session", required = false) String token,
            @RequestBody byte[] body) {
        InventoryImportResponse response = importCommands.importCsv(token, hotelId, idempotencyKey, body);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
