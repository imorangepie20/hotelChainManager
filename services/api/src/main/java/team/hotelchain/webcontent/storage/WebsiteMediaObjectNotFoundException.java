package team.hotelchain.webcontent.storage;

public class WebsiteMediaObjectNotFoundException extends WebsiteMediaStorageException {
    public WebsiteMediaObjectNotFoundException(String storeName, String operation) {
        super("미디어 저장소 객체를 찾을 수 없습니다. store=" + storeName + ", operation=" + operation);
    }
}
