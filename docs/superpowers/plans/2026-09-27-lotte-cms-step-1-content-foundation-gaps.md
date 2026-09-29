# CMS 1단계 — 콘텐츠 기반 기준선 감사 실행 계획

상태: 다음 작업으로 실행 가능. 이 감사 결과를 승인하기 전에는 product code·migration을 변경하지 않는다.
상위 설계: [롯데리조트급 CMS 기능 설계](../../architecture/lotte-resort-level-cms-functional-design.md)
목표: V9~V27과 현재 코드·테스트에 이미 구현된 CMS 기능을 재현 가능한 명령으로 고정하고, 실제 미구현 격차만 파일·테스트 단위로 분리한 후속 구현 계획의 입력을 만든다.

> 이 문서는 기능 구현 계획이 아니라 **read-only 기준선 감사 계획**이다. 감사 산출물에서 구현/부분 구현/미구현을 확정한 뒤 `writing-plans` 형식의 별도 구현 계획을 작성한다.

## 독립 경계

- 이 감사가 조사하는 범위는 `website_page` identity·트리·`content_kind`, 블록 식별자, draft/published version, navigation/page resolve read model이다.
- 상세 블록 렌더러, 예약 도메인 연결, 이동·redirect, 다국어 검토 흐름은 후속 단계가 소비할 계약만 정의하고 구현하지 않는다.
- 기존 `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`, `CONTENT_PAGE`, `/`, `/stays/*`, `/brand/*`와 `HERO/TEXT/CTA` 계약을 호환 facade로 유지한다.
- 기존 구현 기준선은 V9~V27과 [통합 리조트 콘텐츠 모델 계획](2026-09-12-unified-resort-content-model.md)이다. 감사에서 완료된 항목을 구현 범위에서 제거하고 재작성하지 않는다.

감사 결과와 후속 구현 계획이 승인된 뒤에만 1→2→3→4→5 순서로 구현한다. 다음 단계에는 `ContentKind`, page identity/lifecycle/version, stable block ID, versioned published read model만 공개 계약으로 넘기며 내부 Java class에는 의존시키지 않는다.

## 입력·산출 계약

- 입력: 현재 V8~V27 schema/checksum, 기존 page/version/audit/media usage census, 대표 공개 URL 응답과 현재 테스트 결과.
- 산출: `docs/changes/2026-09-30-lotte-cms-step-1-baseline-audit.md`의 구현/부분 구현/미구현 표, 실패 재현, 정확한 변경 파일과 테스트 명령. 2단계는 승인된 산출물만 소비한다.

## 불변식과 기존 콘텐츠 보호

- migration은 expand/backfill/validate/contract 순서로 실행한다. 현재 최신 Flyway 다음 번호를 사용하며 이미 적용된 migration을 수정하거나 번호를 다시 매기지 않는다.
- backfill은 identity·경로·초안/발행 JSON·version·audit·media UUID를 바꾸지 않는다. 분류가 모호한 기존 `CONTENT_PAGE`는 자동 추론하지 않고 호환 kind로 둔다.
- 과거 snapshot JSON은 읽기 전용이다. 누락 block ID는 응답 copy에 결정적으로 보강하고, 저장 또는 재발행으로 과거 행을 고치지 않는다.
- 저장 실패, stale version, 권한 실패, schema 검증 실패는 현재 공개본과 navigation을 바꾸지 않는다.
- 실제 사용자 페이지·자산을 계획 검증용으로 저장·발행·보관·삭제하지 않는다. 별도 fixture와 격리 DB만 사용한다.

## 감사 대상 파일

- DB: `services/api/src/main/resources/db/migration/V9__website_pages.sql`부터 `V27__add_website_media_variant_claim_token.sql`까지의 CMS migration. 적용된 파일은 수정하지 않는다.
- API: `services/api/src/main/java/team/hotelchain/webcontent/`, 특히 `ContentKind.java`, `ContentPageValidator.java`, `WebsitePageService.java`, `WebsitePageConnectionValidator.java`, 공개/관리 controller.
- API 테스트: `WebsitePageIntegrationTest`, `WebContentIntegrationTest`, `WebsiteTranslationIntegrationTest`, preview/media 관련 integration test.
- 고객 웹: `apps/web/src/lib/content-page.ts`, `content-collection.ts`와 같은 이름의 test, `apps/web/src/components/content-page.tsx`, `content-collection-page.tsx`.
- 관리자: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`, `website-page-tree.tsx`, 번역·preview 컴포넌트와 `SDTPL_ADM/e2e/website-*.spec.ts`.

## Migration 감사

- [ ] 현재 스키마와 V9~V27 적용 결과를 inventory하고 nullable/additive 변경만 포함한 새 migration 필요 여부를 결정한다.
- [ ] `content_kind`, parent/depth/path 제약, snapshot version과 인덱스가 현재 migration과 schema에 있는지 매핑한다. 누락이 확인되면 후속 구현 계획에는 nullable 또는 `NOT VALID` expand만 제안한다.
- [ ] DB를 변경하지 않고 전체 행 수, kind별 수, null/충돌 수와 기존 URL checksum을 조회한다. backfill이 필요하면 실행하지 말고 dry-run SQL과 기대 행 수만 감사 산출물에 기록한다.
- [ ] 현재 snapshot의 version 식별과 과거 snapshot reader를 확인한다. 과거 snapshot JSON을 일괄 수정하는 경로가 있으면 결함으로 분류한다.
- [ ] 현재 제약과 legacy null reader의 호환성을 확인한다. contract migration은 감사 범위에서 만들거나 실행하지 않는다.
- [ ] downgrade SQL이나 destructive cleanup이 없는지 확인한다. 발견하면 데이터 보호 결함으로 기록하고 실행하지 않는다.

## API 계약 감사

| 계약 | 요청 | 성공 응답 | 실패/보호 조건 |
| --- | --- | --- | --- |
| `GET /api/website/navigation` | `locale`, 선택 지점 context | 공개 snapshot의 메뉴 필드만 | draft·내부 권한·감사 필드 비노출 |
| `GET /api/website/pages/resolve` | canonical `path`, `locale` | kind, 안전한 block, 공개 metadata | 미발행/보관 404, legacy facade 유지 |
| `GET /api/staff/website/pages` | 인증·필터 | identity/tree/status/version | HQ CMS 역할 외 403 |
| `POST/PUT /api/staff/website/pages...` | 명시적 kind, parent, draftVersion | 갱신된 draft 문서/version | stale 409, 경로·깊이·block 오류 400 |
| publish/lifecycle | draft/published/lifecycle expected version | 불변 snapshot과 audit 결과 | 한 트랜잭션 실패 시 공개본 무변경 |

표의 각 계약을 현재 controller/service/test에 매핑한다. 이미 검증된 계약은 후속 구현 범위에서 제거한다. 응답에 예약 가격·재고·관리자 코멘트·raw storage key를 넣지 않는다는 경계는 보존한다.

## 권한·정합성 감사

- [ ] 한국어 CMS의 현재 `HQ_ADMIN` 전용 경계와 영어 번역의 `HQ_EDITOR`/`HQ_PUBLISHER` 분리를 실제 controller/service/test로 표로 만든다. 1~4단계는 현재 역할을 보존하며 역할 확대는 5단계의 별도 보안 결정 전에는 하지 않는다.
- [ ] parent가 같은 scope인지, 최대 깊이 4인지, sibling slug/path가 유일한지, 자기/순환 parent가 아닌지 DB transaction 안에서 확인한다.
- [ ] 생성과 이동의 parent 허용 정책이 서로 다른 현재 동작을 inventory하고 하나의 명시적 정책으로 통일한다.
- [ ] draft/published/lifecycle version을 각각 낙관적으로 검사하고 snapshot·media usage·audit을 같은 transaction에 기록한다.
- [ ] 공개 query는 오직 PUBLISHED snapshot만 읽고 N+1 및 전체 JSON history 로드를 피하는 인덱스/실행 계획을 검토한다.

## 관리자·고객 UI 감사

- [ ] 관리자는 체인/지점 트리, kind/status/version 배지, 명확한 빈 상태와 stale 충돌 복구를 제공한다. create/save 대화상자는 키보드 focus trap·Escape·focus return을 지킨다.
- [ ] 고객 웹은 navigation과 resolve DTO를 다시 allowlist 검증하고 알 수 없는 kind/block은 안전한 오류/빈 상태로 처리한다.
- [ ] 390px에서 트리와 고객 page가 문서 폭을 넘지 않으며, draft나 관리 링크가 고객 DOM에 나타나지 않는지 확인한다.

## 실행 명령과 기대 결과

- [ ] API 기준선: `cd services/api && ./mvnw -Dtest=WebsitePageIntegrationTest,WebContentIntegrationTest,WebsiteTranslationIntegrationTest test`. 기대 결과는 실패 0이며, 실패하면 새 구현으로 가리지 않고 감사 기록에 현재 재현으로 남긴다. 이 저장소에는 Testcontainers 의존성이 없으므로 도입을 가정하지 않는다.
- [ ] 고객 parser/build: `cd apps/web && pnpm exec tsx src/lib/content-page.test.ts && pnpm exec tsx src/lib/content-collection.test.ts && pnpm run build`. 기대 결과는 두 계약 검사와 production build 종료 코드 0이다.
- [ ] 관리자 기준선: `cd SDTPL_ADM && pnpm exec playwright test e2e/website-content-editor.spec.ts e2e/website-saved-draft-preview.spec.ts && pnpm exec tsc --noEmit`. 기대 결과는 대상 Chromium 실패 0과 TypeScript 종료 코드 0이다.
- [ ] 정적 매핑: migration/Java/TypeScript/Playwright 각 항목을 `구현`, `부분 구현`, `미구현`, `결함` 중 하나로 분류하고 파일:줄과 검증 명령을 기록한다.
- [ ] DB를 읽을 수 있는 격리 환경에서 page/version/audit/media usage/relation 행 수와 대표 published JSON SHA-256을 읽기 전용으로 기록한다. 운영 사용자 행에는 write를 실행하지 않는다.

## 감사 작업 순서

1. 위 파일을 읽고 현재 구현 매트릭스를 작성한다.
2. 세 검증 명령을 실행해 현재 기준선을 기록한다.
3. 운영 또는 배포 DB는 `READ ONLY` transaction만 사용해 행 수·hash를 확인한다.
4. 실제 격차마다 변경 파일, 실패 테스트 이름, 최소 구현, 배포/롤백, 데이터 보호 조건을 적는다.
5. 격차가 확인된 경우에만 별도 상세 구현 계획을 작성하고 사용자 검토를 받는다.

완료 기준은 감사 기록에 모든 항목의 근거가 있고, product code·DB·사용자 콘텐츠 변경이 0건이며, 다음 구현 계획이 현재 기능을 재작성하지 않는다고 독립 리뷰에서 확인되는 것이다.
