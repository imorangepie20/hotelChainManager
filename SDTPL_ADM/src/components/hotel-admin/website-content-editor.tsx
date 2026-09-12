"use client";

import { WebsiteTranslationEditor } from "@/components/hotel-admin/website-translation-editor";
import { WebsiteSavedDraftPreviewAction } from "@/components/hotel-admin/website-saved-draft-preview-action";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, Eye, FilePenLine, GitCompareArrows, History, Plus, RotateCcw, Send, Trash2 } from "lucide-react";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  getWebContent,
  getWebContentVersions,
  getWebsiteHome,
  getWebsiteHomeVersions,
  getWebsitePage, getWebsitePageMoveImpact, moveWebsitePage,
  getWebsitePageVersions,
  getWebsitePageTree,
  getContentReferenceCatalog,
  restoreWebsitePageVersionDraft,
  publishWebContent,
  saveWebContent,
  type StaffPrincipal,
  type WebsitePageDraftMetadata,
  type WebsitePageMetadata,
  type WebsitePageTreeItem,
  type WebsitePageDocument,
  type ContentReferenceCatalog,
  type WebContentVersion,
} from "@/lib/staff-api";
import { WebsitePageTree } from "@/components/hotel-admin/website-page-tree";
import { ContentPageEditor } from "@/components/hotel-admin/content-page-editor";
import { ContentPageCreateDialog } from "@/components/hotel-admin/content-page-create-dialog";
import { ContentPageVersionCompareDialog } from "@/components/hotel-admin/content-page-version-compare-dialog";
import { ContentPageMoveDialog } from "@/components/hotel-admin/content-page-move-dialog";
import { MediaField } from "@/components/hotel-admin/media-field";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "속초", slug: "sokcho", menuOrder: 10 },
  { id: "11000000-0000-0000-0000-000000000002", name: "설악산", slug: "seoraksan", menuOrder: 20 },
  { id: "11000000-0000-0000-0000-000000000003", name: "제주도", slug: "jeju", menuOrder: 30 },
];

const customerWebOrigin = (process.env.NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN ?? "http://127.0.0.1:4000").replace(/\/$/, "");

type ContentRecord = Record<string, unknown>;

function parseContent(value: string): ContentRecord {
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as ContentRecord : {};
  } catch {
    return {};
  }
}

function textValue(record: ContentRecord, key: string) {
  return typeof record[key] === "string" ? record[key] as string : "";
}

function recordValue(record: ContentRecord, key: string): ContentRecord {
  const value = record[key];
  return value && typeof value === "object" && !Array.isArray(value) ? value as ContentRecord : {};
}

function recordsValue(record: ContentRecord, key: string): ContentRecord[] {
  return Array.isArray(record[key]) ? (record[key] as unknown[]).filter((item): item is ContentRecord => Boolean(item) && typeof item === "object" && !Array.isArray(item)) : [];
}

function previewImageUrl(value: string) {
  if (!value || /^(https?:)?\/\//i.test(value) || value.startsWith("data:")) return value;
  return `${customerWebOrigin}/${value.replace(/^\//, "")}`;
}

function treePageById(pages: readonly WebsitePageTreeItem[], pageId: string | null) {
  if (!pageId) return null;
  return pages.find((page) => page.id === pageId)
    ?? pages.flatMap((section) => section.children).find((page) => page.id === pageId)
    ?? null;
}
function flattenTree(pages: readonly WebsitePageTreeItem[]): WebsitePageTreeItem[] { return pages.flatMap((page) => [page, ...flattenTree(page.children)]); }

function defaultDraftPage(hotelId: string): WebsitePageDraftMetadata {
  const hotel = hotels.find((item) => item.id === hotelId) ?? hotels[0];
  return { slug: hotel.slug, menuLabel: hotel.name, menuVisible: true, menuOrder: hotel.menuOrder };
}

function toDraftPage(page: WebsitePageMetadata | null | undefined, hotelId: string): WebsitePageDraftMetadata {
  if (!page) return defaultDraftPage(hotelId);
  return { slug: page.slug, menuLabel: page.menuLabel, menuVisible: page.menuVisible, menuOrder: page.menuOrder };
}

function EditorField({ label, value, onChange, multiline = false, maxLength }: { label: string; value: string; onChange: (value: string) => void; multiline?: boolean; maxLength?: number }) {
  const className = "mt-1 w-full rounded-lg border bg-background px-3 py-2 text-sm outline-none transition focus:ring-2 focus:ring-ring";
  return (
    <label className="block text-sm font-medium">
      {label}
      {multiline ? (
        <textarea aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className={`${className} min-h-24 resize-y`} maxLength={maxLength} />
      ) : (
        <input aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className={className} maxLength={maxLength} />
      )}
    </label>
  );
}

export function WebsiteContentEditor() {
  const [locale, setLocale] = useState<"ko" | "en">("ko");
  const [pendingLocale, setPendingLocale] = useState<"ko" | "en" | null>(null);
  const [translationDirty, setTranslationDirty] = useState(false);
  const [translationBusy, setTranslationBusy] = useState(false);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [hotelId, setHotelId] = useState(hotels[0].id);
  const [selectedPageId, setSelectedPageId] = useState<string | null>(null);
  const [pendingPageId, setPendingPageId] = useState<string | null>(null);
  const [restoreVersion, setRestoreVersion] = useState<number | null>(null);
  const [comparisonSelection, setComparisonSelection] = useState<{ baseVersion: number; compareVersion: number } | null>(null);
  const [restoringVersion, setRestoringVersion] = useState<number | null>(null);
  const [contentPage, setContentPage] = useState<WebsitePageDocument | null>(null);
  const [contentDirty, setContentDirty] = useState(false);
  const [newContentPage, setNewContentPage] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [moveOpen, setMoveOpen] = useState(false);
  const [value, setValue] = useState("{}");
  const [dirty, setDirty] = useState(false);
  const [draftVersion, setDraftVersion] = useState(1);
  const [publishedVersion, setPublishedVersion] = useState(1);
  const [draftPage, setDraftPage] = useState<WebsitePageDraftMetadata>(() => defaultDraftPage(hotels[0].id));
  const [publishedPage, setPublishedPage] = useState<WebsitePageMetadata | null>(null);
  const [versions, setVersions] = useState<WebContentVersion[]>([]);
  const [pageTree, setPageTree] = useState<WebsitePageTreeItem[]>([]);
  const [referenceCatalog, setReferenceCatalog] = useState<ContentReferenceCatalog>({ hotels: [], pages: [] });
  const [referenceError, setReferenceError] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [versionRestoreNotice, setVersionRestoreNotice] = useState("");
  const [versionRestoreError, setVersionRestoreError] = useState("");
  const [busy, setBusy] = useState(false);
  const landingLoadRequest = useRef(0);
  const landingEditGeneration = useRef(0);
  const token = typeof window === "undefined" ? null : window.localStorage.getItem("hotel-chain-staff-session");
  const content = parseContent(value);
  const arrival = recordValue(content, "arrival");
  const seo = recordValue(content, "seo");
  const experiences = recordsValue(content, "experiences");
  const offers = recordsValue(content, "offers");
  const selectedHotel = hotels.find((hotel) => hotel.id === hotelId)?.name ?? "지점";
  const seoPreviewTitle = textValue(seo, "title") || `${selectedHotel} | STAY HANEUL`;
  const seoPreviewDescription = textValue(seo, "description") || "검색 결과에는 지점 랜딩 페이지의 기본 설명이 표시됩니다.";
  const draftPath = `/stays/${draftPage.slug}`;
  const pageStatus = !publishedPage ? "초안" : publishedPage.path === draftPath && draftVersion === publishedVersion ? "발행됨" : "발행 후 초안 변경";
  const selectedTreePage = treePageById(pageTree, selectedPageId);
  const moveParents = flattenTree(pageTree).filter((page) => page.pageType === "SECTION" && page.lifecycleStatus === "ACTIVE");
  const isStructuredPage = selectedTreePage?.pageType === "CONTENT_PAGE" || selectedTreePage?.pageType === "HOME_PAGE" || contentPage?.id === selectedPageId;
  const canRestorePageVersion = contentPage?.pageType === "CONTENT_PAGE"
    && contentPage.lifecycleStatus === "ACTIVE"
    && !contentDirty
    && !newContentPage;

  function changeDocument(update: (next: ContentRecord) => void) {
    const next = structuredClone(parseContent(value));
    update(next);
    setValue(JSON.stringify(next, null, 2));
    landingEditGeneration.current += 1;
    setDirty(true);
    setNotice("");
    setError("");
  }

  function changeText(key: string, nextValue: string) {
    changeDocument((next) => { next[key] = nextValue; });
  }

  function changeArrival(key: string, nextValue: string) {
    changeDocument((next) => {
      const nextArrival = recordValue(next, "arrival");
      nextArrival[key] = nextValue;
      next.arrival = nextArrival;
    });
  }

  function changeSeo(key: "title" | "description", nextValue: string) {
    changeDocument((next) => {
      const nextSeo = recordValue(next, "seo");
      nextSeo[key] = nextValue;
      next.seo = nextSeo;
    });
  }

  function changePage(update: (next: WebsitePageDraftMetadata) => WebsitePageDraftMetadata) {
    setDraftPage((current) => update(current));
    landingEditGeneration.current += 1;
    setDirty(true);
    setNotice("");
    setError("");
  }

  function changeListItem(listKey: "experiences" | "offers", index: number, key: string, nextValue: string) {
    changeDocument((next) => {
      const items = recordsValue(next, listKey);
      items[index] = { ...items[index], [key]: nextValue };
      next[listKey] = items;
    });
  }

  function addListItem(listKey: "experiences" | "offers") {
    changeDocument((next) => {
      const items = recordsValue(next, listKey);
      items.push(listKey === "experiences"
        ? { category: "EXPERIENCE", title: "", description: "" }
        : { title: "", detail: "", bookingPeriod: "", stayPeriod: "" });
      next[listKey] = items;
    });
  }

  function removeListItem(listKey: "experiences" | "offers", index: number) {
    changeDocument((next) => {
      next[listKey] = recordsValue(next, listKey).filter((_, itemIndex) => itemIndex !== index);
    });
  }

  function moveListItem(listKey: "experiences" | "offers", index: number, direction: -1 | 1) {
    changeDocument((next) => {
      const items = recordsValue(next, listKey);
      const destination = index + direction;
      if (destination < 0 || destination >= items.length) return;
      [items[index], items[destination]] = [items[destination], items[index]];
      next[listKey] = items;
    });
  }

  async function load() {
    if (!token) return;
    const request = ++landingLoadRequest.current;
    const editGeneration = landingEditGeneration.current;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const [document, history, tree, catalog] = await Promise.all([
        getWebContent(token, hotelId),
        getWebContentVersions(token, hotelId),
        getWebsitePageTree(token).catch(() => [] as WebsitePageTreeItem[]),
        getContentReferenceCatalog(token).catch(() => null),
      ]);
      if (request !== landingLoadRequest.current || editGeneration !== landingEditGeneration.current) return;
      setValue(JSON.stringify(document.draftContent, null, 2));
      setDirty(false);
      setDraftVersion(document.draftVersion);
      setPublishedVersion(document.publishedVersion);
      setDraftPage(toDraftPage(document.draftPage, hotelId));
      setPublishedPage(document.publishedPage ?? null);
      setVersions(history);
      setPageTree(tree);
      if (catalog) { setReferenceCatalog(catalog); setReferenceError(""); }
      else setReferenceError("콘텐츠 선택 정보를 불러오지 못했습니다. 다시 시도해 주세요.");
      const landingPage = tree.flatMap((section) => section.children).find((page) => page.hotelId === hotelId);
      const firstLeaf = tree.flatMap((section) => section.children)[0];
      const homePage = tree.find((page) => page.pageType === "HOME_PAGE");
      setSelectedPageId((current) => current ?? landingPage?.id ?? firstLeaf?.id ?? homePage?.id ?? null);
    } catch (cause) {
      if (request !== landingLoadRequest.current || editGeneration !== landingEditGeneration.current) return;
      setError(cause instanceof Error ? cause.message : "콘텐츠를 불러오지 못했습니다.");
    } finally {
      if (request === landingLoadRequest.current) setBusy(false);
    }
  }

  useEffect(() => {
    const saved = window.localStorage.getItem("hotel-chain-staff");
    if (saved) {
      const principal = JSON.parse(saved) as StaffPrincipal;
      setStaff(principal);
      if (principal.role === "HQ_EDITOR" || principal.role === "HQ_PUBLISHER") setLocale("en");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [hotelId]);

  async function loadContentPage(pageId: string) {
    if (!token) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const [document, history] = await Promise.all([getWebsitePage(token, pageId), getWebsitePageVersions(token, pageId)]);
      setContentPage(document); setVersions(history); setPublishedVersion(document.publishedVersion); setContentDirty(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "콘텐츠 페이지를 불러오지 못했습니다.");
    } finally { setBusy(false); }
  }

  async function loadHomePage() {
    if (!token) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const [document, history] = await Promise.all([getWebsiteHome(token), getWebsiteHomeVersions(token)]);
      setContentPage(document); setVersions(history); setPublishedVersion(document.publishedVersion); setContentDirty(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "홈페이지 콘텐츠를 불러오지 못했습니다.");
    } finally { setBusy(false); }
  }

  function activatePage(pageId: string) {
    const page = treePageById(pageTree, pageId);
    setSelectedPageId(pageId); setContentPage(null); setDirty(false); setContentDirty(false); setNewContentPage(false);
    if (page?.pageType === "HOME_PAGE") { void loadHomePage(); return; }
    if (page?.pageType === "CONTENT_PAGE") { void loadContentPage(pageId); return; }
    if (page?.hotelId) setHotelId(page.hotelId);
  }

  function selectPage(nextPageId: string) {
    if (nextPageId === selectedPageId) return;
    if (translationBusy || previewBusy) return;
    if (dirty || contentDirty || translationDirty) {
      setPendingPageId(nextPageId);
      return;
    }
    activatePage(nextPageId);
  }

  function applyLocale(next: "ko" | "en") {
    landingLoadRequest.current += 1;
    setLocale(next); setDirty(false); setContentDirty(false); setTranslationDirty(false); setNewContentPage(false);
    if (next === "ko" && selectedPageId) {
      const page = treePageById(pageTree, selectedPageId);
      if (page?.pageType === "HOTEL_LANDING") void load();
      else activatePage(selectedPageId);
    }
  }
  function selectLocale(next: "ko" | "en") {
    if (next === locale || busy || translationBusy || previewBusy) return;
    if (dirty || contentDirty || translationDirty) { setPendingLocale(next); return; }
    applyLocale(next);
  }
  const localeControls = <div role="group" aria-label="콘텐츠 언어" className="flex shrink-0 gap-2">
    <Button type="button" variant={locale === "ko" ? "default" : "outline"} aria-pressed={locale === "ko"} disabled={busy || translationBusy} onClick={() => selectLocale("ko")}>한국어</Button>
    <Button type="button" variant={locale === "en" ? "default" : "outline"} aria-pressed={locale === "en"} disabled={busy || translationBusy || !selectedPageId} onClick={() => selectLocale("en")}>영어</Button>
  </div>;
  const localeConfirmation = <AlertDialog open={pendingLocale !== null} onOpenChange={(open) => { if (!open) setPendingLocale(null); }}>
    <AlertDialogContent><AlertDialogHeader><AlertDialogTitle>저장하지 않고 언어를 전환할까요?</AlertDialogTitle><AlertDialogDescription>현재 언어의 저장되지 않은 변경사항은 사라집니다. 다른 언어의 저장된 초안·발행본은 바뀌지 않습니다.</AlertDialogDescription></AlertDialogHeader>
      <AlertDialogFooter><AlertDialogCancel>계속 편집</AlertDialogCancel><AlertDialogAction variant="destructive" onClick={() => { if (pendingLocale) applyLocale(pendingLocale); setPendingLocale(null); }}>변경 버리고 전환</AlertDialogAction></AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>;

  async function restoreVersionDraft() {
    const page = contentPage;
    const sourceVersion = restoreVersion;
    if (!token || !page || sourceVersion === null || !canRestorePageVersion || sourceVersion >= page.publishedVersion || restoringVersion !== null) return;

    setRestoringVersion(sourceVersion);
    setVersionRestoreError("");
    setVersionRestoreNotice("");
    try {
      const restored = await restoreWebsitePageVersionDraft(token, page.id, sourceVersion, {
        expectedLifecycleVersion: page.lifecycleVersion,
        expectedDraftVersion: page.draftVersion,
        expectedPublishedVersion: page.publishedVersion,
      });
      const history = await getWebsitePageVersions(token, page.id);
      setContentPage(restored);
      setVersions(history);
      setPublishedVersion(restored.publishedVersion);
      setContentDirty(false);
      setNewContentPage(false);
      setRestoreVersion(null);
      setVersionRestoreNotice(`발행본 v${sourceVersion}의 콘텐츠를 초안으로 복원했습니다. 고객 웹에는 아직 반영되지 않으며, 확인 후 발행해 주세요.`);
      void getWebsitePageTree(token).then(setPageTree).catch(() => undefined);
    } catch (cause) {
      setVersionRestoreError(cause instanceof Error ? cause.message : "발행본 콘텐츠를 초안으로 복원하지 못했습니다.");
    } finally {
      setRestoringVersion(null);
    }
  }

  async function save() {
    if (!token) return;

    let content: Record<string, unknown>;
    try {
      content = JSON.parse(value) as Record<string, unknown>;
    } catch {
      setNotice("");
      setError("JSON 형식을 확인해 주세요.");
      return;
    }

    setBusy(true);
    setNotice("");
    setError("");
    try {
      const document = await saveWebContent(token, hotelId, draftVersion, content, draftPage);
      setValue(JSON.stringify(document.draftContent, null, 2));
      setDirty(false);
      setDraftVersion(document.draftVersion);
      setDraftPage(toDraftPage(document.draftPage, hotelId));
      setPublishedPage(document.publishedPage ?? null);
      if (document.draftPage) {
        setPageTree((current) => current.map((section) => ({
          ...section,
          children: section.children.map((page) => page.hotelId === hotelId
            ? { ...page, label: document.draftPage!.menuLabel, draftPath: document.draftPage!.path }
            : page),
        })));
      }
      setNotice("초안이 저장되었습니다. 이제 저장된 초안을 발행할 수 있습니다.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "초안을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function publish() {
    if (!token || dirty) return;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const document = await publishWebContent(token, hotelId, draftVersion, publishedVersion);
      setDraftVersion(document.draftVersion);
      setPublishedVersion(document.publishedVersion);
      setDraftPage(toDraftPage(document.draftPage, hotelId));
      setPublishedPage(document.publishedPage ?? null);
      if (document.draftPage && document.publishedPage) {
        setPageTree((current) => current.map((section) => ({
          ...section,
          children: section.children.map((page) => page.hotelId === hotelId
            ? { ...page, label: document.draftPage!.menuLabel, draftPath: document.draftPage!.path, publishedPath: document.publishedPage!.path, status: "PUBLISHED" }
            : page),
        })));
      }
      setVersions(await getWebContentVersions(token, hotelId));
      setNotice("발행본이 고객 웹에 적용되었습니다.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "발행하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (staff?.role === "BRANCH_STAFF") {
    return (
      <Card>
        <CardHeader>
          <CardTitle>웹사이트 콘텐츠</CardTitle>
          <CardDescription>고객 웹 발행은 본사 계정에서만 할 수 있습니다.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  if (locale === "en") return <div className="flex flex-col gap-5">
    <section className="flex flex-col justify-between gap-4 rounded-xl border bg-card p-5 sm:flex-row sm:items-center"><div><h1 className="text-2xl font-semibold tracking-tight">웹사이트 콘텐츠</h1><p className="mt-2 text-sm text-muted-foreground">영어 초안과 발행본은 한국어와 독립적으로 관리합니다. 페이지 구조·보관·복원은 한국어 화면에서 관리합니다.</p></div>{localeControls}</section>
    <div className="grid min-w-0 gap-5 xl:grid-cols-[220px_minmax(0,1fr)]">
      <WebsitePageTree pages={pageTree} selectedPageId={selectedPageId} onSelectPage={selectPage} />
      {selectedPageId && staff && <WebsiteTranslationEditor key={selectedPageId} token={token ?? ""} pageId={selectedPageId} catalog={referenceCatalog} staff={staff} onDirtyChange={setTranslationDirty} onBusyChange={setTranslationBusy} />}
    </div>
    {localeConfirmation}
    <AlertDialog open={pendingPageId !== null} onOpenChange={(open) => { if (!open) setPendingPageId(null); }}><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>저장하지 않고 페이지를 이동할까요?</AlertDialogTitle><AlertDialogDescription>현재 영어 초안의 저장되지 않은 변경사항은 사라집니다.</AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel>계속 편집</AlertDialogCancel><AlertDialogAction variant="destructive" onClick={() => { if (pendingPageId) { setSelectedPageId(pendingPageId); setTranslationDirty(false); } setPendingPageId(null); }}>변경 버리고 이동</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog>
  </div>;

  return (
    <div className="flex flex-col gap-5">
      <section className="flex flex-col justify-between gap-4 rounded-xl border bg-card p-5 shadow-sm lg:flex-row lg:items-end">
        <div>
          <p className="text-sm font-medium text-primary">WEBSITE CMS · DRAFT</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">웹사이트 콘텐츠</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            초안을 저장한 뒤 발행하면 고객 웹에 노출됩니다. 예약·요금·재고는 이 화면에서 수정하지 않습니다.
          </p>
          {dirty && <p className="mt-2 text-sm font-medium text-amber-700">저장되지 않은 변경사항이 있습니다.</p>}
          {referenceError && <p role="status" className="mt-2 text-sm text-destructive">{referenceError} <Button type="button" variant="link" className="h-auto px-0" onClick={() => void getContentReferenceCatalog(token ?? "").then((catalog) => { setReferenceCatalog(catalog); setReferenceError(""); }).catch(() => setReferenceError("콘텐츠 선택 정보를 불러오지 못했습니다. 다시 시도해 주세요."))}>다시 시도</Button></p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {localeControls}
          <ContentPageCreateDialog
            token={token ?? ""}
            sections={pageTree}
            catalog={referenceCatalog}
            onCreated={(document) => {
              setContentPage(document); setSelectedPageId(document.id); setContentDirty(true); setNewContentPage(true);
              void getWebsitePageTree(token ?? "").then(setPageTree).catch(() => undefined);
            }}
          />
          {selectedTreePage?.pageType === "CONTENT_PAGE" && selectedTreePage.lifecycleStatus === "ACTIVE" && !contentDirty && <Button variant="outline" onClick={() => setMoveOpen(true)} disabled={busy}>페이지 이동</Button>}
          {!isStructuredPage && <>
            {selectedTreePage && <WebsiteSavedDraftPreviewAction token={token ?? ""} pageId={selectedTreePage.id} locale="ko"
              draftVersion={draftVersion} draftPath={draftPath} dirty={dirty} disabled={busy || selectedTreePage.lifecycleStatus === "ARCHIVED"} onBusyChange={setPreviewBusy} />}
            <Button variant="outline" onClick={() => setPreviewOpen(true)} disabled={busy}>
              <Eye /> 미리보기
            </Button>
            <Button variant="outline" onClick={save} disabled={busy || previewBusy || !dirty}>
              <FilePenLine /> 초안 저장
            </Button>
            <Button onClick={publish} disabled={busy || previewBusy || dirty} title={dirty ? "변경사항을 먼저 초안으로 저장해 주세요." : undefined}>
              <Send /> 발행
            </Button>
          </>}
        </div>
      </section>

      {localeConfirmation}

      <ContentPageMoveDialog open={moveOpen} page={selectedTreePage} parents={moveParents} busy={busy} onOpenChange={setMoveOpen}
        onImpact={(parentId, slug) => getWebsitePageMoveImpact(token ?? "", selectedTreePage?.id ?? "", parentId, slug)}
        onMove={async (parentId, slug) => {
          if (!token || !contentPage) return;
          setBusy(true);
          try {
            const expectedPublishedVersion = Object.keys(contentPage.publishedContent).length > 0 ? contentPage.publishedVersion : 0;
            const moved = await moveWebsitePage(token, contentPage.id, { parentId, slug, expectedDraftVersion: contentPage.draftVersion, expectedLifecycleVersion: contentPage.lifecycleVersion, expectedPublishedVersion });
            setContentPage(moved); setNotice(expectedPublishedVersion > 0 ? "페이지를 이동하고 기존 공개 경로에 301 리디렉션을 만들었습니다." : "페이지 초안 경로를 이동했습니다.");
            setPageTree(await getWebsitePageTree(token));
          } finally { setBusy(false); }
        }} />

      <div className="grid gap-5 xl:grid-cols-[220px_1fr_260px]">
        <WebsitePageTree
          pages={pageTree}
          selectedPageId={selectedPageId}
          onSelectPage={selectPage}
        />

        {isStructuredPage ? (contentPage ? <ContentPageEditor
          key={contentPage.id}
          token={token ?? ""}
          document={contentPage}
          catalog={referenceCatalog}
          initialDirty={newContentPage}
          externalBusy={busy}
          onBusyChange={setPreviewBusy}
          onDirtyChange={setContentDirty}
          onSaved={(document, history) => { setContentPage(document); setVersions(history); setPublishedVersion(document.publishedVersion); setNewContentPage(false); void getWebsitePageTree(token ?? "").then(setPageTree).catch(() => undefined); }}
          onPublished={(document, history) => { setContentPage(document); setVersions(history); setPublishedVersion(document.publishedVersion); void getWebsitePageTree(token ?? "").then(setPageTree).catch(() => undefined); }}
          onLifecycleChanged={(document, history) => { setContentPage(document); setVersions(history); setPublishedVersion(document.publishedVersion); setContentDirty(false); setNewContentPage(false); void getWebsitePageTree(token ?? "").then(setPageTree).catch(() => undefined); }}
          onDeleted={() => {
            const homePage = pageTree.find((page) => page.pageType === "HOME_PAGE");
            setContentPage(null); setVersions([]); setContentDirty(false); setNewContentPage(false); setRestoreVersion(null); setComparisonSelection(null);
            if (homePage) activatePage(homePage.id);
            else setSelectedPageId(null);
            void getWebsitePageTree(token ?? "").then(setPageTree).catch(() => undefined);
          }}
        /> : <Card><CardContent className="p-6 text-sm text-muted-foreground">콘텐츠 페이지를 불러오는 중입니다.</CardContent></Card>) : <Card className="min-w-0">
          <CardHeader>
            <CardTitle>지점 랜딩 페이지</CardTitle>
            <CardDescription>고객 웹에 표시할 주소, 메뉴, 히어로, 경험, 오퍼와 도착 안내를 편집합니다.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <section className="space-y-4 rounded-xl border p-4">
              <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-start">
                <div><h2 className="font-semibold">페이지 정보</h2><p className="text-xs text-muted-foreground">주소와 메뉴 설정은 콘텐츠와 함께 초안으로 저장되고, 발행 시에만 고객 웹에 반영됩니다.</p></div>
                <span className="shrink-0 rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground">{pageStatus}</span>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <EditorField label="주소 슬러그" value={draftPage.slug} onChange={(slug) => changePage((page) => ({ ...page, slug }))} />
                <EditorField label="메뉴 이름" value={draftPage.menuLabel} onChange={(menuLabel) => changePage((page) => ({ ...page, menuLabel }))} />
                <label className="flex min-h-11 items-center gap-2 rounded-lg border px-3 text-sm font-medium">
                  <input aria-label="메뉴에 노출" type="checkbox" checked={draftPage.menuVisible} onChange={(event) => changePage((page) => ({ ...page, menuVisible: event.target.checked }))} className="size-4 accent-primary" />
                  메뉴에 노출
                </label>
                <label className="block text-sm font-medium">
                  메뉴 순서
                  <input aria-label="메뉴 순서" type="number" min={0} value={draftPage.menuOrder} onChange={(event) => changePage((page) => ({ ...page, menuOrder: Math.max(0, Number(event.target.value) || 0) }))} className="mt-1 w-full rounded-lg border bg-background px-3 py-2 text-sm outline-none transition focus:ring-2 focus:ring-ring" />
                </label>
              </div>
              <div className="rounded-lg border bg-muted/30 p-3 text-sm">
                <p className="text-xs font-medium text-muted-foreground">초안 주소</p>
                <p className="mt-1 font-medium text-foreground">{draftPath}</p>
                {publishedPage && <p className="mt-2 text-xs text-muted-foreground">현재 발행 주소: {publishedPage.path}</p>}
              </div>
              <p className="text-xs text-muted-foreground">슬러그는 영문 소문자·숫자·하이픈만 사용합니다. 예약·재고·가격 정보는 이 페이지에 저장하지 않습니다.</p>
            </section>

            <section className="space-y-4 rounded-xl border p-4">
              <div><h2 className="font-semibold">히어로</h2><p className="text-xs text-muted-foreground">지점 첫 화면의 핵심 메시지와 대표 이미지입니다.</p></div>
              <div className="grid gap-4 md:grid-cols-2">
                <EditorField label="영문 지점 표기" value={textValue(content, "eyebrow")} onChange={(next) => changeText("eyebrow", next)} />
                <EditorField label="히어로 제목" value={textValue(content, "title")} onChange={(next) => changeText("title", next)} />
                <div className="md:col-span-2"><EditorField multiline label="히어로 설명" value={textValue(content, "description")} onChange={(next) => changeText("description", next)} /></div>
                <MediaField token={token ?? ""} assetId={textValue(content, "heroAssetId")} deliveryUrl={textValue(content, "heroImage")} altText={textValue(content, "heroAlt")} onAssetSelect={(asset, imageAlt) => changeDocument((next) => { next.heroAssetId = asset.id; next.heroImage = asset.deliveryUrl; next.heroAlt = imageAlt; })} onAltTextChange={(heroAlt) => changeText("heroAlt", heroAlt)} />
              </div>
            </section>

            <section className="space-y-4 rounded-xl border p-4">
              <div><h2 className="font-semibold">검색 결과</h2><p className="text-xs text-muted-foreground">발행 후 선택한 지점의 고객 웹 브라우저 제목과 설명에 적용됩니다.</p></div>
              <div className="grid gap-4">
                <EditorField label="검색 결과 제목" value={textValue(seo, "title")} onChange={(next) => changeSeo("title", next)} maxLength={60} />
                <EditorField multiline label="검색 결과 설명" value={textValue(seo, "description")} onChange={(next) => changeSeo("description", next)} maxLength={160} />
              </div>
              <div className="rounded-lg border bg-muted/30 p-4">
                <p className="text-xs font-medium text-muted-foreground">검색 결과 미리보기</p>
                <p className="mt-2 text-sm font-medium text-primary">{seoPreviewTitle}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{seoPreviewDescription}</p>
              </div>
              <p className="text-xs text-muted-foreground">제목은 60자, 설명은 160자까지 입력할 수 있습니다. 두 값을 모두 입력하면 저장할 수 있습니다.</p>
            </section>

            <section className="space-y-4 rounded-xl border p-4">
              <div><h2 className="font-semibold">도착 안내</h2><p className="text-xs text-muted-foreground">주소와 체크인·체크아웃 정보를 안내합니다.</p></div>
              <EditorField label="주소" value={textValue(arrival, "address")} onChange={(next) => changeArrival("address", next)} />
              <EditorField label="체크인 / 체크아웃" value={textValue(arrival, "checkInOut")} onChange={(next) => changeArrival("checkInOut", next)} />
              <EditorField multiline label="도착 안내 문구" value={textValue(arrival, "highlight")} onChange={(next) => changeArrival("highlight", next)} />
            </section>

            <section className="space-y-4 rounded-xl border p-4">
              <div className="flex items-center justify-between gap-3"><div><h2 className="font-semibold">경험</h2><p className="text-xs text-muted-foreground">객실·다이닝·체험 카드를 관리합니다.</p></div><Button type="button" size="sm" variant="outline" onClick={() => addListItem("experiences")}><Plus /> 추가</Button></div>
              {experiences.map((item, index) => (
                <div key={index} className="grid gap-3 rounded-lg bg-muted/30 p-3 md:grid-cols-2">
                  <div className="flex items-center justify-between gap-2 md:col-span-2">
                    <p className="text-sm font-medium">경험 카드 {index + 1}</p>
                    <div className="flex gap-1">
                      <Button type="button" variant="ghost" size="icon-sm" aria-label={`경험 ${index + 1} 위로 이동`} disabled={index === 0} onClick={() => moveListItem("experiences", index, -1)}><ChevronUp /></Button>
                      <Button type="button" variant="ghost" size="icon-sm" aria-label={`경험 ${index + 1} 아래로 이동`} disabled={index === experiences.length - 1} onClick={() => moveListItem("experiences", index, 1)}><ChevronDown /></Button>
                    </div>
                  </div>
                  <EditorField label={`경험 ${index + 1} 분류`} value={textValue(item, "category")} onChange={(next) => changeListItem("experiences", index, "category", next)} />
                  <EditorField label={`경험 ${index + 1} 제목`} value={textValue(item, "title")} onChange={(next) => changeListItem("experiences", index, "title", next)} />
                  <div className="md:col-span-2"><EditorField multiline label={`경험 ${index + 1} 설명`} value={textValue(item, "description")} onChange={(next) => changeListItem("experiences", index, "description", next)} /></div>
                  <Button type="button" size="sm" variant="ghost" className="justify-self-end text-destructive md:col-span-2" onClick={() => removeListItem("experiences", index)}><Trash2 /> 삭제</Button>
                </div>
              ))}
            </section>

            <section className="space-y-4 rounded-xl border p-4">
              <div className="flex items-center justify-between gap-3"><div><h2 className="font-semibold">오퍼</h2><p className="text-xs text-muted-foreground">프로모션 문구와 예약·투숙 기간을 관리합니다.</p></div><Button type="button" size="sm" variant="outline" onClick={() => addListItem("offers")}><Plus /> 추가</Button></div>
              {offers.map((item, index) => (
                <div key={index} className="grid gap-3 rounded-lg bg-muted/30 p-3 md:grid-cols-2">
                  <div className="flex items-center justify-between gap-2 md:col-span-2">
                    <p className="text-sm font-medium">오퍼 카드 {index + 1}</p>
                    <div className="flex gap-1">
                      <Button type="button" variant="ghost" size="icon-sm" aria-label={`오퍼 ${index + 1} 위로 이동`} disabled={index === 0} onClick={() => moveListItem("offers", index, -1)}><ChevronUp /></Button>
                      <Button type="button" variant="ghost" size="icon-sm" aria-label={`오퍼 ${index + 1} 아래로 이동`} disabled={index === offers.length - 1} onClick={() => moveListItem("offers", index, 1)}><ChevronDown /></Button>
                    </div>
                  </div>
                  <EditorField label={`오퍼 ${index + 1} 제목`} value={textValue(item, "title")} onChange={(next) => changeListItem("offers", index, "title", next)} />
                  <EditorField label={`오퍼 ${index + 1} 상세`} value={textValue(item, "detail")} onChange={(next) => changeListItem("offers", index, "detail", next)} />
                  <EditorField label={`오퍼 ${index + 1} 예약 기간`} value={textValue(item, "bookingPeriod")} onChange={(next) => changeListItem("offers", index, "bookingPeriod", next)} />
                  <EditorField label={`오퍼 ${index + 1} 투숙 기간`} value={textValue(item, "stayPeriod")} onChange={(next) => changeListItem("offers", index, "stayPeriod", next)} />
                  <Button type="button" size="sm" variant="ghost" className="justify-self-end text-destructive md:col-span-2" onClick={() => removeListItem("offers", index)}><Trash2 /> 삭제</Button>
                </div>
              ))}
            </section>

            <details className="rounded-xl border bg-muted/20 p-4">
              <summary className="cursor-pointer text-sm font-medium">고급 JSON 편집</summary>
              <textarea aria-label="콘텐츠 JSON 편집기" value={value} onChange={(event) => { setValue(event.target.value); landingEditGeneration.current += 1; setDirty(true); setNotice(""); setError(""); }} className="mt-4 min-h-72 w-full rounded-lg border bg-background p-4 font-mono text-xs leading-6 outline-none focus:ring-2 focus:ring-ring" spellCheck={false} />
            </details>
            {(notice || error) && (
              <p role="status" className={`mt-3 text-sm ${error ? "text-destructive" : "text-emerald-700"}`}>
                {error || notice}
              </p>
            )}
          </CardContent>
        </Card>}

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base"><History className="size-4" /> 발행 이력</CardTitle>
            <CardDescription>현재 발행본 v{publishedVersion}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            {versions.length ? versions.map((version) => {
              const canRestoreThisVersion = Boolean(canRestorePageVersion && version.version < (contentPage?.publishedVersion ?? 0));
              const hasRestoreControl = contentPage?.pageType === "CONTENT_PAGE" && version.version < (contentPage?.publishedVersion ?? 0);
              return <div key={version.version} className="rounded-lg border p-3">
                <p className="font-medium">발행본 v{version.version}</p>
                <p className="mt-1 text-xs text-muted-foreground">{new Date(version.publishedAt).toLocaleString("ko-KR")}</p>
                {hasRestoreControl && <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  className="mt-3 w-full justify-start"
                  onClick={() => setComparisonSelection({ baseVersion: version.version, compareVersion: contentPage!.publishedVersion })}
                >
                  <GitCompareArrows /> 발행본 v{version.version}와 현재 발행본 비교
                </Button>}
                {hasRestoreControl && <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="mt-3 w-full justify-start"
                  disabled={!canRestoreThisVersion || restoringVersion !== null}
                  title={!canRestoreThisVersion ? "활성 일반 콘텐츠 페이지의 저장된 초안에서만 이전 발행본 콘텐츠를 복원할 수 있습니다." : undefined}
                  onClick={() => setRestoreVersion(version.version)}
                >
                  <RotateCcw /> 발행본 v{version.version} 콘텐츠를 초안으로 복원
                </Button>}
              </div>;
            }) : <p className="text-muted-foreground">아직 발행 이력이 없습니다.</p>}
            {contentPage?.pageType === "CONTENT_PAGE" && contentDirty && versions.some((version) => version.version < contentPage.publishedVersion) && <p className="text-xs leading-5 text-muted-foreground">저장되지 않은 변경사항을 먼저 초안으로 저장한 뒤 이전 발행본 콘텐츠를 복원할 수 있습니다.</p>}
            {(versionRestoreNotice || versionRestoreError) && <p role="status" className={versionRestoreError ? "text-sm text-destructive" : "text-sm text-emerald-700"}>{versionRestoreError || versionRestoreNotice}</p>}
          </CardContent>
        </Card>
      </div>

      <AlertDialog open={restoreVersion !== null} onOpenChange={(open) => { if (!open) setRestoreVersion(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>발행본을 초안으로 복원할까요?</AlertDialogTitle>
            <AlertDialogDescription>현재 저장된 초안 콘텐츠가 선택한 발행본 콘텐츠로 대체됩니다. 선택한 발행본의 콘텐츠만 현재 초안으로 가져옵니다. 고객 웹에는 아직 반영되지 않으며, 내용을 확인한 뒤 발행해야 공개됩니다.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={restoringVersion !== null}>취소</AlertDialogCancel>
            <AlertDialogAction disabled={restoringVersion !== null} onClick={() => void restoreVersionDraft()}><RotateCcw /> 초안으로 복원</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <ContentPageVersionCompareDialog
        token={token ?? ""}
        pageId={contentPage?.id ?? ""}
        versions={versions}
        initialBaseVersion={comparisonSelection?.baseVersion ?? null}
        initialCompareVersion={comparisonSelection?.compareVersion ?? null}
        open={comparisonSelection !== null}
        onOpenChange={(open) => { if (!open) setComparisonSelection(null); }}
      />

      <AlertDialog open={pendingPageId !== null} onOpenChange={(open) => { if (!open) setPendingPageId(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>저장하지 않고 페이지를 이동할까요?</AlertDialogTitle>
            <AlertDialogDescription>현재 페이지의 저장되지 않은 변경사항은 사라집니다.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>계속 편집</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                if (pendingPageId) activatePage(pendingPageId);
                setPendingPageId(null);
              }}
            >
              변경 버리고 이동
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto p-0 sm:max-w-4xl" showCloseButton>
          <DialogHeader className="border-b p-5 pr-12">
            <DialogTitle>{selectedHotel} 지점 랜딩 미리보기</DialogTitle>
            <DialogDescription>저장하거나 발행하지 않은 현재 편집 내용을 기준으로 표시합니다.</DialogDescription>
          </DialogHeader>
          <article className="bg-background">
            <section className="relative min-h-80 overflow-hidden bg-slate-950 text-white">
              {textValue(content, "heroImage") && (
                <img
                  src={previewImageUrl(textValue(content, "heroImage"))}
                  alt={textValue(content, "heroAlt")}
                  className="absolute inset-0 size-full object-cover opacity-45"
                />
              )}
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/40 to-transparent" />
              <div className="relative flex min-h-80 max-w-2xl flex-col justify-end p-6 sm:p-10">
                <p className="text-xs font-semibold tracking-[0.2em] text-white/70">{textValue(content, "eyebrow")}</p>
                <h1 className="mt-3 whitespace-pre-line text-3xl font-semibold tracking-tight sm:text-5xl">{textValue(content, "title") || "제목을 입력해 주세요"}</h1>
                <p className="mt-4 max-w-xl whitespace-pre-line text-sm leading-6 text-white/80 sm:text-base">{textValue(content, "description")}</p>
              </div>
            </section>

            <div className="grid gap-8 p-6 sm:p-10">
              <section className="grid gap-4 border-b pb-8 sm:grid-cols-[0.8fr_1.2fr]">
                <div>
                  <p className="text-xs font-semibold tracking-[0.18em] text-primary">ARRIVAL</p>
                  <h2 className="mt-2 text-2xl font-semibold">도착 안내</h2>
                </div>
                <div className="grid gap-3 text-sm leading-6 text-muted-foreground">
                  <p className="font-medium text-foreground">{textValue(arrival, "address")}</p>
                  <p>체크인 / 체크아웃 {textValue(arrival, "checkInOut")}</p>
                  <p>{textValue(arrival, "highlight")}</p>
                </div>
              </section>

              {experiences.length > 0 && (
                <section>
                  <p className="text-xs font-semibold tracking-[0.18em] text-primary">EXPERIENCES</p>
                  <h2 className="mt-2 text-2xl font-semibold">머무는 동안의 경험</h2>
                  <div className="mt-5 grid gap-3 sm:grid-cols-3">
                    {experiences.map((item, index) => (
                      <div key={index} className="rounded-xl border bg-muted/30 p-4">
                        <p className="text-xs font-semibold tracking-[0.14em] text-primary">{textValue(item, "category")}</p>
                        <h3 className="mt-3 font-semibold">{textValue(item, "title")}</h3>
                        <p className="mt-2 text-sm leading-6 text-muted-foreground">{textValue(item, "description")}</p>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {offers.length > 0 && (
                <section>
                  <p className="text-xs font-semibold tracking-[0.18em] text-primary">OFFERS</p>
                  <h2 className="mt-2 text-2xl font-semibold">추천 오퍼</h2>
                  <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    {offers.map((item, index) => (
                      <div key={index} className="rounded-xl border p-4">
                        <h3 className="font-semibold">{textValue(item, "title")}</h3>
                        <p className="mt-2 text-sm leading-6 text-muted-foreground">{textValue(item, "detail")}</p>
                        <p className="mt-4 text-xs text-muted-foreground">예약 {textValue(item, "bookingPeriod")}</p>
                        <p className="mt-1 text-xs text-muted-foreground">투숙 {textValue(item, "stayPeriod")}</p>
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </div>
          </article>
        </DialogContent>
      </Dialog>
    </div>
  );
}
