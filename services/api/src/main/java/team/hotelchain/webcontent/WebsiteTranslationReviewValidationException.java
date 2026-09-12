package team.hotelchain.webcontent;

public class WebsiteTranslationReviewValidationException extends IllegalArgumentException {
    private final String code;

    public WebsiteTranslationReviewValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
