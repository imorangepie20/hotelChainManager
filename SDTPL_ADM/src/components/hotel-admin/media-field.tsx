"use client";

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getWebsiteMedia, type WebsiteMediaAsset } from "@/lib/staff-api";
import { MediaPickerDialog } from "@/components/hotel-admin/media-picker-dialog";

const customerWebOrigin = (process.env.NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN ?? "http://127.0.0.1:4000").replace(/\/$/, "");

function previewUrl(deliveryUrl: string) {
  if (deliveryUrl.startsWith("/images/")) return `${customerWebOrigin}${deliveryUrl}`;
  return deliveryUrl;
}

function assetDetails(asset: WebsiteMediaAsset) {
  const size = asset.byteSize < 1024 * 1024
    ? `${Math.max(1, Math.round(asset.byteSize / 1024))} KB`
    : `${(asset.byteSize / (1024 * 1024)).toFixed(1)} MB`;
  return `${asset.width} × ${asset.height} · ${size}`;
}

export function MediaField({ token, assetId, deliveryUrl, altText, protectedAssetIds = [assetId], onAssetSelect, onAltTextChange }: {
  token: string;
  assetId: string;
  deliveryUrl: string;
  altText: string;
  protectedAssetIds?: readonly string[];
  onAssetSelect: (asset: WebsiteMediaAsset, altText: string) => void;
  onAltTextChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [asset, setAsset] = useState<WebsiteMediaAsset | null>(null);

  useEffect(() => {
    let active = true;
    if (!assetId) {
      setAsset(null);
      return () => { active = false; };
    }
    void getWebsiteMedia(token)
      .then((assets) => {
        if (active) setAsset(assets.find((item) => item.id === assetId) ?? null);
      })
      .catch(() => {
        if (active) setAsset(null);
      });
    return () => { active = false; };
  }, [assetId, token]);

  return (
    <section className="grid gap-3 rounded-xl border bg-muted/20 p-3 md:col-span-2">
      <div className="grid gap-3 sm:grid-cols-[minmax(0,240px)_1fr]">
        <div className="aspect-video overflow-hidden rounded-lg bg-muted">
          {deliveryUrl ? <img src={previewUrl(deliveryUrl)} alt={altText} className="size-full object-cover" /> : <div className="grid size-full place-items-center text-sm text-muted-foreground">미디어를 선택해 주세요.</div>}
        </div>
        <div className="flex min-w-0 flex-col items-start justify-center gap-2">
          <div>
            <p className="text-sm font-medium">대표 이미지</p>
            {asset ? <><p className="mt-1 truncate text-sm text-muted-foreground">{asset.displayName}</p><p className="mt-1 text-xs text-muted-foreground">{assetDetails(asset)} · 사용 {asset.usageCount}곳</p></> : <p className="mt-1 text-xs text-muted-foreground">선택된 자산 정보를 불러오는 중이거나 카탈로그에 없습니다.</p>}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => { setReplacing(false); setOpen(true); }}>미디어 선택</Button>
            <Button type="button" variant="outline" disabled={!assetId || !deliveryUrl} onClick={() => { setReplacing(true); setOpen(true); }}>파일 교체</Button>
          </div>
        </div>
      </div>
      <label className="grid gap-1 text-sm font-medium">대표 이미지 대체 텍스트
        <Input aria-label="대표 이미지 대체 텍스트" value={altText} maxLength={200} required onChange={(event) => onAltTextChange(event.target.value)} />
      </label>
      <p className="text-xs text-muted-foreground">이 페이지에 맞는 대체 텍스트를 입력해 주세요. 자산의 기본 문구는 선택할 때만 복사됩니다.</p>
      <MediaPickerDialog
        token={token}
        open={open}
        onOpenChange={setOpen}
        initialAssetId={assetId}
        protectedAssetIds={protectedAssetIds}
        replacement={replacing ? { deliveryUrl, altText } : undefined}
        onSelect={(selected) => {
          setAsset(selected);
          onAssetSelect(selected, replacing ? altText : selected.defaultAltText);
        }}
      />
    </section>
  );
}
