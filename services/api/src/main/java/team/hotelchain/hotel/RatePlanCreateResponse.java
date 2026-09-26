package team.hotelchain.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 본사가 요금제를 만들거나 이름을 바꾼 결과.
 * <p>
 * {@code created}가 {@code false}면 멱원 재호출로 같은 요금제를 돌려주는 것이다.
 * {@code changed}가 {@code false}면 이름이 이미 요청한 값과 같아서 DB를
 * 건드리지 않은 것이다.
 */
public record RatePlanCreateResponse(
        UUID ratePlanId,
        UUID hotelId,
        UUID roomTypeId,
        String name,
        boolean breakfastIncluded,
        String policyVersion,
        int defaultRateKrw,
        LocalDate fromDate,
        int seededDays,
        boolean created,
        boolean changed,
        List<RateDayRow> days) {

    public record RateDayRow(LocalDate stayDate, int amountKrw) {
    }

    public static RatePlanCreateResponse created(UUID ratePlanId, UUID hotelId, UUID roomTypeId,
            RatePlanCreateRequest request, List<RateDayRow> days, LocalDate fromDate) {
        return new RatePlanCreateResponse(ratePlanId, hotelId, roomTypeId, request.name().trim(),
                request.breakfastIncludedOrFalse(), request.policyVersion().trim(), request.defaultRateKrw(),
                fromDate, days.size(), true, true, days);
    }

    public static RatePlanCreateResponse existing(UUID ratePlanId, UUID hotelId, UUID roomTypeId,
            RatePlanCreateRequest request, List<RateDayRow> days, LocalDate fromDate) {
        return new RatePlanCreateResponse(ratePlanId, hotelId, roomTypeId, request.name().trim(),
                request.breakfastIncludedOrFalse(), request.policyVersion().trim(), request.defaultRateKrw(),
                fromDate, days.size(), false, true, days);
    }

    // 멱원 재호출이 저장된 요금제를 그대로 돌려줄 때 쓴다. 보낸 요청이 아니라
    // DB에 저장된 값을 읽어 돌려주므로 요청 본문을 받지 않는다.
    public static RatePlanCreateResponse replayed(UUID ratePlanId, UUID hotelId, UUID roomTypeId,
            String name, boolean breakfastIncluded, String policyVersion,
            int defaultRateKrw, LocalDate fromDate, List<RateDayRow> days) {
        return new RatePlanCreateResponse(ratePlanId, hotelId, roomTypeId, name, breakfastIncluded,
                policyVersion, defaultRateKrw, fromDate, days.size(), false, true, days);
    }

    public static RatePlanCreateResponse renamed(UUID ratePlanId, UUID hotelId, UUID roomTypeId,
            String name, boolean changed) {
        return new RatePlanCreateResponse(ratePlanId, hotelId, roomTypeId, name,
                false, null, 0, null, 0, false, changed, List.of());
    }
}
