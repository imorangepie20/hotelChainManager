"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { WebsitePageMoveImpact, WebsitePageTreeItem } from "@/lib/staff-api";

type Props = {
  open: boolean; page: WebsitePageTreeItem | null; parents: readonly WebsitePageTreeItem[]; busy?: boolean;
  onOpenChange: (open: boolean) => void;
  onImpact: (parentId: string, slug: string) => Promise<WebsitePageMoveImpact>;
  onMove: (parentId: string, slug: string) => Promise<void>;
};

export function ContentPageMoveDialog({ open, page, parents, busy = false, onOpenChange, onImpact, onMove }: Props) {
  const [parentId, setParentId] = useState(""); const [slug, setSlug] = useState("");
  const [impact, setImpact] = useState<WebsitePageMoveImpact | null>(null); const [error, setError] = useState(""); const [loading, setLoading] = useState(false);
  const reset = (next: boolean) => { if (!next) { setImpact(null); setError(""); } onOpenChange(next); };
  async function inspect() { if (!parentId || !slug.trim()) return; setLoading(true); setError(""); try { setImpact(await onImpact(parentId, slug.trim())); } catch (e) { setImpact(null); setError(e instanceof Error ? e.message : "이동 영향을 확인하지 못했습니다."); } finally { setLoading(false); } }
  async function confirm() { if (!impact) return; setLoading(true); setError(""); try { await onMove(parentId, slug.trim()); reset(false); } catch (e) { setError(e instanceof Error ? e.message : "페이지를 이동하지 못했습니다."); } finally { setLoading(false); } }
  return <Dialog open={open} onOpenChange={reset}><DialogContent className="max-w-lg"><DialogHeader><DialogTitle>페이지 이동</DialogTitle><DialogDescription>영향을 확인한 뒤 이동합니다. 발행된 경로는 기존 주소에서 새 주소로 301 리디렉션됩니다.</DialogDescription></DialogHeader>
    <div className="space-y-3"><label className="block text-sm font-medium">새 상위<select aria-label="새 상위" value={parentId} onChange={e => { setParentId(e.target.value); setImpact(null); }} className="mt-1 w-full rounded-lg border bg-background px-3 py-2"><option value="">선택하세요</option>{parents.filter(item => item.id !== page?.id).map(item => <option key={item.id} value={item.id}>{item.label} · {item.draftPath}</option>)}</select></label>
      <label className="block text-sm font-medium">주소 슬러그<input aria-label="주소 슬러그" value={slug} onChange={e => { setSlug(e.target.value); setImpact(null); }} className="mt-1 w-full rounded-lg border bg-background px-3 py-2" /></label>
      {impact && <div className="rounded-lg border bg-muted/40 p-3 text-xs"><p className="font-medium">예정 경로</p>{impact.items.map(item => <p key={item.pageId}>{item.currentDraftPath} → {item.nextDraftPath}</p>)}{impact.items.some(item => item.published && item.currentPublishedPath && item.nextPublishedPath) && <><p className="mt-3 font-medium">301 리디렉션</p>{impact.items.filter(item => item.published && item.currentPublishedPath && item.nextPublishedPath).map(item => <p key={`${item.pageId}-redirect`}>{item.currentPublishedPath} → {item.nextPublishedPath}</p>)}</>}</div>}{error && <p role="alert" className="text-sm text-destructive">{error}</p>}</div>
    <DialogFooter><Button variant="outline" onClick={() => reset(false)}>취소</Button><Button variant="outline" disabled={loading || busy || !parentId || !slug.trim()} onClick={() => void inspect()}>영향 확인</Button><Button disabled={loading || busy || !impact} onClick={() => void confirm()}>이동</Button></DialogFooter>
  </DialogContent></Dialog>;
}
