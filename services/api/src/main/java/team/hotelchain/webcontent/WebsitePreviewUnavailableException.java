package team.hotelchain.webcontent;

public class WebsitePreviewUnavailableException extends RuntimeException {
    public WebsitePreviewUnavailableException() {
        super("웹사이트 미리보기를 더 이상 사용할 수 없습니다.");
    }

    public String code() {
        return "WEBSITE_PREVIEW_UNAVAILABLE";
    }
}
