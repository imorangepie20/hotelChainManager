"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { ArrowLeft, RefreshCw, RotateCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  createSettlementRun,
  getSettlementRun,
  getSettlementRuns,
  retrySettlementRun,
  type ReconciliationRow,
  type SettlementRunDetailView,
  type SettlementRunSummary,
  type SettlementRunsView,
  type StaffPrincipal,
} from "@/lib/staff-api";

const runStatusOptions = [
  { value: "", label: "전체 상태" },
  { value: "PENDING", label: "대기 중" },
  { value: "PROCESSING", label: "처리 중" },
  { value: "SUCCEEDED", label: "완료" },
  { value: "FAILED", label: "실패" },
];

const rowStatusOptions = [
  { value: "", label: "전체 상태" },
  { value: "MATCHED", label: "일치" },
  { value: "AMOUNT_MISMATCH", label: "금액 불일치" },
  { value: "FEE_MISMATCH", label: "수수료 불일치" },
  { value: "MISSING_INTERNAL", label: "내역 없음" },
  { value: "MISSING_PROVIDER", label: "PG 누락" },
  { value: "PENDING", label: "정산 대기" },
];

const runStatusVariant = new Map<string, "default" | "secondary" | "destructive" | "outline">([
  ["SUCCEEDED", "default"],
  ["PROCESSING", "secondary"],
  ["FAILED", "destructive"],
]);

const rowStatusVariant = new Map<string, "default" | "secondary" | "destructive" | "outline">([
  ["MATCHED", "default"],
  ["AMOUNT_MISMATCH", "destructive"],
  ["FEE_MISMATCH", "destructive"],
  ["MISSING_INTERNAL", "destructive"],
  ["MISSING_PROVIDER", "destructive"],
]);

function label(options: { value: string; label: string }[], value: string) {
  return options.find((option) => option.value === value)?.label ?? value;
}

function money(value: number | null | undefined) {
  if (value === null || value === undefined) return "-";
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function displayDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric" }).format(new Date(value));
}

function displayTimestamp(value: string | null | undefined) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function SettlementReconciliation() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [runs, setRuns] = useState<SettlementRunsView | null>(null);
  const [selectedRun, setSelectedRun] = useState<SettlementRunSummary | null>(null);
  const [detail, setDetail] = useState<SettlementRunDetailView | null>(null);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const alertRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      setStaff(JSON.parse(stored) as StaffPrincipal);
    } catch {
      setError("직원 정보를 읽지 못했습니다.");
    }
  }, []);

  const isHeadquarters = staff?.role === "HQ_ADMIN";

  useEffect(() => {
    if (!isHeadquarters) return;
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setLoading(true);
    setError(null);
    void getSettlementRuns(token, 20)
      .then(setRuns)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "정산 목록 조회에 실패했습니다."))
      .finally(() => setLoading(false));
  }, [isHeadquarters, refreshVersion]);

  useEffect(() => {
    if (!isHeadquarters || !selectedRun) return;
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setLoading(true);
    setError(null);
    void getSettlementRun(token, selectedRun.id, { status, limit: 100 })
      .then(setDetail)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "정산 대사 조회에 실패했습니다."))
      .finally(() => setLoading(false));
  }, [isHeadquarters, selectedRun, status, refreshVersion]);

  useEffect(() => {
    if (error) alertRef.current?.focus();
  }, [error]);

  function refresh() {
    setDetail(null);
    setRefreshVersion((version) => version + 1);
  }

  // 오늘부터 31일 이내만 서버가 허용한다. 폼의 기본값은 최근 7일이다.
  function iso(daysAgo: number) {
    const value = new Date();
    value.setDate(value.getDate() - daysAgo);
    return value.toISOString().slice(0, 10);
  }

  const [range, setRange] = useState({ from: iso(7), to: iso(1) });

  function submitRange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setSubmitting(true);
    setError(null);
    setNotice(null);
    void createSettlementRun(token, { from: range.from, to: range.to })
      .then(() => {
        setNotice(`${range.from} – ${range.to} 정산 실행을 요청했습니다. worker가 순차적으로 처리합니다.`);
        refresh();
      })
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "정산 실행 생성에 실패했습니다."))
      .finally(() => setSubmitting(false));
  }

  function retryRun(run: SettlementRunSummary) {
    const token = window.localStorage.getItem("hotel-chain-staff-session");
    if (!token) return;
    setSubmitting(true);
    setError(null);
    setNotice(null);
    void retrySettlementRun(token, run.id)
      .then(() => {
        setNotice(`${displayDate(run.soldDateFrom)} – ${displayDate(run.soldDateTo)} 실행을 다시 대기 상태로 옮겼습니다.`);
        refresh();
      })
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "정산 실행 재시도에 실패했습니다."))
      .finally(() => setSubmitting(false));
  }

  function selectRun(run: SettlementRunSummary) {
    setSelectedRun(run);
    setStatus("");
    setDetail(null);
  }

  function submitStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setRefreshVersion((version) => version + 1);
  }

  if (!isHeadquarters) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold tracking-tight">정산·대사</h1>
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          본사 관리자만 정산·대사 내역을 조회할 수 있습니다.
        </p>
      </div>
    );
  }

  if (selectedRun) {
    return (
      <div className="flex flex-col gap-4">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
          <div>
            <Button variant="ghost" size="sm" className="mb-2" onClick={() => { setSelectedRun(null); setDetail(null); }}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              정산 실행 목록
            </Button>
            <h1 className="text-2xl font-semibold tracking-tight">정산 대사 내역</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              {displayDate(selectedRun.soldDateFrom)} – {displayDate(selectedRun.soldDateTo)} · {selectedRun.merchantAccount}
            </p>
          </div>
          <Button variant="outline" size="sm" disabled={loading} onClick={refresh}>
            <RefreshCw className="mr-2 h-4 w-4" />
            새로고침
          </Button>
        </div>

        {error && (
          <p role="alert" ref={alertRef} tabIndex={-1} className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </p>
        )}

        <RunSummaryCard run={detail?.run ?? selectedRun} />

        <form onSubmit={submitStatus} className="flex flex-wrap items-end gap-2">
          <label className="grid gap-1 text-xs">
            대사 상태
            <select
              aria-label="대사 상태"
              className="h-8 rounded-lg border bg-background px-2"
              value={status}
              onChange={(event) => setStatus(event.target.value)}
            >
              {rowStatusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <Button type="submit" size="sm" disabled={loading}>필터 적용</Button>
        </form>

        {loading && <p className="text-sm text-muted-foreground">대사 내역을 불러오는 중입니다.</p>}

        <ReconciliationTable rows={detail?.rows ?? []} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">정산·대사</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            토스 정산 snapshot과 내부 거래 대사 결과를 읽기 전용으로 확인합니다.
          </p>
        </div>
        <Button variant="outline" size="sm" disabled={loading} onClick={refresh}>
          <RefreshCw className="mr-2 h-4 w-4" />
          새로고침
        </Button>
      </div>

      {error && (
        <p role="alert" ref={alertRef} tabIndex={-1} className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          {error}
        </p>
      )}

      {notice && (
        <p role="status" className="rounded-lg border border-primary/30 bg-primary/10 p-3 text-sm text-primary">
          {notice}
        </p>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">정산 실행 요청</CardTitle>
          <CardDescription>과거 31일 이내의 판매일 기간을 지정해 worker가 처리할 실행을 만듭니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submitRange} className="flex flex-wrap items-end gap-2">
            <label className="grid gap-1 text-xs">
              시작 판매일
              <input
                aria-label="시작 판매일"
                type="date"
                required
                max={iso(1)}
                className="h-8 rounded-lg border bg-background px-2"
                value={range.from}
                onChange={(event) => setRange((previous) => ({ ...previous, from: event.target.value }))}
              />
            </label>
            <label className="grid gap-1 text-xs">
              종료 판매일
              <input
                aria-label="종료 판매일"
                type="date"
                required
                max={iso(1)}
                className="h-8 rounded-lg border bg-background px-2"
                value={range.to}
                onChange={(event) => setRange((previous) => ({ ...previous, to: event.target.value }))}
              />
            </label>
            <Button type="submit" size="sm" disabled={submitting || loading}>실행 요청</Button>
          </form>
        </CardContent>
      </Card>

      {loading && <p className="text-sm text-muted-foreground">정산 실행 목록을 불러오는 중입니다.</p>}

      {!loading && runs && runs.runs.length === 0 && (
        <Card>
          <CardHeader><CardTitle>정산 실행 내역이 없습니다</CardTitle>
            <CardDescription>위 폼으로 실행을 요청하면 worker가 snapshot과 대사 결과를 채웁니다.</CardDescription></CardHeader>
        </Card>
      )}

      {runs && runs.runs.length > 0 && (
        <div className="grid gap-4">
          {runs.runs.map((run) => (
            <Card key={run.id}>
              <CardHeader className="flex flex-row items-start justify-between gap-3">
                <div>
                  <CardTitle className="text-base">
                    {displayDate(run.soldDateFrom)} – {displayDate(run.soldDateTo)}
                  </CardTitle>
                  <CardDescription>
                    {run.merchantAccount} · {displayTimestamp(run.createdAt)} 생성
                  </CardDescription>
                </div>
                <Badge variant={runStatusVariant.get(run.status) ?? "outline"}>
                  {label(runStatusOptions, run.status)}
                </Badge>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                  <Metric label="snapshot" value={run.snapshotCount} />
                  <Metric label="일치" value={run.matchedCount} />
                  <Metric label="불일치" value={run.mismatchCount} />
                  <Metric label="정산 대기" value={run.pendingCount} />
                </div>
                {run.errorCode && (
                  <p className="mt-3 text-sm text-destructive">오류 코드: {run.errorCode}</p>
                )}
                <div className="mt-4 flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={() => selectRun(run)}>
                    대사 내역 보기
                  </Button>
                  {run.status === "FAILED" && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={submitting}
                      onClick={() => retryRun(run)}
                    >
                      <RotateCw className="mr-2 h-4 w-4" />
                      재실행
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">{value}</p>
    </div>
  );
}

function RunSummaryCard({ run }: { run: SettlementRunSummary }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-3">
        <div>
          <CardTitle className="text-base">실행 요약</CardTitle>
          <CardDescription>
            {run.provider} · {run.merchantAccount} · {run.currentPage}페이지 / 페이지 크기 {run.pageSize}
          </CardDescription>
        </div>
        <Badge variant={runStatusVariant.get(run.status) ?? "outline"}>
          {label(runStatusOptions, run.status)}
        </Badge>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
          <Metric label="snapshot" value={run.snapshotCount} />
          <Metric label="일치" value={run.matchedCount} />
          <Metric label="불일치" value={run.mismatchCount} />
          <Metric label="정산 대기" value={run.pendingCount} />
        </div>
        <div className="mt-4 grid gap-1 text-sm text-muted-foreground">
          <p>기준일: {displayDate(run.currentSoldDate)}</p>
          <p>다음 시도: {displayTimestamp(run.nextAttemptAt)}</p>
          <p>완료: {displayTimestamp(run.completedAt)}</p>
          {run.errorCode && <p className="text-destructive">오류 코드: {run.errorCode}</p>}
        </div>
      </CardContent>
    </Card>
  );
}

function ReconciliationTable({ rows }: { rows: ReconciliationRow[] }) {
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground">해당 조건의 대사 내역이 없습니다.</p>;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">대사 항목 {rows.length}건</CardTitle>
        <CardDescription>금액·수수료·지급액은 snapshot을 그대로 표시하며 화면에서 변경하지 않습니다.</CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>상태</TableHead>
              <TableHead>PG 거래</TableHead>
              <TableHead>주문</TableHead>
              <TableHead className="text-right">예상 금액</TableHead>
              <TableHead className="text-right">PG 금액</TableHead>
              <TableHead className="text-right">수수료</TableHead>
              <TableHead className="text-right">지급액</TableHead>
              <TableHead>비고</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>
                  <Badge variant={rowStatusVariant.get(row.status) ?? "outline"}>
                    {label(rowStatusOptions, row.status)}
                  </Badge>
                </TableCell>
                <TableCell className="font-mono text-xs">
                  {row.snapshotTransactionKey ?? row.reconciliationKey.slice(0, 24)}
                </TableCell>
                <TableCell className="font-mono text-xs">{row.snapshotOrderId ?? "-"}</TableCell>
                <TableCell className="text-right">{money(row.expectedAmountKrw)}</TableCell>
                <TableCell className="text-right">{money(row.providerAmountKrw)}</TableCell>
                <TableCell className="text-right">{money(row.feeKrw)}</TableCell>
                <TableCell className="text-right">{money(row.payoutKrw)}</TableCell>
                <TableCell className="text-xs text-muted-foreground">{row.detailCode ?? "-"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
