# 고객 공개 SEO 메타데이터

## 목표와 범위

기존 CMS의 발행 `seo.title`과 `seo.description`을 고객 브라우저의 canonical·Open Graph·robots 메타데이터에도 일관되게 반영한다. DB, CMS 편집 필드와 공개 API 응답은 변경하지 않는다.

## 구현

- 현재 origin과 정규화된 공개 pathname으로 query·fragment가 없는 절대 canonical URL을 만든다.
- title과 description을 `og:title`, `og:description`에도 사용하고 canonical URL을 `og:url`에 사용한다.
- 공개 페이지에는 `robots=index,follow`, 저장 초안 URL 미리보기에는 `robots=noindex,nofollow`를 적용한다.
- 클라이언트 경로가 바뀌면 기존 head 요소를 재사용해 값을 갱신하므로 이전 페이지 메타데이터가 남지 않는다.

## 검증

- `node --experimental-strip-types src/lib/website-metadata.test.ts`: 공개 영문 경로의 canonical·OG와 미리보기 robots 계약 통과.
- `pnpm.cmd build`: TypeScript와 Vite production build 통과.
- Chromium에서 `http://127.0.0.1:4100/en/brand/story`를 열어 실제 발행 title·description, canonical, `index,follow`, `og:title`, `og:description`, `og:url`을 확인했다.

## 미구현

- CMS의 locale별 OG 대표 이미지·제목·설명 독립 편집
- sitemap, JSON-LD와 크롤러용 SSR 또는 prerender
- 운영 배포 origin·검색 엔진의 실제 수집 결과 검증
