# 리조트 랜딩 5장 히어로·Motion 전환 구현 계획

> 실행 기준 문서: [리조트 랜딩 5장 히어로·격자 전환 설계](../specs/2026-09-30-resort-hero-carousel-design.md)

목표: Spring Boot가 검증·발행하는 정확히 5장의 랜딩 히어로 계약을 추가하고, 관리자에서 순서를 편집하며, 고객 웹에서 Motion for React 격자 전환을 제공한다.

접근: 기존 JSONB 문서의 단일 `heroAssetId`/`heroImage`/`heroAlt` 계약을 제거하지 않고 `heroSlides`를 추가한다. API가 첫 슬라이드를 기존 필드에 미러링한다. 구현은 API 계약 → 관리자 편집 → 고객 렌더링 → 운영 연결·검증 순으로 진행하며 각 단계는 실패하는 테스트를 먼저 추가한다.

기술: Java 21, Spring Boot, PostgreSQL JSONB, Next.js 관리자, React 고객 웹, TypeScript, Motion for React, Playwright, JUnit.

실행 상태(2026-09-30): Task 1~4 완료, Task 5 미수행. 독립 최종 리뷰 차단사항 수정 후 API 81개·관리자 Playwright 70개와 TypeScript·고객 Playwright 42개와 production build가 통과했다. 실제 결과는 [변경 기록](../../changes/2026-09-30-resort-hero-carousel.md)에 확정한다.

## 전역 제약

- 사용자와 기존 staged 변경을 되돌리거나 임의로 재정렬하지 않는다.
- 가격·재고·예약 확정 권한은 기존 Spring Boot 경계를 유지한다.
- `heroSlides` 신규 문서는 정확히 5개·중복 없는 활성 자산만 허용한다.
- 기존 단일 이미지 snapshot은 읽기·미리보기·발행이 계속 가능해야 한다.
- 문구·예약 UI는 이미지 전환과 분리하고 첫 이미지를 LCP 대상으로 유지한다.
- 커밋·push·배포 전 전체 diff를 독립 리뷰하고 관련 문서를 실제 결과와 동기화한다.

## Task 1: API의 5장 랜딩 미디어 계약 — 완료

파일:
- 수정: `services/api/src/main/java/team/hotelchain/webcontent/WebContentService.java`
- 수정: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaReferenceService.java`
- 테스트: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`
- 테스트: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

1. `WebsitePageIntegrationTest`에 정상 5장 저장·발행, 4장 거부, 중복 자산 거부, legacy 단일 이미지 허용 사례를 추가한다.
2. 대상 JUnit을 실행해 신규 사례가 `heroSlides` 미지원 때문에 실패하는 것을 확인한다.
3. `WebContentService`에서 배열 크기, UUID 중복, alt 길이와 필수 값을 검증한다. 첫 슬라이드는 기존 세 필드로 미러링하되 legacy 문서는 그대로 허용한다.
4. `WebsiteMediaIntegrationTest`에 5장 usage·public variants·교체 경로 사례를 추가하고 실패를 확인한다.
5. `WebsiteMediaReferenceService`가 각 `heroSlides[n]`을 정규화·usage 추적·variant 수집·교체하도록 구현한다. `heroSlides`가 있으면 미러 필드를 별도 usage로 계산하지 않는다.
6. 다음 명령으로 통과를 확인한다.
   - `services\\api\\mvnw.cmd -f services\\api\\pom.xml -Dtest=WebsitePageIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest test`

완료 기준: legacy 계약과 신규 5장 계약이 동시에 통과하며 공개 응답에 다섯 자산 variant가 포함된다.

## Task 2: 관리자 5슬롯 편집기 — 완료

파일:
- 수정: `SDTPL_ADM/src/components/hotel-admin/media-field.tsx`
- 생성: `SDTPL_ADM/src/components/hotel-admin/landing-hero-media-fields.tsx`
- 수정: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- 수정: `SDTPL_ADM/src/components/hotel-admin/website-translation-editor.tsx`
- 테스트: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

1. 관리자 E2E에 `메인 이미지 1`~`5`, 과거 문서의 1번 fallback, 다섯 선택 후 저장 payload, 중복·누락 안내, 390px와 키보드 조작 사례를 추가한다.
2. 신규 E2E를 실행해 한 칸만 렌더링되는 현재 동작으로 실패하는 것을 확인한다.
3. `MediaField`에 표시 label·alt label을 받을 수 있는 하위 호환 prop을 추가한다.
4. `LandingHeroMediaFields`를 구현한다.
   - 과거 단일 필드를 1번 슬롯으로 변환한다.
   - 자산 선택·alt 변경 때 `heroSlides`를 갱신한다.
   - 1번 값을 기존 필드에 미러링한다.
   - 전체 슬롯의 자산 ID를 `protectedAssetIds`로 전달한다.
   - 누락·중복을 인라인으로 안내한다.
5. 기존 랜딩 분기의 단일 `MediaField`를 새 컴포넌트로 바꾼다.
6. 다음 명령으로 통과를 확인한다.
   - `cmd.exe /c "cd SDTPL_ADM && pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --workers=1 --max-failures=1 && pnpm.cmd exec tsc --noEmit"`

완료 기준: 관리자에서 다섯 슬롯을 순서대로 저장하며 과거 문서를 안전하게 보완할 수 있다.

## Task 3: 고객 콘텐츠 parser와 Motion 격자 전환 — 완료

파일:
- 수정: `apps/web/package.json`
- 수정: `apps/web/pnpm-lock.yaml`
- 수정: `apps/web/src/lib/destination-content.ts`
- 테스트: `apps/web/src/lib/content-page.test.ts`
- 생성: `apps/web/src/components/destination-hero-carousel.tsx`
- 생성: `apps/web/src/lib/motion-features.ts`
- 수정: `apps/web/src/App.tsx`
- 수정: `apps/web/src/styles.css`
- 생성 또는 수정: `apps/web/test/destination-hero-carousel.spec.ts`

1. `content-page.test.ts`에 정상 5장 parse, 4장·중복·잘못된 경로의 legacy fallback을 추가하고 실패를 확인한다.
2. `DestinationHeroSlide`와 `heroSlides`를 추가한다. 각 slide의 반응형 variant를 asset ID별로 해석하고 배열 전체가 유효할 때만 채택한다.
3. `pnpm --filter web add motion`으로 Motion for React를 추가해 workspace lockfile을 갱신한다.
4. parser 계약과 Playwright 행동 테스트로 순환·타일 수·일시정지·reduced-motion을 먼저 실패시킨다. 별도 순수 helper를 공개하지 않아 컴포넌트 내부 구현에 결합된 단위 테스트는 만들지 않는다.
5. `DestinationHeroCarousel`을 구현한다.
   - 5초 자동 전환, 명시적 일시정지·재생, 이전·다음·위치 버튼
   - hover·focus-within·hidden 일시정지
   - 6×4 타일의 왼쪽→오른쪽 소멸·등장
   - 전환 중 입력 잠금과 완료 후 timer 재시작
   - 다음 이미지 preload
   - `useReducedMotion`의 자동 정지와 180ms 수동 페이드
   - 한 장이면 첫 이미지 외 제어·timer 없음
6. `App.tsx`의 한국어·영어 랜딩에서 배경 이미지만 새 컴포넌트로 교체한다. 문구·shade·예약 링크는 기존 위치를 유지한다.
7. CSS에 타일·제어·focus·모바일·reduced-motion 스타일을 추가한다.
8. Playwright에 자동·수동 전환, hover/focus 일시정지, reduced-motion, 고정 문구와 모바일 제어 사례를 추가하고 통과시킨다.
9. 다음 명령으로 통과를 확인한다.
   - `pnpm.cmd exec tsx src/lib/content-page.test.ts && pnpm.cmd exec tsx src/lib/content-collection.test.ts && pnpm.cmd run build && pnpm.cmd exec playwright test --workers=4 --max-failures=1`

완료 기준: 5장 발행본은 접근 가능한 Motion 격자 전환으로 보이고 legacy 한 장은 이전과 동일하게 보인다.

## Task 4: 문서 동기화·독립 리뷰·전체 검증 — 완료

파일:
- 수정: `docs/architecture/lotte-resort-level-cms-functional-design.md`
- 수정: `docs/overview/current-development-context.md`
- 수정: `docs/changes/2026-09-30-resort-main-media-assets.md`
- 생성: `docs/changes/2026-09-30-resort-hero-carousel.md`

1. 구조 계약, Motion 선택 근거, 하위 호환, 성능·접근성 기준을 아키텍처 문서에 반영한다.
2. 구현 결과와 실제 테스트 결과를 변경 기록·현재 개발 문맥에 반영한다. 완료하지 않은 공개 연결·배포는 완료로 쓰지 않는다.
3. API·관리자·고객 대상 테스트와 `git diff --check`를 다시 실행한다.
4. 전체 diff를 fresh-context 코드 리뷰에 넘기고 Critical/Important를 테스트 우선으로 수정한다. UUID 사전 검증, malformed 공개 fallback, 고객 parser의 UUID·alt 계약과 clean-HEAD 배포 archive를 회귀로 고정한다.
5. 검증 후 관련 파일만 커밋한다. 기존 staged CMS/Toss 문서와 함께 커밋할지는 diff 경계와 리뷰 결과를 기준으로 명시한다.

완료 기준: 문서와 코드 상태가 일치하고 독립 리뷰의 차단 이슈가 없으며 모든 대상 검사가 통과한다.

## Task 5: 배포·리조트별 5장 연결·공개 검증 — 미수행

파일/도구:
- `infra/DEPLOY-ZORIN.md`
- `infra/scripts/deploy-zorin.ps1`
- `infra/scripts/verify-deployment.sh`
- 관리자 웹의 속초·설악산·제주 랜딩 편집 화면

1. 사용자에게 이미 위임받은 범위대로 검증된 변경을 명시적으로 staging해 `main`에 push하고 원격 SHA를 확인한다.
2. clean `HEAD`의 Git archive만 Zorin에 전송해 배포하고 API·관리자·고객 서비스 및 터널을 검증한다.
3. 관리자에서 등록된 15개 자산을 리조트별 순서 있는 5개 슬롯에 연결한다.
4. 지점마다 초안 저장 → 미리보기 → 검토·승인 → 발행 순서를 지키고 발행 응답을 재조회한다.
5. 공개 고객 웹에서 데스크톱·390px, 자동·수동·reduced-motion, alt·순서·crop·예약 UI를 확인한다.
6. 운영 검증 결과와 정확한 미검증 사항을 변경 기록과 현재 개발 문맥에 반영한다.

완료 기준: 세 리조트 공개 페이지가 각각 정확히 5장의 서로 다른 자산을 발행하고 애니메이션·접근성·예약 UI가 실제 배포 환경에서 확인된다.

## Review Focus

- `heroSlides`가 일부만 유효할 때 오래된 첫 이미지와 섞이지 않는가.
- 첫 슬라이드 미러링 때문에 usage count가 중복되거나 자산 교체가 누락되지 않는가.
- 전환 완료 callback 경합, 빠른 연속 입력, 탭 숨김·복귀 때 timer가 중첩되지 않는가.
- 타일마다 같은 전체 크기 `<img>`를 오프셋한 구현이 임의 화면비에서 seam 또는 왜곡을 만들지 않는가.
- reduced motion에서 24개 타일이 DOM에 생성되거나 자동 이동이 사용자 통제를 침해하지 않는가.
- 첫 이미지 LCP 우선순위와 후속 이미지 preload가 네트워크를 과도하게 경쟁시키지 않는가.
- 한국어와 영어 문서에서 alt와 제어 레이블이 올바른 언어를 쓰는가.
