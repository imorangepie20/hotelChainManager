"use client";

import { useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { assignRoom, completeStaffOperation, getAssignableRooms, getDailyOperations, type DailyOperationsView, type StaffPrincipal } from "@/lib/staff-api";
import { RoomOperationsPanel } from "@/components/hotel-admin/room-operations-panel";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "\uC18D\uCD08 \uC9C0\uC810" },
  { id: "11000000-0000-0000-0000-000000000002", name: "\uC124\uC545\uC0B0 \uC9C0\uC810" },
  { id: "11000000-0000-0000-0000-000000000003", name: "\uC81C\uC8FC \uC9C0\uC810" },
];

const today = () => new Date().toISOString().slice(0, 10);

export function DailyOperations() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [hotelId, setHotelId] = useState<string | null>(null);
  const [date, setDate] = useState(today);
  const [data, setData] = useState<DailyOperationsView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [processing, setProcessing] = useState<string | null>(null);

  useEffect(() => {
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      const principal = JSON.parse(stored) as StaffPrincipal;
      setStaff(principal);
      setHotelId(principal.hotelId ?? hotels[0].id);
    } catch {
      setError("\uC9C1\uC6D0 \uC815\uBCF4\uB97C \uC77D\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.");
    }
  }, []);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !hotelId) return;
    setLoading(true);
    setError(null);
    void getDailyOperations(token, hotelId, date)
      .then(setData)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "\uC870\uD68C\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4."))
      .finally(() => setLoading(false));
  }, [date, hotelId, refreshVersion]);

  async function runOperation(path: string, successMessage: string) {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setProcessing(path); setError(null); setNotice(null);
    try {
      await completeStaffOperation(token, path);
      setNotice(successMessage);
      setRefreshVersion((version) => version + 1);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "운영 처리에 실패했습니다.");
    } finally {
      setProcessing(null);
    }
  }

  const availableHotels = useMemo(
    () => staff?.role === "BRANCH_STAFF" ? hotels.filter((hotel) => hotel.id === staff.hotelId) : hotels,
    [staff],
  );
  const hotelName = hotels.find((hotel) => hotel.id === hotelId)?.name ?? "\uC9C0\uC810";

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{"\uB2F9\uC77C \uC6B4\uC601"}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{"\uB3C4\uCC29\u00B7\uCD9C\uBC1C \uC608\uC57D\uACFC \uCCAD\uC18C \uD544\uC694 \uAC1D\uC2E4\uC744 \uC9C0\uC810\uBCC4\uB85C \uD655\uC778\uD569\uB2C8\uB2E4."}</p>
        </div>
        <label className="text-sm font-medium">
          {"\uAE30\uC900 \uB0A0\uC9DC"}
          <input aria-label="\uAE30\uC900 \uB0A0\uC9DC" className="mt-1 block h-8 rounded-lg border bg-background px-2" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </label>
      </div>

      <div className="flex flex-wrap gap-2" aria-label="\uC9C0\uC810 \uC120\uD0DD">
        {availableHotels.map((hotel) => <Button key={hotel.id} variant={hotel.id === hotelId ? "default" : "outline"} onClick={() => setHotelId(hotel.id)}>{hotel.name}</Button>)}
      </div>

      {error && <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
      {notice && <p className="rounded-lg border border-primary/20 bg-primary/5 p-3 text-sm text-primary">{notice}</p>}
      {loading && <p className="text-sm text-muted-foreground">{"\uB2F9\uC77C \uC6B4\uC601 \uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uB294 \uC911\uC785\uB2C8\uB2E4."}</p>}

      {hotelId && <RoomOperationsPanel key={hotelId} hotelId={hotelId} />}

      <div className="grid gap-4 lg:grid-cols-3">
        <OperationsCard title="\uB3C4\uCC29 \uC608\uC815" subtitle={hotelName} items={data?.arrivals ?? []} onAction={runOperation} processing={processing} onAssigned={(roomNumber) => { setNotice(`${roomNumber}호를 배정했습니다.`); setRefreshVersion((version) => version + 1); }} />
        <OperationsCard title="\uCD9C\uBC1C \uC608\uC815" subtitle={hotelName} items={data?.departures ?? []} onAction={runOperation} processing={processing} onAssigned={() => {}} />
        <OperationsCard title="\uCCAD\uC18C \uD544\uC694 \uAC1D\uC2E4" subtitle={hotelName} items={data?.roomsNeedingCleaning ?? []} roomList onAction={runOperation} processing={processing} onAssigned={() => {}} />
      </div>
    </div>
  );
}

type OperationsItem = {
  physicalRoomId?: string;
  reservationId?: string;
  roomNumber?: string;
  guestName?: string;
  roomTypeName: string;
  assignedRoomNumbers?: string[];
  status?: string;
};

function OperationsCard({ title, subtitle, items, roomList = false, onAction, processing, onAssigned }: { title: string; subtitle: string; items: OperationsItem[]; roomList?: boolean; onAction: (path: string, message: string) => Promise<void>; processing: string | null; onAssigned: (roomNumber: string) => void }) {
  return <Card><CardHeader><CardTitle>{title}</CardTitle><CardDescription>{subtitle}</CardDescription></CardHeader><CardContent><ul className="space-y-3">{items.length === 0 ? <li className="text-sm text-muted-foreground">{"\uD574\uB2F9 \uB0B4\uC5ED\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."}</li> : items.map((item) => {
    const path = roomList ? `/api/staff/rooms/${item.physicalRoomId}/housekeeping-complete` : item.status === "CHECKED_IN" ? `/api/staff/reservations/${item.reservationId}/check-out` : item.status === "CONFIRMED" ? `/api/staff/reservations/${item.reservationId}/check-in` : null;
    const label = roomList ? "청소 완료" : item.status === "CHECKED_IN" ? "체크아웃 처리" : item.status === "CONFIRMED" ? "체크인 처리" : null;
    const message = roomList ? "청소 완료 처리가 완료되었습니다." : item.status === "CHECKED_IN" ? "체크아웃 처리가 완료되었습니다." : "체크인 처리가 완료되었습니다.";
    const noShowPath = !roomList && item.status === "CONFIRMED" && item.reservationId ? `/api/staff/reservations/${item.reservationId}/no-show` : null;
    return <li key={roomList ? item.physicalRoomId : item.reservationId} className="rounded-lg bg-muted/60 p-3"><p className="font-medium">{roomList ? item.roomNumber : item.guestName}</p><p className="mt-1 text-xs text-muted-foreground">{roomList ? item.roomTypeName : `${item.roomTypeName}${item.assignedRoomNumbers?.length ? ` · ${item.assignedRoomNumbers.join(", ")}` : ""}`}</p>{item.status === "CONFIRMED" && !item.assignedRoomNumbers?.length && item.reservationId && <AssignmentControl reservationId={item.reservationId} guestName={item.guestName ?? "예약 고객"} onAssigned={onAssigned} />}{path && label && <Button size="sm" className="mt-3" disabled={processing === path} onClick={() => void onAction(path, message)}>{processing === path ? "처리 중" : label}</Button>}{noShowPath && <Button size="sm" variant="outline" className="mt-3 ml-2" disabled={processing === noShowPath} onClick={() => void onAction(noShowPath, "노쇼 처리가 완료되었습니다.")}>{processing === noShowPath ? "처리 중" : "노쇼 처리"}</Button>}</li>;
  })}</ul></CardContent></Card>;
}

function AssignmentControl({ reservationId, guestName, onAssigned }: { reservationId: string; guestName: string; onAssigned: (roomNumber: string) => void }) {
  const [rooms, setRooms] = useState<{ id: string; roomNumber: string }[]>([]);
  const [selected, setSelected] = useState("");
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  async function loadRooms() {
    const token = window.localStorage.getItem("hotel-chain-staff-session"); if (!token) return;
    setLoading(true); try { const result = await getAssignableRooms(token, reservationId); setRooms(result); setSelected(result[0]?.id ?? ""); setOpen(true); } finally { setLoading(false); }
  }
  async function assign() {
    const token = window.localStorage.getItem("hotel-chain-staff-session"); const room = rooms.find((candidate) => candidate.id === selected);
    if (!token || !room) return;
    setLoading(true); try { await assignRoom(token, reservationId, room.id); onAssigned(room.roomNumber); setOpen(false); } finally { setLoading(false); }
  }
  return <div className="mt-3">{!open ? <Button size="sm" variant="outline" disabled={loading} onClick={() => void loadRooms()}>{loading ? "객실 조회 중" : "객실 배정"}</Button> : <div className="flex flex-wrap items-end gap-2"><label className="grid gap-1 text-xs">{guestName} 객실 선택<select aria-label={`${guestName} 객실 선택`} className="h-8 rounded-lg border bg-background px-2" value={selected} onChange={(event) => setSelected(event.target.value)}>{rooms.map((room) => <option key={room.id} value={room.id}>{room.roomNumber}호</option>)}</select></label><Button size="sm" disabled={!selected || loading} onClick={() => void assign()}>{loading ? "배정 중" : "배정 완료"}</Button></div>}</div>;
}
