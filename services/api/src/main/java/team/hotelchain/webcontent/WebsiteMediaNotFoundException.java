package team.hotelchain.webcontent;

import java.util.UUID;

public class WebsiteMediaNotFoundException extends RuntimeException {
    public WebsiteMediaNotFoundException(UUID mediaId) {
        super("미디어를 찾을 수 없습니다: " + mediaId);
    }
}
