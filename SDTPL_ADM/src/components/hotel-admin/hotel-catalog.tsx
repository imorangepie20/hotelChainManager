"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Hotel, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";

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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  createHotel,
  createRoomType,
  createRatePlan,
  renameRatePlan,
  deleteRoomType,
  getRoomTypeCatalog,
  getRoomTypeDefaults,
  getStaffHotels,
  parseRateKrw,
  setHotelActive,
  updateHotel,
  updateRoomType,
  StaffApiError,
  type RoomTypeCatalogEntry,
  type RoomTypeCatalogView,
  type RatePlanSummary,
  type StaffHotelSummary,
  type StaffPrincipal,
} from "@/lib/staff-api";

const DEFAULT_RATE_KRW = "100000";
const DEFAULT_TIMEZONE = "Asia/Seoul";

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
  const [hotels, setHotels] = useState<StaffHotelSummary[]>([]);
  const [hotelId, setHotelId] = useState("");
  const [hotelsLoading, setHotelsLoading] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [hotelError, setHotelError] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createOccupancy, setCreateOccupancy] = useState("2");
  const [createBreakfast, setCreateBreakfast] = useState(false);
  const [createRate, setCreateRate] = useState(DEFAULT_RATE_KRW);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createNotice, setCreateNotice] = useState("");
  const [createKey, setCreateKey] = useState(0);
  const [hotelCreateOpen, setHotelCreateOpen] = useState(false);
  const [hotelName, setHotelName] = useState("");
  const [hotelRegion, setHotelRegion] = useState("");
  const [hotelTimezone, setHotelTimezone] = useState(DEFAULT_TIMEZONE);
  const [hotelCreating, setHotelCreating] = useState(false);
  const [hotelCreateError, setHotelCreateError] = useState("");
  const [hotelCreateKey, setHotelCreateKey] = useState(0);
  const [hotelEditTarget, setHotelEditTarget] = useState<StaffHotelSummary | null>(null);
  const [hotelEditOpen, setHotelEditOpen] = useState(false);
  const [hotelEditName, setHotelEditName] = useState("");
  const [hotelEditRegion, setHotelEditRegion] = useState("");
  const [hotelEditTimezone, setHotelEditTimezone] = useState(DEFAULT_TIMEZONE);
  const [hotelEditing, setHotelEditing] = useState(false);
  const [hotelEditError, setHotelEditError] = useState("");
  const [hotelEditKey, setHotelEditKey] = useState(0);
  const [activationKey, setActivationKey] = useState(0);
  const [activationError, setActivationError] = useState("");
  const [editTarget, setEditTarget] = useState<RoomTypeCatalogEntry | null>(
    null,
  );
  const [editOpen, setEditOpen] = useState(false);
  const [editName, setEditName] = useState("");
  const [editOccupancy, setEditOccupancy] = useState("2");
  const [editBreakfast, setEditBreakfast] = useState(false);
  const [editRate, setEditRate] = useState("");
  const [editing, setEditing] = useState(false);
  const [editError, setEditError] = useState("");
  const [editKey, setEditKey] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<RoomTypeCatalogEntry | null>(
    null,
  );
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [deleteKey, setDeleteKey] = useState(0);
  const [planCreateTarget, setPlanCreateTarget] =
    useState<RoomTypeCatalogEntry | null>(null);
  const [planCreateOpen, setPlanCreateOpen] = useState(false);
  const [planName, setPlanName] = useState("");
  const [planBreakfast, setPlanBreakfast] = useState(false);
  const [planPolicy, setPlanPolicy] = useState("FLEX-2026-01");
  const [planRate, setPlanRate] = useState(DEFAULT_RATE_KRW);
  const [planDays, setPlanDays] = useState("90");
  const [planCreating, setPlanCreating] = useState(false);
  const [planCreateError, setPlanCreateError] = useState("");
  const [planCreateKey, setPlanCreateKey] = useState(0);
  const [planRenameTarget, setPlanRenameTarget] =
    useState<{ roomType: RoomTypeCatalogEntry; plan: RatePlanSummary } | null>(
      null,
    );
  const [planRenameOpen, setPlanRenameOpen] = useState(false);
  const [planRename, setPlanRename] = useState("");
  const [planRenaming, setPlanRenaming] = useState(false);
  const [planRenameError, setPlanRenameError] = useState("");
  const [planRenameKey, setPlanRenameKey] = useState(0);
  const requestGeneration = useRef(0);
  const editDefaultsGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const selectionLocked =
    creating || editing || deleting || planCreating || planRenaming;
  const selectedHotel = hotels.find((hotel) => hotel.id === hotelId) ?? null;
  const selectedCatalog = catalog?.hotelId === hotelId ? catalog : null;
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
    setCatalog(null);
    try {
      const next = await getRoomTypeCatalog(sessionToken, hotelId);
      if (!mounted.current || requestGeneration.current !== generation) return;
      setCatalog(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setCatalog(null);
        setError(
          cause instanceof StaffApiError
            ? cause.message
            : "객실 유형 목록을 불러오지 못했습니다.",
        );
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation)
        setLoading(false);
    }
  }, [hotelId, sessionToken]);

  // 지점 목록은 서버에서 읽는다. 새 지점을 만들면 선택기에 바로 나타난다.
  const refreshHotels = useCallback(async () => {
    if (!sessionToken) return;
    setHotelsLoading(true);
    try {
      const next = await getStaffHotels(sessionToken);
      if (!mounted.current) return;
      setHotels(next);
      setHotelError("");
      // 선택한 지점이 목록에서 사라졌거나 아직 선택이 없으면 첫 지점을 고른다.
      setHotelId((current) =>
        next.some((hotel) => hotel.id === current) ? current : (next[0]?.id ?? ""),
      );
      if (next.length === 0) setCatalog(null);
    } catch (cause) {
      if (!mounted.current) return;
      setHotels([]);
      setHotelId("");
      setCatalog(null);
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
    void refreshHotels();
  }, [isHeadquarters, refreshHotels]);

  useEffect(() => {
    // 지점 목록이 도착해서 선택할 지점이 있어야 객실 유형을 불러올 수 있다.
    if (!isHeadquarters || !hotelId) return;
    void refresh();
  }, [isHeadquarters, hotelId, refresh]);

  function selectHotel(nextHotelId: string) {
    if (nextHotelId === hotelId) return;
    requestGeneration.current += 1;
    editDefaultsGeneration.current += 1;
    setLoading(true);
    setHotelId(nextHotelId);
    setCatalog(null);
    setError("");
  }

  async function createRoomTypeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const name = createName.trim();
    const occupancy = Number(createOccupancy);
    const rate = parseRateKrw(createRate);
    if (!name) {
      setCreateError("객실 유형 이름을 입력해 주세요.");
      return;
    }
    if (!Number.isInteger(occupancy) || occupancy < 1 || occupancy > 20) {
      setCreateError("최대 인원은 1 이상 20 이하여야 합니다.");
      return;
    }
    if (rate === null) {
      setCreateError("기본 요금을 숫자로 입력해 주세요.");
      return;
    }
    if (rate < 0 || rate > 10_000_000) {
      setCreateError("기본 요금은 0원 이상 10,000,000원 이하여야 합니다.");
      return;
    }
    setCreating(true);
    setCreateError("");
    try {
      const result = await createRoomType(
        sessionToken,
        hotelId,
        `create-room-type-${createKey}`,
        {
          name,
          maxOccupancy: occupancy,
          breakfastIncluded: createBreakfast,
          defaultRateKrw: rate,
        },
      );
      setCreateOpen(false);
      setCreateName("");
      setCreateOccupancy("2");
      setCreateBreakfast(false);
      setCreateRate(DEFAULT_RATE_KRW);
      setCreateKey((key) => key + 1);
      await refresh();
      // 시드가 끝나야 고객 검색에 나타난다. 재호출(created=false)이면 안내만 넘긴다.
      const seed = result.seed;
      if (seed && seed.created) {
        setCreateNotice(
          `${name}에 기본 요금제와 ${seed.pricedDays}일분 일자 재고를 심었습니다. ` +
            `기본 요금 ${seed.defaultRateKrw.toLocaleString("ko-KR")}원, 조식 ${seed.breakfastIncluded ? "포함" : "객실만"}, ` +
            `객실 ${seed.inventoryCapacity}실입니다. ` +
            `일자별 요금·재고는 이 화면의 다음 단계에서 조정합니다.`,
        );
      }
    } catch (cause) {
      setCreateError(
        cause instanceof StaffApiError
          ? cause.message
          : "객실 유형을 추가하지 못했습니다.",
      );
    } finally {
      setCreating(false);
    }
  }

  function openCreateDialog() {
    setCreateError("");
    setCreateNotice("");
    setCreateOpen(true);
  }

  async function openEditDialog(roomType: RoomTypeCatalogEntry) {
    const generation = ++editDefaultsGeneration.current;
    const requestedHotelId = hotelId;
    setEditError("");
    setEditTarget(roomType);
    setEditName(roomType.name);
    setEditOccupancy(String(roomType.maxOccupancy));
    setEditBreakfast(roomType.breakfastIncluded);
    setEditRate(
      roomType.defaultRateKrw === null ? "" : String(roomType.defaultRateKrw),
    );
    setEditOpen(true);
    if (!sessionToken) return;
    // 본사가 입력한 값이 현재 설정과 같은지 확인하려면 서버의 현재값이 필요하다.
    // 요금제가 없는 유형은 빈 요금 입력으로 두고 서버가 404로 안내한다.
    try {
      const defaults = await getRoomTypeDefaults(
        sessionToken,
        requestedHotelId,
        roomType.roomTypeId,
      );
      if (
        !defaults ||
        !mounted.current ||
        editDefaultsGeneration.current !== generation
      )
        return;
      setEditBreakfast(defaults.breakfastIncluded);
      setEditRate(
        defaults.defaultRateKrw === null ? "" : String(defaults.defaultRateKrw),
      );
    } catch {
      // 읽기 실패가 수정을 막지 않도록 카탈로그 값을 그대로 둔다.
    }
  }

  function closeEditDialog() {
    editDefaultsGeneration.current += 1;
    setEditOpen(false);
    setEditTarget(null);
  }

  async function createHotelSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const name = hotelName.trim();
    const region = hotelRegion.trim();
    const timezone = hotelTimezone.trim();
    if (!name) {
      setHotelCreateError("지점 이름을 입력해 주세요.");
      return;
    }
    if (!region) {
      setHotelCreateError("지역을 입력해 주세요.");
      return;
    }
    // 범위 판단은 서버에 둔다. 브라우저가 폼 제출을 막으면 서버 검증 응답이
    // 도달하지 않으므로 클라이언트는 필수 입력만 확인한다.
    setHotelCreating(true);
    setHotelCreateError("");
    try {
      const result = await createHotel(
        sessionToken,
        `create-hotel-${hotelCreateKey}`,
        { name, region, timezone },
      );
      setHotelCreateKey((key) => key + 1);
      setHotelCreateOpen(false);
      setHotelName("");
      setHotelRegion("");
      setHotelTimezone(DEFAULT_TIMEZONE);
      await refreshHotels();
      // 시드가 없는 빈 지점이라는 것을 명시적으로 안내한다.
      setCreateNotice(
        result.created
          ? `${result.name} 지점을 만들었습니다. 객실 유형이 없으므로 고객 검색에 나타나지 않습니다. 이 화면에서 객실 유형을 추가하면 기본 요금제와 일자 재고가 심어집니다.`
          : `${result.name} 지점은 이미 만들어져 있습니다. 객실 유형을 추가하면 고객 검색에 나타납니다.`,
      );
    } catch (cause) {
      setHotelCreateError(
        cause instanceof StaffApiError
          ? cause.message
          : "지점을 추가하지 못했습니다.",
      );
    } finally {
      setHotelCreating(false);
    }
  }

  function openHotelCreateDialog() {
    setHotelCreateError("");
    setHotelName("");
    setHotelRegion("");
    setHotelTimezone(DEFAULT_TIMEZONE);
    setHotelCreateOpen(true);
  }

  function openHotelEditDialog(hotel: StaffHotelSummary) {
    setHotelEditError("");
    setHotelEditTarget(hotel);
    setHotelEditName(hotel.name);
    setHotelEditRegion(hotel.region);
    setHotelEditTimezone(hotel.timezone);
    setHotelEditOpen(true);
  }

  async function hotelEditSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !hotelEditTarget) return;
    const name = hotelEditName.trim();
    const region = hotelEditRegion.trim();
    const timezone = hotelEditTimezone.trim();
    if (!name) {
      setHotelEditError("지점 이름을 입력해 주세요.");
      return;
    }
    if (!region) {
      setHotelEditError("지역을 입력해 주세요.");
      return;
    }
    // 범위 판단은 서버에 둔다. 브라우저가 폼 제출을 막으면 서버 검증 응답이
    // 도달하지 않으므로 클라이언트는 필수 입력만 확인한다.
    setHotelEditing(true);
    setHotelEditError("");
    try {
      await updateHotel(sessionToken, hotelEditTarget.id, `update-hotel-${hotelEditKey}`, {
        name,
        region,
        timezone,
      });
      setHotelEditKey((key) => key + 1);
      setHotelEditOpen(false);
      setHotelEditTarget(null);
      await refreshHotels();
    } catch (cause) {
      setHotelEditError(
        cause instanceof StaffApiError
          ? cause.message
          : "지점을 수정하지 못했습니다.",
      );
    } finally {
      setHotelEditing(false);
    }
  }

  async function toggleHotelActive(hotel: StaffHotelSummary) {
    if (!sessionToken) return;
    // 중지·재개는 매번 새 멱원 키를 쓴다. 재시도가 같은 키를 재사용하지 않게 해서
    // 네트워크 장애 뒤 다시 눌러도 중복 전환이 생기지 않는다.
    setActivationError("");
    try {
      await setHotelActive(
        sessionToken,
        hotel.id,
        `toggle-hotel-${activationKey}`,
        !hotel.active,
      );
      setActivationKey((key) => key + 1);
      await refreshHotels();
    } catch (cause) {
      setActivationError(
        cause instanceof StaffApiError
          ? cause.message
          : "판매 상태를 바꾸지 못했습니다.",
      );
    }
  }

  async function editRoomTypeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !editTarget) return;
    const name = editName.trim();
    const occupancy = Number(editOccupancy);
    const rate = parseRateKrw(editRate);
    if (!name) {
      setEditError("객실 유형 이름을 입력해 주세요.");
      return;
    }
    if (!Number.isInteger(occupancy) || occupancy < 1 || occupancy > 20) {
      setEditError("최대 인원은 1 이상 20 이하여야 합니다.");
      return;
    }
    if (rate === null) {
      setEditError(
        "기본 요금을 숫자로 입력해 주세요. 요금제가 없는 유형은 빈 칸으로 두면 변경하지 않습니다.",
      );
      return;
    }
    if (rate < 0 || rate > 10_000_000) {
      setEditError("기본 요금은 0원 이상 10,000,000원 이하여야 합니다.");
      return;
    }
    // 최대 인원을 내릴 때 진행 중인 예약과 충돌하면 서버가 409로 거부한다.
    // 여기서 막으면 서버 검증 메시지를 대화상자에서 볼 수 없다.
    setEditing(true);
    setEditError("");
    try {
      await updateRoomType(
        sessionToken,
        hotelId,
        editTarget.roomTypeId,
        `update-room-type-${editKey}`,
        {
          name,
          maxOccupancy: occupancy,
          breakfastIncluded: editBreakfast,
          defaultRateKrw: rate,
        },
      );
      setEditKey((key) => key + 1);
      closeEditDialog();
      await refresh();
    } catch (cause) {
      setEditError(
        cause instanceof StaffApiError
          ? cause.message
          : "객실 유형을 수정하지 못했습니다.",
      );
    } finally {
      setEditing(false);
    }
  }

  function openDeleteDialog(roomType: RoomTypeCatalogEntry) {
    setDeleteError("");
    setDeleteTarget(roomType);
    setDeleteOpen(true);
  }

  async function deleteRoomTypeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !deleteTarget) return;
    setDeleting(true);
    setDeleteError("");
    try {
      const result = await deleteRoomType(
        sessionToken,
        hotelId,
        deleteTarget.roomTypeId,
        `delete-room-type-${deleteKey}`,
      );
      setDeleteKey((key) => key + 1);
      // 삭제는 매번 새 멱원 키를 쓴다. 재시도가 같은 키를 재사용하지 않게 해서
      // 네트워크 장애 뒤 다시 눌러도 중복 삭제 시도가 생기지 않는다.
      setDeleteOpen(false);
      setDeleteTarget(null);
      await refresh();
      setCreateNotice(
        result.deleted
          ? `${result.name}을 지웠습니다. 남은 객실 유형은 ${result.remainingRoomTypes}종입니다.`
          : `${result.name}은(는) 이미 지워졌습니다. 남은 객실 유형은 ${result.remainingRoomTypes}종입니다.`,
      );
    } catch (cause) {
      setDeleteError(
        cause instanceof StaffApiError
          ? cause.message
          : "객실 유형을 지우지 못했습니다.",
      );
    } finally {
      setDeleting(false);
    }
  }

  function openPlanCreateDialog(roomType: RoomTypeCatalogEntry) {
    setPlanCreateError("");
    setPlanCreateTarget(roomType);
    // 기본 요금제의 조건을 미리 채워 본사가 덜 입력하게 한다.
    const seeded = roomType.ratePlans[0];
    setPlanName("");
    setPlanBreakfast(seeded ? seeded.breakfastIncluded : false);
    setPlanPolicy(seeded ? seeded.policyVersion : "FLEX-2026-01");
    setPlanRate(
      seeded && seeded.minAmountKrw !== null
        ? String(seeded.minAmountKrw)
        : DEFAULT_RATE_KRW,
    );
    setPlanDays("90");
    setPlanCreateOpen(true);
  }

  async function planCreateSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !planCreateTarget) return;
    const rate = parseRateKrw(planRate);
    const days = Number.parseInt(planDays, 10);
    if (rate === null) {
      setPlanCreateError("기본 요금은 0원 이상 10,000,000원 이하의 정수여야 합니다.");
      return;
    }
    if (!Number.isInteger(days) || days < 1 || days > 92) {
      setPlanCreateError("요금을 심을 일수는 1일 이상 92일 이하여야 합니다.");
      return;
    }
    if (!planName.trim()) {
      setPlanCreateError("요금제 이름을 입력해 주세요.");
      return;
    }
    if (!planPolicy.trim()) {
      setPlanCreateError("정책 버전을 입력해 주세요.");
      return;
    }
    setPlanCreating(true);
    setPlanCreateError("");
    try {
      // 생성·수정은 매번 새 멱원 키를 쓴다. 재시도가 같은 키를 재사용하지 않게
      // 해서 네트워크 장애 뒤 다시 눌러도 중복 생성이 생기지 않는다.
      const result = await createRatePlan(
        sessionToken,
        hotelId,
        planCreateTarget.roomTypeId,
        `create-rate-plan-${planCreateKey}`,
        {
          name: planName.trim(),
          breakfastIncluded: planBreakfast,
          policyVersion: planPolicy.trim(),
          defaultRateKrw: rate,
          fromDate: null,
          days,
        },
      );
      setPlanCreateKey((key) => key + 1);
      setPlanCreateOpen(false);
      setPlanCreateTarget(null);
      await refresh();
      setCreateNotice(
        `${result.name}을(를) ${result.seededDays}일분 추가했습니다. 고객 검색에 별도 요금제로 나타납니다.`,
      );
    } catch (cause) {
      // 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
      setPlanCreateError(
        cause instanceof StaffApiError
          ? cause.message
          : "요금제를 추가하지 못했습니다.",
      );
    } finally {
      setPlanCreating(false);
    }
  }

  function openPlanRenameDialog(
    roomType: RoomTypeCatalogEntry,
    plan: RatePlanSummary,
  ) {
    setPlanRenameError("");
    setPlanRenameTarget({ roomType, plan });
    setPlanRename(plan.name);
    setPlanRenameOpen(true);
  }

  async function planRenameSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !planRenameTarget) return;
    if (!planRename.trim()) {
      setPlanRenameError("요금제 이름을 입력해 주세요.");
      return;
    }
    setPlanRenaming(true);
    setPlanRenameError("");
    try {
      await renameRatePlan(
        sessionToken,
        hotelId,
        planRenameTarget.roomType.roomTypeId,
        planRenameTarget.plan.ratePlanId,
        `rename-rate-plan-${planRenameKey}`,
        planRename.trim(),
      );
      setPlanRenameKey((key) => key + 1);
      setPlanRenameOpen(false);
      setPlanRenameTarget(null);
      await refresh();
    } catch (cause) {
      // 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
      setPlanRenameError(
        cause instanceof StaffApiError
          ? cause.message
          : "요금제 이름을 바꾸지 못했습니다.",
      );
    } finally {
      setPlanRenaming(false);
    }
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
            객실 유형과 요금제를 읽기 전용으로 확인한다. 가격·재고·예약 확정의
            권한은 서버에 있다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => void refresh()}
            disabled={loading || !isHeadquarters || !hotelId}
          >
            <RefreshCw className="h-4 w-4" aria-hidden />
            {loading ? "불러오는 중" : "새로고침"}
          </Button>
          {isHeadquarters && (
            <>
              <Button
                type="button"
                variant="outline"
                onClick={openHotelCreateDialog}
                data-testid="create-hotel"
              >
                <Plus className="h-4 w-4" aria-hidden />
                지점 추가
              </Button>
              <Button
                type="button"
                onClick={openCreateDialog}
                data-testid="create-room-type"
              >
                <Plus className="h-4 w-4" aria-hidden />
                객실 유형 추가
              </Button>
            </>
          )}
        </div>
      </div>

      {error && (
        <p role="alert" className="break-words text-sm text-destructive">
          {error}
        </p>
      )}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">지점</CardTitle>
            <CardDescription>
              지점을 선택하면 해당 지점의 객실 유형이 아래에 나타난다. 판매 중지한
              지점은 고객 검색과 지점 목록에서 빠진다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table aria-label="지점 선택 및 관리">
              <TableHeader>
                <TableRow>
                  <TableHead>지점</TableHead>
                  <TableHead>지역</TableHead>
                  <TableHead>시간대</TableHead>
                  <TableHead>판매</TableHead>
                  <TableHead>
                    <span className="sr-only">수정</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {hotels.map((hotel) => {
                  const selected = hotel.id === hotelId;
                  return (
                    <TableRow
                      key={hotel.id}
                      data-state={selected ? "selected" : undefined}
                    >
                      <TableCell className="font-medium">
                        <div className="flex items-center gap-2">
                          <Button
                            type="button"
                            variant="link"
                            size="sm"
                            className="h-auto p-0 text-left aria-pressed:font-bold"
                            aria-pressed={selected}
                            disabled={selectionLocked}
                            data-selected={selected}
                            data-testid={`hotel-selector-${hotel.id}`}
                            onClick={() => selectHotel(hotel.id)}
                          >
                            {hotel.name}
                          </Button>
                          {selected ? (
                            <Badge variant="secondary">선택됨</Badge>
                          ) : null}
                        </div>
                      </TableCell>
                      <TableCell>{hotel.region}</TableCell>
                      <TableCell className="tabular-nums text-muted-foreground">
                        {hotel.timezone}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={hotel.active ? "default" : "secondary"}
                          data-testid={`hotel-active-badge-${hotel.id}`}
                        >
                          {hotel.active ? "판매 중" : "판매 중지"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex flex-wrap justify-end gap-1.5">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => toggleHotelActive(hotel)}
                            aria-label={
                              hotel.active
                                ? `${hotel.name} 판매 중지`
                                : `${hotel.name} 판매 재개`
                            }
                            data-testid={`toggle-hotel-active-${hotel.id}`}
                          >
                            {hotel.active ? "판매 중지" : "판매 재개"}
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => openHotelEditDialog(hotel)}
                            aria-label={`${hotel.name} 수정`}
                            data-testid={`edit-hotel-${hotel.id}`}
                          >
                            <Pencil className="h-4 w-4" aria-hidden />
                            수정
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && activationError && (
        <p role="alert" className="break-words text-sm text-destructive">
          {activationError}
        </p>
      )}

      {isHeadquarters && hotelsLoading && (
        <p
          role="status"
          className="text-sm text-muted-foreground"
          data-testid="hotel-list-loading"
        >
          지점 목록을 불러오는 중입니다.
        </p>
      )}

      {isHeadquarters && !hotelsLoading && hotels.length === 0 && !hotelError && (
        <p className="text-sm text-muted-foreground">
          등록된 지점이 없습니다. 지점을 먼저 추가해 주세요.
        </p>
      )}

      {isHeadquarters && hotelError && (
        <p role="alert" className="break-words text-sm text-destructive">
          {hotelError}
        </p>
      )}

      {selectedCatalog && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {selectedHotel?.name ?? "선택한 지점"} · 객실 유형 {selectedCatalog.totalCount}종
            </CardTitle>
            <CardDescription>
              객실 유형별로 등록된 요금제와 일자별 요금 범위를 보여준다.
              화면에서 값을 변경하지 않는다.
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
                  {isHeadquarters ? (
                    <TableHead className="text-right">수정</TableHead>
                  ) : null}
                  {isHeadquarters ? (
                    <TableHead className="text-right">삭제</TableHead>
                  ) : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {selectedCatalog.roomTypes.map((roomType) => {
                  const prices = roomType.ratePlans.flatMap((plan) =>
                    plan.minAmountKrw !== null && plan.maxAmountKrw !== null
                      ? [plan.minAmountKrw, plan.maxAmountKrw]
                      : [],
                  );
                  return (
                    <>
                      <TableRow
                        key={roomType.roomTypeId}
                        data-room-type-id={roomType.roomTypeId}
                      >
                      <TableCell className="font-medium">
                        {roomType.name}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {roomType.maxOccupancy}명
                      </TableCell>
                      <TableCell>
                        {roomType.ratePlans.length === 0 ? (
                          <span className="text-xs text-muted-foreground">
                            등록된 요금제 없음
                          </span>
                        ) : (
                          <div className="grid gap-1">
                            {roomType.ratePlans.map((plan) => (
                              <span
                                key={plan.ratePlanId}
                                data-testid="rate-plan-name"
                                className="text-sm"
                              >
                                {plan.name}
                              </span>
                            ))}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="grid gap-1">
                          {roomType.ratePlans.map((plan) => (
                            <Badge
                              key={plan.ratePlanId}
                              variant={
                                plan.breakfastIncluded ? "default" : "secondary"
                              }
                            >
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
                      {isHeadquarters ? (
                        <TableCell className="text-right align-top">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => openEditDialog(roomType)}
                            data-testid={`edit-room-type-${roomType.roomTypeId}`}
                            aria-label={`${roomType.name} 수정`}
                          >
                            <Pencil className="h-4 w-4" aria-hidden />
                            수정
                          </Button>
                        </TableCell>
                      ) : null}
                      {isHeadquarters ? (
                        <TableCell className="text-right align-top">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => openDeleteDialog(roomType)}
                            data-testid={`delete-room-type-${roomType.roomTypeId}`}
                            aria-label={`${roomType.name} 삭제`}
                          >
                            <Trash2 className="h-4 w-4" aria-hidden />
                            삭제
                          </Button>
                        </TableCell>
                      ) : null}
                    </TableRow>
                    {isHeadquarters ? (
                      <TableRow
                        key={`${roomType.roomTypeId}-plans`}
                        className="bg-muted/30 hover:bg-muted/30"
                      >
                        <TableCell colSpan={7} className="p-3">
                          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                            <div className="text-xs text-muted-foreground">
                              한 객실 유형에 여러 요금제를 둘 수 있다. 조식
                              포함 여부·정책 버전이 다른 요금제는 고객 검색에
                              별도 오퍼로 나타난다. 조식·정책은 확정 예약의
                              계약 조건이어서 이름만 바꿀 수 있다.
                            </div>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => openPlanCreateDialog(roomType)}
                              data-testid={`add-rate-plan-${roomType.roomTypeId}`}
                              aria-label={`${roomType.name} 요금제 추가`}
                            >
                              <Plus className="h-4 w-4" aria-hidden />
                              요금제 추가
                            </Button>
                          </div>
                          {roomType.ratePlans.length === 0 ? (
                            <p className="mt-2 text-xs text-muted-foreground">
                              등록된 요금제가 없습니다.
                            </p>
                          ) : (
                            <ul
                              className="mt-3 grid gap-2"
                              data-testid={`rate-plan-list-${roomType.roomTypeId}`}
                            >
                              {roomType.ratePlans.map((plan) => (
                                <li
                                  key={plan.ratePlanId}
                                  className="flex flex-col gap-2 rounded-md border bg-background p-2 text-sm sm:flex-row sm:items-center sm:justify-between"
                                >
                                  <span className="grid gap-1">
                                    <span className="font-medium">
                                      {plan.name}
                                    </span>
                                    <span className="text-xs text-muted-foreground">
                                      {plan.breakfastIncluded
                                        ? "조식 포함"
                                        : "객실만"}
                                      {" · "}
                                      {plan.policyVersion}
                                      {" · "}
                                      {plan.pricedDays}일분
                                      {" · "}
                                      {range(
                                        plan.minAmountKrw ?? 0,
                                        plan.maxAmountKrw ?? 0,
                                      )}
                                    </span>
                                  </span>
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    onClick={() =>
                                      openPlanRenameDialog(roomType, plan)
                                    }
                                    data-testid={`rename-rate-plan-${plan.ratePlanId}`}
                                    aria-label={`${plan.name} 이름 변경`}
                                  >
                                    <Pencil className="h-4 w-4" aria-hidden />
                                    이름 변경
                                  </Button>
                                </li>
                              ))}
                            </ul>
                          )}
                        </TableCell>
                      </TableRow>
                    ) : null}
                    </>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && hotelId && !selectedCatalog && !error && (
        <p
          role="status"
          className="text-sm text-muted-foreground"
          data-testid="room-type-list-loading"
        >
          객실 유형 목록을 불러오는 중입니다.
        </p>
      )}

      {createNotice && isHeadquarters && (
        <p
          role="status"
          data-testid="create-room-type-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {createNotice}
        </p>
      )}

      <Dialog open={hotelCreateOpen} onOpenChange={setHotelCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>지점 추가</DialogTitle>
            <DialogDescription>
              새 지점을 만든다. 지점 행만 만들고 객실 유형·요금·재고는 심지
              않으므로, 고객 검색에 나타내려면 이어서 객실 유형을 추가해야
              한다. 시간대는 재고·요금 시드와 취소 마감 시각의 기준이 된다.
              가격·재고·예약 확정의 권한은 서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={createHotelSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              지점 이름
              <input
                aria-label="지점 이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={hotelName}
                onChange={(event) => setHotelName(event.target.value)}
                data-testid="create-hotel-name"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              지역
              <input
                aria-label="지역"
                type="text"
                required
                maxLength={50}
                className="h-9 rounded-lg border bg-background px-3"
                value={hotelRegion}
                onChange={(event) => setHotelRegion(event.target.value)}
                data-testid="create-hotel-region"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              시간대
              <input
                aria-label="시간대"
                type="text"
                required
                maxLength={50}
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={hotelTimezone}
                onChange={(event) => setHotelTimezone(event.target.value)}
                data-testid="create-hotel-timezone"
              />
              <span className="text-xs font-normal text-muted-foreground">
                IANA 시간대. 예: Asia/Seoul
              </span>
            </label>
            {hotelCreateError && (
              <p role="alert" className="break-words text-sm text-destructive">
                {hotelCreateError}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setHotelCreateOpen(false)}
                disabled={hotelCreating}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={hotelCreating || !hotelName.trim()}
                data-testid="create-hotel-submit"
              >
                {hotelCreating ? "추가하는 중" : "추가"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={hotelEditOpen} onOpenChange={setHotelEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>지점 수정</DialogTitle>
            <DialogDescription>
              이름·지역·시간대를 바꾼다. 시간대는 재고·요금 시드와 취소 마감
              시각의 기준이지만, 이미 지나간 날짜의 재고·예약은 다시 계산하지
              않는다. 새 시간대는 그 이후의 판정에 적용된다. 이미 쓰고 있는
              이름은 다른 지점과 겹칠 수 없다. 가격·재고·예약 확정의 권한은
              서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={hotelEditSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              지점 이름
              <input
                aria-label="지점 이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={hotelEditName}
                onChange={(event) => setHotelEditName(event.target.value)}
                data-testid="edit-hotel-name"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              지역
              <input
                aria-label="지역"
                type="text"
                required
                maxLength={50}
                className="h-9 rounded-lg border bg-background px-3"
                value={hotelEditRegion}
                onChange={(event) => setHotelEditRegion(event.target.value)}
                data-testid="edit-hotel-region"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              시간대
              <input
                aria-label="시간대"
                type="text"
                required
                maxLength={50}
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={hotelEditTimezone}
                onChange={(event) => setHotelEditTimezone(event.target.value)}
                data-testid="edit-hotel-timezone"
              />
              <span className="text-xs font-normal text-muted-foreground">
                IANA 시간대. 예: Asia/Seoul
              </span>
            </label>
            {hotelEditError && (
              <p role="alert" className="break-words text-sm text-destructive">
                {hotelEditError}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setHotelEditOpen(false)}
                disabled={hotelEditing}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={hotelEditing || !hotelEditName.trim()}
                data-testid="edit-hotel-submit"
              >
                {hotelEditing ? "수정 중" : "수정"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>객실 유형 추가</DialogTitle>
            <DialogDescription>
              이름·최대 인원·조식 포함 여부·기본 요금을 입력한다. 조식 포함
              여부는 진행 중인 예약의 계약 조건이어서 충돌하면 서버가 거부한다.
              가격·재고·예약 확정의 권한은 서버에 있다.
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
            <label className="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                className="size-4 rounded border"
                checked={createBreakfast}
                onChange={(event) => setCreateBreakfast(event.target.checked)}
                data-testid="create-room-type-breakfast"
              />
              조식 포함
            </label>
            <label className="grid gap-1 text-sm font-medium">
              기본 요금 (원)
              <input
                aria-label="기본 요금"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={createRate}
                onChange={(event) => setCreateRate(event.target.value)}
                data-testid="create-room-type-rate"
              />
              <span className="text-xs font-normal text-muted-foreground">
                90일분 일자 요금·재고의 기준이 되는 1박 금액
              </span>
            </label>
            {createError && (
              <p role="alert" className="break-words text-sm text-destructive">
                {createError}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setCreateOpen(false)}
                disabled={creating}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={creating || !createName.trim()}
                data-testid="create-room-type-submit"
              >
                {creating ? "추가하는 중" : "추가"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={editOpen}
        onOpenChange={(open) => {
          if (!open) closeEditDialog();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>객실 유형 수정</DialogTitle>
            <DialogDescription>
              이름·최대 인원·조식 포함 여부·기본 요금을 변경한다. 최대 인원을
              내릴 때 진행 중인 예약이 새 인원을 초과하면 서버가 거부한다. 조식
              포함 여부도 진행 중인 예약의 계약 조건이어서 충돌하면 거부한다.
              가격·재고·예약 확정의 권한은 서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={editRoomTypeSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              객실 유형 이름
              <input
                aria-label="객실 유형 이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={editName}
                onChange={(event) => setEditName(event.target.value)}
                data-testid="edit-room-type-name"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              최대 인원
              <input
                aria-label="최대 인원"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={editOccupancy}
                onChange={(event) => setEditOccupancy(event.target.value)}
                data-testid="edit-room-type-occupancy"
              />
            </label>
            <label className="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                className="size-4 rounded border"
                checked={editBreakfast}
                onChange={(event) => setEditBreakfast(event.target.checked)}
                data-testid="edit-room-type-breakfast"
              />
              조식 포함
            </label>
            <label className="grid gap-1 text-sm font-medium">
              기본 요금 (원)
              <input
                aria-label="기본 요금"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                placeholder="미등록"
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={editRate}
                onChange={(event) => setEditRate(event.target.value)}
                data-testid="edit-room-type-rate"
              />
              <span className="text-xs font-normal text-muted-foreground">
                요금제가 없는 유형은 빈 칸이면 변경하지 않는다
              </span>
            </label>
            {editError ? (
              <p role="alert" className="break-words text-sm text-destructive">
                {editError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={closeEditDialog}
                disabled={editing}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={editing || !editName.trim()}
                data-testid="edit-room-type-submit"
              >
                {editing ? "수정하는 중" : "수정"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>객실 유형 삭제</DialogTitle>
            <DialogDescription>
              {deleteTarget
                ? `${deleteTarget.name}을(를) 지운다. 진행 중인 예약·예약 변경 요청·보류 중인 재고·배정된 실제 객실이 있으면 서버가 거부한다.`
                : "객실 유형을 선택해 주세요."}
              종료된 예약과 이력은 보존하고 고객 검색에서만 빠진다. 이미 확정된
              예약을 취소하지 않으므로 본사가 고객에게 미리 알려야 한다.
              가격·재고·예약 확정의 권한은 서버에 있다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={deleteRoomTypeSubmit} className="grid gap-3">
            {deleteError ? (
              <p role="alert" className="break-words text-sm text-destructive">
                {deleteError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setDeleteOpen(false)}
                disabled={deleting}
              >
                취소
              </Button>
              <Button
                type="submit"
                variant="destructive"
                disabled={deleting}
                data-testid="delete-room-type-submit"
              >
                {deleting ? "삭제하는 중" : "삭제"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={planCreateOpen} onOpenChange={setPlanCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>요금제 추가</DialogTitle>
            <DialogDescription>
              {planCreateTarget
                ? `${planCreateTarget.name}에 새 요금제를 추가한다.`
                : "객실 유형을 선택해 주세요."}
              조식 포함 여부·정책 버전이 다른 요금제는 고객 검색에 별도
              오퍼로 나타난다. 재고는 객실 유형 단위이므로 요금제가 공유하고,
              재고가 없는 날짜는 거부한다. 시작일을 비우면 지점 현지 시간대
              기준 오늘부터 심는다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={planCreateSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              요금제 이름
              <input
                aria-label="요금제 이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={planName}
                onChange={(event) => setPlanName(event.target.value)}
                data-testid="create-rate-plan-name"
              />
            </label>
            <label className="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                className="size-4 rounded border"
                checked={planBreakfast}
                onChange={(event) => setPlanBreakfast(event.target.checked)}
                data-testid="create-rate-plan-breakfast"
              />
              조식 포함
            </label>
            <label className="grid gap-1 text-sm font-medium">
              정책 버전
              <input
                aria-label="정책 버전"
                type="text"
                required
                maxLength={50}
                className="h-9 rounded-lg border bg-background px-3"
                value={planPolicy}
                onChange={(event) => setPlanPolicy(event.target.value)}
                data-testid="create-rate-plan-policy"
              />
              <span className="text-xs font-normal text-muted-foreground">
                예약의 취소 규정을 구분하는 값 (예: FLEX-2026-01)
              </span>
            </label>
            <label className="grid gap-1 text-sm font-medium">
              기본 요금 (원)
              <input
                aria-label="기본 요금"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={planRate}
                onChange={(event) => setPlanRate(event.target.value)}
                data-testid="create-rate-plan-rate"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              요금을 심을 일수
              <input
                aria-label="요금을 심을 일수"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={planDays}
                onChange={(event) => setPlanDays(event.target.value)}
                data-testid="create-rate-plan-days"
              />
              <span className="text-xs font-normal text-muted-foreground">
                1일 이상 92일 이하. 재고가 있는 날짜만 심을 수 있다.
              </span>
            </label>
            {planCreateError ? (
              <p role="alert" className="break-words text-sm text-destructive">
                {planCreateError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setPlanCreateOpen(false)}
                disabled={planCreating}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={planCreating || !planName.trim()}
                data-testid="create-rate-plan-submit"
              >
                {planCreating ? "추가하는 중" : "추가"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={planRenameOpen} onOpenChange={setPlanRenameOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>요금제 이름 변경</DialogTitle>
            <DialogDescription>
              {planRenameTarget
                ? `${planRenameTarget.roomType.name}의 ${planRenameTarget.plan.name} 이름을 바꾼다.`
                : "요금제를 선택해 주세요."}
              조식 포함 여부·정책 버전은 확정 예약의 계약 조건이어서
              이름만 바꿀 수 있다. 계약 조건을 바꾸려면 새 요금제를
              추가해야 한다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={planRenameSubmit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              요금제 이름
              <input
                aria-label="요금제 이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={planRename}
                onChange={(event) => setPlanRename(event.target.value)}
                data-testid="rename-rate-plan-name"
              />
            </label>
            {planRenameError ? (
              <p role="alert" className="break-words text-sm text-destructive">
                {planRenameError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setPlanRenameOpen(false)}
                disabled={planRenaming}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={planRenaming || !planRename.trim()}
                data-testid="rename-rate-plan-submit"
              >
                {planRenaming ? "변경하는 중" : "변경"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
