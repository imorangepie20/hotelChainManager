export type StaffPrincipal = {
  id: string;
  email: string;
  displayName: string;
  role: "HQ_ADMIN" | "BRANCH_STAFF";
  hotelId: string | null;
};

export type DailyOperationsView = {
  hotelId: string;
  date: string;
  arrivals: Array<{ reservationId: string; guestName: string; roomTypeName: string; status: string; assignedRoomNumbers: string[] }>;
  departures: Array<{ reservationId: string; guestName: string; roomTypeName: string; status: string; assignedRoomNumbers: string[] }>;
  roomsNeedingCleaning: Array<{ physicalRoomId: string; roomNumber: string; roomTypeName: string; housekeepingStatus: string }>;
};

type SessionResponse = { token: string; staff: StaffPrincipal };

export class StaffApiError extends Error {
  constructor(message: string) {
    super(message);
  }
}

export async function loginStaff(email: string, password: string): Promise<SessionResponse> {
  const response = await fetch("/api/staff/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: "로그인 요청을 처리하지 못했습니다." }));
    throw new StaffApiError(error.message ?? "이메일 또는 비밀번호를 확인해 주세요.");
  }
  return response.json() as Promise<SessionResponse>;
}

export async function hasActiveStaffSession(token: string): Promise<boolean> {
  const response = await fetch("/api/staff/me", { headers: { "X-Staff-Session": token } });
  return response.ok;
}

export async function logoutStaff(token: string): Promise<void> {
  await fetch("/api/staff/sessions/current", {
    method: "DELETE",
    headers: { "X-Staff-Session": token },
  });
}

export async function getDailyOperations(token: string, hotelId: string, date: string): Promise<DailyOperationsView> {
  const response = await fetch(`/api/staff/hotels/${hotelId}/operations?date=${date}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    throw new StaffApiError("\uB2F9\uC77C \uC6B4\uC601 \uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.");
  }
  return response.json() as Promise<DailyOperationsView>;
}
