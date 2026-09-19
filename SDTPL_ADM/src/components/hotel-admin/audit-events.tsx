"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { History, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  auditEventLabels,
  getAuditEvents,
  StaffApiError,
  type AuditEvent,
  type AuditEventType,
  type StaffPrincipal,
} from "@/lib/staff-api";

const PAGE_SIZE = 20;

function eventLabel(eventType: string) {
  return auditEventLabels[eventType as AuditEventType] ?? eventType;
}

function dateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
}

export function AuditEvents() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [offset, setOffset] = useState(0);
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

  const refresh = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken) return;
    setLoading(true);
    setError("");
    try {
      const next = await getAuditEvents(sessionToken, { limit: PAGE_SIZE, offset });
      if (!mounted.current || requestGeneration.current !== generation) return;
      setEvents(next.events);
      setTotalCount(next.totalCount);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setEvents([]);
        setTotalCount(0);
        setError(cause instanceof StaffApiError ? cause.message : "감사 이력을 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, [offset, sessionToken]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void refresh();
  }, [isHeadquarters, refresh]);

  function gotoPage(next: number) {
    const page = Math.max(0, next);
    if (page === offset) return;
    setOffset(page);
  }

  const lastPage = Math.max(0, Math.ceil(totalCount / PAGE_SIZE) - 1);
  const pageIndex = Math.min(offset, lastPage);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <History className="h-5 w-5" aria-hidden />
            감사 이력
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            예약자 정정·인원·객실 재배정·일정 변경·취소·운영 상태 전환 이력을 읽기 전용으로 확인한다.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void refresh()} disabled={loading || !isHeadquarters}>
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          감사 이력은 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && events.length === 0 && !error && !loading && (
        <p className="text-sm text-muted-foreground">해당 범위에 감사 이력이 없습니다.</p>
      )}

      {isHeadquarters && events.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">감사 이력 {totalCount}건</CardTitle>
            <CardDescription>
              처리 직원과 예약·객실 정보를 함께 보여준다. 원본 요청·개인정보 전문은 노출하지 않는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>발생 시각</TableHead>
                  <TableHead>유형</TableHead>
                  <TableHead>처리 직원</TableHead>
                  <TableHead>지점</TableHead>
                  <TableHead>예약</TableHead>
                  <TableHead>객실</TableHead>
                  <TableHead>내용</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={`${event.eventType}-${event.createdAt}-${event.staffEmail}`}>
                    <TableCell className="whitespace-nowrap tabular-nums">
                      {dateTime(event.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{eventLabel(event.eventType)}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="grid gap-0.5">
                        <span className="font-medium break-all">{event.staffDisplayName}</span>
                        <span className="text-xs text-muted-foreground break-all">{event.staffEmail}</span>
                      </div>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">{event.hotelName}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {event.reservationId ? (
                        <div className="grid gap-0.5">
                          <span className="break-all">{event.guestName}</span>
                          <span className="text-xs break-all">{event.reservationId.slice(0, 8)}</span>
                        </div>
                      ) : (
                        <span className="text-xs">예약 없음</span>
                      )}
                    </TableCell>
                    <TableCell className="tabular-nums">{event.roomNumber ?? "-"}</TableCell>
                    <TableCell className="max-w-[34ch] break-words text-sm">{event.summary}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && totalCount > PAGE_SIZE && (
        <div className="flex items-center justify-center gap-2" data-testid="audit-paging">
          <Button
            type="button"
            variant="outline"
            onClick={() => gotoPage(pageIndex - 1)}
            disabled={pageIndex === 0}
            aria-label="이전 페이지"
          >
            이전
          </Button>
          <span className="text-sm tabular-nums" data-testid="audit-page">
            {pageIndex + 1} / {lastPage + 1}
          </span>
          <Button
            type="button"
            variant="outline"
            onClick={() => gotoPage(pageIndex + 1)}
            disabled={pageIndex >= lastPage}
            aria-label="다음 페이지"
          >
            다음
          </Button>
        </div>
      )}

    </div>
  );
}