"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { MediaDraftUsageReplacementDialog } from "@/components/hotel-admin/media-draft-usage-replacement-dialog";
import {
  archiveWebsiteMedia,
  deleteWebsiteMedia,
  getWebsiteMedia,
  getWebsiteMediaUsages,
  retryWebsiteMediaVariant,
  restoreWebsiteMedia,
  StaffApiError,
  updateWebsiteMedia,
  uploadWebsiteMedia,
  type WebsiteMediaAsset,
  type WebsiteMediaUsage,
  type WebsiteMediaVariant,
} from "@/lib/staff-api";

const customerWebOrigin = (process.env.NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN ?? "http://127.0.0.1:4000").replace(/\/$/, "");
const deleteDateFormatter = new Intl.DateTimeFormat("ko-KR", { dateStyle: "long", timeZone: "Asia/Seoul" });

function previewUrl(deliveryUrl: string) {
  if (deliveryUrl.startsWith("/images/")) return `${customerWebOrigin}${deliveryUrl}`;
  return deliveryUrl;
}

function imageDetails(asset: WebsiteMediaAsset) {
  const size = asset.byteSize < 1024 * 1024
    ? `${Math.max(1, Math.round(asset.byteSize / 1024))} KB`
    : `${(asset.byteSize / (1024 * 1024)).toFixed(1)} MB`;
  return `${asset.width} × ${asset.height} · ${size}`;
}

function usageStateLabel(state: WebsiteMediaUsage["documentState"]) {
  return state === "PUBLISHED" ? "발행" : "초안";
}

function assetStatusLabel(status: WebsiteMediaAsset["status"]) {
  return status === "ACTIVE" ? "활성" : "보관됨";
}

function variantStatusLabel(status: WebsiteMediaVariant["status"]) {
  if (status === "PENDING") return "변환 대기 중";
  if (status === "PROCESSING") return "변환 중";
  if (status === "READY") return "변환 완료";
  return "변환 실패";
}

function isActiveVariant(status: WebsiteMediaVariant["status"]) {
  return status === "PENDING" || status === "PROCESSING";
}

function permanentDeleteState(asset: WebsiteMediaAsset) {
  const uploaded = asset.deliveryUrl.startsWith("/api/website/media/");
  if (asset.status !== "ARCHIVED" || !uploaded || !asset.permanentDeleteAvailableAt) {
    return { eligible: false, label: "영구 삭제 대상이 아닙니다." };
  }
  const availableAt = new Date(asset.permanentDeleteAvailableAt);
  if (Number.isNaN(availableAt.getTime())) {
    return { eligible: false, label: "영구 삭제 가능일을 확인할 수 없습니다." };
  }
  return availableAt.getTime() <= Date.now()
    ? { eligible: true, label: "영구 삭제 가능" }
    : { eligible: false, label: `영구 삭제 가능일 ${deleteDateFormatter.format(availableAt)}` };
}

function AssetCard({ asset, selected, disabled = false, onSelect }: {
  asset: WebsiteMediaAsset;
  selected: boolean;
  disabled?: boolean;
  onSelect: () => void;
}) {
  const archivedUpload = asset.status === "ARCHIVED" && asset.deliveryUrl.startsWith("/api/website/media/");
  return (
    <Button
      type="button"
      variant={selected ? "default" : "outline"}
      aria-label={`${asset.displayName} 선택`}
      aria-pressed={selected}
      disabled={disabled}
      onClick={onSelect}
      className="h-auto items-stretch overflow-hidden p-0 text-left whitespace-normal"
    >
      <div className="grid w-full grid-cols-[96px_1fr] gap-3 p-3">
        <div className="aspect-video overflow-hidden rounded-md bg-muted">
          {archivedUpload
            ? <div className="grid size-full place-items-center p-2 text-center text-xs text-muted-foreground">보관된 업로드 자산은 미리보기를 표시하지 않습니다.</div>
            : <img src={previewUrl(asset.deliveryUrl)} alt="" className="size-full object-cover" />}
        </div>
        <div className="min-w-0 self-center">
          <p className="truncate font-medium">{asset.displayName}</p>
          <p className="mt-1 text-xs opacity-75">{imageDetails(asset)}</p>
          <p className="mt-1 text-xs opacity-75">사용 {asset.usageCount}곳 · {assetStatusLabel(asset.status)}</p>
        </div>
      </div>
    </Button>
  );
}

export function MediaPickerDialog({ token, open, onOpenChange, initialAssetId, protectedAssetIds, replacement, onSelect }: {
  token: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialAssetId: string;
  protectedAssetIds: readonly string[];
  replacement?: { deliveryUrl: string; altText: string };
  onSelect: (asset: WebsiteMediaAsset) => void;
}) {
  const [assets, setAssets] = useState<WebsiteMediaAsset[]>([]);
  const [selectedAssetId, setSelectedAssetId] = useState(initialAssetId);
  const [usages, setUsages] = useState<WebsiteMediaUsage[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingUsages, setLoadingUsages] = useState(false);
  const [catalogError, setCatalogError] = useState("");
  const [uploadError, setUploadError] = useState("");
  const [uploadNotice, setUploadNotice] = useState("");
  const [uploading, setUploading] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [defaultAltText, setDefaultAltText] = useState("");
  const [metadataDisplayName, setMetadataDisplayName] = useState("");
  const [metadataAltText, setMetadataAltText] = useState("");
  const [metadataError, setMetadataError] = useState("");
  const [metadataNotice, setMetadataNotice] = useState("");
  const [variantError, setVariantError] = useState("");
  const [savingMetadata, setSavingMetadata] = useState(false);
  const [changingStatus, setChangingStatus] = useState(false);
  const [archiveConfirmationOpen, setArchiveConfirmationOpen] = useState(false);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [replacementConfirmationOpen, setReplacementConfirmationOpen] = useState(false);
  const [draftReplacementOpen, setDraftReplacementOpen] = useState(false);
  const [replacementAssetId, setReplacementAssetId] = useState<string | null>(null);
  const [retryingVariantWidths, setRetryingVariantWidths] = useState<Set<640 | 1280>>(new Set());
  const uploadRequestGeneration = useRef(0);
  const catalogRequestGeneration = useRef(0);
  const usageRequestGeneration = useRef(0);

  const selectedAsset = useMemo(
    () => assets.find((asset) => asset.id === selectedAssetId) ?? null,
    [assets, selectedAssetId],
  );
  const activeAssets = useMemo(() => assets.filter((asset) => asset.status === "ACTIVE"), [assets]);
  const archivedAssets = useMemo(() => assets.filter((asset) => asset.status === "ARCHIVED"), [assets]);
  const selectedByCurrentDraft = Boolean(selectedAsset && protectedAssetIds.includes(selectedAsset.id));
  const selectedAssetIdForPolling = selectedAsset?.id ?? "";
  const selectedAssetHasActiveVariant = selectedAsset?.variants.some((item) => isActiveVariant(item.status)) ?? false;

  const refreshCatalog = useCallback(async (preferredAssetId: string, resetMetadata: boolean) => {
    const requestGeneration = ++catalogRequestGeneration.current;
    setLoading(true);
    setCatalogError("");
    try {
      const nextAssets = await getWebsiteMedia(token, true);
      if (catalogRequestGeneration.current !== requestGeneration) return;
      const nextSelected = nextAssets.find((asset) => asset.id === preferredAssetId)
        ?? nextAssets.find((asset) => asset.status === "ACTIVE")
        ?? nextAssets[0]
        ?? null;
      setAssets(nextAssets);
      setSelectedAssetId(nextSelected?.id ?? "");
      if (resetMetadata || nextSelected?.id !== preferredAssetId) {
        setMetadataDisplayName(nextSelected?.displayName ?? "");
        setMetadataAltText(nextSelected?.defaultAltText ?? "");
      }
    } catch (cause) {
      if (catalogRequestGeneration.current === requestGeneration) {
        setCatalogError(cause instanceof Error ? cause.message : "미디어 목록을 불러오지 못했습니다.");
      }
    } finally {
      if (catalogRequestGeneration.current === requestGeneration) setLoading(false);
    }
  }, [token]);

  const refreshUsages = useCallback(async (mediaId: string) => {
    const requestGeneration = ++usageRequestGeneration.current;
    setLoadingUsages(true);
    try {
      const nextUsages = await getWebsiteMediaUsages(token, mediaId);
      if (usageRequestGeneration.current === requestGeneration) setUsages(nextUsages);
    } catch {
      if (usageRequestGeneration.current === requestGeneration) setUsages([]);
    } finally {
      if (usageRequestGeneration.current === requestGeneration) setLoadingUsages(false);
    }
  }, [token]);

  useEffect(() => {
    if (!open) return;
    setReplacementAssetId(null);
    setUploading(false);
    setUploadError("");
    setUploadNotice("");
    setSelectedAssetId(initialAssetId);
    setMetadataError("");
    setMetadataNotice("");
    void refreshCatalog(initialAssetId, true);
    return () => { catalogRequestGeneration.current += 1; uploadRequestGeneration.current += 1; };
  }, [initialAssetId, open, refreshCatalog]);

  useEffect(() => {
    if (!open || !selectedAssetId) {
      usageRequestGeneration.current += 1;
      setLoadingUsages(false);
      setUsages([]);
      return;
    }
    void refreshUsages(selectedAssetId);
  }, [open, refreshUsages, selectedAssetId]);

  useEffect(() => {
    if (!open || !selectedAssetIdForPolling || !selectedAssetHasActiveVariant) return;
    const timer = window.setTimeout(() => {
      void refreshCatalog(selectedAssetIdForPolling, false);
    }, 2_000);
    return () => window.clearTimeout(timer);
  }, [open, refreshCatalog, selectedAssetHasActiveVariant, selectedAssetIdForPolling]);

  function replaceAsset(nextAsset: WebsiteMediaAsset) {
    catalogRequestGeneration.current += 1;
    setAssets((current) => current.map((asset) => asset.id === nextAsset.id ? nextAsset : asset));
  }

  function selectAsset(asset: WebsiteMediaAsset) {
    setSelectedAssetId(asset.id);
    setMetadataDisplayName(asset.displayName);
    setMetadataAltText(asset.defaultAltText);
    setMetadataError("");
    setMetadataNotice("");
  }

  function isMediaConflict(cause: unknown) {
    return cause instanceof StaffApiError
      && cause.status === 409
      && (cause.code === "WEBSITE_MEDIA_VERSION_CONFLICT" || cause.code === "WEBSITE_MEDIA_IN_USE");
  }

  async function recoverFromMediaConflict(mediaId: string) {
    await Promise.all([refreshCatalog(mediaId, true), refreshUsages(mediaId)]);
    setMetadataError("최신 자산 정보를 불러왔습니다. 변경 내용을 확인한 뒤 다시 저장해 주세요.");
  }

  async function retryVariant(targetWidth: 640 | 1280) {
    if (!selectedAsset) return;
    setVariantError("");
    setRetryingVariantWidths((current) => new Set(current).add(targetWidth));
    try {
      replaceAsset(await retryWebsiteMediaVariant(token, selectedAsset.id, targetWidth));
    } catch (cause) {
      setVariantError(cause instanceof Error ? cause.message : `${targetWidth}px 변환을 다시 시도하지 못했습니다.`);
    } finally {
      setRetryingVariantWidths((current) => {
        const next = new Set(current);
        next.delete(targetWidth);
        return next;
      });
    }
  }

  async function upload() {
    setReplacementAssetId(null);
    if (!file || !displayName.trim() || !defaultAltText.trim()) {
      setUploadNotice("");
      setUploadError("이미지 파일, 자산명, 기본 대체 텍스트를 모두 입력해 주세요.");
      return;
    }
    setUploading(true);
    const requestGeneration = ++uploadRequestGeneration.current;
    setUploadError("");
    setUploadNotice("");
    try {
      const asset = await uploadWebsiteMedia(token, { file, displayName: displayName.trim(), defaultAltText: defaultAltText.trim() });
      if (uploadRequestGeneration.current !== requestGeneration) return;
      catalogRequestGeneration.current += 1;
      setAssets((current) => [asset, ...current.filter((item) => item.id !== asset.id)]);
      setSelectedAssetId(asset.id);
      setReplacementAssetId(asset.id);
      setFile(null);
      setDisplayName("");
      setDefaultAltText("");
      setUploadNotice("이미지가 업로드되었습니다. 선택 후 이 페이지의 대체 텍스트를 확인해 주세요.");
      void refreshCatalog(asset.id, false);
    } catch (cause) {
      if (uploadRequestGeneration.current === requestGeneration) setUploadError(cause instanceof Error ? cause.message : "이미지를 업로드하지 못했습니다.");
    } finally {
      if (uploadRequestGeneration.current === requestGeneration) setUploading(false);
    }
  }

  async function saveMetadata() {
    if (!selectedAsset) return;
    setSavingMetadata(true);
    setMetadataError("");
    setMetadataNotice("");
    try {
      const asset = await updateWebsiteMedia(token, selectedAsset.id, {
        displayName: metadataDisplayName.trim(),
        defaultAltText: metadataAltText.trim(),
        expectedVersion: selectedAsset.version,
      });
      replaceAsset(asset);
      setMetadataDisplayName(asset.displayName);
      setMetadataAltText(asset.defaultAltText);
      setMetadataNotice("자산 정보를 저장했습니다. 기존 페이지의 대체 텍스트는 바뀌지 않습니다.");
      void refreshCatalog(asset.id, false);
      void refreshUsages(asset.id);
    } catch (cause) {
      if (isMediaConflict(cause)) await recoverFromMediaConflict(selectedAsset.id);
      else setMetadataError(cause instanceof Error ? cause.message : "자산 정보를 저장하지 못했습니다.");
    } finally {
      setSavingMetadata(false);
    }
  }

  async function archiveSelected() {
    if (!selectedAsset) return;
    setChangingStatus(true);
    setMetadataError("");
    setMetadataNotice("");
    try {
      const asset = await archiveWebsiteMedia(token, selectedAsset.id, { expectedVersion: selectedAsset.version });
      replaceAsset(asset);
      setMetadataNotice("자산을 보관했습니다. 새 페이지에서는 선택할 수 없습니다.");
      void refreshCatalog(asset.id, false);
      void refreshUsages(asset.id);
    } catch (cause) {
      if (isMediaConflict(cause)) await recoverFromMediaConflict(selectedAsset.id);
      else setMetadataError(cause instanceof Error ? cause.message : "자산을 보관하지 못했습니다.");
    } finally {
      setChangingStatus(false);
      setArchiveConfirmationOpen(false);
    }
  }

  async function restoreSelected() {
    if (!selectedAsset) return;
    setChangingStatus(true);
    setMetadataError("");
    setMetadataNotice("");
    try {
      const asset = await restoreWebsiteMedia(token, selectedAsset.id, { expectedVersion: selectedAsset.version });
      replaceAsset(asset);
      setMetadataNotice("자산을 복원했습니다. 새 페이지에서 다시 선택할 수 있습니다.");
      void refreshCatalog(asset.id, false);
      void refreshUsages(asset.id);
    } catch (cause) {
      if (isMediaConflict(cause)) await recoverFromMediaConflict(selectedAsset.id);
      else setMetadataError(cause instanceof Error ? cause.message : "자산을 복원하지 못했습니다.");
    } finally {
      setChangingStatus(false);
    }
  }

  async function permanentlyDeleteSelected() {
    if (!selectedAsset) return;
    setChangingStatus(true);
    setMetadataError("");
    setMetadataNotice("");
    try {
      await deleteWebsiteMedia(token, selectedAsset.id, { expectedVersion: selectedAsset.version });
      const remaining = assets.filter((asset) => asset.id !== selectedAsset.id);
      const nextSelected = remaining.find((asset) => asset.status === "ACTIVE") ?? remaining[0] ?? null;
      setAssets(remaining);
      setSelectedAssetId(nextSelected?.id ?? null);
      setMetadataDisplayName(nextSelected?.displayName ?? "");
      setMetadataAltText(nextSelected?.defaultAltText ?? "");
      setUsages([]);
      setMetadataNotice("자산을 영구 삭제했습니다. 이 작업은 되돌릴 수 없습니다.");
      if (nextSelected) void refreshUsages(nextSelected.id);
    } catch (cause) {
      if (isMediaConflict(cause)) await recoverFromMediaConflict(selectedAsset.id);
      else setMetadataError(cause instanceof Error ? cause.message : "자산을 영구 삭제하지 못했습니다.");
    } finally {
      setChangingStatus(false);
      setDeleteConfirmationOpen(false);
      setDraftReplacementOpen(false);
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      catalogRequestGeneration.current += 1;
      usageRequestGeneration.current += 1;
      uploadRequestGeneration.current += 1;
      setReplacementConfirmationOpen(false);
      setReplacementAssetId(null);
      setFile(null);
      setDisplayName("");
      setDefaultAltText("");
      setMetadataDisplayName("");
      setMetadataAltText("");
      setMetadataError("");
      setMetadataNotice("");
      setVariantError("");
      setRetryingVariantWidths(new Set());
      setArchiveConfirmationOpen(false);
      setDeleteConfirmationOpen(false);
    }
    onOpenChange(nextOpen);
  }

  function close() { handleOpenChange(false); }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[90vh] max-w-[calc(100%-1rem)] flex-col gap-0 p-0 sm:max-w-5xl" showCloseButton>
        <DialogHeader className="shrink-0 border-b p-5 pr-12">
          <DialogTitle>{replacement ? "미디어 파일 교체" : "미디어 선택"}</DialogTitle>
          <DialogDescription>{replacement ? "새 PNG/JPEG 파일을 업로드한 뒤 교체를 확인합니다. 기존 파일은 유지하며, 현재 편집 위치만 바꿉니다. 업로드 후 취소한 자산은 카탈로그에 남습니다." : "활성 자산을 선택하거나 PNG/JPEG 파일을 업로드합니다. 선택은 하단 버튼을 눌러야 현재 페이지에 적용됩니다."}</DialogDescription>
        </DialogHeader>

        <div className="min-h-0 overflow-y-auto p-5">
          <section className="rounded-xl border bg-muted/20 p-4">
            <div className="grid gap-3 md:grid-cols-[1.15fr_1fr_1fr_auto] md:items-end">
              <label className="grid gap-1 text-sm font-medium">이미지 파일
                <Input aria-label="이미지 파일" type="file" accept="image/png,image/jpeg" disabled={uploading || savingMetadata || changingStatus} onChange={(event) => { setFile(event.target.files?.[0] ?? null); setReplacementAssetId(null); }} />
              </label>
              <label className="grid gap-1 text-sm font-medium">자산명
                <Input aria-label="자산명" value={displayName} maxLength={160} disabled={savingMetadata || changingStatus} onChange={(event) => setDisplayName(event.target.value)} />
              </label>
              <label className="grid gap-1 text-sm font-medium">기본 대체 텍스트
                <Input aria-label="기본 대체 텍스트" value={defaultAltText} maxLength={200} disabled={savingMetadata || changingStatus} onChange={(event) => setDefaultAltText(event.target.value)} />
              </label>
              <Button type="button" onClick={() => void upload()} disabled={uploading || savingMetadata || changingStatus}>{uploading ? "업로드 중" : "업로드"}</Button>
            </div>
            {uploadNotice && <p role="status" className="mt-3 text-sm text-emerald-700">{uploadNotice}</p>}
            {uploadError && <p role="alert" className="mt-3 text-sm text-destructive">{uploadError}</p>}
          </section>

          <section className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_280px]">
            <div>
              <div className="flex items-baseline justify-between gap-3"><h3 className="font-medium">선택 가능한 자산</h3><p className="text-xs text-muted-foreground">{activeAssets.length}개</p></div>
              {catalogError && <p role="alert" className="mt-3 text-sm text-destructive">{catalogError}</p>}
              {loading ? <p className="mt-4 text-sm text-muted-foreground">미디어 목록을 불러오는 중입니다.</p> : (
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {activeAssets.map((asset) => <AssetCard key={asset.id} asset={asset} selected={asset.id === selectedAssetId} disabled={savingMetadata || changingStatus} onSelect={() => selectAsset(asset)} />)}
                </div>
              )}
              {!loading && activeAssets.length === 0 && <p className="mt-3 text-sm text-muted-foreground">선택 가능한 활성 자산이 없습니다.</p>}

              {archivedAssets.length > 0 && <section className="mt-6 border-t pt-5">
                <div className="flex items-baseline justify-between gap-3"><h3 className="font-medium">보관 자산</h3><p className="text-xs text-muted-foreground">{archivedAssets.length}개</p></div>
                <p className="mt-1 text-xs text-muted-foreground">보관 자산은 새 페이지에 선택할 수 없으며, 필요한 경우 여기서 복원합니다.</p>
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {archivedAssets.map((asset) => <AssetCard key={asset.id} asset={asset} selected={asset.id === selectedAssetId} disabled={savingMetadata || changingStatus} onSelect={() => selectAsset(asset)} />)}
                </div>
              </section>}
            </div>

            <aside className="grid h-fit gap-4 rounded-xl border bg-muted/20 p-4">
              {!replacement && <section>
                <h3 className="font-medium">자산 정보</h3>
                {!selectedAsset ? <p className="mt-3 text-sm text-muted-foreground">자산을 선택하면 메타데이터와 사용 위치를 표시합니다.</p> : <div className="mt-3 grid gap-3">
                  <p className="text-xs text-muted-foreground">{assetStatusLabel(selectedAsset.status)} · 버전 {selectedAsset.version} · 사용 위치 {selectedAsset.usageCount}곳</p>
                  <label className="grid gap-1 text-sm font-medium">선택한 자산 이름
                    <Input aria-label="선택한 자산 이름" value={metadataDisplayName} maxLength={160} disabled={savingMetadata || changingStatus} onChange={(event) => setMetadataDisplayName(event.target.value)} />
                  </label>
                  <label className="grid gap-1 text-sm font-medium">선택한 자산 기본 대체 텍스트
                    <Input aria-label="선택한 자산 기본 대체 텍스트" value={metadataAltText} maxLength={200} disabled={savingMetadata || changingStatus} onChange={(event) => setMetadataAltText(event.target.value)} />
                  </label>
                  <Button type="button" variant="outline" onClick={() => void saveMetadata()} disabled={savingMetadata || changingStatus}>{savingMetadata ? "저장 중" : "자산 정보 저장"}</Button>
                  {selectedAsset.status === "ACTIVE" ? <>
                    {usages.some((usage) => usage.documentState === "DRAFT") && <Button type="button" variant="outline" onClick={() => setDraftReplacementOpen(true)} disabled={savingMetadata || changingStatus || loadingUsages}>초안 사용 위치 일괄 교체</Button>}
                    <Button type="button" variant="outline" onClick={() => setArchiveConfirmationOpen(true)} disabled={savingMetadata || changingStatus || selectedAsset.usageCount > 0 || selectedByCurrentDraft}>보관</Button>
                    {selectedAsset.usageCount > 0 && <p className="text-xs text-muted-foreground">사용 위치가 있어 보관할 수 없습니다.</p>}
                    {selectedAsset.usageCount === 0 && selectedByCurrentDraft && <p className="text-xs text-muted-foreground">현재 페이지의 저장되지 않은 초안에서 선택되어 보관할 수 없습니다.</p>}
                  </> : <>
                    <Button type="button" variant="outline" onClick={() => void restoreSelected()} disabled={savingMetadata || changingStatus}>{changingStatus ? "복원 중" : "복원"}</Button>
                    <p className="text-xs text-muted-foreground">{permanentDeleteState(selectedAsset).label}</p>
                    <Button type="button" variant="destructive" onClick={() => setDeleteConfirmationOpen(true)} disabled={savingMetadata || changingStatus || !permanentDeleteState(selectedAsset).eligible}>영구 삭제</Button>
                  </>}
                  {metadataNotice && <p role="status" className="text-sm text-emerald-700">{metadataNotice}</p>}
                  {metadataError && <p role="alert" className="text-sm text-destructive">{metadataError}</p>}
                </div>}
              </section>}

              <section aria-label="반응형 이미지" className="border-t pt-4">
                <h3 className="font-medium">반응형 이미지</h3>
                {!selectedAsset ? <p className="mt-3 text-sm text-muted-foreground">자산을 선택하면 변환 상태를 표시합니다.</p> : selectedAsset.variants.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">생성된 반응형 이미지가 없습니다.</p> : (
                  <ul className="mt-3 grid gap-3">
                    {selectedAsset.variants.map((variant) => {
                      const retrying = retryingVariantWidths.has(variant.targetWidth);
                      return <li key={variant.id} className="rounded-lg border bg-background p-3 text-sm">
                        <p className="font-medium">{variant.targetWidth}px · {variantStatusLabel(variant.status)}{variant.status === "FAILED" ? ` · ${variant.attemptCount}/3회` : ""}</p>
                        {variant.status === "READY" && variant.deliveryUrl && variant.width && variant.height && <a href={variant.deliveryUrl} target="_blank" rel="noreferrer" className="mt-1 inline-block text-sm text-primary underline underline-offset-4">{variant.width} × {variant.height} · WebP</a>}
                        {variant.status === "FAILED" && <>
                          {variant.lastError && <p className="mt-1 text-xs text-muted-foreground">{variant.lastError}</p>}
                          <Button type="button" variant="outline" size="sm" className="mt-3" onClick={() => void retryVariant(variant.targetWidth)} disabled={retrying}>{retrying ? `${variant.targetWidth}px 다시 시도 중` : `${variant.targetWidth}px 다시 시도`}</Button>
                        </>}
                      </li>;
                    })}
                  </ul>
                )}
                {variantError && <p role="alert" className="mt-3 text-sm text-destructive">{variantError}</p>}
              </section>

              <section className="border-t pt-4">
                <h3 className="font-medium">사용 위치</h3>
                {!selectedAsset ? <p className="mt-3 text-sm text-muted-foreground">자산을 선택하면 초안과 발행 사용 위치를 표시합니다.</p> : loadingUsages ? <p className="mt-3 text-sm text-muted-foreground">사용 위치를 불러오는 중입니다.</p> : usages.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">현재 등록된 사용 위치가 없습니다.</p> : (
                  <ul className="mt-3 grid gap-3">
                    {usages.map((usage) => (
                      <li key={`${usage.pageId}-${usage.locale ?? "ko"}-${usage.documentState}-${usage.fieldPath}`} className="rounded-lg border bg-background p-3 text-sm">
                        <p className="font-medium">{usage.pageLabel} · {usage.locale === "en" ? "영어" : "한국어"} · {usageStateLabel(usage.documentState)}</p>
                        <p className="mt-1 break-all text-xs text-muted-foreground">{usage.pagePath} · {usage.fieldPath}</p>
                        <p className="mt-1 text-xs text-muted-foreground">대체 텍스트: {usage.altText}</p>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </aside>
          </section>
        </div>

        <DialogFooter className="shrink-0">
          <Button type="button" variant="outline" onClick={close}>취소</Button>
          <Button type="button" disabled={!selectedAsset || selectedAsset.status !== "ACTIVE" || loading || uploading || savingMetadata || changingStatus || Boolean(replacement && selectedAsset.id !== replacementAssetId)} onClick={() => { if (selectedAsset?.status === "ACTIVE") { if (replacement) setReplacementConfirmationOpen(true); else { onSelect(selectedAsset); close(); } } }}>{replacement ? "교체 확인" : "선택"}</Button>
        </DialogFooter>
      </DialogContent>
      {!replacement && selectedAsset && <MediaDraftUsageReplacementDialog
        token={token}
        open={draftReplacementOpen}
        sourceAsset={selectedAsset}
        onOpenChange={setDraftReplacementOpen}
        onCompleted={(result, targetAsset) => {
          setMetadataNotice(`초안 ${result.changedDraftCount}개에서 사용 위치 ${result.replacedUsageCount}곳을 교체했습니다.`);
          setAssets((current) => [targetAsset, ...current.filter((asset) => asset.id !== targetAsset.id)]);
          void refreshCatalog(selectedAsset.id, false);
          void refreshUsages(selectedAsset.id);
        }}
      />}
      <AlertDialog open={archiveConfirmationOpen} onOpenChange={setArchiveConfirmationOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>미디어를 보관할까요?</AlertDialogTitle>
            <AlertDialogDescription>보관된 자산은 새 페이지에서 선택할 수 없습니다. 파일은 삭제하지 않으며 나중에 다시 복원할 수 있습니다.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction disabled={savingMetadata || changingStatus} onClick={() => void archiveSelected()}>보관하기</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog open={deleteConfirmationOpen} onOpenChange={setDeleteConfirmationOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>보관 자산을 영구 삭제할까요?</AlertDialogTitle>
            <AlertDialogDescription><strong>{selectedAsset?.displayName}</strong> 자산과 저장된 원본 파일을 영구 삭제합니다. 이 작업은 되돌릴 수 없습니다.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={changingStatus}>취소</AlertDialogCancel>
            <AlertDialogAction variant="destructive" disabled={changingStatus} onClick={(event) => { event.preventDefault(); void permanentlyDeleteSelected(); }}>영구 삭제</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog open={replacementConfirmationOpen} onOpenChange={setReplacementConfirmationOpen}>
        <AlertDialogContent className="max-h-[90vh] overflow-y-auto">
          <AlertDialogHeader>
            <AlertDialogTitle>현재 이미지 파일을 교체할까요?</AlertDialogTitle>
            <AlertDialogDescription>현재 편집 위치만 새 자산으로 교체합니다. 다른 사용 위치와 발행본은 바뀌지 않습니다. 기존 파일과 발행 이력은 유지하며, 고객 공개에는 초안 저장 후 발행이 필요합니다. 페이지별 대체 텍스트는 유지하므로 새 이미지에 맞는지 확인해 주세요.</AlertDialogDescription>
          </AlertDialogHeader>
          {replacement && selectedAsset && <div className="grid gap-4 sm:grid-cols-2">
            <figure className="min-w-0"><img src={previewUrl(replacement.deliveryUrl)} alt="기존 이미지" className="aspect-video w-full rounded-md object-cover" /><figcaption className="mt-2 text-sm">기존 이미지</figcaption></figure>
            <figure className="min-w-0"><img src={previewUrl(selectedAsset.deliveryUrl)} alt="새 이미지" className="aspect-video w-full rounded-md object-cover" /><figcaption className="mt-2 break-words text-sm">새 이미지 · {selectedAsset.displayName}</figcaption></figure>
            <p className="text-sm text-muted-foreground sm:col-span-2">이 위치의 대체 텍스트: {replacement.altText}</p>
          </div>}
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction disabled={!selectedAsset || selectedAsset.status !== "ACTIVE" || selectedAsset.id !== replacementAssetId || uploading || loading} onClick={() => { if (selectedAsset?.status === "ACTIVE" && selectedAsset.id === replacementAssetId) { onSelect(selectedAsset); close(); } }}>교체하기</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Dialog>
  );
}
