package team.hotelchain.guestrequest;

import java.util.List;
import java.util.UUID;

/** 고객 요청에 지정할 수 있는 최소 직원 정보. */
public record GuestRequestAssigneeListView(List<AssigneeView> assignees) {

    public record AssigneeView(UUID id, String displayName, String role, UUID hotelId) {}
}
