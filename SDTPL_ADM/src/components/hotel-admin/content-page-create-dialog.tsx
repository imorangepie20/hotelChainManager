"use client";

import { useEffect, useMemo, useState } from "react";
import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { createWebsitePage, type ContentKind, type ContentReferenceCatalog, type WebsitePageConnections, type WebsitePageDocument, type WebsitePageTreeItem } from "@/lib/staff-api";

type PageKind = Exclude<ContentKind, "HOME" | "DESTINATION">;

const kinds: Array<{ value: PageKind; label: string; requiresHotel: boolean; allowsHotel: boolean }> = [
  { value: "ROOM", label: "객실", requiresHotel: true, allowsHotel: true },
  { value: "DINING", label: "다이닝", requiresHotel: true, allowsHotel: true },
  { value: "FACILITY", label: "부대시설", requiresHotel: true, allowsHotel: true },
  { value: "EXPERIENCE", label: "경험", requiresHotel: true, allowsHotel: true },
  { value: "PROMOTION", label: "프로모션", requiresHotel: false, allowsHotel: false },
  { value: "GUIDE", label: "이용 안내", requiresHotel: false, allowsHotel: true },
  { value: "BRAND", label: "브랜드", requiresHotel: false, allowsHotel: false },
];
const assetId = "14000000-0000-0000-0000-000000000001";
const id = () => crypto.randomUUID();

function defaultContent(kind: PageKind, label: string, hotelId: string | null, roomTypeId: string | null) {
  const hero = { blockId: id(), type: "HERO", imageAssetId: assetId, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: `${label} 대표 이미지`, eyebrow: "STAY HANEUL", title: label, description: "STAY HANEUL의 새로운 콘텐츠를 소개합니다." };
  const booking = { blockId: id(), type: "BOOKING_CTA", title: "객실 검색", description: "날짜와 인원을 선택하면 실시간 객실을 확인합니다.", label: "객실 검색", hotelId: hotelId ?? "11000000-0000-0000-0000-000000000001", ...(roomTypeId ? { roomTypeId } : {}) };
  const gallery = { blockId: id(), type: "IMAGE_GALLERY", title: "이미지", items: [{ imageAssetId: assetId, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: `${label} 이미지` }, { imageAssetId: assetId, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: `${label} 이미지` }] };
  const spec = { blockId: id(), type: "SPEC_TABLE", title: "상세 정보", rows: [{ label: "안내", value: "내용을 입력해 주세요." }] };
  const rich = { blockId: id(), type: "RICH_TEXT", title: "소개", paragraphs: ["내용을 입력해 주세요."] };
  const hours = { blockId: id(), type: "OPERATING_HOURS", title: "운영 시간", entries: [{ dayLabel: "매일", opensAt: "09:00", closesAt: "18:00", closed: false }] };
  const summary = { blockId: id(), type: "PROMOTION_SUMMARY", title: "프로모션 안내", salesPeriod: "기간을 입력해 주세요.", stayPeriod: "기간을 입력해 주세요.", benefits: ["혜택을 입력해 주세요."] };
  const byKind: Record<PageKind, unknown[]> = {
    ROOM: [hero, gallery, spec, booking], DINING: [hero, gallery, hours], FACILITY: [hero, gallery, spec], EXPERIENCE: [hero, rich], PROMOTION: [hero, summary, booking], GUIDE: [hero], BRAND: [hero],
  };
  return { seo: { title: label, description: "STAY HANEUL의 새로운 콘텐츠를 소개합니다." }, blocks: byKind[kind] };
}

export function ContentPageCreateDialog({ token, sections, catalog, onCreated }: { token: string; sections: readonly WebsitePageTreeItem[]; catalog: ContentReferenceCatalog; onCreated: (document: WebsitePageDocument) => void }) {
  const [open, setOpen] = useState(false); const [kind, setKind] = useState<PageKind>("BRAND"); const [parentId, setParentId] = useState(""); const [hotelId, setHotelId] = useState<string | null>(null); const [roomTypeId, setRoomTypeId] = useState(""); const [targetHotelIds, setTargetHotelIds] = useState<string[]>([]); const [menuLabel, setMenuLabel] = useState(""); const [slug, setSlug] = useState(""); const [menuVisible, setMenuVisible] = useState(true); const [menuOrder, setMenuOrder] = useState(0); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const selectedKind = kinds.find((item) => item.value === kind)!;
  const sectionsForKind = useMemo(() => sections.filter((section) => section.pageType === "SECTION" && (kind === "GUIDE" ? true : selectedKind.requiresHotel ? section.hotelId !== null : section.hotelId === null)), [sections, kind, selectedKind.requiresHotel]);
  const selectedHotel = catalog.hotels.find((hotel) => hotel.id === hotelId);
  const connections: WebsitePageConnections = { roomTypeIds: roomTypeId ? [roomTypeId] : [], targetHotelIds, relatedPages: [] };
  const promotionMissingTargets = kind === "PROMOTION" && targetHotelIds.length === 0;
  const invalid = !parentId || !menuLabel.trim() || !slug.trim() || (selectedKind.requiresHotel && !hotelId) || (kind === "ROOM" && !roomTypeId) || promotionMissingTargets;
  useEffect(() => { setParentId(""); setHotelId(null); setRoomTypeId(""); setTargetHotelIds([]); setError(""); }, [kind]);
  function toggleTarget(hotel: string, checked: boolean) { setTargetHotelIds((current) => checked ? [...current, hotel] : current.filter((id) => id !== hotel)); }
  async function create() { if (invalid) return; setBusy(true); setError(""); try { const document = await createWebsitePage(token, { parentId, contentKind: kind, hotelId, connections, page: { slug, menuLabel, menuVisible, menuOrder }, content: defaultContent(kind, menuLabel, hotelId, roomTypeId || null) }); onCreated(document); setOpen(false); } catch (cause) { setError(cause instanceof Error ? cause.message : "페이지를 만들지 못했습니다."); } finally { setBusy(false); } }
  return <Dialog open={open} onOpenChange={setOpen}><Button type="button" onClick={() => setOpen(true)}><Plus /> + 페이지</Button><DialogContent><DialogHeader><DialogTitle>콘텐츠 페이지 만들기</DialogTitle><DialogDescription>콘텐츠 유형과 소유 범위를 먼저 정하면, 안전한 초안이 생성됩니다.</DialogDescription></DialogHeader><div className="grid gap-4"><label className="grid gap-1 text-sm font-medium">콘텐츠 유형<Select value={kind} onValueChange={(value) => setKind(value as PageKind)}><SelectTrigger aria-label="콘텐츠 유형"><SelectValue /></SelectTrigger><SelectContent>{kinds.map((item) => <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}</SelectContent></Select></label><label className="grid gap-1 text-sm font-medium">상위 섹션<Select value={parentId} onValueChange={(value) => { const parent = sectionsForKind.find((section) => section.id === value); setParentId(value ?? ""); if (selectedKind.requiresHotel) setHotelId(parent?.hotelId ?? null); setRoomTypeId(""); }}><SelectTrigger aria-label="상위 섹션"><SelectValue placeholder="섹션을 선택하세요" /></SelectTrigger><SelectContent>{sectionsForKind.map((section) => <SelectItem key={section.id} value={section.id}>{section.label}</SelectItem>)}</SelectContent></Select></label>{selectedKind.requiresHotel && <p className="text-sm text-muted-foreground">{selectedHotel ? `${selectedHotel.name} 지점 범위로 생성합니다.` : "지점 섹션을 선택해 주세요."}</p>}{kind === "GUIDE" && <label className="grid gap-1 text-sm font-medium">소유 지점<Select value={hotelId ?? "none"} onValueChange={(value) => setHotelId(value === "none" ? null : value)}><SelectTrigger aria-label="소유 지점"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="none">체인 공통</SelectItem>{catalog.hotels.map((hotel) => <SelectItem key={hotel.id} value={hotel.id}>{hotel.name}</SelectItem>)}</SelectContent></Select></label>}{kind === "ROOM" && <label className="grid gap-1 text-sm font-medium">객실 유형<Select value={roomTypeId} onValueChange={(value) => setRoomTypeId(value ?? "")} disabled={!selectedHotel}><SelectTrigger aria-label="객실 유형"><SelectValue placeholder="객실 유형을 선택하세요" /></SelectTrigger><SelectContent>{selectedHotel?.roomTypes.map((room) => <SelectItem key={room.id} value={room.id}>{room.name}</SelectItem>)}</SelectContent></Select></label>}{kind === "PROMOTION" && <fieldset className="grid gap-2"><legend className="text-sm font-medium">대상 지점</legend>{catalog.hotels.map((hotel) => <label key={hotel.id} className="flex items-center gap-2 text-sm"><Checkbox checked={targetHotelIds.includes(hotel.id)} onCheckedChange={(checked) => toggleTarget(hotel.id, checked === true)} />{hotel.name}</label>)}{promotionMissingTargets && <p className="text-sm text-destructive">대상 지점을 하나 이상 선택해 주세요.</p>}</fieldset>}<label className="grid gap-1 text-sm font-medium">메뉴 이름<Input aria-label="메뉴 이름" value={menuLabel} onChange={(event) => setMenuLabel(event.target.value)} /></label><label className="grid gap-1 text-sm font-medium">주소 슬러그<Input aria-label="주소 슬러그" value={slug} onChange={(event) => setSlug(event.target.value)} /></label><label className="flex items-center gap-2 text-sm font-medium"><Checkbox aria-label="메뉴에 노출" checked={menuVisible} onCheckedChange={(value) => setMenuVisible(value === true)} />메뉴에 노출</label><label className="grid gap-1 text-sm font-medium">메뉴 순서<Input aria-label="메뉴 순서" type="number" min={0} value={menuOrder} onChange={(event) => setMenuOrder(Math.max(0, Number(event.target.value) || 0))} /></label>{error && <p role="alert" className="text-sm text-destructive">{error}</p>}</div><DialogFooter><Button type="button" variant="outline" onClick={() => setOpen(false)}>취소</Button><Button type="button" disabled={busy || invalid} onClick={() => void create()}>{busy ? "만드는 중" : "페이지 만들기"}</Button></DialogFooter></DialogContent></Dialog>;
}
