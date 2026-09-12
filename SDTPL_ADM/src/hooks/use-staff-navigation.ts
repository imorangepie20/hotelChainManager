"use client";

import { useEffect, useState } from "react";
import { getNavGroupsForRole, type StaffRole } from "@/lib/nav";

function readStoredRole(): StaffRole | null {
  const stored = window.localStorage.getItem("hotel-chain-staff");
  if (!stored) return null;

  try {
    const role = (JSON.parse(stored) as { role?: unknown }).role;
    return role === "HQ_ADMIN" || role === "HQ_EDITOR" || role === "HQ_PUBLISHER" || role === "BRANCH_STAFF" ? role : null;
  } catch {
    return null;
  }
}

export function useStaffNavigation() {
  const [role, setRole] = useState<StaffRole | null>(null);

  useEffect(() => {
    setRole(readStoredRole());
  }, []);

  return getNavGroupsForRole(role);
}
