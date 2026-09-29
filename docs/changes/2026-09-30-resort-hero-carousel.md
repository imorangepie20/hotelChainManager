# 리조트 랜딩 5장 히어로·Motion 전환 구현

날짜: 2026-09-30
상태: **독립 최종 리뷰 차단사항 수정·전체 회귀 완료, 배포·운영 연결·발행 미수행**
설계: [리조트 랜딩 5장 히어로·격자 전환 설계](../superpowers/specs/2026-09-30-resort-hero-carousel-design.md)
계획: [리조트 랜딩 5장 히어로·Motion 전환 구현 계획](../superpowers/plans/2026-09-30-resort-hero-carousel.md)
자산: [리조트 메인 미디어 자산 15개 등록](2026-09-30-resort-main-media-assets.md)

## 구현 범위

### API

- `HOTEL_LANDING` JSON에 순서 있는 `heroSlides`를 추가했다.
- 신규 배열은 정확히 5개, 중복 없는 자산 UUID, 유효한 서버 전달 경로, 1~200자 alt를 요구한다.
- 첫 슬라이드를 기존 `heroAssetId`, `heroImage`, `heroAlt`에 서버가 미러링한다.
- `heroSlides`가 없는 과거 단일 이미지 snapshot은 이전 계약으로 계속 저장·발행한다.
- 미디어 usage는 배열의 다섯 필드 경로만 기록하고 미러 필드는 중복 usage로 기록하지 않는다.
- 공개 `mediaVariants` 수집과 자산 일괄 교체가 다섯 슬라이드를 모두 처리한다. `pages/resolve`와 legacy `/api/hotels/{id}/content` 응답 모두에 READY variant를 응답 시점에 합치고, 1번 교체 후에는 legacy 미러도 다시 정규화한다.
- 첫 슬라이드 alt를 배열과 legacy `heroAlt` 양쪽에서 같은 trim 값으로 정규화한다. 배열의 UUID 형식과 UUID 정규화 기준 중복까지 미디어 조회보다 먼저 거부한다.
- 공개 snapshot의 `heroSlides`가 불완전하거나 손상됐더라도 legacy `heroAssetId`의 READY variant를 항상 함께 수집한다. 영어 5장 저장·usage·승인·발행·공개 variant와 비첫 슬롯 교체·중복 rollback 회귀도 추가했다.

### 관리자

- 한국어 랜딩과 영어 번역 편집기에 `메인 이미지 1`~`5` 고정 슬롯을 추가했다.
- 과거 단일 이미지 문서는 1번에 복원된다. 1번만 편집하면 legacy 단일 계약을 유지하며, 2~5번을 사용하기 시작하면 5장 배열로 승격한다.
- 중복 선택은 클라이언트에서 적용하지 않고 안내하며 서버가 최종 검증한다.
- 누락 수, 현재 선택 수, 중복 상태를 인라인으로 표시한다.
- 1번의 기존 “미디어 선택” 진입점은 유지해 기존 관리자 회귀와 사용 흐름을 보존했다. 2~5번은 슬롯별 선택 이름을 사용한다.
- 빈 슬롯에는 의미 없는 “파일 교체” 버튼을 표시하지 않는다.
- 완성된 다섯 슬롯은 위로·아래로 이동 버튼으로 키보드에서도 순서를 교환하며, 1번 이동 시 legacy 미러를 함께 갱신한다.

### 고객 웹

- `motion` `^13.4.4`를 사용한다.
- 첫 이미지와 문구·음영·예약 링크는 기존 구조로 즉시 렌더링한다.
- `LazyMotion`과 `domAnimation`을 별도 동적 chunk로 분리해 Motion 전체가 초기 JS에 합쳐지지 않게 했다.
- 데스크톱은 6×4, 24개 타일이고 640px 이하는 4×3, 12개 타일이다.
- 이전 이미지가 왼쪽 열부터 `opacity`·`scaleX`로 사라지고 다음 이미지가 같은 방향으로 나타난다.
- 5초 자동 전환, 명시적 일시정지·재생, 이전·다음·위치 버튼, hover·focus·탭 숨김 일시정지, 다음 이미지 preload를 제공한다.
- `prefers-reduced-motion`에서는 자동 순환을 시작하지 않고 타일 DOM 없이 수동 탐색에 180ms 페이드만 사용한다.
- 타일 이미지는 장식으로 숨기고 확정된 현재 이미지 하나만 의미 있는 alt를 제공한다.
- preload와 outgoing·incoming 타일이 같은 검증된 WebP `srcset`·`sizes`를 사용해 첫 전환 중 별도 원본 요청을 피한다.
- 공개 배열이 5장·대소문자를 무시한 고유 UUID·안전한 전달 경로·1~200자 alt 계약을 만족하지 않으면 배열 전체를 버리고 legacy 단일 이미지로 복귀한다.
- 자동 전환은 live region으로 읽지 않고 수동 탐색만 안내한다. 고정 예약 링크의 hover·focus도 자동 전환을 멈춘다.
- CTA를 가리던 제어 영역을 우측으로 분리했고 380px 이하에서는 2행 grid를 사용한다. 320·360·390px production 렌더링에서 모든 버튼이 viewport 안에 있고 실제 포인터 영역이 44×44px 이상임을 검사한다.

## 테스트 우선 구현 근거

### API

- 신규 테스트의 최초 실패:
  - 정상 5장 테스트는 `heroSlides` 미지원 때문에 첫 슬라이드 미러 assertion에서 실패했다.
  - 4장·중복 테스트는 저장이 거부되지 않아 실패했다.
- 최종 명령:
  - `services/api/mvnw.cmd -f services/api/pom.xml -Dtest=WebsitePageIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsiteTranslationIntegrationTest test`
- 결과: **81개 통과**.

### 관리자

- 신규 Playwright의 최초 실패: `메인 이미지 1` region만 존재하지 않아 5슬롯 count가 `0`으로 실패했다.
- 최종 명령:
  - `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts e2e/website-saved-draft-preview.spec.ts --workers=4 --max-failures=1`
  - `pnpm.cmd exec tsc --noEmit`
- 결과: **Playwright 70개 통과**, TypeScript 통과.

### 고객 웹

- parser 정상 5장 테스트는 `heroSlides`가 `undefined`여서 실패했다.
- 4장 fallback 테스트는 부분 배열이 그대로 채택되어 실패했다.
- 브라우저 테스트는 캐러셀 region 부재로 실패한 뒤 구현했다.
- 최종 검증:
  - `pnpm.cmd exec tsx src/lib/content-page.test.ts`
  - `pnpm.cmd exec tsx src/lib/content-collection.test.ts`
  - `pnpm.cmd run build`
  - `pnpm.cmd exec playwright test --workers=4 --max-failures=1`
- 결과: parser·컬렉션 계약 통과, production build 통과, **고객 Playwright 42개 통과**.
- 최종 리뷰 수정 후 build 산출물: 초기 앱 JS `476.69 kB`(`145.39 kB` gzip), 동적 Motion feature chunk `39.00 kB`(`14.81 kB` gzip). 500kB chunk 경고 없음.

## 독립 최종 리뷰 수정

- API 리뷰에서 UUID 형식·정규화 중복 검증이 일부 미디어 조회 뒤에 실행되는 문제와, 모양만 완전한 손상 배열이 legacy variant fallback을 억제하는 문제를 재현해 RED→GREEN으로 수정했다.
- 고객 parser 리뷰에서 대소문자만 다른 중복 UUID와 200자 초과 alt를 허용하는 문제를 재현해 배열 전체 legacy fallback으로 고정했다.
- 모바일 hit area 지적은 최신 production build에서 320·360·390px의 모든 제어가 44×44px 이상으로 이미 렌더링돼 재현되지 않았다. 해당 최소 크기 assertion을 회귀에 추가했다.
- 배포 리뷰에서 working tree tar가 ignored 루트 `.env`와 생성 산출물을 포함하는 보안 문제를 확인했다. 배포 스크립트는 clean `HEAD`의 `git archive`만 전송하고 원격에 남은 루트 `.env`와 테스트·생성 산출물을 제거하도록 변경했다.

## 아직 완료하지 않은 범위

1. `main` 커밋·push와 clean-HEAD Zorin 배포.
2. 운영 관리자에서 속초·설악산·제주 각각의 등록 자산 5개를 순서대로 연결하는 작업.
3. 초안 저장·미리보기·검토·승인·발행과 공개 API 재조회.
4. 실제 공개 화면의 데스크톱·390px·reduced-motion·자동 전환·crop·alt·예약 UI 검증.

따라서 이 시점에는 “애니메이션 코드 구현 완료”만 주장하며 “운영 공개 완료”로 보지 않는다.

## 롤백

- 고객 회귀 시 `DestinationHeroCarousel` 적용을 기존 첫 이미지 렌더링으로 되돌리면 공개 snapshot은 그대로 유지된다.
- 신규 `heroSlides`를 제거하지 않아도 기존 첫 이미지 미러로 구 reader가 동작한다.
- 운영 연결 이후 배열만 제거하고 기존 첫 이미지 필드를 유지하면 단일 히어로로 복귀한다.
- 배포 롤백 전에 신규 5장 초안·발행본이 구 binary에서 어떻게 보이는지 확인하며, DB snapshot을 삭제하거나 임의 재발행하지 않는다.
