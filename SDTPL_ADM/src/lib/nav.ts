import type { LucideIcon } from "lucide-react";
import { Bot, Boxes, CalendarDays, ClipboardCheck, Globe, History, Hotel, LayoutDashboard, ScrollText, TrendingUp, Users, Wallet } from "lucide-react";

export type NavItem = { title: string; href: string; icon?: LucideIcon };
export type NavGroup = { label: string; items: NavItem[] };
export type StaffRole = "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";

const operationsGroup: NavGroup = {
  label: "운영",
  items: [
    { title: "운영 대시보드", href: "/dashboard/default", icon: LayoutDashboard },
    { title: "예약 관리", href: "/dashboard/reservations", icon: CalendarDays },
    { title: "오늘의 운영", href: "/dashboard/operations", icon: ClipboardCheck },
  ],
};

// 본사 전용 메뉴. 지점 직원에게는 노출하지 않는다.
const administrationGroup: NavGroup = {
  label: "본사 관리",
  items: [
    { title: "직원 권한", href: "/dashboard/staff", icon: Users },
    { title: "공통 정책", href: "/dashboard/policies", icon: ScrollText },
    { title: "운영 통계", href: "/dashboard/reports", icon: TrendingUp },
    { title: "감사 이력", href: "/dashboard/audit", icon: History },
  ],
};

const catalogGroup: NavGroup = {
  label: "카탈로그",
  items: [
    { title: "호텔 및 객실", href: "/dashboard/hotels", icon: Hotel },
    { title: "재고·가격", href: "/dashboard/inventory", icon: Boxes },
  ],
};

const financeGroup: NavGroup = {
  label: "재무",
  items: [
    { title: "정산·대사", href: "/dashboard/settlements", icon: Wallet },
  ],
};

const contentGroup: NavGroup = {
  label: "콘텐츠",
  items: [
    { title: "웹사이트 CMS", href: "/dashboard/website", icon: Globe },
  ],
};

const aiGroup: NavGroup = {
  label: "AI",
  items: [
    { title: "AI 도우미 운영", href: "/dashboard/ai-operations", icon: Bot },
  ],
};

export function getNavGroupsForRole(role: StaffRole | null): NavGroup[] {
  if (role === "HQ_ADMIN") return [operationsGroup, administrationGroup, catalogGroup, financeGroup, contentGroup, aiGroup];
  if (role === "HQ_EDITOR" || role === "HQ_PUBLISHER") return [contentGroup];
  return [operationsGroup];
}

export const navGroups = getNavGroupsForRole("HQ_ADMIN");

export const authRoutes: NavItem[] = [
  { title: "Login", href: "/login" },
  { title: "Register", href: "/register" },
  { title: "Forgot Password", href: "/forgot-password" },
  { title: "Reset Password", href: "/reset-password" },
  { title: "Verify Email", href: "/verify" },
];

export const allNavItems: NavItem[] = navGroups.flatMap((group) => group.items);
