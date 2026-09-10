"use client";

import { useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { getDailyOperations, type DailyOperationsView, type StaffPrincipal } from "@/lib/staff-api";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "\uC18D\uCD08 \uC9C0\uC810" },
  { id: "11000000-0000-0000-0000-000000000002", name: "\uC124\uC545\uC0B0 \uC9C0\uC810" },
  { id: "11000000-0000-0000-0000-000000000003", name: "\uC81C\uC8FC \uC9C0\uC810" },
];

const today = () => new Date().toISOString().slice(0, 10);

export function DailyOperations() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [hotelId, setHotelId] = useState<string | null>(null);
  const [date, setDate] = useState(today);
  const [data, setData] = useState<DailyOperationsView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      const principal = JSON.parse(stored) as StaffPrincipal;
      setStaff(principal);
      setHotelId(principal.hotelId ?? hotels[0].id);
    } catch {
      setError("\uC9C1\uC6D0 \uC815\uBCF4\uB97C \uC77D\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.");
    }
  }, []);

  useEffect(() => {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token || !hotelId) return;
    setLoading(true);
    setError(null);
    void getDailyOperations(token, hotelId, date)
      .then(setData)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "\uC870\uD68C\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4."))
      .finally(() => setLoading(false));
  }, [date, hotelId]);

  const availableHotels = useMemo(
    () => staff?.role === "BRANCH_STAFF" ? hotels.filter((hotel) => hotel.id === staff.hotelId) : hotels,
    [staff],
  );
  const hotelName = hotels.find((hotel) => hotel.id === hotelId)?.name ?? "\uC9C0\uC810";

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{"\uB2F9\uC77C \uC6B4\uC601"}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{"\uB3C4\uCC29\u00B7\uCD9C\uBC1C \uC608\uC57D\uACFC \uCCAD\uC18C \uD544\uC694 \uAC1D\uC2E4\uC744 \uC9C0\uC810\uBCC4\uB85C \uD655\uC778\uD569\uB2C8\uB2E4."}</p>
        </div>
        <label className="text-sm font-medium">
          {"\uAE30\uC900 \uB0A0\uC9DC"}
          <input aria-label="\uAE30\uC900 \uB0A0\uC9DC" className="mt-1 block h-8 rounded-lg border bg-background px-2" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </label>
      </div>

      <div className="flex flex-wrap gap-2" aria-label="\uC9C0\uC810 \uC120\uD0DD">
        {availableHotels.map((hotel) => <Button key={hotel.id} variant={hotel.id === hotelId ? "default" : "outline"} onClick={() => setHotelId(hotel.id)}>{hotel.name}</Button>)}
      </div>

      {error && <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
      {loading && <p className="text-sm text-muted-foreground">{"\uB2F9\uC77C \uC6B4\uC601 \uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uB294 \uC911\uC785\uB2C8\uB2E4."}</p>}

      <div className="grid gap-4 lg:grid-cols-3">
        <OperationsCard title="\uB3C4\uCC29 \uC608\uC815" subtitle={hotelName} items={data?.arrivals ?? []} />
        <OperationsCard title="\uCD9C\uBC1C \uC608\uC815" subtitle={hotelName} items={data?.departures ?? []} />
        <OperationsCard title="\uCCAD\uC18C \uD544\uC694 \uAC1D\uC2E4" subtitle={hotelName} items={data?.roomsNeedingCleaning ?? []} roomList />
      </div>
    </div>
  );
}

type OperationsItem = {
  physicalRoomId?: string;
  reservationId?: string;
  roomNumber?: string;
  guestName?: string;
  roomTypeName: string;
  assignedRoomNumbers?: string[];
};

function OperationsCard({ title, subtitle, items, roomList = false }: { title: string; subtitle: string; items: OperationsItem[]; roomList?: boolean }) {
  return <Card><CardHeader><CardTitle>{title}</CardTitle><CardDescription>{subtitle}</CardDescription></CardHeader><CardContent><ul className="space-y-3">{items.length === 0 ? <li className="text-sm text-muted-foreground">{"\uD574\uB2F9 \uB0B4\uC5ED\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."}</li> : items.map((item) => <li key={roomList ? item.physicalRoomId : item.reservationId} className="rounded-lg bg-muted/60 p-3"><p className="font-medium">{roomList ? item.roomNumber : item.guestName}</p><p className="mt-1 text-xs text-muted-foreground">{roomList ? item.roomTypeName : `${item.roomTypeName}${item.assignedRoomNumbers?.length ? ` · ${item.assignedRoomNumbers.join(", ")}` : ""}`}</p></li>)}</ul></CardContent></Card>;
}
