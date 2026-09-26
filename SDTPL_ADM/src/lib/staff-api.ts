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

  arrivals: Array<{
    reservationId: string;
    guestName: string;
    roomTypeName: string;
    status: string;
    assignedRoomNumbers: string[];
  }>;

  departures: Array<{
    reservationId: string;
    guestName: string;
    roomTypeName: string;
    status: string;
    assignedRoomNumbers: string[];
  }>;

  roomsNeedingCleaning: Array<{
    physicalRoomId: string;
    roomNumber: string;
    roomTypeName: string;
    housekeepingStatus: string;
  }>;
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

export type RoomOperationalStatus =
  "AVAILABLE" | "INSPECTION_REQUIRED" | "OUT_OF_SERVICE";

export type RoomOperationsView = {
  hotelId: string;
  summary: {
    inspectionRequired: number;
    outOfService: number;
    overdueRecovery: number;
  };
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
    readonly details?: {
      assignments?: RoomOperationsView["rooms"][number]["impactedAssignments"];
    },
  ) {
    super(message);
    this.name = "StaffApiError";
  }
}

type ApiErrorPayload = { message?: string; code?: string };

type RoomOperationsErrorPayload = ApiErrorPayload & {
  assignments?: RoomOperationsView["rooms"][number]["impactedAssignments"];
};

export async function loginStaff(
  email: string,
  password: string,
): Promise<SessionResponse> {
  const response = await fetch("/api/staff/sessions", {
    method: "POST",

    headers: { "Content-Type": "application/json" },

    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    const error = await response
      .json()
      .catch(() => ({ message: "로그인 요청을 처리하지 못했습니다." }));

    throw new StaffApiError(
      error.message ?? "이메일 또는 비밀번호를 확인해 주세요.",
    );
  }

  return response.json() as Promise<SessionResponse>;
}

export async function hasActiveStaffSession(token: string): Promise<boolean> {
  const response = await fetch("/api/staff/me", {
    headers: { "X-Staff-Session": token },
  });

  return response.ok;
}

export async function logoutStaff(token: string): Promise<void> {
  await fetch("/api/staff/sessions/current", {
    method: "DELETE",

    headers: { "X-Staff-Session": token },
  });
}

export async function getDailyOperations(
  token: string,
  hotelId: string,
  date: string,
): Promise<DailyOperationsView> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/operations?date=${date}`,
    {
      headers: { "X-Staff-Session": token },
    },
  );

  if (!response.ok) {
    throw new StaffApiError(
      "\uB2F9\uC77C \uC6B4\uC601 \uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.",
    );
  }

  return response.json() as Promise<DailyOperationsView>;
}

export async function completeStaffOperation(
  token: string,
  path: string,
): Promise<void> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok)
    throw new StaffApiError(
      "운영 처리에 실패했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.",
    );
}

export async function getAssignableRooms(
  token: string,
  reservationId: string,
): Promise<AssignableRoom[]> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/assignable-rooms`,
    { headers: { "X-Staff-Session": token } },
  );
  if (!response.ok)
    throw new StaffApiError("배정 가능한 객실을 불러오지 못했습니다.");
  return response.json() as Promise<AssignableRoom[]>;
}

export async function assignRoom(
  token: string,
  reservationId: string,
  physicalRoomId: string,
): Promise<void> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/assignments`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Staff-Session": token },
      body: JSON.stringify({ physicalRoomId }),
    },
  );
  if (!response.ok)
    throw new StaffApiError(
      "객실 배정에 실패했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.",
    );
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
export type ContentKind =
  | "HOME"
  | "DESTINATION"
  | "ROOM"
  | "DINING"
  | "FACILITY"
  | "EXPERIENCE"
  | "PROMOTION"
  | "GUIDE"
  | "BRAND";
export type WebsitePageConnections = {
  roomTypeIds: string[];
  targetHotelIds: string[];
  relatedPages: Array<{
    targetPageId: string;
    relationType: "RELATED" | "MANUAL_CARD";
    displayOrder: number;
  }>;
};
export type ContentReferenceCatalog = {
  hotels: Array<{
    id: string;
    name: string;
    region: string;
    roomTypes: Array<{ id: string; name: string; maxOccupancy: number }>;
  }>;
  pages: Array<{
    id: string;
    contentKind: ContentKind;
    hotelId: string | null;
    title: string;
    path: string;
  }>;
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
export type WebsiteTranslationReviewStatus =
  "DRAFT" | "IN_REVIEW" | "APPROVED" | "PUBLISHED";
export type WebsiteTranslationReviewEvent = {
  id: number;
  action:
    | "REVIEW_REQUESTED"
    | "APPROVED"
    | "REJECTED"
    | "APPROVAL_INVALIDATED"
    | "PUBLISHED";
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
export type WebsiteHomeDocument = Omit<
  WebsitePageDocument,
  "pageType" | "hotelId"
> & {
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
  pageId: string;
  currentDraftPath: string;
  nextDraftPath: string;
  currentPublishedPath: string | null;
  nextPublishedPath: string | null;
  depth: number;
  published: boolean;
};
export type WebsitePageMoveImpact = {
  pageId: string;
  newParentId: string;
  newRootDraftPath: string;
  items: WebsitePageMoveImpactItem[];
};
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
  targets: Pick<
    WebsiteMediaDraftReplacementUsage,
    "pageId" | "locale" | "fieldPath" | "expectedDraftVersion"
  >[];
};
export type WebsiteMediaDraftReplacementResult = {
  sourceMediaId: string;
  targetMediaId: string;
  replacedUsageCount: number;
  changedDraftCount: number;
};

async function contentRequest<T>(
  path: string,
  token: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "웹사이트 콘텐츠 요청을 처리하지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<T>;
}

async function mediaRequest<T>(
  path: string,
  token: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "X-Staff-Session": token, ...init?.headers },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "미디어 요청을 처리하지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<T>;
}

async function operationsRequest<T>(
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
    const error = (await response
      .json()
      .catch(() => ({}))) as RoomOperationsErrorPayload;
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
  return operationsRequest<RoomOperationsView>(
    `/api/staff/hotels/${hotelId}/room-operations`,
    token,
  );
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

export function getCheckedInRoomMoveOptions(
  token: string,
  reservationId: string,
) {
  return operationsRequest<CheckedInRoomMoveOptions>(
    `/api/staff/reservations/${reservationId}/checked-in-room-move-options`,
    token,
  );
}

export function moveCheckedInRoom(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  input: {
    currentPhysicalRoomId: string;
    newPhysicalRoomId: string;
    reason: string;
  },
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
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/room-reassignment-options`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "객실 변경 후보를 불러오지 못했습니다.",
      response.status,
      error.code,
    );
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
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/assignments/${currentPhysicalRoomId}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-Staff-Session": token,
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({ newPhysicalRoomId }),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "배정 객실을 변경하지 못했습니다.",
      response.status,
      error.code,
    );
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
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/reservations?${searchParams}`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok)
    throw new StaffApiError(
      "예약 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  return response.json() as Promise<StaffReservationSearchView>;
}

export async function getStaffCancellationPreview(
  token: string,
  reservationId: string,
): Promise<StaffCancellationPreview> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/cancellation-preview`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "예약 취소 조건을 확인하지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<StaffCancellationPreview>;
}

export async function cancelStaffReservation(
  token: string,
  reservationId: string,
  idempotencyKey: string,
): Promise<StaffCancellationResult> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/cancel`,
    {
      method: "POST",
      headers: { "X-Staff-Session": token, "Idempotency-Key": idempotencyKey },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "예약을 취소하지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<StaffCancellationResult>;
}

export async function updateStaffReservationGuest(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  guest: { guestName: string; guestEmail: string },
): Promise<StaffReservationGuestUpdateResult> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/guest`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-Staff-Session": token,
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(guest),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "예약자 정보를 수정하지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<StaffReservationGuestUpdateResult>;
}

export async function updateStaffReservationParty(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  party: { adults: number; children: number },
): Promise<StaffReservationPartyUpdateResult> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/party`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-Staff-Session": token,
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(party),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "투숙 인원을 변경하지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<StaffReservationPartyUpdateResult>;
}

export async function previewStaffReservationStayChange(
  token: string,
  reservationId: string,
  stay: { checkIn: string; checkOut: string },
): Promise<StaffReservationStayChangePreview> {
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/stay-change-preview`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Staff-Session": token },
      body: JSON.stringify(stay),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "변경 가능한 숙박 조건을 조회하지 못했습니다.",
      response.status,
      error.code,
    );
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
  const response = await fetch(
    `/api/staff/reservations/${reservationId}/stay`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-Staff-Session": token,
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(stay),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "숙박 조건을 변경하지 못했습니다.",
      response.status,
      error.code,
    );
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
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        "예약 변경 요청을 처리하지 못했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<T>;
}

export function getReservationChangePolicy(token: string) {
  return reservationChangeRequest<ReservationChangePolicy>(
    "/api/staff/reservation-change-policy",
    token,
  );
}

export function getReservationChangeRequests(
  token: string,
  filters: { status?: ReservationChangeStatus; hotelId?: string } = {},
) {
  const searchParams = new URLSearchParams();
  if (filters.status) searchParams.set("status", filters.status);
  if (filters.hotelId) searchParams.set("hotelId", filters.hotelId);
  const query = searchParams.size ? `?${searchParams}` : "";
  return reservationChangeRequest<ReservationChangeRequestView[]>(
    `/api/staff/reservation-change-requests${query}`,
    token,
  );
}

export function getReservationChangeRequest(token: string, requestId: string) {
  return reservationChangeRequest<ReservationChangeRequestView>(
    `/api/staff/reservation-change-requests/${requestId}`,
    token,
  );
}

export function createReservationChangeRequest(
  token: string,
  reservationId: string,
  idempotencyKey: string,
  input: {
    checkIn: string;
    checkOut: string;
    roomTypeId: string;
    ratePlanId: string;
    expectedTotal: number;
  },
) {
  return reservationChangeRequest<ReservationChangeRequestView>(
    `/api/staff/reservations/${reservationId}/change-requests`,
    token,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

function mutateReservationChangeRequest(
  token: string,
  requestId: string,
  action: "approve" | "reject" | "reprice" | "cancel",
  idempotencyKey: string,
  input: { version: number; reason?: string },
) {
  return reservationChangeRequest<ReservationChangeRequestView>(
    `/api/staff/reservation-change-requests/${requestId}/${action}`,
    token,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

export function approveReservationChangeRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  version: number,
) {
  return mutateReservationChangeRequest(
    token,
    requestId,
    "approve",
    idempotencyKey,
    { version },
  );
}

export function rejectReservationChangeRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  input: { version: number; reason: string },
) {
  return mutateReservationChangeRequest(
    token,
    requestId,
    "reject",
    idempotencyKey,
    input,
  );
}

export function repriceReservationChangeRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  version: number,
) {
  return mutateReservationChangeRequest(
    token,
    requestId,
    "reprice",
    idempotencyKey,
    { version },
  );
}

export function cancelReservationChangeRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  version: number,
) {
  return mutateReservationChangeRequest(
    token,
    requestId,
    "cancel",
    idempotencyKey,
    { version },
  );
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
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
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
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ version }),
    },
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
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

type WebsiteMediaAssetResponse = Omit<WebsiteMediaAsset, "variants"> & {
  variants?: WebsiteMediaVariant[];
};

function normalizeWebsiteMediaAsset(
  asset: WebsiteMediaAssetResponse,
): WebsiteMediaAsset {
  return { ...asset, variants: asset.variants ?? [] };
}

async function mediaAssetRequest(
  path: string,
  token: string,
  init?: RequestInit,
): Promise<WebsiteMediaAsset> {
  return normalizeWebsiteMediaAsset(
    await mediaRequest<WebsiteMediaAssetResponse>(path, token, init),
  );
}

export type WebsitePreviewGrantResponse = {
  grantId: string;
  previewToken: string;
  previewPath: string;
  expiresAt: string;
};
export function issueWebsitePreviewGrant(
  token: string,
  pageId: string,
  input: { locale: "ko" | "en"; expectedDraftVersion: number },
) {
  return contentRequest<WebsitePreviewGrantResponse>(
    `/api/staff/website/pages/${pageId}/preview-grants`,
    token,
    { method: "POST", body: JSON.stringify(input) },
  );
}
export async function revokeWebsitePreviewGrant(
  token: string,
  grantId: string,
): Promise<void> {
  const response = await fetch(`/api/staff/website/preview-grants/${grantId}`, {
    method: "DELETE",
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "미리보기 링크를 폐기하지 못했습니다.",
      response.status,
      error.code,
    );
  }
}

export function getWebContent(token: string, hotelId: string) {
  return contentRequest<WebContentDocument>(
    `/api/staff/web-content/hotels/${hotelId}`,
    token,
  );
}
export function saveWebContent(
  token: string,
  hotelId: string,
  expectedDraftVersion: number,
  content: Record<string, unknown>,
  page?: WebsitePageDraftMetadata,
) {
  return contentRequest<WebContentDocument>(
    `/api/staff/web-content/hotels/${hotelId}`,
    token,
    {
      method: "PUT",
      body: JSON.stringify({
        expectedDraftVersion,
        content,
        ...(page ? { page } : {}),
      }),
    },
  );
}
export function publishWebContent(
  token: string,
  hotelId: string,
  expectedDraftVersion: number,
  expectedPublishedVersion: number,
) {
  return contentRequest<WebContentDocument>(
    `/api/staff/web-content/hotels/${hotelId}/publish`,
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
    },
  );
}
export function getWebContentVersions(token: string, hotelId: string) {
  return contentRequest<WebContentVersion[]>(
    `/api/staff/web-content/hotels/${hotelId}/versions`,
    token,
  );
}
export function getWebsiteHome(token: string) {
  return contentRequest<WebsiteHomeDocument>("/api/staff/website/home", token);
}
export function saveWebsiteHome(token: string, input: SaveWebsiteHomeInput) {
  return contentRequest<WebsiteHomeDocument>("/api/staff/website/home", token, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
export function publishWebsiteHome(
  token: string,
  expectedDraftVersion: number,
  expectedPublishedVersion: number,
) {
  return contentRequest<WebsiteHomeDocument>(
    "/api/staff/website/home/publish",
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
    },
  );
}
export function getWebsiteHomeVersions(token: string) {
  return contentRequest<WebContentVersion[]>(
    "/api/staff/website/home/versions",
    token,
  );
}
export async function getWebsiteMedia(
  token: string,
  includeArchived = false,
): Promise<WebsiteMediaAsset[]> {
  const assets = await mediaRequest<WebsiteMediaAssetResponse[]>(
    `/api/staff/website/media${includeArchived ? "?includeArchived=true" : ""}`,
    token,
  );
  return assets.map(normalizeWebsiteMediaAsset);
}
export async function getWebsiteMediaStorageAudit(
  token: string,
): Promise<WebsiteMediaStorageAudit> {
  type AuditResponse = Omit<WebsiteMediaStorageAudit, "mode" | "stores"> & {
    mode?: WebsiteMediaStorageAudit["mode"];
    stores?: WebsiteMediaStoreAudit[];
  };
  const result = await mediaRequest<AuditResponse>(
    "/api/staff/website/media/storage-audit",
    token,
  );
  const local = {
    storeName: "local",
    healthy: result.healthy,
    missingStorageKeys: result.missingStorageKeys,
    orphanStorageKeys: result.orphanStorageKeys,
    staleTemporaryStorageKeys: result.staleTemporaryStorageKeys,
  };
  return {
    ...result,
    mode: result.mode ?? "local",
    stores: result.stores ?? [local],
  };
}
export function getWebsiteMediaStorageMigration(token: string) {
  return mediaRequest<WebsiteMediaStorageMigrationStatus>(
    "/api/staff/website/media/storage-migration",
    token,
  );
}
export function backfillWebsiteMediaStorage(token: string) {
  return mediaRequest<WebsiteMediaStorageBackfillResult>(
    "/api/staff/website/media/storage-migration/backfill",
    token,
    { method: "POST" },
  );
}
export function uploadWebsiteMedia(
  token: string,
  input: UploadWebsiteMediaInput,
) {
  const formData = new FormData();
  formData.append("file", input.file);
  formData.append("displayName", input.displayName);
  formData.append("defaultAltText", input.defaultAltText);
  return mediaAssetRequest("/api/staff/website/media", token, {
    method: "POST",
    body: formData,
  });
}
export function getWebsiteMediaUsages(token: string, mediaId: string) {
  return mediaRequest<WebsiteMediaUsage[]>(
    `/api/staff/website/media/${mediaId}/usages`,
    token,
  );
}
export function retryWebsiteMediaVariant(
  token: string,
  mediaId: string,
  targetWidth: 640 | 1280,
) {
  return mediaAssetRequest(
    `/api/staff/website/media/${mediaId}/variants/${targetWidth}/retry`,
    token,
    { method: "POST" },
  );
}
export async function getWebsiteMediaDraftReplacementImpact(
  token: string,
  sourceMediaId: string,
  targetMediaId: string,
): Promise<WebsiteMediaDraftReplacementImpact> {
  const query = new URLSearchParams({ targetMediaId });
  const impact = await mediaRequest<
    Omit<WebsiteMediaDraftReplacementImpact, "sourceAsset" | "targetAsset"> & {
      sourceAsset: WebsiteMediaAssetResponse;
      targetAsset: WebsiteMediaAssetResponse;
    }
  >(
    `/api/staff/website/media/${sourceMediaId}/draft-replacement-impact?${query}`,
    token,
  );
  return {
    ...impact,
    sourceAsset: normalizeWebsiteMediaAsset(impact.sourceAsset),
    targetAsset: normalizeWebsiteMediaAsset(impact.targetAsset),
  };
}
export function replaceWebsiteMediaDraftUsages(
  token: string,
  sourceMediaId: string,
  input: ReplaceWebsiteMediaDraftUsagesInput,
) {
  return mediaRequest<WebsiteMediaDraftReplacementResult>(
    `/api/staff/website/media/${sourceMediaId}/draft-replacements`,
    token,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}
export function updateWebsiteMedia(
  token: string,
  mediaId: string,
  input: WebsiteMediaMetadataInput,
) {
  return mediaAssetRequest(`/api/staff/website/media/${mediaId}`, token, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}
export function archiveWebsiteMedia(
  token: string,
  mediaId: string,
  input: WebsiteMediaVersionInput,
) {
  return mediaAssetRequest(
    `/api/staff/website/media/${mediaId}/archive`,
    token,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}
export function restoreWebsiteMedia(
  token: string,
  mediaId: string,
  input: WebsiteMediaVersionInput,
) {
  return mediaAssetRequest(
    `/api/staff/website/media/${mediaId}/restore`,
    token,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}
export async function deleteWebsiteMedia(
  token: string,
  mediaId: string,
  input: WebsiteMediaVersionInput,
): Promise<void> {
  const response = await fetch(`/api/staff/website/media/${mediaId}`, {
    method: "DELETE",
    headers: { "X-Staff-Session": token, "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "미디어를 영구 삭제하지 못했습니다.",
      response.status,
      error.code,
    );
  }
}
export function getWebsitePageTree(token: string) {
  return contentRequest<WebsitePageTreeItem[]>(
    "/api/staff/website/pages",
    token,
  );
}
export function getContentReferenceCatalog(token: string) {
  return contentRequest<ContentReferenceCatalog>(
    "/api/staff/website/content-reference",
    token,
  );
}
export function createWebsitePage(
  token: string,
  input: CreateWebsitePageInput,
) {
  return contentRequest<WebsitePageDocument>(
    "/api/staff/website/pages",
    token,
    { method: "POST", body: JSON.stringify(input) },
  );
}
export function getWebsitePage(token: string, pageId: string) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}`,
    token,
  );
}
export function getWebsiteTranslation(token: string, pageId: string) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/translations/en`,
    token,
  );
}
export function getWebsiteTranslationReview(token: string, pageId: string) {
  return contentRequest<WebsiteTranslationReviewState>(
    `/api/staff/website/pages/${pageId}/translations/en/review`,
    token,
  );
}
export function requestWebsiteTranslationReview(
  token: string,
  pageId: string,
  expectedDraftVersion: number,
  comment: string | null,
) {
  return contentRequest<WebsiteTranslationReviewState>(
    `/api/staff/website/pages/${pageId}/translations/en/review/request`,
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, comment }),
    },
  );
}
export function approveWebsiteTranslationReview(
  token: string,
  pageId: string,
  expectedDraftVersion: number,
  comment: string | null,
) {
  return contentRequest<WebsiteTranslationReviewState>(
    `/api/staff/website/pages/${pageId}/translations/en/review/approve`,
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, comment }),
    },
  );
}
export function rejectWebsiteTranslationReview(
  token: string,
  pageId: string,
  expectedDraftVersion: number,
  comment: string,
) {
  return contentRequest<WebsiteTranslationReviewState>(
    `/api/staff/website/pages/${pageId}/translations/en/review/reject`,
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, comment }),
    },
  );
}
export function initializeWebsiteTranslation(
  token: string,
  pageId: string,
  expectedSourceDraftVersion: number,
  expectedLifecycleVersion: number,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/translations/en`,
    token,
    {
      method: "POST",
      body: JSON.stringify({
        expectedSourceDraftVersion,
        expectedLifecycleVersion,
      }),
    },
  );
}
export function saveWebsiteTranslation(
  token: string,
  pageId: string,
  input: SaveWebsitePageInput,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/translations/en`,
    token,
    { method: "PUT", body: JSON.stringify(input) },
  );
}
export function publishWebsiteTranslation(
  token: string,
  pageId: string,
  expectedDraftVersion: number,
  expectedPublishedVersion: number,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/translations/en/publish`,
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
    },
  );
}
export function getWebsiteTranslationVersions(token: string, pageId: string) {
  return contentRequest<WebContentVersion[]>(
    `/api/staff/website/pages/${pageId}/translations/en/versions`,
    token,
  );
}
export function getWebsitePageMoveImpact(
  token: string,
  pageId: string,
  parentId: string,
  slug: string,
) {
  return contentRequest<WebsitePageMoveImpact>(
    `/api/staff/website/pages/${pageId}/move-impact?parentId=${encodeURIComponent(parentId)}&slug=${encodeURIComponent(slug)}`,
    token,
  );
}
export function moveWebsitePage(
  token: string,
  pageId: string,
  input: WebsitePageMoveInput,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/move`,
    token,
    { method: "POST", body: JSON.stringify(input) },
  );
}
export function saveWebsitePage(
  token: string,
  pageId: string,
  input: SaveWebsitePageInput,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}`,
    token,
    { method: "PUT", body: JSON.stringify(input) },
  );
}
export function publishWebsitePage(
  token: string,
  pageId: string,
  expectedDraftVersion: number,
  expectedPublishedVersion: number,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/publish`,
    token,
    {
      method: "POST",
      body: JSON.stringify({ expectedDraftVersion, expectedPublishedVersion }),
    },
  );
}
export function archiveWebsitePage(
  token: string,
  pageId: string,
  input: WebsitePageLifecycleInput,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/archive`,
    token,
    {
      method: "POST",
      body: JSON.stringify(input),
    },
  );
}
export function restoreWebsitePage(
  token: string,
  pageId: string,
  input: WebsitePageLifecycleInput,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/restore`,
    token,
    {
      method: "POST",
      body: JSON.stringify(input),
    },
  );
}
export async function deleteWebsitePage(
  token: string,
  pageId: string,
  input: WebsitePageLifecycleInput,
): Promise<void> {
  const response = await fetch(`/api/staff/website/pages/${pageId}`, {
    method: "DELETE",
    headers: { "X-Staff-Session": token, "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "페이지를 영구 삭제하지 못했습니다.",
      response.status,
      error.code,
    );
  }
}
export function restoreWebsitePageVersionDraft(
  token: string,
  pageId: string,
  sourceVersion: number,
  input: WebsitePageLifecycleInput,
) {
  return contentRequest<WebsitePageDocument>(
    `/api/staff/website/pages/${pageId}/versions/${sourceVersion}/restore-draft`,
    token,
    {
      method: "POST",
      body: JSON.stringify(input),
    },
  );
}
export function getWebsitePageVersions(token: string, pageId: string) {
  return contentRequest<WebContentVersion[]>(
    `/api/staff/website/pages/${pageId}/versions`,
    token,
  );
}
export function getWebsitePageVersionComparison(
  token: string,
  pageId: string,
  baseVersion: number,
  compareVersion: number,
) {
  const query = new URLSearchParams({
    baseVersion: String(baseVersion),
    compareVersion: String(compareVersion),
  });
  return contentRequest<WebsitePageVersionComparison>(
    `/api/staff/website/pages/${pageId}/versions/compare?${query}`,
    token,
  );
}

export type SettlementRunStatus =
  "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED";

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

export async function getSettlementRuns(
  token: string,
  limit = 20,
): Promise<SettlementRunsView> {
  const response = await fetch(`/api/staff/settlements/runs?limit=${limit}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        settlementFailureMessage(
          response.status,
          "정산 실행 목록을 불러오지 못했습니다.",
        ),
      response.status,
      error.code,
    );
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
  const response = await fetch(
    `/api/staff/settlements/runs/${runId}?${searchParams}`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        settlementFailureMessage(
          response.status,
          "정산 대사 내역을 불러오지 못했습니다.",
        ),
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<SettlementRunDetailView>;
}

// 정산 worker가 비활성화된 환경에서는 엔드포인트 자체가 노출되지 않는다.
// 404는 기능이 꺼져 있음을 뜻하므로 조회 실패과 구분해 안내한다.
function settlementFailureMessage(status: number, fallback: string) {
  return status === 404
    ? "정산·대사 기능이 비활성화되어 있습니다. 토스 라이브 결제와 정산 worker를 활성화한 환경에서만 사용할 수 있습니다."
    : fallback;
}

export type CreateSettlementRunInput = {
  from: string;
  to: string;
};

export type CreatedSettlementRun = {
  runId: string;
};

// 본사가 정산 실행을 직접 만든다. worker가 처리할 PENDING 실행을 준비만 하고
// 결제·재고·대사 상태를 바꾸지 않는다.
export async function createSettlementRun(
  token: string,
  input: CreateSettlementRunInput,
): Promise<CreatedSettlementRun> {
  const response = await fetch("/api/staff/settlements/runs", {
    method: "POST",
    headers: { "X-Staff-Session": token, "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        settlementFailureMessage(
          response.status,
          "정산 실행 생성에 실패했습니다.",
        ),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as CreatedSettlementRun;
}

// 실패한 정산 실행을 worker가 다시 집을 수 있도록 대기 상태로 되돌린다.
export async function retrySettlementRun(
  token: string,
  runId: string,
): Promise<void> {
  const response = await fetch(`/api/staff/settlements/runs/${runId}/retry`, {
    method: "POST",
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        settlementFailureMessage(
          response.status,
          "정산 실행 재시도에 실패했습니다.",
        ),
      response.status,
      error.code,
    );
  }
}

// AI 도우미의 LLM 호출 결과를 본사가 읽기 전용으로 확인한다.
// concierge에는 세션 검증이 없으므로 next.config의 rewrite가 도우미로만 보낸다.
// 도우미가 없는 배포에서는 rewrite 자체가 없어서 404가 돌아온다.
// 그럴 때는 서버 오류가 아니라 "도우미가 실행 중이 아니다"로 안내한다.
export type ConciergeLlmOutcome =
  | "success"
  | "schema_rejected"
  | "unparsable"
  | "empty_response"
  | "api_error"
  | "no_key";

export type ConciergeLlmMetrics = {
  model: string;
  outcomes: Record<
    ConciergeLlmOutcome,
    { count: number; totalElapsedMs: number; avgElapsedMs: number }
  >;
};

const CONCIERGE_UNAVAILABLE_HTTP_STATUS = 404;

export async function getConciergeLlmMetrics(): Promise<ConciergeLlmMetrics> {
  const response = await fetch("/concierge/metrics/llm");
  if (!response.ok) {
    const unavailable = response.status === CONCIERGE_UNAVAILABLE_HTTP_STATUS;
    throw new StaffApiError(
      unavailable
        ? "AI 도우미가 실행 중이 아닙니다. 이 배포에는 도우미가 포함되지 않았습니다."
        : "AI 도우미 측정을 불러오지 못했습니다. 도우미가 실행 중인지 확인해 주세요.",
      response.status,
    );
  }
  return (await response.json()) as ConciergeLlmMetrics;
}

// 본사가 지점 목록을 읽는다. 카탈로그·재고·보고서 화면의 지점 선택기가 쓴다.
export type StaffHotelSummary = {
  id: string;
  name: string;
  region: string;
  timezone: string;
  active: boolean;
};

export async function getStaffHotels(
  token: string,
): Promise<StaffHotelSummary[]> {
  const response = await fetch("/api/staff/hotels", {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        "지점 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<StaffHotelSummary[]>;
}

// 본사가 새 지점을 만든다. 멱원 키로 같은 요청의 중복 생성을 막는다.
// 지점 행만 만들고 객실 유형·요금·재고는 심지 않는다. 유형을 추가할 때 심어진다.
export type CreateHotelInput = {
  name: string;
  region: string;
  timezone: string;
};

export type CreatedHotel = {
  hotelId: string;
  name: string;
  region: string;
  timezone: string;
  roomTypes: number;
  created: boolean;
};

export async function createHotel(
  token: string,
  idempotencyKey: string,
  input: CreateHotelInput,
): Promise<CreatedHotel> {
  const response = await fetch("/api/staff/hotels", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "지점을 추가하지 못했습니다. 입력값을 확인해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<CreatedHotel>;
}

// 본사가 지점의 이름·지역·시간대를 바꾼다. 세 필드 모두 선택이고
// 보내지 않은 필드는 현재 값을 유지한다.
export type UpdateHotelInput = {
  name?: string;
  region?: string;
  timezone?: string;
};

export type UpdatedHotel = {
  hotelId: string;
  name: string;
  region: string;
  timezone: string;
  roomTypes: number;
  changed: boolean;
};

export async function updateHotel(
  token: string,
  hotelId: string,
  idempotencyKey: string,
  input: UpdateHotelInput,
): Promise<UpdatedHotel> {
  const response = await fetch(`/api/staff/hotels/${hotelId}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "지점을 수정하지 못했습니다. 입력값을 확인해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<UpdatedHotel>;
}

// 본사가 지점의 판매를 중지·재개한다.
// 중지한 지점은 고객 검색과 지점 목록에서 빠진다. 이미 확정된 예약은 그대로 둔다.
export async function setHotelActive(
  token: string,
  hotelId: string,
  idempotencyKey: string,
  active: boolean,
): Promise<UpdatedHotel> {
  const response = await fetch(`/api/staff/hotels/${hotelId}/active`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Staff-Session": token,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ active }),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "판매 상태를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<UpdatedHotel>;
}

// 본사가 객실 유형과 요금제 카탈로그를 읽기 전용으로 확인한다.
export type RoomTypeRatePlanSummary = {
  ratePlanId: string;
  name: string;
  breakfastIncluded: boolean;
  policyVersion: string;
  pricedDays: number;
  minAmountKrw: number | null;
  maxAmountKrw: number | null;
  avgAmountKrw: number | null;
};

export type RoomTypeCatalogEntry = {
  roomTypeId: string;
  name: string;
  maxOccupancy: number;
  breakfastIncluded: boolean;
  defaultRateKrw: number | null;
  ratePlans: RoomTypeRatePlanSummary[];
};

export type RoomTypeCatalogView = {
  hotelId: string;
  totalCount: number;
  roomTypes: RoomTypeCatalogEntry[];
};

export async function getRoomTypeCatalog(
  token: string,
  hotelId: string,
  filters: { limit?: number; offset?: number } = {},
): Promise<RoomTypeCatalogView> {
  const searchParams = new URLSearchParams();
  if (filters.limit) searchParams.set("limit", String(filters.limit));
  if (filters.offset) searchParams.set("offset", String(filters.offset));
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types${query}`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        "객실 유형 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<RoomTypeCatalogView>;
}

// 본사가 새 객실 유형을 만든다. 서버가 멱원 키로 같은 요청의 중복 생성을 막는다.
// breakfastIncluded·defaultRateKrw는 함께 심기는 기본 요금제의 조건이다.
export type CreateRoomTypeRequest = {
  name: string;
  maxOccupancy: number;
  breakfastIncluded: boolean;
  defaultRateKrw: number | null;
};

export type CreatedRoomTypeSeed = {
  ratePlanId: string | null;
  ratePlanName: string | null;
  breakfastIncluded: boolean;
  defaultRateKrw: number;
  pricedDays: number;
  inventoryCapacity: number;
  created: boolean;
};

export type CreatedRoomType = {
  roomTypeId: string;
  hotelId: string;
  name: string;
  maxOccupancy: number;
  created: boolean;
  seed: CreatedRoomTypeSeed | null;
};

export async function createRoomType(
  token: string,
  hotelId: string,
  idempotencyKey: string,
  input: CreateRoomTypeRequest,
): Promise<CreatedRoomType> {
  const response = await fetch(`/api/staff/hotels/${hotelId}/room-types`, {
    method: "POST",
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? roomTypeCreateFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as CreatedRoomType;
}

// 멱원 재호출은 200으로 같은 객실 유형을 돌려준다. 201과 200 모두 성공이다.
function roomTypeCreateFailureMessage(status: number) {
  if (status === 403) return "객실 유형 추가는 본사 관리자만 할 수 있습니다.";
  if (status === 409)
    return "이미 처리된 요청입니다. 목록을 새로고침해 주세요.";
  return "객실 유형을 추가하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 빈 문자열·공백·숫자가 아닌 입력을 서버 검증에 맡기기 전에 정리한다.
// 폼에서는 type="text" + inputMode="numeric"을 쓴다.
export function parseRateKrw(input: string): number | null {
  const trimmed = input.trim().replace(/[^0-9]/g, "");
  if (trimmed === "") return null;
  const parsed = Number(trimmed);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

// 본사가 객실 유형의 이름·최대 인원·조식 포함 여부·기본 요금을 바꾼다.
// breakfastIncluded·defaultRateKrw는 null이면 서버가 바꾸지 않는다.
export type UpdateRoomTypeInput = {
  name: string;
  maxOccupancy: number;
  breakfastIncluded: boolean | null;
  defaultRateKrw: number | null;
};

export type UpdatedRoomType = {
  roomTypeId: string;
  hotelId: string;
  name: string;
  maxOccupancy: number;
  created: boolean;
  ratePlan: {
    ratePlanId: string;
    ratePlanName: string;
    breakfastIncluded: boolean;
    defaultRateKrw: number;
    pricedDays: number;
  } | null;
};

// 본사가 수정 화면에 미리 채울 기본 요금제의 현재값. SELECT만 사용한다.
export type RoomTypeDefaults = {
  ratePlanId: string;
  ratePlanName: string;
  breakfastIncluded: boolean;
  defaultRateKrw: number | null;
};

export async function getRoomTypeDefaults(
  token: string,
  hotelId: string,
  roomTypeId: string,
): Promise<RoomTypeDefaults | null> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/defaults`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (response.status === 404) return null;
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? "기본 요금제 정보를 불러오지 못했습니다.",
      response.status,
      error.code,
    );
  }
  return (await response.json()) as RoomTypeDefaults;
}

export async function updateRoomType(
  token: string,
  hotelId: string,
  roomTypeId: string,
  idempotencyKey: string,
  input: UpdateRoomTypeInput,
): Promise<UpdatedRoomType> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}`,
    {
      method: "PATCH",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(input),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        roomTypeUpdateFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as UpdatedRoomType;
}

function roomTypeUpdateFailureMessage(
  status: number,
  code: string | undefined,
) {
  if (status === 403) return "객실 유형 수정은 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409 && code === "ROOM_TYPE_BREAKFAST_CONFLICT") {
    return "조식 포함 여부를 바꿀 수 없습니다. 진행 중인 예약이 현재 조식 조건으로 예약됐습니다.";
  }
  if (status === 409) {
    return "최대 인원을 내릴 수 없습니다. 진행 중인 예약이 새 인원을 초과합니다.";
  }
  return "객실 유형을 수정하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 객실 유형을 지운다. 멱원 재호출은 200 deleted=false로 같은 결과를
// 돌려준다. 진행 중인 예약·변경 요청·보류·배정 객실이 있으면 서버가 409로 거부한다.
export type DeletedRoomType = {
  hotelId: string;
  roomTypeId: string;
  name: string;
  deleted: boolean;
  remainingRoomTypes: number;
};

export async function deleteRoomType(
  token: string,
  hotelId: string,
  roomTypeId: string,
  idempotencyKey: string,
): Promise<DeletedRoomType> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}`,
    {
      method: "DELETE",
      headers: {
        "X-Staff-Session": token,
        "Idempotency-Key": idempotencyKey,
      },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? roomTypeDeleteFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as DeletedRoomType;
}

function roomTypeDeleteFailureMessage(status: number) {
  if (status === 403) return "객실 유형 삭제는 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409)
    return "객실 유형을 지울 수 없습니다. 진행 중인 예약·변경 요청·보류 재고·배정 객실이 없어야 합니다.";
  return "객실 유형을 지우지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 객실 유형의 전체 요금제를 읽는다. 한 유형에 요금제가 여러 개일 수 있다.
export type RatePlanSummary = {
  ratePlanId: string;
  name: string;
  breakfastIncluded: boolean;
  policyVersion: string;
  pricedDays: number;
  minAmountKrw: number | null;
  maxAmountKrw: number | null;
};

export type RatePlanListView = {
  hotelId: string;
  roomTypeId: string;
  ratePlans: RatePlanSummary[];
};

export async function listRatePlans(
  token: string,
  hotelId: string,
  roomTypeId: string,
): Promise<RatePlanListView> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/rate-plans`,
    { headers: { "X-Staff-Session": token } },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        ratePlanListFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as RatePlanListView;
}

function ratePlanListFailureMessage(status: number) {
  if (status === 403) return "해당 지점의 요금제는 조회 권한이 없습니다.";
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  return "요금제 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 객실 유형에 새 요금제를 만든다. 조식 포함 여부·취소 규정·금액이
// 다른 요금제를 같은 객실에서 동시에 팔기 위해 쓴다.
export type CreateRatePlanInput = {
  name: string;
  breakfastIncluded: boolean;
  policyVersion: string;
  defaultRateKrw: number;
  fromDate: string | null;
  days: number | null;
};

export type CreatedRatePlan = {
  ratePlanId: string;
  hotelId: string;
  roomTypeId: string;
  name: string;
  breakfastIncluded: boolean;
  policyVersion: string;
  defaultRateKrw: number;
  fromDate: string | null;
  seededDays: number;
  created: boolean;
  changed: boolean;
};

export async function createRatePlan(
  token: string,
  hotelId: string,
  roomTypeId: string,
  idempotencyKey: string,
  input: CreateRatePlanInput,
): Promise<CreatedRatePlan> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/rate-plans`,
    {
      method: "POST",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(input),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? ratePlanCreateFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as CreatedRatePlan;
}

function ratePlanCreateFailureMessage(status: number) {
  if (status === 403) return "요금제 추가는 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409)
    return "객실 유형 안에 같은 이름의 요금제가 있거나, 재고가 없는 날짜를 포함했습니다.";
  return "요금제를 추가하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 요금제의 이름을 바꾼다. 조식 포함 여부·정책 버전은 확정 예약의
// 계약 조건이어서 새 요금제를 만드는 것으로만 바꿀 수 있다.
export async function renameRatePlan(
  token: string,
  hotelId: string,
  roomTypeId: string,
  ratePlanId: string,
  idempotencyKey: string,
  name: string,
): Promise<CreatedRatePlan> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/rate-plans/${ratePlanId}`,
    {
      method: "PATCH",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({ name }),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? ratePlanRenameFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as CreatedRatePlan;
}

function ratePlanRenameFailureMessage(status: number) {
  if (status === 403) return "요금제 이름 변경은 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "요금제를 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409)
    return "같은 객실 유형 안에 같은 이름의 요금제가 있습니다.";
  return "요금제 이름을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 직원 목록을 읽기 전용으로 확인한다.
export type StaffAccountView = {
  id: string;
  email: string;
  displayName: string;
  role: "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";
  hotelId: string | null;
  hotelName: string | null;
  active: boolean;
};

export async function getStaffAccounts(
  token: string,
  filters: { limit?: number } = {},
): Promise<StaffAccountView[]> {
  const searchParams = new URLSearchParams();
  if (filters.limit) searchParams.set("limit", String(filters.limit));
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(`/api/staff/staff${query}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        staffAccountFailureMessage(
          response.status,
          "직원 목록을 불러오지 못했습니다.",
        ),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as StaffAccountView[];
}

// 본사가 새 직원을 만든다. 임시 비밀번호는 생성 시 한 번만 내려온다.
export type CreateStaffAccountInput = {
  email: string;
  displayName: string;
  role: "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";
  hotelId: string | null;
};

export type CreatedStaffAccount = {
  staffId: string;
  email: string;
  displayName: string;
  role: string;
  hotelId: string | null;
  hotelName: string | null;
  temporaryPassword: string | null;
  created: boolean;
};

export async function createStaffAccount(
  token: string,
  idempotencyKey: string,
  input: CreateStaffAccountInput,
): Promise<CreatedStaffAccount> {
  const response = await fetch("/api/staff/staff", {
    method: "POST",
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        staffAccountFailureMessage(
          response.status,
          "직원을 추가하지 못했습니다.",
        ),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as CreatedStaffAccount;
}

function staffAccountFailureMessage(status: number, fallback: string) {
  if (status === 403) return "직원 관리는 본사 관리자만 할 수 있습니다.";
  if (status === 409)
    return "이미 등록된 이메일입니다. 다른 이메일을 사용해 주세요.";
  return fallback;
}

// 본사가 직원의 임시 비밀번호를 재발급한다. 비밀번호는 첫 발급에만 내려온다.
export type StaffPasswordReset = {
  staffId: string;
  email: string;
  displayName: string;
  role: string;
  hotelId: string | null;
  hotelName: string | null;
  temporaryPassword: string | null;
  created: boolean;
  cooldownSeconds: number;
};

export async function resetStaffPassword(
  token: string,
  staffId: string,
  idempotencyKey: string,
): Promise<StaffPasswordReset> {
  const response = await fetch(`/api/staff/staff/${staffId}/password`, {
    method: "POST",
    headers: { "X-Staff-Session": token, "Idempotency-Key": idempotencyKey },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? passwordResetFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as StaffPasswordReset;
}

function passwordResetFailureMessage(status: number) {
  if (status === 403) return "비밀번호 재발급은 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "직원을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409) {
    return "방금 재발급된 직원입니다. 잠시 후 다시 시도해 주세요.";
  }
  return "비밀번호를 재발급하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 직원의 역할과 소속 지점을 바꾼다. hotelId가 null이면
// "소속 지점 없음"이고, 본사 역할은 지점을 가질 수 없다.
// 멱원 재호출은 200으로 같은 결과를 돌려준다.
export type UpdateStaffAccountInput = {
  role: StaffRole;
  hotelId: string | null;
};

export type UpdatedStaffAccount = {
  staffId: string;
  email: string;
  displayName: string;
  role: StaffRole;
  hotelId: string | null;
  hotelName: string | null;
  created: boolean;
};

export async function updateStaffAccount(
  token: string,
  staffId: string,
  idempotencyKey: string,
  input: UpdateStaffAccountInput,
): Promise<UpdatedStaffAccount> {
  const response = await fetch(`/api/staff/staff/${staffId}`, {
    method: "PATCH",
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? staffUpdateFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as UpdatedStaffAccount;
}

function staffUpdateFailureMessage(status: number, code: string | undefined) {
  if (status === 403) return "직원 관리는 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "직원을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409 && code === "STAFF_SELF_MODIFICATION_FORBIDDEN") {
    return "본인 계정의 역할·소속 지점은 이 화면에서 바꿀 수 없습니다. 다른 본사 관리자에게 요청해 주세요.";
  }
  if (status === 409)
    return "이미 처리된 요청입니다. 목록을 새로고침해 주세요.";
  return "직원 정보를 수정하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 직원을 비활성하거나 다시 활성한다. 비활성 직원의 세션은
// 즉시 끊기고 로그인도 거부된다. 멱원 재호출은 200으로 같은 결과를 돌려준다.
export type StaffActivationResult = {
  staffId: string;
  email: string;
  displayName: string;
  role: StaffRole;
  hotelId: string | null;
  hotelName: string | null;
  active: boolean;
  created: boolean;
};

export async function setStaffAccountActive(
  token: string,
  staffId: string,
  idempotencyKey: string,
  active: boolean,
): Promise<StaffActivationResult> {
  const response = await fetch(`/api/staff/staff/${staffId}/active`, {
    method: "PATCH",
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ active }),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? activationFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as StaffActivationResult;
}

function activationFailureMessage(status: number, code: string | undefined) {
  if (status === 403) return "직원 관리는 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "직원을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409 && code === "STAFF_SELF_MODIFICATION_FORBIDDEN") {
    return "본인 계정은 이 화면에서 비활성할 수 없습니다. 다른 본사 관리자에게 요청해 주세요.";
  }
  if (status === 409)
    return "이미 처리된 요청입니다. 목록을 새로고침해 주세요.";
  return "활성 상태를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 직원 계정을 영구 삭제한다. 행을 남기고 식별자만 비워서
// 감사 이력 참조가 끊기지 않는다. 되돌릴 수 없다.
export type DeletedStaffAccount = {
  staffId: string;
  email: string;
  displayName: string;
  deleted: boolean;
  remainingStaff: number;
};

export async function deleteStaffAccount(
  token: string,
  staffId: string,
  idempotencyKey: string,
): Promise<DeletedStaffAccount> {
  const response = await fetch(`/api/staff/staff/${staffId}`, {
    method: "DELETE",
    headers: { "X-Staff-Session": token, "Idempotency-Key": idempotencyKey },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? deletionFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as DeletedStaffAccount;
}

function deletionFailureMessage(status: number, code: string | undefined) {
  if (status === 403) return "직원 삭제는 본사 관리자만 할 수 있습니다.";
  if (status === 404)
    return "직원을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409 && code === "STAFF_SELF_MODIFICATION_FORBIDDEN") {
    return "본인 계정은 삭제할 수 없습니다. 다른 본사 관리자에게 요청해 주세요.";
  }
  if (status === 409) {
    return "진행 중인 예약 변경 요청이나 정산 실행이 있어 삭제할 수 없습니다. 완료된 뒤에 다시 시도해 주세요.";
  }
  return "직원을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 직원이 본인의 표시 이름·비밀번호를 바꾼다. 전 역할이 쓴다.
// 역할·소속 지점은 본사 전용 권한이다.
export type StaffSelfUpdateInput = {
  displayName?: string;
  currentPassword?: string;
  newPassword?: string;
};

export type StaffSelfUpdateResult = {
  staffId: string;
  email: string;
  displayName: string;
  role: StaffRole;
  changed: boolean;
};

export async function updateOwnStaffAccount(
  token: string,
  idempotencyKey: string,
  input: StaffSelfUpdateInput,
): Promise<StaffSelfUpdateResult> {
  const response = await fetch("/api/staff/staff/me", {
    method: "PATCH",
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? selfUpdateFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as StaffSelfUpdateResult;
}

function selfUpdateFailureMessage(status: number, code: string | undefined) {
  if (status === 400 && code === "STAFF_PASSWORD_MISMATCH") {
    return "현재 비밀번호가 올바르지 않습니다. 다시 확인해 주세요.";
  }
  if (status === 400) {
    return "입력값을 확인해 주세요. 이름은 1자 이상 100자 이하, 새 비밀번호는 8자 이상 100자 이하여야 합니다.";
  }
  if (status === 401)
    return "세션이 만료됐습니다. 다시 로그인해 주세요.";
  return "내 계정을 수정하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export type StaffRole =
  "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";

// 본사가 객실 유형별 일자 재고를 읽기 전용으로 확인한다.
// salesStatus는 총량과 별개다. STOPPED여도 capacity는 그대로여서
// 재개하면 중지 전과 같은 재고가 돌아온다.
export type InventoryDay = {
  stayDate: string;
  capacity: number;
  held: number;
  confirmed: number;
  remaining: number;
  salesStatus: "OPEN" | "STOPPED";
};

export type RoomTypeInventory = {
  roomTypeId: string;
  name: string;
  maxOccupancy: number;
  days: InventoryDay[];
};

export type InventoryView = {
  hotelId: string;
  roomTypes: RoomTypeInventory[];
};

export async function getStaffInventory(
  token: string,
  hotelId: string,
  filters: { from?: string; to?: string } = {},
): Promise<InventoryView> {
  const searchParams = new URLSearchParams();
  if (filters.from) searchParams.set("from", filters.from);
  if (filters.to) searchParams.set("to", filters.to);
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/inventory${query}`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        "재고를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<InventoryView>;
}

// 본사가 객실 유형별 일자 재고의 총량을 바꾼다. 멱원 재호출은 200으로
// 같은 결과를 돌려주고 재고가 두 번 바뀌지 않는다.
export type InventoryAdjustInput = {
  roomTypeId: string;
  adjustments: Array<{ stayDate: string; capacity: number }>;
};

export type InventoryAdjustResult = {
  hotelId: string;
  roomTypeId: string;
  days: InventoryDay[];
  created: boolean;
};

export async function adjustStaffInventory(
  token: string,
  hotelId: string,
  idempotencyKey: string,
  input: InventoryAdjustInput,
): Promise<InventoryAdjustResult> {
  const response = await fetch(`/api/staff/hotels/${hotelId}/inventory`, {
    method: "PATCH",
    headers: {
      "X-Staff-Session": token,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        inventoryAdjustFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as InventoryAdjustResult;
}

function inventoryAdjustFailureMessage(
  status: number,
  code: string | undefined,
) {
  if (status === 403) return "재고 조정은 본사 관리자만 할 수 있습니다.";
  if (status === 404 && code === "INVENTORY_DAY_NOT_FOUND") {
    return "해당 날짜의 재고가 없습니다. 객실 유형을 만들 때 심어둔 기간 안에서 조정할 수 있습니다.";
  }
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409) {
    return "총량을 내릴 수 없습니다. 이미 확정되거나 보류 중인 예약이 새 총량을 초과합니다.";
  }
  return "재고를 조정하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 여러 객실 유형의 여러 날짜 재고를 CSV로 내려받는다.
// SELECT만 사용하고 재고를 변경하지 않는다.
export async function exportInventoryCsv(
  token: string,
  hotelId: string,
  filters: { from?: string; to?: string } = {},
): Promise<string> {
  const searchParams = new URLSearchParams();
  if (filters.from) searchParams.set("from", filters.from);
  if (filters.to) searchParams.set("to", filters.to);
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/inventory/export${query}`,
    { headers: { "X-Staff-Session": token } },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        inventoryAdjustFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return response.text();
}

// 본사가 CSV로 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾼다.
// 한 파일의 모든 행을 한 트랜잭션에 처리해서 전부 성공하거나 전부 실패한다.
export type InventoryImportResult = {
  hotelId: string;
  totalRows: number;
  appliedRows: number;
  skippedRows: number;
  days: InventoryDay[];
  created: boolean;
};

export async function importInventoryCsv(
  token: string,
  hotelId: string,
  idempotencyKey: string,
  csv: string,
): Promise<InventoryImportResult> {
  // 엑셀이 UTF-8을 인식하게 하려고 BOM을 붙여서 올린다.
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/inventory/import`,
    {
      method: "POST",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "text/csv;charset=utf-8",
        "Idempotency-Key": idempotencyKey,
      },
      body: `\ufeff${csv}`,
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? importFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as InventoryImportResult;
}

function importFailureMessage(status: number, code: string | undefined) {
  if (status === 403) return "재고 일괄 업로드는 본사 관리자만 할 수 있습니다.";
  if (status === 400 && code === "INVENTORY_IMPORT_FORMAT") {
    return "CSV 형식이 올바르지 않습니다. 내보낸 파일을 그대로 올려 주세요.";
  }
  if (status === 404 && code === "INVENTORY_DAY_NOT_FOUND") {
    return "해당 날짜의 재고가 없습니다. 객실 유형을 만들 때 심어둔 기간 안에서만 올릴 수 있습니다.";
  }
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409) {
    return "총량을 내릴 수 없는 행이 있습니다. 확정되거나 보류 중인 예약이 새 총량을 초과합니다. 전체를 반영하지 않았습니다.";
  }
  return "재고를 올리지 못했습니다. 파일을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 객실 유형의 날짜 구간 판매를 중지·재개한다. 총량은 건드리지 않는다.
// 중지는 신규 판매만 막고 기존 예약은 그대로 둬서 재개하면 중지 전과 같은
// 재고가 돌아온다.
export type SalesStatusResult = {
  hotelId: string;
  roomTypeId: string;
  fromDate: string;
  toDate: string;
  status: "OPEN" | "STOPPED";
  stoppedDays: number;
  created: boolean;
};

export type SalesStatusRange = {
  hotelId: string;
  roomTypeId: string;
  stoppedRanges: Array<{ fromDate: string; toDate: string }>;
};

export async function setSalesStatus(
  token: string,
  hotelId: string,
  roomTypeId: string,
  idempotencyKey: string,
  input: { fromDate: string; toDate: string; status: "OPEN" | "STOPPED" },
): Promise<SalesStatusResult> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/sales-status`,
    {
      method: "PATCH",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(input),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        salesStatusFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as SalesStatusResult;
}

export async function getSalesStatus(
  token: string,
  hotelId: string,
  roomTypeId: string,
): Promise<SalesStatusRange> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/sales-status`,
    { headers: { "X-Staff-Session": token } },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ??
        salesStatusFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as SalesStatusRange;
}

function salesStatusFailureMessage(status: number, code: string | undefined) {
  if (status === 403)
    return "판매 중지는 본사 관리자만 할 수 있습니다. 지점 직원은 자기 지점의 중지 구간만 확인할 수 있습니다.";
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  if (status === 409 && code === "SALES_STATUS_CONFLICT") {
    return "판매 상태 변경이 동시에 처리됐습니다. 잠시 후 다시 시도해 주세요.";
  }
  return "판매 상태를 바꾸지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 객실 유형의 일자별 요금을 읽는다. SELECT만 사용한다.
export type RoomTypeRateDay = {
  stayDate: string;
  amountKrw: number;
};

export type RoomTypeRates = {
  hotelId: string;
  roomTypeId: string;
  ratePlanId: string | null;
  ratePlanName: string | null;
  days: RoomTypeRateDay[];
};

export async function getRoomTypeRates(
  token: string,
  hotelId: string,
  roomTypeId: string,
  filters: { from?: string; to?: string } = {},
): Promise<RoomTypeRates> {
  const searchParams = new URLSearchParams();
  if (filters.from) searchParams.set("from", filters.from);
  if (filters.to) searchParams.set("to", filters.to);
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/rates${query}`,
    { headers: { "X-Staff-Session": token } },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? rateReadFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as RoomTypeRates;
}

function rateReadFailureMessage(status: number, code: string | undefined) {
  if (status === 403) return "다른 지점의 가격은 조회할 수 없습니다.";
  if (status === 404 && code === "RATE_DAY_NOT_FOUND") {
    return "해당 날짜의 요금이 없습니다. 객실 유형을 만들 때 심어둔 기간 안에서 바꿀 수 있습니다.";
  }
  if (status === 404 && code === "ROOM_TYPE_RATE_PLAN_NOT_FOUND") {
    return "이 객실 유형에는 요금제가 없습니다. 먼저 객실 유형을 바르게 시드해 주세요.";
  }
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  return "일자별 요금을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 객실 유형의 일자별 요금을 바꾼다. 멱원 재호출은 200으로
// 같은 결과를 돌려주고 가격이 두 번 바뀌지 않는다.
export type RateAdjustInput = {
  roomTypeId: string;
  adjustments: Array<{ stayDate: string; amountKrw: number }>;
};

export type RateAdjustResult = {
  hotelId: string;
  roomTypeId: string;
  ratePlanId: string | null;
  days: RoomTypeRateDay[];
  created: boolean;
};

export async function adjustRoomTypeRates(
  token: string,
  hotelId: string,
  roomTypeId: string,
  idempotencyKey: string,
  input: RateAdjustInput,
): Promise<RateAdjustResult> {
  const response = await fetch(
    `/api/staff/hotels/${hotelId}/room-types/${roomTypeId}/rates`,
    {
      method: "PATCH",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(input),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? rateAdjustFailureMessage(response.status, error.code),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as RateAdjustResult;
}

function rateAdjustFailureMessage(status: number, code: string | undefined) {
  if (status === 403) return "가격 변경은 본사 관리자만 할 수 있습니다.";
  if (status === 404 && code === "RATE_DAY_NOT_FOUND") {
    return "해당 날짜의 요금이 없습니다. 객실 유형을 만들 때 심어둔 기간 안에서 바꿀 수 있습니다.";
  }
  if (status === 404 && code === "ROOM_TYPE_RATE_PLAN_NOT_FOUND") {
    return "이 객실 유형에는 요금제가 없습니다. 먼저 객실 유형을 바르게 시드해 주세요.";
  }
  if (status === 404)
    return "객실 유형을 찾을 수 없습니다. 목록을 새로고침해 주세요.";
  return "가격을 변경하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
}

// 본사가 체인 공통 정책의 현재값을 읽기 전용으로 확인한다.
export type CancellationPolicy = {
  refundCutoffDaysBefore: number;
  refundCutoffLocalTime: string;
  timezone: string;
};

export type ChainPolicy = {
  cancellation: CancellationPolicy;
  changeApprovalDirectLimitKrw: number;
  changeApprovalTtlSeconds: number;
  changeSettlementEnabled: boolean;
  revision: number;
};

async function policyRequest<T>(
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
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? policyFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as T;
}

function policyFailureMessage(status: number) {
  if (status === 403) return "공통 정책은 본사 관리자만 확인할 수 있습니다.";
  return "공통 정책을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function getChainPolicies(token: string) {
  return policyRequest<ChainPolicy>("/api/staff/policies", token);
}

// 본사가 취소 정책을 변경한다. 멱원 재호출은 200으로 같은 revision을 돌려준다.
export type UpdateCancellationPolicyInput = {
  refundCutoffDaysBefore: number;
  refundCutoffLocalTime: string;
};

export type UpdatedCancellationPolicy = {
  refundCutoffDaysBefore: number;
  refundCutoffLocalTime: string;
  timezone: string;
  revision: number;
  created: boolean;
};

export function updateCancellationPolicy(
  token: string,
  idempotencyKey: string,
  input: UpdateCancellationPolicyInput,
) {
  return policyRequest<UpdatedCancellationPolicy>(
    "/api/staff/policies/cancellation",
    token,
    {
      method: "PUT",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

// 본사가 지점 직접 승인 한도를 변경한다. 진행 중인 변경 요청은 저장된 한도를 유지한다.
export type UpdateChangeApprovalLimitInput = {
  directLimitKrw: number;
};

export type UpdatedChangeApprovalLimit = {
  directLimitKrw: number;
  revision: number;
  created: boolean;
};

export function updateChangeApprovalLimit(
  token: string,
  idempotencyKey: string,
  input: UpdateChangeApprovalLimitInput,
) {
  return policyRequest<UpdatedChangeApprovalLimit>(
    "/api/staff/policies/change-limit",
    token,
    {
      method: "PUT",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

// 본사가 예약 변경 승인 TTL을 변경한다. 진행 중인 변경 요청은 저장된
// 만료 시각을 유지하고 신규 요청부터 새 TTL이 적용된다.
export type UpdateChangeApprovalTtlInput = {
  approvalTtlSeconds: number;
};

export type UpdatedChangeApprovalTtl = {
  approvalTtlSeconds: number;
  revision: number;
  created: boolean;
};

export function updateChangeApprovalTtl(
  token: string,
  idempotencyKey: string,
  input: UpdateChangeApprovalTtlInput,
) {
  return policyRequest<UpdatedChangeApprovalTtl>(
    "/api/staff/policies/change-approval-ttl",
    token,
    {
      method: "PUT",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

// 본사가 정책 변경 이력을 최신순으로 읽는다. SELECT만 노출한다.
export type PolicyRevision = {
  key: string;
  summary: string;
  staffEmail: string;
  staffDisplayName: string;
  staffRole: string;
  createdAt: string;
};

export const policyRevisionLabels: Record<string, string> = {
  cancellation: "취소 정책",
  "change-approval": "예약 변경 승인 한도",
  "change-approval-ttl": "예약 변경 승인 TTL",
};

export type PolicyRevisions = {
  revisions: PolicyRevision[];
  totalCount: number;
  limit: number;
  offset: number;
};

export async function getPolicyRevisions(
  token: string,
  filters: { limit?: number; offset?: number } = {},
): Promise<PolicyRevisions> {
  const searchParams = new URLSearchParams();
  if (filters.limit) searchParams.set("limit", String(filters.limit));
  if (filters.offset) searchParams.set("offset", String(filters.offset));
  const query = searchParams.size ? `?${searchParams}` : "";
  return policyRequest<PolicyRevisions>(
    `/api/staff/policies/revisions${query}`,
    token,
  );
}

// 본사가 V29~V37 감사 표와 예약 변경 이력을 통합해 읽는다. SELECT만 노출한다.
export type AuditEventType =
  | "GUEST_UPDATE"
  | "PARTY_UPDATE"
  | "ROOM_REASSIGNMENT"
  | "STAY_CHANGE"
  | "CANCELLATION"
  | "ROOM_OPERATIONAL_TRANSITION"
  | "CHECKED_IN_ROOM_MOVE"
  | "CHANGE_REQUEST_EVENT";

export const auditEventLabels: Record<AuditEventType, string> = {
  GUEST_UPDATE: "예약자 정정",
  PARTY_UPDATE: "투숙 인원 변경",
  ROOM_REASSIGNMENT: "배정 객실 변경",
  STAY_CHANGE: "숙박 조건 변경",
  CANCELLATION: "예약 취소",
  ROOM_OPERATIONAL_TRANSITION: "객실 운영 상태",
  CHECKED_IN_ROOM_MOVE: "투숙 중 객실 이동",
  CHANGE_REQUEST_EVENT: "예약 변경 요청",
};

export type AuditEvent = {
  eventType: AuditEventType;
  createdAt: string;
  staffEmail: string;
  staffDisplayName: string;
  staffRole: string;
  hotelId: string;
  hotelName: string;
  reservationId: string | null;
  guestName: string | null;
  roomNumber: string | null;
  summary: string;
};

export type AuditEvents = {
  events: AuditEvent[];
  totalCount: number;
  limit: number;
  offset: number;
  masked: boolean;
};

export async function getAuditEvents(
  token: string,
  filters: {
    reservationId?: string;
    hotelId?: string;
    from?: string;
    to?: string;
    masked?: boolean;
    limit?: number;
    offset?: number;
  } = {},
): Promise<AuditEvents> {
  const searchParams = new URLSearchParams();
  if (filters.reservationId)
    searchParams.set("reservationId", filters.reservationId);
  if (filters.hotelId) searchParams.set("hotelId", filters.hotelId);
  if (filters.from) searchParams.set("from", filters.from);
  if (filters.to) searchParams.set("to", filters.to);
  if (filters.masked) searchParams.set("masked", "true");
  if (filters.limit) searchParams.set("limit", String(filters.limit));
  if (filters.offset) searchParams.set("offset", String(filters.offset));
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(`/api/staff/audit${query}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? auditFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as AuditEvents;
}

function auditFailureMessage(status: number) {
  if (status === 403) return "감사 이력은 본사 관리자만 확인할 수 있습니다.";
  return "감사 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 감사 이력 CSV 내보내기에 쓴다. BOM이 있어야 엑셀이 UTF-8로 읽는다.
// 마스킹이 켜져 있으면 서버가 가린 값이 그대로 내려온다.
export function buildAuditEventsCsv(view: AuditEvents): string {
  const header = [
    "발생 시각",
    "유형",
    "처리 직원",
    "처리 직원 이메일",
    "역할",
    "지점",
    "예약 id",
    "고객",
    "객실",
    "내용",
  ];
  const rows = view.events.map((event) => [
    event.createdAt,
    auditEventLabels[event.eventType as AuditEventType] ?? event.eventType,
    event.staffDisplayName,
    event.staffEmail,
    event.staffRole,
    event.hotelName,
    event.reservationId ?? "",
    event.guestName ?? "",
    event.roomNumber ?? "",
    event.summary,
  ]);
  return [header, ...rows]
    .map((cells) =>
      cells.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(","),
    )
    .join("\r\n");
}

// 본사가 지점별 운영 통계를 읽기 전용으로 확인한다. 매출은 취소·노쇼를 제외한다.
export type HotelOperationsMetrics = {
  hotelId: string;
  hotelName: string;
  region: string;
  reservations: number;
  cancelled: number;
  noShow: number;
  expired: number;
  revenueKrw: number;
  changeRequestsPending: number;
  changeRequestsCompleted: number;
  occupancyRate: number;
};

export type OperationsReportTotals = {
  reservations: number;
  cancelled: number;
  noShow: number;
  expired: number;
  revenueKrw: number;
  changeRequestsPending: number;
  changeRequestsCompleted: number;
};

export type OperationsReport = {
  from: string;
  to: string;
  days: number;
  hotels: HotelOperationsMetrics[];
  totals: OperationsReportTotals;
};

export async function getOperationsReport(
  token: string,
  filters: { from?: string; to?: string; hotelId?: string } = {},
): Promise<OperationsReport> {
  const searchParams = new URLSearchParams();
  if (filters.from) searchParams.set("from", filters.from);
  if (filters.to) searchParams.set("to", filters.to);
  if (filters.hotelId) searchParams.set("hotelId", filters.hotelId);
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(`/api/staff/reports/operations${query}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? reportFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<OperationsReport>;
}

function reportFailureMessage(status: number) {
  if (status === 403) return "운영 통계는 본사 관리자만 확인할 수 있습니다.";
  return "운영 통계를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 본사가 지점의 객실 유형별 매출을 읽는다. SELECT만 사용한다.
export type RoomTypeRevenueRow = {
  roomTypeId: string;
  roomTypeName: string;
  reservations: number;
  cancelled: number;
  noShow: number;
  revenueKrw: number;
  revenueShare: number;
};

export type RoomTypeRevenue = {
  from: string;
  to: string;
  days: number;
  hotelId: string;
  hotelName: string;
  roomTypes: RoomTypeRevenueRow[];
  totals: {
    reservations: number;
    cancelled: number;
    noShow: number;
    revenueKrw: number;
  };
};

export async function getRoomTypeRevenue(
  token: string,
  hotelId: string,
  filters: { from?: string; to?: string } = {},
): Promise<RoomTypeRevenue> {
  const searchParams = new URLSearchParams({ hotelId });
  if (filters.from) searchParams.set("from", filters.from);
  if (filters.to) searchParams.set("to", filters.to);
  const response = await fetch(
    `/api/staff/reports/operations/room-types?${searchParams}`,
    {
      headers: { "X-Staff-Session": token },
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? roomTypeRevenueFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return response.json() as Promise<RoomTypeRevenue>;
}

function roomTypeRevenueFailureMessage(status: number) {
  if (status === 403)
    return "객실 유형별 매출은 본사 관리자만 확인할 수 있습니다.";
  if (status === 404) return "지점을 찾을 수 없습니다. 지점을 선택해 주세요.";
  return "객실 유형별 매출을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

// 매출 CSV 내보내기에 쓴다. BOM이 있어야 엑셀이 UTF-8로 읽는다.
export function buildOperationsReportCsv(report: OperationsReport): string {
  const header = [
    "지점",
    "지역",
    "예약",
    "취소",
    "노쇼",
    "만료",
    "매출",
    "점유율",
    "변경 대기",
    "변경 완료",
  ];
  const rows = report.hotels.map((hotel) => [
    hotel.hotelName,
    hotel.region,
    String(hotel.reservations),
    String(hotel.cancelled),
    String(hotel.noShow),
    String(hotel.expired),
    String(hotel.revenueKrw),
    (hotel.occupancyRate * 100).toFixed(1),
    String(hotel.changeRequestsPending),
    String(hotel.changeRequestsCompleted),
  ]);
  return [header, ...rows]
    .map((cells) =>
      cells.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(","),
    )
    .join("\r\n");
}

// 고객이 호텔에 보낸 요청·문의. 예약 변경·취소는 전용 API가 있으므로
// 여기서는 그 외의 요청만 다룬다.
export type GuestRequestType =
  | "ROOM_REQUEST"
  | "AMENITY_REQUEST"
  | "REFUND_INQUIRY"
  | "GENERAL_INQUIRY"
  | "OTHER";

export type GuestRequestStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "RESOLVED"
  | "CLOSED";

export const guestRequestTypeLabels: Record<GuestRequestType, string> = {
  ROOM_REQUEST: "객실 요청",
  AMENITY_REQUEST: "편의 요청",
  REFUND_INQUIRY: "환불 문의",
  GENERAL_INQUIRY: "일반 문의",
  OTHER: "기타",
};

export const guestRequestStatusLabels: Record<GuestRequestStatus, string> = {
  OPEN: "접수",
  IN_PROGRESS: "처리 중",
  RESOLVED: "해결",
  CLOSED: "종료",
};

export type GuestRequestSummary = {
  id: string;
  hotelId: string;
  hotelName: string;
  reservationId: string | null;
  requestType: GuestRequestType;
  subject: string;
  guestName: string;
  status: GuestRequestStatus;
  priority: string;
  assignedDisplayName: string | null;
  createdAt: string;
  updatedAt: string;
};

export type GuestRequestEvent = {
  id: string;
  eventType: string;
  fromStatus: GuestRequestStatus | null;
  toStatus: GuestRequestStatus;
  actorDisplayName: string;
  note: string | null;
  createdAt: string;
};

export type GuestRequestDetail = {
  id: string;
  hotelId: string;
  hotelName: string;
  reservationId: string | null;
  requestType: GuestRequestType;
  subject: string;
  body: string;
  guestName: string;
  guestEmail: string;
  guestPhone: string | null;
  status: GuestRequestStatus;
  priority: string;
  assignedTo: string | null;
  assignedDisplayName: string | null;
  createdAt: string;
  updatedAt: string;
  events: GuestRequestEvent[];
};

export type GuestRequestList = {
  requests: GuestRequestSummary[];
  totalCount: number;
  status: string;
  hotelId: string;
  limit: number;
  offset: number;
};

function guestRequestFailureMessage(status: number) {
  if (status === 403) return "고객 요청은 본사 관리자만 확인할 수 있습니다.";
  if (status === 404) return "고객 요청을 찾을 수 없습니다.";
  return "고객 요청을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export async function getGuestRequests(
  token: string,
  filters: {
    status?: GuestRequestStatus;
    hotelId?: string;
    limit?: number;
    offset?: number;
  } = {},
): Promise<GuestRequestList> {
  const searchParams = new URLSearchParams();
  if (filters.status) searchParams.set("status", filters.status);
  if (filters.hotelId) searchParams.set("hotelId", filters.hotelId);
  if (filters.limit) searchParams.set("limit", String(filters.limit));
  if (filters.offset) searchParams.set("offset", String(filters.offset));
  const query = searchParams.size ? `?${searchParams}` : "";
  const response = await fetch(`/api/staff/guest-requests${query}`, {
    headers: { "X-Staff-Session": token },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? guestRequestFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as GuestRequestList;
}

export async function getGuestRequest(
  token: string,
  requestId: string,
): Promise<GuestRequestDetail> {
  const response = await fetch(
    `/api/staff/guest-requests/${encodeURIComponent(requestId)}`,
    { headers: { "X-Staff-Session": token } },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? guestRequestFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as GuestRequestDetail;
}

// 직원이 고객 요청의 처리 상태를 바꾼다. 멱원 재호출은 200으로 같은 결과를
// 돌려주고 요청이 두 번 바뀌지 않는다.
export type GuestRequestTransitionInput = {
  status: GuestRequestStatus;
  assignTo?: string | null;
  resolutionNote?: string | null;
};

export async function transitionGuestRequest(
  token: string,
  requestId: string,
  idempotencyKey: string,
  input: GuestRequestTransitionInput,
): Promise<GuestRequestDetail> {
  const response = await fetch(
    `/api/staff/guest-requests/${encodeURIComponent(requestId)}/transition`,
    {
      method: "POST",
      headers: {
        "X-Staff-Session": token,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(input),
    },
  );
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorPayload;
    throw new StaffApiError(
      error.message ?? guestRequestFailureMessage(response.status),
      response.status,
      error.code,
    );
  }
  return (await response.json()) as GuestRequestDetail;
}
