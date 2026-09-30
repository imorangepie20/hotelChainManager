# 한국어 데모 상세 9개 운영 발행과 사용자 정의 SECTION 상세 경로 보완

작성일: 2026-09-30
상태: 한국어 상세 9개·추천 3지점 발행, 고객 경로 수정 커밋·푸시·운영 배포, 공개 API·고객 상세 렌더 검증 완료

## 승인·등록 범위

사용자가 커밋·푸시·운영 배포와 데모 상세 9개 발행을 승인하고 Chrome 본사 관리자 로그인을 준비했다. 연결된 HQ_ADMIN 세션을 브라우저 안에서만 사용하는 정상 관리 API로 등록했다. 자격 증명·세션 토큰을 파일이나 저장소로 내보내지 않았고 운영 DB 직접 SQL 입력은 하지 않았다.

세 지점에 메뉴 비노출 `discover` SECTION을 각각 생성했고 아래 상세를 한국어로 발행했다. 메뉴 노출은 false이며 상세의 최초 발행 버전은 모두 2다. SECTION 초기 `publishedVersion=1`은 실제 콘텐츠 발행의 증거가 아니며 빈 공개 문서와 실제 발행 응답으로 구분했다. 최초 검증의 버전 가정 오류 후 이미 생성/발행된 ID를 재조회해 재사용했고 중복 생성·재발행하지 않았다.

## 발행 상세

공개 base: `https://hcm.approid.team`.

| 지점 | 유형 | 제목 | pageId | 공개 경로 |
| --- | --- | --- | --- | --- |
| 속초 | DINING | 파도 테이블 | 550ad8e6-1a62-4d61-8be4-839b40ca88ac | /stays/sokcho/discover/pado-table |
| 속초 | FACILITY | 오션 라운지 | 245288c3-016a-4243-9ae6-7794f6fe2f13 | /stays/sokcho/discover/ocean-lounge |
| 속초 | EXPERIENCE | 해변 산책 | 83f717e8-5f9c-4b3c-a696-8ea720997b8e | /stays/sokcho/discover/beach-walk |
| 설악산 | DINING | 숲의 식탁 | 28c357b7-6d09-45f3-98d4-c2ace2008460 | /stays/seoraksan/discover/forest-table |
| 설악산 | FACILITY | 포레스트 라운지 | f58261bd-a2e1-4cd4-90f1-f4f23cafd585 | /stays/seoraksan/discover/forest-lounge |
| 설악산 | EXPERIENCE | 숲길 산책 | 2f49a2d6-f6b4-4d09-ba24-6923dcb9c262 | /stays/seoraksan/discover/forest-walk |
| 제주 | DINING | 돌담 키친 | a5a86c55-053f-4646-9740-adc88ca44f60 | /stays/jeju/discover/stonewall-kitchen |
| 제주 | FACILITY | 선셋 라운지 | e0d73193-ce3a-4d98-836e-0a39b3e0f479 | /stays/jeju/discover/sunset-lounge |
| 제주 | EXPERIENCE | 해안 산책 | d528e9eb-b4b6-4234-8709-a5fb092f4113 | /stays/jeju/discover/coastal-walk |

각 상세는 한국어 SEO, hero, 콘셉트·이용 제안, 2장 이미지 갤러리, 운영 안내 또는 제원, 중요 데모 고지로 구성했다. 기존 등록 이미지 6개를 재사용했다. 실제 메뉴·가격·예약 가능 여부·영업시간·정확한 산책 코스를 만들지 않았다. 식음 필수 OPERATING_HOURS는 실제 미운영 데모로 표시하고 시간 값 없이 closed=true로 넣었다. 영문 상세와 영문 랜딩은 발행하지 않았다.

## 추천 발행·보존 검증

- 상세 9개를 먼저 발행하고 세 지점 랜딩에 같은 지점의 DINING→FACILITY→EXPERIENCE `MANUAL_CARD` 관계를 displayOrder 0·1·2로 추가했다.
- 발행 직전 랜딩 초안과 공개본이 동일한지 검사했다. 기존 hero·carousel·소개·offers·arrival·자유 입력 experiences·메타데이터·다른 관계를 보존했다.
- 관리 API 저장/발행 후 정확한 ID를 GET 재조회하고 원본 콘텐츠와 새 관계를 비교했다. 세 랜딩 공개 `recommendedExperiences`는 각각 3개이며 순서·ID·유형·제목·상세 경로가 일치했다.
- 공개 resolve 9개·지점×유형 컬렉션 9개가 모두 200이다. 상세 ID 9개가 중복 없이 존재하며 지점별 3개·유형별 1개다. 참조 미디어 6개의 delivery HEAD도 200이다.

## 고객 경로 장애·제한된 보완

API 발행 뒤 실제 고객 랜딩 링크를 활성화하자 상세에 PAGE NOT FOUND가 표시됐다. 서버는 사용자 정의 SECTION slug `discover`를 허용하지만 `customer-route.ts`의 4단계 상세 정규식은 `rooms|dining|facilities|experiences`만 허용했다. HTML 200 및 공개 JSON 200만으로 렌더 완료라고 판단하지 않았다.

`/stays/{hotelSlug}/{sectionSlug}/{detailSlug}` 형태의 유효 slug 4단계만 CMS resolve 경로로 해석하도록 보완했다. 기존 목록 경로 우선순위, 영어 접두사, 최대 깊이, 인코딩 구분자 거절, SECTION 자체 및 지점 밖 깊은 경로 거절은 유지했다.

- 한국어 discover 회귀 테스트의 expected page/received null 실패(RED)를 확인한 뒤 정규식만 수정했다.
- customer-route·content-page 직접 테스트 GREEN, 고객 `tsc -b && vite build`, `git diff --check`가 통과했다.
- 존재하지 않는 destination-content.test.ts 실행은 모듈 없음으로 실패했다. 해당 테스트를 통과했다고 보고하지 않았고 실제 존재하는 두 대상 테스트와 빌드를 실행했다.
- 고객 웹만 clean commit archive 기반으로 재빌드·배포한다. API·관리자·DB·concierge·tunnel의 이미지나 설정을 다시 변경하지 않는다.

## 운영 배포·최종 검증

- 독립 정적 리뷰는 passed=true, security_concerns·logic_errors 없음이다. 경로 해석과 API 발행 판정을 구별하는 테스트 설명 개선 제안만 있었다.
- 고객 경로 보완 커밋 `07469c6777e3fdf3f4b0f43bbbc523c55ab93a4b`를 main에 푸시하고 원격 HEAD 동일 SHA를 확인했다. 필수 검사 실행 전 push 우회 경고가 있었으나 이후 정확한 SHA의 Customer Web Playwright CI completed/success를 직접 조회했다: https://github.com/imorangepie20/hotelChainManager/actions/runs/36700011941.
- clean commit archive SHA-256 `4a97d37d5161f0c1453501f71c94566bf50298fd437bd19654c3e2075b752baa`, 일반 파일 1,464개 manifest를 운영 추출 전후 및 별도 SSH read-back으로 검증했다. 임시 발행 스크립트·미디어 파일·자격 증명은 배포 archive에 넣지 않았다.
- 운영 고객 웹만 build 및 `up -d --no-deps --wait`로 재생성했다. 이미지 `sha256:be0372c18a505cf8358457e946cbaaab8b7f8cf63883d002fb9bde8ebd68a7f7`, 새 web 컨테이너 ID `3666fb85c79b`, running/healthy다. admin·api·concierge·postgres·tunnel은 전후 ID 동일한 것을 독립 검사했다.
- 고객 웹 재배포 전 현재 CMS 콘텐츠를 포함한 PostgreSQL 논리 백업 `/home/approid/apps/hotel-chain-manager/backup/pre-demo-web-07469c6.dump`를 만들었다. 335,695 bytes·mode 600·non-empty 확인, restore 훈련은 하지 않았다.
- 운영 Chrome에서 세 랜딩의 실제 추천 anchor를 DOM click으로 활성화하고 9개 경로 이동·해당 제목 h1·가상 호텔 데모 고지·본문 이미지 로드를 모두 확인했다. CDP pointer/Enter 입력은 이동 효과를 확인하지 못해 성공으로 보고하지 않았으며, 마우스·키보드 입력 수단 자체의 운영 검증은 미완료다. URL 직접 이동만으로 카드 연결 완료라고 판단하지 않았다.
- 고객 웹 배포 후 공개 API를 다시 조회했다. 중복 없는 상세 9개, 지점별 3개, 유형별 1개, 랜딩 추천 3개씩, 이미지 delivery 6개 200이다. CMS 트리를 children까지 펼쳐 ID로 중복 제거한 최종 GET 감사에서 9개 PUBLISHED, 세 SECTION 비노출, 세 랜딩 원본 초안·발행 문서·메타데이터 보존을 다시 확인했다.
- 이 최종 검증 기록은 documentation-only 후속 커밋으로 푸시하고 운영 문서만 갱신한다. 실제 고객 웹 애플리케이션 커밋은 `07469c6`, API·관리자 애플리케이션 커밋은 이전 `b6e5049`로 구분한다.

## 범위 밖·다음 작업

관리 API 등록/발행과 고객 카드 링크 활성화는 검증했지만 관리자 생성 다이얼로그에 수동 입력하는 운영 UI 흐름·운영 pointer/keyboard 입력·모바일 전체 회귀는 이번 작업에서 재시험하지 않았다. 영어 상세/랜딩 발행과 백업 restore 훈련도 미수행이다. 승인된 한국어 상세 9개 발행·추천 연결에는 남은 작업이 없다.
