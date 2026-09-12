"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ContentPageEditor } from "@/components/hotel-admin/content-page-editor";
import { MediaField } from "@/components/hotel-admin/media-field";
import { WebsiteTranslationReviewActions } from "@/components/hotel-admin/website-translation-review-actions";
import {
  getWebsitePage, getWebsiteTranslation, getWebsiteTranslationReview, getWebsiteTranslationVersions, initializeWebsiteTranslation,
  publishWebsiteTranslation, saveWebsiteTranslation,
  type ContentReferenceCatalog, type WebContentVersion, type WebsitePageDocument, type WebsitePageDraftMetadata,
  type WebsiteTranslationReviewState,
} from "@/lib/staff-api";

type RecordValue = Record<string, unknown>;
const record = (value: unknown): RecordValue => value && typeof value === "object" && !Array.isArray(value) ? value as RecordValue : {};
const text = (value: unknown) => typeof value === "string" ? value : "";

function TranslationField({ label, value, onChange }: { label: string; value: unknown; onChange: (value: string) => void }) {
  return <label className="grid gap-1 text-sm font-medium">{label}<Textarea aria-label={label} value={text(value)} maxLength={1000} onChange={(event) => onChange(event.target.value)} /></label>;
}

function LandingTranslationEditor({ token, document, onDirtyChange, onBusyChange, onApplied }: {
  token: string; document: WebsitePageDocument; onDirtyChange: (dirty: boolean) => void; onBusyChange: (busy: boolean) => void;
  onApplied: (document: WebsitePageDocument, history: WebContentVersion[]) => void;
}) {
  const [content, setContent] = useState(document.draftContent);
  const [metadata, setMetadata] = useState<WebsitePageDraftMetadata>(document.draftMetadata);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const archived = document.lifecycleStatus === "ARCHIVED";
  function change(next: RecordValue) { setContent(next); setDirty(true); onDirtyChange(true); setError(""); setNotice(""); }
  function changeMetadata(next: WebsitePageDraftMetadata) { setMetadata(next); setDirty(true); onDirtyChange(true); setNotice(""); }
  async function save() {
    if (busy || archived || !dirty) return;
    setBusy(true); onBusyChange(true); setError(""); setNotice("");
    try {
      const result = await saveWebsiteTranslation(token, document.id, { expectedDraftVersion: document.draftVersion, page: metadata, content, connections: document.draftConnections });
      // A history read failure must not disguise an already successful write as a failed write.
      const history = await getWebsiteTranslationVersions(token, document.id).catch(() => []);
      setContent(result.draftContent); setMetadata(result.draftMetadata); setDirty(false); onDirtyChange(false); onApplied(result, history);
      setNotice("영어 초안을 저장했습니다. 검토를 요청해 주세요.");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "번역을 저장하지 못했습니다. 새로고침 후 다시 시도해 주세요."); }
    finally { setBusy(false); onBusyChange(false); }
  }
  const arrival = record(content.arrival), seo = record(content.seo);
  return <Card className="min-w-0"><CardHeader><CardTitle>영어 지점 랜딩 페이지</CardTitle><CardDescription>한국어 콘텐츠와 별도로 저장·발행합니다.</CardDescription>
    <div className="flex flex-wrap gap-2"><Button variant="outline" disabled={busy || archived || !dirty} onClick={() => void save()}>초안 저장</Button></div>
  </CardHeader><CardContent className="space-y-6">
    <fieldset disabled={busy || archived} className="grid min-w-0 gap-6 border-0 p-0">
      <section className="grid gap-4 rounded-xl border p-4"><h2 className="font-semibold">페이지 정보</h2><label className="grid gap-1 text-sm font-medium">메뉴 이름<Input aria-label="메뉴 이름" maxLength={100} value={metadata.menuLabel} onChange={(event) => changeMetadata({ ...metadata, menuLabel: event.target.value })} /></label>
        <label className="flex items-center gap-2 text-sm"><Checkbox aria-label="메뉴에 노출" checked={metadata.menuVisible} onCheckedChange={(menuVisible) => changeMetadata({ ...metadata, menuVisible })} />메뉴에 노출</label>
        <label className="grid gap-1 text-sm font-medium">메뉴 순서<Input aria-label="메뉴 순서" type="number" min={0} value={metadata.menuOrder} onChange={(event) => changeMetadata({ ...metadata, menuOrder: Math.max(0, Number(event.target.value) || 0) })} /></label>
        <p className="break-all text-sm text-muted-foreground">초안 주소: {document.draftMetadata.path}</p>
      </section>
      <section className="grid gap-4 rounded-xl border p-4"><h2 className="font-semibold">히어로</h2>
        {([['eyebrow', '상단 문구'], ['title', '히어로 제목'], ['description', '히어로 설명']] as const).map(([key, label]) => <TranslationField key={key} label={label} value={content[key]} onChange={(value) => change({ ...content, [key]: value })} />)}
        <MediaField token={token} assetId={text(content.heroAssetId)} deliveryUrl={text(content.heroImage)} altText={text(content.heroAlt)} protectedAssetIds={[text(content.heroAssetId)]}
          onAssetSelect={(asset, heroAlt) => change({ ...content, heroAssetId: asset.id, heroImage: asset.deliveryUrl, heroAlt })} onAltTextChange={(heroAlt) => change({ ...content, heroAlt })} />
      </section>
      <section className="grid gap-4 rounded-xl border p-4"><h2 className="font-semibold">도착 안내</h2>
        {([['address', '주소'], ['checkInOut', '체크인·체크아웃'], ['highlight', '도착 안내 설명']] as const).map(([key, label]) => <TranslationField key={key} label={label} value={arrival[key]} onChange={(value) => change({ ...content, arrival: { ...arrival, [key]: value } })} />)}
      </section>
      {(['experiences', 'offers'] as const).map((listKey) => {
        const items = Array.isArray(content[listKey]) ? content[listKey] as RecordValue[] : [];
        const fields = listKey === 'experiences' ? [['category', '분류'], ['title', '제목'], ['description', '설명']] : [['title', '제목'], ['detail', '상세'], ['bookingPeriod', '예약 기간'], ['stayPeriod', '투숙 기간']];
        return <section key={listKey} className="grid gap-4 rounded-xl border p-4"><h2 className="font-semibold">{listKey === 'experiences' ? '경험' : '오퍼'}</h2>{items.map((item, index) => <div key={index} className="grid gap-3 rounded-lg bg-muted/20 p-3 md:grid-cols-2">
          {fields.map(([key, label]) => <TranslationField key={key} label={`${listKey === 'experiences' ? '경험' : '오퍼'} ${index + 1} ${label}`} value={item[key]} onChange={(value) => change({ ...content, [listKey]: items.map((current, itemIndex) => itemIndex === index ? { ...current, [key]: value } : current) })} />)}
        </div>)}</section>;
      })}
      <section className="grid gap-4 rounded-xl border p-4"><h2 className="font-semibold">SEO</h2>
        <label className="grid gap-1 text-sm font-medium">SEO 제목<Input aria-label="SEO 제목" maxLength={60} value={text(seo.title)} onChange={(event) => change({ ...content, seo: { ...seo, title: event.target.value } })} /></label>
        <label className="grid gap-1 text-sm font-medium">SEO 설명<Textarea aria-label="SEO 설명" maxLength={160} value={text(seo.description)} onChange={(event) => change({ ...content, seo: { ...seo, description: event.target.value } })} /></label>
      </section>
    </fieldset>
    {(error || notice) && <p role="status" className={error ? "text-sm text-destructive" : "text-sm text-emerald-700"}>{error || notice}</p>}
  </CardContent></Card>;
}

export function WebsiteTranslationEditor({ token, pageId, catalog, onDirtyChange, onBusyChange }: {
  token: string; pageId: string; catalog: ContentReferenceCatalog; onDirtyChange: (dirty: boolean) => void; onBusyChange: (busy: boolean) => void;
}) {
  const [document, setDocument] = useState<WebsitePageDocument | null>(null);
  const [source, setSource] = useState<WebsitePageDocument | null>(null);
  const [history, setHistory] = useState<WebContentVersion[]>([]);
  const [reviewState, setReviewState] = useState<WebsiteTranslationReviewState>({ status: "DRAFT", reviewedDraftVersion: null, events: [] });
  const [editorDirty, setEditorDirty] = useState(false);
  const [editorBusy, setEditorBusy] = useState(false);
  const [error, setError] = useState("");
  const [initializing, setInitializing] = useState(false);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setDocument(null); setSource(null); setHistory([]); setReviewState({ status: "DRAFT", reviewedDraftVersion: null, events: [] });
    setEditorDirty(false); setEditorBusy(false); onDirtyChange(false); onBusyChange(false);
    Promise.all([getWebsiteTranslation(token, pageId), getWebsitePage(token, pageId), getWebsiteTranslationVersions(token, pageId), getWebsiteTranslationReview(token, pageId)])
      .then(([translation, original, versions, review]) => { if (active) { setDocument(translation); setSource(original); setHistory(versions); setReviewState(review); } })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : "영어 번역을 불러오지 못했습니다."); });
    return () => { active = false; };
  }, [token, pageId, reload, onDirtyChange, onBusyChange]);
  function changeDirty(dirty: boolean) { setEditorDirty(dirty); onDirtyChange(dirty); }
  function changeBusy(busy: boolean) { setEditorBusy(busy); onBusyChange(busy); }
  function retry() { setDocument(null); setError(""); changeDirty(false); changeBusy(false); setReload((value) => value + 1); }
  async function initialize() {
    if (!source || initializing || source.lifecycleStatus !== "ACTIVE") return;
    setInitializing(true); changeBusy(true); setError("");
    try { setDocument(await initializeWebsiteTranslation(token, pageId, source.draftVersion, source.lifecycleVersion)); setReviewState({ status: "DRAFT", reviewedDraftVersion: null, events: [] }); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "영어 초안을 가져오지 못했습니다."); }
    finally { setInitializing(false); changeBusy(false); }
  }
  const saved = (next: WebsitePageDocument, versions: WebContentVersion[]) => {
    setDocument(next); setHistory(versions); setReviewState((current) => ({ status: "DRAFT", reviewedDraftVersion: null, events: current.events }));
  };
  async function publish() {
    if (!document || editorDirty || editorBusy || document.lifecycleStatus === "ARCHIVED") return;
    const published = await publishWebsiteTranslation(token, document.id, document.draftVersion, document.publishedVersion);
    setDocument(published);
    const versions = await getWebsiteTranslationVersions(token, document.id).catch(() => null);
    if (versions) setHistory(versions);
  }
  if (!document) return <Card><CardContent className="p-6 text-sm"><p role="status">{error || "영어 번역을 불러오는 중입니다."}</p>{error && <Button className="mt-3" variant="outline" onClick={retry}>다시 불러오기</Button>}</CardContent></Card>;
  return <div className="grid min-w-0 gap-5 lg:grid-cols-[minmax(0,1fr)_240px]">
    <div className="grid min-w-0 content-start gap-4">
      <p role="status" className="rounded-lg border bg-muted/20 p-3 text-sm">영어 · {document.publishedVersion ? (Object.keys(document.publishedContent).length ? `발행본 v${document.publishedVersion}` : '공개 중단') : '미발행'} · 초안 v{document.draftVersion} — 한국어를 가져온 내용은 직접 번역하고 검토한 뒤 발행해 주세요.</p>
      {document.draftVersion === 0 ? <Card><CardHeader><CardTitle>영어 번역 초안이 없습니다.</CardTitle><CardDescription>한국어 초안을 가져와 제목·본문·SEO·이미지 설명을 번역합니다. 고객 웹에는 자동으로 공개되지 않습니다.</CardDescription></CardHeader><CardContent><Button disabled={initializing || document.lifecycleStatus !== "ACTIVE"} onClick={() => void initialize()}>한국어 초안을 가져오기</Button>{error && <p role="alert" className="mt-3 text-sm text-destructive">{error} <Button variant="link" onClick={retry}>다시 불러오기</Button></p>}</CardContent></Card>
        : <><WebsiteTranslationReviewActions token={token} pageId={document.id} draftVersion={document.draftVersion} state={reviewState} dirty={editorDirty} busy={editorBusy} archived={document.lifecycleStatus === "ARCHIVED"} onStateChange={setReviewState} onPublish={publish} onBusyChange={changeBusy} />
          {document.pageType === "HOTEL_LANDING" ? <LandingTranslationEditor token={token} document={document} onDirtyChange={changeDirty} onBusyChange={changeBusy} onApplied={saved} />
            : <ContentPageEditor token={token} document={document} catalog={catalog} locale="en" showPublishAction={false} onDirtyChange={changeDirty} onBusyChange={changeBusy} onSaved={saved} onPublished={saved} onLifecycleChanged={saved} onDeleted={() => undefined} />}</>}
      {document.lifecycleStatus === "ARCHIVED" && <p className="text-sm text-muted-foreground">페이지가 보관되어 두 언어 모두 공개되지 않습니다. 한국어 화면에서 페이지를 복원한 뒤 언어별로 다시 발행해 주세요.</p>}
    </div>
    <Card className="min-w-0 self-start"><CardHeader><CardTitle className="text-base">영어 발행 이력</CardTitle></CardHeader><CardContent className="space-y-3 text-sm">{history.length ? history.map((version) => <div key={version.version} className="rounded-lg border p-3"><p>발행본 v{version.version}</p><p className="mt-1 text-xs text-muted-foreground">{new Date(version.publishedAt).toLocaleString('ko-KR')}</p></div>) : <p className="text-muted-foreground">아직 영어 발행 이력이 없습니다.</p>}</CardContent></Card>
  </div>;
}
