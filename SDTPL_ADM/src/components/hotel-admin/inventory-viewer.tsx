"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Boxes, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  getStaffInventory,
  StaffApiError,
  type InventoryView,
  type StaffPrincipal,
} from "@/lib/staff-api";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "속초 지점" },
  { id: "11000000-0000-0000-0000-000000000002", name: "설악산 지점" },
  { id: "11000000-0000-0000-0000-000000000003", name: "제주 지점" },
];

const rangePresets = [
  { days: 7, label: "7일" },
  { days: 14, label: "14일" },
  { days: 30, label: "30일" },
];

function isoDate(value: Date) {
  return value.toISOString().slice(0, 10);
}

function defaultRange(days: number) {
  const start = new Date();
  return { from: isoDate(start), to: isoDate(new Date(start.getTime() + (days - 1) * 86400000)) };
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric" })
    .format(new Date(`${value}T00:00:00`));
}

function displayWeekday(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { weekday: "short" })
    .format(new Date(`${value}T00:00:00`));
}

export function InventoryViewer() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [inventory, setInventory] = useState<InventoryView | null>(null);
  const [hotelId, setHotelId] = useState(hotels[0].id);
  const [range, setRange] = useState(defaultRange(14));
  const from = range.from;
  const to = range.to;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const sessionToken = typeof window !== "undefined" ? window.localStorage.getItem("hotel-chain-staff-session") : null;

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

  const loadInventory = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken) return;
    setLoading(true);
    setError("");
    try {
      const next = await getStaffInventory(sessionToken, hotelId, { from, to });
      if (!mounted.current || requestGeneration.current !== generation) return;
      setInventory(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setInventory(null);
        setError(cause instanceof StaffApiError ? cause.message : "재고를 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, [hotelId, from, to, sessionToken]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void loadInventory();
  }, [isHeadquarters, loadInventory]);

  const roomTypes = inventory?.roomTypes ?? [];
  // 모든 객실 유형의 숙박일을 합쳐 가로축을 만든다. 유형마다 빠진 날짜가 있을 수 있다.
  const columns = Array.from(new Set(roomTypes.flatMap((roomType) => roomType.days.map((day) => day.stayDate)))).sort();
  const totalDays = columns.length;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Boxes className="h-5 w-5" aria-hidden />
            재고·가격
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            객실 유형(행)과 숙박일(열)로 잔여 재고를 한 화면에 비교한다. 재고 조정·판매 중지 구간 설정은 아직 지원하지 않는다.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void loadInventory()} disabled={loading || !isHeadquarters}>
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          재고·가격 관리는 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && (
        <div className="flex flex-wrap gap-2">
          {hotels.map((hotel) => (
            <Button
              key={hotel.id}
              type="button"
              variant={hotel.id === hotelId ? "default" : "outline"}
              aria-pressed={hotel.id === hotelId}
              onClick={() => setHotelId(hotel.id)}
            >
              {hotel.name}
            </Button>
          ))}
        </div>
      )}

      {isHeadquarters && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1">
            <Label htmlFor="inventory-from">시작일</Label>
            <Input id="inventory-from" type="date" value={from} onChange={(event) => setRange((prev) => ({ ...prev, from: event.target.value }))} />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="inventory-to">종료일</Label>
            <Input id="inventory-to" type="date" value={to} onChange={(event) => setRange((prev) => ({ ...prev, to: event.target.value }))} />
          </div>
          <div className="flex flex-wrap gap-1.5 pb-0.5">
            {rangePresets.map((preset) => (
              <Button
                key={preset.days}
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setRange(defaultRange(preset.days))}
              >
                {preset.label}
              </Button>
            ))}
          </div>
        </div>
      )}

      {roomTypes.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">객실 유형 {roomTypes.length}종 · 숙박일 {totalDays}일</CardTitle>
            <CardDescription>
              셀의 숫자는 판매 가능 잔여 수다. 매진 셀은 빨간색으로 표시하고 화면에서 값을 변경하지 않는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th scope="col" className="sticky left-0 z-10 min-w-[8.5rem] border-b border-r bg-card p-2 text-left font-medium">
                    객실 유형
                  </th>
                  {columns.map((stayDate) => {
                    const isWeekend = [0, 6].includes(new Date(`${stayDate}T00:00:00`).getDay());
                    return (
                      <th
                        key={stayDate}
                        scope="col"
                        className={`border-b p-2 text-center font-medium ${isWeekend ? "bg-muted/50 text-muted-foreground" : ""}`}
                      >
                        <span className="block tabular-nums">{displayDate(stayDate)}</span>
                        <span className="block text-xs font-normal text-muted-foreground">{displayWeekday(stayDate)}</span>
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {roomTypes.map((roomType) => (
                  <tr key={roomType.roomTypeId}>
                    <th scope="row" className="sticky left-0 z-10 min-w-[8.5rem] border-b border-r bg-card p-2 text-left font-medium">
                      {roomType.name}
                      <span className="block text-xs font-normal text-muted-foreground">{roomType.maxOccupancy}명까지</span>
                    </th>
                    {columns.map((stayDate) => {
                      const day = roomType.days.find((candidate) => candidate.stayDate === stayDate);
                      if (!day) {
                        return <td key={stayDate} className="border-b p-2 text-center text-muted-foreground">-</td>;
                      }
                      const soldOut = day.remaining === 0;
                      return (
                        <td
                          key={stayDate}
                          className={`border-b p-2 text-center tabular-nums ${soldOut ? "bg-destructive/10 font-medium text-destructive" : ""}`}
                        >
                          {soldOut ? "매진" : day.remaining}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}

      {roomTypes.length > 0 && (
        <div className="flex flex-wrap gap-x-5 gap-y-1.5 text-xs text-muted-foreground">
          <span>잔여 = 총량 - 보류 - 확정</span>
          <span className="text-destructive">매진 = 잔여 0</span>
          <span>- = 해당 일자 재고 없음</span>
        </div>
      )}

      {isHeadquarters && totalDays === 0 && !error && (
        <p className="text-sm text-muted-foreground">선택한 기간에 등록된 재고가 없거나 불러오는 중입니다.</p>
      )}
    </div>
  );
}
