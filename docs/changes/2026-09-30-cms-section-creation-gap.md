# CMS 지점 섹션 생성 경로 누락 재개 점검

상태: 제한된 보완 구현·대상 검증·독립 코드 리뷰 완료. 운영 DB 변경·커밋·배포·실제 콘텐츠 발행은 수행하지 않았다.

## 중단 지점과 원인

이전 대화의 마지막 요청은 지점 상세 콘텐츠 생성부터 발행·랜딩 추천 연결까지 정상 동작하도록 구현하는 것이다. 추천 기능 배포와 실제 콘텐츠 운영 가능 여부는 별도 완료 기준이다.

- `WebsitePageService.createContentPage`는 같은 지점 소유의 SECTION 또는 CONTENT_PAGE 부모를 요구한다. HOTEL_LANDING 아래 직접 생성은 허용하지 않는다.
- `WebsitePageManagementController`의 페이지 생성 API는 CONTENT_PAGE만 생성한다. SECTION 생성 API가 없고 관리자에도 생성 UI가 없다.
- 관리자 `ContentPageCreateDialog`는 지점 콘텐츠의 부모를 지점 SECTION으로 필터링한다. 섹션이 없으면 상세 페이지 생성 자체를 시작할 수 없다.
- `WebsitePageIntegrationTest.hotelScopedSection`은 SQL로 지점 SECTION을 직접 삽입한다. 이 준비 방식은 실제 관리자에 없는 생성 경로를 감췄다.
- 현재 로컬 개발 DB를 READ ONLY 트랜잭션으로 확인했다. HOME_PAGE 1개, HOTEL_LANDING은 세 지점 각 1개, SECTION은 체인 공통 2개다. 지점 SECTION과 CONTENT_PAGE는 없다. 이전 대화에서 확인한 운영 상태와 일치하지만 이번 재개 점검에서 운영 DB를 재조회하지는 않았다.

## 제안하는 보완 범위

기존 콘텐츠 모델과 지점 범위 검증을 유지하는 제한된 생성 경로 보완으로 분류한다.

1. 본사 관리자 전용 SECTION 생성 API와 관리자 `섹션 추가` UI를 제공한다. 지점은 서버 카탈로그에서 선택하며 신규 섹션은 고객 메뉴 비노출로 만든다.
2. 생성 시 실제 지점 존재, 슬러그·표시명·순서, 기존 초안/공개 경로 및 redirect 충돌을 서버가 검증한다. 생성 감사 기록을 남기고 기존 콘텐츠나 랜딩을 수정·발행하지 않는다.
3. 섹션 생성 후 CMS 트리를 다시 읽어 상세 페이지 생성 선택기에 즉시 반영한다. 섹션이 없는 상태에서는 생성 방법을 안내한다.
4. 신규 지점에 대해 직접 SQL SECTION 준비 없이 관리 API로 섹션 생성 → 상세 생성·발행 → 랜딩 추천 저장·발행 → 공개 resolve를 검증한다. 관리자에서는 390px·키보드, 권한·오류·재조회 실패 안내를 검증한다.
5. 이후 기존 승인된 한국어 데모 상세 9개 발행을 재개한다. 운영 로그인이나 배포 권한이 필요한 시점에는 해당 상태와 승인을 별도로 확인한다. 커밋·푸시·배포를 이번 재개 요청만으로 자동 수행하지 않는다.

## 검증과 미검증

### 확정 계약과 완료 기준

- `POST /api/staff/website/sections`: 본사 관리자만 `hotelId`, `slug`, `menuLabel`, `menuOrder`를 전달한다. 실재 지점과 메타데이터를 검증한다.
- 기존 루트 SECTION 모델을 유지하며 부모는 null이다. 경로는 기존 지점 랜딩 초안 경로(없으면 서버의 초기 지점 경로) 아래 `/{slug}`로 만든다. 지점 랜딩 자체를 생성하거나 수정하지 않는다.
- 메뉴 노출은 초안·공개 모두 false로 서버가 고정한다. 문서는 비어 있고 발행 이력은 만들지 않으며 CREATED 감사 이벤트만 기록한다.
- 기존 초안·공개 경로 및 한국어 redirect source 충돌을 거부한다. slug 120자·메뉴명 100자·경로 255자·깊이 4 제한을 적용한다.
- 관리자 공통 Dialog·Select를 사용해 지점을 선택한다. 생성 성공 뒤 트리를 재조회하며, 재조회만 실패하면 생성 완료를 명시하고 POST 없이 목록 조회만 재시도한다. 기존 편집 중 페이지는 전환하지 않는다.
- 완료 기준: 생성 HTTP 회귀 RED→GREEN, 본사 전용 권한·입력·경로 충돌·비노출 검증, SECTION fixture SQL 없는 상세 생성/발행/랜딩 추천/공개 resolve, 관리자 데스크톱·390px·키보드·오류·재조회 재시도, 대상 타입/빌드 검사와 문서 갱신.

- `git status --short`: 기존 미추적 콘텐츠 발행 계획 1개가 있었으며 코드 수정은 없었다. 현재 main HEAD는 `0341253`이다.
- 로컬 `docker compose ps`: db·db-test healthy, api·concierge running.
- 로컬 DB READ ONLY 집계 성공. 최초 조회의 고정 DB 사용자명은 실제 환경과 달라 실패했고, 컨테이너의 POSTGRES_USER·POSTGRES_DB를 내부에서 사용해 재조회했다. 자격 증명은 출력하지 않았다.
- 위 git·compose·개발 DB 집계는 구현 전 재개 시점의 확인이다.

### 구현 후 실제 실행 결과

- 생성 API 회귀: 최초 `200 기대/404 실제` RED를 확인했다. 구현 후 SECTION 문서 직렬화에서 contentKind 없는 구조 항목을 콘텐츠 종류로 해석하던 문제(400)를 바로잡아 contentKind=null로 응답한다.
- redirect source 재사용과 121자 슬러그 회귀 각각 RED(충돌 없이 생성됨, DB 문자열 길이 예외)를 확인한 후 공통 경로·메타데이터 검증을 보강했다. 기존 다른 생성·저장 경로도 같은 검증을 사용한다.
- `WebsitePageIntegrationTest`: 40 tests, failures=0, errors=0, skipped=0. 실제 PostgreSQL 테스트 DB `localhost:55433`를 사용하고 테스트 transaction은 rollback한다. 지점·직원만 fixture로 준비하며 신규 bootstrap 검증의 SECTION·상세·추천은 모두 관리 HTTP handler로 생성/저장/발행한다.
- Spring MockMvc 전체 bootstrap: 랜딩 조회 → SECTION 생성 → EXPERIENCE 상세 생성 → 발행 전 공개 404 → 상세 발행 → 참조 카탈로그 재조회 → legacy 랜딩 관리 API로 추천 저장·발행 → 공개 resolve에서 추천 ID/경로/본문 및 상세 지점/유형 일치. 운영 서버에 HTTP 쓰기를 보내지는 않았다.
- 생성 권한: BRANCH_STAFF/HQ_EDITOR/HQ_PUBLISHER 403. 잘못된·누락 지점, 형식·예약 슬러그·길이·표시명·음수 순서 400. 중복 경로 409. 기존 랜딩 초안·발행 콘텐츠와 비노출 강제를 확인했다.
- 관리자 Playwright 대상 9건 통과: 신규 생성 1280px/390px·키보드, 기존 미저장 랜딩 보존, 지점 SECTION 없는 안내와 새 선택기 반영, POST 성공 후 트리 실패 시 닫고 다시 열어도 POST 1회/GET만 재시도, 서버 403/409 안내·폼 보존, 편집자/게시자 버튼 미노출, 기존 상세 생성·저장·발행 및 유형별 생성 회귀.
- 병렬 검증 중 초기 데이터 입력과 Select popup 렌더보다 키 입력이 먼저 실행되는 테스트 동기화 문제를 확인했다. 초기 제목과 option 가시성을 기다린 뒤 키보드 Home/Enter를 사용하도록 수정했다. 임의 sleep이나 재시도 횟수 증가는 사용하지 않았다.
- 관리자 `tsc --noEmit`, `next build` 성공. 변경 TS/TSX ESLint는 오류 0·기존 경고 6건이다. 최초 lint 60초 제한은 시간 초과였으며 180초 제한 재실행은 성공했다.
- Maven shell wrapper 실패 원인은 네트워크가 아니라 MSYS `/tmp`를 native curl에 전달한 경로 오류였다. native TMPDIR로 다운로드한 뒤 설치된 배포본의 `mvn.cmd`를 Python subprocess로 실행해 Maven 3.9.16/Java 21 테스트를 완료했다. 저장소 wrapper 설정은 변경하지 않았다.
- `git diff --check` 통과. 고객 웹 코드·DB migration은 변경하지 않았고 전체 시스템·고객 브라우저 회귀는 범위 밖으로 실행하지 않았다.
- 독립 읽기 전용 코드 리뷰는 `passed=true`, 중요한 논리 오류·보안 우려·수정 제안 없음으로 완료됐다. 기존 파일 diff와 신규 파일 2개의 권한·초기값·경로 검증·parameterized SQL·편집본 보존·재조회 실패 처리를 검토했다. 리뷰는 런타임 테스트 결과와 별개의 정적 판정이다.

### 다음 작업과 미검증

- 로컬 보완 구현과 검증은 완료했다. 독립 리뷰에서 추가 수정이 필요한 발견은 없었다.
- 커밋·푸시·운영 배포는 미실행이다. 운영 API 이미지도 이번 변경으로 재빌드하지 않았다.
- 운영 배포 권한·로그인 세션을 확인한 뒤 기존 승인된 한국어 상세 9개 생성/발행과 세 지점 추천 입력·공개 브라우저 클릭을 재개한다. 이번 테스트 데이터가 실제 CMS 운영 콘텐츠로 등록됐다고 보고하지 않는다.
