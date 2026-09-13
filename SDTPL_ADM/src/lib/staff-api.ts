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


type SessionResponse = { token: string; staff: StaffPrincipal };



export class StaffApiError extends Error {
  constructor(message: string, readonly status?: number, readonly code?: string) {
    super(message);
    this.name = "StaffApiError";
  }
}

type ApiErrorPayload = { message?: string; code?: string };


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
