export type StaffPrincipal = {

  id: string;

  email: string;

  displayName: string;

  role: "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";

  hotelId: string | null;

};



export type DailyOperationsView = {
  hotelId: string;

  date: string;

  arrivals: Array<{ reservationId: string; guestName: string; roomTypeName: string; status: string; assignedRoomNumbers: string[] }>;

  departures: Array<{ reservationId: string; guestName: string; roomTypeName: string; status: string; assignedRoomNumbers: string[] }>;

  roomsNeedingCleaning: Array<{ physicalRoomId: string; roomNumber: string; roomTypeName: string; housekeepingStatus: string }>;

};

export type AssignableRoom = { id: string; roomNumber: string };

export type RoomReassignmentOptions = {
  reservationId: string;
  assignments: AssignableRoom[];
  candidates: AssignableRoom[];
};

export type RoomReassignmentResult = {
  reservationId: string;
  previousPhysicalRoomId: string;
  previousRoomNumber: string;
  physicalRoomId: string;
  roomNumber: string;
};

export type RoomOperationalStatus = "AVAILABLE" | "INSPECTION_REQUIRED" | "OUT_OF_SERVICE";

export type RoomOperationsView = {
  hotelId: string;
  summary: { inspectionRequired: number; outOfService: number; overdueRecovery: number };
  rooms: Array<{
    physicalRoomId: string;
    roomNumber: string;
    roomTypeName: string;
    housekeepingStatus: "CLEAN" | "NEEDS_CLEANING";
    operationalStatus: RoomOperationalStatus;
    operationalReason: string | null;
    expectedRecoveryAt: string | null;
    operationalVersion: number;
    impactedAssignments: Array<{
      reservationId: string;
      guestName: string;
      status: string;
      checkIn: string;
      checkOut: string;
    }>;
    events: Array<{
      id: string;
      previousStatus: RoomOperationalStatus;
      status: RoomOperationalStatus;
      reason: string;
      expectedRecoveryAt: string | null;
      staffId: string;
      createdAt: string;
    }>;
  }>;
};

export type RoomOperationalTransitionResult = {
  physicalRoomId: string;
  roomNumber: string;
  housekeepingStatus: "CLEAN" | "NEEDS_CLEANING";
  operationalStatus: RoomOperationalStatus;
  operationalReason: string | null;
  expectedRecoveryAt: string | null;
  operationalVersion: number;
};

export type CheckedInRoomMoveOptions = {
  reservationId: string;
  assignments: AssignableRoom[];
  candidates: AssignableRoom[];
};

export type CheckedInRoomMoveResult = RoomReassignmentResult & {
  reason: string;
  movedAt: string;
};

export type StaffReservationSummary = {
  reservationId: string;
  guestName: string;
  guestEmail: string;
  roomTypeName: string;
  ratePlanName: string;
  checkIn: string;
  checkOut: string;
  adults: number;
  children: number;
  rooms: number;
  status: string;
  totalKrw: number;
  currency: string;
  assignedRoomNumbers: string[];
};

export type StaffReservationSearchView = {
  hotelId: string;
  date: string;
  truncated: boolean;
  reservations: StaffReservationSummary[];
};

export type StaffCancellationPreview = {
  reservationId: string;
  status: string;
  cancellable: boolean;
  refundAmount: number;
  currency: string;
  cutoffAt: string;
  unavailableReason: string | null;
};

export type StaffCancellationResult = {
  reservationId: string;
  status: string;
  refundAmount: number;
  currency: string;
};

export type StaffReservationGuestUpdateResult = {
  reservationId: string;
  guestName: string;
  guestEmail: string;
};

export type StaffReservationPartyUpdateResult = {
  reservationId: string;
  adults: number;
  children: number;
};

export type StaffReservationStayChangeOffer = {
  roomTypeId: string;
  roomTypeName: string;
  ratePlanId: string;
  ratePlanName: string;
  breakfastIncluded: boolean;
  remaining: number;
  nightlyPrices: Array<{ date: string; amount: number }>;
  totalKrw: number;
  differenceKrw: number;
  currency: string;
};

export type StaffReservationStayChangePreview = {
  reservationId: string;
  checkIn: string;
  checkOut: string;
  currentTotalKrw: number;
  currency: string;
  offers: StaffReservationStayChangeOffer[];
};

export type StaffReservationStayChangeResult = {
  reservationId: string;
  checkIn: string;
  checkOut: string;
  roomTypeId: string;
  ratePlanId: string;
  totalKrw: number;
  differenceKrw: number;
  currency: string;
};

export type ReservationChangeStatus =
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "AWAITING_PAYMENT"
  | "REFUND_PENDING"
  | "READY_TO_APPLY"
  | "APPLYING"
  | "COMPLETED"
  | "REJECTED"
  | "CANCELLED"
  | "EXPIRED"
  | "RECONCILIATION_REQUIRED";

export type ReservationChangePolicy = {
  settlementEnabled: boolean;
  directLimitKrw: number;
  approvalTtlSeconds: number;
  holdTtlSeconds: number;
};

export type ReservationChangeQuote = {
  id: string;
  revision: number;
  previousTotalKrw: number;
  totalKrw: number;
  differenceKrw: number;
  currency: string;
  nightlyPrices: Array<{ date: string; amount: number }>;
  createdAt: string;
};

export type ReservationChangeApproval = {
  id: string;
  decisionType: string;
  decidedBy: string | null;
  decidedRole: string;
  limitKrw: number;
  reason: string | null;
  createdAt: string;
};

export type ReservationChangeEvent = {
  id: string;
  eventType: string;
  fromStatus: ReservationChangeStatus | null;
  toStatus: ReservationChangeStatus;
  actorStaffId: string | null;
  reason: string | null;
  createdAt: string;
};

export type ReservationChangeRequestView = {
  id: string;
  reservationId: string;
  hotelId: string;
  hotelName?: string;
  guestName?: string;
  status: ReservationChangeStatus;
  settlementDirection: "CHARGE" | "REFUND" | "NONE";
  version: number;
  previousCheckIn: string;
  previousCheckOut: string;
  previousRoomTypeId: string;
  previousRoomTypeName?: string;
  previousRatePlanId: string;
  previousRatePlanName?: string;
  targetCheckIn: string;
  targetCheckOut: string;
  targetRoomTypeId: string;
  targetRoomTypeName?: string;
  targetRatePlanId: string;
  targetRatePlanName?: string;
  rooms: number;
  adults: number;
  children: number;
  approvalExpiresAt: string;
  quote: ReservationChangeQuote;
  approval: ReservationChangeApproval | null;
  actions: string[];
  events: ReservationChangeEvent[];
};

export type ReservationChangePaymentLinkView = {
  requestId: string;
  status: ReservationChangeStatus;
  version: number;
  customerUrl: string;
  createdAt: string;
  expiresAt: string;
};


type SessionResponse = { token: string; staff: StaffPrincipal };



export class StaffApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
    readonly code?: string,
    readonly details?: { assignments?: RoomOperationsView["rooms"][number]["impactedAssignments"] },
  ) {
    super(message);
    this.name = "StaffApiError";
  }
}

type ApiErrorPayload = { message?: string; code?: string };

type RoomOperationsErrorPayload = ApiErrorPayload & {
  assignments?: RoomOperationsView["rooms"][number]["impactedAssignments"];
};


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

export async function completeStaffOperation(token: string, path: string): Promise<void> {
  const response = await fetch(path, { method: "POST", headers: { "X-Staff-Session": token } });
  if (!response.ok) throw new StaffApiError("운영 처리에 실패했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.");
}

export async function getAssignableRooms(token: string, reservationId: string): Promise<AssignableRoom[]> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/assignable-rooms`, { headers: { "X-Staff-Session": token } });
  if (!response.ok) throw new StaffApiError("배정 가능한 객실을 불러오지 못했습니다.");
  return response.json() as Promise<AssignableRoom[]>;
}

export async function assignRoom(token: string, reservationId: string, physicalRoomId: string): Promise<void> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/assignments`, {
    method: "POST", headers: { "Content-Type": "application/json", "X-Staff-Session": token }, body: JSON.stringify({ physicalRoomId }),
  });
  if (!response.ok) throw new StaffApiError("객실 배정에 실패했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.");
}
export type WebsitePageMetadata = {
  slug: string;
  path: string;
  menuLabel: string;
  menuVisible: boolean;
  menuOrder: number;
};
export type WebsitePageDraftMetadata = Omit<WebsitePageMetadata, "path">;
export type WebsitePageTreeItem = {
  id: string;
  hotelId: string | null;
  pageType: string;
  label: string;
  draftPath: string;
  publishedPath: string;
  status: "DRAFT" | "PUBLISHED" | "CHANGED_AFTER_PUBLISH";
  lifecycleStatus: "ACTIVE" | "ARCHIVED";
  lifecycleVersion: number;
  children: WebsitePageTreeItem[];
};
export type ContentKind = "HOME" | "DESTINATION" | "ROOM" | "DINING" | "FACILITY" | "EXPERIENCE" | "PROMOTION" | "GUIDE" | "BRAND";
export type WebsitePageConnections = {
  roomTypeIds: string[];
  targetHotelIds: string[];
  relatedPages: Array<{ targetPageId: string; relationType: "RELATED" | "MANUAL_CARD"; displayOrder: number }>;
};
export type ContentReferenceCatalog = {
  hotels: Array<{ id: string; name: string; region: string; roomTypes: Array<{ id: string; name: string; maxOccupancy: number }> }>;
  pages: Array<{ id: string; contentKind: ContentKind; hotelId: string | null; title: string; path: string }>;
};
export type WebContentDocument = {
  draftContent: Record<string, unknown>;
  draftVersion: number;
  publishedContent: Record<string, unknown>;
  publishedVersion: number;
  draftPage?: WebsitePageMetadata | null;
  publishedPage?: WebsitePageMetadata | null;
};
export type WebContentVersion = { version: number; publishedAt: string };
export type WebsitePageVersionSnapshot = {
  version: number;
  publishedAt: string;
  metadata: WebsitePageMetadata;
  publishedFromDraftVersion: number | null;
  content: Record<string, unknown>;
};
export type WebsitePageVersionComparison = {
  pageId: string;
  base: WebsitePageVersionSnapshot;
  compare: WebsitePageVersionSnapshot;
};
export type WebsitePageDocument = {
  id: string;
  pageType: "CONTENT_PAGE" | "HOME_PAGE" | "HOTEL_LANDING";
  hotelId: string | null;
  contentKind: ContentKind;
  draftConnections: WebsitePageConnections;
  draftContent: Record<string, unknown>;
  draftVersion: number;
  draftMetadata: WebsitePageMetadata;
  publishedContent: Record<string, unknown>;
  publishedVersion: number;
  publishedMetadata: WebsitePageMetadata | null;
  lifecycleStatus: "ACTIVE" | "ARCHIVED";
  lifecycleVersion: number;
};
export type WebsiteTranslationReviewStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "PUBLISHED";
export type WebsiteTranslationReviewEvent = {
  id: number;
  action: "REVIEW_REQUESTED" | "APPROVED" | "REJECTED" | "APPROVAL_INVALIDATED" | "PUBLISHED";
  draftVersion: number;
  actorId: string | null;
  actorDisplayName: string | null;
  createdAt: string;
  comment: string | null;
};
export type WebsiteTranslationReviewState = {
  status: WebsiteTranslationReviewStatus;
  reviewedDraftVersion: number | null;
  events: WebsiteTranslationReviewEvent[];
};
export type WebsiteHomeDocument = Omit<WebsitePageDocument, "pageType" | "hotelId"> & {
  pageType: "HOME_PAGE";
  hotelId: null;
};
export type SaveWebsiteHomeInput = {
  expectedDraftVersion: number;
  content: Record<string, unknown>;
};
export type CreateWebsitePageInput = {
  parentId: string;
  contentKind: Exclude<ContentKind, "HOME" | "DESTINATION">;
  hotelId: string | null;
  connections: WebsitePageConnections;
  page: WebsitePageDraftMetadata;
  content: Record<string, unknown>;
};
export type SaveWebsitePageInput = {
  expectedDraftVersion: number;
  page: WebsitePageDraftMetadata;
  content: Record<string, unknown>;
  connections?: WebsitePageConnections;
};
export type WebsitePageLifecycleInput = {
  expectedLifecycleVersion: number;
  expectedDraftVersion: number;
  expectedPublishedVersion: number;
};
export type WebsitePageMoveInput = {
  parentId: string;
  slug: string;
  expectedDraftVersion: number;
  expectedLifecycleVersion: number;
  expectedPublishedVersion: number;
};
export type WebsitePageMoveImpactItem = {
  pageId: string; currentDraftPath: string; nextDraftPath: string;
  currentPublishedPath: string | null; nextPublishedPath: string | null;
  depth: number; published: boolean;
};
export type WebsitePageMoveImpact = { pageId: string; newParentId: string; newRootDraftPath: string; items: WebsitePageMoveImpactItem[] };
export type WebsiteMediaVariant = {
  id: string;
  format: "WEBP";
  targetWidth: 640 | 1280;
  status: "PENDING" | "PROCESSING" | "READY" | "FAILED";
  deliveryUrl: string | null;
  mimeType: "image/webp" | null;
  byteSize: number | null;
  width: number | null;
  height: number | null;
  attemptCount: number;
  lastError: string | null;
  updatedAt: string;
};
export type WebsiteMediaAsset = {
  id: string;
  displayName: string;
  deliveryUrl: string;
  mimeType: string;
  byteSize: number;
  width: number;
  height: number;
  defaultAltText: string;
  usageCount: number;
  status: "ACTIVE" | "ARCHIVED";
  version: number;
  archivedAt: string | null;
  permanentDeleteAvailableAt: string | null;
  variants: WebsiteMediaVariant[];
};
export type WebsiteMediaUsage = {
  locale?: "ko" | "en";
  pageId: string;
  pageLabel: string;
  pagePath: string;
  pageType: string;
  documentState: "DRAFT" | "PUBLISHED";
  fieldPath: string;
  altText: string;
};
export type WebsiteMediaStoreAudit = {
  storeName: string;
  healthy: boolean;
  missingStorageKeys: string[];
  orphanStorageKeys: string[];
  staleTemporaryStorageKeys: string[];
};
export type WebsiteMediaStorageAudit = {
  checkedAt: string;
  healthy: boolean;
  missingStorageKeys: string[];
  orphanStorageKeys: string[];
  staleTemporaryStorageKeys: string[];
  mode: "local" | "mirror" | "s3-primary";
  stores: WebsiteMediaStoreAudit[];
};
export type WebsiteMediaStorageMigrationStatus = {
  mode: "local" | "mirror" | "s3-primary";
  total: number;
  both: number;
  localOnly: number;
  s3Only: number;
  mismatch: number;
  missing: number;
  fallbackCount: number;
};
export type WebsiteMediaStorageBackfillResult = {
  examined: number;
  copied: number;
  skipped: number;
  mismatch: number;
  failed: number;
  failedStorageKeys: string[];
};
export type UploadWebsiteMediaInput = {
  file: File;
  displayName: string;
  defaultAltText: string;
};
export type WebsiteMediaMetadataInput = {
  displayName: string;
  defaultAltText: string;
  expectedVersion: number;
};
export type WebsiteMediaVersionInput = { expectedVersion: number };
export type WebsiteMediaDraftReplacementUsage = {
  pageId: string;
  pageLabel: string;
  pagePath: string;
  pageType: string;
  locale: "ko" | "en";
  fieldPath: string;
  expectedDraftVersion: number;
};
export type WebsiteMediaDraftReplacementImpact = {
  sourceAsset: WebsiteMediaAsset;
  targetAsset: WebsiteMediaAsset;
  replaceableUsages: WebsiteMediaDraftReplacementUsage[];
  publishedUsageCount: number;
  archivedDraftUsageCount: number;
};
export type ReplaceWebsiteMediaDraftUsagesInput = {
  targetMediaId: string;
  expectedSourceVersion: number;
  expectedTargetVersion: number;
  targets: Pick<WebsiteMediaDraftReplacementUsage, "pageId" | "locale" | "fieldPath" | "expectedDraftVersion">[];
};
export type WebsiteMediaDraftReplacementResult = {
  sourceMediaId: string;
  targetMediaId: string;
  replacedUsageCount: number;
  changedDraftCount: number;
};

async function contentRequest<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...init, headers: { "X-Staff-Session": token, "Content-Type": "application/json", ...init?.headers } });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "웹사이트 콘텐츠 요청을 처리하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<T>;
}

async function mediaRequest<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...init, headers: { "X-Staff-Session": token, ...init?.headers } });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "미디어 요청을 처리하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<T>;
}

async function operationsRequest<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "X-Staff-Session": token,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as RoomOperationsErrorPayload;
    throw new StaffApiError(
      error.message ?? "객실 운영 요청을 처리하지 못했습니다.",
      response.status,
      error.code,
      { assignments: error.assignments ?? [] },
    );
  }
  return response.json() as Promise<T>;
}

export function getRoomOperations(token: string, hotelId: string) {
  return operationsRequest<RoomOperationsView>(`/api/staff/hotels/${hotelId}/room-operations`, token);
}

export function transitionRoomOperationalStatus(
  token: string,
  roomId: string,
  idempotencyKey: string,
  input: {
    targetStatus: RoomOperationalStatus;
    reason: string;
    expectedRecoveryAt: string | null;
    expectedVersion: number;
  },
) {
  return operationsRequest<RoomOperationalTransitionResult>(
    `/api/staff/rooms/${roomId}/operational-transitions`,
    token,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

export function getCheckedInRoomMoveOptions(token: string, reservationId: string) {
  return operationsRequest<CheckedInRoomMoveOptions>(
    `/api/staff/reservations/${reservationId}/checked-in-room-move-options`,
    token,
  );
}

export function moveCheckedInRoom(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  input: { currentPhysicalRoomId: string; newPhysicalRoomId: string; reason: string },
) {
  return operationsRequest<CheckedInRoomMoveResult>(
    `/api/staff/reservations/${reservationId}/checked-in-room-moves`,
    token,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

export async function getRoomReassignmentOptions(
  token: string,
  reservationId: string,
): Promise<RoomReassignmentOptions> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/room-reassignment-options`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "객실 변경 후보를 불러오지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<RoomReassignmentOptions>;
}

export async function reassignRoom(
  token: string,
  reservationId: string,
  currentPhysicalRoomId: string,
  newPhysicalRoomId: string,
  idempotencyKey: string,
): Promise<RoomReassignmentResult> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/assignments/${currentPhysicalRoomId}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ newPhysicalRoomId }),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "배정 객실을 변경하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<RoomReassignmentResult>;
}

export async function getStaffReservations(
  token: string,
  hotelId: string,
  date: string,
  filters: { query?: string; status?: string } = {},
): Promise<StaffReservationSearchView> {
  const searchParams = new URLSearchParams({ date });
  if (filters.query?.trim()) searchParams.set("query", filters.query.trim());
  if (filters.status?.trim()) searchParams.set("status", filters.status.trim());
  const response = await fetch(`/api/staff/hotels/${hotelId}/reservations?${searchParams}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) throw new StaffApiError("예약 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
  return response.json() as Promise<StaffReservationSearchView>;
}

export async function getStaffCancellationPreview(token: string, reservationId: string): Promise<StaffCancellationPreview> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/cancellation-preview`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "예약 취소 조건을 확인하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<StaffCancellationPreview>;
}

export async function cancelStaffReservation(
  token: string,
  reservationId: string,
  idempotencyKey: string,
): Promise<StaffCancellationResult> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/cancel`, {
    method: "POST",
    headers: { "X-Staff-Session": token, "Idempotency-Key": idempotencyKey },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "예약을 취소하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<StaffCancellationResult>;
}

export async function updateStaffReservationGuest(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  guest: { guestName: string; guestEmail: string },
): Promise<StaffReservationGuestUpdateResult> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/guest`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(guest),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "예약자 정보를 수정하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<StaffReservationGuestUpdateResult>;
}

export async function updateStaffReservationParty(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  party: { adults: number; children: number },
): Promise<StaffReservationPartyUpdateResult> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/party`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(party),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "투숙 인원을 변경하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<StaffReservationPartyUpdateResult>;
}

export async function previewStaffReservationStayChange(
  token: string,
  reservationId: string,
  stay: { checkIn: string; checkOut: string },
): Promise<StaffReservationStayChangePreview> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/stay-change-preview`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Staff-Session": token },
    body: JSON.stringify(stay),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "변경 가능한 숙박 조건을 조회하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<StaffReservationStayChangePreview>;
}

export async function updateStaffReservationStay(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  stay: {
    checkIn: string;
    checkOut: string;
    roomTypeId: string;
    ratePlanId: string;
    expectedTotal: number;
  },
): Promise<StaffReservationStayChangeResult> {
  const response = await fetch(`/api/staff/reservations/${reservationId}/stay`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(stay),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "숙박 조건을 변경하지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<StaffReservationStayChangeResult>;
}

async function reservationChangeRequest<T>(
  path: string,
  token: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "X-Staff-Session": token,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "예약 변경 요청을 처리하지 못했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<T>;
}

export function getReservationChangePolicy(token: string) {
  return reservationChangeRequest<ReservationChangePolicy>("/api/staff/reservation-change-policy", token);
}

export function getReservationChangeRequests(
  token: string,
  filters: { status?: ReservationChangeStatus; hotelId?: string } = {},
) {
  const searchParams = new URLSearchParams();
  if (filters.status) searchParams.set("status", filters.status);
  if (filters.hotelId) searchParams.set("hotelId", filters.hotelId);
  const query = searchParams.size ? `?${searchParams}` : "";
  return reservationChangeRequest<ReservationChangeRequestView[]>(`/api/staff/reservation-change-requests${query}`, token);
}

export function getReservationChangeRequest(token: string, requestId: string) {
  return reservationChangeRequest<ReservationChangeRequestView>(`/api/staff/reservation-change-requests/${requestId}`, token);
}

export function createReservationChangeRequest(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  input: { checkIn: string; checkOut: string; roomTypeId: string; ratePlanId: string; expectedTotal: number },
) {
  return reservationChangeRequest<ReservationChangeRequestView>(`/api/staff/reservations/${reservationId}/change-requests`, token, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(input),
  });
}

function mutateReservationChangeRequest(
  token: string,
  requestId: string,
  action: "approve" | "reject" | "reprice" | "cancel",
  idempotencyKey: string,
  input: { version: number; reason?: string },
) {
  return reservationChangeRequest<ReservationChangeRequestView>(`/api/staff/reservation-change-requests/${requestId}/${action}`, token, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(input),
  });
}

export function approveReservationChangeRequest(token: string, requestId: string, idempotencyKey: string, version: number) {
  return mutateReservationChangeRequest(token, requestId, "approve", idempotencyKey, { version });
}

export function rejectReservationChangeRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  input: { version: number; reason: string },
) {
  return mutateReservationChangeRequest(token, requestId, "reject", idempotencyKey, input);
}

export function repriceReservationChangeRequest(token: string, requestId: string, idempotencyKey: string, version: number) {
  return mutateReservationChangeRequest(token, requestId, "reprice", idempotencyKey, { version });
}

export function cancelReservationChangeRequest(token: string, requestId: string, idempotencyKey: string, version: number) {
  return mutateReservationChangeRequest(token, requestId, "cancel", idempotencyKey, { version });
}

export function createReservationChangePaymentLink(
  token: string,
  requestId: string,
  idempotencyKey: string,
  input: { version: number; publicToken: string },
) {
  return reservationChangeRequest<ReservationChangePaymentLinkView>(
    `/api/staff/reservation-change-requests/${requestId}/payment-link`,
    token,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(input) },
  );
}

export function startReservationChangeRefund(
  token: string,
  requestId: string,
  idempotencyKey: string,
  version: number,
) {
  return reservationChangeRequest<ReservationChangeRequestView>(
    `/api/staff/reservation-change-requests/${requestId}/refund`,
    token,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify({ version }) },
  );
}

export function reconcileReservationChangeRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  input: { action: string; version: number; reason: string },
) {
  return reservationChangeRequest<ReservationChangeRequestView>(
    `/api/staff/reservation-change-requests/${requestId}/reconcile`,
    token,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(input) },
  );
}

type WebsiteMediaAssetResponse = Omit<WebsiteMediaAsset, "variants"> & { variants?: WebsiteMediaVariant[] };

function normalizeWebsiteMediaAsset(asset: WebsiteMediaAssetResponse): WebsiteMediaAsset {
  return { ...asset, variants: asset.variants ?? [] };
}

async function mediaAssetRequest(path: string, token: string, init?: RequestInit): Promise<WebsiteMediaAsset> {
  return normalizeWebsiteMediaAsset(await mediaRequest<WebsiteMediaAssetResponse>(path, token, init));
}

export type WebsitePreviewGrantResponse = { grantId: string; previewToken: string; previewPath: string; expiresAt: string };
export function issueWebsitePreviewGrant(token: string, pageId: string, input: { locale: "ko" | "en"; expectedDraftVersion: number }) {
  return contentRequest<WebsitePreviewGrantResponse>(`/api/staff/website/pages/${pageId}/preview-grants`, token,
    { method: "POST", body: JSON.stringify(input) });
}
export async function revokeWebsitePreviewGrant(token: string, grantId: string): Promise<void> {
  const response = await fetch(`/api/staff/website/preview-grants/${grantId}`, { method: "DELETE", headers: { "X-Staff-Session": token } });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "미리보기 링크를 폐기하지 못했습니다.", response.status, error.code);
  }
}

export function getWebContent(token: string, hotelId: string) { return contentRequest<WebContentDocument>(`/api/staff/web-content/hotels/${hotelId}`, token); }
export function saveWebContent(token: string, hotelId: string, expectedDraftVersion: number, content: Record<string, unknown>, page?: WebsitePageDraftMetadata) {
  return contentRequest<WebContentDocument>(`/api/staff/web-content/hotels/${hotelId}`, token, {
    method: "PUT",
    body: JSON.stringify({ expectedDraftVersion, content, ...(page ? { page } : {}) }),
  });
}
export function publishWebContent(token: string, hotelId: string, expectedDraftVersion: number, expectedPublishedVersion: number) { return contentRequest<WebContentDocument>(`/api/staff/web-content/hotels/${hotelId}/publish`, token, { method: "POST", body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }) }); }
export function getWebContentVersions(token: string, hotelId: string) { return contentRequest<WebContentVersion[]>(`/api/staff/web-content/hotels/${hotelId}/versions`, token); }
export function getWebsiteHome(token: string) {
  return contentRequest<WebsiteHomeDocument>("/api/staff/website/home", token);
}
export function saveWebsiteHome(token: string, input: SaveWebsiteHomeInput) {
  return contentRequest<WebsiteHomeDocument>("/api/staff/website/home", token, { method: "PUT", body: JSON.stringify(input) });
}
export function publishWebsiteHome(token: string, expectedDraftVersion: number, expectedPublishedVersion: number) {
  return contentRequest<WebsiteHomeDocument>("/api/staff/website/home/publish", token, {
    method: "POST", body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
  });
}
export function getWebsiteHomeVersions(token: string) {
  return contentRequest<WebContentVersion[]>("/api/staff/website/home/versions", token);
}
export async function getWebsiteMedia(token: string, includeArchived = false): Promise<WebsiteMediaAsset[]> {
  const assets = await mediaRequest<WebsiteMediaAssetResponse[]>(`/api/staff/website/media${includeArchived ? "?includeArchived=true" : ""}`, token);
  return assets.map(normalizeWebsiteMediaAsset);
}
export async function getWebsiteMediaStorageAudit(token: string): Promise<WebsiteMediaStorageAudit> {
  type AuditResponse = Omit<WebsiteMediaStorageAudit, "mode" | "stores"> & {
    mode?: WebsiteMediaStorageAudit["mode"];
    stores?: WebsiteMediaStoreAudit[];
  };
  const result = await mediaRequest<AuditResponse>("/api/staff/website/media/storage-audit", token);
  const local = {
    storeName: "local",
    healthy: result.healthy,
    missingStorageKeys: result.missingStorageKeys,
    orphanStorageKeys: result.orphanStorageKeys,
    staleTemporaryStorageKeys: result.staleTemporaryStorageKeys,
  };
  return { ...result, mode: result.mode ?? "local", stores: result.stores ?? [local] };
}
export function getWebsiteMediaStorageMigration(token: string) {
  return mediaRequest<WebsiteMediaStorageMigrationStatus>("/api/staff/website/media/storage-migration", token);
}
export function backfillWebsiteMediaStorage(token: string) {
  return mediaRequest<WebsiteMediaStorageBackfillResult>(
    "/api/staff/website/media/storage-migration/backfill", token, { method: "POST" },
  );
}
export function uploadWebsiteMedia(token: string, input: UploadWebsiteMediaInput) {
  const formData = new FormData();
  formData.append("file", input.file);
  formData.append("displayName", input.displayName);
  formData.append("defaultAltText", input.defaultAltText);
  return mediaAssetRequest("/api/staff/website/media", token, { method: "POST", body: formData });
}
export function getWebsiteMediaUsages(token: string, mediaId: string) {
  return mediaRequest<WebsiteMediaUsage[]>(`/api/staff/website/media/${mediaId}/usages`, token);
}
export function retryWebsiteMediaVariant(token: string, mediaId: string, targetWidth: 640 | 1280) {
  return mediaAssetRequest(`/api/staff/website/media/${mediaId}/variants/${targetWidth}/retry`, token, { method: "POST" });
}
export async function getWebsiteMediaDraftReplacementImpact(token: string, sourceMediaId: string, targetMediaId: string): Promise<WebsiteMediaDraftReplacementImpact> {
  const query = new URLSearchParams({ targetMediaId });
  const impact = await mediaRequest<Omit<WebsiteMediaDraftReplacementImpact, "sourceAsset" | "targetAsset"> & {
    sourceAsset: WebsiteMediaAssetResponse; targetAsset: WebsiteMediaAssetResponse;
  }>(`/api/staff/website/media/${sourceMediaId}/draft-replacement-impact?${query}`, token);
  return { ...impact, sourceAsset: normalizeWebsiteMediaAsset(impact.sourceAsset), targetAsset: normalizeWebsiteMediaAsset(impact.targetAsset) };
}
export function replaceWebsiteMediaDraftUsages(token: string, sourceMediaId: string, input: ReplaceWebsiteMediaDraftUsagesInput) {
  return mediaRequest<WebsiteMediaDraftReplacementResult>(`/api/staff/website/media/${sourceMediaId}/draft-replacements`, token, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
  });
}
export function updateWebsiteMedia(token: string, mediaId: string, input: WebsiteMediaMetadataInput) {
  return mediaAssetRequest(`/api/staff/website/media/${mediaId}`, token, {
    method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
  });
}
export function archiveWebsiteMedia(token: string, mediaId: string, input: WebsiteMediaVersionInput) {
  return mediaAssetRequest(`/api/staff/website/media/${mediaId}/archive`, token, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
  });
}
export function restoreWebsiteMedia(token: string, mediaId: string, input: WebsiteMediaVersionInput) {
  return mediaAssetRequest(`/api/staff/website/media/${mediaId}/restore`, token, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
  });
}
export async function deleteWebsiteMedia(token: string, mediaId: string, input: WebsiteMediaVersionInput): Promise<void> {
  const response = await fetch(`/api/staff/website/media/${mediaId}`, {
    method: "DELETE",
    headers: { "X-Staff-Session": token, "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "미디어를 영구 삭제하지 못했습니다.", response.status, error.code);
  }
}
export function getWebsitePageTree(token: string) { return contentRequest<WebsitePageTreeItem[]>("/api/staff/website/pages", token); }
export function getContentReferenceCatalog(token: string) { return contentRequest<ContentReferenceCatalog>("/api/staff/website/content-reference", token); }
export function createWebsitePage(token: string, input: CreateWebsitePageInput) {
  return contentRequest<WebsitePageDocument>("/api/staff/website/pages", token, { method: "POST", body: JSON.stringify(input) });
}
export function getWebsitePage(token: string, pageId: string) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}`, token);
}
export function getWebsiteTranslation(token: string, pageId: string) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/translations/en`, token);
}
export function getWebsiteTranslationReview(token: string, pageId: string) {
  return contentRequest<WebsiteTranslationReviewState>(`/api/staff/website/pages/${pageId}/translations/en/review`, token);
}
export function requestWebsiteTranslationReview(token: string, pageId: string, expectedDraftVersion: number, comment: string | null) {
  return contentRequest<WebsiteTranslationReviewState>(`/api/staff/website/pages/${pageId}/translations/en/review/request`, token, {
    method: "POST", body: JSON.stringify({ expectedDraftVersion, comment }),
  });
}
export function approveWebsiteTranslationReview(token: string, pageId: string, expectedDraftVersion: number, comment: string | null) {
  return contentRequest<WebsiteTranslationReviewState>(`/api/staff/website/pages/${pageId}/translations/en/review/approve`, token, {
    method: "POST", body: JSON.stringify({ expectedDraftVersion, comment }),
  });
}
export function rejectWebsiteTranslationReview(token: string, pageId: string, expectedDraftVersion: number, comment: string) {
  return contentRequest<WebsiteTranslationReviewState>(`/api/staff/website/pages/${pageId}/translations/en/review/reject`, token, {
    method: "POST", body: JSON.stringify({ expectedDraftVersion, comment }),
  });
}
export function initializeWebsiteTranslation(token: string, pageId: string, expectedSourceDraftVersion: number, expectedLifecycleVersion: number) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/translations/en`, token, {
    method: "POST", body: JSON.stringify({ expectedSourceDraftVersion, expectedLifecycleVersion }),
  });
}
export function saveWebsiteTranslation(token: string, pageId: string, input: SaveWebsitePageInput) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/translations/en`, token, { method: "PUT", body: JSON.stringify(input) });
}
export function publishWebsiteTranslation(token: string, pageId: string, expectedDraftVersion: number, expectedPublishedVersion: number) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/translations/en/publish`, token, {
    method: "POST", body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
  });
}
export function getWebsiteTranslationVersions(token: string, pageId: string) {
  return contentRequest<WebContentVersion[]>(`/api/staff/website/pages/${pageId}/translations/en/versions`, token);
}
export function getWebsitePageMoveImpact(token: string, pageId: string, parentId: string, slug: string) {
  return contentRequest<WebsitePageMoveImpact>(`/api/staff/website/pages/${pageId}/move-impact?parentId=${encodeURIComponent(parentId)}&slug=${encodeURIComponent(slug)}`, token);
}
export function moveWebsitePage(token: string, pageId: string, input: WebsitePageMoveInput) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/move`, token, { method: "POST", body: JSON.stringify(input) });
}
export function saveWebsitePage(token: string, pageId: string, input: SaveWebsitePageInput) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}`, token, { method: "PUT", body: JSON.stringify(input) });
}
export function publishWebsitePage(token: string, pageId: string, expectedDraftVersion: number, expectedPublishedVersion: number) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/publish`, token, {
    method: "POST", body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
  });
}
export function archiveWebsitePage(token: string, pageId: string, input: WebsitePageLifecycleInput) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/archive`, token, {
    method: "POST", body: JSON.stringify(input),
  });
}
export function restoreWebsitePage(token: string, pageId: string, input: WebsitePageLifecycleInput) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/restore`, token, {
    method: "POST", body: JSON.stringify(input),
  });
}
export async function deleteWebsitePage(token: string, pageId: string, input: WebsitePageLifecycleInput): Promise<void> {
  const response = await fetch(`/api/staff/website/pages/${pageId}`, {
    method: "DELETE",
    headers: { "X-Staff-Session": token, "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "페이지를 영구 삭제하지 못했습니다.", response.status, error.code);
  }
}
export function restoreWebsitePageVersionDraft(token: string, pageId: string, sourceVersion: number, input: WebsitePageLifecycleInput) {
  return contentRequest<WebsitePageDocument>(`/api/staff/website/pages/${pageId}/versions/${sourceVersion}/restore-draft`, token, {
    method: "POST", body: JSON.stringify(input),
  });
}
export function getWebsitePageVersions(token: string, pageId: string) {
  return contentRequest<WebContentVersion[]>(`/api/staff/website/pages/${pageId}/versions`, token);
}
export function getWebsitePageVersionComparison(token: string, pageId: string, baseVersion: number, compareVersion: number) {
  const query = new URLSearchParams({ baseVersion: String(baseVersion), compareVersion: String(compareVersion) });
  return contentRequest<WebsitePageVersionComparison>(`/api/staff/website/pages/${pageId}/versions/compare?${query}`, token);
}

export type SettlementRunStatus = "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED";

export type SettlementRunSummary = {
  id: string;
  provider: string;
  merchantAccount: string;
  soldDateFrom: string;
  soldDateTo: string;
  status: SettlementRunStatus;
  currentSoldDate: string;
  currentPage: number;
  pageSize: number;
  snapshotCount: number;
  matchedCount: number;
  mismatchCount: number;
  pendingCount: number;
  attemptCount: number;
  nextAttemptAt: string | null;
  errorCode: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type SettlementRunsView = {
  runs: SettlementRunSummary[];
  serverAt: string;
};

export type ReconciliationRow = {
  id: string;
  runId: string;
  reconciliationKey: string;
  snapshotId: string | null;
  paymentTransactionId: string | null;
  refundCommandId: string | null;
  status: string;
  expectedAmountKrw: number | null;
  providerAmountKrw: number | null;
  feeKrw: number | null;
  feeSupplyKrw: number | null;
  feeVatKrw: number | null;
  payoutKrw: number | null;
  detailCode: string | null;
  snapshotOrderId: string | null;
  snapshotPaymentKey: string | null;
  snapshotTransactionKey: string | null;
  snapshotMethod: string | null;
  snapshotCancellation: boolean | null;
  snapshotSoldDate: string | null;
  createdAt: string;
};

export type SettlementRunDetailView = {
  run: SettlementRunSummary | null;
  rows: ReconciliationRow[];
  serverAt: string;
};

export async function getSettlementRuns(token: string, limit = 20): Promise<SettlementRunsView> {
  const response = await fetch(`/api/staff/settlements/runs?limit=${limit}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "정산 실행 목록을 불러오지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<SettlementRunsView>;
}

export async function getSettlementRun(
  token: string,
  runId: string,
  filters: { status?: string; limit?: number } = {},
): Promise<SettlementRunDetailView> {
  const searchParams = new URLSearchParams();
  if (filters.status?.trim()) searchParams.set("status", filters.status.trim());
  if (filters.limit) searchParams.set("limit", String(filters.limit));
  const response = await fetch(`/api/staff/settlements/runs/${runId}?${searchParams}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as ApiErrorPayload;
    throw new StaffApiError(error.message ?? "정산 대사 내역을 불러오지 못했습니다.", response.status, error.code);
  }
  return response.json() as Promise<SettlementRunDetailView>;
}
