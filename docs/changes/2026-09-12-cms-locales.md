# CMS 한국어·영어 독립 초안·발행

## 변경 이유와 범위

한국어·영어 지원은 초반 기획과 [롯데리조트급 CMS 설계 6.2](../architecture/lotte-resort-level-cms-functional-design.md)의 기존 요구다. 사용자의 진행 지시에 따라 독립 초안·발행 첫 단계를 구현했다. 페이지 identity·종류·지점·부모 구조는 공유하고 기존 한국어 API와 데이터는 단일 원본으로 유지한다. 영어는 V20 추가 테이블에 저장한다. [설계](../superpowers/specs/2026-09-12-cms-locales-design.md)와 [구현 계획](../superpowers/plans/2026-09-12-cms-locales.md)을 먼저 기록했다.

## 구현

- 본사 전용 `translations/en` 조회·초안 가져오기·저장·발행·이력 API를 추가했다. 조회는 version 0·빈 콘텐츠를 반환할 뿐 DB에 초안을 생성하지 않는다. 가져오기는 한국어 source draft/lifecycle version을 확인하며 번역이나 발행을 자동으로 수행하지 않는다.
- 영어 초안·공개 콘텐츠·메뉴·SEO·이미지 alt/캡션·연결 snapshot·version/history를 분리했다. 한국어 저장/발행이 영어 media usage를 제거하지 않도록 usage PK와 동기화에 locale을 추가했다. 자산 사용 위치에는 언어별 이름·주소·alt를 표시한다.
- 쓰기는 본사 권한·활성 페이지·개별 optimistic version·페이지 행 잠금·실제 미디어/콘텐츠/연결 validator를 사용한다. 관련 페이지 대상/입력 검증은 공유하되 순환과 공개 대상은 영어 그래프와 영어 발행본으로 판단한다. 홈/랜딩은 빈 연결만 허용한다.
- 영어 주소는 `/en` 접두사와 공유 구조 슬러그를 사용한다. 한국어 이동만으로 영어 현재 공개 주소를 변경하지 않는다. 영어 저장 후 재발행 시 단일 301을 만든다. 영어 목록의 지점 해석과 응답도 영어 랜딩 발행 경로를 기준으로 한다.
- 공개 navigation/resolve/collection은 `ko|en`을 받는다. 미발행 영어는 404/빈 목록이고 한국어 콘텐츠로 fallback하지 않는다. 미번역 SECTION 이름을 가져오지 않고 번역된 자식 페이지를 루트 메뉴로 표시한다.
- 페이지 전체 보관은 양 언어 공개본·공개 usage를 중단한다. 복원은 초안만 유지하므로 언어별 재발행이 필요하다. 영구 삭제는 번역·번역 이력을 cascade 삭제하되 미디어 자산은 보존한다.
- 관리자 테마와 기존 트리·대화상자·구조화 콘텐츠·미디어 필드를 재사용했다. 언어별 상태/이력, 초안 없음과 명시적 가져오기, 미저장 전환 확인, 영어 랜딩 편집, 문단·갤러리 캡션·구조화 블록 번역 필드를 추가했다. 영어 화면에서는 한국어 구조 슬러그와 복원/삭제를 편집할 수 없다.
- 영어 페이지 조회의 늦은 응답은 페이지 변경 후 폐기한다. 모바일 공통 검색 버튼은 고정 폭 검색창 대신 접근 가능한 아이콘 버튼으로 표시해 헤더 가로 넘침을 해결했다.
- 고객 `/en` 경로에서 영어 CMS 문서·메뉴·SEO·링크와 누락 안내를 렌더링한다. 갤러리 접근성 이름과 CMS 공통 안내도 locale을 따른다. 기존 한국어 예약 검색·결제·AI 경로는 변경하지 않는다.

## 검증

- TDD RED로 영어 미발행의 잘못된 한국어 응답, 초안 조회 부재, 관리자 영어 선택 부재, `/en` route 및 영어 링크 누락을 확인한 뒤 구현했다. 본문 번역 필드·모바일 overflow·영어 보관 화면 lifecycle 버튼 노출도 실패 테스트로 확인했다.
- 실제 PostgreSQL 테스트 DB `55433/hotel_chain_test`에서 `WebsiteTranslationIntegrationTest,WebsitePageIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest` 55건 통과(번역 10·페이지 26·미디어 15·기존 web content 4, 실패/오류/건너뜀 0). 영어 랜딩 저장 검증을 보강한 뒤 번역 10건을 다시 통과했다.
- 읽기 전용 별도 리뷰의 한국어 관련 그래프 간섭·한국어 slug 변경 후 영어 collection 404를 직접 RED로 재현했다. 각각 영어 그래프와 영어 발행 주소만 기준으로 수정했고 실제 영어 cycle은 계속 거부한다. 홈 임의 연결·잘못된 미디어의 무변경 거부도 확인했다.
- Chromium 직접 E2E 13건 통과: 관리자 새 영어 6건, 기존 한국어 저장/전환/보관/복원/삭제 회귀 5건, 고객 영어 2건. 관리자 새 검증은 1280px/390px 저장·발행, 원본 보존, 미저장 언어 전환과 Escape/focus, 문단·캡션·갤러리 설명, 늦은 조회 폐기, 보관 영어의 한국어 lifecycle 비노출, 랜딩 제목/alt/SEO/경험 저장을 포함한다.
- 고객 1280px/390px에서 영어 문서·SEO·`html lang=en`·locale 유지 CTA·갤러리 조작, 미발행 홈 안내와 빈 영어 객실 목록, 영어 API 요청을 확인했다. 관리자/고객 모바일 스크린샷을 시각 확인했다. 브라우저 데이터는 fixture이며 실제 사용자 CMS 변경 요청은 없다.
- 관리자 `tsc --noEmit` 통과. 변경 파일 직접 ESLint는 오류 0·경고 10건이며 기존 effect/dependency 및 일반 `img` 경고가 남는다. 새 번역 wrapper의 추가 effect 경고는 제거했다. 마지막 발견된 booking 필드 optional 타입 오류를 수정한 뒤 TypeScript를 재실행했다.
- 고객 `cms-locale`, `customer-route`, `content-page`, `content-collection` 직접 테스트와 TypeScript를 포함한 production build 통과.
- Docker API 재빌드·기동 성공, 개발 DB V20 성공, API `4080` health `UP`. 적용 전후 한국어 `website_page` 전체와 기존 usage 열의 체크섬이 일치했고 기존 20개 참조가 모두 `ko`로 유지됐다. 영어 번역 행은 0이며 생성/발행하지 않았다.
- 실제 runtime `/brand/story` resolve 200·CONTENT_PAGE, `/en/brand/story` resolve 404·WEBSITE_PAGE_NOT_FOUND, 영어 navigation/BRAND collection 200·빈 배열을 읽기 전용으로 확인했다. 실제 영어 발행본이 없으므로 발행/redirect 동작은 테스트 DB와 fixture에서 검증했다.
- Maven wrapper 대신 설치된 동일 Maven 3.9.16을 사용했다. JDK/Mockito agent 및 Playwright 색상 환경 경고가 있으나 테스트 실패는 없다.

## 호환·롤백 주의

V20은 추가만 수행하고 한국어 콘텐츠·버전·이력·redirect를 수정하지 않는다. 한국어 저장 모델의 물리적 번역 테이블 이관은 하지 않았다. 이전 바이너리는 locale 없는 usage 삭제를 수행하므로 신/구 버전 CMS 쓰기를 혼용하지 않는다. 롤백은 V20 schema validation을 지원하는 호환 빌드와 CMS 쓰기 차단을 전제로 데이터·locale usage를 보존한다. 테이블 제거·축소 migration이나 사용자 변경 되돌리기는 수행하지 않는다. 롤백 배포 훈련은 미검증이다.

## 미검증과 다음 작업

- 실제 사용자 페이지·자산에 가져오기·저장·발행·이동·보관·복원·삭제 요청을 보내지 않았다. 실제 번역 문구도 작성/발행하지 않았다.
- 전체 서버 테스트·전체 관리자 회귀·예약/결제/AI 전체 브라우저 회귀·별도 관리자 production build는 범위 밖으로 실행하지 않았다.
- 언어별 임의 슬러그·SECTION 번역·언어별 트리 상태 배지·영어 과거 이력 복원/비교·검토/번역 승인·전체 예약/결제/AI UI 영어화는 후속이다. 영어 운영 시각·휴무 및 카드 배열은 가져온 구조를 유지하며 현재 번역 필드는 문구 중심이다.
- 영어 resolve/navigation/collection의 전체 공개 번역 로딩 최적화, 인증된 초안 URL preview, canonical/OG/robots, 예약 발행은 후속 범위다. 이번 구현으로 롯데리조트급 CMS 전체가 완료된 것은 아니다.
