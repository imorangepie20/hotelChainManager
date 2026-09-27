"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { BellRing, Inbox, RefreshCw, UserRoundCog } from "lucide-react";

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
  getGuestRequestAssignees,
  getGuestRequestNotifications,
  getGuestRequests,
  guestRequestNotificationLabels,
  guestRequestPriorityLabels,
  guestRequestStatusLabels,
  guestRequestTypeLabels,
  StaffApiError,
  transitionGuestRequest,
  type GuestRequestAssignee,
  type GuestRequestDetail,
  type GuestRequestNotification,
  type GuestRequestNotificationType,
  type GuestRequestPriority,
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

const PRIORITIES: GuestRequestPriority[] = ["LOW", "NORMAL", "HIGH"];

const NOTIFICATION_TYPES: GuestRequestNotificationType[] = [
  "FRONT_DESK",
  "HOUSEKEEPING",
  "REFUND_REVIEW",
  "GENERAL",
];

function dateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
}

function eventChange(event: GuestRequestDetail["events"][number]) {
  if (event.eventType === "CREATED") {
    return `요청 생성 · 우선순위 ${event.toPriority ?? "NORMAL"}`;
  }
  if (event.eventType === "ASSIGNED") {
    return `담당자 ${event.fromAssignedDisplayName ?? "미지정"} → ${event.toAssignedDisplayName ?? "미지정"}`;
  }
  if (event.eventType === "PRIORITY_CHANGED") {
    return `우선순위 ${event.fromPriority ?? "없음"} → ${event.toPriority ?? "없음"}`;
  }
  if (event.fromStatus || event.toStatus) {
    const from = event.fromStatus
      ? guestRequestStatusLabels[event.fromStatus]
      : "시작";
    const to = event.toStatus
      ? guestRequestStatusLabels[event.toStatus]
      : "상태 없음";
    return `${from} → ${to}`;
  }
  return event.eventType === "NOTE_ADDED" ? "메모 추가" : event.eventType;
}

export function GuestRequests() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [requests, setRequests] = useState<GuestRequestSummary[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [status, setStatus] = useState<GuestRequestStatus>("OPEN");
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notifications, setNotifications] = useState<
    GuestRequestNotification[]
  >([]);
  const [detail, setDetail] = useState<GuestRequestDetail | null>(null);
  const [detailError, setDetailError] = useState("");
  const [nextStatus, setNextStatus] = useState<GuestRequestStatus | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [manageOpen, setManageOpen] = useState(false);
  const [assignees, setAssignees] = useState<GuestRequestAssignee[]>([]);
  const [assigneeLoading, setAssigneeLoading] = useState(false);
  const [selectedAssignee, setSelectedAssignee] = useState("");
  const [selectedPriority, setSelectedPriority] =
    useState<GuestRequestPriority>("NORMAL");
  const idempotencyKey = useRef("");
  const manageIdempotencyKey = useRef("");
  const assigneeSelect = useRef<HTMLSelectElement>(null);
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

  const refreshNotifications = useCallback(async () => {
    if (!sessionToken) return;
    try {
      const next = await getGuestRequestNotifications(sessionToken);
      if (mounted.current) setNotifications(next.notifications ?? []);
    } catch {
      // 알림은 보조 정보이므로 목록 조회를 막지 않는다.
      if (mounted.current) setNotifications([]);
    }
  }, [sessionToken]);

  useEffect(() => {
    if (!isStaff) return;
    void refresh();
  }, [isStaff, refresh]);

  useEffect(() => {
    if (!isStaff) return;
    void refreshNotifications();
  }, [isStaff, refreshNotifications]);

  useEffect(() => {
    if (manageOpen && !assigneeLoading) assigneeSelect.current?.focus();
  }, [assigneeLoading, manageOpen]);

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

  async function openManage() {
    if (!sessionToken || !detail) return;
    setManageOpen(true);
    setAssignees([]);
    setSelectedAssignee(detail.assignedTo ?? "");
    setSelectedPriority(detail.priority);
    setDetailError("");
    setAssigneeLoading(true);
    manageIdempotencyKey.current = window.crypto.randomUUID();
    try {
      const next = await getGuestRequestAssignees(sessionToken, detail.id);
      setAssignees(next.assignees ?? []);
    } catch (cause) {
      setDetailError(
        cause instanceof StaffApiError
          ? cause.message
          : "담당자 목록을 불러오지 못했습니다.",
      );
    } finally {
      setAssigneeLoading(false);
    }
  }

  function closeManage() {
    if (saving) return;
    setManageOpen(false);
    setAssignees([]);
    manageIdempotencyKey.current = "";
  }

  function changeManageInput(
    kind: "assignee" | "priority",
    value: string,
  ) {
    if (kind === "assignee") setSelectedAssignee(value);
    else setSelectedPriority(value as GuestRequestPriority);
    manageIdempotencyKey.current = window.crypto.randomUUID();
  }

  async function saveManagement() {
    if (
      !sessionToken ||
      !detail ||
      !manageIdempotencyKey.current ||
      assigneeLoading
    )
      return;
    setSaving(true);
    setDetailError("");
    try {
      const updated = await transitionGuestRequest(
        sessionToken,
        detail.id,
        manageIdempotencyKey.current,
        {
          status: detail.status,
          ...(selectedAssignee ? { assignTo: selectedAssignee } : {}),
          priority: selectedPriority,
        },
      );
      setDetail(updated);
      setManageOpen(false);
      setAssignees([]);
      manageIdempotencyKey.current = "";
      void refresh();
      void refreshNotifications();
    } catch (cause) {
      setDetailError(
        cause instanceof StaffApiError
          ? cause.message
          : "담당자와 우선순위를 바꾸지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
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
      void refreshNotifications();
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
  const notificationCounts = NOTIFICATION_TYPES.map((type) => ({
    type,
    count: notifications.filter(
      (notification) => notification.notificationType === type,
    ).length,
  })).filter(({ count }) => count > 0);

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
          onClick={() => {
            void refresh();
            void refreshNotifications();
          }}
          disabled={loading || !isStaff}
        >
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {isStaff && notificationCounts.length > 0 && (
        <section
          aria-labelledby="guest-request-notifications-title"
          className="rounded-xl border bg-muted/30 p-4"
        >
          <h3
            id="guest-request-notifications-title"
            className="flex items-center gap-2 text-sm font-semibold"
          >
            <BellRing className="h-4 w-4" aria-hidden />
            요청 유형별 알림
          </h3>
          <div className="mt-3 flex flex-wrap gap-2">
            {notificationCounts.map(({ type, count }) => (
              <Badge key={type} variant="secondary">
                {guestRequestNotificationLabels[type]} {count}건
              </Badge>
            ))}
          </div>
        </section>
      )}

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
                  <TableHead>우선순위</TableHead>
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
                    <TableCell>
                      {guestRequestPriorityLabels[request.priority]}
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
                <DetailField
                  label="우선순위"
                  value={guestRequestPriorityLabels[detail.priority]}
                />
              </dl>
            </div>

            {detail.events.length > 0 && (
              <div className="overflow-x-auto rounded-xl border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>시각</TableHead>
                      <TableHead>변경</TableHead>
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
                        <TableCell>{eventChange(event)}</TableCell>
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
              <Button
                type="button"
                variant="outline"
                disabled={saving}
                onClick={() => void openManage()}
              >
                <UserRoundCog className="h-4 w-4" aria-hidden />
                담당·우선순위 변경
              </Button>
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
        open={manageOpen}
        onOpenChange={(open) => {
          if (!open) closeManage();
        }}
      >
        <DialogContent className="max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>담당자와 우선순위 변경</DialogTitle>
            <DialogDescription>
              이 요청을 처리할 직원과 업무 우선순위를 지정합니다. 지정 가능한
              직원은 서버 권한 범위에 따라 제한됩니다.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <div className="grid gap-2">
              <Label htmlFor="guest-request-assignee">담당자</Label>
              <select
                id="guest-request-assignee"
                ref={assigneeSelect}
                autoFocus
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={selectedAssignee}
                disabled={saving || assigneeLoading}
                onChange={(event) =>
                  changeManageInput("assignee", event.target.value)
                }
              >
                <option value="">
                  {assigneeLoading ? "담당자 불러오는 중" : "미지정"}
                </option>
                {assignees.map((assignee) => (
                  <option key={assignee.id} value={assignee.id}>
                    {assignee.displayName}
                  </option>
                ))}
              </select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="guest-request-priority">우선순위</Label>
              <select
                id="guest-request-priority"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={selectedPriority}
                disabled={saving}
                onChange={(event) =>
                  changeManageInput("priority", event.target.value)
                }
              >
                {PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {guestRequestPriorityLabels[priority]}
                  </option>
                ))}
              </select>
            </div>
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
              onClick={closeManage}
              disabled={saving}
            >
              돌아가기
            </Button>
            <Button
              type="button"
              disabled={saving || assigneeLoading}
              onClick={() => void saveManagement()}
            >
              {saving ? "저장 중" : "변경 저장"}
            </Button>
          </DialogFooter>
        </DialogContent>
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
