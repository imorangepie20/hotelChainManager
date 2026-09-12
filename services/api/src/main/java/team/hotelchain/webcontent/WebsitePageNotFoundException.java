package team.hotelchain.webcontent;

public class WebsitePageNotFoundException extends RuntimeException {
    public WebsitePageNotFoundException(String path) {
        super("발행된 웹사이트 페이지를 찾을 수 없습니다: " + path);
    }
}
