package team.hotelchain.webcontent;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 운영에서는 신뢰된 TLS 프록시 또는 직접 HTTPS 요청만 허용한다. */
@Component
public class WebsitePreviewTransport {
    private final boolean requireHttps;

    public WebsitePreviewTransport(@Value("${website.preview.require-https:true}") boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    public void requireSecure(HttpServletRequest request) {
        if (requireHttps && !request.isSecure()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "초안 미리보기는 HTTPS에서만 사용할 수 있습니다.");
        }
    }
}
