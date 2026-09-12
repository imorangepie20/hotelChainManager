"use client";

import { useState } from "react";
import { ArrowRight, ImageOff, Monitor, Smartphone } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { WebsitePageDraftMetadata } from "@/lib/staff-api";

type ContentRecord = Record<string, unknown>;
type ContentBlock = ContentRecord & { type: "HERO" | "TEXT" | "CTA" | "IMAGE_GALLERY" | "FEATURE_GRID" | "SPEC_TABLE" | "ACCORDION" | "NOTICE_LIST" | "RICH_TEXT" | "OPERATING_HOURS" | "LOCATION" | "PROMOTION_SUMMARY" | "RELATED_COLLECTION" | "BOOKING_CTA" };

const customerWebOrigin = (process.env.NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN ?? "http://127.0.0.1:4000").replace(/\/$/, "");
const localImage = /^\/images\/[A-Za-z0-9][A-Za-z0-9._/-]*$/;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const uploadedMediaDelivery = /^\/api\/website\/media\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/content$/i;

function record(value: unknown): ContentRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? value as ContentRecord : {};
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function blocks(value: unknown): ContentBlock[] {
  return Array.isArray(value)
    ? value.filter((block): block is ContentBlock => Boolean(block) && typeof block === "object" && !Array.isArray(block) && ["HERO", "TEXT", "CTA", "IMAGE_GALLERY", "FEATURE_GRID", "SPEC_TABLE", "ACCORDION", "NOTICE_LIST", "RICH_TEXT", "OPERATING_HOURS", "LOCATION", "PROMOTION_SUMMARY", "RELATED_COLLECTION", "BOOKING_CTA"].includes(text((block as ContentRecord).type)))
    : [];
}

function safePreviewImageUrl(assetIdValue: unknown, deliveryPathValue: unknown): string | null {
  const assetId = text(assetIdValue);
  const deliveryPath = text(deliveryPathValue);
  if (!assetId || !deliveryPath || !uuid.test(assetId)) return null;

  const validBundledImage = localImage.test(deliveryPath)
    && deliveryPath.slice("/images/".length).split("/").every((segment) => segment !== "" && segment !== "." && segment !== "..");
  if (validBundledImage) return `${customerWebOrigin}${deliveryPath}`;

  const uploadedMatch = deliveryPath.match(uploadedMediaDelivery);
  if (uploadedMatch?.[1]?.toLowerCase() === assetId.toLowerCase()) return `${customerWebOrigin}${deliveryPath}`;
  return null;
}

function PreviewEyebrow({ children }: { children: string }) {
  return children ? <p className="text-xs font-semibold tracking-[0.18em] text-teal-700">{children}</p> : null;
}

function PreviewCta({ label }: { label: string }) {
  return label ? <span className="inline-flex min-h-10 items-center gap-2 border border-current px-4 text-sm font-semibold">{label}<ArrowRight className="size-4" aria-hidden="true" /></span> : null;
}

export function ContentPagePreviewDialog({ open, onOpenChange, metadata, content }: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  metadata: WebsitePageDraftMetadata;
  content: ContentRecord;
}) {
  const [previewViewport, setPreviewViewport] = useState<"desktop" | "mobile">("desktop");
  const pageBlocks = blocks(content.blocks);
  const hero = pageBlocks.find((block) => block.type === "HERO");
  const heroCta = hero ? record(hero.cta) : {};
  const heroImage = hero ? safePreviewImageUrl(hero.imageAssetId, hero.imageSrc) : null;
  const mobilePreview = previewViewport === "mobile";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-x-hidden overflow-y-auto p-0 sm:max-w-5xl" showCloseButton>
        <DialogHeader className="border-b px-5 pt-5 pr-12 pb-4 sm:px-7 sm:pt-7">
          <DialogTitle>일반 페이지 미리보기</DialogTitle>
          <DialogDescription>저장하거나 발행하지 않은 현재 편집 내용을 기준으로 표시합니다.</DialogDescription>
          <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="미리보기 화면 크기">
            <Button type="button" size="sm" variant={mobilePreview ? "outline" : "default"} aria-pressed={!mobilePreview} onClick={() => setPreviewViewport("desktop")}>
              <Monitor aria-hidden="true" /> 데스크톱
            </Button>
            <Button type="button" size="sm" variant={mobilePreview ? "default" : "outline"} aria-pressed={mobilePreview} onClick={() => setPreviewViewport("mobile")}>
              <Smartphone aria-hidden="true" /> 모바일 390px
            </Button>
          </div>
        </DialogHeader>

        <div className={mobilePreview ? "grid justify-items-center bg-stone-200 py-5 sm:px-7" : ""}>
        <article data-preview-viewport={previewViewport} className={mobilePreview ? "w-full max-w-[390px] overflow-hidden bg-stone-50 text-slate-950 shadow-xl" : "bg-stone-50 text-slate-950"}>
          <section className="border-b bg-white px-5 py-3 text-xs text-slate-600 sm:px-7">
            <span className="font-semibold text-slate-900">{metadata.menuLabel || "메뉴 이름을 입력해 주세요"}</span>
            <span aria-hidden="true"> · </span>
            <span>초안 주소 /{metadata.slug || "주소-슬러그"}</span>
          </section>

          <section className={`relative min-h-80 overflow-hidden bg-slate-950 text-white${mobilePreview ? "" : " sm:min-h-104"}`}>
            {heroImage ? <img src={heroImage} alt={text(hero?.imageAlt)} className="absolute inset-0 size-full object-cover opacity-55" /> : (
              <div className="absolute inset-0 grid place-items-center bg-linear-to-br from-slate-800 via-slate-700 to-slate-950 text-center text-sm text-white/75">
                <span className="grid justify-items-center gap-2"><ImageOff className="size-5" aria-hidden="true" />안전한 대표 이미지를 선택해 주세요.</span>
              </div>
            )}
            <div className="absolute inset-0 bg-linear-to-t from-slate-950 via-slate-950/50 to-slate-950/10" />
            <div className={`relative flex min-h-80 max-w-3xl flex-col justify-end px-5 py-8${mobilePreview ? "" : " sm:min-h-104 sm:px-10 sm:py-12"}`}>
              <p className="text-xs font-semibold tracking-[0.2em] text-white/75">{text(hero?.eyebrow) || "STAY HANEUL"}</p>
              <h1 className="mt-3 whitespace-pre-line font-heading text-3xl leading-tight font-semibold tracking-tight sm:text-5xl">{text(hero?.title) || "제목을 입력해 주세요"}</h1>
              <p className="mt-4 max-w-2xl whitespace-pre-line text-sm leading-7 text-white/85 sm:text-base">{text(hero?.description) || "내용을 입력해 주세요"}</p>
              <div className="mt-6"><PreviewCta label={text(heroCta.label)} /></div>
            </div>
          </section>

          <div className={`grid gap-12 px-5 py-9${mobilePreview ? "" : " sm:px-10 sm:py-14"}`}>
            {pageBlocks.slice(1).map((block, index) => {
              if (block.type === "TEXT") {
                const paragraphs = Array.isArray(block.paragraphs) ? block.paragraphs.map(text).filter(Boolean) : [];
                return <section key={`text-${index}`} className="max-w-2xl">
                  <PreviewEyebrow>{text(block.eyebrow)}</PreviewEyebrow>
                  <h2 className="mt-3 whitespace-pre-line font-heading text-3xl leading-tight font-semibold tracking-tight">{text(block.title) || "제목을 입력해 주세요"}</h2>
                  <div className="mt-5 grid gap-4 whitespace-pre-line text-sm leading-7 text-slate-600 sm:text-base">
                    {paragraphs.length ? paragraphs.map((paragraph, paragraphIndex) => <p key={paragraphIndex}>{paragraph}</p>) : <p>내용을 입력해 주세요.</p>}
                  </div>
                </section>;
              }

              if (block.type === "CTA") {
                const cta = record(block.cta);
                return <section key={`cta-${index}`} className={`flex flex-col gap-6 bg-stone-200 p-6${mobilePreview ? "" : " sm:flex-row sm:items-end sm:justify-between sm:p-9"}`}>
                  <div className="max-w-xl">
                    <PreviewEyebrow>{text(block.eyebrow)}</PreviewEyebrow>
                    <h2 className="mt-3 whitespace-pre-line font-heading text-3xl leading-tight font-semibold tracking-tight">{text(block.title) || "제목을 입력해 주세요"}</h2>
                    <p className="mt-4 whitespace-pre-line text-sm leading-7 text-slate-700 sm:text-base">{text(block.description) || "내용을 입력해 주세요"}</p>
                  </div>
                  <PreviewCta label={text(cta.label)} />
                </section>;
              }

              if (block.type === "BOOKING_CTA") return <section key={`booking-${index}`} className="rounded-lg bg-stone-200 p-6"><h2 className="font-heading text-2xl font-semibold">{text(block.title)}</h2><p className="mt-3 text-sm text-slate-700">{text(block.description)}</p><Button type="button" disabled className="mt-5">{text(block.label) || "객실 검색"}</Button></section>;
              if (block.type === "PROMOTION_SUMMARY") return <section key={`promotion-${index}`} className="rounded-lg border border-stone-200 p-6"><h2 className="font-heading text-2xl font-semibold">{text(block.title)}</h2><p className="mt-3 text-sm text-slate-700">{text(block.salesPeriod)} · {text(block.stayPeriod)}</p><p className="mt-3 text-sm text-slate-600">표시 정보이며 실제 예약 가격은 선택 조건에서 다시 계산됩니다.</p></section>;
              if (block.type === "LOCATION") return <section key={`location-${index}`}><h2 className="font-heading text-2xl font-semibold">{text(block.title)}</h2><p className="mt-3 text-sm text-slate-700">{text(block.address)}</p><p className="mt-2 text-sm text-slate-600">{text(block.directions)}</p></section>;
              if (block.type === "OPERATING_HOURS") return <section key={`hours-${index}`}><h2 className="font-heading text-2xl font-semibold">{text(block.title)}</h2><div className="mt-4 overflow-x-auto"><table className="w-full text-left text-sm"><tbody>{(Array.isArray(block.entries) ? block.entries : []).map((entry, entryIndex) => { const item = record(entry); return <tr key={entryIndex} className="border-b"><th className="py-2 pr-4">{text(item.dayLabel)}</th><td className="py-2">{item.closed === true ? "휴무" : `${text(item.opensAt)} – ${text(item.closesAt)}`}</td></tr>; })}</tbody></table></div></section>;
              if (block.type === "RICH_TEXT" || block.type === "RELATED_COLLECTION") return <section key={`${block.type}-${index}`}><h2 className="font-heading text-2xl font-semibold">{text(block.title)}</h2><p className="mt-3 text-sm text-slate-600">{text(block.description) || (Array.isArray(block.paragraphs) ? block.paragraphs.map(text).join(" ") : "관련 콘텐츠를 표시합니다.")}</p></section>;

              if (["IMAGE_GALLERY", "FEATURE_GRID", "SPEC_TABLE", "ACCORDION", "NOTICE_LIST"].includes(block.type)) {
                const entries = Array.isArray(block.items) ? block.items : Array.isArray(block.rows) ? block.rows : [];
                return <section key={`${block.type}-${index}`} className="max-w-3xl">
                  <PreviewEyebrow>{text(block.eyebrow)}</PreviewEyebrow>
                  <h2 className="mt-3 whitespace-pre-line font-heading text-3xl leading-tight font-semibold tracking-tight">{text(block.title) || "제목을 입력해 주세요"}</h2>
                  {block.type === "IMAGE_GALLERY" ? <div className={`mt-5 grid grid-cols-2 gap-2${mobilePreview ? "" : " sm:grid-cols-3"}`}>{entries.map((entry, entryIndex) => { const item = record(entry); const image = safePreviewImageUrl(item.imageAssetId, item.imageSrc); return image ? <img className="aspect-4/3 w-full object-cover" src={image} alt={text(item.imageAlt)} key={entryIndex} /> : null; })}</div> : <div className="mt-5 grid gap-2">{entries.map((entry, entryIndex) => { const item = record(entry); return <div className="border-b border-stone-200 py-3 text-sm text-slate-700" key={entryIndex}><strong>{text(item.title) || text(item.label) || text(item.text)}</strong>{(text(item.description) || text(item.value) || text(item.content)) && <p className="mt-1 whitespace-pre-line text-slate-600">{text(item.description) || text(item.value) || text(item.content)}</p>}</div>; })}</div>}
                </section>;
              }

              return null;
            })}
          </div>
        </article>
        </div>

        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" />}>닫기</DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
