package team.hotelchain.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class OperationsReportXlsxServiceTest {

    private final OperationsReportXlsxService service = new OperationsReportXlsxService();

    @Test
    void writesNumericMetricsAndKeepsUntrustedNamesAsStrings() throws Exception {
        OperationsReportView.Totals current = new OperationsReportView.Totals(
                8, 1, 1, 0, 800_000, 1, 1);
        OperationsReportView.Totals previous = new OperationsReportView.Totals(
                4, 2, 0, 1, 1_000_000, 2, 0);
        OperationsReportView report = new OperationsReportView(
                "2026-09-14", "2026-09-20", 7,
                List.of(
                        new HotelOperationsMetrics(
                                "hotel-1", "=1+1", "+cmd|' /C calc'!A0",
                                8, 1, 1, 0, 800_000, 1, 1, 0.25d),
                        new HotelOperationsMetrics(
                                "hotel-2", "-1+1", "@SUM(A1:A2)",
                                2, 0, 0, 0, 200_000, 0, 0, 0.50d)),
                current,
                new OperationsReportView.PreviousPeriod(
                        "2026-09-07", "2026-09-13", 7, previous));

        byte[] bytes = service.write(report, "en");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("Summary");
            assertThat(workbook.getSheetName(1)).isEqualTo("Hotels");

            Row hotel = workbook.getSheet("Hotels").getRow(1);
            assertThat(hotel.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(hotel.getCell(0).getStringCellValue()).isEqualTo("=1+1");
            assertThat(hotel.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(hotel.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(hotel.getCell(6).getNumericCellValue()).isEqualTo(800_000d);
            assertThat(hotel.getCell(7).getNumericCellValue()).isEqualTo(25d);

            Row secondHotel = workbook.getSheet("Hotels").getRow(2);
            assertThat(secondHotel.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(secondHotel.getCell(0).getStringCellValue()).isEqualTo("-1+1");
            assertThat(secondHotel.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(secondHotel.getCell(1).getStringCellValue()).isEqualTo("@SUM(A1:A2)");

            Row currentPeriod = workbook.getSheet("Summary").getRow(1);
            assertThat(currentPeriod.getCell(1).getStringCellValue()).isEqualTo("2026-09-14");
            assertThat(currentPeriod.getCell(2).getStringCellValue()).isEqualTo("2026-09-20");
            assertThat(currentPeriod.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(currentPeriod.getCell(3).getNumericCellValue()).isEqualTo(7d);
            assertThat(currentPeriod.getCell(4).getStringCellValue()).isEqualTo("Asia/Seoul");

            Row previousPeriod = workbook.getSheet("Summary").getRow(2);
            assertThat(previousPeriod.getCell(1).getStringCellValue()).isEqualTo("2026-09-07");
            assertThat(previousPeriod.getCell(2).getStringCellValue()).isEqualTo("2026-09-13");
            assertThat(previousPeriod.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(previousPeriod.getCell(3).getNumericCellValue()).isEqualTo(7d);

            Row reservations = workbook.getSheet("Summary").getRow(5);
            assertThat(reservations.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(reservations.getCell(1).getNumericCellValue()).isEqualTo(8d);
            assertThat(reservations.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(reservations.getCell(2).getNumericCellValue()).isEqualTo(4d);

            workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell ->
                    assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA))));
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(service.write(report, "ko")))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("요약");
            assertThat(workbook.getSheetName(1)).isEqualTo("지점");
            assertThat(workbook.getSheet("지점").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("지점");
            assertThat(workbook.getSheet("지점").getRow(0).getCell(6).getStringCellValue())
                    .isEqualTo("매출");
        }
    }

    @Test
    void rejectsUnsupportedLocale() {
        OperationsReportView.Totals zero = new OperationsReportView.Totals(0, 0, 0, 0, 0, 0, 0);
        OperationsReportView report = new OperationsReportView(
                "2026-09-14", "2026-09-20", 7, List.of(), zero,
                new OperationsReportView.PreviousPeriod(
                        "2026-09-07", "2026-09-13", 7, zero));

        assertThatThrownBy(() -> service.write(report, "ja"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
