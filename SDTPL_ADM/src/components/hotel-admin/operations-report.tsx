"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw, TrendingUp } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  getOperationsReport,
  StaffApiError,
  type OperationsReport,
  type StaffPrincipal,
} from "@/lib/staff-api";

const rangePresets = [
  { days: 7, label: "7일" },
  { days: 14, label: "14일" },
  { days: 30, label: "30일" },
];

function isoDate(value: Date) {
  return value.toISOString().slice(0, 10);
}

function defaultRange(days: number) {
  const start = new Date();
  return { from: isoDate(start), to: isoDate(new Date(start.getTime() + (days - 1) * 86400000)) };
}

function money(value: number) {
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function percent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric" })
    .format(new Date(`${value}T00:00:00`));
}

export function OperationsReport() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [report, setReport] = useState<OperationsReport | null>(null);
  const [range, setRange] = useState(defaultRange(7));
  const from = range.from;
  const to = range.to;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const sessionToken = typeof window !== "undefined" ? window.localStorage.getItem("hotel-chain-staff-session") : null;

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      setStaff(JSON.parse(stored) as StaffPrincipal);
      requestGeneration.current += 1;
    } catch {
      setError("직원 정보를 읽지 못했습니다.");
    }
    return () => {
      mounted.current = false;
      requestGeneration.current += 1;
    };
  }, []);

  const loadReport = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken) return;
    setLoading(true);
    setError("");
    try {
      const next = await getOperationsReport(sessionToken, { from, to });
      if (!mounted.current || requestGeneration.current !== generation) return;
      setReport(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setReport(null);
        setError(cause instanceof StaffApiError ? cause.message : "운영 통계를 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, [from, to, sessionToken]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void loadReport();
  }, [isHeadquarters, loadReport]);

  const hotels = report?.hotels ?? [];
  const totals = report?.totals;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <TrendingUp className="h-5 w-5" aria-hidden />
            운영 통계
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            지점별 예약 건수·매출·점유율·취소율·노쇼율과 예약 변경 승인 대기·완료 건수를 읽기 전용으로 확인한다.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void loadReport()} disabled={loading || !isHeadquarters}>
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          운영 통계는 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1">
            <Label htmlFor="report-from">시작일</Label>
            <Input
              id="report-from"
              type="date"
              value={from}
              onChange={(event) => setRange((prev) => ({ ...prev, from: event.target.value }))}
              data-testid="report-from"
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="report-to">종료일</Label>
            <Input
              id="report-to"
              type="date"
              value={to}
              onChange={(event) => setRange((prev) => ({ ...prev, to: event.target.value }))}
              data-testid="report-to"
            />
          </div>
          <div className="flex flex-wrap gap-1.5 pb-0.5">
            {rangePresets.map((preset) => (
              <Button
                key={preset.days}
                type="button"
                variant="outline"
                size="sm"
                aria-pressed={from === defaultRange(preset.days).from && to === defaultRange(preset.days).to}
                onClick={() => setRange(defaultRange(preset.days))}
                data-testid={`report-preset-${preset.days}`}
              >
                {preset.label}
              </Button>
            ))}
          </div>
        </div>
      )}

      {isHeadquarters && report && (
        <p className="text-sm text-muted-foreground" data-testid="report-range">
          {displayDate(report.from)} ~ {displayDate(report.to)} · {report.days}일
        </p>
      )}

      {isHeadquarters && totals && (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>예약 건수</CardDescription>
              <CardTitle className="tabular-nums" data-testid="report-total-reservations">
                {totals.reservations}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-muted-foreground">
              취소 {totals.cancelled} · 노쇼 {totals.noShow} · 만료 {totals.expired}
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>매출</CardDescription>
              <CardTitle className="tabular-nums" data-testid="report-total-revenue">
                {money(totals.revenueKrw)}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-muted-foreground">
              취소·노쇼 예약의 금액은 제외한다.
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>변경 승인 대기</CardDescription>
              <CardTitle className="tabular-nums" data-testid="report-total-pending">
                {totals.changeRequestsPending}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-muted-foreground">진행 중인 예약 변경 요청</CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>변경 완료</CardDescription>
              <CardTitle className="tabular-nums" data-testid="report-total-completed">
                {totals.changeRequestsCompleted}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-muted-foreground">완료된 예약 변경 요청</CardContent>
          </Card>
        </div>
      )}

      {hotels.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">지점별 통계</CardTitle>
            <CardDescription>
              점유율은 숙박일별 가용 재고 대비 확정 건수로 계산한다. 화면에서 값을 변경하지 않는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>지점</TableHead>
                  <TableHead className="text-right">예약</TableHead>
                  <TableHead className="text-right">취소</TableHead>
                  <TableHead className="text-right">노쇼</TableHead>
                  <TableHead className="text-right">매출</TableHead>
                  <TableHead className="text-right">점유율</TableHead>
                  <TableHead className="text-right">변경 대기</TableHead>
                  <TableHead className="text-right">변경 완료</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {hotels.map((hotel) => {
                  const cancellationRate = hotel.reservations === 0
                    ? 0
                    : (hotel.cancelled + hotel.noShow) / hotel.reservations;
                  return (
                    <TableRow key={hotel.hotelId}>
                      <TableCell>
                        <div className="grid gap-0.5">
                          <span className="font-medium whitespace-nowrap">{hotel.hotelName}</span>
                          <span className="text-xs text-muted-foreground">{hotel.region}</span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right tabular-nums" data-testid="report-reservations">
                        {hotel.reservations}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        <div className="grid justify-items-end gap-0.5">
                          <span>{hotel.cancelled}</span>
                          <span className="text-xs text-muted-foreground">
                            취소·노쇼율 {(cancellationRate * 100).toFixed(1)}%
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{hotel.noShow}</TableCell>
                      <TableCell className="text-right tabular-nums whitespace-nowrap">{money(hotel.revenueKrw)}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        <Badge variant={hotel.occupancyRate >= 0.8 ? "default" : "secondary"}>
                          {percent(hotel.occupancyRate)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{hotel.changeRequestsPending}</TableCell>
                      <TableCell className="text-right tabular-nums">{hotel.changeRequestsCompleted}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && hotels.length === 0 && !error && (
        <p className="text-sm text-muted-foreground" data-testid="report-empty">
          선택한 기간에 집계된 운영 통계가 없거나 불러오는 중입니다.
        </p>
      )}
    </div>
  );
}
