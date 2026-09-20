"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Hotel, Plus, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  createRoomType,
  getRoomTypeCatalog,
  StaffApiError,
  type RoomTypeCatalogView,
  type StaffPrincipal,
} from "@/lib/staff-api";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "속초 지점" },
  { id: "11000000-0000-0000-0000-000000000002", name: "설악산 지점" },
  { id: "11000000-0000-0000-0000-000000000003", name: "제주 지점" },
];

function money(value: number | null | undefined) {
  if (value === null || value === undefined) return "-";
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function range(min: number, max: number) {
  if (min === max) return money(min);
  return `${money(min)} ~ ${money(max)}`;
}

export function HotelCatalog() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [catalog, setCatalog] = useState<RoomTypeCatalogView | null>(null);
  const [hotelId, setHotelId] = useState(hotels[0].id);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createOccupancy, setCreateOccupancy] = useState("2");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createNotice, setCreateNotice] = useState("");
  const [createKey, setCreateKey] = useState(0);
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

  const refresh = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken) return;
    setLoading(true);
    setError("");
    try {
      const next = await getRoomTypeCatalog(sessionToken, hotelId);
      if (!mounted.current || requestGeneration.current !== generation) return;
      setCatalog(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setCatalog(null);
        setError(cause instanceof StaffApiError ? cause.message : "객실 유형 목록을 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, [hotelId, sessionToken]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void refresh();
  }, [isHeadquarters, refresh]);

  async function createRoomTypeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const name = createName.trim();
    const occupancy = Number(createOccupancy);
    if (!name) {
      setCreateError("객실 유형 이름을 입력해 주세요.");
      return;
    }
    if (!Number.isInteger(occupancy) || occupancy < 1 || occupancy > 20) {
      setCreateError("최대 인원은 1 이상 20 이하여야 합니다.");
      return;
    }
    setCreating(true);
    setCreateError("");
    try {
      const result = await createRoomType(sessionToken, hotelId, `create-room-type-${createKey}`, { name, maxOccupancy: occupancy });
      setCreateOpen(false);
      setCreateName("");
      setCreateOccupancy("2");
      setCreateKey((key) => key + 1);
      await refresh();
      // 시드가 끝나야 고객 검색에 나타난다. 재호출(created=false)이면 안내만 넘긴다.
      const seed = result.seed;
      if (seed && seed.created) {
        setCreateNotice(
          `${name}에 기본 요금제와 ${seed.pricedDays}일분 일자 재고를 심었습니다. ` +
          `기본 요금 ${seed.defaultRateKrw.toLocaleString("ko-KR")}원, 객실 ${seed.inventoryCapacity}실입니다. ` +
          `일자별 요금·재고는 이 화면의 다음 단계에서 조정합니다.`,
        );
      }
    } catch (cause) {
      setCreateError(cause instanceof StaffApiError ? cause.message : "객실 유형을 추가하지 못했습니다.");
    } finally {
      setCreating(false);
    }
  }

  function openCreateDialog() {
    setCreateError("");
    setCreateNotice("");
    setCreateOpen(true);
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Hotel className="h-5 w-5" aria-hidden />
            호텔 및 객실
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            객실 유형과 요금제를 읽기 전용으로 확인한다. 가격·재고·예약 확정의 권한은 서버에 있다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void refresh()} disabled={loading || !isHeadquarters}>
            <RefreshCw className="h-4 w-4" aria-hidden />
            {loading ? "불러오는 중" : "새로고침"}
          </Button>
          {isHeadquarters && (
            <Button type="button" onClick={openCreateDialog} data-testid="create-room-type">
              <Plus className="h-4 w-4" aria-hidden />
              객실 유형 추가
            </Button>
          )}
        </div>
      </div>

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다.
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

      {catalog && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">객실 유형 {catalog.totalCount}종</CardTitle>
            <CardDescription>
              객실 유형별로 등록된 요금제와 일자별 요금 범위를 보여준다. 화면에서 값을 변경하지 않는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>객실 유형</TableHead>
                  <TableHead className="text-right">최대 인원</TableHead>
                  <TableHead>요금제</TableHead>
                  <TableHead>조식</TableHead>
                  <TableHead className="text-right">요금 범위</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {catalog.roomTypes.map((roomType) => {
                  const prices = roomType.ratePlans.flatMap((plan) =>
                    plan.minAmountKrw !== null && plan.maxAmountKrw !== null
                      ? [plan.minAmountKrw, plan.maxAmountKrw]
                      : [],
                  );
                  return (
                    <TableRow key={roomType.roomTypeId} data-room-type-id={roomType.roomTypeId}>
                      <TableCell className="font-medium">{roomType.name}</TableCell>
                      <TableCell className="text-right tabular-nums">{roomType.maxOccupancy}명</TableCell>
                      <TableCell>
                        {roomType.ratePlans.length === 0 ? (
                          <span className="text-xs text-muted-foreground">등록된 요금제 없음</span>
                        ) : (
                          <div className="grid gap-1">
                            {roomType.ratePlans.map((plan) => (
                              <span key={plan.ratePlanId} data-testid="rate-plan-name" className="text-sm">{plan.name}</span>
                            ))}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="grid gap-1">
                          {roomType.ratePlans.map((plan) => (
                            <Badge key={plan.ratePlanId} variant={plan.breakfastIncluded ? "default" : "secondary"}>
                              {plan.breakfastIncluded ? "포함" : "객실만"}
                            </Badge>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell className="text-right tabular-nums align-top">
                        {prices.length === 0
                          ? "요금 미등록"
                          : range(Math.min(...prices), Math.max(...prices))}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && !catalog && !error && (
        <p className="text-sm text-muted-foreground">객실 유형 목록을 불러오는 중입니다.</p>
      )}

      {createNotice && isHeadquarters && (
        <p role="status" data-testid="create-room-type-notice" className="break-words text-sm text-muted-foreground">
          {createNotice}
        </p>
      )}

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>객실 유형 추가</DialogTitle>
            <DialogDescription>
              이름과 최대 인원을 입력한다. 가격·재고·예약 확정의 권한은 서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={createRoomTypeSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              객실 유형 이름
              <input
                aria-label="객실 유형 이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
                data-testid="create-room-type-name"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              최대 인원
              <input
                aria-label="최대 인원"
                type="number"
                required
                min={1}
                max={20}
                step={1}
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={createOccupancy}
                onChange={(event) => setCreateOccupancy(event.target.value)}
                data-testid="create-room-type-occupancy"
              />
            </label>
            {createError && (
              <p role="alert" className="break-words text-sm text-destructive">{createError}</p>
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)} disabled={creating}>
                취소
              </Button>
              <Button type="submit" disabled={creating || !createName.trim()} data-testid="create-room-type-submit">
                {creating ? "추가하는 중" : "추가"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
