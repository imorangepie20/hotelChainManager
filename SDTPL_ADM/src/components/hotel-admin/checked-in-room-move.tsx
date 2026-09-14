"use client";

import { type FormEvent, useRef, useState } from "react";
import { ArrowRight } from "lucide-react";

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
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import {
  getCheckedInRoomMoveOptions,
  moveCheckedInRoom,
  type CheckedInRoomMoveOptions,
  type CheckedInRoomMoveResult,
  type StaffReservationSummary,
} from "@/lib/staff-api";

export function CheckedInRoomMove({
  reservation,
  onMoved,
}: {
  reservation: StaffReservationSummary;
  onMoved: (result: CheckedInRoomMoveResult) => void;
}) {
  const [options, setOptions] = useState<CheckedInRoomMoveOptions | null>(null);
  const [currentRoomId, setCurrentRoomId] = useState("");
  const [newRoomId, setNewRoomId] = useState("");
  const [reason, setReason] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const confirmTriggerRef = useRef<HTMLButtonElement>(null);

  async function loadOptions() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const result = await getCheckedInRoomMoveOptions(token, reservation.reservationId);
      setOptions(result);
      setCurrentRoomId(result.assignments[0]?.id ?? "");
      setNewRoomId(result.candidates[0]?.id ?? "");
      setReason("");
      setIdempotencyKey(window.crypto.randomUUID());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "이동 가능한 객실을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function changeValue(setValue: (value: string) => void, value: string) {
    setValue(value);
    setIdempotencyKey(window.crypto.randomUUID());
    setError(null);
  }

  function confirmMove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentRoomId || !newRoomId || !reason.trim()) return;
    setError(null);
    setConfirming(true);
  }

  async function moveRoom() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !idempotencyKey || !currentRoomId || !newRoomId || !reason.trim()) return;
    setSaving(true);
    setError(null);
    try {
      const result = await moveCheckedInRoom(token, reservation.reservationId, idempotencyKey, {
        currentPhysicalRoomId: currentRoomId,
        newPhysicalRoomId: newRoomId,
        reason: reason.trim(),
      });
      setConfirming(false);
      setOptions(null);
      setNotice(`${result.roomNumber}호로 이동했습니다. 기존 ${result.previousRoomNumber}호는 청소·점검 필요 상태입니다.`);
      onMoved(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "투숙 객실을 이동하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  const currentRoom = options?.assignments.find((room) => room.id === currentRoomId);
  const newRoom = options?.candidates.find((room) => room.id === newRoomId);

  return (
    <section aria-labelledby="checked-in-room-move-title" className="space-y-3 border-b pb-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 id="checked-in-room-move-title" className="text-sm font-medium">투숙 중 객실 이동</h3>
          <p className="mt-1 text-xs text-muted-foreground">같은 객실 유형의 청결하고 운영 가능한 객실로 이동합니다.</p>
        </div>
        {!options && (
          <Button type="button" variant="outline" disabled={loading} onClick={() => void loadOptions()}>
            {loading ? "후보 조회 중" : "이동 후보 조회"}
          </Button>
        )}
      </div>

      {options && (
        <form className="space-y-3" onSubmit={confirmMove}>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="checked-in-current-room">현재 객실</Label>
              <NativeSelect
                id="checked-in-current-room"
                className="w-full"
                value={currentRoomId}
                disabled={saving}
                onChange={(event) => changeValue(setCurrentRoomId, event.target.value)}
              >
                {options.assignments.map((room) => (
                  <NativeSelectOption key={room.id} value={room.id}>{room.roomNumber}호</NativeSelectOption>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="checked-in-new-room">새 객실</Label>
              {options.candidates.length > 0 ? (
                <NativeSelect
                  id="checked-in-new-room"
                  className="w-full"
                  value={newRoomId}
                  disabled={saving}
                  onChange={(event) => changeValue(setNewRoomId, event.target.value)}
                >
                  {options.candidates.map((room) => (
                    <NativeSelectOption key={room.id} value={room.id}>{room.roomNumber}호</NativeSelectOption>
                  ))}
                </NativeSelect>
              ) : (
                <p className="rounded-lg bg-muted/60 p-2 text-sm text-muted-foreground">현재 이동 가능한 객실이 없습니다.</p>
              )}
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="checked-in-room-move-reason">이동 사유</Label>
            <Textarea
              id="checked-in-room-move-reason"
              value={reason}
              maxLength={500}
              disabled={saving}
              placeholder="설비 고장, 소음 등 이동 사유를 입력하세요."
              onChange={(event) => changeValue(setReason, event.target.value)}
            />
          </div>
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="ghost" disabled={saving} onClick={() => { setOptions(null); setError(null); }}>
              닫기
            </Button>
            {options.candidates.length > 0 && (
              <Button
                ref={confirmTriggerRef}
                type="submit"
                disabled={!currentRoomId || !newRoomId || !reason.trim() || saving}
              >
                객실 이동 확인
              </Button>
            )}
          </div>
        </form>
      )}

      {notice && <p role="status" className="rounded-lg bg-primary/8 p-3 text-sm text-primary">{notice}</p>}
      {error && !confirming && <p role="alert" className="text-sm text-destructive">{error}</p>}

      <AlertDialog open={confirming} onOpenChange={(open) => {
        if (!open && !saving) {
          setConfirming(false);
          setError(null);
          window.requestAnimationFrame(() => confirmTriggerRef.current?.focus());
        }
      }}>
        <AlertDialogContent className="max-w-[calc(100vw-1.5rem)] sm:max-w-sm">
          <AlertDialogHeader>
            <AlertDialogTitle>객실 이동을 확정할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              {currentRoom?.roomNumber ?? "현재"}호에서 {newRoom?.roomNumber ?? "새"}호로 이동합니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="flex items-center justify-center gap-3 rounded-xl bg-muted/70 p-4 font-medium tabular-nums">
            <span>{currentRoom?.roomNumber}호</span>
            <ArrowRight className="size-4 text-muted-foreground" aria-hidden="true" />
            <span>{newRoom?.roomNumber}호</span>
          </div>
          <p className="text-sm">이동 사유: {reason.trim()}</p>
          <p className="text-xs text-muted-foreground">완료 후 기존 객실은 청소 필요·점검 필요 상태로 전환됩니다.</p>
          {error && <p role="alert" className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={saving}>돌아가기</AlertDialogCancel>
            <AlertDialogAction type="button" disabled={saving} onClick={() => void moveRoom()}>
              {saving ? "이동 중" : "객실 이동 확정"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
