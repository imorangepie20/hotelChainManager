package team.hotelchain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class AuditEventsXlsxServiceTest {

    @Test
    void writesTheCsvCompatibleColumnsAsSafeStringCells() throws Exception {
        AuditEventView event = new AuditEventView(
                "GUEST_REQUEST_EVENT", "2026-09-27T08:00:00+09:00", "@staff@example.com",
                "=담당자", "HQ_ADMIN", "+hotel", "-지점", null, "=고객", null, "@요약");
        AuditEventsView view = new AuditEventsView(List.of(event), 1, 20, 0, true);

        byte[] bytes = new AuditEventsXlsxService().write(view);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("감사 이력");
            assertThat(sheet.getRow(0).getPhysicalNumberOfCells()).isEqualTo(10);
            assertThat(sheet.getRow(1).getPhysicalNumberOfCells()).isEqualTo(10);
            for (int column = 0; column < AuditEventsXlsxService.HEADERS.size(); column++) {
                assertThat(sheet.getRow(0).getCell(column).getStringCellValue())
                        .isEqualTo(AuditEventsXlsxService.HEADERS.get(column));
            }
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("고객 요청 이력");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("'=담당자");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("'@staff@example.com");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("'=고객");
            assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("'@요약");
            for (int column = 0; column < 10; column++) {
                assertThat(sheet.getRow(1).getCell(column).getCellType()).isEqualTo(CellType.STRING);
            }
        }
    }
}
