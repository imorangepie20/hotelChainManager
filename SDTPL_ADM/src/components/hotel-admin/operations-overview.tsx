"use client";

import { useEffect, useState } from "react";
import {
  BedDouble,
  Building2,
  CalendarCheck2,
  ClipboardCheck,
  Sparkles,
  SprayCan,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { StaffPrincipal } from "@/lib/staff-api";

type WorkspaceId = "hq" | "sokcho" | "seoraksan" | "jeju";

type Workspace = {
  id: WorkspaceId;
  label: string;
  title: string;
  description: string;
};

const workspaces: Workspace[] = [
  {
    id: "hq",
    label: "본사",
    title: "본사 운영 준비 현황",
    description: "지점별 운영 데이터가 연결되면 예약, 객실, 청소 현황을 한 곳에서 비교합니다.",
  },
  {
    id: "sokcho",
    label: "속초 지점",
    title: "속초 지점 오늘의 업무",
    description: "프런트와 하우스키핑이 같은 운영 상태를 확인할 수 있도록 준비합니다.",
  },
  {
    id: "seoraksan",
    label: "설악산 지점",
    title: "설악산 지점 오늘의 업무",
    description: "프런트와 하우스키핑이 같은 운영 상태를 확인할 수 있도록 준비합니다.",
  },
  {
    id: "jeju",
    label: "제주 지점",
    title: "제주 지점 오늘의 업무",
    description: "프런트와 하우스키핑이 같은 운영 상태를 확인할 수 있도록 준비합니다.",
  },
];

const branchWorkspaceByHotelId: Record<string, WorkspaceId> = {
  "11000000-0000-0000-0000-000000000001": "sokcho",
  "11000000-0000-0000-0000-000000000002": "seoraksan",
  "11000000-0000-0000-0000-000000000003": "jeju",
};

const branchTasks = [
  { title: "도착 확인 준비", description: "오늘 체크인 대상과 객실 배정을 연결합니다.", icon: CalendarCheck2 },
  { title: "출발 확인 준비", description: "체크아웃 후 청소 필요 상태를 기록합니다.", icon: ClipboardCheck },
  { title: "객실 배정 준비", description: "판매 객실 유형과 실제 객실 번호를 분리해 관리합니다.", icon: BedDouble },
  { title: "청소 상태 준비", description: "하우스키핑 완료 후 프런트가 객실 상태를 확인합니다.", icon: SprayCan },
];

export function OperationsOverview() {
  const [selectedId, setSelectedId] = useState<WorkspaceId>("hq");
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);

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

  const availableWorkspaces = staff?.role === "BRANCH_STAFF" && staff.hotelId
    ? workspaces.filter((workspace) => workspace.id === branchWorkspaceByHotelId[staff.hotelId!])
    : workspaces;
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
              본사와 각 지점의 업무를 한 흐름으로 연결하는 운영 화면입니다. 현재는 실제 예약 데이터 연결 전 준비 상태를 안내합니다.
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

        {isHeadOffice ? <HeadOfficeReadiness /> : <BranchReadiness />}
      </section>
    </div>
  );
}

function HeadOfficeReadiness() {
  return (
    <div className="grid gap-4 md:grid-cols-3">
      {["속초 지점", "설악산 지점", "제주 지점"].map((branch) => (
        <Card key={branch}>
          <CardHeader>
            <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Building2 className="size-4" />
            </div>
            <CardTitle>{branch}</CardTitle>
            <CardDescription>예약·객실·요금 데이터 연결 준비</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm font-medium text-muted-foreground">운영 지표 준비 중</p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function BranchReadiness() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {branchTasks.map((task) => {
        const Icon = task.icon;
        return (
          <Card key={task.title}>
            <CardHeader>
              <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Icon className="size-4" />
              </div>
              <CardTitle>{task.title}</CardTitle>
              <CardDescription>{task.description}</CardDescription>
            </CardHeader>
            <CardContent className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Sparkles className="size-4" /> 실제 운영 데이터 연결 전
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
