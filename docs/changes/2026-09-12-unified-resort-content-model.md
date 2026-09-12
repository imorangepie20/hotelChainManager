# 통합 리조트 콘텐츠 모델 변경 기록

최종 갱신: 2026-09-12

## 완료한 서버 기반

- V16 `content_kind`와 상태별 연결 테이블을 생성·저장·발행 경로에 연결했다.
- `ROOM`은 같은 지점 room type 하나를 초안과 발행본에 분리 저장한다. `PROMOTION`의 지점·room type 범위도 같은 검증기를 사용한다.
- 저장은 DRAFT 연결만 바꾼다. 발행은 DRAFT 연결을 PUBLISHED로 복사하고 version snapshot에 `contentKind`와 `connections`를 남긴다.
- 보관은 PUBLISHED 연결만 비운다. 복원은 DRAFT 연결을 보존한다. 발행 이력 초안 복원과 영구 삭제도 연결 행을 같은 transaction에서 처리한다.
- 본사 전용 `GET /api/staff/website/content-reference`는 지점, 지점별 room type, 활성·공개 CONTENT_PAGE 후보만 반환한다. 가격·재고·판매 가능 상태는 반환하지 않는다.

## 변경 이유

고객 상세와 관리자 편집기가 같은 page identity를 쓰면서도 초안 연결을 공개 연결과 분리해야 한다. 가격·재고·예약 확정 권한은 계속 Spring 예약 도메인에 남긴다.

## 공개 read model과 고객 예약 intent

- 공개 resolve는 `PUBLISHED` 상태 연결만 반환한다. collection은 발행·활성 페이지의 ID, kind, path, title, summary, image, hotel slug만 반환한다.
- 고객 웹은 block allowlist, stable `blockId`, UUID 연결, 안전한 내부 media/map 경로를 다시 확인하며, 외부 경로·가격 필드·초안 전용 카드 필드는 렌더하지 않는다.
- ROOM CTA는 지점·room type만 검색 상태에 적용한다. availability 요청은 기존 날짜·투숙객·객실 조건만 보내고, 최신 응답에서만 room type을 필터한다.

## 관리자 유형 생성과 편집

- 관리자 CMS는 세션당 `content-reference` 카탈로그를 페이지 트리와 함께 읽고, 객실 유형·지점 ID를 클라이언트에 고정하지 않는다.
- 생성 dialog는 유형과 상위 SECTION 범위를 확인한다. ROOM은 선택 SECTION과 같은 room type 하나를, PROMOTION은 대상 지점 하나 이상을 요청에 포함한다. 가격·재고·판매 상태는 요청과 UI에 추가하지 않는다.
- 유형별 편집기는 ROOM/PROMOTION 연결, 전용 예약 CTA 지점 선택, 종류별 block 추가, 읽기 전용 데스크톱·390px 미리보기를 제공한다. 미리보기는 저장·발행·수명주기 요청을 보내지 않는다.

## V16 런타임 통합 검증

| 범위 | 명령 또는 경로 | 결과 |
| --- | --- | --- |
| 서버 통합 | `services/api`: `./mvnw.cmd -Dmaven.repo.local=C:\Users\jowoo\.m2\repository -Dtest=WebsitePageIntegrationTest test` | 22건 통과 |
| API 재빌드 | `docker compose up --build -d api` | 이미지 빌드 성공, 개발 DB에 V16 적용 |
| 공개 읽기 | `GET /actuator/health`, `GET /api/website/pages/resolve?path=/brand/story` | `UP`, 기존 `CONTENT_PAGE`·`BRAND`, canonical `/brand/story` 확인 |
| 고객 코드 | parser·예약 의도·갤러리 검사, `pnpm.cmd exec tsc -b`, `pnpm.cmd build` | 모두 종료 코드 0, production build 성공 |
| 관리자 코드 | Task 8 Chromium E2E와 `pnpm.cmd exec tsc --noEmit` | E2E 4건, TypeScript 통과 |
| 관리자 브라우저 | `http://127.0.0.1:4001/dashboard/website` | 기존 속초 랜딩·페이지 트리·저장 비활성 상태를 읽기 전용으로 확인 |

브라우저 유형 생성, 교차 지점 오류, 데스크톱/390px 미리보기, 미리보기의 무변경 요청은 전용 Playwright fixture에서 확인했다. 실제 사용자 페이지나 미디어에 save, publish, archive, restore, delete 요청을 보내지 않았다.

## 미구현 및 미검증 범위

- 한국어·영어 번역과 번역 승인, 검토·승인·예약 발행
- redirect, canonical/OG/robots 등 SEO 운영
- 미디어 variant, 파일 교체, 변환, CDN·객체 저장소
- 실제 사용자 CMS 데이터를 변경하는 유형 생성·저장·발행과 전 구간 브라우저 예약 결제 회귀
