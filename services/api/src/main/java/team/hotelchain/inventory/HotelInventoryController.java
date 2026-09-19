package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/hotels")
public class HotelInventoryController {

    private final InventoryQueryService inventory;

    public HotelInventoryController(InventoryQueryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/{hotelId}/inventory")
    public InventoryView inventory(
            @PathVariable UUID hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-Staff-Session", required = false) String token) {
        return inventory.list(token, hotelId, from, to);
    }
}
