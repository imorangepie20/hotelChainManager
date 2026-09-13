package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsiteMediaDraftReplacementResult(
        UUID sourceMediaId,
        UUID targetMediaId,
        int replacedUsageCount,
        int changedDraftCount) {
}
