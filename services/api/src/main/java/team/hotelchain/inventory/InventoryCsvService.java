package team.hotelchain.inventory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * 재고 CSV를 만들고 읽는다.
 * <p>
 * 내보내기와 업로드는 같은 열 순서를 쓴다. 그래야 본사가 내려받은 파일을
 * 수정하지 않고 그대로 다시 올릴 수 있다. {@code 보류}·{@code 확정}·
 * {@code 잔여}·{@code 판매 상태}는 본사가 참고로 보는 읽기 전용 열이고
 * 업로드에서는 무시한다.
 */
@Service
public class InventoryCsvService {

    static final int MAX_ROWS = 1_000;

    static final String HEADER_ROOM_TYPE_ID = "객실 유형 ID";
    static final String HEADER_ROOM_TYPE_NAME = "객실 유형";
    static final String HEADER_STAY_DATE = "숙박일";
    static final String HEADER_CAPACITY = "총량";
    static final String HEADER_HELD = "보류";
    static final String HEADER_CONFIRMED = "확정";
    static final String HEADER_REMAINING = "잔여";
    static final String HEADER_SALES_STATUS = "판매 상태";
    static final String HEADER_MAX_OCCUPANCY = "최대 인원";

    private static final String[] HEADERS = {
            HEADER_ROOM_TYPE_ID, HEADER_ROOM_TYPE_NAME, HEADER_STAY_DATE, HEADER_CAPACITY,
            HEADER_HELD, HEADER_CONFIRMED, HEADER_REMAINING, HEADER_SALES_STATUS, HEADER_MAX_OCCUPANCY,
    };

    private static final int COLUMN_COUNT = HEADERS.length;
    private static final int COLUMN_ROOM_TYPE_ID = 0;
    static final int COLUMN_CAPACITY = 3;

    /**
     * 재고를 CSV 문자열로 만든다. SELECT 결과만 쓰므로 재고를 변경하지 않는다.
     */
    public String export(InventoryView inventory) {
        StringWriter writer = new StringWriter();
        writer.append(String.join(",", HEADERS)).append("\r\n");
        for (InventoryView.RoomTypeInventory roomType : inventory.roomTypes()) {
            for (InventoryView.InventoryDay day : roomType.days()) {
                writer.append(csvRow(roomType, day));
            }
        }
        return writer.toString();
    }

    private String csvRow(InventoryView.RoomTypeInventory roomType, InventoryView.InventoryDay day) {
        return String.join(",",
                roomType.roomTypeId().toString(),
                csv(roomType.name()),
                day.stayDate().toString(),
                Integer.toString(day.capacity()),
                Integer.toString(day.held()),
                Integer.toString(day.confirmed()),
                Integer.toString(day.remaining()),
                day.salesStatus(),
                Integer.toString(roomType.maxOccupancy())) + "\r\n";
    }

    // 값에 쉼표·따옴표가 들어 있으면 CSV 규칙대로 감싼다.
    private String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * CSV 본문을 객실 유형별 조정 목록으로 바꾼다.
     * <p>
     * 형식이 잘못되면 {@link InventoryImportFormatException}을 던져서
     * 호출자가 400을 내게 한다. 보류·확정·잔여·판매 상태 열은 읽지
     * 않고 무시한다.
     */
    public ParsedCsv parse(byte[] body) {
        List<Row> rows = new ArrayList<>();
        int skipped = 0;
        boolean headerSeen = false;

        // 엑셀이 내보낸 파일은 BOM이 붙을 수 있다. UTF-8 BOM을 먼저 없앤다.
        Charset charset = StandardCharsets.UTF_8;
        byte[] content = stripUtf8Bom(body);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    // 빈 행은 무시하지만 skippedRows에 센다.
                    skipped += 1;
                    continue;
                }
                List<String> columns = splitCsvLine(trimmed);
                if (!headerSeen && isHeader(columns)) {
                    headerSeen = true;
                    skipped += 1;
                    continue;
                }
                if (columns.size() < COLUMN_COUNT) {
                    throw new InventoryImportFormatException(
                            "열 수가 부족합니다. " + COLUMN_COUNT + "개의 열이 필요합니다.");
                }
                rows.add(parseRow(columns, rows.size() + 1));
                if (rows.size() > MAX_ROWS) {
                    throw new InventoryImportFormatException(
                            "한 번에 올릴 수 있는 행은 최대 " + MAX_ROWS + "행입니다.");
                }
            }
        } catch (InventoryImportFormatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InventoryImportFormatException("CSV 파일을 읽지 못했습니다.");
        }

        if (!headerSeen) {
            throw new InventoryImportFormatException(
                    "첫 행에 헤더가 없습니다. 내보낸 파일을 그대로 올려 주세요.");
        }
        if (rows.isEmpty()) {
            throw new InventoryImportFormatException("올릴 재고 행이 없습니다.");
        }
        return new ParsedCsv(rows, skipped);
    }

    private byte[] stripUtf8Bom(byte[] body) {
        if (body.length >= 3
                && (body[0] & 0xFF) == 0xEF
                && (body[1] & 0xFF) == 0xBB
                && (body[2] & 0xFF) == 0xBF) {
            byte[] stripped = new byte[body.length - 3];
            System.arraycopy(body, 3, stripped, 0, stripped.length);
            return stripped;
        }
        return body;
    }

    private boolean isHeader(List<String> columns) {
        return columns.get(COLUMN_ROOM_TYPE_ID).trim().equals(HEADER_ROOM_TYPE_ID)
                || columns.get(COLUMN_CAPACITY).trim().equals(HEADER_CAPACITY);
    }

    private Row parseRow(List<String> columns, int lineNumber) {
        UUID roomTypeId;
        try {
            roomTypeId = UUID.fromString(columns.get(COLUMN_ROOM_TYPE_ID).trim());
        } catch (IllegalArgumentException exception) {
            throw new InventoryImportFormatException(
                    lineNumber + "번째 행의 객실 유형 ID가 올바르지 않습니다.");
        }
        LocalDate stayDate;
        try {
            stayDate = LocalDate.parse(columns.get(2).trim());
        } catch (Exception exception) {
            throw new InventoryImportFormatException(
                    lineNumber + "번째 행의 숙박일 형식이 올바르지 않습니다. YYYY-MM-DD 형식이어야 합니다.");
        }
        int capacity;
        try {
            capacity = Integer.parseInt(columns.get(COLUMN_CAPACITY).trim());
        } catch (NumberFormatException exception) {
            throw new InventoryImportFormatException(
                    lineNumber + "번째 행의 총량이 숫자가 아닙니다.");
        }
        return new Row(roomTypeId, stayDate, capacity);
    }

    // 따옴표로 감싼 필드와 쉼표를 포함한 필드를 나눈다.
    private List<String> splitCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        current.append('"');
                        index += 1;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    /**
     * CSV에서 읽은 행. 총량만 가지고 있고 보류·확정은 가지고 있지 않다.
     */
    public record Row(UUID roomTypeId, LocalDate stayDate, int capacity) {
    }

    /**
     * 파싱 결과. {@code skippedRows}는 헤더·빈 행 수다.
     */
    public record ParsedCsv(List<Row> rows, int skippedRows) {

        /**
         * 객실 유형별로 모은 조정 목록을 만든다.
         * 기존 {@code PATCH .../inventory}가 받는 형식과 같다.
         */
        public Map<UUID, List<InventoryAdjustRequest.DayAdjustment>> groupedAdjustments() {
            Map<UUID, List<InventoryAdjustRequest.DayAdjustment>> grouped = new LinkedHashMap<>();
            for (Row row : rows) {
                grouped.computeIfAbsent(row.roomTypeId(), ignored -> new ArrayList<>())
                        .add(new InventoryAdjustRequest.DayAdjustment(row.stayDate(), row.capacity()));
            }
            return grouped;
        }
    }
}
