import { CheckCircle2, Circle, XCircle } from "lucide-react";

import type { ReservationChangeEvent, ReservationChangeStatus } from "@/lib/staff-api";

const statusLabels: Record<ReservationChangeStatus, string> = {
  PENDING_APPROVAL: "본사 승인 대기",
  APPROVED: "승인 완료",
  AWAITING_PAYMENT: "고객 결제 대기",
  REFUND_PENDING: "부분 환불 처리 중",
  READY_TO_APPLY: "예약 반영 대기",
  APPLYING: "예약 반영 중",
  COMPLETED: "변경 완료",
  REJECTED: "반려",
  CANCELLED: "요청 취소",
  EXPIRED: "요청 만료",
  RECONCILIATION_REQUIRED: "운영 확인 필요",
};

export function reservationChangeStatusLabel(status: ReservationChangeStatus) {
  return statusLabels[status] ?? status;
}

function eventIcon(status: ReservationChangeStatus) {
  if (["REJECTED", "CANCELLED", "EXPIRED", "RECONCILIATION_REQUIRED"].includes(status)) {
    return <XCircle className="size-4 text-destructive" aria-hidden="true" />;
  }
  if (["APPROVED", "COMPLETED"].includes(status)) {
    return <CheckCircle2 className="size-4 text-emerald-600" aria-hidden="true" />;
  }
  return <Circle className="size-4 text-muted-foreground" aria-hidden="true" />;
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

export function ReservationChangeTimeline({ events }: { events: ReservationChangeEvent[] }) {
  return (
    <section aria-labelledby="reservation-change-history-title" className="space-y-3">
      <h4 id="reservation-change-history-title" className="text-sm font-medium">처리 이력</h4>
      {events.length === 0 ? (
        <p className="text-sm text-muted-foreground">아직 기록된 처리 이력이 없습니다.</p>
      ) : (
        <ol className="space-y-3">
          {events.map((event) => (
            <li key={event.id} className="grid grid-cols-[1rem_minmax(0,1fr)] gap-3">
              <span className="pt-0.5">{eventIcon(event.toStatus)}</span>
              <div className="min-w-0">
                <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                  <p className="font-medium">{reservationChangeStatusLabel(event.toStatus)}</p>
                  <time className="text-xs text-muted-foreground" dateTime={event.createdAt}>{displayDateTime(event.createdAt)}</time>
                </div>
                {event.reason && <p className="mt-1 text-sm text-muted-foreground">{event.reason}</p>}
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
