package team.hotelchain.hotel;

/**
 * 본사가 요금제의 조식 포함 여부를 바꿀 때, 진행 중인 예약 중 현재 조건과
 * 다른 조건으로 예약된 것이 있으면 발생한다. 예약은 {@code rate_plan_id}만
 * 가지므로 계약 내용이 달라지면 안 된다. 값을 바꾸지 않고 409로 거부한다.
 */
public class RoomTypeBreakfastConflictException extends RuntimeException {

    private final int conflictingReservations;

    public RoomTypeBreakfastConflictException(int conflictingReservations) {
        super("조식 포함 여부를 바꿀 수 없습니다. 진행 중인 예약 " + conflictingReservations
                + "건이 현재 조식 조건으로 예약됐습니다.");
        this.conflictingReservations = conflictingReservations;
    }

    public int conflictingReservations() {
        return conflictingReservations;
    }
}
