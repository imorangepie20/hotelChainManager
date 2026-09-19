"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw, Sparkles } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getConciergeLlmMetrics, type ConciergeLlmMetrics, type ConciergeLlmOutcome, type StaffPrincipal } from "@/lib/staff-api";

const outcomeOptions: Array<{ value: ConciergeLlmOutcome; label: string; hint: string }> = [
  { value: "success", label: "성공", hint: "스키마까지 통과해 조건을 추출" },
  { value: "schema_rejected", label: "스키마 위반", hint: "스키마·지점 화이트리스트·날짜 형식이 어긋나 전체 결과를 버림" },
  { value: "unparsable", label: "파싱 실패", hint: "응답이 JSON이 아니거나 파싱에 실패" },
  { value: "empty_response", label: "빈 응답", hint: "파싱은 됐으나 null 등 비어 있음" },
  { value: "api_error", label: "API 오류", hint: "Gemini 호출이 예외로 끝남" },
  { value: "no_key", label: "키 없음", hint: "GOOGLE_API_KEY가 없어 LLM을 시도하지 않음" },
];

const outcomeVariant = new Map<ConciergeLlmOutcome, "default" | "secondary" | "destructive">([
  ["success", "default"],
  ["no_key", "secondary"],
]);

function elapsed(ms: number) {
  if (ms <= 0) return "-";
  return `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(ms)}ms`;
}

export function AiOperations() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [metrics, setMetrics] = useState<ConciergeLlmMetrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      setStaff(JSON.parse(stored) as StaffPrincipal);
      requestGeneration.current += 1;
    } catch {
      setError("직원 정보를 읽지 못했습니다.");
    }
    return () => {
      mounted.current = false;
      requestGeneration.current += 1;
    };
  }, []);

  const refresh = useCallback(async () => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    setError("");
    try {
      const next = await getConciergeLlmMetrics();
      if (!mounted.current || requestGeneration.current !== generation) return;
      setMetrics(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setMetrics(null);
        setError(cause instanceof Error ? cause.message : "AI 도우미 측정을 불러오지 못했습니다.");
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation) setLoading(false);
    }
  }, []);

  const entries = metrics
    ? outcomeOptions.map((option) => {
        const entry = metrics.outcomes[option.value];
        return { ...option, ...entry };
      })
    : [];
  const totalCalls = entries.reduce((sum, entry) => sum + entry.count, 0);
  const failedCalls = entries.filter((entry) => entry.value !== "success").reduce((sum, entry) => sum + entry.count, 0);
  const showMetrics = !staff || isHeadquarters;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Sparkles className="h-5 w-5" aria-hidden />
            AI 도우미 운영
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            예약 도우미의 LLM 호출 결과와 지연을 읽기 전용으로 확인한다. 측정은 도우미 프로세스에 쌓이고 재시작하면 0부터 시작한다.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void refresh()} disabled={loading || !isHeadquarters}>
          <RefreshCw className="h-4 w-4" aria-hidden />
          {loading ? "불러오는 중" : "새로고침"}
        </Button>
      </div>

      {error && <p role="alert" className="break-words text-sm text-destructive">{error}</p>}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          AI 도우미 운영은 본사 관리자만 확인할 수 있습니다.
        </p>
      )}

      {showMetrics && metrics && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>모델</CardDescription>
              <CardTitle className="font-mono text-base">{metrics.model}</CardTitle>
            </CardHeader>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>전체 호출</CardDescription>
              <CardTitle className="tabular-nums">{totalCalls.toLocaleString("ko-KR")}</CardTitle>
            </CardHeader>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardDescription>성공 외 호출</CardDescription>
              <CardTitle className="tabular-nums text-destructive">{failedCalls.toLocaleString("ko-KR")}</CardTitle>
            </CardHeader>
          </Card>
        </div>
      )}

      {metrics && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">결과별 집계</CardTitle>
            <CardDescription>
              측정은 LLM 비활성화·정규식 폴백 동작과 무관하게 남는다. 실패가 폴백으로 묻히지 않는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>결과</TableHead>
                  <TableHead>의미</TableHead>
                  <TableHead className="text-right">호출 수</TableHead>
                  <TableHead className="text-right">평균 지연</TableHead>
                  <TableHead className="text-right">누적 지연</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.map((entry) => (
                  <TableRow key={entry.value}>
                    <TableCell>
                      <Badge variant={outcomeVariant.get(entry.value) ?? "destructive"}>{entry.label}</Badge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{entry.hint}</TableCell>
                    <TableCell className="text-right tabular-nums">{entry.count.toLocaleString("ko-KR")}</TableCell>
                    <TableCell className="text-right tabular-nums">{elapsed(entry.avgElapsedMs)}</TableCell>
                    <TableCell className="text-right tabular-nums">{elapsed(entry.totalElapsedMs)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {showMetrics && !metrics && !error && (
        <p className="text-sm text-muted-foreground">새로고침을 눌러 AI 도우미 측정을 불러오세요.</p>
      )}
    </div>
  );
}
