"use client";

import { ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { hasActiveStaffSession } from "@/lib/staff-api";

export function DashboardAccessGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    const staff = window.localStorage.getItem("hotel-chain-staff");
    if (!token || !staff) {
      router.replace("/login");
      return;
    }
    try {
      JSON.parse(staff);
      void hasActiveStaffSession(token).then((valid) => {
        if (valid) {
          setAuthenticated(true);
          return;
        }
        window.localStorage.removeItem("hotel-chain-staff-session");
        window.localStorage.removeItem("hotel-chain-staff");
        router.replace("/login");
      });
    } catch {
      window.localStorage.removeItem("hotel-chain-staff-session");
      window.localStorage.removeItem("hotel-chain-staff");
      router.replace("/login");
    }
  }, [router]);

  if (!authenticated) {
    return <main className="grid min-h-screen place-items-center text-sm text-muted-foreground">{"\uC138\uC158\uC744 \uD655\uC778\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4."}</main>;
  }
  return <>{children}</>;
}
