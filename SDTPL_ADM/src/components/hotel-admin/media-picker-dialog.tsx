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
import {
  archiveWebsiteMedia,
  getWebsiteMedia,
  getWebsiteMediaUsages,
  restoreWebsiteMedia,
  StaffApiError,
  updateWebsiteMedia,
  uploadWebsiteMedia,
  type WebsiteMediaAsset,
  type WebsiteMediaUsage,
} from "@/lib/staff-api";

const customerWebOrigin = (process.env.NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN ?? "http://127.0.0.1:4000").replace(/\/$/, "");

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

export function MediaPickerDialog({ token, open, onOpenChange, initialAssetId, protectedAssetIds, onSelect }: {
  token: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialAssetId: string;
  protectedAssetIds: readonly string[];
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
  const [savingMetadata, setSavingMetadata] = useState(false);
  const [changingStatus, setChangingStatus] = useState(false);
  const [archiveConfirmationOpen, setArchiveConfirmationOpen] = useState(false);
  const catalogRequestGeneration = useRef(0);
  const usageRequestGeneration = useRef(0);

  const selectedAsset = useMemo(
    () => assets.find((asset) => asset.id === selectedAssetId) ?? null,
    [assets, selectedAssetId],
  );
  const activeAssets = useMemo(() => assets.filter((asset) => asset.status === "ACTIVE"), [assets]);
  const archivedAssets = useMemo(() => assets.filter((asset) => asset.status === "ARCHIVED"), [assets]);
  const selectedByCurrentDraft = Boolean(selectedAsset && protectedAssetIds.includes(selectedAsset.id));

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
    setSelectedAssetId(initialAssetId);
    setMetadataError("");
    setMetadataNotice("");
    void refreshCatalog(initialAssetId, true);
    return () => { catalogRequestGeneration.current += 1; };
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

  async function upload() {
    if (!file || !displayName.trim() || !defaultAltText.trim()) {
      setUploadNotice("");
      setUploadError("이미지 파일, 자산명, 기본 대체 텍스트를 모두 입력해 주세요.");
      return;
    }
    setUploading(true);
    setUploadError("");
    setUploadNotice("");
    try {
      const asset = await uploadWebsiteMedia(token, { file, displayName: displayName.trim(), defaultAltText: defaultAltText.trim() });
      catalogRequestGeneration.current += 1;
      setAssets((current) => [asset, ...current.filter((item) => item.id !== asset.id)]);
      setSelectedAssetId(asset.id);
      setFile(null);
      setDisplayName("");
      setDefaultAltText("");
      setUploadNotice("이미지가 업로드되었습니다. 선택 후 이 페이지의 대체 텍스트를 확인해 주세요.");
      void refreshCatalog(asset.id, false);
    } catch (cause) {
      setUploadError(cause instanceof Error ? cause.message : "이미지를 업로드하지 못했습니다.");
    } finally {
      setUploading(false);
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

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      catalogRequestGeneration.current += 1;
      usageRequestGeneration.current += 1;
      setMetadataDisplayName("");
      setMetadataAltText("");
      setMetadataError("");
      setMetadataNotice("");
      setArchiveConfirmationOpen(false);
    }
    onOpenChange(nextOpen);
  }

  function close() { handleOpenChange(false); }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[90vh] max-w-[calc(100%-1rem)] flex-col gap-0 p-0 sm:max-w-5xl" showCloseButton>
        <DialogHeader className="shrink-0 border-b p-5 pr-12">
          <DialogTitle>미디어 선택</DialogTitle>
          <DialogDescription>활성 자산을 선택하거나 PNG/JPEG 파일을 업로드합니다. 선택은 하단 버튼을 눌러야 현재 페이지에 적용됩니다.</DialogDescription>
        </DialogHeader>

        <div className="min-h-0 overflow-y-auto p-5">
          <section className="rounded-xl border bg-muted/20 p-4">
            <div className="grid gap-3 md:grid-cols-[1.15fr_1fr_1fr_auto] md:items-end">
              <label className="grid gap-1 text-sm font-medium">이미지 파일
                <Input aria-label="이미지 파일" type="file" accept="image/png,image/jpeg" disabled={savingMetadata || changingStatus} onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
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
              <section>
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
                    <Button type="button" variant="outline" onClick={() => setArchiveConfirmationOpen(true)} disabled={savingMetadata || changingStatus || selectedAsset.usageCount > 0 || selectedByCurrentDraft}>보관</Button>
                    {selectedAsset.usageCount > 0 && <p className="text-xs text-muted-foreground">사용 위치가 있어 보관할 수 없습니다.</p>}
                    {selectedAsset.usageCount === 0 && selectedByCurrentDraft && <p className="text-xs text-muted-foreground">현재 페이지의 저장되지 않은 초안에서 선택되어 보관할 수 없습니다.</p>}
                  </> : <Button type="button" variant="outline" onClick={() => void restoreSelected()} disabled={savingMetadata || changingStatus}>{changingStatus ? "복원 중" : "복원"}</Button>}
                  {metadataNotice && <p role="status" className="text-sm text-emerald-700">{metadataNotice}</p>}
                  {metadataError && <p role="alert" className="text-sm text-destructive">{metadataError}</p>}
                </div>}
              </section>

              <section className="border-t pt-4">
                <h3 className="font-medium">사용 위치</h3>
                {!selectedAsset ? <p className="mt-3 text-sm text-muted-foreground">자산을 선택하면 초안과 발행 사용 위치를 표시합니다.</p> : loadingUsages ? <p className="mt-3 text-sm text-muted-foreground">사용 위치를 불러오는 중입니다.</p> : usages.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">현재 등록된 사용 위치가 없습니다.</p> : (
                  <ul className="mt-3 grid gap-3">
                    {usages.map((usage) => (
                      <li key={`${usage.pageId}-${usage.documentState}-${usage.fieldPath}`} className="rounded-lg border bg-background p-3 text-sm">
                        <p className="font-medium">{usage.pageLabel} · {usageStateLabel(usage.documentState)}</p>
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
          <Button type="button" disabled={!selectedAsset || selectedAsset.status !== "ACTIVE" || loading || uploading || savingMetadata || changingStatus} onClick={() => { if (selectedAsset?.status === "ACTIVE") { onSelect(selectedAsset); close(); } }}>선택</Button>
        </DialogFooter>
      </DialogContent>
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
    </Dialog>
  );
}
