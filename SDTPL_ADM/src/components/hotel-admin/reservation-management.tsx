"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import { format } from "date-fns";
import { ko } from "date-fns/locale";
import { CalendarSearch, Search } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  getStaffReservations,
  type StaffPrincipal,
  type StaffReservationSearchView,
  type StaffReservationSummary,
} from "@/lib/staff-api";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "속초 지점", timeZone: "Asia/Seoul" },
  { id: "11000000-0000-0000-0000-000000000002", name: "설악산 지점", timeZone: "Asia/Seoul" },
  { id: "11000000-0000-0000-0000-000000000003", name: "제주 지점", timeZone: "Asia/Seoul" },
];

const statusOptions = [
  { value: "", label: "전체 상태" },
  { value: "PENDING_PAYMENT", label: "결제 대기" },
  { value: "CONFIRMED", label: "예약 확정" },
  { value: "CHECKED_IN", label: "투숙 중" },
  { value: "CHECKED_OUT", label: "퇴실 완료" },
  { value: "CANCELLED", label: "취소" },
  { value: "EXPIRED", label: "만료" },
  { value: "NO_SHOW", label: "노쇼" },
];

function statusLabel(status: string) {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function statusVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  if (status === "CONFIRMED" || status === "CHECKED_IN") return "default";
  if (status === "CANCELLED" || status === "NO_SHOW" || status === "EXPIRED") return "destructive";
  if (status === "CHECKED_OUT") return "secondary";
  return "outline";
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", weekday: "short" })
    .format(new Date(`${value}T00:00:00`));
}

function money(value: number, currency: string) {
  if (currency === "KRW") return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency }).format(value);
}

function dateInTimeZone(timeZone: string) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function parseDate(value: string) {
  return new Date(`${value}T00:00:00`);
}

export function ReservationManagement() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [hotelId, setHotelId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<Date>();
  const [date, setDate] = useState("");
  const [queryInput, setQueryInput] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [data, setData] = useState<StaffReservationSearchView | null>(null);
  const [selectedReservation, setSelectedReservation] = useState<StaffReservationSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    function loadStoredStaff() {
      const defaultDate = dateInTimeZone(hotels[0].timeZone);
      setSelectedDate(parseDate(defaultDate));
      setDate(defaultDate);
      const stored = window.localStorage.getItem("hotel-chain-staff");
      if (!stored) return;
      try {
        const principal = JSON.parse(stored) as StaffPrincipal;
        setStaff(principal);
        setHotelId(principal.hotelId ?? hotels[0].id);
      } catch {
        setError("직원 정보를 읽지 못했습니다. 다시 로그인해 주세요.");
      }
    }
    loadStoredStaff();
  }, []);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !hotelId || !date) return;
    const sessionToken = token;
    const selectedHotelId = hotelId;
    let current = true;
    async function loadReservations() {
      setLoading(true);
      setError(null);
      setData(null);
      setSelectedReservation(null);
      try {
        const result = await getStaffReservations(sessionToken, selectedHotelId, date, { query, status });
        if (current) setData(result);
      } catch (cause) {
        if (current) setError(cause instanceof Error ? cause.message : "예약 목록을 불러오지 못했습니다.");
      } finally {
        if (current) setLoading(false);
      }
    }
    void loadReservations();
    return () => { current = false; };
  }, [date, hotelId, query, status]);

  const availableHotels = useMemo(
    () => staff?.role === "BRANCH_STAFF" ? hotels.filter((hotel) => hotel.id === staff.hotelId) : hotels,
    [staff],
  );
  const hotelName = hotels.find((hotel) => hotel.id === hotelId)?.name ?? "지점";
  const reservations = data?.reservations ?? [];

  function chooseDate(nextDate: Date | undefined) {
    if (!nextDate) return;
    setSelectedDate(nextDate);
    setDate(format(nextDate, "yyyy-MM-dd"));
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setQuery(queryInput.trim());
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-end">
        <div>
          <p className="mb-1 text-xs font-medium tracking-[0.16em] text-muted-foreground uppercase">Reservations</p>
          <h1 className="text-2xl font-semibold tracking-tight">예약 관리</h1>
          <p className="mt-1 text-sm text-muted-foreground">기준일에 도착·투숙·출발하는 예약을 한곳에서 찾고 확인합니다.</p>
        </div>
        <div className="flex flex-wrap gap-2" aria-label="지점 선택">
          {availableHotels.map((hotel) => (
            <Button key={hotel.id} aria-pressed={hotel.id === hotelId} variant={hotel.id === hotelId ? "default" : "outline"} onClick={() => setHotelId(hotel.id)}>
              {hotel.name}
            </Button>
          ))}
        </div>
      </div>

      {error && <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}

      <div className="grid items-start gap-4 xl:grid-cols-[20rem_minmax(0,1fr)]">
        <Card aria-label="예약 날짜 달력" role="group">
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><CalendarSearch className="size-4" />조회 날짜</CardTitle>
            <CardDescription>이 날짜에 도착·투숙·출발하는 예약을 표시합니다.</CardDescription>
          </CardHeader>
          <CardContent>
            <Calendar mode="single" locale={ko} selected={selectedDate} onSelect={chooseDate} className="w-full p-0 [--cell-size:--spacing(9)]" />
          </CardContent>
        </Card>

        <Card className="min-w-0">
          <CardHeader className="border-b">
            <CardTitle>{date ? `${displayDate(date)} · ${hotelName}` : hotelName}</CardTitle>
            <CardDescription role="status" aria-live="polite">{loading ? "예약을 불러오는 중입니다." : data?.truncated ? "200건 이상입니다. 검색 조건을 더 좁혀 주세요." : `${reservations.length}건의 예약이 있습니다.`}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_10rem_auto]" onSubmit={submitSearch}>
              <div className="space-y-1.5">
                <Label htmlFor="reservation-search">예약 검색</Label>
                <div className="relative">
                  <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input id="reservation-search" aria-label="예약 검색" className="pl-8" value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="이름, 이메일, 예약 번호" />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="reservation-status">예약 상태</Label>
                <select id="reservation-status" aria-label="예약 상태" className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50" value={status} onChange={(event) => setStatus(event.target.value)}>
                  {statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
              </div>
              <Button className="self-end" type="submit">검색</Button>
            </form>

            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>고객</TableHead>
                    <TableHead>숙박 일정</TableHead>
                    <TableHead className="hidden md:table-cell">객실</TableHead>
                    <TableHead>상태</TableHead>
                    <TableHead className="hidden text-right sm:table-cell">결제 금액</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {!loading && reservations.length === 0 ? (
                    <TableRow><TableCell colSpan={5} className="h-28 text-center text-muted-foreground">조건에 맞는 예약이 없습니다.</TableCell></TableRow>
                  ) : reservations.map((reservation) => (
                    <TableRow key={reservation.reservationId}>
                      <TableCell>
                        <Button variant="ghost" className="h-auto min-w-0 justify-start px-0 py-1 text-left" aria-label={`${reservation.guestName} 예약 상세`} onClick={() => setSelectedReservation(reservation)}>
                          <span className="min-w-0"><span className="block truncate font-medium">{reservation.guestName}</span><span className="block truncate text-xs font-normal text-muted-foreground">{reservation.guestEmail}</span></span>
                        </Button>
                      </TableCell>
                      <TableCell><span className="block">{displayDate(reservation.checkIn)}</span><span className="text-xs text-muted-foreground">~ {displayDate(reservation.checkOut)}</span></TableCell>
                      <TableCell className="hidden md:table-cell"><span className="block">{reservation.roomTypeName}</span><span className="text-xs text-muted-foreground">{reservation.rooms}실 · {reservation.assignedRoomNumbers.length ? `${reservation.assignedRoomNumbers.join(", ")}호` : "미배정"}</span></TableCell>
                      <TableCell><Badge variant={statusVariant(reservation.status)}>{statusLabel(reservation.status)}</Badge></TableCell>
                      <TableCell className="hidden text-right font-medium sm:table-cell">{money(reservation.totalKrw, reservation.currency)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      </div>

      <ReservationDetail reservation={selectedReservation} onOpenChange={(open) => { if (!open) setSelectedReservation(null); }} />
    </div>
  );
}

function ReservationDetail({ reservation, onOpenChange }: { reservation: StaffReservationSummary | null; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={Boolean(reservation)} onOpenChange={onOpenChange}>
      {reservation && (
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <div className="flex items-center gap-2"><Badge variant={statusVariant(reservation.status)}>{statusLabel(reservation.status)}</Badge></div>
            <DialogTitle className="text-xl">{reservation.guestName} 고객 예약</DialogTitle>
            <DialogDescription className="break-all">예약 번호 {reservation.reservationId}</DialogDescription>
          </DialogHeader>
          <dl className="grid gap-x-6 gap-y-4 border-y py-4 sm:grid-cols-2">
            <Detail label="이메일" value={reservation.guestEmail} />
            <Detail label="숙박 일정" value={`${displayDate(reservation.checkIn)} ~ ${displayDate(reservation.checkOut)}`} />
            <Detail label="객실·요금제" value={`${reservation.roomTypeName} · ${reservation.ratePlanName}`} />
            <Detail label="투숙 인원" value={`성인 ${reservation.adults}명 · 아동 ${reservation.children}명 · ${reservation.rooms}실`} />
            <Detail label="배정 객실" value={reservation.assignedRoomNumbers.length ? `${reservation.assignedRoomNumbers.join(", ")}호` : "아직 배정되지 않음"} />
            <Detail label="결제 금액" value={money(reservation.totalKrw, reservation.currency)} />
          </dl>
          <p className="text-xs text-muted-foreground">예약 변경과 운영 처리는 별도 권한이 필요한 기능입니다. 체크인·체크아웃은 오늘의 운영에서 처리하세요.</p>
        </DialogContent>
      )}
    </Dialog>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-medium text-muted-foreground">{label}</dt><dd className="mt-1 font-medium">{value}</dd></div>;
}
