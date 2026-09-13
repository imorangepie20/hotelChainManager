# 현재 개발 상태











최종 갱신: 2026-09-13

## 고객 공개 SEO 메타데이터 (2026-09-13)

- 고객 공개 페이지는 현재 origin과 정규화된 공개 경로로 canonical과 `og:url`을 만들고 발행 SEO를 title·description·Open Graph에 반영한다. 공개 화면은 `robots=index,follow`, 저장 초안 미리보기는 `noindex,nofollow`다.
- 직접 계약 테스트, production build와 실제 영문 CMS 경로의 브라우저 DOM을 확인했다. OG 대표 이미지 편집·sitemap·크롤러용 서버 렌더링은 포함하지 않았다. 상세 범위는 [변경 기록](../changes/2026-09-13-public-seo-metadata.md)을 따른다.

## 저장 초안 URL 미리보기 (2026-09-13)

- V24 grant로 저장된 최신 한국어·영어 홈/지점 랜딩/일반 페이지를 실제 고객 URL에서 10분간 검토한다. 기존 저장 전 인메모리 dialog와 별개이며 미저장 변경사항은 발급 전에 저장한다.
- 본사 관리자·편집자·게시자만 발급하고 발급자 또는 본사 관리자가 폐기한다. token 해시만 DB에 저장하고 fragment는 고객 요청 전에 제거한다. 일반 이동·종료·만료 시 검토 상태를 격리하고 예약·결제·취소·AI·콘텐츠 CTA는 실행하지 않는다.
- 개발 API를 현재 코드로 재빌드해 V24~V27 적용과 readiness를 확인했다. 실제 관리자 4001에서 발급한 저장 초안을 고객 4000에서 열어 배너·남은 시간·예약/AI 차단·종료 후 공개본 복귀를 확인했다. 고객 390×844에서 한글 제목이 어절 중간에 분리되지 않도록 회귀 검사와 CSS를 보강했다.
- 직접 테스트, 운영 HTTPS 설정과 미검증 범위: [변경 기록](../changes/2026-09-13-authenticated-saved-draft-url-preview.md). 실제 운영 배포·공개본 저장/발행·실제 예약/결제·10분 만료 시간 경과 검증은 수행하지 않았다.

## CMS 비동기 미디어 variant (2026-09-13)

- V25는 활성 업로드 PNG/JPEG에 원본 폭 이하의 640px·1280px WebP 작업을 멱등 enqueue/backfill하고, V26은 READY 메타데이터 제약을 additive하게 보강하며, V27은 새 worker claim을 attempt와 함께 식별하는 nullable `claim_token`을 추가한다. V25 파일은 최초 적용 checksum을 유지한다.
- 전용 단일 스레드의 2초 worker는 예약 만료 scheduler와 분리하며 `FOR UPDATE SKIP LOCKED`, 5분 lease, 30초·2분 backoff와 최대 3회 시도를 사용한다. 완료 파일은 claim별 고유 immutable key로 발행하고 덮어쓰지 않으며 rollback은 자기 파일만 정리하고 `STATUS_UNKNOWN`은 보존한다. 만료된 PROCESSING attempt 3은 `SKIP LOCKED LIMIT 1`로 한 건씩 terminal FAILED로 정리해 다른 작업 선점을 막지 않는다.
- 공개 READY variant는 `image/webp`와 1년 immutable cache로 전달한다. 관리자 미디어 선택기는 상태·규격·용량·실패와 폭별 재시도를 표시하고 ACTIVE 자산의 PENDING/PROCESSING·FAILED 1·2회에서 polling한다. polling/재시도 응답은 편집 메타데이터·버전을 유지하고 이전 API의 누락 variants도 정규화한다. 원본 URL·한국어/영어 페이지 JSON은 유지하며, 공개·저장 초안 미리보기 응답은 참조 중인 READY variant를 응답 전용 map으로 제공한다. 고객 HERO와 이미지 갤러리는 이를 검증한 뒤 `<picture>`·`srcset`으로 사용하고 원본을 fallback으로 둔다.
- 서버 변경 범위 92건, 관리자 variant 1건과 영문 제목 미디어 회귀 18건, 관리자 TypeScript, 고객 parser 2개와 production build가 통과했다. 별도 4082 API·`db-test` 내 일회성 DB·전용 storage에서 V25→V26→V27, health `UP`, 실제 업로드·640/1280 READY, 640 WebP HTTP 200·정확한 캐시 헤더·RIFF/WEBP·원본 checksum 보존을 확인하고 컨테이너·DB·volume을 제거했다. 개발 4080 컨테이너와 개발 DB는 변경하지 않았다.
- rolling 배포에서는 이전 worker를 먼저 drain해야 하며, 실제 운영 다중 인스턴스 장기 부하와 CDN·객체 저장소는 후속이다. 고객 연결 결과는 [반응형 미디어 변경 기록](../changes/2026-09-13-customer-responsive-media.md), worker 상세는 [비동기 variant 변경 기록](../changes/2026-09-13-async-media-variants.md)을 따른다.
- 최종 리뷰 보완 검증은 서버 99건, 관리자 미디어 E2E 27건·API 경계 7건과 TypeScript를 통과했다. 실제 PostgreSQL terminal 잠금 회귀와 인코더 차단 중 예약 만료 스케줄 실행을 포함한다. 개발 API/DB mutation과 서버 재시작은 수행하지 않았다.

## CMS 미디어 저장소 점검 (2026-09-13)

- `HQ_ADMIN`은 관리자 미디어 선택기에서 DB의 업로드 원본·READY variant 참조와 로컬 named volume 파일을 수동으로 읽기 전용 비교할 수 있다. 누락 파일, 참조 없는 일반 파일, 10분 이상 지난 참조 없는 `.tmp`를 분리하고 최근 `.tmp`와 `.trash`는 제외한다.
- 응답은 상대 storage key만 제공하며 자동 삭제·복구·정기 실행은 하지 않는다. 실제 개발 API/DB/volume 점검은 health `UP`, 누락 0, orphan 0, 오래된 임시 파일 0이었다.
- 서버 관련 46건, 관리자 Chromium 2건(390×844 포함), TypeScript와 실제 CMS 정상 표시를 확인했다. 상세 범위는 [저장소 점검 변경 기록](../changes/2026-09-13-media-storage-audit.md)을 따른다.










## 목표와 범위





호텔 고객 웹·예약·직원 운영·본사 관리·LangGraph 예약 도우미. 상세 범위는 project-brief.md 참조.





- 국내 지점 위치 확정: 속초·설악산·제주도. 속초와 설악산은 별도 지점이며, 지점 메타데이터와 속초 개발 재고를 생성했다.











## 현재 상태

- 고객 웹의 한국어·영문 본문, UI, 브랜드와 제목을 Pretendard로 통일했다. 고객 웹에 `pretendard` 1.3.9를 고정하고 로컬 가변 다이나믹 서브셋을 사용하며, 기존 Google Fonts 요청과 `DM Sans`·`Noto Sans KR`·`Playfair Display` 선언을 제거했다. 영문 CMS 1280px·390px, 한국어 헤더 1186px·1024px, 데스크톱 액션 1440px Playwright 회귀 5건과 production build를 통과했다. 실제 한국어·영문 화면에서 대상 글리프 로딩과 계산 스타일, 가로 오버플로 부재를 확인했다. 상세 기록은 [고객 웹 Pretendard 변경 기록](../changes/2026-09-12-customer-pretendard-font.md)을 따른다.

- 고객 설악산 페이지의 약 1186px 화면에서 상단 메뉴가 글자 단위로 줄바꿈되던 문제를 수정했다. 메뉴 링크와 고정 헤더 요소를 한 줄로 유지하고 간격을 가용 폭에 맞게 조정하며, 1100px 이하에서는 헤더만 메뉴 버튼으로 전환한다. 데스크톱 우측의 `예약 조회`와 현재 언어 표시는 동일한 44px 높이와 하단 기준선으로 정렬했다. 1440px 액션 정렬, 1186px 한 줄 표시, 1024px 메뉴 열기, 영문 CMS 1280px·390px 회귀와 고객 production build를 통과했다. 실제 1441px 화면에서 두 액션의 상·하단과 높이가 일치하고 가로 오버플로가 없음을 확인했다. 상세 기록은 [고객 헤더 반응형 내비게이션 변경 기록](../changes/2026-09-12-customer-header-responsive-navigation.md)을 따른다.

- 사용자의 명시적 요청으로 CMS 트리의 편집 가능한 홈·지점 3개·브랜드 3개를 실제 영어로 번역해 모두 발행했다. 공개 경로는 `/en`, `/en/stays/{sokcho|seoraksan|jeju}`, `/en/brand/{story|haneul-story|forest-gallery-demo}`다. 영문 홈 발행 뒤 고객 파서가 block ID가 있는 `HOME` 응답을 거부하는 오류를 TDD로 수정했고, 고객 영문 E2E 1280px·390px 2건, 관련 직접 테스트 4건, production build와 실제 7개 URL 렌더링을 확인했다. 기존 한국어 원문·이미지·기간·연결은 유지했으며 예약 CTA는 아직 한국어 예약 화면으로 연결됨을 표시한다. 상세 기록은 [전체 영문 발행 변경 기록](../changes/2026-09-12-all-english-publication.md)을 따른다.

- V21은 영어 번역 초안을 version에 묶어 `DRAFT → IN_REVIEW → APPROVED → PUBLISHED`로 제한하고 V22는 검토 version 제약을 보강했다. V23은 `HQ_EDITOR`와 `HQ_PUBLISHER`를 추가해 영어 작성·검토 요청과 승인·반려·발행을 분리하고 자가 승인을 서버에서 거부한다. 관리자는 역할별 action과 승인자 읽기 전용 편집기를 표시한다. 번역 서버 통합 18건, 역할 UI Chromium 1건, 관리자 TypeScript 검사, 개발 DB V23 적용과 두 개발 계정의 로그인·CMS 트리 조회를 확인했다. 전체 관리자 E2E·전체 backend suite·한국어 역할 분리는 반복하거나 확장하지 않았다. 상세 결과와 이전 binary 롤백 제약은 [영어 번역 검토·승인 변경 기록](../changes/2026-09-12-cms-translation-review.md)을 따른다.

- V20은 기존 기획의 한국어·영어 독립 초안/발행 첫 단계를 구현했다. 기존 한국어 데이터/API를 유지하고 영어 번역·메뉴·SEO·alt/캡션·연결 snapshot·version/history·media usage를 분리한다. 본사는 명시적 초안 가져오기 후 직접 번역·저장·발행하며 고객 `/en`에는 영어 발행본만 제공한다. 관련 PostgreSQL 통합 55건, 관리자/고객 직접 Chromium E2E 13건, 관리자 TypeScript·고객 직접 테스트/production build가 통과했다. 직접 lint는 오류 0·경고 10건이다. Docker API 재빌드·V20 성공·health `UP`, 기존 한국어 페이지와 usage 체크섬 보존 및 미발행 영어 404/빈 공개 목록을 읽기 전용으로 확인했다. V20 구현 검증 당시에는 실제 사용자 CMS mutation이 없었고, 이후 사용자 요청에 따른 실제 발행은 위 최신 항목을 따른다. 임의 언어별 slug·SECTION 번역·번역 승인·영어 이력 복원/비교·예약/결제/AI 전체 영어 UI는 후속이다. 상세 결과와 롤백 제약은 [다국어 변경 기록](../changes/2026-09-12-cms-locales.md)을 따른다.





- Spring Boot 4.1.1 API, PostgreSQL 16.15 개발·테스트 DB, Flyway, Docker 빌드와 readiness를 구성했다. 속초 3개 객실 유형의 90일 개발 재고·요금과 3개 지점 목록, 검색 API를 구현했다.





- 예약 임시 확보 API와 조회를 구현했다. 날짜순 행 잠금으로 전 날짜 재고를 확보하고, 서버 재계산 금액·32바이트 관리 토큰 해시·멱등 키·정책 스냅샷을 저장한다.





- [첫 예약 구현 계획](../superpowers/plans/2026-09-10-first-reservation.md)의 작업 1~3을 완료했다. 실행 환경 확인 결과는 [변경 기록](../changes/2026-09-10-execution-plan.md)에 정리했다.





- 참조 프로젝트의 Reports·Accounts·APIs·Settings·Advanced → Automations 화면 확인 완료. [화면 분석과 Archify 검증 결과](../architecture/admin-reference-analysis.md)에 적용 제안과 미검증 범위를 기록했다. 구조도는 구현 전 설계다.





- 관리자 디자인 참조: `SDTPL_ADM/` 및 `http://localhost:2455/dashboard`. 2026-09-10 사용자가 열어둔 Chrome의 로그인된 대시보드를 시각 확인했다. 밝은 회색 배경·흰색 카드·얇은 테두리·둥근 모서리·큰 지표 숫자·절제된 차트 색상을 참고한다. 제공 테마를 재사용한 첫 관리자 진입 화면을 구현했으며, 시각적 모바일 검증은 아직 수행하지 않았다.





- 루트 작업 규칙과 하네스 기반 문서 진입 구조 작성.





- 첫 예약 흐름의 검토용 설계 작성.





- 제공된 SDTPL_ADM/package.json에서 Next.js 16.2.7, React 19.2.4 및 테이블·달력·차트 의존성 확인.





- 테스트 결제·만료·취소와 고객 웹 기본 흐름을 구현했다. 결제 실패 후 재시도와 세션 예약 복구도 실제 브라우저에서 확인했다. 이후 AI 대화 패널과 직원 운영 화면 연결을 추가했으며 각 영역의 상세 검증은 관련 변경 기록을 따른다.





- 관리자 4001에서 본사·속초·설악산·제주 컨텍스트를 전환하는 정적 운영 준비 화면을 구현했다. 본사에는 지점별 준비 상태를, 지점에는 도착·출발·객실 배정·청소 업무 준비 카드를 표시한다. 이는 초기 진입 화면 구현 범위이며, 이후 추가한 로그인·서버 권한·운영 API 연결은 아래 직원 접근·운영 항목을 따른다.





- 직원 접근 기반을 추가했다. 직원 비밀번호와 세션은 해시만 저장하며 본사는 전 지점, 지점 직원은 소속 지점만 서버에서 확인한다. 개발 프로필은 환경 변수로 받은 비밀번호로 본사·3개 지점 계정을 초기화한다. 관리자 루트는 로그인 화면이며, 성공한 세션은 대시보드로 이동한다. 역할별 컨텍스트 제한과 대시보드 진입 시 서버 세션 재검증을 구현했다.





- 실제 객실·객실 배정·청소 상태 기반의 직원 운영 API를 추가했다. 배정된 청결 객실만 체크인할 수 있고, 체크아웃은 객실을 청소 필요로 전환한다. 관리자 당일 운영 화면은 도착·출발·청소 필요 목록을 표시하고 실제 객실 후보 선택·배정·체크인·체크아웃·청소 완료·노쇼 처리를 실행한다. 객실 변경·다객실 배정은 남아 있다.




- 고객 웹 CMS는 지점 랜딩의 전체 필수 스키마, 경험·오퍼 카드 순서 변경, 고객 화면 형태의 미리보기, 선택 SEO 제목·설명 편집을 지원한다. 지점 랜딩의 페이지·경로·메뉴·콘텐츠 발행 기준은 `website_page`이며 `/stays/sokcho`, `/stays/seoraksan`, `/stays/jeju`를 고객 웹 직접 경로와 공개 메뉴에 연결했다. V10~V24에서 일반·홈 페이지, 미디어 카탈로그, 페이지 수명주기·이력·미리보기·부모 이동·redirect, 한국어·영어 독립 발행과 영어 검토 역할, 저장 초안 URL 미리보기를 단계적으로 구현했다. 보관 업로드 영구 삭제, 현재 위치 파일 교체, 활성 한국어·영어 초안 사용 위치 일괄 교체, V25~V27 비동기 WebP variant와 고객 HERO·갤러리 반응형 선택도 구현했다. 기존 호텔 콘텐츠 endpoint와 원본 미디어 URL은 유지한다. CDN·객체 저장소, 언어별 임의 슬러그와 예약 발행은 후속 범위다.




- 로컬 관리자 개발 계정은 Git에서 제외한 `.env`로 초기화한다. 본사와 3개 지점 계정을 API 4080에서 실제 로그인 확인했으며, 관리자 개발 서버는 4001에서 실행 중이다. 비밀번호 원문은 문서에 기록하지 않는다.










## 검증과 환경





- 초기 예약 작업 당시 PostgreSQL 통합 테스트 11개 통과(현재 전체 테스트 집계가 아님): DB 1개, 검색 5개, 예약 5개. 예약 테스트는 마지막 객실 동시 확보, 다박 재고 롤백, 가격 변경, 멱등 재요청, 관리 토큰 접근을 검증한다. 라이브 확인은 작업 2 기준 API readiness UP, 지점 3개, 속초 검색 offer 3개다.





- 로컬 Temurin JDK `21.0.12.1` 및 Python `3.12.10` 설치 완료. java·javac·pip `25.0.1` 실행과 임시 가상환경 생성·pip 실행을 확인했다. JAVA_HOME과 Python 사용자 PATH도 등록되어 있다. 기존 IDE는 재시작해야 새 환경 변수를 반영할 수 있다.





- 확정된 개발 포트: 고객 웹 `4000`, 관리자 `4001`, API `4080`, AI `9000`. API 4080과 관리자 4001 HTTP 응답을 확인했다.




- harness에 기록된 기존 프로젝트의 도구 검증을 현재 프로젝트 검증으로 취급하지 않는다.




- V10 일반 콘텐츠 페이지의 코드 수준 검증은 서버 `WebContentIntegrationTest,WebsitePageIntegrationTest` 12건, 관리자 TypeScript 검사와 Playwright 3건, 고객 경로·문서 파서 검사와 프로덕션 빌드로 통과했다. 로컬 API `4080` readiness `UP` 후 본사 세션으로 `/brand/story`를 생성·발행(초안 v1, 발행 v2)했고, 공개 navigation·resolve의 `CONTENT_PAGE`·`hotelId: null`, 고객 웹 직접 경로의 제목·메뉴·HERO/TEXT/CTA·로컬 CTA 링크, 관리자 `4001` CMS 새로고침의 브랜드 SECTION > 브랜드 이야기 트리 항목을 확인했다.




- V11 홈페이지 CMS는 대상 서버 통합 테스트 `WebContentIntegrationTest,WebsitePageIntegrationTest` 13건(실패·오류 0), 관리자 TypeScript 검사와 Playwright 4건, 고객 콘텐츠 파서 검사와 프로덕션 빌드로 통과했다. API 컨테이너 재빌드에서 Flyway V11 적용을 확인한 뒤, 개발 API의 본사 세션으로 기존 홈 초안을 내용 변경 없이 저장·발행해 초안·발행 버전 모두 2가 되도록 검증했다. 공개 `/` resolve는 `HOME_PAGE`·`hotelId: null`을 반환하고 navigation은 루트 항목을 제외했다. 실제 in-app browser에서 고객 `4000` 홈페이지의 CMS 히어로·예약 검색/AI·본문/CTA 및 관리자 `4001` CMS 트리의 고정 홈 항목을 확인했다.




- V12 미디어 카탈로그는 대상 서버 통합 테스트 17건, 관리자 TypeScript 검사와 Playwright 7건, 고객 콘텐츠 파서 검사와 프로덕션 빌드로 통과했다. API 재빌드에서 Flyway V12·반복 데이터 migration과 `/app/media` named volume의 `hotel` 사용자 쓰기 권한을 확인했다. 공개 홈 resolve는 번들 자산 UUID와 안전한 `/images/...` 전달 경로를 반환했다. 실제 in-app browser에서 본사 CMS의 자산 1건, 초안·발행 사용 위치 10건, 미리보기·선택·페이지별 alt 입력을 확인했다. V12에 자산 삭제·보관이 없어 검증용 업로드를 공개 페이지에 저장·발행하지 않았다.




- V13 보완·V14 페이지 수명주기는 대상 서버 통합 테스트 `WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest` 22건, 관리자 TypeScript 검사와 CMS Playwright 15건을 통과했다. V14 Docker API 재빌드에서 Flyway 적용과 readiness `UP`을 확인했다. 활성 `/brand/story`는 `CONTENT_PAGE`, `hotelId: null`으로 resolve되고 공개 navigation에도 남아 있었다. 실제 본사 CMS에서는 기존 속초 랜딩만 읽기 전용으로 열었고, 사용자 페이지나 자산을 저장·보관·삭제하지 않았다.



- V15 발행본 콘텐츠 초안 복원은 `WebsitePageIntegrationTest` 12건, 관리자 TypeScript 검사, CMS Playwright 전체 16건을 통과했다. 현재 저장 초안 대체 경고를 추가한 뒤 복원 대상 E2E 1건과 TypeScript 검사를 다시 통과했다. 테스트는 과거 콘텐츠의 초안 복원, 현재 공개본 유지, 재발행 뒤 공개 반영, 초안 미디어 usage·audit, 0·현재 발행본·없는 version, 권한·보관 거부를 확인한다. Docker API 재빌드에서 Flyway V15와 readiness `UP`, 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했다. 실제 CMS 저장·복원·발행은 하지 않았다.





- V16 발행본 비교는 `WebsitePageIntegrationTest` 13건, 관리자 TypeScript 검사, 비교 대상 Chromium Playwright 1건을 통과했다. 테스트는 두 snapshot의 metadata·content·발행 시각, 현재 초안·공개본·수명주기·audit·media usage 불변성, 보관 페이지·권한·잘못된 version·다른 페이지 version·손상 snapshot 거부와 UI의 version 선택·닫기·Escape·포커스 복귀·변경 요청 부재를 확인한다. Docker API 재빌드가 성공했고 4080 health `UP`과 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했다. 실제 사용자 페이지의 저장·발행·복원·비교 요청은 보내지 않았다.

- V17 일반 페이지 초안 미리보기는 대상 Chromium Playwright 1건과 관리자 TypeScript 검사를 통과했다. 저장하지 않은 HERO와 TEXT 입력, 안전한 내장 이미지의 고객 웹 origin 경로, 닫기·Escape 뒤 포커스 복귀, staff website PUT/POST 부재를 확인했다. 관리자 전용 읽기 UI 변경이므로 API 재빌드나 실제 사용자 페이지 저장·발행은 수행하지 않았다.

- V18 일반 페이지 영구 삭제는 `WebsitePageIntegrationTest` 14건, 대상 Chromium Playwright 1건, 관리자 TypeScript 검사를 통과했다. 보관된 page의 page-scoped version/audit/media usage와 parent row 삭제, 자산 유지, 활성·오래된 version·SECTION·지점 권한 거부, 확인 dialog·DELETE body·홈 전환·트리 제거를 확인했다. Docker API 재빌드, 4080 health `UP`, 기존 `/brand/story` 공개 resolve와 유효하지 않은 세션 DELETE의 401도 확인했다. 실제 사용자 페이지에는 보관·삭제 요청을 보내지 않았다.







## 다음 작업





1. CMS 미디어를 CDN·S3 호환 객체 저장소로 이관한다. 저장소 점검 결과에 대한 자동 정리·복구는 별도 영향 확인·승인·감사 흐름으로 설계한다. 저장 초안 URL 미리보기는 운영 HTTPS 설정을 배포 환경에서 확인한다.




2. 한국어 승인·영어 이력 복원/비교, 예약된 발행과 블록 이동 감지를 구현한다. 공개 페이지의 브라우저 canonical/OG/robots는 구현했으며 OG 대표 이미지 편집·sitemap·크롤러용 서버 렌더링은 별도 단계로 남긴다.




3. 저장 초안 미리보기의 실제 10분 만료 시간 경과 검증을 추가한다.

- 2026-09-11: 사용자는 롯데리조트 속초 수준의 객실·다이닝·부대시설·프로모션 운영을 CMS 목표로 재확인했다. 구현을 추가하기 전에 콘텐츠 유형·트리·블록·도메인 연결·번역·검토·발행·SEO를 다시 정의했고, [설계 문서](../architecture/lotte-resort-level-cms-functional-design.md)와 [목표 구조도](../architecture/lotte-resort-cms-target.html)를 작성했다. 이후 구현은 이 설계를 기준으로 기능 단위 계획을 작성한 뒤 진행한다.




4. AI 도우미의 LLM·정책 임베딩과 고객 대화의 영구 E2E suite를 추가한다.










## 관련 문서





- [프로젝트 개요](project-brief.md)





- [전체 사이트 구현 설계서](../architecture/full-site-implementation-design.md)





- [의사결정](../decisions/decision-log.md)





- [첫 예약 설계](../superpowers/specs/2026-09-10-reservation-foundation-design.md)











## 최신 고객 웹 검증





- 고객 웹 빌드 성공. 실제 브라우저 검색 → 확보 → 테스트 결제 → 새로고침 조회 → 취소 완료 확인.





- 상세 결과와 미완료 항목: [고객 예약 변경 기록](../changes/2026-09-10-customer-booking.md).





- 관리자 진입 화면 구현·검증·미연결 범위: [관리자 대시보드 변경 기록](../changes/2026-09-10-admin-entry-dashboard.md).





- 직원 접근 기반 구현·검증·다음 작업: [직원 접근 변경 기록](../changes/2026-09-10-staff-access.md).





- 직원 객실 운영 API 구현·검증·다음 작업: [직원 운영 변경 기록](../changes/2026-09-10-staff-operations.md).











- 고객 웹을 리조트 경험 중심으로 업그레이드했다. 속초·설악산·제주도별 히어로·경험·오퍼·현장 안내 콘텐츠를 제공하고, 기존 실제 재고·요금·예약 흐름은 유지한다. 고객 웹 빌드와 데스크톱·390×844 화면, API readiness·호텔 목록·속초 검색 결과·설악산 콘텐츠 전환을 확인했다. 이번 화면 변경 뒤 테스트 결제·취소 전체 재검증은 남아 있다.





- LangGraph 예약 도우미를 9000 포트의 FastAPI 서비스로 추가했다. 현재는 정해진 한국어 조건 패턴으로 동작하며, Spring Boot 검색 API의 실제 객실·가격·잔여 수량만 반환한다. 고객 웹은 현재 예약 조건을 `/chat`에 보내고, AI 적용 시 Spring availability를 직접 다시 조회해 그 결과만 카드에 사용한다. 늦게 도착한 이전 검색 응답은 요청 순서 보호로 무효화한다. 실제 `/chat`·Spring 응답 비교와 headless 고객 웹의 대화→적용→재검색을 예약 생성 없이 확인했다. LLM·정책 임베딩과 영구 고객 대화 E2E suite는 후속 작업이다.











- 고객 웹 CMS의 지점 랜딩 기반은 초안·발행본·버전·감사 로그 및 본사 관리 API와 3개 지점 초기 콘텐츠 이관으로 구성했다. 관리자 편집기는 미저장 발행 방지·지점 전환 경고·전체 랜딩 스키마·카드 순서 변경·미리보기·선택 SEO 편집을 제공한다. 실제 HTTP에서 본사 저장·발행·공개 조회·감사 로그, 지점 조회·저장·발행 403, 오래된 저장·발행 409와 SEO 400/저장/발행을 확인했고, 고객 웹의 브라우저 제목·설명 메타도 확인했다. 후속 V9~V11의 실제 관리자·고객 브라우저 검증 결과는 아래 기록과 [CMS 변경 기록](../changes/2026-09-10-web-content-management.md)을 따른다.




- 고객 웹 CMS 페이지 트리 기반을 추가했다. V9은 기존 3개 지점 랜딩을 페이지·경로·메뉴·콘텐츠 발행 문서로 이관하고, 개발 시드는 새 DB에도 이를 보장한다. 관리자에는 실제 페이지 트리와 슬러그·메뉴 설정이 표시되며, 고객은 `/stays/:slug`을 공개 resolve API로 읽는다. API readiness, 공개 메뉴 3개 지점, 속초 resolve, 본사 트리와 지점 직원 403, 고객 직접 경로, 서버 통합 테스트 8건과 관리자 E2E 1건을 확인했다. 실제 로그인 UI의 변경값 발행은 남아 있다.




- V10은 일반 콘텐츠 페이지를 `SECTION`의 직계 자식으로 제한해 `CONTENT_PAGE.hotelId = null`을 보장한다. 본사만 page ID 기반 생성·조회·저장·발행·버전 조회를 할 수 있으며, 문서는 `seo` 및 단일 `HERO`로 시작하는 `HERO`·`TEXT`·`CTA` 블록만 허용한다. 고객 웹은 `/brand/story` 같은 최대 두 단계의 공개 경로에서 allowlist 렌더러를 사용한다. 실제 본사 세션으로 `/brand/story`를 생성·발행하고 공개 메뉴·resolve, 고객 직접 경로, 관리자 CMS 트리까지 확인했다.




- V11은 `HOME_PAGE`를 `website_page`의 단일 고정 루트 페이지로 추가했다. `hotelId`와 부모는 `null`, 경로는 `/`, 메뉴 노출은 `false`이고 홈 전용 본사 API만 초안 저장·발행·버전 조회를 처리한다. 일반 page publish API는 HOME_PAGE를 거부한다. 고객 홈은 CMS 히어로 뒤에 코드 소유 예약 검색·AI를 두고 CMS 본문/CTA와 기존 지점 동적 영역을 이어서 표시한다. 실제 본사 저장·발행, 공개 resolve·navigation 제외, 고객 홈과 관리자 홈 트리를 확인했다.




- V12는 이미지 파일을 고객 CMS 문서의 자유 경로가 아니라 카탈로그 자산 UUID로 관리한다. 본사만 업로드·목록·사용 위치를 조회하고, 서버가 실제 PNG/JPEG 형식과 한도를 검사한 뒤 UUID 공개 경로를 문서에 넣는다. V13은 본사 자산명·기본 alt의 버전 기반 저장, 참조 없는 자산만 가능한 보관·복원, 보관 업로드의 공개 전달 차단과 재검증 캐시를 추가했다. V14는 일반 페이지를 보관해 공개를 중단하고 초안으로 복원하는 상태 전환을 추가했다. Docker named volume은 파일을 이미지 재생성 뒤에도 유지한다. 영구 삭제·현재 위치 파일 교체·활성 초안 사용 위치 일괄 교체, V25~V27 비동기 WebP variant와 고객 HERO·갤러리 선택 연결까지 구현했다.

- V16 통합 리조트 콘텐츠 모델은 콘텐츠 종류별 블록과 초안·발행 연결(객실 유형·대상 지점·관련 페이지)을 추가했다. `WebsitePageIntegrationTest` 22건, 고객 파서·예약 의도·갤러리 검사와 production build, 관리자 Chromium E2E 4건·TypeScript 검사를 통과했다. API 재빌드 뒤 health `UP` 및 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했으며, 실제 사용자 페이지·미디어에 저장·발행·수명주기 요청을 보내지 않았다. 상세 범위와 미구현 항목은 [변경 기록](../changes/2026-09-12-unified-resort-content-model.md)을 따른다.

- V17은 미발행 콘텐츠 페이지의 부모 이동과 최대 4단계 트리를 추가했다. 본사 impact 조회 뒤에만 이동을 실행하며, 이동은 하위 초안 경로·draft version·audit만 갱신한다. 발행된 root 또는 하위 페이지는 redirect 정책 전까지 서버에서 거부한다. 서버 통합 23건, 관리자 이동 E2E 1건, TypeScript 검사, Docker API 재빌드와 health `UP`, 기존 `/brand/story` 읽기 전용 resolve를 확인했다. 실제 사용자 CMS 페이지·자산에는 이동 요청을 보내지 않았다. 상세 결과는 [부모 이동 변경 기록](../changes/2026-09-12-content-page-parent-move.md)을 따른다.

- V18은 발행 콘텐츠 페이지 이동 때 이전 공개 경로를 영구 301 redirect로 보존한다. 서버는 root·하위 published path와 snapshot 메타데이터, redirect row, audit을 transaction에서 함께 갱신하고 redirect chain·cycle을 거부한다. 공개 resolve는 현재 공개본 우선, 없을 때 한 번만 301을 반환한다. 관리자는 impact에서 301 경로를 확인하고 발행 버전을 포함해 이동한다. `WebsitePageIntegrationTest` 26건, 발행/미발행 이동 Chromium E2E 2건과 TypeScript 검사를 통과했다. Docker API 재빌드의 Flyway V18 적용과 health `UP`, 기존 `/brand/story` 공개 resolve를 확인했으며 실제 사용자 CMS 변경 요청은 보내지 않았다. 사용자 페이지 이동이 없으므로 runtime old-path 301/new-path resolve 쌍은 서버 통합 테스트에서만 확인했다. 상세 결과는 [redirect 변경 기록](../changes/2026-09-12-content-page-redirect.md)을 따른다.

- 관리자 사이드바를 현재 동작하는 호텔 운영 기능 중심으로 정리했다. 본사에는 운영 대시보드·오늘의 운영·웹사이트 CMS를, 지점 직원에게는 운영 메뉴만 표시하며 메뉴 검색에도 같은 권한 필터를 적용한다. 템플릿 샘플 메뉴는 제거했고, 아직 화면이 없는 메뉴는 추가하지 않았다. 관리자 내비게이션 Playwright 2건과 TypeScript 검사를 통과했으며 데스크톱과 390×844 모바일 사이드바를 확인했다. 상세 결과는 [관리자 내비게이션 변경 기록](../changes/2026-09-12-admin-navigation.md)을 따른다.
- 제공된 관리자 템플릿의 전체 메뉴 정의는 `SDTPL_ADM/src/lib/template-nav.ts`로 분리해 보존했다. 현재 역할별 운영 메뉴에는 연결하지 않고, 기능을 실제 구현할 때 기존 템플릿 화면·공통 컴포넌트와 함께 선택적으로 재사용한다.
- V19는 참조 없는 보관 업로드 미디어를 보관 후 30일이 지난 경우에만 영구 삭제한다. 본사 전용 DELETE API는 자산 버전·출처·상태·사용 위치·유예 기간을 확인하고, 파일을 같은 볼륨에 격리한 뒤 DB 트랜잭션 롤백 시 복구·커밋 시 제거한다. 관리자는 삭제 가능일과 복구 불가 확인을 거쳐 실행한다. Docker 환경의 V19 적용과 API `4080` 포트 헬스 `UP`, 실제 관리자 화면의 활성 자산 6건 조회를 확인했으며 사용자 자산에는 삭제 요청을 보내지 않았다. 상세 범위는 [변경 기록](../changes/2026-09-12-media-permanent-delete.md)을 따른다.
- 미디어 파일 교체는 공통 이미지 필드에서 새 PNG/JPEG 자산을 업로드하고 기존·새 이미지 확인 뒤 현재 편집 위치만 교체한다. 페이지별 alt·기존 파일·다른 위치와 페이지·발행 이력은 유지하며 초안 저장과 명시적 발행으로만 공개를 바꾼다. 기존 본사 업로드·페이지 API를 재사용해 migration·파일 덮어쓰기는 없다. 관리자 직접 E2E 13건, 미디어 서버 통합 15건, TypeScript 검사를 통과했다. 데스크톱·390×844 확인창과 로컬 API health `UP`·기존 페이지 공개 resolve를 확인했으며 실제 사용자 CMS mutation은 하지 않았다. 직접 lint는 오류 0·경고 12건이다. 상세 범위와 미검증 항목은 [파일 교체 변경 기록](../changes/2026-09-12-media-file-replacement.md)을 따른다.
- 미디어 초안 사용 위치 일괄 교체는 `HQ_ADMIN`이 새 업로드 자산과 영향 목록을 확인한 뒤 활성 한국어·영어 초안만 한 트랜잭션에서 바꾼다. 자산 UUID·전달 URL 외의 페이지별 alt·caption·블록 순서는 유지하고, 공개본·발행 usage·snapshot·보관 페이지·기존 자산과 파일은 보존한다. 영어 검토·승인은 일반 저장 규칙대로 무효화한다. `WebsiteMediaIntegrationTest` 19건과 관리자 Chromium E2E 4건, TypeScript 검사를 통과했다. 실제 사용자 CMS mutation, 전체 suite, 고객 예약 회귀는 실행하지 않았다. 상세 범위는 [변경 기록](../changes/2026-09-13-media-draft-usage-bulk-replacement.md)을 따른다.
- CMS 미디어 저장소를 provider 중립 계약으로 분리하고 `local`, `mirror`, `s3-primary` 모드를 구현했다. 모든 모드가 로컬 사본과 기존 공개 URL을 유지하며, `mirror`는 명시적 100개 단위 S3 backfill을 제공하고 `s3-primary`는 S3 오류 때 로컬 fallback을 계측한다. 격리 기반 영구 삭제의 commit·rollback도 양쪽 저장소에 적용한다. 격리된 Compose 환경에서 실제 업로드·WebP 생성, backfill, S3 우선 읽기·강제 fallback, local 롤백을 확인했고 서버 119건과 관리자 E2E 8건이 통과했다. 운영 provider·CDN 연동은 아직 검증하지 않았다. 상세 결과는 [S3 미디어 저장소 이관 기록](../changes/2026-09-13-s3-media-storage-migration.md)을 따른다.
