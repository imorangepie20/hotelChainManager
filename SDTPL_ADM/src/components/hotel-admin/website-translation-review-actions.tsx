"use client";

import { useRef, useState } from "react";

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  approveWebsiteTranslationReview,
  rejectWebsiteTranslationReview,
  requestWebsiteTranslationReview,
  type StaffPrincipal,
  type WebsiteTranslationReviewState,
} from "@/lib/staff-api";

const statusLabels = {
  DRAFT: "초안",
  IN_REVIEW: "검토 중",
  APPROVED: "승인됨",
  PUBLISHED: "발행됨",
} as const;

type ReviewAction = "request" | "approve" | "reject" | "publish";

export function WebsiteTranslationReviewActions({
  token,
  pageId,
  draftVersion,
  state,
  staff,
  reviewReady,
  dirty,
  busy,
  archived,
  onStateChange,
  onPublish,
  onBusyChange,
  onHistoryRefresh,
}: {
  token: string;
  pageId: string;
  draftVersion: number;
  state: WebsiteTranslationReviewState;
  staff: StaffPrincipal;
  reviewReady: boolean;
  dirty: boolean;
  busy: boolean;
  archived: boolean;
  onStateChange: (state: WebsiteTranslationReviewState) => void;
  onPublish: () => Promise<void>;
  onBusyChange: (busy: boolean) => void;
  onHistoryRefresh: () => Promise<void>;
}) {
  const [activeAction, setActiveAction] = useState<ReviewAction | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectionComment, setRejectionComment] = useState("");
  const [rejectionError, setRejectionError] = useState("");
  const inFlight = useRef(false);
  const rejectButtonRef = useRef<HTMLButtonElement>(null);
  const requestButtonRef = useRef<HTMLButtonElement>(null);
  const blocked = !reviewReady || dirty || busy || archived || activeAction !== null;
  const canEdit = staff.role === "HQ_ADMIN" || staff.role === "HQ_EDITOR";
  const canPublish = staff.role === "HQ_ADMIN" || staff.role === "HQ_PUBLISHER";
  const requesterId = state.events.find((event) => event.action === "REVIEW_REQUESTED" && event.draftVersion === state.reviewedDraftVersion)?.actorId ?? null;
  const selfApproval = requesterId !== null && requesterId === staff.id;

  function restoreRejectFocus() {
    window.setTimeout(() => rejectButtonRef.current?.focus(), 0);
  }

  function closeRejectDialog() {
    setRejectOpen(false);
    setRejectionComment("");
    setRejectionError("");
    restoreRejectFocus();
  }

  function closeRejectedDialog() {
    setRejectOpen(false);
    setRejectionComment("");
    setRejectionError("");
    window.setTimeout(() => requestButtonRef.current?.focus(), 0);
  }

  async function runAction(action: ReviewAction, work: () => Promise<WebsiteTranslationReviewState | void>, successMessage: string) {
    if (blocked || inFlight.current) return false;
    inFlight.current = true;
    setActiveAction(action);
    setError("");
    setNotice("");
    onBusyChange(true);
    try {
      const next = await work();
      if (next) onStateChange(next);
      setNotice(successMessage);
      void onHistoryRefresh().catch(() => undefined);
      return true;
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "검토 상태를 변경하지 못했습니다. 새로고침 후 다시 시도해 주세요.";
      if (action === "reject") setRejectionError(message);
      else setError(message);
      return false;
    } finally {
      inFlight.current = false;
      setActiveAction(null);
      onBusyChange(false);
    }
  }

  async function requestReview() {
    await runAction("request", () => requestWebsiteTranslationReview(token, pageId, draftVersion, null), "영어 번역 검토를 요청했습니다.");
  }

  async function approveReview() {
    await runAction("approve", () => approveWebsiteTranslationReview(token, pageId, draftVersion, null), "영어 번역을 승인했습니다.");
  }

  async function rejectReview() {
    const comment = rejectionComment.trim();
    if (!comment) return;
    const succeeded = await runAction("reject", () => rejectWebsiteTranslationReview(token, pageId, draftVersion, comment), "영어 번역을 반려했습니다.");
    if (succeeded) closeRejectedDialog();
  }

  async function publish() {
    await runAction("publish", async () => {
      await onPublish();
      onStateChange({ ...state, status: "PUBLISHED", reviewedDraftVersion: draftVersion });
    }, "발행본이 고객 웹에 적용되었습니다.");
  }

  const actionLabel = activeAction === "request" ? "검토 요청 중"
    : activeAction === "approve" ? "승인 중"
      : activeAction === "reject" ? "반려 중"
        : activeAction === "publish" ? "발행 중" : null;

  return <>
    <section aria-label="영어 번역 검토" className="grid min-w-0 gap-3 rounded-xl border bg-card p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={state.status === "DRAFT" ? "outline" : "secondary"}>{statusLabels[state.status]}</Badge>
          {state.reviewedDraftVersion !== null && <span className="text-sm text-muted-foreground">검토 대상 초안 v{state.reviewedDraftVersion}</span>}
        </div>
        {dirty && <p className="text-sm text-muted-foreground">검토 작업 전에 변경사항을 초안으로 저장해 주세요.</p>}
        {archived && <p className="text-sm text-muted-foreground">보관된 페이지에서는 검토 작업을 진행할 수 없습니다.</p>}
        {!reviewReady && <p className="text-sm text-muted-foreground">검토 상태와 이력을 확인한 뒤 작업할 수 있습니다.</p>}
        {state.status === "IN_REVIEW" && selfApproval && <p className="text-sm text-muted-foreground">본인이 요청한 초안은 다른 승인자가 승인해야 합니다.</p>}
        {state.status === "IN_REVIEW" && !canPublish && <p className="text-sm text-muted-foreground">승인 권한이 있는 담당자의 검토를 기다리고 있습니다.</p>}
      </div>
      <div className="flex flex-wrap gap-2">
        {state.status === "DRAFT" && canEdit && <Button ref={requestButtonRef} type="button" disabled={blocked || draftVersion === 0} onClick={() => void requestReview()}>{activeAction === "request" ? actionLabel : "검토 요청"}</Button>}
        {state.status === "IN_REVIEW" && canPublish && <>
          {!selfApproval && <Button type="button" disabled={blocked} onClick={() => void approveReview()}>{activeAction === "approve" ? actionLabel : "승인"}</Button>}
          <Button ref={rejectButtonRef} type="button" variant="outline" disabled={blocked} onClick={() => { setRejectionError(""); setRejectOpen(true); }}>반려</Button>
        </>}
        {state.status === "APPROVED" && canPublish && <Button type="button" disabled={blocked} onClick={() => void publish()}>{activeAction === "publish" ? actionLabel : "발행"}</Button>}
      </div>
      {error && <p role="alert" className="text-sm text-destructive sm:col-span-2">{error}</p>}
      {notice && <p role="status" className="text-sm text-emerald-700 sm:col-span-2">{notice}</p>}
    </section>

    <AlertDialog open={rejectOpen} onOpenChange={(open) => { if (open) setRejectOpen(true); else closeRejectDialog(); }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>영어 번역을 반려할까요?</AlertDialogTitle>
          <AlertDialogDescription>수정할 내용을 구체적으로 남기면 번역 초안이 다시 검토 가능한 상태로 돌아갑니다.</AlertDialogDescription>
        </AlertDialogHeader>
        <label className="grid gap-1 text-sm font-medium">
          반려 사유
          <Textarea aria-label="반려 사유" value={rejectionComment} maxLength={2000} disabled={activeAction === "reject"} onChange={(event) => { setRejectionComment(event.target.value); setRejectionError(""); }} />
        </label>
        {rejectionError && <p role="alert" className="text-sm text-destructive">{rejectionError}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={activeAction === "reject"} onClick={closeRejectDialog}>취소</AlertDialogCancel>
          <Button type="button" variant="destructive" disabled={activeAction === "reject" || !rejectionComment.trim()} onClick={() => void rejectReview()}>{activeAction === "reject" ? actionLabel : "반려하기"}</Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </>;
}
