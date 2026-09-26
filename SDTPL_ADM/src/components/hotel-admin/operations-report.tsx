"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { Download, RefreshCw, TrendingUp } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  buildOperationsReportCsv,
  downloadOperationsReportXlsx,
  getOperationsReport,
  getRoomTypeRevenue,
  StaffApiError,
  type OperationsReport,
  type OperationsReportLocale,
  type RoomTypeRevenue,
  type StaffPrincipal,
} from "@/lib/staff-api";

const rangePresets = [7, 14, 30] as const;

const copy = {
  ko: {
    heading: "운영 통계",
    description:
      "지점별 예약·매출·점유율과 예약 변경 현황을 읽기 전용으로 확인합니다.",
    refresh: "새로고침",
    loading: "불러오는 중",
    csv: "CSV 내보내기",
    xlsx: "XLSX 내보내기",
    from: "시작일",
    to: "종료일",
    hotel: "객실 유형별 대상 지점",
    currentPeriod: "조회 기간",
    previousPeriod: "이전 동일 기간",
    reservations: "예약 건수",
    revenue: "매출",
    pending: "변경 승인 대기",
    completed: "변경 완료",
    newValue: "신규",
    cancelled: "취소",
    noShow: "노쇼",
    expired: "만료",
    metrics: "지점별 통계",
    metricsDescription:
      "점유율은 숙박일별 가용 재고 대비 확정 건수로 계산합니다. 표는 차트와 같은 값을 제공합니다.",
    hotelColumn: "지점",
    occupancy: "점유율",
    pendingShort: "변경 대기",
    completedShort: "변경 완료",
    cancellationRate: "취소·노쇼율",
    chart: "지점별 매출 및 점유율 차트",
    chartDescription: "막대는 지점별 매출을 나타내며 점유율은 도움말과 표에서 확인할 수 있습니다.",
    roomTypeRevenue: "객실 유형별 매출",
    roomTypeDescription:
      "취소·노쇼 예약 금액은 제외하며 비중은 지점 매출에서 차지하는 몫입니다.",
    roomType: "객실 유형",
    roomRevenue: "객실 매출",
    revenueShare: "매출 비중",
    noRoomTypes: "선택한 기간에 객실 유형별 매출이 없습니다.",
    empty: "선택한 기간에 집계된 운영 통계가 없거나 불러오는 중입니다.",
    forbidden: "운영 통계는 본사 관리자만 확인할 수 있습니다.",
    staffError: "직원 정보를 읽지 못했습니다.",
    loadError: "운영 통계를 불러오지 못했습니다.",
    roomTypeError: "객실 유형별 매출을 불러오지 못했습니다.",
    exportError: "XLSX 파일을 내려받지 못했습니다.",
    unauthorized: "직원 세션이 만료되었습니다. 다시 로그인해 주세요.",
    hotelNotFound: "지점을 찾을 수 없습니다.",
    revenueNote: "취소·노쇼 예약의 금액은 제외합니다.",
    pendingNote: "진행 중인 예약 변경 요청",
    completedNote: "완료된 예약 변경 요청",
    days: "일",
  },
  en: {
    heading: "Operations report",
    description:
      "Review reservation, revenue, occupancy, and change-request metrics by hotel in read-only mode.",
    refresh: "Refresh",
    loading: "Loading",
    csv: "Export CSV",
    xlsx: "Export XLSX",
    from: "Start date",
    to: "End date",
    hotel: "Hotel for room-type revenue",
    currentPeriod: "Current period",
    previousPeriod: "Previous period",
    reservations: "Reservations",
    revenue: "Revenue",
    pending: "Pending changes",
    completed: "Completed changes",
    newValue: "New",
    cancelled: "Cancelled",
    noShow: "No-show",
    expired: "Expired",
    metrics: "Metrics by hotel",
    metricsDescription:
      "Occupancy uses confirmed stays over available inventory. The table provides the same values as the chart.",
    hotelColumn: "Hotel",
    occupancy: "Occupancy",
    pendingShort: "Pending",
    completedShort: "Completed",
    cancellationRate: "Cancel/no-show rate",
    chart: "Revenue and occupancy by hotel chart",
    chartDescription:
      "Bars show revenue by hotel; occupancy is available in the tooltip and data table.",
    roomTypeRevenue: "Revenue by room type",
    roomTypeDescription:
      "Revenue excludes cancelled and no-show reservations; share is relative to hotel revenue.",
    roomType: "Room type",
    roomRevenue: "Room amount",
    revenueShare: "Share",
    noRoomTypes: "No room-type revenue exists for the selected period.",
    empty: "No operations metrics exist for the selected period, or they are loading.",
    forbidden: "Operations reports are available to headquarters administrators only.",
    staffError: "Could not read the staff profile.",
    loadError: "Could not load the operations report.",
    roomTypeError: "Could not load room-type revenue.",
    exportError: "Could not download the XLSX file.",
    unauthorized: "Your staff session has expired. Please sign in again.",
    hotelNotFound: "The hotel could not be found.",
    revenueNote: "Cancelled and no-show reservation amounts are excluded.",
    pendingNote: "Reservation change requests in progress",
    completedNote: "Completed reservation change requests",
    days: "days",
  },
} as const;

type Failure = {
  kind: "staff" | "report" | "roomType" | "export";
  status?: number;
};

function failureMessage(failure: Failure, locale: OperationsReportLocale) {
  const t = copy[locale];
  if (failure.status === 401) return t.unauthorized;
  if (failure.status === 403) return t.forbidden;
  if (failure.status === 404) return t.hotelNotFound;
  if (failure.kind === "staff") return t.staffError;
  if (failure.kind === "roomType") return t.roomTypeError;
  if (failure.kind === "export") return t.exportError;
  return t.loadError;
}

function kstToday() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function addDays(value: string, amount: number) {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + amount);
  return [
    String(date.getFullYear()).padStart(4, "0"),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

function defaultRange(days: number) {
  const to = kstToday();
  return { from: addDays(to, -(days - 1)), to };
}

function money(value: number, locale: OperationsReportLocale) {
  if (locale === "ko") return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "KRW",
    maximumFractionDigits: 0,
  }).format(value);
}

function percent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function displayDate(value: string, locale: OperationsReportLocale) {
  return new Intl.DateTimeFormat(locale === "ko" ? "ko-KR" : "en-US", {
    timeZone: "UTC",
    month: locale === "ko" ? "long" : "short",
    day: "numeric",
  }).format(new Date(`${value}T00:00:00Z`));
}

function deltaText(
  current: number,
  previous: number,
  locale: OperationsReportLocale,
) {
  const difference = current - previous;
  const signed = difference > 0 ? `+${difference}` : String(difference);
  if (previous === 0) {
    if (current > 0) return `${signed} · ${copy[locale].newValue}`;
    return "0 · 0.0%";
  }
  const rate = (difference / previous) * 100;
  const signedRate = rate > 0 ? `+${rate.toFixed(1)}%` : `${rate.toFixed(1)}%`;
  return `${signed} · ${signedRate}`;
}

export function OperationsReport() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [report, setReport] = useState<OperationsReport | null>(null);
  const [range, setRange] = useState(defaultRange(7));
  const [resolvedRange, setResolvedRange] = useState<{
    from: string;
    to: string;
  } | null>(null);
  const [hotelId, setHotelId] = useState("");
  const [roomTypes, setRoomTypes] = useState<RoomTypeRevenue | null>(null);
  const [locale, setLocale] = useState<OperationsReportLocale>("ko");
  const [loading, setLoading] = useState(false);
  const [roomTypeRefresh, setRoomTypeRefresh] = useState(0);
  const [failure, setFailure] = useState<Failure | null>(null);
  const reportGeneration = useRef(0);
  const roomTypeGeneration = useRef(0);
  const mounted = useRef(false);
  const t = copy[locale];
  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const sessionToken =
    typeof window !== "undefined"
      ? window.localStorage.getItem("hotel-chain-staff-session")
      : null;

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as StaffPrincipal;
        queueMicrotask(() => {
          if (mounted.current) setStaff(parsed);
        });
      } catch {
        queueMicrotask(() => {
          if (mounted.current) setFailure({ kind: "staff" });
        });
      }
    }
    return () => {
      mounted.current = false;
      reportGeneration.current += 1;
      roomTypeGeneration.current += 1;
    };
  }, []);

  const loadReport = useCallback(async () => {
    const generation = ++reportGeneration.current;
    if (!sessionToken) return;
    const requestedRange = range;
    setLoading(true);
    setFailure(null);
    try {
      const next = await getOperationsReport(sessionToken, range);
      if (!mounted.current || reportGeneration.current !== generation) return;
      setReport(next);
      setResolvedRange(requestedRange);
      if (next.hotels.length === 0) {
        roomTypeGeneration.current += 1;
        setRoomTypes(null);
      }
      setHotelId((current) =>
        next.hotels.some((hotel) => hotel.hotelId === current)
          ? current
          : (next.hotels[0]?.hotelId ?? ""),
      );
    } catch (cause) {
      if (mounted.current && reportGeneration.current === generation) {
        roomTypeGeneration.current += 1;
        setReport(null);
        setResolvedRange(null);
        setRoomTypes(null);
        setFailure({
          kind: "report",
          status: cause instanceof StaffApiError ? cause.status : undefined,
        });
      }
    } finally {
      if (mounted.current && reportGeneration.current === generation) setLoading(false);
    }
  }, [range, sessionToken]);

  useEffect(() => {
    if (isHeadquarters) queueMicrotask(() => void loadReport());
  }, [isHeadquarters, loadReport]);

  useEffect(() => {
    const generation = ++roomTypeGeneration.current;
    if (!isHeadquarters || !sessionToken || !hotelId) {
      return;
    }
    queueMicrotask(() => {
      if (mounted.current && roomTypeGeneration.current === generation) {
        setRoomTypes(null);
        setFailure((current) =>
          current?.kind === "roomType" ? null : current,
        );
      }
    });
    void getRoomTypeRevenue(sessionToken, hotelId, range)
      .then((next) => {
        if (mounted.current && roomTypeGeneration.current === generation) {
          setRoomTypes(next);
          setFailure((current) =>
            current?.kind === "roomType" ? null : current,
          );
        }
      })
      .catch((cause) => {
        if (mounted.current && roomTypeGeneration.current === generation) {
          setRoomTypes(null);
          setFailure({
            kind: "roomType",
            status: cause instanceof StaffApiError ? cause.status : undefined,
          });
        }
      });
  }, [hotelId, isHeadquarters, range, roomTypeRefresh, sessionToken]);

  const exportReady =
    report !== null &&
    resolvedRange?.from === range.from &&
    resolvedRange?.to === range.to;

  function downloadCsv() {
    if (!report || !exportReady) return;
    const blob = new Blob(["﻿" + buildOperationsReportCsv(report)], {
      type: "text/csv;charset=utf-8",
    });
    downloadBlob(blob, `operations-report-${report.from}-${report.to}.csv`);
  }

  async function downloadXlsx() {
    if (!report || !sessionToken || !exportReady) return;
    setFailure((current) => (current?.kind === "export" ? null : current));
    try {
      const downloaded = await downloadOperationsReportXlsx(sessionToken, {
        from: report.from,
        to: report.to,
        locale,
      });
      downloadBlob(downloaded.blob, downloaded.filename);
    } catch (cause) {
      setFailure({
        kind: "export",
        status: cause instanceof StaffApiError ? cause.status : undefined,
      });
    }
  }

  function downloadBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  const hotels = report?.hotels ?? [];
  const totals = report?.totals;
  const previous = report?.previousPeriod;
  const chartConfig = {
    revenueKrw: { label: t.revenue, color: "var(--chart-3)" },
    occupancyPercent: { label: t.occupancy, color: "var(--chart-2)" },
  } satisfies ChartConfig;
  const chartData = hotels.map((hotel) => ({
    ...hotel,
    occupancyPercent: hotel.occupancyRate * 100,
  }));
  const chartLabel = `${t.chart}. ${hotels
    .map(
      (hotel) =>
        `${hotel.hotelName}: ${money(hotel.revenueKrw, locale)}, ${percent(hotel.occupancyRate)}`,
    )
    .join("; ")}`;

  return (
    <div className="flex min-w-0 max-w-full flex-col gap-4" lang={locale}>
      <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <TrendingUp className="h-5 w-5 shrink-0" aria-hidden />
            {t.heading}
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">{t.description}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex rounded-lg border p-0.5" aria-label="Language">
            <Button
              type="button"
              variant={locale === "ko" ? "secondary" : "ghost"}
              size="sm"
              aria-pressed={locale === "ko"}
              onClick={() => setLocale("ko")}
            >
              한국어
            </Button>
            <Button
              type="button"
              variant={locale === "en" ? "secondary" : "ghost"}
              size="sm"
              aria-pressed={locale === "en"}
              onClick={() => setLocale("en")}
            >
              English
            </Button>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setRoomTypeRefresh((current) => current + 1);
              void loadReport();
            }}
            disabled={loading || !isHeadquarters}
          >
            <RefreshCw className="h-4 w-4" aria-hidden />
            {loading ? t.loading : t.refresh}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={downloadCsv}
            disabled={loading || !isHeadquarters || !exportReady}
            data-testid="report-csv"
          >
            <Download className="h-4 w-4" aria-hidden />
            {t.csv}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => void downloadXlsx()}
            disabled={loading || !isHeadquarters || !exportReady}
            data-testid="report-xlsx"
          >
            <Download className="h-4 w-4" aria-hidden />
            {t.xlsx}
          </Button>
        </div>
      </div>

      {failure && <p role="alert" className="break-words text-sm text-destructive">{failureMessage(failure, locale)}</p>}
      {staff && !isHeadquarters && <p role="status" className="text-sm text-muted-foreground">{t.forbidden}</p>}

      {isHeadquarters && (
        <div className="grid min-w-0 gap-3 sm:grid-cols-2 lg:flex lg:flex-wrap lg:items-end">
          <div className="grid min-w-0 gap-1">
            <Label htmlFor="report-from">{t.from}</Label>
            <Input
              id="report-from"
              type="date"
              value={range.from}
              onChange={(event) => setRange((old) => ({ ...old, from: event.target.value }))}
              data-testid="report-from"
            />
          </div>
          <div className="grid min-w-0 gap-1">
            <Label htmlFor="report-to">{t.to}</Label>
            <Input
              id="report-to"
              type="date"
              value={range.to}
              onChange={(event) => setRange((old) => ({ ...old, to: event.target.value }))}
              data-testid="report-to"
            />
          </div>
          <div className="grid min-w-0 gap-1 sm:col-span-2 lg:col-span-1">
            <Label htmlFor="report-hotel">{t.hotel}</Label>
            <select
              id="report-hotel"
              aria-label={t.hotel}
              className="h-9 min-w-0 max-w-full rounded-lg border bg-background px-3"
              value={hotelId}
              onChange={(event) => {
                setRoomTypes(null);
                setHotelId(event.target.value);
              }}
              data-testid="report-hotel"
            >
              {hotels.map((hotel) => (
                <option key={hotel.hotelId} value={hotel.hotelId}>{hotel.hotelName}</option>
              ))}
            </select>
          </div>
          <div className="flex flex-wrap gap-1.5 pb-0.5 sm:col-span-2 lg:col-span-1">
            {rangePresets.map((days) => {
              const preset = defaultRange(days);
              return (
                <Button
                  key={days}
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-pressed={range.from === preset.from && range.to === preset.to}
                  onClick={() => setRange(preset)}
                  data-testid={`report-preset-${days}`}
                >
                  {days}{locale === "ko" ? "일" : " days"}
                </Button>
              );
            })}
          </div>
        </div>
      )}

      {isHeadquarters && report && (
        <div className="grid gap-1 text-sm text-muted-foreground">
          <p data-testid="report-range">
            {displayDate(report.from, locale)} ~ {displayDate(report.to, locale)} · {report.days}{locale === "ko" ? "일" : " days"}
          </p>
          {previous && (
            <p data-testid="report-previous-range">
              <span>{t.previousPeriod}</span>{" "}
              {displayDate(previous.from, locale)} ~ {displayDate(previous.to, locale)}
            </p>
          )}
        </div>
      )}

      {isHeadquarters && totals && (
        <div className="grid min-w-0 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard
            label={t.reservations}
            value={String(totals.reservations)}
            valueTestId="report-total-reservations"
            change={previous ? deltaText(totals.reservations, previous.totals.reservations, locale) : undefined}
            changeTestId="report-reservations-change"
            note={`${t.cancelled} ${totals.cancelled} · ${t.noShow} ${totals.noShow} · ${t.expired} ${totals.expired}`}
          />
          <MetricCard
            label={t.revenue}
            value={money(totals.revenueKrw, locale)}
            valueTestId="report-total-revenue"
            change={previous ? deltaText(totals.revenueKrw, previous.totals.revenueKrw, locale) : undefined}
            changeTestId="report-revenue-change"
            note={t.revenueNote}
          />
          <MetricCard
            label={t.pending}
            value={String(totals.changeRequestsPending)}
            valueTestId="report-total-pending"
            change={previous ? deltaText(totals.changeRequestsPending, previous.totals.changeRequestsPending, locale) : undefined}
            changeTestId="report-pending-change"
            note={t.pendingNote}
          />
          <MetricCard
            label={t.completed}
            value={String(totals.changeRequestsCompleted)}
            valueTestId="report-total-completed"
            change={previous ? deltaText(totals.changeRequestsCompleted, previous.totals.changeRequestsCompleted, locale) : undefined}
            changeTestId="report-completed-change"
            note={t.completedNote}
          />
        </div>
      )}

      {hotels.length > 0 && (
        <Card className="min-w-0 overflow-hidden">
          <CardHeader>
            <CardTitle id="report-chart-title" className="text-base">{t.chart}</CardTitle>
            <CardDescription id="report-chart-description">{t.chartDescription}</CardDescription>
          </CardHeader>
          <CardContent className="min-w-0">
            <figure
              role="img"
              aria-label={chartLabel}
              aria-describedby="report-chart-description report-chart-data-note"
              className="min-w-0"
            >
              <ChartContainer config={chartConfig} className="h-64 w-full min-w-0">
                <BarChart data={chartData} margin={{ left: 0, right: 0 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="hotelName" tickLine={false} axisLine={false} />
                  <YAxis yAxisId="revenue" tickLine={false} axisLine={false} width={48} tickFormatter={(value) => `${Math.round(Number(value) / 1000)}k`} />
                  <YAxis yAxisId="occupancy" orientation="right" domain={[0, 100]} tickLine={false} axisLine={false} width={36} tickFormatter={(value) => `${value}%`} />
                  <ChartTooltip
                    content={
                      <ChartTooltipContent
                        formatter={(value, name) =>
                          name === "occupancyPercent"
                            ? `${t.occupancy}: ${Number(value).toFixed(1)}%`
                            : `${t.revenue}: ${money(Number(value), locale)}`
                        }
                      />
                    }
                  />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar yAxisId="revenue" dataKey="revenueKrw" fill="var(--color-revenueKrw)" radius={4} />
                  <Bar yAxisId="occupancy" dataKey="occupancyPercent" fill="var(--color-occupancyPercent)" radius={4} />
                </BarChart>
              </ChartContainer>
              <figcaption className="sr-only">{t.metricsDescription}</figcaption>
            </figure>
            <p id="report-chart-data-note" className="sr-only">
              {locale === "ko"
                ? "동일한 값은 다음 지점별 통계 표에서 확인할 수 있습니다."
                : "The same values are available in the following metrics table."}
            </p>
          </CardContent>
        </Card>
      )}

      {hotels.length > 0 && (
        <Card className="min-w-0 overflow-hidden">
          <CardHeader>
            <CardTitle className="text-base">{t.metrics}</CardTitle>
            <CardDescription>{t.metricsDescription}</CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table id="report-hotel-data-table" aria-label={t.metrics}>
              <caption className="sr-only">{t.metricsDescription}</caption>
              <TableHeader><TableRow>
                <TableHead>{t.hotelColumn}</TableHead><TableHead className="text-right">{t.reservations}</TableHead>
                <TableHead className="text-right">{t.cancelled}</TableHead><TableHead className="text-right">{t.noShow}</TableHead>
                <TableHead className="text-right">{t.revenue}</TableHead><TableHead className="text-right">{t.occupancy}</TableHead>
                <TableHead className="text-right">{t.pendingShort}</TableHead><TableHead className="text-right">{t.completedShort}</TableHead>
              </TableRow></TableHeader>
              <TableBody>{hotels.map((hotel) => {
                const cancellationRate = hotel.reservations === 0 ? 0 : (hotel.cancelled + hotel.noShow) / hotel.reservations;
                return <TableRow key={hotel.hotelId}>
                  <TableCell><div className="grid gap-0.5"><span className="whitespace-nowrap font-medium">{hotel.hotelName}</span><span className="text-xs text-muted-foreground">{hotel.region}</span></div></TableCell>
                  <TableCell className="text-right tabular-nums" data-testid="report-reservations">{hotel.reservations}</TableCell>
                  <TableCell className="text-right tabular-nums"><div className="grid justify-items-end gap-0.5"><span>{hotel.cancelled}</span><span className="text-xs text-muted-foreground">{t.cancellationRate} {(cancellationRate * 100).toFixed(1)}%</span></div></TableCell>
                  <TableCell className="text-right tabular-nums">{hotel.noShow}</TableCell>
                  <TableCell className="whitespace-nowrap text-right tabular-nums">{money(hotel.revenueKrw, locale)}</TableCell>
                  <TableCell className="text-right tabular-nums"><Badge variant={hotel.occupancyRate >= 0.8 ? "default" : "secondary"}>{percent(hotel.occupancyRate)}</Badge></TableCell>
                  <TableCell className="text-right tabular-nums">{hotel.changeRequestsPending}</TableCell>
                  <TableCell className="text-right tabular-nums">{hotel.changeRequestsCompleted}</TableCell>
                </TableRow>;
              })}</TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && roomTypes && roomTypes.roomTypes.length > 0 && (
        <Card className="min-w-0 overflow-hidden">
          <CardHeader><CardTitle className="text-base">{roomTypes.hotelName} · {t.roomTypeRevenue}</CardTitle><CardDescription>{t.roomTypeDescription}</CardDescription></CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table><TableHeader><TableRow>
              <TableHead>{t.roomType}</TableHead><TableHead className="text-right">{t.reservations}</TableHead>
              <TableHead className="text-right">{t.cancelled}</TableHead><TableHead className="text-right">{t.noShow}</TableHead>
              <TableHead className="text-right">{t.roomRevenue}</TableHead><TableHead className="text-right">{t.revenueShare}</TableHead>
            </TableRow></TableHeader><TableBody>{roomTypes.roomTypes.map((row) => (
              <TableRow key={row.roomTypeId}>
                <TableCell className="whitespace-nowrap font-medium">{row.roomTypeName}</TableCell>
                <TableCell className="text-right tabular-nums">{row.reservations}</TableCell><TableCell className="text-right tabular-nums">{row.cancelled}</TableCell>
                <TableCell className="text-right tabular-nums">{row.noShow}</TableCell><TableCell className="whitespace-nowrap text-right tabular-nums">{money(row.revenueKrw, locale)}</TableCell>
                <TableCell className="text-right tabular-nums"><div className="grid justify-items-end gap-0.5"><Badge variant={row.revenueShare >= 0.5 ? "default" : "secondary"}>{percent(row.revenueShare)}</Badge><span className="text-xs text-muted-foreground">{money(row.revenueKrw, locale)}</span></div></TableCell>
              </TableRow>
            ))}</TableBody></Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && ((roomTypes && roomTypes.roomTypes.length === 0) || (report && hotels.length === 0)) && !failure && (
        <p className="text-sm text-muted-foreground" data-testid="room-type-empty">{t.noRoomTypes}</p>
      )}
      {isHeadquarters && hotels.length === 0 && !failure && (
        <p className="text-sm text-muted-foreground" data-testid="report-empty">{t.empty}</p>
      )}
    </div>
  );
}

function MetricCard({
  label,
  value,
  valueTestId,
  change,
  changeTestId,
  note,
}: {
  label: string;
  value: string;
  valueTestId: string;
  change?: string;
  changeTestId: string;
  note: string;
}) {
  return (
    <Card className="min-w-0">
      <CardHeader className="pb-2">
        <CardDescription>{label}</CardDescription>
        <CardTitle className="break-words tabular-nums" data-testid={valueTestId}>{value}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-1 text-xs text-muted-foreground">
        {change && <span className="font-medium tabular-nums text-foreground" data-testid={changeTestId}>{change}</span>}
        <span>{note}</span>
      </CardContent>
    </Card>
  );
}
