"use client";

import { useEffect, useMemo, useState } from "react";
import { ArrowLeftRight, LoaderCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  getWebsitePageVersionComparison,
  type WebContentVersion,
  type WebsitePageVersionComparison,
  type WebsitePageVersionSnapshot,
} from "@/lib/staff-api";

type ContentRecord = Record<string, unknown>;
type FieldStatus = "동일" | "변경" | "추가" | "제거";

const BLOCK_FIELDS: Record<string, readonly string[]> = {
  HERO: ["imageAssetId", "imageSrc", "imageAlt", "eyebrow", "title", "description", "cta.label", "cta.href"],
  TEXT: ["eyebrow", "title", "paragraphs"],
  CTA: ["eyebrow", "title", "description", "cta.label", "cta.href"],
};

function record(value: unknown): ContentRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? value as ContentRecord : {};
}

function contentBlocks(content: ContentRecord): ContentRecord[] {
  return Array.isArray(content.blocks)
    ? content.blocks.filter((block): block is ContentRecord => Boolean(block) && typeof block === "object" && !Array.isArray(block))
    : [];
}

function fieldValue(source: ContentRecord | undefined, path: string): unknown {
  if (!source) return undefined;
  return path.split(".").reduce<unknown>((value, key) => record(value)[key], source);
}

function display(value: unknown): string {
  if (value === undefined || value === null || value === "") return "—";
  if (typeof value === "boolean") return value ? "노출" : "숨김";
  if (Array.isArray(value)) return value.map(display).join("\n");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function fieldStatus(base: unknown, compare: unknown): FieldStatus {
  if (base === undefined && compare !== undefined) return "추가";
  if (base !== undefined && compare === undefined) return "제거";
  return JSON.stringify(base) === JSON.stringify(compare) ? "동일" : "변경";
}

function fieldLabel(path: string): string {
  return ({
    imageAssetId: "이미지 자산 ID",
    imageSrc: "이미지 전달 경로",
    imageAlt: "이미지 대체 텍스트",
    eyebrow: "상단 문구",
    title: "제목",
    description: "설명",
    paragraphs: "본문",
    "cta.label": "버튼 문구",
    "cta.href": "버튼 주소",
  } as Record<string, string>)[path] ?? path;
}

function statusClass(status: FieldStatus) {
  return ({
    동일: "bg-muted text-muted-foreground",
    변경: "bg-amber-100 text-amber-950",
    추가: "bg-emerald-100 text-emerald-950",
    제거: "bg-rose-100 text-rose-950",
  } as Record<FieldStatus, string>)[status];
}

function VersionValue({ value, status }: { value: unknown; status: FieldStatus }) {
  return <div className="grid gap-2 rounded-lg border bg-background p-3">
    <span className={`w-fit rounded-full px-2 py-0.5 text-xs font-medium ${statusClass(status)}`}>{status}</span>
    <p className="whitespace-pre-wrap break-words text-sm leading-6">{display(value)}</p>
  </div>;
}

function Metadata({ snapshot, counterpart }: { snapshot: WebsitePageVersionSnapshot; counterpart: WebsitePageVersionSnapshot }) {
  const fields: Array<[string, unknown, unknown]> = [
    ["경로", snapshot.metadata.path, counterpart.metadata.path],
    ["메뉴 이름", snapshot.metadata.menuLabel, counterpart.metadata.menuLabel],
    ["메뉴 노출", snapshot.metadata.menuVisible, counterpart.metadata.menuVisible],
    ["메뉴 순서", snapshot.metadata.menuOrder, counterpart.metadata.menuOrder],
    ["발행 당시 초안 버전", snapshot.publishedFromDraftVersion, counterpart.publishedFromDraftVersion],
  ];
  return <section className="space-y-3 rounded-xl border bg-muted/20 p-4">
    <h3 className="font-semibold">페이지 정보</h3>
    <dl className="grid gap-3">
      {fields.map(([label, value, other]) => <div key={label} className="grid gap-1">
        <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
        <dd><VersionValue value={value} status={fieldStatus(value, other)} /></dd>
      </div>)}
    </dl>
  </section>;
}

function ContentDetails({ snapshot, counterpart }: { snapshot: WebsitePageVersionSnapshot; counterpart: WebsitePageVersionSnapshot }) {
  const content = record(snapshot.content);
  const otherContent = record(counterpart.content);
  const seo = record(content.seo);
  const otherSeo = record(otherContent.seo);
  const blocks = contentBlocks(content);
  const otherBlocks = contentBlocks(otherContent);
  const totalBlocks = Math.max(blocks.length, otherBlocks.length);

  return <div className="grid gap-4">
    <section className="space-y-3 rounded-xl border bg-muted/20 p-4">
      <h3 className="font-semibold">검색 결과</h3>
      {(["title", "description"] as const).map((key) => <div key={key} className="grid gap-1">
        <p className="text-xs font-medium text-muted-foreground">{key === "title" ? "검색 결과 제목" : "검색 결과 설명"}</p>
        <VersionValue value={seo[key]} status={fieldStatus(seo[key], otherSeo[key])} />
      </div>)}
    </section>
    {Array.from({ length: totalBlocks }, (_, index) => {
      const block = blocks[index];
      const otherBlock = otherBlocks[index];
      const blockType = typeof block?.type === "string" ? block.type : undefined;
      const otherType = typeof otherBlock?.type === "string" ? otherBlock.type : undefined;
      const fields = Array.from(new Set([...(BLOCK_FIELDS[blockType ?? ""] ?? []), ...(BLOCK_FIELDS[otherType ?? ""] ?? [])]));
      return <section key={index} className="space-y-3 rounded-xl border bg-muted/20 p-4">
        <h3 className="font-semibold">블록 {index + 1}</h3>
        <div className="grid gap-1">
          <p className="text-xs font-medium text-muted-foreground">블록 유형</p>
          <VersionValue value={blockType} status={fieldStatus(blockType, otherType)} />
        </div>
        {fields.map((field) => {
          const value = fieldValue(block, field);
          const other = fieldValue(otherBlock, field);
          return <div key={field} className="grid gap-1">
            <p className="text-xs font-medium text-muted-foreground">{fieldLabel(field)}</p>
            <VersionValue value={value} status={fieldStatus(value, other)} />
          </div>;
        })}
      </section>;
    })}
  </div>;
}

function VersionColumn({ title, snapshot, counterpart }: { title: string; snapshot: WebsitePageVersionSnapshot; counterpart: WebsitePageVersionSnapshot }) {
  return <article className="grid min-w-0 content-start gap-4 rounded-xl border bg-card p-4">
    <header className="border-b pb-3">
      <p className="text-xs font-semibold tracking-[0.16em] text-primary">{title}</p>
      <h2 className="mt-1 text-lg font-semibold">발행본 v{snapshot.version}</h2>
      <p className="mt-1 text-xs text-muted-foreground">{new Date(snapshot.publishedAt).toLocaleString("ko-KR")}</p>
    </header>
    <Metadata snapshot={snapshot} counterpart={counterpart} />
    <ContentDetails snapshot={snapshot} counterpart={counterpart} />
  </article>;
}

export function ContentPageVersionCompareDialog({
  token,
  pageId,
  versions,
  initialBaseVersion,
  initialCompareVersion,
  open,
  onOpenChange,
}: {
  token: string;
  pageId: string;
  versions: readonly WebContentVersion[];
  initialBaseVersion: number | null;
  initialCompareVersion: number | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const orderedVersions = useMemo(() => [...versions].sort((left, right) => left.version - right.version), [versions]);
  const [baseVersion, setBaseVersion] = useState<number | null>(null);
  const [compareVersion, setCompareVersion] = useState<number | null>(null);
  const [comparison, setComparison] = useState<WebsitePageVersionComparison | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setBaseVersion(initialBaseVersion);
    setCompareVersion(initialCompareVersion);
    setComparison(null);
    setError("");
  }, [open, initialBaseVersion, initialCompareVersion]);

  useEffect(() => {
    if (!open || !token || !pageId || baseVersion === null || compareVersion === null || baseVersion >= compareVersion) return;
    let cancelled = false;
    setLoading(true);
    setError("");
    void getWebsitePageVersionComparison(token, pageId, baseVersion, compareVersion)
      .then((next) => { if (!cancelled) setComparison(next); })
      .catch((cause) => { if (!cancelled) setError(cause instanceof Error ? cause.message : "발행본 비교를 불러오지 못했습니다."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open, token, pageId, baseVersion, compareVersion]);

  const baseOptions = orderedVersions.filter((version) => compareVersion === null || version.version < compareVersion);
  const compareOptions = orderedVersions.filter((version) => baseVersion === null || version.version > baseVersion);
  const updateBaseVersion = (value: string | null) => {
    const next = Number(value);
    if (!Number.isInteger(next)) return;
    setBaseVersion(next);
    if (compareVersion === null || next >= compareVersion) {
      setCompareVersion(orderedVersions.find((version) => version.version > next)?.version ?? null);
    }
  };
  const updateCompareVersion = (value: string | null) => {
    const next = Number(value);
    if (!Number.isInteger(next)) return;
    setCompareVersion(next);
    if (baseVersion === null || baseVersion >= next) {
      const earlier = [...orderedVersions].reverse().find((version) => version.version < next)?.version ?? null;
      setBaseVersion(earlier);
    }
  };

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="max-h-[90vh] overflow-y-auto p-0 sm:max-w-6xl" showCloseButton={false}>
      <DialogHeader className="border-b p-5">
        <DialogTitle>발행본 비교</DialogTitle>
        <DialogDescription>읽기 전용이며 초안과 고객 웹을 변경하지 않습니다.</DialogDescription>
      </DialogHeader>
      <div className="grid gap-4 p-5 sm:grid-cols-2">
        <label className="grid gap-1 text-sm font-medium">기준 발행본
          <Select value={baseVersion === null ? null : String(baseVersion)} onValueChange={updateBaseVersion}>
            <SelectTrigger aria-label="기준 발행본" className="w-full"><SelectValue placeholder="기준 발행본 선택" /></SelectTrigger>
            <SelectContent>{baseOptions.map((version) => <SelectItem key={version.version} value={String(version.version)}>발행본 v{version.version}</SelectItem>)}</SelectContent>
          </Select>
        </label>
        <label className="grid gap-1 text-sm font-medium">비교 발행본
          <Select value={compareVersion === null ? null : String(compareVersion)} onValueChange={updateCompareVersion}>
            <SelectTrigger aria-label="비교 발행본" className="w-full"><SelectValue placeholder="비교 발행본 선택" /></SelectTrigger>
            <SelectContent>{compareOptions.map((version) => <SelectItem key={version.version} value={String(version.version)}>발행본 v{version.version}</SelectItem>)}</SelectContent>
          </Select>
        </label>
      </div>
      {error && <p role="alert" className="mx-5 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
      {loading && <div className="flex items-center justify-center gap-2 px-5 pb-5 text-sm text-muted-foreground"><LoaderCircle className="size-4 animate-spin" /> 비교 내용을 불러오는 중입니다.</div>}
      {comparison && <div className="grid gap-4 p-5 pt-0 lg:grid-cols-2">
        <VersionColumn title="기준 발행본" snapshot={comparison.base} counterpart={comparison.compare} />
        <VersionColumn title="비교 발행본" snapshot={comparison.compare} counterpart={comparison.base} />
      </div>}
      <DialogFooter>
        <DialogClose render={<Button type="button" variant="outline" />}><ArrowLeftRight /> 닫기</DialogClose>
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}
