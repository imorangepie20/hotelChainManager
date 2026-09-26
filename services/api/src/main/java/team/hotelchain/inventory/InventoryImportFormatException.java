package team.hotelchain.inventory;

/**
 * CSV 업로드 형식이 잘못됐을 때 발생한다.
 * <p>
 * 헤더가 없거나, 열 수가 다르거나, 날짜·총량이 숫자가 아니거나, 빈 행이
 * 중간에 있으면 400으로 거부한다. 이때 재고를 전혀 바꾸지 않는다.
 */
public class InventoryImportFormatException extends RuntimeException {

    public InventoryImportFormatException(String message) {
        super(message);
    }
}
