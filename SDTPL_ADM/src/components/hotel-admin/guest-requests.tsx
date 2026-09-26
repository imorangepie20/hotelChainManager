"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Inbox, RefreshCw } from "lucide-react";

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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  getGuestRequest,
  getGuestRequests,
  guestRequestStatusLabels,
  guestRequestTypeLabels,
  StaffApiError,
  transitionGuestRequest,
  type GuestRequestDetail,
  type GuestRequestStatus,
  type GuestRequestSummary,
  type StaffPrincipal,
} from "@/lib/staff-api";

const PAGE_SIZE = 20;

const STATUS_FILTERS: GuestRequestStatus[] = [
  "OPEN",
  "IN_PROGRESS",
  "RESOLVED",
  "CLOSED",
];

function dateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
}

export function GuestRequests() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [requests, setRequests] = useState<GuestRequestSummary[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [status, setStatus] = useState<GuestRequestStatus>("OPEN");
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [detail, setDetail] = useState<GuestRequestDetail | null>(null);
  const [detailError, setDetailError] = useState("");
  const [nextStatus, setNextStatus] = useState<GuestRequestStatus | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [saving, setSaving] = useState(false);
  const idempotencyKey = useRef("");
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  // 고객 요청 처리는 지점 직원과 본사 관리자 모두 한다. 본사는 전 지점을,
  // 지점 직원은 자기 지점만 서버가 돌려준다.
  const isStaff =
    staff?.role === "HQ_ADMIN" || staff?.role === "BRANCH_STAFF";
  const sessionToken =
    typeof window !== "undefined"
      ? window.localStorage.getItem("hotel-chain-staff-session")
      : null;

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
      const next = await getGuestRequests(sessionToken, {
        status,
        limit: PAGE_SIZE,
        offset,
      });
      if (!mounted.current || requestGeneration.current !== generation) return;
      setRequests(next.requests);
      setTotalCount(next.totalCount);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setRequests([]);
        setTotalCount(0);
        setError(
          cause instanceof StaffApiError
            ? cause.message
            : "고객 요청을 불러오지 못했습니다.",
        );
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation)
        setLoading(false);
    }
  }, [status, offset, sessionToken]);

  useEffect(() => {
    if (!isStaff) return;
    void refresh();
  }, [isStaff, refresh]);

  async function openDetail(request: GuestRequestSummary) {
    if (!sessionToken) return;
    setDetailError("");
    setDetail(null);
    try {
      const view = await getGuestRequest(sessionToken, request.id);
      setDetail(view);
    } catch (cause) {
      setDetailError(
        cause instanceof StaffApiError
          ? cause.message
          : "고객 요청을 불러오지 못했습니다.",
      );
    }
  }

  function openTransition(target: GuestRequestStatus) {
    setNextStatus(target);
    setResolutionNote("");
    idempotencyKey.current = window.crypto.randomUUID();
    setDetailError("");
  }

  function closeTransition() {
    if (saving) return;
    setNextStatus(null);
    setResolutionNote("");
    idempotencyKey.current = "";
  }

  async function saveTransition() {
    if (!sessionToken || !detail || !nextStatus || !idempotencyKey.current)
      return;
    setSaving(true);
    setDetailError("");
    try {
      const updated = await transitionGuestRequest(
        sessionToken,
        detail.id,
        idempotencyKey.current,
        { status: nextStatus, resolutionNote: resolutionNote.trim() || null },
      );
      setDetail(updated);
      setNextStatus(null);
      setResolutionNote("");
      idempotencyKey.current = "";
      void refresh();
    } catch (cause) {
      setDetailError(
        cause instanceof StaffApiError
          ? cause.message
          : "처리 상태를 바꾸지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  function gotoPage(next: number) {
    const page = Math.max(0, next);
    if (page === offset) return;
    setOffset(page);
  }

  const lastPage = Math.max(0, Math.ceil(totalCount / PAGE_SIZE) - 1);
  const pageIndex = Math.min(offset, lastPage);
  const detailTargets = detail
    ? STATUS_FILTERS.filter((target) => target !== detail.status)
    : [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Inbox className="h-5 w-5" aria-hidden />
            고객 요청
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            고객이 보낸 객실·편의 요청과 일반 문의를 확인하고 처리 상태를
            관리한다. 예약 변경·취소는 전용 화면에서 처리한다.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => void refresh()}
          disabled={loading || !isStaff}
        >
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {isStaff && (
        <div className="flex flex-wrap gap-1.5">
          {STATUS_FILTERS.map((filter) => (
            <Button
              key={filter}
              type="button"
              variant={filter === status ? "default" : "outline"}
              size="sm"
              aria-pressed={filter === status}
              onClick={() => {
                setStatus(filter);
                setOffset(0);
              }}
              data-testid={`guest-request-status-${filter}`}
            >
              {guestRequestStatusLabels[filter]}
            </Button>
          ))}
        </div>
      )}

      {error && (
        <p role="alert" className="break-words text-sm text-destructive">
          {error}
        </p>
      )}

      {staff && !isStaff && (
        <p role="status" className="text-sm text-muted-foreground">
          고객 요청은 지점 직원과 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isStaff && requests.length === 0 && !error && !loading && (
        <p
          className="text-sm text-muted-foreground"
          data-testid="guest-request-empty"
        >
          해당 상태에 고객 요청이 없습니다.
        </p>
      )}

      {isStaff && requests.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {guestRequestStatusLabels[status]} {totalCount}건
            </CardTitle>
            <CardDescription>
              고객이 남긴 연락처는 처리할 때만 보여준다. 화면을 공유할 때는
              주의한다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>접수 시각</TableHead>
                  <TableHead>유형</TableHead>
                  <TableHead>지점</TableHead>
                  <TableHead>고객</TableHead>
                  <TableHead>제목</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>담당</TableHead>
                  <TableHead>
                    <span className="sr-only">검토</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {requests.map((request) => (
                  <TableRow key={request.id}>
                    <TableCell className="whitespace-nowrap tabular-nums">
                      {dateTime(request.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {guestRequestTypeLabels[request.requestType] ??
                          request.requestType}
                      </Badge>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      {request.hotelName}
                    </TableCell>
                    <TableCell>{request.guestName}</TableCell>
                    <TableCell className="max-w-[34ch] break-words text-sm">
                      {request.subject}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {guestRequestStatusLabels[request.status]}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {request.assignedDisplayName ?? "-"}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => void openDetail(request)}
                        aria-label={`${request.subject} 검토`}
                        data-testid={`guest-request-open-${request.id}`}
                      >
                        검토
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isStaff && totalCount > PAGE_SIZE && (
        <div
          className="flex items-center justify-center gap-2"
          data-testid="guest-request-paging"
        >
          <Button
            type="button"
            variant="outline"
            onClick={() => gotoPage(pageIndex - 1)}
            disabled={pageIndex === 0}
            aria-label="이전 페이지"
          >
            이전
          </Button>
          <span
            className="text-sm tabular-nums"
            data-testid="guest-request-page"
          >
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

      <Dialog
        open={Boolean(detail)}
        onOpenChange={(open) => {
          if (!open && !nextStatus && !saving) {
            setDetail(null);
            setDetailError("");
          }
        }}
      >
        {detail && (
          <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-2xl">
            <DialogHeader>
              <DialogTitle>{detail.subject}</DialogTitle>
              <DialogDescription>
                {detail.hotelName} ·{" "}
                {guestRequestTypeLabels[detail.requestType] ??
                  detail.requestType}{" "}
                · {dateTime(detail.createdAt)}
              </DialogDescription>
            </DialogHeader>

            <div className="grid gap-3">
              <div>
                <p className="text-xs font-medium text-muted-foreground">
                  내용
                </p>
                <p className="mt-1 whitespace-pre-line break-words text-sm">
                  {detail.body}
                </p>
              </div>
              <dl className="grid gap-3 rounded-xl bg-muted/60 p-4 sm:grid-cols-2">
                <DetailField label="고객 이름" value={detail.guestName} />
                <DetailField label="이메일" value={detail.guestEmail} />
                <DetailField
                  label="전화번호"
                  value={detail.guestPhone ?? "-"}
                />
                <DetailField
                  label="예약"
                  value={
                    detail.reservationId
                      ? detail.reservationId.slice(0, 8)
                      : "없음"
                  }
                />
                <DetailField
                  label="상태"
                  value={guestRequestStatusLabels[detail.status]}
                />
                <DetailField
                  label="담당"
                  value={detail.assignedDisplayName ?? "-"}
                />
              </dl>
            </div>

            {detail.events.length > 0 && (
              <div className="overflow-x-auto rounded-xl border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>시각</TableHead>
                      <TableHead>전환</TableHead>
                      <TableHead>처리 직원</TableHead>
                      <TableHead>비고</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.events.map((event) => (
                      <TableRow key={event.id}>
                        <TableCell className="whitespace-nowrap tabular-nums">
                          {dateTime(event.createdAt)}
                        </TableCell>
                        <TableCell>
                          {event.fromStatus
                            ? guestRequestStatusLabels[
                                event.fromStatus as GuestRequestStatus
                              ]
                            : "시작"}{" "}
                          →{" "}
                          {
                            guestRequestStatusLabels[
                              event.toStatus as GuestRequestStatus
                            ]
                          }
                        </TableCell>
                        <TableCell>{event.actorDisplayName}</TableCell>
                        <TableCell className="max-w-[30ch] break-words text-sm">
                          {event.note ?? "-"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}

            {detailError && (
              <p role="alert" className="break-words text-sm text-destructive">
                {detailError}
              </p>
            )}

            <div className="flex flex-wrap justify-end gap-2">
              {detailTargets.map((target) => (
                <Button
                  key={target}
                  type="button"
                  variant={target === "CLOSED" ? "destructive" : "outline"}
                  disabled={saving}
                  onClick={() => openTransition(target)}
                  data-testid={`guest-request-transition-${target}`}
                >
                  {guestRequestStatusLabels[target]}
                </Button>
              ))}
            </div>
          </DialogContent>
        )}
      </Dialog>

      <Dialog
        open={nextStatus !== null}
        onOpenChange={(open) => {
          if (!open) closeTransition();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {nextStatus
                ? `이 요청을 ${guestRequestStatusLabels[nextStatus]} 상태로 바꿀까요?`
                : ""}
            </DialogTitle>
            <DialogDescription>
              상태를 바꾸면 고객에게 안내할 내용을 남길 수 있다. 같은 요청의
              중복 처리는 멱원 키로 막는다.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor="guest-request-note">처리 안내</Label>
            <Textarea
              id="guest-request-note"
              value={resolutionNote}
              disabled={saving}
              maxLength={500}
              placeholder="고객에게 전달할 내용을 입력해 주세요."
              onChange={(event) => setResolutionNote(event.target.value)}
            />
          </div>
          {detailError && (
            <p role="alert" className="break-words text-sm text-destructive">
              {detailError}
            </p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={closeTransition}
              disabled={saving}
            >
              돌아가기
            </Button>
            <Button
              type="button"
              disabled={saving}
              onClick={() => void saveTransition()}
            >
              {saving ? "저장 중" : "상태 변경"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function DetailField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all text-sm font-medium">{value}</dd>
    </div>
  );
}
