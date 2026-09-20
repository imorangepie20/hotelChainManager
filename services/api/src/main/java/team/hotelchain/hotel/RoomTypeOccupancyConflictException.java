package team.hotelchain.hotel;

/**
 * 본사가 객실 유형의 최대 인원을 내릴 때, 진행 중인 예약 중 새 인원을
 * 초과하는 예약이 있으면 발생한다. 예약 가능 조건에 영영을 주므로
 * 값을 바꾸지 않고 409로 거부한다.
 */
public class RoomTypeOccupancyConflictException extends RuntimeException {

    private final int conflictingReservations;

    public RoomTypeOccupancyConflictException(int conflictingReservations) {
        super("최대 인원을 내릴 수 없습니다. 진행 중인 예약 " + conflictingReservations + "건이 새 인원을 초과합니다.");
        this.conflictingReservations = conflictingReservations;
    }

    public int conflictingReservations() {
        return conflictingReservations;
    }
}
