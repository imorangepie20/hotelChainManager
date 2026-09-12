"use client";

import { useEffect, useState } from "react";
import { Archive, ChevronDown, ChevronUp, Eye, FilePenLine, Plus, RotateCcw, Send, Trash2 } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { MediaField } from "@/components/hotel-admin/media-field";
import { ContentPagePreviewDialog } from "@/components/hotel-admin/content-page-preview-dialog";
import {
  archiveWebsitePage,
  deleteWebsitePage,
  getWebsiteHomeVersions,
  getWebsitePageVersions,
  publishWebsiteHome,
  publishWebsitePage,
  restoreWebsitePage,
  saveWebsiteHome,
  saveWebsitePage,
  type WebsitePageDocument,
  type ContentReferenceCatalog,
  type WebsitePageDraftMetadata,
  type WebsitePageTreeItem,
  type WebContentVersion,
} from "@/lib/staff-api";

type ContentRecord = Record<string, unknown>;
type ContentBlock = ContentRecord & { type: "HERO" | "TEXT" | "CTA" | "IMAGE_GALLERY" | "FEATURE_GRID" | "SPEC_TABLE" | "ACCORDION" | "NOTICE_LIST" };
const INITIAL_PUBLISHED_VERSION = 1;
const BUNDLED_ASSET_ID = "14000000-0000-0000-0000-000000000001";

function record(value: unknown): ContentRecord { return value && typeof value === "object" && !Array.isArray(value) ? value as ContentRecord : {}; }
function text(value: unknown): string { return typeof value === "string" ? value : ""; }
function blocks(value: unknown): ContentBlock[] {
  return Array.isArray(value) ? value.filter((block): block is ContentBlock => Boolean(block) && typeof block === "object" && !Array.isArray(block) && ["HERO", "TEXT", "CTA", "IMAGE_GALLERY", "FEATURE_GRID", "SPEC_TABLE", "ACCORDION", "NOTICE_LIST"].includes(text((block as ContentRecord).type))) : [];
}

function contentFrom(document: WebsitePageDocument): ContentRecord {
  const value = record(document.draftContent);
  return { seo: record(value.seo), blocks: blocks(value.blocks) };
}

function Field({ label, value, onChange, multiline = false, maxLength }: { label: string; value: string; onChange: (value: string) => void; multiline?: boolean; maxLength?: number }) {
  return <label className="grid gap-1 text-sm font-medium">{label}{multiline
    ? <Textarea aria-label={label} value={value} maxLength={maxLength} onChange={(event) => onChange(event.target.value)} />
    : <Input aria-label={label} value={value} maxLength={maxLength} onChange={(event) => onChange(event.target.value)} />}</label>;
}

function BlockControls({ label, index, total, onMove, onRemove }: { label: string; index: number; total: number; onMove: (direction: -1 | 1) => void; onRemove: () => void }) {
  return <div className="flex items-center justify-between gap-2"><p className="font-medium">{label}</p><div className="flex gap-1">
    <Button type="button" variant="ghost" size="icon-sm" aria-label={`${label} 위로 이동`} disabled={index === 1} onClick={() => onMove(-1)}><ChevronUp /></Button>
    <Button type="button" variant="ghost" size="icon-sm" aria-label={`${label} 아래로 이동`} disabled={index === total - 1} onClick={() => onMove(1)}><ChevronDown /></Button>
    <Button type="button" variant="ghost" size="icon-sm" aria-label={`${label} 삭제`} onClick={onRemove}><Trash2 /></Button>
  </div></div>;
}

function RichBlockEditor({ block, index, total, label, token, protectedAssetIds, onMove, onRemove, onChange }: { block: ContentBlock; index: number; total: number; label: string; token: string; protectedAssetIds: string[]; onMove: (direction: -1 | 1) => void; onRemove: () => void; onChange: (update: (block: ContentBlock) => ContentBlock) => void }) {
  const entries = Array.isArray(block.items) ? block.items : Array.isArray(block.rows) ? block.rows : [];
  const key = block.type === "SPEC_TABLE" ? "rows" : "items";
  const minimum = block.type === "IMAGE_GALLERY" || block.type === "FEATURE_GRID" ? 2 : 1;
  const limit = block.type === "IMAGE_GALLERY" ? 12 : block.type === "FEATURE_GRID" ? 6 : block.type === "NOTICE_LIST" ? 20 : 12;
  const update = (entryIndex: number, next: ContentRecord) => onChange((current) => ({ ...current, [key]: entries.map((entry, itemIndex) => itemIndex === entryIndex ? next : entry) }));
  return <section className="space-y-4 rounded-xl border p-4"><BlockControls label={label} index={index} total={total} onMove={onMove} onRemove={onRemove} /><Field label={`${label} 제목`} maxLength={160} value={text(block.title)} onChange={(title) => onChange((current) => ({ ...current, title }))} />
    {entries.map((entry, entryIndex) => { const item = record(entry); return <div className="grid gap-3 rounded-lg border bg-muted/20 p-3" key={entryIndex}>
      {block.type === "IMAGE_GALLERY" ? <MediaField token={token} assetId={text(item.imageAssetId)} deliveryUrl={text(item.imageSrc)} altText={text(item.imageAlt)} protectedAssetIds={protectedAssetIds} onAssetSelect={(asset) => update(entryIndex, { ...item, imageAssetId: asset.id, imageSrc: asset.deliveryUrl, imageAlt: asset.defaultAltText })} onAltTextChange={(imageAlt) => update(entryIndex, { ...item, imageAlt })} />
        : block.type === "SPEC_TABLE" ? <><Field label={`${label} ${entryIndex + 1} 항목`} maxLength={100} value={text(item.label)} onChange={(value) => update(entryIndex, { ...item, label: value })} /><Field label={`${label} ${entryIndex + 1} 값`} maxLength={300} value={text(item.value)} onChange={(value) => update(entryIndex, { ...item, value })} /></>
        : block.type === "NOTICE_LIST" ? <Field label={`${label} ${entryIndex + 1} 내용`} multiline maxLength={1000} value={text(item.text)} onChange={(value) => update(entryIndex, { ...item, text: value })} />
        : <><Field label={`${label} ${entryIndex + 1} 제목`} maxLength={160} value={text(item.title)} onChange={(value) => update(entryIndex, { ...item, title: value })} /><Field label={`${label} ${entryIndex + 1} ${block.type === "ACCORDION" ? "답변" : "설명"}`} multiline maxLength={block.type === "ACCORDION" ? 2000 : 500} value={text(block.type === "ACCORDION" ? item.content : item.description)} onChange={(value) => update(entryIndex, { ...item, [block.type === "ACCORDION" ? "content" : "description"]: value })} /></>}
      <Button type="button" size="sm" variant="ghost" className="justify-self-start" disabled={entries.length <= minimum} onClick={() => onChange((current) => ({ ...current, [key]: entries.filter((_, itemIndex) => itemIndex !== entryIndex) }))}><Trash2 /> 항목 삭제</Button>
    </div>; })}
    {entries.length < limit && <Button type="button" size="sm" variant="outline" onClick={() => onChange((current) => ({ ...current, [key]: [...entries, block.type === "IMAGE_GALLERY" ? { imageAssetId: BUNDLED_ASSET_ID, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "속초 해안" } : block.type === "FEATURE_GRID" ? { title: "특징", description: "특징을 입력해 주세요." } : block.type === "SPEC_TABLE" ? { label: "항목", value: "내용" } : block.type === "ACCORDION" ? { title: "질문", content: "답변을 입력해 주세요." } : { text: "안내를 입력해 주세요.", severity: "DEFAULT" }] }))}><Plus /> 항목 추가</Button>}
  </section>;
}

export function ContentPageEditor({ token, document, catalog: _catalog, initialDirty = false, onDirtyChange, onSaved, onPublished, onLifecycleChanged, onDeleted }: {
  token: string;
  document: WebsitePageDocument;
  catalog: ContentReferenceCatalog;
  initialDirty?: boolean;
  onDirtyChange: (dirty: boolean) => void;
  onSaved: (document: WebsitePageDocument, versions: WebContentVersion[]) => void;
  onPublished: (document: WebsitePageDocument, versions: WebContentVersion[]) => void;
  onLifecycleChanged: (document: WebsitePageDocument, versions: WebContentVersion[]) => void;
  onDeleted: () => void;
}) {
  const [content, setContent] = useState<ContentRecord>(() => contentFrom(document));
  const [metadata, setMetadata] = useState<WebsitePageDraftMetadata>(() => ({ slug: document.draftMetadata.slug, menuLabel: document.draftMetadata.menuLabel, menuVisible: document.draftMetadata.menuVisible, menuOrder: document.draftMetadata.menuOrder }));
  const [draftVersion, setDraftVersion] = useState(document.draftVersion);
  const [publishedVersion, setPublishedVersion] = useState(document.publishedVersion || INITIAL_PUBLISHED_VERSION);
  const [lifecycleVersion, setLifecycleVersion] = useState(document.lifecycleVersion);
  const [dirty, setDirty] = useState(initialDirty);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [archiveConfirmationOpen, setArchiveConfirmationOpen] = useState(false);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const isHomePage = document.pageType === "HOME_PAGE";
  const isContentPage = document.pageType === "CONTENT_PAGE";
  const isArchived = isContentPage && document.lifecycleStatus === "ARCHIVED";

  useEffect(() => { onDirtyChange(dirty); }, [dirty, onDirtyChange]);
  useEffect(() => {
    setContent(contentFrom(document));
    setMetadata({ slug: document.draftMetadata.slug, menuLabel: document.draftMetadata.menuLabel, menuVisible: document.draftMetadata.menuVisible, menuOrder: document.draftMetadata.menuOrder });
    setDraftVersion(document.draftVersion); setPublishedVersion(document.publishedVersion || INITIAL_PUBLISHED_VERSION); setLifecycleVersion(document.lifecycleVersion); setDirty(initialDirty);
  }, [document, initialDirty]);

  const pageBlocks = blocks(content.blocks);
  const protectedAssetIds = pageBlocks.flatMap((block) => block.type === "HERO" && text(block.imageAssetId) ? [text(block.imageAssetId)] : block.type === "IMAGE_GALLERY" && Array.isArray(block.items) ? block.items.flatMap((item) => text(record(item).imageAssetId) ? [text(record(item).imageAssetId)] : []) : []);
  const seo = record(content.seo);
  const change = (next: ContentRecord) => { if (isArchived) return; setContent(next); setDirty(true); setError(""); setNotice(""); };
  const changeMetadata = (next: WebsitePageDraftMetadata) => { if (isArchived) return; setMetadata(next); setDirty(true); setError(""); setNotice(""); };
  const changeBlock = (index: number, update: (block: ContentBlock) => ContentBlock) => change({ ...content, blocks: pageBlocks.map((block, blockIndex) => blockIndex === index ? update(block) : block) });
  const moveBlock = (index: number, direction: -1 | 1) => {
    const destination = index + direction; if (index === 0 || destination < 1 || destination >= pageBlocks.length) return;
    const next = [...pageBlocks]; [next[index], next[destination]] = [next[destination], next[index]]; change({ ...content, blocks: next });
  };
  const addBlock = (type: Exclude<ContentBlock["type"], "HERO">) => {
    const defaults: Record<string, ContentBlock> = {
      TEXT: { type: "TEXT", eyebrow: "STAY HANEUL", title: "새 이야기", paragraphs: ["새로운 내용을 입력해 주세요."] },
      CTA: { type: "CTA", eyebrow: "STAY HANEUL", title: "여정을 시작하세요", description: "숙소와 경험을 살펴보세요.", cta: { label: "자세히 보기", href: "/" } },
      IMAGE_GALLERY: { type: "IMAGE_GALLERY", title: "새 갤러리", items: [{ imageAssetId: BUNDLED_ASSET_ID, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "속초 해안" }, { imageAssetId: BUNDLED_ASSET_ID, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "속초 해안" }] },
      FEATURE_GRID: { type: "FEATURE_GRID", title: "새 특징", items: [{ title: "특징", description: "특징을 입력해 주세요." }, { title: "특징", description: "특징을 입력해 주세요." }] },
      SPEC_TABLE: { type: "SPEC_TABLE", title: "새 사양 표", rows: [{ label: "항목", value: "내용" }] },
      ACCORDION: { type: "ACCORDION", title: "새 FAQ", items: [{ title: "질문", content: "답변을 입력해 주세요." }] },
      NOTICE_LIST: { type: "NOTICE_LIST", title: "새 안내", items: [{ text: "안내를 입력해 주세요.", severity: "DEFAULT" }] },
    };
    change({ ...content, blocks: [...pageBlocks, defaults[type]] });
  };

  function applyDocument(next: WebsitePageDocument) {
    setContent(contentFrom(next));
    setMetadata({ slug: next.draftMetadata.slug, menuLabel: next.draftMetadata.menuLabel, menuVisible: next.draftMetadata.menuVisible, menuOrder: next.draftMetadata.menuOrder });
    setDraftVersion(next.draftVersion);
    setPublishedVersion(next.publishedVersion || INITIAL_PUBLISHED_VERSION);
    setLifecycleVersion(next.lifecycleVersion);
  }

  async function save() {
    if (isArchived || busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const saved = isHomePage
        ? await saveWebsiteHome(token, { expectedDraftVersion: draftVersion, content })
        : await saveWebsitePage(token, document.id, { expectedDraftVersion: draftVersion, page: metadata, content });
      const history = isHomePage
        ? await getWebsiteHomeVersions(token)
        : await getWebsitePageVersions(token, document.id);
      applyDocument(saved); setDirty(false); setNotice("초안이 저장되었습니다. 이제 저장된 초안을 발행할 수 있습니다."); onSaved(saved, history);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "초안을 저장하지 못했습니다."); }
    finally { setBusy(false); }
  }
  async function publish() {
    if (dirty || isArchived || busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const published = isHomePage
        ? await publishWebsiteHome(token, draftVersion, publishedVersion)
        : await publishWebsitePage(token, document.id, draftVersion, publishedVersion);
      const history = isHomePage
        ? await getWebsiteHomeVersions(token)
        : await getWebsitePageVersions(token, document.id);
      applyDocument(published); setNotice("발행본이 고객 웹에 적용되었습니다."); onPublished(published, history);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "발행하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function archive() {
    if (!isContentPage || busy || dirty) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const archived = await archiveWebsitePage(token, document.id, { expectedLifecycleVersion: lifecycleVersion, expectedDraftVersion: draftVersion, expectedPublishedVersion: publishedVersion });
      const history = await getWebsitePageVersions(token, document.id);
      applyDocument(archived); setDirty(false); setArchiveConfirmationOpen(false); setNotice("페이지를 보관했습니다. 고객 웹과 메뉴에서 즉시 제외되며, 복원 뒤 다시 발행해야 공개됩니다."); onLifecycleChanged(archived, history);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "페이지를 보관하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function restore() {
    if (!isContentPage || !isArchived || busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const restored = await restoreWebsitePage(token, document.id, { expectedLifecycleVersion: lifecycleVersion, expectedDraftVersion: draftVersion, expectedPublishedVersion: publishedVersion });
      const history = await getWebsitePageVersions(token, document.id);
      applyDocument(restored); setDirty(false); setNotice("페이지를 초안으로 복원했습니다. 내용을 확인한 뒤 발행하면 고객 웹에 다시 공개됩니다."); onLifecycleChanged(restored, history);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "페이지를 복원하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function permanentlyDelete() {
    if (!isContentPage || !isArchived || busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await deleteWebsitePage(token, document.id, {
        expectedLifecycleVersion: lifecycleVersion,
        expectedDraftVersion: draftVersion,
        expectedPublishedVersion: publishedVersion,
      });
      setDeleteConfirmationOpen(false);
      onDeleted();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "페이지를 영구 삭제하지 못했습니다."); }
    finally { setBusy(false); }
  }

  return <Card className="min-w-0"><CardHeader><div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><CardTitle><h2>{isHomePage ? "홈페이지 콘텐츠" : "일반 콘텐츠 페이지"}</h2></CardTitle><CardDescription>{isHomePage ? "홈페이지의 HERO, TEXT, CTA를 편집합니다. 예약 검색과 재고·가격은 별도 예약 시스템에서 관리합니다." : "안전한 HERO, TEXT, CTA 블록으로 고객 웹 콘텐츠를 편집합니다."}</CardDescription></div><div className="flex flex-wrap gap-2">{isContentPage && <Button type="button" variant="outline" onClick={() => setPreviewOpen(true)}><Eye /> 미리보기</Button>}{isArchived ? <><Button variant="outline" onClick={() => void restore()} disabled={busy}><RotateCcw /> 초안으로 복원</Button><Button type="button" variant="destructive" onClick={() => setDeleteConfirmationOpen(true)} disabled={busy}><Trash2 /> 영구 삭제</Button></> : isContentPage && <><Button variant="destructive" onClick={() => setArchiveConfirmationOpen(true)} disabled={busy || dirty} title={dirty ? "저장되지 않은 변경사항을 먼저 초안으로 저장해 주세요." : undefined} aria-describedby={dirty ? "archive-before-save-hint" : undefined}><Archive /> 페이지 보관</Button>{dirty && <span id="archive-before-save-hint" className="sr-only">저장되지 않은 변경사항을 먼저 초안으로 저장해야 페이지를 보관할 수 있습니다.</span>}</>}<Button variant="outline" onClick={() => void save()} disabled={busy || isArchived || !dirty}><FilePenLine /> 초안 저장</Button><Button onClick={() => void publish()} disabled={busy || isArchived || dirty}><Send /> 발행</Button></div></div></CardHeader><CardContent className="space-y-6">
    {isArchived && <section role="status" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950"><p className="font-semibold">보관된 페이지입니다.</p><p className="mt-1">고객 웹과 메뉴에 표시되지 않습니다. 초안으로 복원한 뒤 내용을 확인하고 다시 발행해 주세요.</p></section>}
    <fieldset disabled={isArchived} className="grid min-w-0 gap-6 border-0 p-0">
    {!isHomePage && <section className="space-y-4 rounded-xl border p-4"><h2 className="font-semibold">페이지 정보</h2><div className="grid gap-4 md:grid-cols-2"><Field label="주소 슬러그" value={metadata.slug} onChange={(slug) => changeMetadata({ ...metadata, slug })} /><Field label="메뉴 이름" value={metadata.menuLabel} onChange={(menuLabel) => changeMetadata({ ...metadata, menuLabel })} /><label className="flex min-h-8 items-center gap-2 text-sm font-medium"><Checkbox aria-label="메뉴에 노출" checked={metadata.menuVisible} onCheckedChange={(menuVisible) => changeMetadata({ ...metadata, menuVisible })} />메뉴에 노출</label><label className="grid gap-1 text-sm font-medium">메뉴 순서<Input aria-label="메뉴 순서" type="number" min={0} value={metadata.menuOrder} onChange={(event) => changeMetadata({ ...metadata, menuOrder: Math.max(0, Number(event.target.value) || 0) })} /></label></div><p className="text-xs text-muted-foreground">초안 주소: {document.draftMetadata.path.replace(/[^/]+$/, metadata.slug)}</p></section>}
    <section className="space-y-4 rounded-xl border p-4"><h2 className="font-semibold">검색 결과</h2><Field label="검색 결과 제목" value={text(seo.title)} maxLength={60} onChange={(title) => change({ ...content, seo: { ...seo, title } })} /><Field label="검색 결과 설명" value={text(seo.description)} multiline maxLength={160} onChange={(description) => change({ ...content, seo: { ...seo, description } })} /></section>
    {pageBlocks.map((block, index) => {
      if (block.type === "HERO") { const cta = record(block.cta); return <section key="hero" className="space-y-4 rounded-xl border p-4"><h2 className="font-semibold">히어로</h2><div className="grid gap-4 md:grid-cols-2"><Field label="히어로 상단 문구" maxLength={100} value={text(block.eyebrow)} onChange={(eyebrow) => changeBlock(index, (current) => ({ ...current, eyebrow }))} /><Field label="히어로 제목" maxLength={160} value={text(block.title)} onChange={(title) => changeBlock(index, (current) => ({ ...current, title }))} /><div className="md:col-span-2"><Field label="히어로 설명" multiline maxLength={1000} value={text(block.description)} onChange={(description) => changeBlock(index, (current) => ({ ...current, description }))} /></div><MediaField token={token} assetId={text(block.imageAssetId)} deliveryUrl={text(block.imageSrc)} altText={text(block.imageAlt)} protectedAssetIds={protectedAssetIds} onAssetSelect={(asset) => changeBlock(index, (current) => ({ ...current, imageAssetId: asset.id, imageSrc: asset.deliveryUrl, imageAlt: asset.defaultAltText }))} onAltTextChange={(imageAlt) => changeBlock(index, (current) => ({ ...current, imageAlt }))} /><Field label="히어로 CTA 문구" maxLength={100} value={text(cta.label)} onChange={(label) => changeBlock(index, (current) => ({ ...current, cta: { ...record(current.cta), label } }))} /><Field label="히어로 CTA 주소" maxLength={255} value={text(cta.href)} onChange={(href) => changeBlock(index, (current) => ({ ...current, cta: { ...record(current.cta), href } }))} /></div></section>; }
      const typeCount = pageBlocks.slice(0, index + 1).filter((item) => item.type === block.type).length;
      const label = block.type === "TEXT" ? `텍스트 ${typeCount}` : `CTA ${typeCount}`;
      if (block.type === "TEXT") { const paragraphs = Array.isArray(block.paragraphs) ? block.paragraphs.map(text) : [""]; return <section key={`${block.type}-${index}`} className="space-y-4 rounded-xl border p-4"><BlockControls label={label} index={index} total={pageBlocks.length} onMove={(direction) => moveBlock(index, direction)} onRemove={() => change({ ...content, blocks: pageBlocks.filter((_, itemIndex) => itemIndex !== index) })} /><Field label={`${label} 상단 문구`} maxLength={100} value={text(block.eyebrow)} onChange={(eyebrow) => changeBlock(index, (current) => ({ ...current, eyebrow }))} /><Field label={`${label} 제목`} maxLength={160} value={text(block.title)} onChange={(title) => changeBlock(index, (current) => ({ ...current, title }))} />{paragraphs.map((paragraph, paragraphIndex) => <Field key={paragraphIndex} label={`${label} 본문 ${paragraphIndex + 1}`} multiline maxLength={1000} value={paragraph} onChange={(nextParagraph) => { const next = [...paragraphs]; next[paragraphIndex] = nextParagraph; changeBlock(index, (current) => ({ ...current, paragraphs: next })); }} />)}{paragraphs.length < 6 && <Button type="button" size="sm" variant="outline" onClick={() => changeBlock(index, (current) => ({ ...current, paragraphs: [...paragraphs, ""] }))}><Plus /> 본문 추가</Button>}</section>; }
      if (["IMAGE_GALLERY", "FEATURE_GRID", "SPEC_TABLE", "ACCORDION", "NOTICE_LIST"].includes(block.type)) return <RichBlockEditor key={`${block.type}-${index}`} block={block} index={index} total={pageBlocks.length} label={`${block.type === "IMAGE_GALLERY" ? "갤러리" : block.type === "FEATURE_GRID" ? "특징" : block.type === "SPEC_TABLE" ? "사양 표" : block.type === "ACCORDION" ? "FAQ" : "안내"} ${typeCount}`} token={token} protectedAssetIds={protectedAssetIds} onMove={(direction) => moveBlock(index, direction)} onRemove={() => change({ ...content, blocks: pageBlocks.filter((_, itemIndex) => itemIndex !== index) })} onChange={(update) => changeBlock(index, update)} />;
      const cta = record(block.cta); return <section key={`${block.type}-${index}`} className="space-y-4 rounded-xl border p-4"><BlockControls label={label} index={index} total={pageBlocks.length} onMove={(direction) => moveBlock(index, direction)} onRemove={() => change({ ...content, blocks: pageBlocks.filter((_, itemIndex) => itemIndex !== index) })} /><Field label={`${label} 상단 문구`} maxLength={100} value={text(block.eyebrow)} onChange={(eyebrow) => changeBlock(index, (current) => ({ ...current, eyebrow }))} /><Field label={`${label} 제목`} maxLength={160} value={text(block.title)} onChange={(title) => changeBlock(index, (current) => ({ ...current, title }))} /><Field label={`${label} 설명`} multiline maxLength={1000} value={text(block.description)} onChange={(description) => changeBlock(index, (current) => ({ ...current, description }))} /><Field label={`${label} 버튼 문구`} maxLength={100} value={text(cta.label)} onChange={(label) => changeBlock(index, (current) => ({ ...current, cta: { ...record(current.cta), label } }))} /><Field label={`${label} 버튼 주소`} maxLength={255} value={text(cta.href)} onChange={(href) => changeBlock(index, (current) => ({ ...current, cta: { ...record(current.cta), href } }))} /></section>;
    })}
    <div className="flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => addBlock("TEXT")} disabled={pageBlocks.length >= 20}><Plus /> 텍스트 블록 추가</Button><Button type="button" variant="outline" onClick={() => addBlock("CTA")} disabled={pageBlocks.length >= 20}><Plus /> CTA 블록 추가</Button><Button type="button" variant="outline" onClick={() => addBlock("IMAGE_GALLERY")} disabled={pageBlocks.length >= 20}><Plus /> 갤러리 추가</Button><Button type="button" variant="outline" onClick={() => addBlock("FEATURE_GRID")} disabled={pageBlocks.length >= 20}><Plus /> 특징 추가</Button><Button type="button" variant="outline" onClick={() => addBlock("SPEC_TABLE")} disabled={pageBlocks.length >= 20}><Plus /> 사양 표 추가</Button><Button type="button" variant="outline" onClick={() => addBlock("ACCORDION")} disabled={pageBlocks.length >= 20}><Plus /> FAQ 추가</Button><Button type="button" variant="outline" onClick={() => addBlock("NOTICE_LIST")} disabled={pageBlocks.length >= 20}><Plus /> 안내 추가</Button></div>
    </fieldset>
    {(notice || error) && <p role="status" className={error ? "text-sm text-destructive" : "text-sm text-emerald-700"}>{error || notice}</p>}
  </CardContent><ContentPagePreviewDialog open={previewOpen} onOpenChange={setPreviewOpen} metadata={metadata} content={content} /><AlertDialog open={archiveConfirmationOpen} onOpenChange={setArchiveConfirmationOpen}><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>페이지를 보관할까요?</AlertDialogTitle><AlertDialogDescription>보관하면 고객 웹과 메뉴에서 즉시 제외됩니다. 주소와 초안·발행 이력은 유지되며, 복원 뒤에는 다시 발행해야 공개됩니다.</AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel>취소</AlertDialogCancel><AlertDialogAction variant="destructive" disabled={busy} onClick={() => void archive()}>보관하기</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog><AlertDialog open={deleteConfirmationOpen} onOpenChange={setDeleteConfirmationOpen}><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>보관된 페이지를 영구 삭제할까요?</AlertDialogTitle><AlertDialogDescription><strong>{metadata.menuLabel}</strong> 페이지와 발행 이력, 감사 이력, 미디어 사용 위치를 영구 삭제합니다. 이 작업은 되돌릴 수 없습니다.</AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel disabled={busy}>취소</AlertDialogCancel><AlertDialogAction variant="destructive" disabled={busy} onClick={(event) => { event.preventDefault(); void permanentlyDelete(); }}><Trash2 /> 영구 삭제</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog></Card>;
}
