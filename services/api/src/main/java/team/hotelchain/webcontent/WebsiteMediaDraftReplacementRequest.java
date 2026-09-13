package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

public record WebsiteMediaDraftReplacementRequest(
        UUID targetMediaId,
        int expectedSourceVersion,
        int expectedTargetVersion,
        List<WebsiteMediaDraftReplacementTarget> targets) {
}
