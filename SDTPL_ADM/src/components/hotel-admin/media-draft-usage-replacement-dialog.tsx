"use client";

import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  getWebsiteMediaDraftReplacementImpact,
  replaceWebsiteMediaDraftUsages,
  StaffApiError,
  uploadWebsiteMedia,
  type WebsiteMediaAsset,
  type WebsiteMediaDraftReplacementImpact,
  type WebsiteMediaDraftReplacementResult,
} from "@/lib/staff-api";

export function MediaDraftUsageReplacementDialog({ token, open, sourceAsset, onOpenChange, onCompleted }: {
  token: string;
  open: boolean;
  sourceAsset: WebsiteMediaAsset;
  onOpenChange: (open: boolean) => void;
  onCompleted: (result: WebsiteMediaDraftReplacementResult, targetAsset: WebsiteMediaAsset) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [defaultAltText, setDefaultAltText] = useState("");
  const [impact, setImpact] = useState<WebsiteMediaDraftReplacementImpact | null>(null);
  const [targetAsset, setTargetAsset] = useState<WebsiteMediaAsset | null>(null);
  const [uploading, setUploading] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [error, setError] = useState("");
  const requestGeneration = useRef(0);

  function reset() {
    requestGeneration.current += 1;
    setFile(null);
    setDisplayName("");
    setDefaultAltText("");
    setImpact(null);
    setTargetAsset(null);
    setUploading(false);
    setReplacing(false);
    setError("");
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  }

  async function uploadAndPreview() {
    if (!file || !displayName.trim() || !defaultAltText.trim()) {
      setError("새 이미지 파일, 자산명, 기본 대체 텍스트를 모두 입력해 주세요.");
      return;
    }
    const generation = ++requestGeneration.current;
    setUploading(true);
    setError("");
    setImpact(null);
    setTargetAsset(null);
    try {
      const uploaded = await uploadWebsiteMedia(token, {
        file,
        displayName: displayName.trim(),
        defaultAltText: defaultAltText.trim(),
      });
      if (requestGeneration.current !== generation) return;
      const nextImpact = await getWebsiteMediaDraftReplacementImpact(token, sourceAsset.id, uploaded.id);
      if (requestGeneration.current !== generation) return;
      setTargetAsset(uploaded);
      setImpact(nextImpact);
    } catch (cause) {
      if (requestGeneration.current !== generation) return;
      setError(cause instanceof Error ? cause.message : "교체 영향을 확인하지 못했습니다.");
    } finally {
      if (requestGeneration.current === generation) setUploading(false);
    }
  }

  async function replaceAll() {
    if (!impact || !targetAsset) return;
    const generation = ++requestGeneration.current;
    setReplacing(true);
    setError("");
    try {
      const result = await replaceWebsiteMediaDraftUsages(token, sourceAsset.id, {
        targetMediaId: targetAsset.id,
        expectedSourceVersion: impact.sourceAsset.version,
        expectedTargetVersion: impact.targetAsset.version,
        targets: impact.replaceableUsages.map(({ pageId, locale, fieldPath, expectedDraftVersion }) => ({
          pageId,
          locale,
          fieldPath,
          expectedDraftVersion,
        })),
      });
      if (requestGeneration.current !== generation) return;
      onCompleted(result, targetAsset);
      handleOpenChange(false);
    } catch (cause) {
      if (requestGeneration.current !== generation) return;
      if (cause instanceof StaffApiError && cause.code === "WEBSITE_MEDIA_REPLACEMENT_CONFLICT") {
        setError("영향 범위가 변경되었습니다. 다시 확인해 주세요. 새 이미지는 카탈로그에 유지됩니다.");
      } else {
        setError(cause instanceof Error ? cause.message : "초안 사용 위치를 교체하지 못했습니다.");
      }
    } finally {
      if (requestGeneration.current === generation) setReplacing(false);
    }
  }

  const koreanCount = impact?.replaceableUsages.filter((usage) => usage.locale === "ko").length ?? 0;
  const englishCount = impact?.replaceableUsages.filter((usage) => usage.locale === "en").length ?? 0;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[90vh] max-w-[calc(100%-1rem)] flex-col gap-0 p-0 sm:max-w-2xl" showCloseButton>
        <DialogHeader className="shrink-0 border-b p-5 pr-12">
          <DialogTitle>초안 사용 위치 일괄 교체</DialogTitle>
          <DialogDescription>
            새 이미지를 업로드하고 영향을 확인한 뒤, 활성 페이지의 한국어·영어 초안 사용 위치만 한 번에 교체합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="min-h-0 overflow-y-auto p-5">
          <p className="text-sm"><strong>{sourceAsset.displayName}</strong>의 초안 사용 위치를 교체합니다.</p>
          <div className="mt-4 grid gap-3 rounded-xl border bg-muted/20 p-4 sm:grid-cols-2">
            <label className="grid gap-1 text-sm font-medium sm:col-span-2">새 이미지 파일
              <Input aria-label="새 이미지 파일" type="file" accept="image/png,image/jpeg" disabled={uploading || replacing} onChange={(event) => { setFile(event.target.files?.[0] ?? null); setImpact(null); setTargetAsset(null); }} />
            </label>
            <label className="grid gap-1 text-sm font-medium">새 자산명
              <Input aria-label="새 자산명" value={displayName} maxLength={160} disabled={uploading || replacing} onChange={(event) => { setDisplayName(event.target.value); setImpact(null); setTargetAsset(null); }} />
            </label>
            <label className="grid gap-1 text-sm font-medium">새 기본 대체 텍스트
              <Input aria-label="새 기본 대체 텍스트" value={defaultAltText} maxLength={200} disabled={uploading || replacing} onChange={(event) => { setDefaultAltText(event.target.value); setImpact(null); setTargetAsset(null); }} />
            </label>
            <Button className="sm:col-span-2" type="button" disabled={uploading || replacing} onClick={() => void uploadAndPreview()}>
              {uploading ? "업로드 및 확인 중" : "업로드하고 영향 확인"}
            </Button>
          </div>

          {impact && <section className="mt-4 rounded-xl border p-4" aria-label="교체 영향">
            <h3 className="font-medium">교체 영향 확인</h3>
            <p className="mt-2 text-sm">한국어 초안 {koreanCount}곳 · 영어 초안 {englishCount}곳</p>
            <ul className="mt-3 grid gap-2">
              {impact.replaceableUsages.map((usage) => <li key={`${usage.pageId}-${usage.locale}-${usage.fieldPath}`} className="rounded-lg bg-muted/40 p-3 text-sm">
                <p className="font-medium">{usage.pageLabel} · {usage.locale === "en" ? "영어" : "한국어"} · 초안 v{usage.expectedDraftVersion}</p>
                <p className="mt-1 break-all text-xs text-muted-foreground">{usage.pagePath} · {usage.fieldPath}</p>
              </li>)}
            </ul>
            <p className="mt-3 text-sm text-muted-foreground">발행 사용 위치 {impact.publishedUsageCount}곳은 바뀌지 않습니다.</p>
            <p className="mt-1 text-sm text-muted-foreground">보관 페이지 초안 {impact.archivedDraftUsageCount}곳은 바뀌지 않습니다.</p>
            <p className="mt-3 text-sm">페이지별 대체 텍스트와 캡션은 유지됩니다. 새 이미지 문맥과 맞는지 교체 후 확인해 주세요.</p>
          </section>}
          {error && <p role="alert" className="mt-4 text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter className="shrink-0">
          <Button type="button" variant="outline" disabled={uploading || replacing} onClick={() => handleOpenChange(false)}>취소</Button>
          <Button type="button" disabled={!impact || impact.replaceableUsages.length === 0 || uploading || replacing} onClick={() => void replaceAll()}>
            {replacing ? "교체 중" : "초안 위치 교체하기"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
