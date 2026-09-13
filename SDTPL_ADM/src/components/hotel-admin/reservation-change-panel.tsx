"use client";

import { type FormEvent, useEffect, useState } from "react";
import { Clock3, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  cancelReservationChangeRequest,
  createReservationChangeRequest,
  getReservationChangePolicy,
  getReservationChangeRequests,
  previewStaffReservationStayChange,
  repriceReservationChangeRequest,
  type ReservationChangePolicy,
  type ReservationChangeRequestView,
  type StaffPrincipal,
  type StaffReservationStayChangeOffer,
  type StaffReservationStayChangePreview,
  type StaffReservationStayChangeResult,
  type StaffReservationSummary,
  updateStaffReservationStay,
} from "@/lib/staff-api";
import { ReservationChangeTimeline, reservationChangeStatusLabel } from "@/components/hotel-admin/reservation-change-timeline";

const terminalStatuses = new Set(["COMPLETED", "REJECTED", "CANCELLED", "EXPIRED"]);

function money(value: number, currency: string) {
  if (currency === "KRW") return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency }).format(value);
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short" })
    .format(new Date(`${value}T00:00:00`));
}

function displayDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function offerId(offer: StaffReservationStayChangeOffer) {
  return `${offer.roomTypeId}:${offer.ratePlanId}`;
}

function differenceLabel(offer: StaffReservationStayChangeOffer) {
  if (offer.differenceKrw > 0) return `추가 금액 ${money(offer.differenceKrw, offer.currency)}`;
  if (offer.differenceKrw < 0) return `환불 예정 ${money(Math.abs(offer.differenceKrw), offer.currency)}`;
  return "금액 차이 없음";
}

export function ReservationChangePanel({
  reservation,
  staff,
  onLegacyUpdated,
}: {
  reservation: StaffReservationSummary;
  staff: StaffPrincipal | null;
  onLegacyUpdated: (result: StaffReservationStayChangeResult) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [checkIn, setCheckIn] = useState(reservation.checkIn);
  const [checkOut, setCheckOut] = useState(reservation.checkOut);
  const [preview, setPreview] = useState<StaffReservationStayChangePreview | null>(null);
  const [selectedOfferId, setSelectedOfferId] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [policy, setPolicy] = useState<ReservationChangePolicy | null>(null);
  const [activeRequest, setActiveRequest] = useState<ReservationChangeRequestView | null>(null);
  const [foundationUnavailable, setFoundationUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingFoundation, setLoadingFoundation] = useState(true);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [saving, setSaving] = useState(false);
  const canPreview = checkIn.length > 0 && checkOut.length > 0 && checkOut > checkIn;
  const selectedOffer = preview?.offers.find((offer) => offerId(offer) === selectedOfferId) ?? null;

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    let current = true;
    async function loadFoundation() {
      setLoadingFoundation(true);
      try {
        const [nextPolicy, requests] = await Promise.all([
          getReservationChangePolicy(token!),
          getReservationChangeRequests(token!, { hotelId: staff?.hotelId ?? undefined }),
        ]);
        if (!current) return;
        setPolicy(nextPolicy);
        setActiveRequest(requests.find((request) => request.reservationId === reservation.reservationId && !terminalStatuses.has(request.status)) ?? null);
        setFoundationUnavailable(false);
      } catch {
        if (current) setFoundationUnavailable(true);
      } finally {
        if (current) setLoadingFoundation(false);
      }
    }
    void loadFoundation();
    return () => { current = false; };
  }, [reservation.reservationId, staff?.hotelId]);

  function beginEditing() {
    setCheckIn(reservation.checkIn);
    setCheckOut(reservation.checkOut);
    setPreview(null);
    setSelectedOfferId("");
    setIdempotencyKey("");
    setError(null);
    setEditing(true);
  }

  function stopEditing() {
    if (loadingPreview || saving) return;
    setEditing(false);
    setError(null);
  }

  function changeDate(setValue: (value: string) => void, value: string) {
    setValue(value);
    setPreview(null);
    setSelectedOfferId("");
    setIdempotencyKey("");
    setError(null);
  }

  async function loadPreview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !canPreview) return;
    setLoadingPreview(true);
    setError(null);
    setPreview(null);
    setSelectedOfferId("");
    setIdempotencyKey("");
    try {
      const result = await previewStaffReservationStayChange(token, reservation.reservationId, { checkIn, checkOut });
      setPreview(result);
      if (result.offers.length > 0) {
        setSelectedOfferId(offerId(result.offers[0]));
        setIdempotencyKey(window.crypto.randomUUID());
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "변경 가능한 숙박 조건을 조회하지 못했습니다.");
    } finally {
      setLoadingPreview(false);
    }
  }

  function selectOffer(value: string) {
    setSelectedOfferId(value);
    setIdempotencyKey(window.crypto.randomUUID());
    setError(null);
  }

  async function submitChange() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !selectedOffer || !idempotencyKey || !preview) return;
    setSaving(true);
    setError(null);
    const input = {
      checkIn: preview.checkIn,
      checkOut: preview.checkOut,
      roomTypeId: selectedOffer.roomTypeId,
      ratePlanId: selectedOffer.ratePlanId,
      expectedTotal: selectedOffer.totalKrw,
    };
    try {
      if (foundationUnavailable) {
        onLegacyUpdated(await updateStaffReservationStay(token, reservation.reservationId, idempotencyKey, input));
        return;
      }
      const request = await createReservationChangeRequest(token, reservation.reservationId, idempotencyKey, input);
      setActiveRequest(request);
      setEditing(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "예약 변경 요청을 만들지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function runRequestAction(action: "REPRICE" | "CANCEL") {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !activeRequest) return;
    setSaving(true);
    setError(null);
    try {
      const key = window.crypto.randomUUID();
      const result = action === "REPRICE"
        ? await repriceReservationChangeRequest(token, activeRequest.id, key, activeRequest.version)
        : await cancelReservationChangeRequest(token, activeRequest.id, key, activeRequest.version);
      setActiveRequest(result);
      if (terminalStatuses.has(result.status)) setEditing(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "예약 변경 상태를 갱신하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  if (activeRequest && !terminalStatuses.has(activeRequest.status)) {
    return (
      <section aria-labelledby="reservation-change-title" className="space-y-4 border-b pb-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 id="reservation-change-title" className="text-sm font-medium">숙박 조건 변경</h3>
            <p className="mt-1 text-xs text-muted-foreground">진행 중인 요청이 있어 새 변경안을 만들 수 없습니다.</p>
          </div>
          <Badge variant={activeRequest.status === "APPROVED" ? "default" : "outline"}>
            {reservationChangeStatusLabel(activeRequest.status)}
          </Badge>
        </div>
        <div role="status" aria-label="예약 변경 상태" className="space-y-3 rounded-xl bg-muted/60 p-4">
          <div className="flex items-center gap-2 font-medium">
            <Clock3 className="size-4" aria-hidden="true" />
            {reservationChangeStatusLabel(activeRequest.status)}
          </div>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <Detail label="변경 일정" value={`${displayDate(activeRequest.targetCheckIn)} ~ ${displayDate(activeRequest.targetCheckOut)}`} />
            <Detail label="변경 금액" value={money(activeRequest.quote.totalKrw, activeRequest.quote.currency)} />
            <Detail label="차액" value={differenceLabel({ ...activeRequest.quote, roomTypeId: "", roomTypeName: "", ratePlanId: "", ratePlanName: "", breakfastIncluded: false, remaining: 0 })} />
            <Detail label="승인 만료" value={displayDateTime(activeRequest.approvalExpiresAt)} />
          </dl>
        </div>
        <ReservationChangeTimeline events={activeRequest.events} />
        <div className="flex flex-wrap justify-end gap-2">
          {activeRequest.actions.includes("REPRICE") && (
            <Button type="button" variant="outline" disabled={saving} onClick={() => void runRequestAction("REPRICE")}>최신 가격 다시 확인</Button>
          )}
          {activeRequest.actions.includes("CANCEL") && (
            <Button type="button" variant="ghost" disabled={saving} onClick={() => void runRequestAction("CANCEL")}>변경 요청 취소</Button>
          )}
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      </section>
    );
  }

  if (!editing) {
    return (
      <div className="flex justify-end">
        <Button variant="outline" disabled={loadingFoundation} onClick={beginEditing}>
          {loadingFoundation ? "변경 상태 확인 중" : "숙박 일정·객실 유형 변경"}
        </Button>
      </div>
    );
  }

  const needsHqApproval = Boolean(policy && staff?.role === "BRANCH_STAFF" && selectedOffer && Math.abs(selectedOffer.differenceKrw) > policy.directLimitKrw);
  const actionLabel = foundationUnavailable
    ? "숙박 조건 변경 확정"
    : needsHqApproval ? "본사 승인 요청" : staff?.role === "HQ_ADMIN" ? "변경 요청 승인" : "변경 요청 만들기";

  return (
    <section aria-labelledby="reservation-stay-change-title" className="space-y-4 border-b pb-4">
      <div>
        <h3 id="reservation-stay-change-title" className="text-sm font-medium">숙박 일정·객실 유형 변경</h3>
        <p className="mt-1 text-xs text-muted-foreground">새 날짜의 실제 재고와 최신 일별 요금을 서버에서 다시 확인합니다.</p>
        {policy && <p className="mt-1 text-xs text-muted-foreground">지점 직접 처리 한도 {money(policy.directLimitKrw, "KRW")}</p>}
      </div>
      <form className="space-y-4" onSubmit={(event) => void loadPreview(event)}>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="reservation-stay-check-in">변경 체크인</Label>
            <Input id="reservation-stay-check-in" type="date" value={checkIn} disabled={loadingPreview || saving} required onChange={(event) => changeDate(setCheckIn, event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reservation-stay-check-out">변경 체크아웃</Label>
            <Input id="reservation-stay-check-out" type="date" min={checkIn} value={checkOut} disabled={loadingPreview || saving} required onChange={(event) => changeDate(setCheckOut, event.target.value)} />
          </div>
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" disabled={loadingPreview || saving} onClick={stopEditing}>변경 취소</Button>
          <Button type="submit" variant="outline" disabled={!canPreview || loadingPreview || saving}>{loadingPreview ? "변경안 조회 중" : "변경안 조회"}</Button>
        </div>
      </form>

      {preview && preview.offers.length === 0 && (
        <p role="status" className="rounded-lg bg-muted/60 p-3 text-sm">선택한 날짜에 변경 가능한 객실이 없습니다. 다른 날짜를 선택해 주세요.</p>
      )}
      {preview && preview.offers.length > 0 && (
        <div className="space-y-4" aria-live="polite">
          <div className="space-y-2">
            <Label htmlFor="reservation-stay-offer">변경할 객실·요금제</Label>
            <select id="reservation-stay-offer" className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none transition-colors focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50" value={selectedOfferId} disabled={saving} onChange={(event) => selectOffer(event.target.value)}>
              {preview.offers.map((offer) => <option key={offerId(offer)} value={offerId(offer)}>{offer.roomTypeName} · {offer.ratePlanName} · {money(offer.totalKrw, offer.currency)}</option>)}
            </select>
          </div>
          {selectedOffer && (
            <div className="space-y-4 rounded-xl bg-muted/60 p-4 text-sm">
              <div role="status" aria-label="예약 변경 승인 기준" className="flex items-start gap-3">
                <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                <div>
                  <p className="font-medium">{needsHqApproval ? "본사 승인 필요" : staff?.role === "HQ_ADMIN" ? "본사 직접 승인" : "직접 처리 가능"}</p>
                  <p className="mt-1 text-xs text-muted-foreground">승인 단계에서는 대상 객실 재고나 결제를 처리하지 않습니다.</p>
                </div>
              </div>
              {foundationUnavailable && (
                <p className="text-xs text-muted-foreground">현재는 테스트 결제 범위입니다. 실제 추가 결제나 부분 환불은 처리되지 않습니다.</p>
              )}
              <dl className="grid gap-3 sm:grid-cols-2">
                <Detail label="현재 예약 금액" value={money(preview.currentTotalKrw, preview.currency)} />
                <Detail label="변경 예약 금액" value={money(selectedOffer.totalKrw, selectedOffer.currency)} />
                <Detail label="금액 차이" value={differenceLabel(selectedOffer)} />
                <Detail label="남은 객실" value={`${selectedOffer.remaining}실`} />
              </dl>
              <div className="overflow-x-auto rounded-lg border bg-background">
                <Table>
                  <TableHeader><TableRow><TableHead>숙박일</TableHead><TableHead className="text-right">일별 요금</TableHead></TableRow></TableHeader>
                  <TableBody>{selectedOffer.nightlyPrices.map((night) => <TableRow key={night.date}><TableCell>{displayDate(night.date)}</TableCell><TableCell className="text-right tabular-nums">{money(night.amount, selectedOffer.currency)}</TableCell></TableRow>)}</TableBody>
                </Table>
              </div>
            </div>
          )}
          <div className="flex justify-end">
            <Button type="button" disabled={!selectedOffer || !idempotencyKey || saving} onClick={() => void submitChange()}>{saving ? "요청 처리 중" : actionLabel}</Button>
          </div>
        </div>
      )}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </section>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-medium text-muted-foreground">{label}</dt><dd className="mt-1 font-medium">{value}</dd></div>;
}
