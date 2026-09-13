# 고객 반응형 CMS 미디어

## 목표와 완료 기준

비동기로 생성된 READY 640px·1280px WebP variant를 공개 페이지와 저장 초안 미리보기의 고객 이미지에서 사용한다.

- 공개 응답은 활성 업로드 자산의 READY variant만 자산별로 제공한다.
- 고객 HERO와 이미지 갤러리는 안전하게 검증된 variant만 `<picture>`·`srcset`으로 렌더링하고 원본 `<img>`를 fallback으로 유지한다.
- variant가 없거나 PENDING·PROCESSING·FAILED이면 기존 원본 렌더링을 유지한다.
- 저장된 한국어·영어 콘텐츠 JSON, 원본 URL, 예약·결제·AI 동작은 변경하지 않는다.

## 구현 순서와 검증

1. 실제 PostgreSQL 기반 공개 응답 계약과 고객 parser·renderer 회귀 테스트를 먼저 실패시킨다.
2. 공개 조회 시점의 읽기 전용 variant 메타데이터와 공통 반응형 이미지 renderer를 최소 구현한다.
3. 대상 서버 테스트, 고객 parser 검사, 고객 Playwright, TypeScript·프로덕션 빌드와 실제 브라우저 요청을 확인한다.

## 구현 결과

- 공개 발행본과 인증된 저장 초안 미리보기 응답에 자산 UUID별 `mediaVariants`를 추가했다. 서버는 페이지 HERO·이미지 갤러리·지점 랜딩 HERO가 참조한 자산을 한 번에 조회하고, `ACTIVE`·`UPLOADED` 자산의 `READY` WebP 640px·1280px만 노출한다.
- `mediaVariants`는 조회 시점에 조합하는 응답 전용 필드다. 한국어·영어 초안·발행 JSON과 원본 `/api/website/media/{id}/content` URL은 수정하지 않는다.
- 고객 parser는 자산 UUID, 허용 폭, 동일 자산의 공개 variant 경로, `image/webp`, 중복 폭을 다시 검사한다. 한 항목이라도 신뢰할 수 없으면 그 variant만 버리고 원본 이미지를 유지한다.
- 공통 `ResponsiveCmsImage`는 variant가 있을 때만 `<picture>`와 WebP `srcset`을 만들고 기존 `<img>`를 fallback으로 둔다. 홈·지점·일반 페이지 HERO와 일반 페이지 이미지 갤러리의 현재 이미지·전환 이미지·썸네일에 재사용한다.

## 검증 결과

- PostgreSQL 기반 `WebsitePageIntegrationTest`, `WebsiteTranslationIntegrationTest`, `WebsitePreviewGrantIntegrationTest`, `WebsiteMediaVariantIntegrationTest` 76건이 failures/errors/skipped 0으로 통과했다. READY만 발행본·미리보기에 노출되고 PENDING은 제외되며 저장 JSON에는 응답 필드가 섞이지 않는 회귀를 포함한다.
- 고객 `content-page.test.ts`가 통과했다. 잘못된 외부 URL은 제외하고 안전한 HERO·갤러리·지점 랜딩 variant만 연결하는 경우를 확인했다.
- 고객 Chromium E2E 6건이 통과했다. 390px에서는 640w, 1280px에서는 1280w가 선택되고 갤러리 전환 뒤에도 `srcset`이 유지되며, 기존 한글 어절·내비게이션 회귀도 함께 확인했다.
- 고객 `pnpm build`가 TypeScript와 Vite production build를 완료했고 2,901개 module을 변환했다.
- 개발 API를 현재 코드로 한 번 재빌드해 4080 health `UP`을 확인했다. 실제 공개 `/stays/seoraksan`, `/stays/jeju` 응답은 각각 READY variant 자산 1건을 반환했다. 실제 고객 `/stays/seoraksan` 화면은 WebP 640w·1280w `srcset`을 갖고 데스크톱에서 1280 WebP를 `currentSrc`로 선택했다.
- Impeccable detector는 종료 코드 0이었다. 공통 컴포넌트의 JSX prop spread를 빈 이미지로 오인한 경고 2건과 변경 전부터 있던 예약 패널 테두리 경고 1건만 보고했다.

## 미검증 항목

- 실제 운영 배포, CDN·객체 저장소, 브라우저별 장기 cache 동작과 실사용 네트워크 성능은 검증하지 않았다.
- 변경 경로 밖 전체 서버 suite, 전체 관리자 E2E, 예약·결제·AI 회귀는 실행하지 않았다.
- 개발 DB의 사용자 콘텐츠에는 저장·발행 요청을 보내지 않았고 API 재빌드와 공개 조회만 수행했다.

## 제외 범위

콘텐츠 목록 카드, CDN·객체 저장소, AVIF, crop·초점, variant 일괄 재생성, 저장 문서 migration은 이번 단계에 포함하지 않는다.
