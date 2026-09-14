import { Suspense } from "react";
import { ReservationManagement } from "@/components/hotel-admin/reservation-management";

export default function ReservationsPage() {
  return (
    <Suspense fallback={null}>
      <ReservationManagement />
    </Suspense>
  );
}
