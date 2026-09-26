package team.hotelchain.audit;

/**
 * 감사 이력에 노출되는 고객 개인정보를 가린다.
 * <p>
 * 본사가 화면을 공유하거나 캡처할 때 이름·이메일이 퍼지는 것을 막는다.
 * 식별자(id, 객실 번호, 금액)는 그대로 둬서 처리 건을 추적할 수 있다.
 */
final class PiiMasker {

    private PiiMasker() {
    }

    static String name(String value) {
        return mask(value, 1);
    }

    static String email(String value) {
        if (value == null) return null;
        int at = value.indexOf('@');
        if (at <= 0) return value;
        String local = value.substring(0, at);
        String domain = value.substring(at);
        return mask(local, 1) + domain;
    }

    // 뒤에서 보이는 글자 수만큼 남기고 나머지를 *로 가린다. 1글자면 전체를 가린다.
    private static String mask(String value, int visibleTail) {
        if (value == null) return null;
        int length = value.length();
        if (length == 0) return value;
        if (length <= visibleTail) return "*".repeat(length);
        return "*".repeat(length - visibleTail) + value.substring(length - visibleTail);
    }
}
