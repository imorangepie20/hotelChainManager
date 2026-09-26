"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Plus, RefreshCw, ScrollText, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  getChainPolicies,
  getPolicyRevisions,
  getStaffHotels,
  inheritHotelCancellationPolicy,
  policyRevisionLabels,
  StaffApiError,
  updateCancellationPolicy,
  updateChangeApprovalLimit,
  updateChangeApprovalTtl,
  updateHotelCancellationPolicy,
  type CancellationRefundRule,
  type ChainPolicy,
  type PolicyRevisions,
  type StaffHotelSummary,
  type StaffPrincipal,
} from "@/lib/staff-api";

const REVISION_PAGE_SIZE = 10;
const DEFAULT_RULE: CancellationRefundRule = {
  daysBefore: 1,
  cutoffLocalTime: "18:00",
  refundPercent: 100,
};

function money(value: number) {
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function ttlHoursLabel(seconds: number) {
  const hours = seconds / 3600;
  return Number.isInteger(hours) ? `${hours}시간` : `${seconds}초`;
}

function dateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
}

function rulesOf(policy: ChainPolicy | null): CancellationRefundRule[] {
  if (!policy) return [{ ...DEFAULT_RULE }];
  if (policy.cancellation.rules?.length) {
    return policy.cancellation.rules.map((rule) => ({ ...rule }));
  }
  return [
    {
      daysBefore: policy.cancellation.refundCutoffDaysBefore,
      cutoffLocalTime: policy.cancellation.refundCutoffLocalTime,
      refundPercent: 100,
    },
  ];
}

function newIdempotencyKey() {
  return crypto.randomUUID();
}

export function Policies() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [hotels, setHotels] = useState<StaffHotelSummary[]>([]);
  const [selectedHotelId, setSelectedHotelId] = useState("");
  const [policy, setPolicy] = useState<ChainPolicy | null>(null);
  const [revisions, setRevisions] = useState<PolicyRevisions | null>(null);
  const [revisionOffset, setRevisionOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [editOpen, setEditOpen] = useState(false);
  const [rules, setRules] = useState<CancellationRefundRule[]>([{ ...DEFAULT_RULE }]);
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState("");
  const [limitOpen, setLimitOpen] = useState(false);
  const [limitKrw, setLimitKrw] = useState("100000");
  const [limitError, setLimitError] = useState("");
  const [ttlOpen, setTtlOpen] = useState(false);
  const [ttlHours, setTtlHours] = useState("24");
  const [ttlError, setTtlError] = useState("");
  const requestGeneration = useRef(0);
  const revisionGeneration = useRef(0);
  const mounted = useRef(false);
  const cancellationKey = useRef<string | null>(null);
  const inheritKey = useRef<string | null>(null);
  const limitKey = useRef<string | null>(null);
  const ttlKey = useRef<string | null>(null);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const sessionToken =
    typeof window !== "undefined"
      ? window.localStorage.getItem("hotel-chain-staff-session")
      : null;

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (stored) {
      try {
        setStaff(JSON.parse(stored) as StaffPrincipal);
      } catch {
        setError("직원 정보를 읽지 못했습니다.");
      }
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
      const next = await getChainPolicies(sessionToken, selectedHotelId || undefined);
      if (!mounted.current || requestGeneration.current !== generation) return;
      setPolicy(next);
      setRules(rulesOf(next));
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setPolicy(null);
        setError(
          cause instanceof StaffApiError
            ? cause.message
            : "공통 정책을 불러오지 못했습니다.",
        );
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) {
        setLoading(false);
      }
    }
  }, [selectedHotelId, sessionToken]);

  const refreshRevisions = useCallback(async () => {
    const generation = ++revisionGeneration.current;
    if (!sessionToken) return;
    try {
      const next = await getPolicyRevisions(sessionToken, {
        limit: REVISION_PAGE_SIZE,
        offset: revisionOffset,
      });
      if (mounted.current && revisionGeneration.current === generation) {
        setRevisions(next);
      }
    } catch {
      if (mounted.current && revisionGeneration.current === generation) {
        setRevisions(null);
      }
    }
  }, [revisionOffset, sessionToken]);

  useEffect(() => {
    if (!isHeadquarters || !sessionToken) return;
    void getStaffHotels(sessionToken)
      .then((next) => {
        if (mounted.current) setHotels(next.filter((hotel) => hotel.active));
      })
      .catch(() => {
        // 정책 본문은 호텔 목록 실패와 독립적으로 계속 사용할 수 있다.
      });
  }, [isHeadquarters, sessionToken]);

  useEffect(() => {
    if (isHeadquarters) void refresh();
  }, [isHeadquarters, refresh]);

  useEffect(() => {
    if (isHeadquarters) void refreshRevisions();
  }, [isHeadquarters, refreshRevisions]);

  function openEditDialog() {
    setEditError("");
    setRules(rulesOf(policy));
    cancellationKey.current = newIdempotencyKey();
    setEditOpen(true);
  }

  function updateRule(index: number, field: keyof CancellationRefundRule, value: string) {
    setRules((current) =>
      current.map((rule, ruleIndex) =>
        ruleIndex === index
          ? {
              ...rule,
              [field]: field === "cutoffLocalTime" ? value : Number(value),
            }
          : rule,
      ),
    );
  }

  function addRule() {
    if (rules.length >= 10) return;
    setRules((current) => [
      ...current,
      { daysBefore: 0, cutoffLocalTime: "18:00", refundPercent: 0 },
    ]);
  }

  function removeRule(index: number) {
    if (rules.length <= 1) return;
    setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index));
  }

  function validateRules() {
    if (rules.length < 1 || rules.length > 10) return "환불 규칙은 1개 이상 10개 이하여야 합니다.";
    const thresholds = new Set<string>();
    for (const rule of rules) {
      if (!Number.isInteger(rule.daysBefore) || rule.daysBefore < 0 || rule.daysBefore > 30) {
        return "기준 일수는 0 이상 30 이하여야 합니다.";
      }
      if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(rule.cutoffLocalTime)) {
        return "마감 시각은 HH:MM 형식이어야 합니다.";
      }
      if (!Number.isInteger(rule.refundPercent) || rule.refundPercent < 0 || rule.refundPercent > 100) {
        return "환불률은 0 이상 100 이하여야 합니다.";
      }
      const threshold = `${rule.daysBefore}-${rule.cutoffLocalTime}`;
      if (thresholds.has(threshold)) return "같은 기준 일수와 시각을 중복해서 등록할 수 없습니다.";
      thresholds.add(threshold);
    }
    if (!rules.some((rule) => rule.refundPercent === 100)) {
      return "100% 환불 규칙이 하나 이상 필요합니다.";
    }
    return "";
  }

  async function submitEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const validationError = validateRules();
    if (validationError) {
      setEditError(validationError);
      return;
    }
    cancellationKey.current ??= newIdempotencyKey();
    setSaving(true);
    setEditError("");
    try {
      const input = { rules };
      if (selectedHotelId) {
        await updateHotelCancellationPolicy(
          sessionToken,
          selectedHotelId,
          cancellationKey.current,
          input,
        );
      } else {
        await updateCancellationPolicy(sessionToken, cancellationKey.current, input);
      }
      cancellationKey.current = null;
      setEditOpen(false);
      await Promise.all([refresh(), refreshRevisions()]);
    } catch (cause) {
      setEditError(
        cause instanceof StaffApiError
          ? cause.message
          : "취소·환불 정책을 변경하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function inheritPolicy() {
    if (!sessionToken || !selectedHotelId) return;
    inheritKey.current ??= newIdempotencyKey();
    setSaving(true);
    setError("");
    try {
      await inheritHotelCancellationPolicy(
        sessionToken,
        selectedHotelId,
        inheritKey.current,
      );
      inheritKey.current = null;
      await Promise.all([refresh(), refreshRevisions()]);
    } catch (cause) {
      setError(
        cause instanceof StaffApiError
          ? cause.message
          : "체인 정책 상속으로 전환하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function submitLimit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const parsed = Number(limitKrw.trim());
    if (!Number.isFinite(parsed) || !Number.isInteger(parsed)) {
      setLimitError("예약 변경 승인 한도는 정수여야 합니다.");
      return;
    }
    limitKey.current ??= newIdempotencyKey();
    setSaving(true);
    setLimitError("");
    try {
      await updateChangeApprovalLimit(sessionToken, limitKey.current, {
        directLimitKrw: parsed,
      });
      limitKey.current = null;
      setLimitOpen(false);
      await refresh();
    } catch (cause) {
      setLimitError(cause instanceof StaffApiError ? cause.message : "승인 한도를 변경하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function submitTtl(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const parsed = Number(ttlHours.trim());
    if (!Number.isFinite(parsed) || !Number.isInteger(parsed)) {
      setTtlError("승인 TTL은 정수(시간)여야 합니다.");
      return;
    }
    ttlKey.current ??= newIdempotencyKey();
    setSaving(true);
    setTtlError("");
    try {
      await updateChangeApprovalTtl(sessionToken, ttlKey.current, {
        approvalTtlSeconds: parsed * 3600,
      });
      ttlKey.current = null;
      setTtlOpen(false);
      await refresh();
    } catch (cause) {
      setTtlError(cause instanceof StaffApiError ? cause.message : "승인 TTL을 변경하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  const revisionRows = revisions?.revisions ?? [];
  const revisionLastPage = Math.max(0, Math.ceil((revisions?.totalCount ?? 0) / REVISION_PAGE_SIZE) - 1);
  const revisionPage = Math.min(
    Math.floor(revisionOffset / REVISION_PAGE_SIZE),
    revisionLastPage,
  );
  const hasHotelOverride = Boolean(
    selectedHotelId && policy?.hotelCancellationRevision?.action === "SET",
  );

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <ScrollText className="h-5 w-5" aria-hidden />
            공통 정책
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            체인 공통 환불 규칙을 관리하고 지점별로 상속하거나 재정의합니다.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void refresh()} disabled={loading || !isHeadquarters}>
            <RefreshCw className="h-4 w-4" aria-hidden />
            {loading ? "불러오는 중" : "새로고침"}
          </Button>
          {isHeadquarters && (
            <Button type="button" onClick={openEditDialog} data-testid="edit-cancellation">
              환불 규칙 편집
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

      {isHeadquarters && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">정책 적용 범위</CardTitle>
            <CardDescription>체인 공통 정책 또는 확인할 지점을 선택하세요.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
            <label className="grid min-w-0 gap-1 text-sm font-medium" htmlFor="policy-hotel-select">
              지점 선택
              <select
                id="policy-hotel-select"
                data-testid="policy-hotel-select"
                className="h-10 min-w-0 rounded-lg border bg-background px-3"
                value={selectedHotelId}
                onChange={(event) => setSelectedHotelId(event.target.value)}
              >
                <option value="">체인 공통 정책</option>
                {hotels.map((hotel) => (
                  <option key={hotel.id} value={hotel.id}>{hotel.name} · {hotel.region}</option>
                ))}
              </select>
            </label>
            <div className="flex flex-wrap items-center gap-2">
              <Badge data-testid="policy-scope-status" variant={hasHotelOverride ? "default" : "secondary"}>
                {!selectedHotelId ? "체인 공통" : hasHotelOverride ? "지점 재정의" : "체인 정책 상속"}
              </Badge>
              {hasHotelOverride && (
                <Button type="button" variant="outline" onClick={() => void inheritPolicy()} disabled={saving} data-testid="inherit-cancellation">
                  체인 정책 상속으로 전환
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {isHeadquarters && policy && (
        <div className="grid min-w-0 gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">취소·환불 정책</CardTitle>
              <CardDescription>
                예약 생성 시점의 유효 정책이 저장되며, 이후 정책 변경은 기존 예약에 영향을 주지 않습니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="sr-only">
                <span data-testid="cutoff-days">체크인 {policy.cancellation.refundCutoffDaysBefore}일 전</span>
                <span data-testid="cutoff-time">{policy.cancellation.refundCutoffLocalTime}</span>
              </div>
              <div className="grid gap-2" data-testid="refund-rules">
                {rulesOf(policy).map((rule, index) => (
                  <div key={`${rule.daysBefore}-${rule.cutoffLocalTime}-${index}`} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3">
                    <span className="tabular-nums">체크인 {rule.daysBefore}일 전 {rule.cutoffLocalTime} 이전</span>
                    <Badge variant="secondary">{rule.refundPercent}% 환불</Badge>
                  </div>
                ))}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">기준 시간대</span>
                <span className="font-medium">{policy.cancellation.timezone}</span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">예약 변경 승인</CardTitle>
              <CardDescription>승인 한도와 TTL은 체인 전체에 공통 적용됩니다.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">지점 직접 승인 한도</span>
                <div className="flex items-center gap-2">
                  <span className="font-medium tabular-nums" data-testid="change-limit">{money(policy.changeApprovalDirectLimitKrw)}</span>
                  <Button type="button" variant="outline" size="sm" onClick={() => { setLimitError(""); setLimitKrw(String(policy.changeApprovalDirectLimitKrw)); limitKey.current = newIdempotencyKey(); setLimitOpen(true); }} data-testid="edit-limit">한도 변경</Button>
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">본사 승인 대기 시간</span>
                <div className="flex items-center gap-2">
                  <span className="font-medium tabular-nums" data-testid="approval-ttl">{ttlHoursLabel(policy.changeApprovalTtlSeconds)}</span>
                  <Button type="button" variant="outline" size="sm" onClick={() => { setTtlError(""); setTtlHours(String(policy.changeApprovalTtlSeconds / 3600)); ttlKey.current = newIdempotencyKey(); setTtlOpen(true); }} data-testid="edit-approval-ttl">TTL 변경</Button>
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted-foreground">차액 정산</span>
                <Badge variant={policy.changeSettlementEnabled ? "default" : "secondary"}>{policy.changeSettlementEnabled ? "활성" : "비활성"}</Badge>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">정책 변경 이력 {revisions?.totalCount ?? 0}건</CardTitle>
              <CardDescription>정책 변경 담당자와 시각을 최신순으로 표시합니다.</CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto p-0">
              {revisionRows.length ? (
                <Table>
                  <TableHeader><TableRow><TableHead>변경 시각</TableHead><TableHead>내용</TableHead><TableHead>처리 직원</TableHead></TableRow></TableHeader>
                  <TableBody>
                    {revisionRows.map((revision) => (
                      <TableRow key={`${revision.key}-${revision.createdAt}-${revision.staffEmail}`}>
                        <TableCell className="whitespace-nowrap tabular-nums">{dateTime(revision.createdAt)}</TableCell>
                        <TableCell><Badge variant="secondary">{policyRevisionLabels[revision.key] ?? revision.key}</Badge><span className="ml-2 break-words">{revision.summary}</span></TableCell>
                        <TableCell><div className="grid gap-0.5"><span className="font-medium break-all">{revision.staffDisplayName}</span><span className="text-xs text-muted-foreground break-all">{revision.staffEmail}</span></div></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : <p className="p-4 text-sm text-muted-foreground" data-testid="revisions-empty">정책 변경 이력이 없습니다.</p>}
            </CardContent>
          </Card>
        </div>
      )}

      {isHeadquarters && revisions && revisions.totalCount > REVISION_PAGE_SIZE && (
        <div className="flex items-center justify-center gap-2" data-testid="revisions-paging">
          <Button type="button" variant="outline" onClick={() => setRevisionOffset(Math.max(0, revisionPage - 1) * REVISION_PAGE_SIZE)} disabled={revisionPage === 0} aria-label="이전 페이지">이전</Button>
          <span className="text-sm tabular-nums" data-testid="revisions-page">{revisionPage + 1} / {revisionLastPage + 1}</span>
          <Button type="button" variant="outline" onClick={() => setRevisionOffset((revisionPage + 1) * REVISION_PAGE_SIZE)} disabled={revisionPage >= revisionLastPage} aria-label="다음 페이지">다음</Button>
        </div>
      )}

      <Dialog open={editOpen} onOpenChange={(open) => { if (!saving) setEditOpen(open); }}>
        <DialogContent className="max-h-[90vh] w-[calc(100vw-2rem)] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{selectedHotelId ? "지점 환불 규칙 재정의" : "체인 환불 규칙 변경"}</DialogTitle>
            <DialogDescription>1~10개의 환불 단계와 현지 마감 시각을 설정하세요. 100% 환불 규칙이 하나 이상 필요합니다.</DialogDescription>
          </DialogHeader>
          <form onSubmit={submitEdit} className="grid min-w-0 gap-4">
            <div className="grid gap-3" data-testid="refund-rule-editor">
              {rules.map((rule, index) => (
                <fieldset key={index} className="grid min-w-0 gap-3 rounded-lg border p-3">
                  <legend className="px-1 text-sm font-semibold">환불 규칙 {index + 1}</legend>
                  <div className="grid min-w-0 gap-3 sm:grid-cols-3">
                    <label className="grid min-w-0 gap-1 text-sm font-medium">기준 일수
                      <input aria-label={`환불 규칙 ${index + 1} 기준 일수`} type="number" min={0} max={30} required className="h-9 min-w-0 rounded-lg border bg-background px-3 tabular-nums" value={rule.daysBefore} onChange={(event) => updateRule(index, "daysBefore", event.target.value)} data-testid={index === 0 ? "edit-cutoff-days" : `edit-rule-days-${index}`} />
                    </label>
                    <label className="grid min-w-0 gap-1 text-sm font-medium">마감 시각
                      <input aria-label={`환불 규칙 ${index + 1} 마감 시각`} type="time" required className="h-9 min-w-0 rounded-lg border bg-background px-3 tabular-nums" value={rule.cutoffLocalTime} onChange={(event) => updateRule(index, "cutoffLocalTime", event.target.value)} data-testid={index === 0 ? "edit-cutoff-time" : `edit-rule-time-${index}`} />
                    </label>
                    <label className="grid min-w-0 gap-1 text-sm font-medium">환불률(%)
                      <input aria-label={`환불 규칙 ${index + 1} 환불률`} type="number" min={0} max={100} required className="h-9 min-w-0 rounded-lg border bg-background px-3 tabular-nums" value={rule.refundPercent} onChange={(event) => updateRule(index, "refundPercent", event.target.value)} data-testid={`edit-rule-percent-${index}`} />
                    </label>
                  </div>
                  <Button type="button" variant="outline" size="sm" className="justify-self-end" onClick={() => removeRule(index)} disabled={rules.length === 1 || saving} aria-label={`환불 규칙 ${index + 1} 삭제`}>
                    <Trash2 className="h-4 w-4" aria-hidden /> 삭제
                  </Button>
                </fieldset>
              ))}
            </div>
            <Button type="button" variant="outline" onClick={addRule} disabled={rules.length >= 10 || saving} data-testid="add-refund-rule"><Plus className="h-4 w-4" aria-hidden /> 환불 규칙 추가</Button>
            {editError && <p role="alert" className="break-words text-sm text-destructive">{editError}</p>}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditOpen(false)} disabled={saving}>취소</Button>
              <Button type="submit" disabled={saving} data-testid="edit-cancellation-submit">{saving ? "변경하는 중" : "변경"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={limitOpen} onOpenChange={setLimitOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>예약 변경 승인 한도 변경</DialogTitle><DialogDescription>지점이 본사 승인 없이 처리할 수 있는 최대 차액입니다.</DialogDescription></DialogHeader>
          <form onSubmit={submitLimit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">지점 직접 승인 한도(원)<input aria-label="지점 직접 승인 한도" type="text" inputMode="numeric" className="h-9 rounded-lg border bg-background px-3 tabular-nums" value={limitKrw} onChange={(event) => setLimitKrw(event.target.value)} data-testid="edit-limit-value" /></label>
            {limitError && <p role="alert" className="break-words text-sm text-destructive">{limitError}</p>}
            <DialogFooter><Button type="button" variant="outline" onClick={() => setLimitOpen(false)} disabled={saving}>취소</Button><Button type="submit" disabled={saving} data-testid="edit-limit-submit">{saving ? "변경하는 중" : "변경"}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={ttlOpen} onOpenChange={setTtlOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>예약 변경 승인 TTL 변경</DialogTitle><DialogDescription>본사 승인을 기다릴 수 있는 최대 시간입니다.</DialogDescription></DialogHeader>
          <form onSubmit={submitTtl} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">본사 승인 대기 시간(시간)<input aria-label="본사 승인 대기 시간" type="text" inputMode="numeric" className="h-9 rounded-lg border bg-background px-3 tabular-nums" value={ttlHours} onChange={(event) => setTtlHours(event.target.value)} data-testid="edit-approval-ttl-value" /></label>
            {ttlError && <p role="alert" className="break-words text-sm text-destructive">{ttlError}</p>}
            <DialogFooter><Button type="button" variant="outline" onClick={() => setTtlOpen(false)} disabled={saving}>취소</Button><Button type="submit" disabled={saving} data-testid="edit-approval-ttl-submit">{saving ? "변경하는 중" : "변경"}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
