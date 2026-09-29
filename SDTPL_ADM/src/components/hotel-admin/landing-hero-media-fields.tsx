"use client";

import { useState } from "react";

import { MediaField } from "@/components/hotel-admin/media-field";
import { Button } from "@/components/ui/button";
import type { WebsiteMediaAsset } from "@/lib/staff-api";

type ContentRecord = Record<string, unknown>;
type HeroSlide = { assetId: string; image: string; alt: string };

const HERO_SLIDE_COUNT = 5;

function text(value: unknown) {
  return typeof value === "string" ? value : "";
}

function slide(value: unknown): HeroSlide {
  if (!value || typeof value !== "object" || Array.isArray(value)) return { assetId: "", image: "", alt: "" };
  const record = value as ContentRecord;
  return { assetId: text(record.assetId), image: text(record.image), alt: text(record.alt) };
}

export function landingHeroSlides(content: ContentRecord): HeroSlide[] {
  const stored = Array.isArray(content.heroSlides) ? content.heroSlides.map(slide).slice(0, HERO_SLIDE_COUNT) : [];
  if (stored.length === 0) {
    stored.push({ assetId: text(content.heroAssetId), image: text(content.heroImage), alt: text(content.heroAlt) });
  }
  while (stored.length < HERO_SLIDE_COUNT) stored.push({ assetId: "", image: "", alt: "" });
  return stored;
}

export function LandingHeroMediaFields({ token, content, onChange }: {
  token: string;
  content: ContentRecord;
  onChange: (content: ContentRecord) => void;
}) {
  const [selectionError, setSelectionError] = useState("");
  const slides = landingHeroSlides(content);
  const hasCarousel = Array.isArray(content.heroSlides);
  const protectedAssetIds = slides.map((item) => item.assetId).filter(Boolean);
  const selectedCount = protectedAssetIds.length;
  const uniqueCount = new Set(protectedAssetIds).size;
  const complete = selectedCount === HERO_SLIDE_COUNT && uniqueCount === HERO_SLIDE_COUNT
    && slides.every((item) => item.image && item.alt.trim());

  function applySlide(index: number, nextSlide: HeroSlide) {
    setSelectionError("");
    if (!hasCarousel && index === 0) {
      onChange({
        ...content,
        heroAssetId: nextSlide.assetId,
        heroImage: nextSlide.image,
        heroAlt: nextSlide.alt,
      });
      return;
    }
    const nextSlides = slides.map((item, itemIndex) => itemIndex === index ? nextSlide : item);
    const first = nextSlides[0];
    onChange({
      ...content,
      heroAssetId: first.assetId,
      heroImage: first.image,
      heroAlt: first.alt,
      heroSlides: nextSlides,
    });
  }

  function selectAsset(index: number, asset: WebsiteMediaAsset, alt: string) {
    if (slides.some((item, itemIndex) => itemIndex !== index && item.assetId === asset.id)) {
      setSelectionError("이미 선택한 자산입니다. 각 슬롯에는 서로 다른 이미지를 선택해 주세요.");
      return false;
    }
    applySlide(index, { assetId: asset.id, image: asset.deliveryUrl, alt });
    return true;
  }

  function moveSlide(index: number, offset: -1 | 1) {
    const targetIndex = index + offset;
    if (!hasCarousel || !complete || targetIndex < 0 || targetIndex >= HERO_SLIDE_COUNT) return;
    const nextSlides = [...slides];
    [nextSlides[index], nextSlides[targetIndex]] = [nextSlides[targetIndex], nextSlides[index]];
    const first = nextSlides[0];
    setSelectionError("");
    onChange({
      ...content,
      heroAssetId: first.assetId,
      heroImage: first.image,
      heroAlt: first.alt,
      heroSlides: nextSlides,
    });
  }

  return (
    <div className="grid gap-4 md:col-span-2">
      <div className="grid gap-4">
        {slides.map((item, index) => {
          const label = `메인 이미지 ${index + 1}`;
          return (
            <div key={index} role="region" aria-label={label} className="min-w-0">
              <MediaField
                token={token}
                assetId={item.assetId}
                deliveryUrl={item.image}
                altText={item.alt}
                label={label}
                altLabel={`${label} 대체 텍스트`}
                selectLabel={index === 0 ? "미디어 선택" : `${label} 선택`}
                protectedAssetIds={protectedAssetIds}
                onAssetSelect={(asset, alt) => selectAsset(index, asset, alt)}
                onAltTextChange={(alt) => applySlide(index, { ...item, alt })}
              />
              <div className="mt-2 flex flex-wrap justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={!hasCarousel || !complete || index === 0}
                  aria-label={`${label} 위로 이동`}
                  onClick={() => moveSlide(index, -1)}
                >위로</Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={!hasCarousel || !complete || index === HERO_SLIDE_COUNT - 1}
                  aria-label={`${label} 아래로 이동`}
                  onClick={() => moveSlide(index, 1)}
                >아래로</Button>
              </div>
            </div>
          );
        })}
      </div>
      {selectionError && <p role="alert" className="text-sm text-destructive">{selectionError}</p>}
      <p role="status" className={complete ? "text-sm text-emerald-700" : "text-sm text-muted-foreground"}>
        {complete
          ? "서로 다른 메인 이미지 5개가 순서대로 준비되었습니다."
          : `메인 이미지 ${Math.max(0, HERO_SLIDE_COUNT - uniqueCount)}개를 더 선택하고 모든 대체 텍스트를 입력해 주세요.`}
      </p>
    </div>
  );
}