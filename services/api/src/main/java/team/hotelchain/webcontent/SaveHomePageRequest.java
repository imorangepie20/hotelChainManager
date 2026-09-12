package team.hotelchain.webcontent;

import java.util.Map;

public record SaveHomePageRequest(int expectedDraftVersion, Map<String, Object> content) {
}
