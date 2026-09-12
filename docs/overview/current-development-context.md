# 현재 개발 상태











최종 갱신: 2026-09-11










## 목표와 범위





호텔 고객 웹·예약·직원 운영·본사 관리·LangGraph 예약 도우미. 상세 범위는 project-brief.md 참조.





- 국내 지점 위치 확정: 속초·설악산·제주도. 속초와 설악산은 별도 지점이며, 지점 메타데이터와 속초 개발 재고를 생성했다.











## 현재 상태





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




- 고객 웹 CMS는 지점 랜딩의 전체 필수 스키마, 경험·오퍼 카드 순서 변경, 고객 화면 형태의 미리보기, 선택 SEO 제목·설명 편집을 지원한다. 지점 랜딩의 페이지·경로·메뉴·콘텐츠 발행 기준은 `website_page`이며 `/stays/sokcho`, `/stays/seoraksan`, `/stays/jeju`를 고객 웹 직접 경로와 공개 메뉴에 연결했다. V10은 `/brand` `SECTION` 직계 `CONTENT_PAGE` 생성·초안 저장·발행·버전 조회와 `HERO`·`TEXT`·`CTA` 구조화 편집을, V11은 루트 단일 `HOME_PAGE`를 추가했다. V12는 `website_media_asset`·`website_media_usage`, 내장 이미지 자산, 본사 전용 PNG/JPEG 업로드·사용 위치 API, 안전한 전달 URL 정규화, 공통 관리자 선택기와 고객 파서 검증을 연결했다. V13은 자산 이름·기본 alt·버전, 참조 0건만 허용하는 보관·복원, 보관 업로드 공개 차단과 활성/보관 자산을 분리한 관리자 선택기를 추가했고 공개 업로드 응답을 재검증 캐시로 바꿨다. V14는 `CONTENT_PAGE`의 공개 중단·초안 복원을 추가했다. 보관은 URL·초안·발행 이력과 초안 미디어 usage를 보존하고 현재 공개본·공개 usage만 비우며, 복원 뒤 본사가 다시 발행해야 공개된다. V15는 활성 일반 페이지의 이전 발행본 콘텐츠만 현재 초안으로 복원한다. URL·메뉴·부모·공개본은 유지하고, 서버가 lifecycle/draft/published 버전·source 페이지·구조화 콘텐츠·활성 미디어를 확인한 뒤 초안 usage와 `VERSION_RESTORED` audit을 갱신한다. V16은 `ACTIVE`와 `ARCHIVED` `CONTENT_PAGE`의 두 발행 snapshot을 읽기 전용으로 비교한다. 서버는 같은 페이지·오름차순 version·snapshot 구조만 검증하고 현재 활성 미디어를 확인하거나 초안·공개본·audit·usage를 바꾸지 않는다. 관리자는 두 버전을 선택해 경로·메뉴·SEO·HERO·TEXT·CTA를 나란히 보고 `동일`·`변경`·`추가`·`제거`를 확인한다. V17은 일반 페이지의 현재 HERO·TEXT·CTA·페이지 메타데이터를 저장·발행 없이 고객 화면과 유사한 dialog로 표시한다. 고객 parser와 같은 안전한 로컬 미디어 경로만 고객 웹 origin에서 보이며, CTA는 미리보기에서 이동하지 않는다. V18은 보관된 일반 페이지만 세 version 일치와 명시적 확인 뒤 history/audit/media usage와 함께 영구 삭제하고 자산 자체는 유지한다. 블록 이동 감지와 과거 이미지 preview는 후속 범위다. 기존 호텔 콘텐츠 endpoint는 새 페이지를 읽는 호환 경로다. 페이지 이동·깊은 트리, 자산 영구 삭제·파일 교체, 다국어·번역, 예약된 발행은 아직 구현하지 않았다.




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





1. CMS에 부모 이동·깊은 트리와 보관된 업로드 자산의 영구 삭제·파일 교체 정책을 단계적으로 구현한다.




2. 한국어·영어 콘텐츠와 번역 승인, 인증된 초안 미리보기·canonical/OG/robots, 예약된 발행과 블록 이동 감지를 구현한다.




3. 고객 화면 390×844 실제 해상도와 만료 시간 경과 검증, 자동 E2E를 구성한다.

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




- V12는 이미지 파일을 고객 CMS 문서의 자유 경로가 아니라 카탈로그 자산 UUID로 관리한다. 본사만 업로드·목록·사용 위치를 조회하고, 서버가 실제 PNG/JPEG 형식과 한도를 검사한 뒤 UUID 공개 경로를 문서에 넣는다. V13은 본사 자산명·기본 alt의 버전 기반 저장, 참조 없는 자산만 가능한 보관·복원, 보관 업로드의 공개 전달 차단과 재검증 캐시를 추가했다. V14는 일반 페이지를 보관해 공개를 중단하고 초안으로 복원하는 상태 전환을 추가했다. Docker named volume은 파일을 이미지 재생성 뒤에도 유지하며, 영구 삭제·파일 교체·변환·CDN·객체 저장소는 다음 단계다.
