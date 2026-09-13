package team.hotelchain.webcontent;

import java.time.Instant;

public record WebsitePreviewResult(PublishedWebsitePage page, Instant expiresAt) {}
