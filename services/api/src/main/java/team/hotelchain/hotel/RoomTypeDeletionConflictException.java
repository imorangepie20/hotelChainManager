package team.hotelchain.hotel;

/**
 * 본사가 객실 유형을 지울 때, 진행 중인 판매·예약·배정이 있으면 발생한다.
 * 지우면 예약이 가리키는 객실 유형이 사라지고 재고가 음수가 될 수 있으므로
 * 값을 지우지 않고 409로 거부한다.
 */
public class RoomTypeDeletionConflictException extends RuntimeException {

    private final int confirmedReservations;
    private final int openChangeRequests;
    private final int heldInventoryDays;
    private final int physicalRooms;

    public RoomTypeDeletionConflictException(int confirmedReservations, int openChangeRequests,
            int heldInventoryDays, int physicalRooms) {
        super(buildMessage(confirmedReservations, openChangeRequests, heldInventoryDays, physicalRooms));
        this.confirmedReservations = confirmedReservations;
        this.openChangeRequests = openChangeRequests;
        this.heldInventoryDays = heldInventoryDays;
        this.physicalRooms = physicalRooms;
    }

    public int confirmedReservations() {
        return confirmedReservations;
    }

    public int openChangeRequests() {
        return openChangeRequests;
    }

    public int heldInventoryDays() {
        return heldInventoryDays;
    }

    public int physicalRooms() {
        return physicalRooms;
    }

    // 어느 조건이 막았는지 본사가 알아야 다음에 무엇을 할지 정할 수 있다.
    private static String buildMessage(int confirmed, int changeRequests, int held, int rooms) {
        StringBuilder message = new StringBuilder("객실 유형을 지울 수 없습니다. ");
        boolean first = true;
        if (confirmed > 0) {
            message.append("진행 중인 예약 ").append(confirmed).append("건");
            first = false;
        }
        if (changeRequests > 0) {
            if (!first) message.append(", ");
            message.append("진행 중인 예약 변경 요청 ").append(changeRequests).append("건");
            first = false;
        }
        if (held > 0) {
            if (!first) message.append(", ");
            message.append("보류 중인 재고 ").append(held).append("일분");
            first = false;
        }
        if (rooms > 0) {
            if (!first) message.append(", ");
            message.append("배정된 실제 객실 ").append(rooms).append("실");
        }
        message.append("이 있어야 지울 수 있습니다.");
        return message.toString();
    }
}
