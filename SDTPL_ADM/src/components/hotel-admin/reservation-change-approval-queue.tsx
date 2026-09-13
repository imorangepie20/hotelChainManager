"use client";

import { useEffect, useRef, useState } from "react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  approveReservationChangeRequest,
  getReservationChangeRequests,
  rejectReservationChangeRequest,
  type ReservationChangeRequestView,
} from "@/lib/staff-api";
import { ReservationChangeTimeline } from "@/components/hotel-admin/reservation-change-timeline";

const hotelNames: Record<string, string> = {
  "11000000-0000-0000-0000-000000000001": "속초 지점",
  "11000000-0000-0000-0000-000000000002": "설악산 지점",
  "11000000-0000-0000-0000-000000000003": "제주 지점",
};

function money(value: number, currency: string) {
  if (currency === "KRW") return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency }).format(value);
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short" })
    .format(new Date(`${value}T00:00:00`));
}

function requestGuestName(request: ReservationChangeRequestView) {
  return request.guestName ?? `예약 ${request.reservationId.slice(0, 8)}`;
}

export function ReservationChangeApprovalQueue() {
  const [requests, setRequests] = useState<ReservationChangeRequestView[]>([]);
  const [selected, setSelected] = useState<ReservationChangeRequestView | null>(null);
  const [confirmation, setConfirmation] = useState<"approve" | "reject" | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const rejectTriggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    let current = true;
    async function loadRequests() {
      setLoading(true);
      setError(null);
      try {
        const result = await getReservationChangeRequests(token!, { status: "PENDING_APPROVAL" });
        if (current) setRequests(result);
      } catch (cause) {
        if (current) setError(cause instanceof Error ? cause.message : "승인 대기 요청을 불러오지 못했습니다.");
      } finally {
        if (current) setLoading(false);
      }
    }
    void loadRequests();
    return () => { current = false; };
  }, []);

  function openConfirmation(type: "approve" | "reject") {
    setConfirmation(type);
    setRejectionReason("");
    setIdempotencyKey(window.crypto.randomUUID());
    setError(null);
  }

  function closeConfirmation() {
    if (saving) return;
    const restoreRejectFocus = confirmation === "reject";
    setConfirmation(null);
    setRejectionReason("");
    setIdempotencyKey("");
    if (restoreRejectFocus) window.requestAnimationFrame(() => rejectTriggerRef.current?.focus());
  }

  async function decide() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !selected || !confirmation || !idempotencyKey) return;
    const reason = rejectionReason.trim();
    if (confirmation === "reject" && !reason) return;
    setSaving(true);
    setError(null);
    try {
      const result = confirmation === "approve"
        ? await approveReservationChangeRequest(token, selected.id, idempotencyKey, selected.version)
        : await rejectReservationChangeRequest(token, selected.id, idempotencyKey, { version: selected.version, reason });
      setRequests((current) => current.filter((request) => request.id !== result.id));
      setNotice(`${requestGuestName(selected)} 고객의 변경 요청을 ${confirmation === "approve" ? "승인했습니다" : "반려했습니다"}.`);
      setConfirmation(null);
      setSelected(null);
      setRejectionReason("");
      setIdempotencyKey("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "승인 결정을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card role="region" aria-label="승인 대기" className="min-w-0">
      <CardHeader className="border-b">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>승인 대기</CardTitle>
            <CardDescription>지점 직접 처리 한도를 넘은 숙박 변경 요청입니다.</CardDescription>
          </div>
          <Badge variant="outline">{loading ? "확인 중" : `${requests.length}건`}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {notice && <p role="status" aria-label="예약 변경 처리 결과" className="rounded-xl bg-emerald-500/10 p-3 text-sm text-emerald-800 dark:text-emerald-300">{notice}</p>}
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="overflow-x-auto rounded-xl border">
          <Table>
            <TableHeader><TableRow><TableHead>지점·고객</TableHead><TableHead>변경 일정</TableHead><TableHead className="text-right">차액</TableHead><TableHead><span className="sr-only">검토</span></TableHead></TableRow></TableHeader>
            <TableBody>
              {!loading && requests.length === 0 ? (
                <TableRow><TableCell colSpan={4} className="h-24 text-center text-muted-foreground">승인 대기 중인 요청이 없습니다.</TableCell></TableRow>
              ) : requests.map((request) => (
                <TableRow key={request.id}>
                  <TableCell><span className="block font-medium">{request.hotelName ?? hotelNames[request.hotelId] ?? "지점"}</span><span className="block text-xs text-muted-foreground">{requestGuestName(request)}</span></TableCell>
                  <TableCell><span className="block">{displayDate(request.targetCheckIn)}</span><span className="text-xs text-muted-foreground">~ {displayDate(request.targetCheckOut)}</span></TableCell>
                  <TableCell className="text-right font-medium tabular-nums">{money(request.quote.differenceKrw, request.quote.currency)}</TableCell>
                  <TableCell className="text-right"><Button variant="outline" onClick={() => setSelected(request)} aria-label={`${requestGuestName(request)} 변경 요청 검토`}>검토</Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>

      <Dialog open={Boolean(selected)} onOpenChange={(open) => { if (!open && !confirmation && !saving) setSelected(null); }}>
        {selected && (
          <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-2xl">
            <DialogHeader>
              <DialogTitle>{requestGuestName(selected)} 고객 변경 요청</DialogTitle>
              <DialogDescription>{selected.hotelName ?? hotelNames[selected.hotelId] ?? "지점"} · 예약 번호 {selected.reservationId}</DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 sm:grid-cols-2">
              <Condition title="기존 조건" checkIn={selected.previousCheckIn} checkOut={selected.previousCheckOut} roomType={selected.previousRoomTypeName ?? selected.previousRoomTypeId} ratePlan={selected.previousRatePlanName ?? selected.previousRatePlanId} />
              <Condition title="변경 조건" checkIn={selected.targetCheckIn} checkOut={selected.targetCheckOut} roomType={selected.targetRoomTypeName ?? selected.targetRoomTypeId} ratePlan={selected.targetRatePlanName ?? selected.targetRatePlanId} />
            </div>
            <dl className="grid gap-3 rounded-xl bg-muted/60 p-4 sm:grid-cols-3">
              <Detail label="기존 금액" value={money(selected.quote.previousTotalKrw, selected.quote.currency)} />
              <Detail label="변경 금액" value={money(selected.quote.totalKrw, selected.quote.currency)} />
              <Detail label="차액" value={money(selected.quote.differenceKrw, selected.quote.currency)} />
            </dl>
            <div className="overflow-x-auto rounded-xl border">
              <Table>
                <TableHeader><TableRow><TableHead>숙박일</TableHead><TableHead className="text-right">일별 요금</TableHead></TableRow></TableHeader>
                <TableBody>{selected.quote.nightlyPrices.map((night) => <TableRow key={night.date}><TableCell>{displayDate(night.date)}</TableCell><TableCell className="text-right tabular-nums">{money(night.amount, selected.quote.currency)}</TableCell></TableRow>)}</TableBody>
              </Table>
            </div>
            <ReservationChangeTimeline events={selected.events} />
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
            <div className="flex flex-wrap justify-end gap-2">
              <Button ref={rejectTriggerRef} type="button" variant="outline" disabled={saving || !selected.actions.includes("REJECT")} onClick={() => openConfirmation("reject")}>반려</Button>
              <Button type="button" disabled={saving || !selected.actions.includes("APPROVE")} onClick={() => openConfirmation("approve")}>승인</Button>
            </div>
          </DialogContent>
        )}
      </Dialog>

      <AlertDialog open={confirmation !== null} onOpenChange={(open) => { if (!open) closeConfirmation(); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{confirmation === "approve" ? "이 변경 요청을 승인할까요?" : "이 변경 요청을 반려할까요?"}</AlertDialogTitle>
            <AlertDialogDescription>{confirmation === "approve" ? "승인은 현재 조건과 차액 한도에 묶입니다. 승인 단계에서는 재고나 결제를 처리하지 않습니다." : "지점 직원이 다음 변경안을 준비할 수 있도록 구체적인 사유를 남겨 주세요."}</AlertDialogDescription>
          </AlertDialogHeader>
          {confirmation === "reject" && (
            <div className="space-y-2">
              <Label htmlFor="reservation-change-rejection-reason">반려 사유</Label>
              <Textarea id="reservation-change-rejection-reason" value={rejectionReason} disabled={saving} required maxLength={500} onChange={(event) => setRejectionReason(event.target.value)} />
            </div>
          )}
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={saving}>돌아가기</AlertDialogCancel>
            <AlertDialogAction variant={confirmation === "reject" ? "destructive" : "default"} disabled={saving || (confirmation === "reject" && !rejectionReason.trim())} onClick={(event) => { event.preventDefault(); void decide(); }}>
              {saving ? "저장 중" : confirmation === "approve" ? "변경 승인 확정" : "변경 요청 반려"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}

function Condition({ title, checkIn, checkOut, roomType, ratePlan }: { title: string; checkIn: string; checkOut: string; roomType: string; ratePlan: string }) {
  return <section aria-label={title} className="space-y-2 rounded-xl border p-4"><h3 className="font-medium">{title}</h3><p className="text-sm">{displayDate(checkIn)} ~ {displayDate(checkOut)}</p><p className="text-sm text-muted-foreground">{roomType} · {ratePlan}</p></section>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-medium text-muted-foreground">{label}</dt><dd className="mt-1 font-medium tabular-nums">{value}</dd></div>;
}
