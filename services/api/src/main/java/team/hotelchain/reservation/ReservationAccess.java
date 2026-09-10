package team.hotelchain.reservation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class ReservationAccess {

    public String hashToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("예약 관리 토큰은 필수입니다.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("예약 관리 토큰 형식이 올바르지 않습니다.");
            }
            return sha256(decoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("예약 관리 토큰 형식이 올바르지 않습니다.");
        }
    }

    public String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
