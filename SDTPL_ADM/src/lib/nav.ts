import type { LucideIcon } from "lucide-react";
import { ClipboardCheck, Globe, LayoutDashboard } from "lucide-react";

export type NavItem = { title: string; href: string; icon?: LucideIcon };
export type NavGroup = { label: string; items: NavItem[] };
export type StaffRole = "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";

const operationsGroup: NavGroup = {
  label: "운영",
  items: [
    { title: "운영 대시보드", href: "/dashboard/default", icon: LayoutDashboard },
    { title: "오늘의 운영", href: "/dashboard/operations", icon: ClipboardCheck },
  ],
};

const contentGroup: NavGroup = {
  label: "콘텐츠",
  items: [
    { title: "웹사이트 CMS", href: "/dashboard/website", icon: Globe },
  ],
};

export function getNavGroupsForRole(role: StaffRole | null): NavGroup[] {
  if (role === "HQ_ADMIN") return [operationsGroup, contentGroup];
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
