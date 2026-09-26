package team.hotelchain.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** 화면의 현재 감사 페이지와 같은 10개 열을 OOXML 문자열 셀로 직렬화한다. */
@Service
public class AuditEventsXlsxService {

    static final List<String> HEADERS = List.of(
            "발생 시각", "유형", "처리 직원", "처리 직원 이메일", "역할",
            "지점", "예약 id", "고객", "객실", "내용");

    private static final Map<String, String> EVENT_LABELS = Map.ofEntries(
            Map.entry("GUEST_UPDATE", "예약자 정정"),
            Map.entry("PARTY_UPDATE", "투숙 인원 변경"),
            Map.entry("ROOM_REASSIGNMENT", "배정 객실 변경"),
            Map.entry("STAY_CHANGE", "숙박 조건 변경"),
            Map.entry("CANCELLATION", "예약 취소"),
            Map.entry("ROOM_OPERATIONAL_TRANSITION", "객실 운영 상태"),
            Map.entry("CHECKED_IN_ROOM_MOVE", "투숙 중 객실 이동"),
            Map.entry("CHANGE_REQUEST_EVENT", "예약 변경 요청"),
            Map.entry("GUEST_REQUEST_EVENT", "고객 요청 이력"),
            Map.entry("POLICY_CHANGE", "정책 변경"));

    public byte[] write(AuditEventsView view) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("감사 이력");
            CellStyle headerStyle = headerStyle(workbook);
            writeRow(sheet.createRow(0), HEADERS, headerStyle);
            for (int index = 0; index < view.events().size(); index++) {
                writeRow(sheet.createRow(index + 1), row(view.events().get(index)), null);
            }
            for (int column = 0; column < HEADERS.size(); column++) {
                sheet.autoSizeColumn(column);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("감사 이력 XLSX 파일을 만들지 못했습니다.", exception);
        }
    }

    static List<String> row(AuditEventView event) {
        return List.of(
                value(event.createdAt()),
                value(EVENT_LABELS.getOrDefault(event.eventType(), event.eventType())),
                value(event.staffDisplayName()),
                value(event.staffEmail()),
                value(event.staffRole()),
                value(event.hotelName()),
                value(event.reservationId()),
                value(event.guestName()),
                value(event.roomNumber()),
                value(event.summary()));
    }

    // CSV와 동일하게 spreadsheet formula 접두 문자는 명시적인 텍스트로 정규화한다.
    static String value(String raw) {
        if (raw == null) return "";
        String stripped = raw.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) {
            return "'" + raw;
        }
        return raw;
    }

    private void writeRow(Row row, List<String> values, CellStyle style) {
        for (int column = 0; column < values.size(); column++) {
            var cell = row.createCell(column);
            cell.setCellValue(values.get(column));
            if (style != null) cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }
}
