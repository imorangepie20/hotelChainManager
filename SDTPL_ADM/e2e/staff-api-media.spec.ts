import { expect, test } from "@playwright/test";
import {
  archiveWebsiteMedia, getWebsiteMedia, getWebsiteMediaDraftReplacementImpact,
  restoreWebsiteMedia, retryWebsiteMediaVariant, updateWebsiteMedia, uploadWebsiteMedia,
  type WebsiteMediaAsset,
} from "../src/lib/staff-api";

const legacyAsset: Omit<WebsiteMediaAsset, "variants"> = {
  id: "legacy-media", displayName: "이전 자산", deliveryUrl: "/api/website/media/legacy-media/content",
  mimeType: "image/png", byteSize: 2048, width: 1280, height: 720,
  defaultAltText: "이전 대체 텍스트", usageCount: 0, status: "ACTIVE", version: 1,
  archivedAt: null, permanentDeleteAvailableAt: null,
};

const singleAssetCalls: Array<[string, () => Promise<WebsiteMediaAsset>]> = [
  ["업로드", () => uploadWebsiteMedia("test-token", {
    file: new File(["image"], "test.png", { type: "image/png" }), displayName: "이전 자산", defaultAltText: "이전 대체 텍스트",
  })],
  ["메타데이터", () => updateWebsiteMedia("test-token", "legacy-media", {
    displayName: "이전 자산", defaultAltText: "이전 대체 텍스트", expectedVersion: 1,
  })],
  ["보관", () => archiveWebsiteMedia("test-token", "legacy-media", { expectedVersion: 1 })],
  ["복원", () => restoreWebsiteMedia("test-token", "legacy-media", { expectedVersion: 1 })],
  ["재시도", () => retryWebsiteMediaVariant("test-token", "legacy-media", 640)],
];

for (const [name, call] of singleAssetCalls) {
  test(`미디어 API ${name} 응답의 누락 variants를 빈 배열로 정규화한다`, async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => Response.json(legacyAsset);
    try {
      expect(await call()).toEqual({ ...legacyAsset, variants: [] });
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
}

test("미디어 API 카탈로그의 누락 variants를 빈 배열로 정규화한다", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json([legacyAsset]);
  try {
    expect(await getWebsiteMedia("test-token", true)).toEqual([{ ...legacyAsset, variants: [] }]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("미디어 API 일괄 교체 영향의 두 자산을 정규화한다", async () => {
  const impact = {
    sourceAsset: legacyAsset, targetAsset: { ...legacyAsset, id: "target-media" },
    replaceableUsages: [], publishedUsageCount: 0, archivedDraftUsageCount: 0,
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json(impact);
  try {
    expect(await getWebsiteMediaDraftReplacementImpact("test-token", "legacy-media", "target-media")).toEqual({
      ...impact, sourceAsset: { ...legacyAsset, variants: [] },
      targetAsset: { ...legacyAsset, id: "target-media", variants: [] },
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
