"use client";

import { useState } from "react";
import { FolderPlus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { createWebsiteSection, type ContentReferenceCatalog } from "@/lib/staff-api";

type Props = {
  token: string;
  hotels: ContentReferenceCatalog["hotels"];
  disabled?: boolean;
  onCreated: () => Promise<void>;
};

export function WebsiteSectionCreateDialog({ token, hotels, disabled, onCreated }: Props) {
  const [open, setOpen] = useState(false);
  const [hotelId, setHotelId] = useState("");
  const [menuLabel, setMenuLabel] = useState("");
  const [slug, setSlug] = useState("");
  const [menuOrder, setMenuOrder] = useState("0");
  const [created, setCreated] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const order = Number(menuOrder);
  const invalid = !hotels.some((hotel) => hotel.id === hotelId) || !menuLabel.trim() || menuLabel.trim().length > 100
    || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) || slug.length > 120 || !menuOrder.trim()
    || !Number.isInteger(order) || order < 0 || order > 2147483647;

  async function createOrRefresh() {
    if (busy || (!created && invalid)) return;
    setBusy(true);
    setError("");
    let didCreate = created;
    try {
      if (!didCreate) {
        await createWebsiteSection(token, { hotelId, slug, menuLabel: menuLabel.trim(), menuOrder: order });
        didCreate = true;
        setCreated(true);
      }
      await onCreated();
      setOpen(false);
      setCreated(false);
      setHotelId("");
      setMenuLabel("");
      setSlug("");
      setMenuOrder("0");
    } catch (cause) {
      setError(didCreate
        ? "섹션 생성은 완료됐지만 목록을 불러오지 못했습니다. 목록 다시 불러오기를 눌러 주세요."
        : cause instanceof Error ? cause.message : "섹션을 만들지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(value) => { if (!busy) setOpen(value); }}>
      <DialogTrigger render={<Button type="button" variant="outline" disabled={disabled} />}>
        <FolderPlus aria-hidden="true" /> 섹션 추가
      </DialogTrigger>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto" showCloseButton={!busy}>
        <DialogHeader>
          <DialogTitle>지점 섹션 만들기</DialogTitle>
          <DialogDescription>상세 페이지를 담을 지점 섹션을 만듭니다. 고객 메뉴에는 노출되지 않으며 기존 콘텐츠를 발행하지 않습니다.</DialogDescription>
        </DialogHeader>
        <form className="grid gap-4" onSubmit={(event) => { event.preventDefault(); void createOrRefresh(); }}>
          <fieldset disabled={busy || created} className="grid min-w-0 gap-4">
            <label className="grid gap-1 text-sm font-medium">
              소유 지점
              <Select items={hotels.map((hotel) => ({ value: hotel.id, label: hotel.name }))} value={hotelId} onValueChange={(value) => setHotelId(value ?? "")} disabled={busy || created}>
                <SelectTrigger aria-label="소유 지점"><SelectValue placeholder="지점을 선택하세요" /></SelectTrigger>
                <SelectContent>{hotels.map((hotel) => <SelectItem key={hotel.id} value={hotel.id}>{hotel.name}</SelectItem>)}</SelectContent>
              </Select>
            </label>
            {hotels.length === 0 && <p role="status" className="text-sm text-muted-foreground">지점 선택 정보를 먼저 불러와 주세요.</p>}
            <label className="grid gap-1 text-sm font-medium">
              섹션 이름
              <Input aria-label="섹션 이름" value={menuLabel} maxLength={100} onChange={(event) => setMenuLabel(event.target.value)} />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              섹션 슬러그
              <Input aria-label="섹션 슬러그" value={slug} maxLength={120} placeholder="experiences" onChange={(event) => setSlug(event.target.value)} />
              <span className="text-xs font-normal text-muted-foreground">영문 소문자·숫자·하이픈만 사용하세요. 지점 경로 아래 주소로 생성됩니다.</span>
            </label>
            <label className="grid gap-1 text-sm font-medium">
              섹션 순서
              <Input aria-label="섹션 순서" type="number" min={0} max={2147483647} step={1} value={menuOrder} onChange={(event) => setMenuOrder(event.target.value)} />
            </label>
          </fieldset>
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={busy} onClick={() => setOpen(false)}>취소</Button>
            <Button type="submit" disabled={busy || (!created && invalid)}>{busy ? "처리 중…" : created ? "목록 다시 불러오기" : "섹션 만들기"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
