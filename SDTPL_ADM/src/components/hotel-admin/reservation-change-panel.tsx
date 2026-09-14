"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { Clock3, Copy, CreditCard, RefreshCw, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  cancelReservationChangeRequest,
  createReservationChangePaymentLink,
  createReservationChangeRequest,
  getReservationChangeRequest,
  getReservationChangePolicy,
  getReservationChangeRequests,
  previewStaffReservationStayChange,
  repriceReservationChangeRequest,
  reconcileReservationChangeRequest,
  startReservationChangeRefund,
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
import { describeReservationChangeLink, shouldPollReservationChangeRequest, type ReservationChangeLinkState } from "@/lib/reservation-change-link-state";

const terminalStatuses = new Set(["COMPLETED", "REJECTED", "CANCELLED", "EXPIRED"]);

function createPublicToken() {
  const bytes = window.crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

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
  const [customerUrl, setCustomerUrl] = useState("");
  const [customerLinkCreatedAt, setCustomerLinkCreatedAt] = useState("");
  const [customerLinkExpiresAt, setCustomerLinkExpiresAt] = useState("");
  const [customerLinkState, setCustomerLinkState] = useState<ReservationChangeLinkState>("NOT_CREATED");
  const settlementAttempt = useRef<{ requestId: string; key: string; publicToken: string } | null>(null);
  const completedNotification = useRef<string | null>(null);
  const customerLinkInputRef = useRef<HTMLInputElement | null>(null);
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

  useEffect(() => {
    if (!activeRequest || !shouldPollReservationChangeRequest(activeRequest.status)) return;
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    let current = true;
    const refresh = async () => {
      try {
        const next = await getReservationChangeRequest(token, activeRequest.id);
        if (!current) return;
        setActiveRequest(next);
        if (next.status === "COMPLETED" && completedNotification.current !== next.id) {
          completedNotification.current = next.id;
          onLegacyUpdated({
            reservationId: next.reservationId,
            checkIn: next.targetCheckIn,
            checkOut: next.targetCheckOut,
            roomTypeId: next.targetRoomTypeId,
            ratePlanId: next.targetRatePlanId,
            totalKrw: next.quote.totalKrw,
            differenceKrw: next.quote.differenceKrw,
            currency: next.quote.currency,
          });
        }
      } catch (cause) {
        if (current) setError(cause instanceof Error ? cause.message : "정산 상태를 갱신하지 못했습니다.");
      }
    };
    const timer = window.setInterval(() => void refresh(), 2000);
    return () => { current = false; window.clearInterval(timer); };
  }, [activeRequest, onLegacyUpdated]);

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

  async function createPaymentLink() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !activeRequest) return;
    const attempt = settlementAttempt.current?.requestId === activeRequest.id
      ? settlementAttempt.current
      : { requestId: activeRequest.id, key: window.crypto.randomUUID(), publicToken: createPublicToken() };
    settlementAttempt.current = attempt;
    setSaving(true);
    setCustomerLinkState("CREATING");
    setError(null);
    try {
      const link = await createReservationChangePaymentLink(
        token, activeRequest.id, attempt.key,
        { version: activeRequest.version, publicToken: attempt.publicToken },
      );
      setCustomerUrl(link.customerUrl);
      setCustomerLinkCreatedAt(link.createdAt ?? new Date().toISOString());
      setCustomerLinkExpiresAt(link.expiresAt);
      setCustomerLinkState(link.status === "EXPIRED" ? "EXPIRED" : "READY");
      setActiveRequest(await getReservationChangeRequest(token, activeRequest.id));
    } catch (cause) {
      setCustomerLinkState("NOT_CREATED");
      setError(cause instanceof Error ? cause.message : "고객 결제 링크를 만들지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function copyCustomerUrl() {
    if (!customerUrl) return;
    try {
      if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(customerUrl);
      setCustomerLinkState("COPIED");
      setError(null);
    } catch {
      customerLinkInputRef.current?.focus();
      customerLinkInputRef.current?.select();
      setError("클립보드에 복사하지 못했습니다. 아래 URL을 선택해 전달해 주세요.");
    }
  }

  function startNewRequestForExpiredLink() {
    setCustomerUrl("");
    setCustomerLinkCreatedAt("");
    setCustomerLinkExpiresAt("");
    setCustomerLinkState("NOT_CREATED");
    settlementAttempt.current = null;
    setActiveRequest(null);
    beginEditing();
  }

  async function startRefund() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !activeRequest) return;
    if (!window.confirm("변경 차액을 원 결제 수단으로 부분 환불할까요?")) return;
    setSaving(true);
    setError(null);
    try {
      setActiveRequest(await startReservationChangeRefund(
        token, activeRequest.id, window.crypto.randomUUID(), activeRequest.version,
      ));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "부분 환불을 시작하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function reconcile(action: string) {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !activeRequest) return;
    const reason = window.prompt("조정 사유를 입력해 주세요.")?.trim();
    if (!reason) return;
    setSaving(true);
    setError(null);
    try {
      setActiveRequest(await reconcileReservationChangeRequest(
        token, activeRequest.id, window.crypto.randomUUID(),
        { action, version: activeRequest.version, reason },
      ));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "정산 조정 작업을 처리하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  const linkDisplay = describeReservationChangeLink(activeRequest?.status ?? null, Boolean(customerUrl), customerLinkState);
  if (activeRequest && (!terminalStatuses.has(activeRequest.status) || linkDisplay.keepVisible)) {
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
        {customerUrl && (
          <div className="space-y-2 rounded-xl border p-4">
            <Label htmlFor="reservation-change-customer-link">고객 결제 링크</Label>
            <div className="flex min-w-0 gap-2">
              <Input ref={customerLinkInputRef} id="reservation-change-customer-link" readOnly value={customerUrl} onFocus={(event) => event.currentTarget.select()} className="min-w-0" />
              <Button type="button" variant="outline" aria-label="고객 결제 링크 복사" onClick={() => void copyCustomerUrl()}><Copy className="size-4" aria-hidden="true" /></Button>
            </div>
            <dl className="grid gap-1 text-xs text-muted-foreground sm:grid-cols-2">
              <div><dt className="inline font-medium text-foreground">생성 시각: </dt><dd className="inline">{customerLinkCreatedAt ? displayDateTime(customerLinkCreatedAt) : "확인 중"}</dd></div>
              <div><dt className="inline font-medium text-foreground">만료 시각: </dt><dd className="inline">{customerLinkExpiresAt ? displayDateTime(customerLinkExpiresAt) : "확인 중"}</dd></div>
            </dl>
            <p className="text-xs text-muted-foreground">로컬 테스트 결제 링크입니다. 이메일이나 문자로 자동 전달하지 않으므로 URL을 고객에게 직접 전달해 주세요.</p>
            {customerLinkState === "COPIED" && <p role="status" className="text-xs text-emerald-700">복사했습니다. 고객에게 전달해 주세요.</p>}
            {linkDisplay.state === "EXPIRED" && <p role="status" className="text-xs text-destructive">결제 링크가 만료되었습니다. 새 결제 링크를 위해 변경 요청을 다시 만들어 주세요.</p>}
          </div>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          {activeRequest.actions.includes("REPRICE") && (
            <Button type="button" variant="outline" disabled={saving} onClick={() => void runRequestAction("REPRICE")}>최신 가격 다시 확인</Button>
          )}
          {activeRequest.actions.includes("CANCEL") && (
            <Button type="button" variant="ghost" disabled={saving} onClick={() => void runRequestAction("CANCEL")}>변경 요청 취소</Button>
          )}
          {activeRequest.actions.includes("PAYMENT_LINK") && (
            <Button type="button" disabled={saving} onClick={() => void createPaymentLink()}><CreditCard className="size-4" aria-hidden="true" />{customerLinkState === "CREATING" ? "고객 결제 링크 만드는 중" : "고객 결제 링크 만들기"}</Button>
          )}
          {linkDisplay.canStartNewRequest && (
            <Button type="button" variant="outline" onClick={startNewRequestForExpiredLink}>새 결제 링크를 위해 변경 요청 만들기</Button>
          )}
          {activeRequest.actions.includes("REFUND") && (
            <Button type="button" disabled={saving} onClick={() => void startRefund()}>원 결제 수단으로 부분 환불</Button>
          )}
          {activeRequest.actions.filter((action) => ["QUERY_GATEWAY", "RETRY_APPLY", "RELEASE_AFTER_CONFIRMED_FAILURE", "REFUND_ADJUSTMENT_AND_CANCEL"].includes(action)).map((action) => (
            <Button key={action} type="button" variant="outline" disabled={saving} onClick={() => void reconcile(action)}>
              <RefreshCw className="size-4" aria-hidden="true" />{reconciliationLabel(action)}
            </Button>
          ))}
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

function reconciliationLabel(action: string) {
  if (action === "QUERY_GATEWAY") return "결제사 상태 조회";
  if (action === "RETRY_APPLY") return "예약 반영 재시도";
  if (action === "RELEASE_AFTER_CONFIRMED_FAILURE") return "실패 확인 후 요청 종료";
  return "추가 결제 환급 후 종료";
}
