package team.hotelchain.staff;

/**
 * 본사가 직원을 삭제할 때, 진행 중인 작업이 있으면 발생한다.
 * <p>
 * 직원을 삭제하면 진행 중인 예약 변경 요청·정산 실행의 근거가 사라지므로
 * 409로 거부하고 계정을 비우지 않는다.
 */
public class StaffAccountDeletionConflictException extends RuntimeException {

    private final int openChangeRequests;
    private final int activeSettlementRuns;

    public StaffAccountDeletionConflictException(int openChangeRequests, int activeSettlementRuns) {
        super(buildMessage(openChangeRequests, activeSettlementRuns));
        this.openChangeRequests = openChangeRequests;
        this.activeSettlementRuns = activeSettlementRuns;
    }

    public int openChangeRequests() {
        return openChangeRequests;
    }

    public int activeSettlementRuns() {
        return activeSettlementRuns;
    }

    // 어느 조건이 막았는지 본사가 알아야 다음에 무엇을 할지 정할 수 있다.
    private static String buildMessage(int changeRequests, int settlementRuns) {
        StringBuilder message = new StringBuilder("직원을 삭제할 수 없습니다. ");
        boolean first = true;
        if (changeRequests > 0) {
            message.append("진행 중인 예약 변경 요청 ").append(changeRequests).append("건");
            first = false;
        }
        if (settlementRuns > 0) {
            if (!first) message.append(", ");
            message.append("진행 중인 정산 실행 ").append(settlementRuns).append("건");
        }
        message.append("이 없어야 삭제할 수 있습니다. 완료된 뒤에 다시 시도해 주세요.");
        return message.toString();
    }
}
