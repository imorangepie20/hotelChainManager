"use client";

import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Ban, CheckCircle2, RotateCcw, Search } from "lucide-react";

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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  getRoomOperations,
  StaffApiError,
  transitionRoomOperationalStatus,
  type RoomOperationalStatus,
  type RoomOperationsView,
} from "@/lib/staff-api";

type Room = RoomOperationsView["rooms"][number];
type Transition = { room: Room; target: RoomOperationalStatus };

const statusLabel: Record<RoomOperationalStatus, string> = {
  AVAILABLE: "사용 가능",
  INSPECTION_REQUIRED: "점검 필요",
  OUT_OF_SERVICE: "판매 중지",
};

const housekeepingLabel = { CLEAN: "청소 완료", NEEDS_CLEANING: "청소 필요" } as const;

function newRequestKey() {
  return globalThis.crypto.randomUUID();
}

export function RoomOperationsPanel({ hotelId }: { hotelId: string }) {
  const [data, setData] = useState<RoomOperationsView | null>(null);
  const [filter, setFilter] = useState<"ALL" | RoomOperationalStatus>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [transition, setTransition] = useState<Transition | null>(null);
  const [reason, setReason] = useState("");
  const [expectedRecovery, setExpectedRecovery] = useState("");
  const [requestKey, setRequestKey] = useState(newRequestKey);
  const [submitting, setSubmitting] = useState(false);
  const [transitionError, setTransitionError] = useState<string | null>(null);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setData(null);
        setLoading(true);
        setError(null);
      }
    });
    void getRoomOperations(token, hotelId)
      .then((result) => {
        if (!active) return;
        setData(result);
        setTransition((current) => {
          if (!current) return null;
          const room = result.rooms.find((item) => item.physicalRoomId === current.room.physicalRoomId);
          return room ? { ...current, room } : null;
        });
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "객실 운영 상태를 불러오지 못했습니다.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [hotelId, refreshVersion]);

  const filteredRooms = useMemo(
    () => (data?.rooms ?? []).filter((room) => filter === "ALL" || room.operationalStatus === filter),
    [data, filter],
  );

  function openTransition(room: Room, target: RoomOperationalStatus) {
    setTransition({ room, target });
    setReason("");
    setExpectedRecovery("");
    setRequestKey(newRequestKey());
    setTransitionError(null);
  }

  function updateReason(value: string) {
    setReason(value);
    setRequestKey(newRequestKey());
    setTransitionError(null);
  }

  function updateRecovery(value: string) {
    setExpectedRecovery(value);
    setRequestKey(newRequestKey());
    setTransitionError(null);
  }

  async function submitTransition() {
    if (!transition || !reason.trim()) return;
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setSubmitting(true);
    setTransitionError(null);
    try {
      await transitionRoomOperationalStatus(token, transition.room.physicalRoomId, requestKey, {
        targetStatus: transition.target,
        reason: reason.trim(),
        expectedRecoveryAt: transition.target === "AVAILABLE" || !expectedRecovery
          ? null
          : new Date(expectedRecovery).toISOString(),
        expectedVersion: transition.room.operationalVersion,
      });
      setTransition(null);
      setNotice("객실 운영 상태를 변경했습니다.");
      setRefreshVersion((version) => version + 1);
    } catch (cause) {
      if (cause instanceof StaffApiError && cause.status === 409) {
        const assignments = cause.details?.assignments;
        if (assignments) {
          setTransition((current) => current ? {
            ...current,
            room: { ...current.room, impactedAssignments: assignments },
          } : null);
        }
        if (cause.code === "ROOM_OPERATIONAL_VERSION_CONFLICT") {
          setRequestKey(newRequestKey());
        }
        setRefreshVersion((version) => version + 1);
      }
      setTransitionError(cause instanceof Error ? cause.message : "객실 운영 상태를 변경하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  const blockedByAssignments = transition?.target === "OUT_OF_SERVICE"
    && transition.room.impactedAssignments.length > 0;

  return (
    <Card role="region" aria-label="객실 운영 상태" className="min-w-0">
      <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle>객실 운영 상태</CardTitle>
          <CardDescription>청소 상태와 별도로 점검·판매 중지를 관리합니다.</CardDescription>
        </div>
        <NativeSelect
          aria-label="객실 운영 상태 필터"
          value={filter}
          onChange={(event) => setFilter(event.target.value as typeof filter)}
          className="w-full sm:w-44"
        >
          <NativeSelectOption value="ALL">모든 운영 상태</NativeSelectOption>
          <NativeSelectOption value="AVAILABLE">사용 가능</NativeSelectOption>
          <NativeSelectOption value="INSPECTION_REQUIRED">점검 필요</NativeSelectOption>
          <NativeSelectOption value="OUT_OF_SERVICE">판매 중지</NativeSelectOption>
        </NativeSelect>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2" aria-label="객실 운영 요약">
          <Badge variant="outline"><Search aria-hidden="true" />점검 필요 {data?.summary.inspectionRequired ?? 0}건</Badge>
          <Badge variant="outline"><Ban aria-hidden="true" />판매 중지 {data?.summary.outOfService ?? 0}건</Badge>
          <Badge variant={data?.summary.overdueRecovery ? "destructive" : "outline"}>
            <AlertTriangle aria-hidden="true" />예상 복구 지연 {data?.summary.overdueRecovery ?? 0}건
          </Badge>
        </div>

        {notice && <p role="status" className="rounded-lg bg-primary/8 px-3 py-2 text-sm text-primary">{notice}</p>}
        {error && <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        {loading ? (
          <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3" aria-label="객실 운영 상태 불러오는 중">
            {[0, 1, 2].map((item) => <Skeleton key={item} className="h-32 rounded-xl" />)}
          </div>
        ) : filteredRooms.length === 0 ? (
          <div className="rounded-xl border border-dashed px-4 py-8 text-center">
            <CheckCircle2 className="mx-auto size-5 text-muted-foreground" aria-hidden="true" />
            <p className="mt-2 text-sm font-medium">해당 상태의 객실이 없습니다.</p>
            <p className="mt-1 text-xs text-muted-foreground">필터를 바꾸면 다른 객실을 확인할 수 있습니다.</p>
          </div>
        ) : (
          <ul className="divide-y">
            {filteredRooms.map((room) => (
              <li key={room.physicalRoomId} className="flex min-w-0 flex-col py-4 first:pt-0 last:pb-0">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{room.roomNumber}호 · {room.roomTypeName}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {housekeepingLabel[room.housekeepingStatus]} · {statusLabel[room.operationalStatus]}
                    </p>
                  </div>
                  <Badge variant={room.operationalStatus === "OUT_OF_SERVICE" ? "destructive" : "secondary"}>
                    {statusLabel[room.operationalStatus]}
                  </Badge>
                </div>
                {room.operationalReason && <p className="mt-3 text-sm">{room.operationalReason}</p>}
                {room.expectedRecoveryAt && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    예상 복구 {new Date(room.expectedRecoveryAt).toLocaleString("ko-KR")}
                  </p>
                )}
                <div className="mt-auto flex flex-wrap gap-2 pt-4">
                  {room.operationalStatus !== "INSPECTION_REQUIRED" && (
                    <Button
                      type="button"
                      size="sm"
                      className="min-h-11"
                      variant="outline"
                      disabled={loading || submitting}
                      aria-label={`${room.roomNumber}호 점검 필요로 변경`}
                      onClick={() => openTransition(room, "INSPECTION_REQUIRED")}
                    >
                      점검 필요
                    </Button>
                  )}
                  {room.operationalStatus !== "OUT_OF_SERVICE" && (
                    <Button
                      type="button"
                      size="sm"
                      className="min-h-11"
                      variant="outline"
                      disabled={loading || submitting}
                      aria-label={`${room.roomNumber}호 판매 중지`}
                      onClick={() => openTransition(room, "OUT_OF_SERVICE")}
                    >
                      판매 중지
                    </Button>
                  )}
                  {room.operationalStatus !== "AVAILABLE" && (
                    <Button
                      type="button"
                      size="sm"
                      className="min-h-11"
                      disabled={loading || submitting}
                      aria-label={`${room.roomNumber}호 사용 가능으로 복구`}
                      onClick={() => openTransition(room, "AVAILABLE")}
                    >
                      <RotateCcw aria-hidden="true" />사용 가능 복구
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>

      <AlertDialog open={transition !== null} onOpenChange={(open) => { if (!open && !submitting) setTransition(null); }}>
        <AlertDialogContent className="max-h-[calc(100dvh-1.5rem)] max-w-[calc(100vw-1.5rem)] overflow-y-auto sm:max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>
              {transition ? `${transition.room.roomNumber}호 ${statusLabel[transition.target]} 전환` : "객실 운영 상태 변경"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              변경 사유는 운영 이력에 남습니다. 사용 가능 복구는 청소 완료 객실에서만 처리됩니다.
            </AlertDialogDescription>
          </AlertDialogHeader>

          {transition && (
            <div className="space-y-4">
              {transition.room.impactedAssignments.length > 0 && (
                <section className="rounded-xl bg-muted/70 p-3" aria-label="영향 예약">
                  <p className="text-sm font-medium">영향 예약 {transition.room.impactedAssignments.length}건</p>
                  <ul className="mt-2 space-y-2">
                    {transition.room.impactedAssignments.map((assignment) => (
                      <li key={assignment.reservationId} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                        <span>{assignment.guestName} · {assignment.checkIn}~{assignment.checkOut}</span>
                        <a
                          className="font-medium text-primary underline underline-offset-4"
                          href={`/dashboard/reservations?date=${assignment.checkIn}&hotelId=${hotelId}&reservationId=${assignment.reservationId}`}
                        >
                          {assignment.guestName} 예약 보기
                        </a>
                      </li>
                    ))}
                  </ul>
                  {blockedByAssignments && (
                    <p className="mt-3 text-xs text-destructive">배정된 예약을 먼저 다른 객실로 이동해야 판매 중지할 수 있습니다.</p>
                  )}
                </section>
              )}

              <div className="grid gap-2">
                <Label htmlFor="room-operation-reason">변경 사유</Label>
                <Textarea
                  id="room-operation-reason"
                  value={reason}
                  maxLength={500}
                  placeholder="점검 또는 판매 중지 사유를 입력하세요."
                  onChange={(event) => updateReason(event.target.value)}
                />
              </div>
              {transition.target !== "AVAILABLE" && (
                <div className="grid gap-2">
                  <Label htmlFor="room-operation-recovery">예상 복구 시각 (선택)</Label>
                  <Input
                    id="room-operation-recovery"
                    type="datetime-local"
                    value={expectedRecovery}
                    onChange={(event) => updateRecovery(event.target.value)}
                  />
                </div>
              )}
              {transitionError && <p role="alert" className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{transitionError}</p>}
            </div>
          )}

          <AlertDialogFooter>
            <AlertDialogCancel disabled={submitting}>취소</AlertDialogCancel>
            <AlertDialogAction
              type="button"
              disabled={!reason.trim() || blockedByAssignments || submitting}
              onClick={() => void submitTransition()}
            >
              {submitting ? "처리 중" : `${transition ? statusLabel[transition.target] : "상태 변경"} 확정`}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
