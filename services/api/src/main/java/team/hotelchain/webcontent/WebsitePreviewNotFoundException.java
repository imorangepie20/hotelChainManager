package team.hotelchain.webcontent;

public class WebsitePreviewNotFoundException extends RuntimeException {
    public WebsitePreviewNotFoundException() {
        super("웹사이트 미리보기를 찾을 수 없습니다.");
    }

    public String code() {
        return "WEBSITE_PREVIEW_NOT_FOUND";
    }
}
