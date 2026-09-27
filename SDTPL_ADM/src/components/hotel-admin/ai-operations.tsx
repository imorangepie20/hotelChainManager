"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw, Sparkles } from "lucide-react";
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getConciergeLlmMetrics, type ConciergeLlmMetrics, type ConciergeLlmOutcome, type StaffPrincipal } from "@/lib/staff-api";

type MetricsPeriod = "24H" | "7D" | "30D";

const periods: Array<{ value: MetricsPeriod; label: string }> = [
  { value: "24H", label: "24시간" },
  { value: "7D", label: "7일" },
  { value: "30D", label: "30일" },
];

const outcomeOptions: Array<{ value: ConciergeLlmOutcome; label: string; hint: string }> = [
  { value: "success", label: "성공", hint: "스키마까지 통과해 조건을 추출" },
  { value: "schema_rejected", label: "스키마 위반", hint: "스키마·지점 화이트리스트·날짜 형식이 어긋남" },
  { value: "unparsable", label: "파싱 실패", hint: "응답이 JSON이 아니거나 파싱에 실패" },
  { value: "empty_response", label: "빈 응답", hint: "파싱은 됐으나 null 등 비어 있음" },
  { value: "api_error", label: "API 오류", hint: "Gemini 호출이 예외로 끝남" },
  { value: "no_key", label: "키 없음", hint: "GOOGLE_API_KEY가 없어 LLM을 시도하지 않음" },
];

const outcomeVariant = new Map<ConciergeLlmOutcome, "default" | "secondary" | "destructive">([
  ["success", "default"],
  ["no_key", "secondary"],
]);

const chartConfig = {
  calls: { label: "LLM 호출", color: "var(--chart-1)" },
  policyViolations: { label: "정책 위반", color: "var(--chart-2)" },
} satisfies ChartConfig;

function elapsed(ms: number) {
  if (ms <= 0) return "-";
  return `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(ms)}ms`;
}

function bucketLabel(value: string, period: MetricsPeriod) {
  const date = new Date(value);
  return new Intl.DateTimeFormat("ko-KR", period === "24H"
    ? { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hour12: false }
    : { timeZone: "Asia/Seoul", month: "numeric", day: "numeric" }).format(date);
}

export function AiOperations() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [token, setToken] = useState("");
  const [period, setPeriod] = useState<MetricsPeriod>("24H");
  const [metrics, setMetrics] = useState<ConciergeLlmMetrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    const storedToken = window.localStorage.getItem("hotel-chain-staff-session") ?? "";
    if (stored) {
      try {
        setStaff(JSON.parse(stored) as StaffPrincipal);
        setToken(storedToken);
      } catch {
        setError("직원 정보를 읽지 못했습니다.");
      }
    }
    return () => {
      mounted.current = false;
      requestGeneration.current += 1;
    };
  }, []);

  const refresh = useCallback(async (nextPeriod: MetricsPeriod, sessionToken: string) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    setError("");
    setMetrics(null);
    try {
      const next = await getConciergeLlmMetrics(sessionToken, nextPeriod);
      if (!mounted.current || requestGeneration.current !== generation) return;
      setMetrics(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setMetrics(null);
        setError(cause instanceof Error ? cause.message : "AI 도우미 운영 지표를 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isHeadquarters && token) void refresh(period, token);
  }, [isHeadquarters, period, refresh, token]);

  const entries = metrics
    ? outcomeOptions.map((option) => ({ ...option, ...metrics.outcomes[option.value] }))
    : [];
  const chartData = metrics?.series.map((point) => ({
    ...point,
    label: bucketLabel(point.startAt, metrics?.period ?? period),
  })) ?? [];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Sparkles className="h-5 w-5" aria-hidden />
            AI 도우미 운영
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            영구 저장된 LLM 결과·지연과 정책 위반을 서울 시간 기준으로 읽기 전용 집계합니다. 원문 메시지와 고객 정보는 저장하지 않습니다.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void refresh(period, token)} disabled={loading || !isHeadquarters || !token}>
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          AI 도우미 운영은 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {isHeadquarters && (
        <div className="flex flex-wrap gap-2" aria-label="조회 기간">
          {periods.map((option) => (
            <Button
              key={option.value}
              type="button"
              size="sm"
              variant={period === option.value ? "default" : "outline"}
              aria-pressed={period === option.value}
              onClick={() => setPeriod(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      )}

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {isHeadquarters && metrics && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard label="모델" value={metrics.models.length ? metrics.models.join(", ") : "기록 없음"} mono />
            <MetricCard label="전체 호출" value={metrics.totals.calls.toLocaleString("ko-KR")} />
            <MetricCard label="평균 지연" value={elapsed(metrics.totals.avgElapsedMs)} />
            <MetricCard label="정책 위반" value={metrics.totals.policyViolations.toLocaleString("ko-KR")} destructive />
          </div>

          <Card className="min-w-0 overflow-hidden">
            <CardHeader>
              <CardTitle className="text-base">시간대별 집계</CardTitle>
              <CardDescription>저장 시각은 UTC, 표시와 버킷 경계는 Asia/Seoul 기준입니다.</CardDescription>
            </CardHeader>
            <CardContent className="min-w-0">
              <figure role="figure" aria-label="LLM 호출 및 정책 위반 시계열" className="min-w-0">
                <ChartContainer config={chartConfig} className="h-64 w-full min-w-0">
                  <LineChart data={chartData} margin={{ left: 0, right: 8 }}>
                    <CartesianGrid vertical={false} />
                    <XAxis dataKey="label" tickLine={false} axisLine={false} minTickGap={24} />
                    <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={32} />
                    <ChartTooltip content={<ChartTooltipContent />} />
                    <Line type="monotone" dataKey="calls" stroke="var(--color-calls)" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="policyViolations" stroke="var(--color-policyViolations)" strokeWidth={2} dot={false} />
                  </LineChart>
                </ChartContainer>
                <figcaption className="sr-only">같은 수치는 아래 시간 구간별 표에서 확인할 수 있습니다.</figcaption>
              </figure>
            </CardContent>
          </Card>

          <Card className="min-w-0 overflow-hidden">
            <CardHeader>
              <CardTitle className="text-base">시간 구간별 상세</CardTitle>
              <CardDescription>차트와 동일한 호출 수, 평균 지연, 정책 위반 건수입니다.</CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto p-0">
              <Table aria-label="시간 구간별 LLM 운영 지표">
                <TableHeader><TableRow>
                  <TableHead>구간</TableHead>
                  <TableHead className="text-right">호출 수</TableHead>
                  <TableHead className="text-right">평균 지연</TableHead>
                  <TableHead className="text-right">정책 위반</TableHead>
                </TableRow></TableHeader>
                <TableBody>{chartData.map((point) => (
                  <TableRow key={point.startAt}>
                    <TableCell className="whitespace-nowrap">{point.label}</TableCell>
                    <TableCell className="text-right tabular-nums">{point.calls.toLocaleString("ko-KR")}</TableCell>
                    <TableCell className="whitespace-nowrap text-right tabular-nums">{elapsed(point.avgElapsedMs)}</TableCell>
                    <TableCell className="text-right tabular-nums">{point.policyViolations.toLocaleString("ko-KR")}</TableCell>
                  </TableRow>
                ))}</TableBody>
              </Table>
            </CardContent>
          </Card>

          <Card className="min-w-0 overflow-hidden">
            <CardHeader>
              <CardTitle className="text-base">결과별 집계</CardTitle>
              <CardDescription>정규식 폴백이 동작해도 LLM 결과는 별도로 남습니다.</CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto p-0">
              <Table aria-label="LLM 결과별 집계">
                <TableHeader><TableRow>
                  <TableHead>결과</TableHead><TableHead>의미</TableHead>
                  <TableHead className="text-right">호출 수</TableHead>
                  <TableHead className="text-right">평균 지연</TableHead>
                  <TableHead className="text-right">누적 지연</TableHead>
                </TableRow></TableHeader>
                <TableBody>{entries.map((entry) => (
                  <TableRow key={entry.value}>
                    <TableCell><Badge variant={outcomeVariant.get(entry.value) ?? "destructive"}>{entry.label}</Badge></TableCell>
                    <TableCell className="min-w-48 text-xs text-muted-foreground">{entry.hint}</TableCell>
                    <TableCell className="text-right tabular-nums">{entry.count.toLocaleString("ko-KR")}</TableCell>
                    <TableCell className="whitespace-nowrap text-right tabular-nums">{elapsed(entry.avgElapsedMs)}</TableCell>
                    <TableCell className="whitespace-nowrap text-right tabular-nums">{elapsed(entry.totalElapsedMs)}</TableCell>
                  </TableRow>
                ))}</TableBody>
              </Table>
            </CardContent>
          </Card>

          <p className="text-xs text-muted-foreground">원본 이벤트 보존 기간: {metrics.retentionDays}일</p>
        </>
      )}

      {isHeadquarters && !metrics && !error && (
        <p className="text-sm text-muted-foreground">{loading ? "AI 도우미 운영 지표를 불러오는 중입니다." : "저장된 운영 지표가 없습니다."}</p>
      )}
    </div>
  );
}

function MetricCard({ label, value, mono = false, destructive = false }: {
  label: string;
  value: string;
  mono?: boolean;
  destructive?: boolean;
}) {
  return (
    <Card className="min-w-0">
      <CardHeader className="pb-2">
        <CardDescription>{label}</CardDescription>
        <CardTitle className={`${mono ? "break-all font-mono text-base" : "tabular-nums"} ${destructive ? "text-destructive" : ""}`}>
          {value}
        </CardTitle>
      </CardHeader>
    </Card>
  );
}
