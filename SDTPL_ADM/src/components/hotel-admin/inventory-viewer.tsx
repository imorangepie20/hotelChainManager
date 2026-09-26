"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Boxes, Check, Pencil, RefreshCw, Wallet } from "lucide-react";
import { Ban } from "lucide-react";
import { Download, Upload } from "lucide-react";

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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  adjustRoomTypeRates,
  adjustStaffInventory,
  exportInventoryCsv,
  getRoomTypeRates,
  getSalesStatus,
  getStaffInventory,
  getStaffHotels,
  importInventoryCsv,
  setSalesStatus,
  StaffApiError,
  type InventoryDay,
  type InventoryView,
  type RateAdjustResult,
  type RoomTypeInventory,
  type RoomTypeRateDay,
  type SalesStatusRange,
  type StaffHotelSummary,
  type StaffPrincipal,
} from "@/lib/staff-api";

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
  return {
    from: isoDate(start),
    to: isoDate(new Date(start.getTime() + (days - 1) * 86400000)),
  };
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
  }).format(new Date(`${value}T00:00:00`));
}

function displayWeekday(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { weekday: "short" }).format(
    new Date(`${value}T00:00:00`),
  );
}

// 재고 입력은 서버 검증에 맡기기 전에 숫자만 남긴다.
// 폼에서는 type="text" + inputMode="numeric"을 쓴다.
function parseCapacity(input: string): number | null {
  const trimmed = input.trim().replace(/[^0-9]/g, "");
  if (trimmed === "") return null;
  const parsed = Number(trimmed);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

// 전체 그리드에 표시된 열이 곧 조정 대상 날짜다.
// UI는 요청 범위를 좁히는 입력 없이 보이는 범위를 한 번에 덮는다.
function selectedAdjustDays(roomType: RoomTypeInventory): InventoryDay[] {
  return roomType.days
    .slice()
    .sort((a, b) => a.stayDate.localeCompare(b.stayDate));
}

// 요금 입력은 서버 검증에 맡기기 전에 숫자만 남긴다.
// 폼에서는 type="text" + inputMode="numeric"을 쓴다.
function parseAmount(input: string): number | null {
  const trimmed = input.trim().replace(/[^0-9]/g, "");
  if (trimmed === "") return null;
  const parsed = Number(trimmed);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

function formatKrw(amount: number): string {
  return amount.toLocaleString("ko-KR");
}

export function InventoryViewer() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [inventory, setInventory] = useState<InventoryView | null>(null);
  const [hotels, setHotels] = useState<StaffHotelSummary[]>([]);
  const [hotelId, setHotelId] = useState("");
  const [hotelsLoading, setHotelsLoading] = useState(true);
  const [hotelError, setHotelError] = useState("");
  const [range, setRange] = useState(defaultRange(14));
  const from = range.from;
  const to = range.to;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [adjustTarget, setAdjustTarget] = useState<RoomTypeInventory | null>(
    null,
  );
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [adjustCapacity, setAdjustCapacity] = useState("");
  const [adjusting, setAdjusting] = useState(false);
  const [adjustError, setAdjustError] = useState("");
  const [adjustNotice, setAdjustNotice] = useState("");
  const [adjustKey, setAdjustKey] = useState(0);
  const [rateTarget, setRateTarget] = useState<RoomTypeInventory | null>(null);
  const [rateOpen, setRateOpen] = useState(false);
  const [rateLoading, setRateLoading] = useState(false);
  const [rateDays, setRateDays] = useState<RoomTypeRateDay[]>([]);
  const [rateDraft, setRateDraft] = useState<Record<string, string>>({});
  const [rateAdjusting, setRateAdjusting] = useState(false);
  const [rateError, setRateError] = useState("");
  const [rateNotice, setRateNotice] = useState("");
  const [rateKey, setRateKey] = useState(0);
  const [statusTarget, setStatusTarget] = useState<RoomTypeInventory | null>(
    null,
  );
  const [statusOpen, setStatusOpen] = useState(false);
  const [statusFrom, setStatusFrom] = useState("");
  const [statusTo, setStatusTo] = useState("");
  const [statusBusy, setStatusBusy] = useState(false);
  const [statusError, setStatusError] = useState("");
  const [statusNotice, setStatusNotice] = useState("");
  const [statusKey, setStatusKey] = useState(0);
  const [stopped, setStopped] = useState<Record<string, SalesStatusRange>>({});
  const [importOpen, setImportOpen] = useState(false);
  const [importText, setImportText] = useState("");
  const [importBusy, setImportBusy] = useState(false);
  const [importError, setImportError] = useState("");
  const [importNotice, setImportNotice] = useState("");
  const [importKey, setImportKey] = useState(0);
  const requestGeneration = useRef(0);
  const rateRequestGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const selectionLocked = adjusting || rateAdjusting || statusBusy || importBusy;
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

  const loadHotels = useCallback(async () => {
    if (!sessionToken) return;
    setHotelsLoading(true);
    setHotelError("");
    try {
      const next = await getStaffHotels(sessionToken);
      if (!mounted.current) return;
      setHotels(next);
      setHotelId((current) =>
        next.some((hotel) => hotel.id === current) ? current : (next[0]?.id ?? ""),
      );
      if (next.length === 0) setInventory(null);
    } catch (cause) {
      if (!mounted.current) return;
      setHotels([]);
      setHotelId("");
      setInventory(null);
      setHotelError(
        cause instanceof StaffApiError
          ? cause.message
          : "지점 목록을 불러오지 못했습니다.",
      );
    } finally {
      if (mounted.current) setHotelsLoading(false);
    }
  }, [sessionToken]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void loadHotels();
  }, [isHeadquarters, loadHotels]);

  const loadInventory = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken || !hotelId) return;
    setLoading(true);
    setError("");
    setInventory(null);
    try {
      const next = await getStaffInventory(sessionToken, hotelId, { from, to });
      if (!mounted.current || requestGeneration.current !== generation) return;
      setInventory(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setInventory(null);
        setError(
          cause instanceof StaffApiError
            ? cause.message
            : "재고를 불러오지 못했습니다.",
        );
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation)
        setLoading(false);
    }
  }, [hotelId, from, to, sessionToken]);

  useEffect(() => {
    if (!isHeadquarters || !hotelId) return;
    void loadInventory();
  }, [isHeadquarters, hotelId, loadInventory]);

  function selectHotel(nextHotelId: string) {
    if (nextHotelId === hotelId) return;
    requestGeneration.current += 1;
    rateRequestGeneration.current += 1;
    setLoading(true);
    setHotelId(nextHotelId);
    setInventory(null);
    setError("");
    setAdjustOpen(false);
    setAdjustTarget(null);
    setRateOpen(false);
    setRateTarget(null);
    setStatusOpen(false);
    setStatusTarget(null);
    setImportOpen(false);
    setAdjustNotice("");
    setRateNotice("");
    setStatusNotice("");
    setImportNotice("");
  }

  function openAdjustDialog(roomType: RoomTypeInventory) {
    setAdjustError("");
    setAdjustNotice("");
    setAdjustTarget(roomType);
    setAdjustCapacity("");
    setAdjustOpen(true);
  }

  // 판매 중지 대화상자를 열 때 현재 보이는 범위를 기본값으로 쓴다.
  // 본사가 날짜를 다시 고르지 않게 하되, 중지 구간이 이미 있으면
  // 그 구간을 보여준다.
  function openStatusDialog(roomType: RoomTypeInventory) {
    if (!sessionToken) return;
    setStatusError("");
    setStatusNotice("");
    setStatusTarget(roomType);
    setStatusFrom(from);
    setStatusTo(to);
    setStatusOpen(true);
    void loadSalesStatus(roomType);
  }

  async function loadSalesStatus(roomType: RoomTypeInventory) {
    if (!sessionToken) return;
    try {
      const result = await getSalesStatus(
        sessionToken,
        hotelId,
        roomType.roomTypeId,
      );
      if (!mounted.current) return;
      setStopped((prev) => ({ ...prev, [roomType.roomTypeId]: result }));
    } catch (cause) {
      // 중지 구간을 못 읽어도 대화상자는 쓸 수 있다. 안내만 남긴다.
      if (mounted.current) {
        setStatusError(
          cause instanceof StaffApiError
            ? cause.message
            : "판매 중지 구간을 불러오지 못했습니다.",
        );
      }
    }
  }

  async function statusSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !statusTarget) return;
    if (!statusFrom || !statusTo) {
      setStatusError("시작일과 종료일을 모두 선택해 주세요.");
      return;
    }
    if (statusTo < statusFrom) {
      setStatusError("종료일은 시작일과 같거나 이후여야 합니다.");
      return;
    }
    setStatusBusy(true);
    setStatusError("");
    try {
      // 중지·재개는 매번 새 멱원 키를 쓴다. 재시도가 같은 키를
      // 재사용하지 않게 해서 네트워크 장애 뒤 다시 눌러도
      // 중복 전환이 생기지 않는다.
      const result = await setSalesStatus(
        sessionToken,
        hotelId,
        statusTarget.roomTypeId,
        `set-sales-status-${statusKey}`,
        { fromDate: statusFrom, toDate: statusTo, status: "STOPPED" },
      );
      setStatusKey((key) => key + 1);
      await loadSalesStatus(statusTarget);
      await loadInventory();
      setStatusOpen(false);
      setStatusNotice(
        `${statusTarget.name}의 ${statusFrom} ~ ${statusTo} 판매를 중지했습니다. 총량은 그대로 두고 신규 판매만 막았습니다. 현재 중지된 일자는 ${result.stoppedDays}일입니다.`,
      );
    } catch (cause) {
      // 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
      setStatusError(
        cause instanceof StaffApiError
          ? cause.message
          : "판매 중지를 설정하지 못했습니다.",
      );
    } finally {
      if (mounted.current) setStatusBusy(false);
    }
  }

  // 내보낸 CSV를 파일로 내려준다. UTF-8 BOM을 붙여 엑셀이
  // 한글을 깨지지 않게 읽는다 (감사 이력·운영 통계와 같은 패턴).
  async function downloadCsv() {
    if (!sessionToken) return;
    setImportError("");
    try {
      const csv = await exportInventoryCsv(sessionToken, hotelId, { from, to });
      if (!mounted.current) return;
      const blob = new Blob(["﻿" + csv], {
        type: "text/csv;charset=utf-8",
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `inventory-${hotelId}-${from}-${to}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
      setImportNotice(
        `${from} ~ ${to} 재고를 CSV로 내려받았습니다. 총량 열을 수정해서 다시 올릴 수 있습니다.`,
      );
    } catch (cause) {
      setImportError(
        cause instanceof StaffApiError
          ? cause.message
          : "재고 CSV를 내려받지 못했습니다.",
      );
    }
  }

  // 파일을 고르면 본문을 읽어 미리 보여준다. 올리기 전에 본사가
  // 몇 행인지 확인하게 한다.
  function pickImportFile(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setImportError("");
    const reader = new FileReader();
    reader.onload = () => {
      if (!mounted.current) return;
      setImportText(String(reader.result ?? ""));
    };
    reader.readAsText(file, "utf-8");
  }

  function openImportDialog() {
    setImportError("");
    setImportNotice("");
    setImportText("");
    setImportOpen(true);
  }

  // 내려받은 파일에서 몇 개의 의미 있는 행을 올릴 수 있는지 센다.
  // 헤더·빈 행은 건너뛴다. 본사가 올리기 전에 규모를 확인하게 한다.
  function countImportRows(value: string): number {
    // 내보낸 파일은 UTF-8 BOM으로 시작한다. 올리기 전 화면에서만
    // BOM을 떼고 센다. 서버도 올려진 본문에서 BOM을 뗀다.
    const body = value.startsWith("\ufeff") ? value.slice(1) : value;
    return body
      .split(/\r\n|\r|\n/)
      .map((line) => line.trim())
      .filter((line, index) => {
        if (line === "") return false;
        // 첫 행이 헤더면 올리지 않는다.
        if (index === 0 && line.startsWith("객실 유형 ID")) return false;
        return true;
      })
      .length;
  }

  function closeImportDialog() {
    setImportOpen(false);
    setImportText("");
    setImportError("");
  }

  async function importSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    if (!importText.trim()) {
      setImportError("파일을 먼저 선택해 주세요.");
      return;
    }
    setImportBusy(true);
    setImportError("");
    try {
      // 업로드는 매번 새 멱원 키를 쓴다. 재시도가 같은 키를
      // 재사용하지 않게 해서 네트워크 장애 뒤 다시 눌러도
      // 재고가 두 번 바뀌지 않는다.
      const result = await importInventoryCsv(
        sessionToken,
        hotelId,
        `import-inventory-${importKey}`,
        importText,
      );
      setImportKey((key) => key + 1);
      closeImportDialog();
      await loadInventory();
      setImportNotice(
        `${result.totalRows}행을 올렸습니다. 그중 ${result.appliedRows}행의 재고를 바꿨습니다. (건너뛴 행 ${result.skippedRows}개)`,
      );
    } catch (cause) {
      // 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
      setImportError(
        cause instanceof StaffApiError
          ? cause.message
          : "재고를 올리지 못했습니다.",
      );
    } finally {
      if (mounted.current) setImportBusy(false);
    }
  }

  async function resumeSales(roomType: RoomTypeInventory) {    if (!sessionToken) return;
    const range = stopped[roomType.roomTypeId];
    if (!range || range.stoppedRanges.length === 0) return;
    const earliest = range.stoppedRanges.reduce((min, current) =>
      current.fromDate < min.fromDate ? current : min,
    );
    setStatusBusy(true);
    setStatusError("");
    setStatusNotice("");
    try {
      // 재개도 매번 새 멱원 키를 쓴다. 중지와 같은 양식이다.
      const result = await setSalesStatus(
        sessionToken,
        hotelId,
        roomType.roomTypeId,
        `resume-sales-${statusKey}`,
        { fromDate: earliest.fromDate, toDate: earliest.toDate, status: "OPEN" },
      );
      setStatusKey((key) => key + 1);
      // 대화상자를 먼저 닫아야 loadInventory가 닫힌 상태의
      // 콜백 인스턴스로 새로 만들어진다. 순서가 바뀌면
      // loadInventory가 닫히기 전의 인스턴스를 재사용해서
      // requestGeneration 검사에 걸려 결과를 버린다.
      setStatusOpen(false);
      setStatusTarget(null);
      await loadSalesStatus(roomType);
      await loadInventory();
      setStatusNotice(
        `${roomType.name}의 ${earliest.fromDate} ~ ${earliest.toDate} 판매를 재개했습니다. 중지 전의 재고가 그대로 돌아옵니다. 남은 중지 일자는 ${result.stoppedDays}일입니다.`,
      );
    } catch (cause) {
      setStatusError(
        cause instanceof StaffApiError
          ? cause.message
          : "판매 재개를 하지 못했습니다.",
      );
    } finally {
      if (mounted.current) setStatusBusy(false);
    }
  }

  // 요금 대화상자를 열 때 서버에서 현재 일자별 요금을 가져온다.
  // 그래야 본사가 빈 칸에 새 금액을 쓰는 것이 아니라 현재값을 덮어쓴다.
  async function openRateDialog(roomType: RoomTypeInventory) {
    if (!sessionToken) return;
    const generation = ++rateRequestGeneration.current;
    const requestedHotelId = hotelId;
    setRateError("");
    setRateNotice("");
    setRateTarget(roomType);
    setRateDays([]);
    setRateDraft({});
    setRateOpen(true);
    setRateLoading(true);
    try {
      const rates = await getRoomTypeRates(
        sessionToken,
        requestedHotelId,
        roomType.roomTypeId,
        { from, to },
      );
      if (
        !mounted.current ||
        rateRequestGeneration.current !== generation
      )
        return;
      setRateDays(rates.days);
      setRateDraft(
        Object.fromEntries(
          rates.days.map((day) => [day.stayDate, String(day.amountKrw)]),
        ),
      );
    } catch (cause) {
      if (
        mounted.current &&
        rateRequestGeneration.current === generation
      ) {
        setRateError(
          cause instanceof StaffApiError
            ? cause.message
            : "일자별 요금을 불러오지 못했습니다.",
        );
      }
    } finally {
      if (
        mounted.current &&
        rateRequestGeneration.current === generation
      )
        setRateLoading(false);
    }
  }

  function closeRateDialog() {
    rateRequestGeneration.current += 1;
    setRateOpen(false);
    setRateTarget(null);
    setRateError("");
  }

  // 바뀐 날짜만 서버에 보낸다. 바뀌지 않은 날짜까지 보내면 멱원 지문이
  // 의미 없이 커지고, 서버가 404로 거부할 수 있는 날짜가 늘어난다.
  function collectRateAdjustments(): Array<{
    stayDate: string;
    amountKrw: number;
  }> {
    return rateDays
      .filter(
        (day) => parseAmount(rateDraft[day.stayDate] ?? "") !== day.amountKrw,
      )
      .map((day) => ({
        stayDate: day.stayDate,
        amountKrw: parseAmount(rateDraft[day.stayDate] ?? "") as number,
      }));
  }

  async function adjustRatesSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !rateTarget) return;
    const adjustments = collectRateAdjustments();
    if (adjustments.length === 0) {
      setRateError("바꿀 금액을 하나 이상 입력해 주세요.");
      return;
    }
    const invalid = adjustments.find(
      (entry) => entry.amountKrw < 0 || entry.amountKrw > 10_000_000,
    );
    if (invalid) {
      setRateError("금액은 0원 이상 10,000,000원 이하여야 합니다.");
      return;
    }
    setRateAdjusting(true);
    setRateError("");
    try {
      const result: RateAdjustResult = await adjustRoomTypeRates(
        sessionToken,
        hotelId,
        rateTarget.roomTypeId,
        `adjust-rates-${rateKey}`,
        { roomTypeId: rateTarget.roomTypeId, adjustments },
      );
      setRateKey((key) => key + 1);
      closeRateDialog();
      const total = result.days.length;
      const min = Math.min(...result.days.map((day) => day.amountKrw));
      const max = Math.max(...result.days.map((day) => day.amountKrw));
      setRateNotice(
        `${rateTarget.name}의 ${total}일 요금을 바꿨습니다. ` +
          (min === max
            ? `모두 ${formatKrw(min)}원입니다.`
            : `${formatKrw(min)}원 ~ ${formatKrw(max)}원입니다.`),
      );
    } catch (cause) {
      setRateError(
        cause instanceof StaffApiError
          ? cause.message
          : "가격을 변경하지 못했습니다.",
      );
    } finally {
      if (mounted.current) setRateAdjusting(false);
    }
  }

  async function adjustInventorySubmit(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();
    if (!sessionToken || !adjustTarget) return;
    const capacity = parseCapacity(adjustCapacity);
    if (capacity === null) {
      setAdjustError("총량을 숫자로 입력해 주세요.");
      return;
    }
    if (capacity < 0 || capacity > 1_000) {
      setAdjustError("총량은 0 이상 1,000 이하여야 합니다.");
      return;
    }
    // 보조 쿼리가 행을 반환하지 않아 SQL이 완성되지 않는 일을 막는다.
    const selectedDays = selectedAdjustDays(adjustTarget);
    if (selectedDays.length === 0) {
      setAdjustError("조정할 날짜가 없습니다. 범위를 다시 선택해 주세요.");
      return;
    }
    setAdjusting(true);
    setAdjustError("");
    try {
      const result = await adjustStaffInventory(
        sessionToken,
        hotelId,
        `adjust-inventory-${adjustKey}`,
        {
          roomTypeId: adjustTarget.roomTypeId,
          adjustments: selectedDays.map((day) => ({
            stayDate: day.stayDate,
            capacity,
          })),
        },
      );
      setAdjustKey((key) => key + 1);
      setAdjustOpen(false);
      setAdjustTarget(null);
      await loadInventory();
      const total = result.days.length;
      const blocked = result.days.filter((day) => day.remaining === 0).length;
      setAdjustNotice(
        `${adjustTarget.name}의 선택한 ${total}일의 총량을 ${capacity.toLocaleString("ko-KR")}실로 바꿨습니다.` +
          (blocked > 0 ? ` 그중 ${blocked}일이 매진입니다.` : ""),
      );
    } catch (cause) {
      setAdjustError(
        cause instanceof StaffApiError
          ? cause.message
          : "재고를 조정하지 못했습니다.",
      );
    } finally {
      setAdjusting(false);
    }
  }

  const selectedHotel = hotels.find((hotel) => hotel.id === hotelId) ?? null;
  const selectedInventory = inventory?.hotelId === hotelId ? inventory : null;
  const roomTypes = selectedInventory?.roomTypes ?? [];
  // 모든 객실 유형의 숙박일을 합쳐 가로축을 만든다. 유형마다 빠진 날짜가 있을 수 있다.
  const columns = Array.from(
    new Set(
      roomTypes.flatMap((roomType) => roomType.days.map((day) => day.stayDate)),
    ),
  ).sort();
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
            객실 유형(행)과 숙박일(열)로 잔여 재고를 한 화면에 비교한다. 총량은
            본사 관리자가 조정하고, 범위 판단은 서버가 최종 결정한다.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => void loadInventory()}
          disabled={loading || !isHeadquarters || !hotelId}
        >
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {error && (
        <p role="alert" className="break-words text-sm text-destructive">
          {error}
        </p>
      )}

      {adjustNotice && isHeadquarters && (
        <p
          role="status"
          data-testid="adjust-inventory-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {adjustNotice}
        </p>
      )}

      {rateNotice && isHeadquarters && (
        <p
          role="status"
          data-testid="adjust-rates-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {rateNotice}
        </p>
      )}

      {statusNotice && isHeadquarters && (
        <p
          role="status"
          data-testid="sales-status-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {statusNotice}
        </p>
      )}

      {importNotice && isHeadquarters && (
        <p
          role="status"
          data-testid="import-inventory-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {importNotice}
        </p>
      )}

      {importError && isHeadquarters && (
        <p
          role="alert"
          data-testid="import-inventory-error"
          className="break-words text-sm text-destructive"
        >
          {importError}
        </p>
      )}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          재고·가격 관리는 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && hotelError && (
        <p role="alert" className="break-words text-sm text-destructive">
          {hotelError}
        </p>
      )}

      {isHeadquarters && hotelsLoading && (
        <p role="status" className="text-sm text-muted-foreground">
          지점 목록을 불러오는 중입니다.
        </p>
      )}

      {isHeadquarters && hotels.length > 0 && (
        <div
          className="flex flex-wrap items-center gap-2"
          role="group"
          aria-label="지점 선택"
        >
          {hotels.map((hotel) => (
            <div key={hotel.id} className="flex items-center gap-1.5">
              <Button
                type="button"
                variant={hotel.id === hotelId ? "default" : "outline"}
                aria-pressed={hotel.id === hotelId}
                disabled={selectionLocked}
                onClick={() => selectHotel(hotel.id)}
              >
                {hotel.id === hotelId ? (
                  <Check className="h-4 w-4" aria-hidden />
                ) : null}
                {hotel.name}
              </Button>
              {hotel.id === hotelId ? (
                <span className="text-xs font-medium text-foreground">선택됨</span>
              ) : null}
            </div>
          ))}
        </div>
      )}

      {isHeadquarters && !hotelsLoading && hotels.length === 0 && !hotelError && (
        <p className="text-sm text-muted-foreground">
          등록된 지점이 없습니다. 호텔 및 객실 화면에서 지점을 먼저 추가해 주세요.
        </p>
      )}

      {isHeadquarters && roomTypes.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void downloadCsv()}
            disabled={loading}
            data-testid="export-inventory-csv"
          >
            <Download className="h-4 w-4" aria-hidden />
            CSV 내보내기
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => openImportDialog()}
            data-testid="import-inventory-csv"
          >
            <Upload className="h-4 w-4" aria-hidden />
            CSV 업로드
          </Button>
        </div>
      )}

      {isHeadquarters && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1">
            <Label htmlFor="inventory-from">시작일</Label>            <Input
              id="inventory-from"
              type="date"
              value={from}
              onChange={(event) =>
                setRange((prev) => ({ ...prev, from: event.target.value }))
              }
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="inventory-to">종료일</Label>
            <Input
              id="inventory-to"
              type="date"
              value={to}
              onChange={(event) =>
                setRange((prev) => ({ ...prev, to: event.target.value }))
              }
            />
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
            <CardTitle className="text-base">
              {selectedHotel?.name ?? "선택한 지점"} · 객실 유형 {roomTypes.length}종 · 숙박일 {totalDays}일
            </CardTitle>
            <CardDescription>
              셀의 숫자는 판매 가능 잔여 수다. 매진 셀은 빨간색으로 표시한다.
              총량 조정은 표시된 전체 숙박일을 한 번에 덮는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th
                    scope="col"
                    className="sticky left-0 z-10 min-w-[8.5rem] border-b border-r bg-card p-2 text-left font-medium"
                  >
                    객실 유형
                  </th>
                  {columns.map((stayDate) => {
                    const isWeekend = [0, 6].includes(
                      new Date(`${stayDate}T00:00:00`).getDay(),
                    );
                    return (
                      <th
                        key={stayDate}
                        scope="col"
                        className={`border-b p-2 text-center font-medium ${isWeekend ? "bg-muted/50 text-muted-foreground" : ""}`}
                      >
                        <span className="block tabular-nums">
                          {displayDate(stayDate)}
                        </span>
                        <span className="block text-xs font-normal text-muted-foreground">
                          {displayWeekday(stayDate)}
                        </span>
                      </th>
                    );
                  })}
                  {isHeadquarters ? (
                    <>
                      <th
                        scope="col"
                        className="border-b border-l p-2 text-center font-medium"
                      >
                        요금 조정
                      </th>
                      <th
                        scope="col"
                        className="border-b border-l p-2 text-center font-medium"
                      >
                        총량 조정
                      </th>
                      <th
                        scope="col"
                        className="border-b border-l p-2 text-center font-medium"
                      >
                        판매 중지
                      </th>
                    </>
                  ) : null}
                </tr>
              </thead>
              <tbody>
                {roomTypes.map((roomType) => (
                  <tr key={roomType.roomTypeId} data-room-type-id={roomType.roomTypeId}>
                    <th
                      scope="row"
                      className="sticky left-0 z-10 min-w-[8.5rem] border-b border-r bg-card p-2 text-left font-medium"
                    >
                      {roomType.name}
                      <span className="block text-xs font-normal text-muted-foreground">
                        {roomType.maxOccupancy}명까지
                      </span>
                    </th>
                    {columns.map((stayDate) => {
                      const day = roomType.days.find(
                        (candidate) => candidate.stayDate === stayDate,
                      );
                      if (!day) {
                        return (
                          <td
                            key={stayDate}
                            className="border-b p-2 text-center text-muted-foreground"
                          >
                            -
                          </td>
                        );
                      }
                      const soldOut = day.remaining === 0;
                      const isStopped = day.salesStatus === "STOPPED";
                      return (
                        <td
                          key={stayDate}
                          className={`border-b p-2 text-center tabular-nums ${isStopped ? "bg-amber-500/10 font-medium text-amber-700 dark:text-amber-400" : soldOut ? "bg-destructive/10 font-medium text-destructive" : ""}`}
                        >
                          {isStopped ? "중지" : soldOut ? "매진" : day.remaining}
                        </td>
                      );
                    })}
                    {isHeadquarters ? (
                      <>
                        <td className="border-b border-l p-2 text-center align-middle">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => void openRateDialog(roomType)}
                            data-testid={`adjust-rates-${roomType.roomTypeId}`}
                            aria-label={`${roomType.name} 요금 조정`}
                          >
                            <Wallet className="h-4 w-4" aria-hidden />
                            요금
                          </Button>
                        </td>
                        <td className="border-b border-l p-2 text-center align-middle">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => openAdjustDialog(roomType)}
                            data-testid={`adjust-inventory-${roomType.roomTypeId}`}
                            aria-label={`${roomType.name} 총량 조정`}
                          >
                            <Pencil className="h-4 w-4" aria-hidden />
                            {roomType.name}
                          </Button>
                        </td>
                        <td className="border-b border-l p-2 text-center align-middle">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => openStatusDialog(roomType)}
                            data-testid={`sales-status-${roomType.roomTypeId}`}
                            aria-label={`${roomType.name} 판매 중지`}
                          >
                            <Ban className="h-4 w-4" aria-hidden />
                            중지
                          </Button>
                        </td>
                      </>
                    ) : null}
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
          <span className="text-amber-700 dark:text-amber-400">중지 = 본사가 판매 중지</span>
          <span>- = 해당 일자 재고 없음</span>
        </div>
      )}

      {isHeadquarters && hotelId && loading && !error && (
        <p role="status" className="text-sm text-muted-foreground">
          {selectedHotel?.name ?? "선택한 지점"} 재고를 불러오는 중입니다.
        </p>
      )}

      {isHeadquarters && selectedInventory && !loading && totalDays === 0 && !error && (
        <p className="text-sm text-muted-foreground">
          선택한 기간에 등록된 재고가 없습니다.
        </p>
      )}

      <Dialog open={adjustOpen} onOpenChange={setAdjustOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>일자 재고 총량 조정</DialogTitle>
          </DialogHeader>
          <DialogDescription>
            {adjustTarget
              ? `${adjustTarget.name}의 표시된 숙박일 ${selectedAdjustDays(adjustTarget).length}일의 총량을 같은 값으로 바꾼다. 잔여가 0이 되면 매진으로 표시된다.`
              : "객실 유형을 선택해 주세요."}
            총량은 0 이상 1,000 이하여야 하고, 확정·보류 중인 예약보다 작은 값은
            서버가 거부한다. 가격·재고·예약 확정의 권한은 서버에 있다.
          </DialogDescription>
          <form onSubmit={adjustInventorySubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              새 총량 (실)
              <input
                aria-label="새 총량"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={adjustCapacity}
                onChange={(event) => setAdjustCapacity(event.target.value)}
                data-testid="adjust-inventory-capacity"
              />
              <span className="text-xs font-normal text-muted-foreground">
                0은 판매 중지와 같다. 확정·보류 중인 예약이 새 총량을 초과하면
                서버가 409로 거부한다.
              </span>
            </label>
            {adjustError ? (
              <p role="alert" className="break-words text-sm text-destructive">
                {adjustError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setAdjustOpen(false)}
                disabled={adjusting}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={adjusting || parseCapacity(adjustCapacity) === null}
                data-testid="adjust-inventory-submit"
              >
                {adjusting ? "조정하는 중" : "조정"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={rateOpen}
        onOpenChange={(open) => {
          if (!open) closeRateDialog();
        }}
      >
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>일자별 요금 조정</DialogTitle>
          </DialogHeader>
          <DialogDescription>
            {rateTarget
              ? `${rateTarget.name}의 표시된 숙박일 ${rateDays.length}일 요금을 날짜별로 바꾼다. 바꾼 날짜만 서버에 보낸다.`
              : "객실 유형을 선택해 주세요."}
            금액은 0원 이상 10,000,000원 이하여야 하고, 심어둔 기간 밖의 날짜는
            서버가 거부한다. 가격·재고·예약 확정의 권한은 서버에 있다.
          </DialogDescription>
          {rateLoading && (
            <p role="status" className="text-sm text-muted-foreground">
              현재 요금을 불러오는 중입니다.
            </p>
          )}
          {!rateLoading && rateDays.length === 0 && !rateError && (
            <p className="text-sm text-muted-foreground">
              선택한 기간에 등록된 요금이 없습니다.
            </p>
          )}
          {rateDays.length > 0 && (
            <form onSubmit={adjustRatesSubmit} className="grid gap-3">
              <div className="grid max-h-[45vh] gap-2 overflow-y-auto pr-1">
                {rateDays.map((day) => (
                  <label
                    key={day.stayDate}
                    className="grid grid-cols-[7.5rem_1fr] items-center gap-3 text-sm"
                  >
                    <span className="tabular-nums text-muted-foreground">
                      {displayDate(day.stayDate)} (
                      {displayWeekday(day.stayDate)})
                    </span>
                    <input
                      aria-label={`${displayDate(day.stayDate)} 요금`}
                      type="text"
                      inputMode="numeric"
                      autoComplete="off"
                      required
                      className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                      value={rateDraft[day.stayDate] ?? ""}
                      onChange={(event) =>
                        setRateDraft((prev) => ({
                          ...prev,
                          [day.stayDate]: event.target.value,
                        }))
                      }
                      data-testid={`rate-day-${day.stayDate}`}
                    />
                  </label>
                ))}
              </div>
              {rateError ? (
                <p
                  role="alert"
                  className="break-words text-sm text-destructive"
                  data-testid="rate-error"
                >
                  {rateError}
                </p>
              ) : null}
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  onClick={closeRateDialog}
                  disabled={rateAdjusting}
                >
                  취소
                </Button>
                <Button
                  type="submit"
                  disabled={
                    rateAdjusting ||
                    rateLoading ||
                    collectRateAdjustments().length === 0
                  }
                  data-testid="adjust-rates-submit"
                >
                  {rateAdjusting
                    ? "변경하는 중"
                    : `${collectRateAdjustments().length}일 변경`}
                </Button>
              </DialogFooter>
            </form>
          )}
          {rateDays.length === 0 && rateError ? (
            <p
              role="alert"
              className="break-words text-sm text-destructive"
              data-testid="rate-error"
            >
              {rateError}
            </p>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={statusOpen} onOpenChange={setStatusOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>판매 중지 구간 설정</DialogTitle>
            <DialogDescription>
              {statusTarget
                ? `${statusTarget.name}의 선택한 구간의 신규 판매를 멈춘다.`
                : "객실 유형을 선택해 주세요."}
              총량은 그대로 두고 신규 판매만 막으므로, 재개하면 중지 전과
              같은 재고가 돌아온다. 이미 확정·보류된 예약은 그대로 둔다.
              취소는 전용 API가 있다. 가격·재고·예약 확정의 권한은
              서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={statusSubmit} className="grid gap-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="grid gap-1 text-sm font-medium">
                시작일
                <Input
                  type="date"
                  required
                  value={statusFrom}
                  onChange={(event) => setStatusFrom(event.target.value)}
                  data-testid="sales-status-from"
                />
              </label>
              <label className="grid gap-1 text-sm font-medium">
                종료일
                <Input
                  type="date"
                  required
                  value={statusTo}
                  onChange={(event) => setStatusTo(event.target.value)}
                  data-testid="sales-status-to"
                />
              </label>
            </div>
            {statusTarget && stopped[statusTarget.roomTypeId]?.stoppedRanges.length ? (
              <div className="grid gap-2 text-sm">
                <span className="font-medium">현재 중지 구간</span>
                <ul
                  className="grid gap-1 text-muted-foreground"
                  data-testid={`sales-status-stopped-${statusTarget.roomTypeId}`}
                >
                  {stopped[statusTarget.roomTypeId].stoppedRanges.map((range) => (
                    <li key={`${range.fromDate}-${range.toDate}`}>
                      {range.fromDate} ~ {range.toDate}
                    </li>
                  ))}
                </ul>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="justify-self-start"
                  onClick={() => void resumeSales(statusTarget)}
                  disabled={statusBusy}
                  data-testid={`sales-status-resume-${statusTarget.roomTypeId}`}
                >
                  가장 이른 중지 구간 판매 재개
                </Button>
              </div>
            ) : null}
            {statusError ? (
              <p role="alert" className="break-words text-sm text-destructive">
                {statusError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setStatusOpen(false)}
                disabled={statusBusy}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={statusBusy || !statusFrom || !statusTo}
                data-testid="sales-status-submit"
              >
                {statusBusy ? "설정하는 중" : "판매 중지"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={importOpen} onOpenChange={(open) => {
        if (!open) closeImportDialog();
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>재고 일괄 업로드 (CSV)</DialogTitle>
            <DialogDescription>
              내보낸 파일의 {`총량`} 열만 반영한다. {`보류`}·{`확정`}·{`잔여`}·
              {`판매 상태`}는 읽기 전용이라 올려도 무시한다. 한 파일의 모든 행을
              한 트랜잭션에 처리해서 전부 성공하거나 전부 실패한다. 총량은
              0 이상 1,000 이하여야 하고, 확정·보류 중인 예약보다 작은 값이
              있으면 서버가 전체를 거부한다. 가격·재고·예약 확정의 권한은
              서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={importSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              CSV 파일
              <input
                aria-label="CSV 파일"
                type="file"
                accept=".csv,text/csv"
                required
                className="text-sm file:mr-3 file:rounded-lg file:border file:bg-background file:px-3 file:py-1.5 file:text-sm"
                onChange={(event) => pickImportFile(event)}
                data-testid="import-inventory-file"
              />
            </label>
            {importText ? (
              <div className="grid gap-1 text-sm text-muted-foreground">
                <span data-testid="import-inventory-preview-count">
                  {countImportRows(importText)}행의 재고를 올리게 됩니다.
                </span>
                <pre
                  className="max-h-[32vh] overflow-auto rounded-lg border bg-muted/30 p-2 text-xs"
                  data-testid="import-inventory-preview"
                >
                  {importText}
                </pre>
              </div>
            ) : null}
            {importError ? (
              <p
                role="alert"
                className="break-words text-sm text-destructive"
                data-testid="import-inventory-form-error"
              >
                {importError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => closeImportDialog()}
                disabled={importBusy}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={importBusy || !importText.trim()}
                data-testid="import-inventory-submit"
              >
                {importBusy ? "올리는 중" : "올리기"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
