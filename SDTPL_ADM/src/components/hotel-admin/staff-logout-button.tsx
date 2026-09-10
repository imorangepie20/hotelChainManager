"use client";

import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { logoutStaff } from "@/lib/staff-api";

export function StaffLogoutButton() {
  async function logout() {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (token) {
      await logoutStaff(token);
    }
    window.localStorage.removeItem("hotel-chain-staff-session");
    window.localStorage.removeItem("hotel-chain-staff");
    window.location.assign("/login");
  }

  return (
    <Button type="button" variant="ghost" size="icon" aria-label={"\uB85C\uADF8\uC544\uC6C3"} onClick={logout}>
      <LogOut />
    </Button>
  );
}
