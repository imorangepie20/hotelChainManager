"use client";

import { useEffect, useRef, useState } from "react";
import { ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { issueWebsitePreviewGrant, revokeWebsitePreviewGrant, type WebsitePreviewGrantResponse } from "@/lib/staff-api";

type Props = {
  token: string; pageId: string; locale: "ko" | "en"; draftVersion: number;
  draftPath: string; dirty: boolean; disabled?: boolean; onBusyChange?: (busy: boolean) => void;
};
const CUSTOMER_ORIGIN = process.env.NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN ?? "http://127.0.0.1:4000";
function grantUrl(grant: WebsitePreviewGrantResponse) {
  const url = new URL(grant.previewPath, CUSTOMER_ORIGIN);
  if (url.protocol !== "https:" && !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)) throw new Error("미리보기 고객 주소는 HTTPS여야 합니다.");
  url.hash = `preview=${grant.previewToken}`;
  return url.toString();
}

export function WebsiteSavedDraftPreviewAction({ token, pageId, locale, draftVersion, draftPath, dirty, disabled, onBusyChange }: Props) {
  const [grant, setGrant] = useState<WebsitePreviewGrantResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const trigger = useRef<HTMLButtonElement>(null);
  const generation = useRef(0);
  const busyCallback = useRef(onBusyChange);
  useEffect(() => { busyCallback.current = onBusyChange; }, [onBusyChange]);
  const identity = `${token}:${pageId}:${locale}:${draftVersion}:${draftPath}`;
  useEffect(() => {
    generation.current++;
    setGrant(null); setOpen(false); setMessage(""); setError(""); setBusy(false);
    busyCallback.current?.(false);
    return () => { generation.current++; busyCallback.current?.(false); };
  }, [identity]);
  function changeBusy(value: boolean) { setBusy(value); busyCallback.current?.(value); }
  async function issue() {
    if (busy || disabled || dirty || draftVersion < 1) return;
    const origin = new URL(CUSTOMER_ORIGIN);
    if (origin.protocol !== "https:" && !["localhost", "127.0.0.1", "[::1]"].includes(origin.hostname)) { setError("미리보기 고객 주소는 HTTPS여야 합니다."); return; }
    const popup = window.open("about:blank", "_blank");
    if (popup) popup.opener = null;
    const request = ++generation.current;
    changeBusy(true); setError(""); setMessage(""); setGrant(null);
    try {
      const issued = await issueWebsitePreviewGrant(token, pageId, { locale, expectedDraftVersion: draftVersion });
      if (request !== generation.current) { popup?.close(); return; }
      setGrant(issued); setOpen(true);
      if (popup && !popup.closed) { popup.location.replace(grantUrl(issued)); setMessage("새 탭에서 저장 초안을 열었습니다."); }
      else setMessage("새 탭을 열지 못했습니다. 아래 링크를 복사해 검토해 주세요.");
    } catch (cause) {
      popup?.close();
      if (request === generation.current) setError(cause instanceof Error ? cause.message : "미리보기를 열지 못했습니다.");
    } finally { if (request === generation.current) changeBusy(false); }
  }
  async function copy() {
    if (!grant || busy) return;
    const request = generation.current;
    changeBusy(true);
    try { await navigator.clipboard.writeText(grantUrl(grant)); if (request === generation.current) setMessage("링크를 복사했습니다."); }
    catch { if (request === generation.current) setMessage("자동 복사가 차단됐습니다. 링크 입력란을 선택해 직접 복사해 주세요."); }
    finally { if (request === generation.current) changeBusy(false); }
  }
  async function revoke() {
    if (!grant || busy) return;
    const request = generation.current;
    changeBusy(true); setError("");
    try {
      await revokeWebsitePreviewGrant(token, grant.grantId);
      if (request === generation.current) { setGrant(null); setMessage("링크를 폐기했습니다. 열린 미리보기는 다음 조회 시 사용할 수 없습니다."); }
    } catch (cause) { if (request === generation.current) setError(cause instanceof Error ? cause.message : "링크를 폐기하지 못했습니다."); }
    finally { if (request === generation.current) changeBusy(false); }
  }
  return <>
    <Button ref={trigger} type="button" variant="outline" disabled={busy || disabled || dirty || draftVersion < 1}
      title={dirty || draftVersion < 1 ? "변경사항을 먼저 초안으로 저장해 주세요." : "저장된 최신 초안을 실제 고객 화면에서 검토합니다."}
      onClick={() => void issue()}><ExternalLink /> 실제 화면 미리보기</Button>
    {!open && error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    <Dialog open={open} onOpenChange={setOpen}><DialogContent finalFocus={trigger} className="sm:max-w-lg">
      <DialogHeader><DialogTitle>저장 초안 미리보기</DialogTitle><DialogDescription>저장된 초안만 표시합니다. 링크를 가진 사람은 만료 전까지 초안을 볼 수 있으므로 외부 공유에 주의해 주세요.</DialogDescription></DialogHeader>
      {grant && <><p className="text-sm">만료: {new Date(grant.expiresAt).toLocaleString("ko-KR")} (발급 후 10분)</p><label className="grid min-w-0 gap-2 text-sm">미리보기 링크<Input aria-label="미리보기 링크" value={grantUrl(grant)} readOnly onFocus={event => event.target.select()} /></label></>}
      <p role="status" className="text-sm text-muted-foreground">{message}</p>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      {grant && <DialogFooter><Button type="button" variant="destructive" disabled={busy} onClick={() => void revoke()}>링크 폐기</Button><Button type="button" disabled={busy} onClick={() => void copy()}>링크 복사</Button></DialogFooter>}
    </DialogContent></Dialog>
  </>;
}
