"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  BedDouble,
  Building2,
  CalendarCheck2,
  ClipboardCheck,
  SprayCan,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  getDailyOperations,
  type DailyOperationsView,
  type StaffPrincipal,
} from "@/lib/staff-api";

type WorkspaceId = "hq" | "sokcho" | "seoraksan" | "jeju";

type Workspace = {
  id: WorkspaceId;
  hotelId: string | null;
  label: string;
  title: string;
  description: string;
};

const workspaces: Workspace[] = [
  {
    id: "hq",
    hotelId: null,
    label: "본사",
    title: "본사 오늘의 운영 현황",
    description: "서울 오늘 기준으로 세 지점의 도착·출발·객실 배정·청소 업무를 비교합니다.",
  },
  {
    id: "sokcho",
    hotelId: "11000000-0000-0000-0000-000000000001",
    label: "속초 지점",
    title: "속초 지점 오늘의 업무",
    description: "프런트와 하우스키핑이 처리할 오늘 업무를 실제 예약과 객실 상태로 확인합니다.",
  },
  {
    id: "seoraksan",
    hotelId: "11000000-0000-0000-0000-000000000002",
    label: "설악산 지점",
    title: "설악산 지점 오늘의 업무",
    description: "프런트와 하우스키핑이 처리할 오늘 업무를 실제 예약과 객실 상태로 확인합니다.",
  },
  {
    id: "jeju",
    hotelId: "11000000-0000-0000-0000-000000000003",
    label: "제주 지점",
    title: "제주 지점 오늘의 업무",
    description: "프런트와 하우스키핑이 처리할 오늘 업무를 실제 예약과 객실 상태로 확인합니다.",
  },
];

const branchWorkspaces = workspaces.filter((workspace) => workspace.hotelId !== null);
const branchWorkspaceByHotelId = Object.fromEntries(
  branchWorkspaces.map((workspace) => [workspace.hotelId, workspace.id]),
) as Record<string, WorkspaceId>;

function seoulToday() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? "";
  return `${value("year")}-${value("month")}-${value("day")}`;
}

function metrics(data?: DailyOperationsView) {
  return {
    arrivals: data?.arrivals.length ?? 0,
    departures: data?.departures.length ?? 0,
    assignments: data?.arrivals.filter((arrival) => arrival.assignedRoomNumbers.length === 0).length ?? 0,
    cleaning: data?.roomsNeedingCleaning.length ?? 0,
  };
}

export function OperationsOverview() {
  const [selectedId, setSelectedId] = useState<WorkspaceId>("hq");
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [dailyByHotel, setDailyByHotel] = useState<Record<string, DailyOperationsView>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshVersion, setRefreshVersion] = useState(0);

  useEffect(() => {
    const saved = window.localStorage.getItem("hotel-chain-staff");
    if (!saved) return;
    try {
      const principal = JSON.parse(saved) as StaffPrincipal;
      setStaff(principal);
      if (principal.role === "BRANCH_STAFF" && principal.hotelId) {
        const workspace = branchWorkspaceByHotelId[principal.hotelId];
        if (workspace) setSelectedId(workspace);
      }
    } catch {
      window.localStorage.removeItem("hotel-chain-staff");
    }
  }, []);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !staff) return;
    const targets = staff.role === "BRANCH_STAFF"
      ? branchWorkspaces.filter((workspace) => workspace.hotelId === staff.hotelId)
      : branchWorkspaces;
    const today = seoulToday();
    let current = true;
    setLoading(true);
    setError(null);
    void Promise.allSettled(targets.map(async (workspace) => {
      const result = await getDailyOperations(token, workspace.hotelId!, today);
      return [workspace.hotelId!, result] as const;
    }))
      .then((results) => {
        if (!current) return;
        const entries = results.flatMap((result) => result.status === "fulfilled" ? [result.value] : []);
        const failedCount = results.length - entries.length;
        setDailyByHotel(Object.fromEntries(entries));
        if (failedCount > 0) {
          setError(entries.length > 0
            ? "일부 지점의 오늘 운영 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
            : "오늘의 운영 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [staff, refreshVersion]);

  const availableWorkspaces = useMemo(
    () => staff?.role === "BRANCH_STAFF" && staff.hotelId
      ? workspaces.filter((workspace) => workspace.id === branchWorkspaceByHotelId[staff.hotelId!])
      : workspaces,
    [staff],
  );
  const selected = availableWorkspaces.find((workspace) => workspace.id === selectedId) ?? availableWorkspaces[0];
  const isHeadOffice = selected.id === "hq";

  return (
    <div className="flex flex-col gap-6">
      <section className="rounded-xl border bg-card p-5 shadow-sm sm:p-6">
        <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
          <div className="space-y-2">
            <p className="text-sm font-medium text-primary">Hotel Chain Manager</p>
            <h1 className="text-2xl font-semibold tracking-tight">호텔 운영 현황</h1>
            <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
              서울 오늘 기준의 실제 예약과 객실 상태를 본사와 각 지점의 업무 흐름으로 연결합니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2" aria-label="운영 컨텍스트 선택">
            {availableWorkspaces.map((workspace) => (
              <Button
                key={workspace.id}
                type="button"
                variant={selectedId === workspace.id ? "default" : "outline"}
                aria-pressed={selectedId === workspace.id}
                onClick={() => setSelectedId(workspace.id)}
              >
                {workspace.label}
              </Button>
            ))}
          </div>
        </div>
      </section>

      <section className="space-y-4" aria-live="polite">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{selected.title}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{selected.description}</p>
        </div>

        {error && (
          <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
            <span>{error}</span>
            <Button type="button" size="sm" variant="outline" onClick={() => setRefreshVersion((version) => version + 1)}>
              다시 시도
            </Button>
          </div>
        )}
        {loading && <p className="text-sm text-muted-foreground">오늘의 운영 데이터를 불러오는 중입니다.</p>}

        {isHeadOffice
          ? <HeadOfficeMetrics dailyByHotel={dailyByHotel} />
          : <BranchMetrics workspace={selected} data={selected.hotelId ? dailyByHotel[selected.hotelId] : undefined} />}
      </section>
    </div>
  );
}

function HeadOfficeMetrics({ dailyByHotel }: { dailyByHotel: Record<string, DailyOperationsView> }) {
  return (
    <div className="grid gap-4 md:grid-cols-3">
      {branchWorkspaces.map((branch) => {
        const summary = metrics(dailyByHotel[branch.hotelId!]);
        return (
          <Card key={branch.id} role="group" aria-label={`${branch.label} 오늘 업무`}>
            <CardHeader>
              <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Building2 className="size-4" />
              </div>
              <CardTitle>{branch.label}</CardTitle>
              <CardDescription>서울 오늘 기준 실제 운영 데이터</CardDescription>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-3 text-sm">
              <Metric label="도착" value={summary.arrivals} />
              <Metric label="출발" value={summary.departures} />
              <Metric label="배정 필요" value={summary.assignments} />
              <Metric label="청소 필요" value={summary.cleaning} />
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-muted/60 p-3">
      <p className="text-muted-foreground">{label}</p>
      <p className="mt-1 text-lg font-semibold">{value}건</p>
    </div>
  );
}

function BranchMetrics({ workspace, data }: { workspace: Workspace; data?: DailyOperationsView }) {
  const summary = metrics(data);
  const cards = [
    { title: "도착 예정", description: "오늘 체크인할 확정 예약입니다.", value: summary.arrivals, icon: CalendarCheck2 },
    { title: "출발 예정", description: "오늘 체크아웃할 투숙 예약입니다.", value: summary.departures, icon: ClipboardCheck },
    { title: "객실 배정 필요", description: "아직 실제 객실 번호가 없는 도착 예약입니다.", value: summary.assignments, icon: BedDouble },
    { title: "청소 필요", description: "하우스키핑 완료 처리가 필요한 객실입니다.", value: summary.cleaning, icon: SprayCan },
  ];
  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <Card key={card.title} role="group" aria-label={`${workspace.label} ${card.title} ${card.value}건`}>
              <CardHeader>
                <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <Icon className="size-4" />
                </div>
                <CardTitle>{card.title}</CardTitle>
                <CardDescription>{card.description}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-semibold">{card.value}건</p>
              </CardContent>
            </Card>
          );
        })}
      </div>
      <Link className={buttonVariants()} href="/dashboard/operations">오늘의 운영 열기</Link>
    </div>
  );
}
