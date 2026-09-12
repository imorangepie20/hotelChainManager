package team.hotelchain.webcontent;

import java.util.Set;

public enum ContentKind {
    HOME(false, false),
    DESTINATION(true, true),
    ROOM(true, true),
    DINING(true, true),
    FACILITY(true, true),
    EXPERIENCE(true, true),
    PROMOTION(false, false),
    GUIDE(false, true),
    BRAND(false, false);

    private final boolean requiresHotel;
    private final boolean allowsHotel;

    ContentKind(boolean requiresHotel, boolean allowsHotel) {
        this.requiresHotel = requiresHotel;
        this.allowsHotel = allowsHotel;
    }

    public boolean requiresHotel() {
        return requiresHotel;
    }

    public boolean allowsHotel() {
        return allowsHotel;
    }

    public static ContentKind legacyForPageType(String pageType) {
        return switch (pageType) {
            case "HOME_PAGE" -> HOME;
            case "HOTEL_LANDING" -> DESTINATION;
            case "CONTENT_PAGE" -> BRAND;
            default -> throw new IllegalArgumentException("콘텐츠 종류가 없는 페이지 유형입니다.");
        };
    }

    public static Set<ContentKind> contentPageKinds() {
        return Set.of(ROOM, DINING, FACILITY, EXPERIENCE, PROMOTION, GUIDE, BRAND);
    }
}
