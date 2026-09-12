package team.hotelchain.webcontent;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebsitePageConnectionValidator {
    private final JdbcTemplate jdbc;

    public WebsitePageConnectionValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validate(ContentKind kind, UUID hotelId, WebsitePageConnections connections) {
        if (kind == null || !ContentKind.contentPageKinds().contains(kind)) throw invalid("상세 콘텐츠 종류가 올바르지 않습니다.");
        WebsitePageConnections next = connections == null ? WebsitePageConnections.empty() : connections;
        requireHotelScope(kind, hotelId);
        rejectDuplicates(next.roomTypeIds(), "객실 유형");
        rejectDuplicates(next.targetHotelIds(), "대상 지점");
        for (UUID targetHotelId : next.targetHotelIds()) requireHotel(targetHotelId, "대상 지점");
        switch (kind) {
            case ROOM -> validateRoom(hotelId, next);
            case PROMOTION -> validatePromotion(next);
            default -> {
                if (!next.roomTypeIds().isEmpty() || !next.targetHotelIds().isEmpty()) {
                    throw invalid(kind + " 유형에는 객실 유형 또는 대상 지점 연결을 둘 수 없습니다.");
                }
            }
        }
    }

    public void validateRelatedPages(UUID pageId, String documentState, WebsitePageConnections connections,
            boolean requirePublishedTarget) {
        if (pageId == null || !List.of("DRAFT", "PUBLISHED").contains(documentState)) {
            throw invalid("관련 페이지 상태가 올바르지 않습니다.");
        }
        WebsitePageConnections next = connections == null ? WebsitePageConnections.empty() : connections;
        HashSet<UUID> targets = new HashSet<>();
        HashSet<String> displayOrders = new HashSet<>();
        for (WebsitePageRelation relation : next.relatedPages()) {
            if (relation == null || relation.targetPageId() == null || relation.targetPageId().equals(pageId)) {
                throw invalid("관련 페이지는 자기 자신을 가리킬 수 없습니다.");
            }
            if (!List.of("RELATED", "MANUAL_CARD").contains(relation.relationType()) || relation.displayOrder() < 0) {
                throw invalid("관련 페이지 유형 또는 표시 순서가 올바르지 않습니다.");
            }
            if (!targets.add(relation.targetPageId()) || !displayOrders.add(relation.relationType() + ":" + relation.displayOrder())) {
                throw invalid("관련 페이지 연결이 중복되었습니다.");
            }
            requireRelatedPage(relation.targetPageId(), requirePublishedTarget);
            if (hasPathTo(pageId, relation.targetPageId(), documentState, new HashSet<>())) {
                throw invalid("관련 페이지 연결은 순환할 수 없습니다.");
            }
        }
    }

    private void requireHotelScope(ContentKind kind, UUID hotelId) {
        if (kind.requiresHotel() && hotelId == null) throw invalid(kind + " 유형에는 소유 지점이 필요합니다.");
        if (!kind.allowsHotel() && hotelId != null) throw invalid(kind + " 유형에는 소유 지점을 둘 수 없습니다.");
        if (hotelId != null) requireHotel(hotelId, "소유 지점");
    }

    private void validateRoom(UUID hotelId, WebsitePageConnections connections) {
        if (connections.roomTypeIds().size() != 1) throw invalid("ROOM 유형에는 객실 유형 하나가 필요합니다.");
        if (!connections.targetHotelIds().isEmpty()) throw invalid("ROOM 유형에는 대상 지점 연결을 둘 수 없습니다.");
        UUID roomTypeHotelId = roomTypeHotel(connections.roomTypeIds().getFirst());
        if (!hotelId.equals(roomTypeHotelId)) throw invalid("객실 유형과 소유 지점이 일치해야 합니다.");
    }

    private void validatePromotion(WebsitePageConnections connections) {
        if (connections.targetHotelIds().isEmpty()) throw invalid("PROMOTION 유형에는 대상 지점 하나 이상이 필요합니다.");
        for (UUID roomTypeId : connections.roomTypeIds()) {
            if (!connections.targetHotelIds().contains(roomTypeHotel(roomTypeId))) {
                throw invalid("객실 유형은 대상 지점 중 하나에 속해야 합니다.");
            }
        }
    }

    private void requireHotel(UUID hotelId, String label) {
        Integer count = jdbc.queryForObject("select count(*) from hotel where id = ?", Integer.class, hotelId);
        if (count == null || count == 0) throw invalid(label + "을 찾을 수 없습니다.");
    }

    private UUID roomTypeHotel(UUID roomTypeId) {
        UUID hotelId = jdbc.query("select hotel_id from room_type where id = ?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, roomTypeId);
        if (hotelId == null) throw invalid("객실 유형을 찾을 수 없습니다.");
        return hotelId;
    }

    private void requireRelatedPage(UUID pageId, boolean requirePublishedTarget) {
        String query = "select count(*) from website_page where id = ? and page_type = 'CONTENT_PAGE' "
                + "and lifecycle_status = 'ACTIVE'" + (requirePublishedTarget ? " and published_content <> '{}'::jsonb" : "");
        Integer count = jdbc.queryForObject(query, Integer.class, pageId);
        if (count == null || count == 0) throw invalid("관련 페이지를 찾을 수 없거나 공개할 수 없습니다.");
    }

    private boolean hasPathTo(UUID expectedTarget, UUID currentPage, String documentState, HashSet<UUID> visited) {
        if (expectedTarget.equals(currentPage)) return true;
        if (!visited.add(currentPage)) return false;
        List<UUID> targets = jdbc.query("""
                select target_page_id from website_page_relation
                 where page_id = ? and document_state = ?
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), currentPage, documentState);
        return targets.stream().anyMatch(target -> hasPathTo(expectedTarget, target, documentState, visited));
    }

    private void rejectDuplicates(List<UUID> identifiers, String label) {
        if (identifiers.stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(identifiers).size() != identifiers.size()) {
            throw invalid(label + " 연결이 중복되었거나 올바르지 않습니다.");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("콘텐츠 연결 오류: " + message);
    }
}
