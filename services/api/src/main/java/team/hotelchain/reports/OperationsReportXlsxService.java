package team.hotelchain.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** 조회된 운영 통계를 실제 OOXML workbook으로 직렬화한다. DB 상태는 변경하지 않는다. */
@Service
public class OperationsReportXlsxService {

    public byte[] write(OperationsReportView report, String locale) {
        Labels labels = Labels.of(locale);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            writeSummary(workbook.createSheet(labels.summarySheet()), report, labels, header);
            writeHotels(workbook.createSheet(labels.hotelsSheet()), report.hotels(), labels, header);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("운영 통계 XLSX 파일을 만들지 못했습니다.", exception);
        }
    }

    private void writeSummary(
            Sheet sheet, OperationsReportView report, Labels labels, CellStyle header) {
        Row periodHeader = sheet.createRow(0);
        stringCell(periodHeader, 0, labels.period(), header);
        stringCell(periodHeader, 1, labels.from(), header);
        stringCell(periodHeader, 2, labels.to(), header);
        stringCell(periodHeader, 3, labels.days(), header);
        stringCell(periodHeader, 4, labels.timezone(), header);

        Row currentPeriod = sheet.createRow(1);
        stringCell(currentPeriod, 0, labels.currentPeriod(), null);
        stringCell(currentPeriod, 1, report.from(), null);
        stringCell(currentPeriod, 2, report.to(), null);
        numericCell(currentPeriod, 3, report.days());
        stringCell(currentPeriod, 4, "Asia/Seoul", null);

        Row previousPeriod = sheet.createRow(2);
        stringCell(previousPeriod, 0, labels.previousPeriod(), null);
        stringCell(previousPeriod, 1, report.previousPeriod().from(), null);
        stringCell(previousPeriod, 2, report.previousPeriod().to(), null);
        numericCell(previousPeriod, 3, report.previousPeriod().days());
        stringCell(previousPeriod, 4, "Asia/Seoul", null);

        Row metricHeader = sheet.createRow(4);
        stringCell(metricHeader, 0, labels.metric(), header);
        stringCell(metricHeader, 1, labels.currentPeriod(), header);
        stringCell(metricHeader, 2, labels.previousPeriod(), header);

        List<Metric> metrics = List.of(
                new Metric(labels.reservations(), report.totals().reservations(),
                        report.previousPeriod().totals().reservations()),
                new Metric(labels.cancelled(), report.totals().cancelled(),
                        report.previousPeriod().totals().cancelled()),
                new Metric(labels.noShow(), report.totals().noShow(),
                        report.previousPeriod().totals().noShow()),
                new Metric(labels.expired(), report.totals().expired(),
                        report.previousPeriod().totals().expired()),
                new Metric(labels.revenue(), report.totals().revenueKrw(),
                        report.previousPeriod().totals().revenueKrw()),
                new Metric(labels.pending(), report.totals().changeRequestsPending(),
                        report.previousPeriod().totals().changeRequestsPending()),
                new Metric(labels.completed(), report.totals().changeRequestsCompleted(),
                        report.previousPeriod().totals().changeRequestsCompleted()));
        for (int index = 0; index < metrics.size(); index++) {
            Metric metric = metrics.get(index);
            Row row = sheet.createRow(index + 5);
            stringCell(row, 0, metric.name(), null);
            numericCell(row, 1, metric.current());
            numericCell(row, 2, metric.previous());
        }
        for (int column = 0; column < 5; column++) sheet.autoSizeColumn(column);
    }

    private void writeHotels(
            Sheet sheet, List<HotelOperationsMetrics> hotels, Labels labels, CellStyle header) {
        Row titles = sheet.createRow(0);
        String[] columns = { labels.hotel(), labels.region(), labels.reservations(), labels.cancelled(),
                labels.noShow(), labels.expired(), labels.revenue(), labels.occupancy(), labels.pending(),
                labels.completed() };
        for (int index = 0; index < columns.length; index++) stringCell(titles, index, columns[index], header);
        for (int index = 0; index < hotels.size(); index++) {
            HotelOperationsMetrics hotel = hotels.get(index);
            Row row = sheet.createRow(index + 1);
            // 명칭은 수식이 아닌 명시적인 문자열 셀로 저장해 spreadsheet formula injection을 막는다.
            stringCell(row, 0, hotel.hotelName(), null);
            stringCell(row, 1, hotel.region(), null);
            numericCell(row, 2, hotel.reservations());
            numericCell(row, 3, hotel.cancelled());
            numericCell(row, 4, hotel.noShow());
            numericCell(row, 5, hotel.expired());
            numericCell(row, 6, hotel.revenueKrw());
            numericCell(row, 7, hotel.occupancyRate() * 100.0d);
            numericCell(row, 8, hotel.changeRequestsPending());
            numericCell(row, 9, hotel.changeRequestsCompleted());
        }
        for (int column = 0; column < columns.length; column++) sheet.autoSizeColumn(column);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void stringCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) cell.setCellStyle(style);
    }

    private void numericCell(Row row, int column, double value) {
        row.createCell(column).setCellValue(value);
    }

    private record Metric(String name, long current, long previous) {}

    private record Labels(
            String summarySheet, String hotelsSheet, String period, String from, String to, String days,
            String timezone, String currentPeriod, String previousPeriod, String metric, String hotel,
            String region, String reservations, String cancelled, String noShow, String expired,
            String revenue, String occupancy, String pending, String completed) {

        private static Labels of(String locale) {
            if ("ko".equals(locale)) {
                return new Labels("요약", "지점", "기간", "시작일", "종료일", "일수", "시간대",
                        "현재 기간", "이전 기간", "지표", "지점", "지역", "예약", "취소", "노쇼",
                        "만료", "매출", "점유율", "변경 대기", "변경 완료");
            }
            if ("en".equals(locale)) {
                return new Labels("Summary", "Hotels", "Period", "From", "To", "Days", "Timezone",
                        "Current period", "Previous period", "Metric", "Hotel", "Region", "Reservations",
                        "Cancelled", "No-show", "Expired", "Revenue", "Occupancy", "Changes pending",
                        "Changes completed");
            }
            throw new IllegalArgumentException("locale은 ko 또는 en이어야 합니다.");
        }
    }
}
