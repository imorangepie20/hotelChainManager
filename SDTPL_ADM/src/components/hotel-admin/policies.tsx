"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ScrollText, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  getChainPolicies,
  getPolicyRevisions,
  StaffApiError,
  updateCancellationPolicy,
  updateChangeApprovalLimit,
  type ChainPolicy,
  type PolicyRevisions,
  type StaffPrincipal,
} from "@/lib/staff-api";

function money(value: number) {
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function dateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
}

const REVISION_PAGE_SIZE = 10;

export function Policies() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [policy, setPolicy] = useState<ChainPolicy | null>(null);
  const [revisions, setRevisions] = useState<PolicyRevisions | null>(null);
  const [revisionOffset, setRevisionOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [editOpen, setEditOpen] = useState(false);
  const [cutoffDays, setCutoffDays] = useState("1");
  const [cutoffTime, setCutoffTime] = useState("18:00");
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState("");
  const [updateKey, setUpdateKey] = useState(0);
  const [limitOpen, setLimitOpen] = useState(false);
  const [limitKrw, setLimitKrw] = useState("100000");
  const [limitError, setLimitError] = useState("");
  const [limitUpdateKey, setLimitUpdateKey] = useState(0);
  const requestGeneration = useRef(0);
  const revisionGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const sessionToken = typeof window !== "undefined" ? window.localStorage.getItem("hotel-chain-staff-session") : null;

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      setStaff(JSON.parse(stored) as StaffPrincipal);
    } catch {
      setError("직원 정보를 읽지 못했습니다.");
    }
    return () => {
      mounted.current = false;
    };
  }, []);

  const refresh = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken) return;
    setLoading(true);
    setError("");
    try {
      const next = await getChainPolicies(sessionToken);
      if (!mounted.current || requestGeneration.current !== generation) return;
      setPolicy(next);
      setCutoffDays(String(next.cancellation.refundCutoffDaysBefore));
      setCutoffTime(next.cancellation.refundCutoffLocalTime);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setPolicy(null);
        setError(cause instanceof StaffApiError ? cause.message : "공통 정책을 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, [sessionToken]);

  const refreshRevisions = useCallback(async () => {
    // 정책 본문과 이력은 별도 세대 카운터를 쓴다. 하나를 공유하면 두 effect가
    // 연달아 실행될 때 이력 쪽이 본문 응답을 이전 세대로 오인해 버린다.
    const generation = ++revisionGeneration.current;
    if (!sessionToken) return;
    try {
      const next = await getPolicyRevisions(sessionToken, { limit: REVISION_PAGE_SIZE, offset: revisionOffset });
      if (!mounted.current || revisionGeneration.current !== generation) return;
      setRevisions(next);
    } catch (cause) {
      if (mounted.current && revisionGeneration.current === generation) {
        setRevisions(null);
        setError(cause instanceof StaffApiError ? cause.message : "정책 변경 이력을 불러오지 못했습니다.");
      }
    }
  }, [sessionToken, revisionOffset]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void refresh();
  }, [isHeadquarters, refresh]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void refreshRevisions();
  }, [isHeadquarters, refreshRevisions]);

  async function submitEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const days = Number(cutoffDays);
    const time = cutoffTime.trim();
    if (!Number.isInteger(days) || days < 1 || days > 30) {
      setEditError("취소 마감 일수는 1 이상 30 이하여야 합니다.");
      return;
    }
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) {
      setEditError("취소 마감 시각은 HH:MM 형식이어야 합니다.");
      return;
    }
    setSaving(true);
    setEditError("");
    try {
      // 멱원 재호출이 200으로 돌아와도 같은 revision이므로 중복 안내는 생략한다.
      await updateCancellationPolicy(sessionToken, `update-cancellation-${updateKey}`, {
        refundCutoffDaysBefore: days,
        refundCutoffLocalTime: time,
      });
      setUpdateKey((key) => key + 1);
      setEditOpen(false);
      await refresh();
    } catch (cause) {
      setEditError(cause instanceof StaffApiError ? cause.message : "취소 정책을 변경하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  function openEditDialog() {
    setEditError("");
    if (policy) {
      setCutoffDays(String(policy.cancellation.refundCutoffDaysBefore));
      setCutoffTime(policy.cancellation.refundCutoffLocalTime);
    }
    setEditOpen(true);
  }

  async function submitLimit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    // 한도 범위는 서버가 최종 판단한다. 여기서 거부하면 서버 검증 메시지를
    // 대화상자에서 볼 수 없다.
    const parsed = Number(limitKrw.trim());
    if (!Number.isFinite(parsed) || !Number.isInteger(parsed)) {
      setLimitError("예약 변경 승인 한도는 정수여야 합니다.");
      return;
    }
    setSaving(true);
    setLimitError("");
    try {
      await updateChangeApprovalLimit(sessionToken, `update-limit-${limitUpdateKey}`, { directLimitKrw: parsed });
      setLimitUpdateKey((key) => key + 1);
      setLimitOpen(false);
      await refresh();
    } catch (cause) {
      setLimitError(cause instanceof StaffApiError ? cause.message : "예약 변경 승인 한도를 변경하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  function openLimitDialog() {
    setLimitError("");
    if (policy) setLimitKrw(String(policy.changeApprovalDirectLimitKrw));
    setLimitOpen(true);
  }

  function gotoRevisionPage(next: number) {
    const page = Math.max(0, next);
    if (page === revisionOffset) return;
    setRevisionOffset(page);
  }

  const revisionRows = revisions?.revisions ?? [];
  const revisionLastPage = Math.max(0, Math.ceil((revisions?.totalCount ?? 0) / REVISION_PAGE_SIZE) - 1);
  const revisionPage = Math.min(revisionOffset, revisionLastPage);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <ScrollText className="h-5 w-5" aria-hidden />
            공통 정책
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            취소 정책과 예약 변경 승인 한도의 현재값을 확인하고 변경한다. 변경은 신규 예약·변경 요청부터 적용된다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void refresh()} disabled={loading || !isHeadquarters}>
            <RefreshCw className="h-4 w-4" aria-hidden />
            {loading ? "불러오는 중" : "새로고침"}
          </Button>
          {isHeadquarters && (
            <Button type="button" onClick={openEditDialog} data-testid="edit-cancellation">
              취소 정책 변경
            </Button>
          )}
        </div>
      </div>

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          공통 정책은 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && policy && (
        <div className="grid gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">취소 정책</CardTitle>
              <CardDescription>
                체크인 며칠 전까지 전액 환급되는지, 마감 시각은 언제인지 보여준다. 변경하면 신규 예약부터 적용된다.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">전액 환급 마감</span>
                <span className="font-medium tabular-nums" data-testid="cutoff-days">
                  체크인 {policy.cancellation.refundCutoffDaysBefore}일 전
                </span>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">마감 시각</span>
                <span className="font-medium tabular-nums" data-testid="cutoff-time">
                  {policy.cancellation.refundCutoffLocalTime}
                </span>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">시간대</span>
                <span className="font-medium">{policy.cancellation.timezone}</span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">예약 변경 승인</CardTitle>
              <CardDescription>
                지점이 직접 승인할 수 있는 차액 한도와 정산 활성 여부를 보여준다. 한도 변경은 진행 중인 변경 요청에 영향을 주지 않는다.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">지점 직접 승인 한도</span>
                <div className="flex items-center gap-2">
                  <span className="font-medium tabular-nums" data-testid="change-limit">
                    {money(policy.changeApprovalDirectLimitKrw)}
                  </span>
                  <Button type="button" variant="outline" size="sm" onClick={openLimitDialog} data-testid="edit-limit">
                    한도 변경
                  </Button>
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">차액 정산</span>
                <Badge variant={policy.changeSettlementEnabled ? "default" : "secondary"}>
                  {policy.changeSettlementEnabled ? "활성" : "비활성"}
                </Badge>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">정책 변경 이력 {revisions?.totalCount ?? 0}건</CardTitle>
              <CardDescription>
                취소 정책·예약 변경 승인 한도의 변경을 최신순으로 보여준다. 처리 직원과 시각을 함께 표시한다.
              </CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto p-0">
              {revisionRows.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>변경 시각</TableHead>
                      <TableHead>내용</TableHead>
                      <TableHead>처리 직원</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {revisionRows.map((revision) => (
                      <TableRow key={`${revision.key}-${revision.createdAt}-${revision.staffEmail}`}>
                        <TableCell className="whitespace-nowrap tabular-nums">{dateTime(revision.createdAt)}</TableCell>
                        <TableCell>
                          <Badge variant="secondary">
                            {revision.key === "cancellation" ? "취소 정책" : "예약 변경 승인 한도"}
                          </Badge>
                          <span className="ml-2 break-words">{revision.summary}</span>
                        </TableCell>
                        <TableCell>
                          <div className="grid gap-0.5">
                            <span className="font-medium break-all">{revision.staffDisplayName}</span>
                            <span className="text-xs text-muted-foreground break-all">{revision.staffEmail}</span>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <p className="p-4 text-sm text-muted-foreground" data-testid="revisions-empty">
                  정책 변경 이력이 없습니다.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {isHeadquarters && !policy && !error && (
        <p className="text-sm text-muted-foreground">공통 정책을 불러오는 중입니다.</p>
      )}

      {isHeadquarters && revisions && revisions.totalCount > REVISION_PAGE_SIZE && (
        <div className="flex items-center justify-center gap-2" data-testid="revisions-paging">
          <Button
            type="button"
            variant="outline"
            onClick={() => gotoRevisionPage(revisionPage - 1)}
            disabled={revisionPage === 0}
            aria-label="이전 페이지"
          >
            이전
          </Button>
          <span className="text-sm tabular-nums" data-testid="revisions-page">
            {revisionPage + 1} / {revisionLastPage + 1}
          </span>
          <Button
            type="button"
            variant="outline"
            onClick={() => gotoRevisionPage(revisionPage + 1)}
            disabled={revisionPage >= revisionLastPage}
            aria-label="다음 페이지"
          >
            다음
          </Button>
        </div>
      )}

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>취소 정책 변경</DialogTitle>
            <DialogDescription>
              마감 일수는 1~30, 마감 시각은 HH:MM 형식이다. 이미 확정된 예약의 취소 조건은 그대로 유지된다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitEdit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              전액 환급 마감 일수
              <input
                aria-label="전액 환급 마감 일수"
                type="number"
                required
                min={1}
                max={30}
                step={1}
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={cutoffDays}
                onChange={(event) => setCutoffDays(event.target.value)}
                data-testid="edit-cutoff-days"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              마감 시각
              <input
                aria-label="마감 시각"
                type="time"
                required
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={cutoffTime}
                onChange={(event) => setCutoffTime(event.target.value)}
                data-testid="edit-cutoff-time"
              />
            </label>
            {editError && (
              <p role="alert" className="break-words text-sm text-destructive">{editError}</p>
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditOpen(false)} disabled={saving}>
                취소
              </Button>
              <Button type="submit" disabled={saving} data-testid="edit-cancellation-submit">
                {saving ? "변경하는 중" : "변경"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={limitOpen} onOpenChange={setLimitOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>예약 변경 승인 한도 변경</DialogTitle>
            <DialogDescription>
              지점이 본사 승인 없이 직접 처리할 수 있는 차액의 최대값이다. 0원 이상 10,000,000원 이하이며,
              진행 중인 예약 변경 요청은 이미 저장된 한도를 유지한다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitLimit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              지점 직접 승인 한도(원)
              <input
                aria-label="지점 직접 승인 한도"
                // 범위는 서버가 최종 판단한다. 브라우저 min/max가 제출을 막으면
                // 서버 검증 메시지를 대화상자에서 볼 수 없다.
                type="text"
                inputMode="numeric"
                autoComplete="off"
                className="h-9 rounded-lg border bg-background px-3 tabular-nums"
                value={limitKrw}
                onChange={(event) => setLimitKrw(event.target.value)}
                data-testid="edit-limit-value"
              />
            </label>
            {limitError && (
              <p role="alert" className="break-words text-sm text-destructive">{limitError}</p>
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setLimitOpen(false)} disabled={saving}>
                취소
              </Button>
              <Button type="submit" disabled={saving} data-testid="edit-limit-submit">
                {saving ? "변경하는 중" : "변경"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
