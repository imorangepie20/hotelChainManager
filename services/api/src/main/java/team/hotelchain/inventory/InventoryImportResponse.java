package team.hotelchain.inventory;

import java.util.List;
import java.util.UUID;

/**
 * 본사가 CSV로 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾼 결과.
 * <p>
 * {@code created}가 {@code false}면 멱원 재호출로 같은 결과를 돌려주는 것이다.
 * {@code totalRows}는 파일에서 읽은 의미 있는 행 수, {@code skippedRows}는
 * 헤더·빈 행 수, {@code appliedRows}는 실제로 재고를 바꾼 행 수다.
 */
public record InventoryImportResponse(
        UUID hotelId,
        int totalRows,
        int appliedRows,
        int skippedRows,
        List<InventoryAdjustResponse.AdjustedDay> days,
        boolean created) {
}
