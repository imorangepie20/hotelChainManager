# CMS 공통 상세 레이아웃과 지점 추천 카드 정돈 점검

작성일: 2026-10-01
상태: 구현·로컬 검증·main 커밋/푸시·고객 웹 운영 배포 및 실제 공개 페이지 검증 완료. CMS 재발행 없음.

## 사용자 피드백과 범위

사용자는 파도 테이블 상세가 정돈되지 않고 중구난방이라고 지적한 뒤 같은 종류의 페이지 모두에 해당한다고 범위를 바로잡았다. 개별 페이지에 예외 CSS를 넣지 않고 공통 CONTENT_PAGE renderer와 공통 지점 추천 카드의 문제로 조사한다. 첨부 세 이미지는 같은 속초 지점 랜딩의 추천 카드 영역이며 상세 본문 캡처가 아니다.

## 확인된 원인

- 운영 Chrome에서 속초 DINING/FACILITY/EXPERIENCE 대표 상세 3개를 읽기 전용으로 점검했다. 같은 공통 컴포넌트를 사용한다.
- 해당 브라우저 viewport에서 본문 텍스트 section 너비 760px·왼쪽 241px, 갤러리 너비 약 1130px·왼쪽 55px, 운영 정보 section 너비 920px·왼쪽 161px였다. 각 블록이 따로 중앙 정렬되어 본문 시작선이 계속 이동한다. 모든 본문 section의 상하 margin은 100px이고 hero 높이는 560px였다. 한 상세의 읽기 흐름보다 개별 블록의 독립 전시가 우선된 구성이다.
- `apps/web/src/components/content-page.tsx`의 TEXT/RICH_TEXT, IMAGE_GALLERY, SPEC_TABLE/OPERATING_HOURS/NOTICE_LIST가 각자 독립 section을 생성하고 `styles.css`의 서로 다른 max-width를 받는다.
- 랜딩 `DestinationExperienceCard`의 link 분기는 inline `color: inherit`를 설정한다. 이는 `.experience-card`의 흰 글자 지정 대신 상위의 어두운 본문 글자색을 상속시켜 첨부 화면과 같은 어두운 배경 위 어두운 글자를 만든다.
- 추천 카드 category의 auto margin과 제목의 큰 top margin은 이미지와 제목 사이의 빈 공간을 크게 만든다. 연결 카드에도 기존 이미지 없는 legacy 카드용 배경·장식·여백이 그대로 적용된다.
- 이전 검증은 발행·링크·제목·이미지 로드를 확인했지만 레이아웃 정렬·시각적 위계·글자 대비의 수용 기준을 검증하지 못했다. 기능 성공을 디자인 완료로 취급하지 않는다.

## 제안한 제한된 개선 설계

- 개별 slug가 아닌 공통 상세 renderer에 전용 레이아웃 범위를 둔다. 콘텐츠 유형이 달라도 본문 기준선·제목 위계·section 간격을 공유한다.
- 상세 hero는 기존 지점 랜딩보다 낮은 높이로 분리한다. 공통 본문 container 안에서 텍스트의 읽기 폭만 제한하며 갤러리·운영 정보·공지의 바깥 정렬선은 유지한다.
- 큰 제목과 동일한 간격을 모든 블록에 반복하지 않고 소개·사진·운영 정보·공지의 위계를 구분한다. CMS 블록 순서·내용·데모 고지를 임의 삭제하거나 재발행하지 않는다.
- 이미지가 있는 추천 link는 사진+밝은 설명 면의 통일된 카드로 정리하고 category→제목→설명→자세히 보기 순서와 간격을 고정한다. 기존 링크와 이미지 없는 legacy fallback은 유지한다.
- 서버/API·CMS 스키마·예약 도메인은 변경하지 않는다. 상세 개선을 홈/지점 hero에 무차별 적용하지 않도록 selector 범위를 나눈다.

## 승인 후 검증과 완료 기준

사용자 `수정해`로 제한된 개선 설계의 구현을 승인했다. 변경 범위는 공통 상세 renderer, 지점 추천 카드, 전용 scoped stylesheet와 직접 Playwright 회귀 테스트다. 본문 section은 공통 1120px container의 같은 시작선·폭을 사용하고 읽기용 문단만 68ch로 제한한다. 일반 문구의 상세 hero는 desktop 440px 이하·mobile 340px로 분리하되 장문에는 높이를 자연 확장해 잘림을 방지하고 정보 블록 제목은 소개보다 작게 구분한다. CMS 순서·갤러리 기능·홈/지점 hero·이미지 없는 legacy 카드와 CTA·preview 동작은 유지한다. 구현 승인만 받았으므로 커밋·푸시·운영 재배포는 수행하지 않는다.

공통 상세의 세 콘텐츠 유형과 세 지점 데모 9개, 한국어/영어 표시 경로의 컴포넌트 호환을 변경 범위에 맞게 확인한다. 대표 데스크톱·모바일에서 정렬선·section 간격·본문 가독성·텍스트 대비·가로 넘침을 확인하고 갤러리·추천 링크·키보드 포커스를 직접 검증한다. 관련 회귀 테스트와 고객 타입/빌드를 우선 실행하고 승인된 구현·검증·미수행 범위를 이 기록 및 현재 개발 상태에 반영한다. 커밋·푸시·운영 배포 승인 여부는 구현 승인과 별도로 확인한다.

## 구현 결과

- `ContentPage`에 상세 전용 article과 `.cms-content-body`를 추가했다. 본문 바깥 정렬선과 폭은 1120px 공통 container로 맞추고 문단만 읽기 폭 68ch로 제한한다. section 간격은 데스크톱 48px·모바일 32px이다.
- 상세 hero의 기본 최소 높이는 데스크톱 320~440px 범위·모바일 340px로 분리하되 실제 콘텐츠 높이에 따라 확장한다. 소개 제목은 최대 36px, 갤러리·운영 정보·공지 제목은 최대 28px로 낮추고 정보 블록에 구분선을 적용했다. 새 scoped stylesheet는 기존 `styles.css` 뒤에 import한다.
- 사진이 있는 상세 연결 카드는 전체 폭 사진과 흰 설명 면으로 분리했다. category·제목·설명·링크의 순서를 유지하면서 간격을 고정했고 기존 inline color 상속 오류를 제거했다. 데스크톱 3열·900px 이하 2열·600px 이하 1열이며 이미지 없는 legacy 카드·예약 anchor는 유지한다.
- 모바일 테스트에서 기존 상세 갤러리 버튼 38px를 확인해 상세 범위 안에서 44×44px로 보완했다. 이미지 선택·이전/다음·키보드 입력과 reduced-motion은 기존 로직을 유지한다.
- 공통 컴포넌트가 영어 CMS 홈에서도 호출됨을 확인했다. 영어 홈 hero가 낮아지는 회귀를 별도 RED로 재현한 뒤 `layout='home'`으로 기존 렌더를 보존했다. 한국어 홈의 분리된 hero/after-hero 사용 경로와 지점 carousel은 변경하지 않았다.
- 서버/API·CMS 저장 문서·블록 순서·추천 snapshot·운영 발행본·예약 권한은 바꾸지 않았다. 기존 previewMode와 예약 callback도 그대로 전달한다.

## 실제 검증 결과

- 정렬 회귀와 추천 카드 밝은 면/대비 회귀를 RED→GREEN으로 실행했다. 모바일 갤러리 최소 클릭 영역과 영어 홈 보존도 실패 원인을 확인한 뒤 GREEN을 확인했다. EXPERIENCE fixture는 해당 유형에서 금지된 OPERATING_HOURS를 쓰지 않고 실제 허용 SPEC_TABLE로 구성했다.
- 최종 `npm run build` (`tsc -b && vite build`) 성공. 새 공통 상세 Playwright 11건과 기존 지점 hero/추천 Playwright 11건, 총 22건이 통과했다. 한국어·영어 UI 경로, 320px·390px 상세 정렬/overflow/갤러리 키보드, 추천 Enter 이동·focus·자식 문구별 대비, legacy 카드 및 영어 홈 보존, 320px 장문 hero의 제목·설명·CTA 경계 보존을 포함한다.
- `node --import tsx src/lib/content-page.test.ts`, `node --import tsx src/lib/customer-route.test.ts`, `git diff --check` 통과. 고객 전체 테스트·서버·관리자 회귀는 고객 레이아웃만 변경했으므로 반복 실행하지 않았다.
- 운영 공개 API에 GET만 사용해 한국어 상세 9개·지점 랜딩 3개·관련 이미지 delivery 51개를 임시 캐시에 가져왔다. 신규 코드는 로컬 production preview에서 이 실제 공개 JSON·이미지로 검증했다. 운영 사이트가 새 스타일로 배포되었다는 의미가 아니다.
- 실제 상세 9개 각각 1440px·390px, 총 18개 상세 viewport에서 제목/블록 순서 보존·정렬선·폭·hero 높이·가로 넘침 없음·갤러리 이미지 로드/이전/다음을 확인했다. 랜딩 3개 × 2개 viewport, 총 6개에서 추천 3개·흰 배경·기존 링크 순서·실제 Enter 상세 이동을 확인했다.
- 스크린샷 24개와 구조 측정 결과를 `C:/Users/jowoo/AppData/Local/Temp/hcm-layout-20261001/`에 저장했다. `verification.json`에 각 상세/랜딩 경로·viewport·측정·이미지 경로가 있다. 파도 테이블 desktop/mobile 전체·상하단 확대 및 속초 추천 desktop/mobile 대표 이미지를 직접 보고 가독성·정렬·여백·겹침을 점검했다.
- 영어 실제 발행본은 없으므로 영어 검증은 유효한 fixture를 사용하는 UI/컴포넌트 호환 검증이다. 실제 영어 발행·모든 CMS 유형의 장문 스트레스 및 preview 비활성 CTA 직접 UI 검증은 미수행이다. 이후 승인된 운영 배포와 실제 신규 스타일 상호작용 검증은 아래에 별도로 기록한다.

## 독립 리뷰와 제한된 보완

- 독립 읽기 전용 리뷰 1회에서 보안 우려는 없었지만 고정 hero 높이와 absolute hero-copy 조합의 장문 잘림이 지적돼 최초 판정은 실패였다. 이 판정을 리뷰 통과로 기록하지 않는다.
- 허용 최대 길이 제목 160자·eyebrow 100자·설명 1000자 및 CTA를 포함한 320px 회귀를 추가해 hero-copy가 hero 위로 벗어나는 RED를 확인했다. hero 배경만 absolute로 두고 copy를 normal flow grid에 넣으며 최소 높이와 padding을 사용해 내용에 따라 확장하도록 보완했다.
- 실제 공개 데이터의 READY variant는 `<picture>`를 렌더한다. 재검증에서 direct img만 배경 처리한 초기 보완의 높이 회귀를 발견해 `<picture>` 안의 img도 같은 배경 규칙을 적용했다. fixture에 실제 variant schema와 loaded-image 대기를 추가해 이 차이를 RED→GREEN으로 고정했다.
- 최종 모바일 캡처에서 기존 responsive hero-copy의 `right:6vw`가 relative 배치에도 남아 왼쪽이 -3.39px로 밀리는 것을 확인했다. hero-copy의 right도 명시적으로 초기화하고 320/390px의 좌우 경계 및 실제 공개 상세의 hero/본문 시작선 일치를 RED→GREEN으로 고정했다.
- 카드 대비 테스트도 루트뿐 아니라 category·제목·summary·CTA 각각의 계산된 색을 흰 배경과 비교하도록 보완했다. 최종 빌드·대상 Playwright 22건·실제 캐시 상세 18개/랜딩 6개 재검증을 통과했고 스크린샷 24개를 최종 빌드로 다시 생성했다.
- 같은 지적의 이해된 CSS 수정은 직접 처리했으며 독립 리뷰 재실행이나 전체 제품 회귀를 반복하지 않았다. 비필수 제안의 양 언어 모든 홈/랜딩·preview UI 조합 확장은 별도 회귀 범위로 남긴다.

## 운영 반영 승인과 범위

사용자 `커밋 푸시 배포해`로 운영 반영을 승인했다. 관련 코드·직접 회귀·문서를 명시적으로 커밋하고 main 원격 SHA를 읽어 확인한다. clean Git archive와 SHA-256 manifest를 전송하고 배포 전 DB 백업 및 서비스 ID를 기록한다. 고객 웹만 build/up하며 API·관리자·컨시어지·DB·터널의 ID가 유지되는지 확인한다. 실제 공개 상세 9개·랜딩 3개에서 데스크톱/모바일 정렬·이미지·갤러리·카드 대비와 링크 이동을 확인한 뒤 배포 결과를 기록한다. CMS 콘텐츠 재발행은 하지 않는다. 성공한 백업의 복원 검증과 전체 제품 회귀는 이번 배포 범위에 포함하지 않는다.

## 운영 배포와 읽기 전용 실제 검증 결과

- 배포 애플리케이션 커밋: `f832b86335eaa8b4ac94e5f26b184f253806eb33` (`fix(web): align CMS detail layouts and recommendation cards`). main push 후 `git ls-remote`로 동일한 원격 SHA를 읽어 확인했다. 배포 전 고객 타입/production build·직접 Playwright 22건·content-page/customer-route 단위 테스트를 다시 통과했다.
- 해당 커밋의 GitHub `customer-web-playwright` check-run은 `completed/success`다. 검증 주소: https://github.com/imorangepie20/hotelChainManager/actions/runs/36809900076/job/110202406896 . push 시 서버가 required-check 예상 상태의 우회를 표시했으므로, 이를 CI 성공으로 간주하지 않고 실제 check-run 성공을 읽은 후 배포했다. 별도 legacy status가 없어서 combined status는 pending이지만 해당 Actions check-run의 성공은 직접 확인했다.
- clean Git archive SHA-256: `72bb274d52f119fe4498131d2adf9fe16dc52c0fee1fa1304f9add57241dc51e`. archive 내부 바이트로 만든 소스 manifest 1,467개를 운영 추출 및 웹 재시작 후 확인했다. Windows 작업 트리의 CRLF 바이트와 비교하지 않는다.
- `/home/approid/apps/hotel-chain-manager`에서 기존 Compose 설정으로 고객 `web`만 build/up했다. 웹 컨테이너 `06e8df5b5fd4` 및 실행 이미지 `sha256:8e46256535d7f7b7887a2b9a3dc2acdd257a8887e5b5f4323431d1e19bffc578`를 독립 재조회해 running/healthy를 확인했다. API·관리자·컨시어지·PostgreSQL·터널 5개 서비스의 컨테이너 ID는 배포 전후 동일하다. healthy 설정이 없는 터널은 running 상태와 실제 공개 경로로 확인했다.
- 배포 전 DB logical backup `/home/approid/apps/hotel-chain-manager/backup/pre-layout-web-f832b86.dump`는 335,695 bytes·권한 0600이다. 기존 웹 이미지 `hcm-web:pre-layout-f832b86`도 보존했다. 백업 복원·실제 rollback 실행은 하지 않았으므로 복구 완료로 보고하지 않는다.
- `https://hcm.approid.team`에 실제 연결한 Playwright 검증이 통과했다. API·미디어를 cache/fixture로 대체하지 않았다. 상세 9개 × 1440px/390px = 18개에서 본문 시작선/폭·hero 시작선/높이·블록 제목/순서·가로 넘침 없음·실제 hero/갤러리 이미지 로드·이전/다음 이동을 확인했다. 랜딩 3개 × 2개 viewport = 6개에서 추천 3개·흰 설명 면·자식 문구별 대비·기존 링크 순서·Enter 상세 이동을 확인했다. JavaScript pageerror는 0건이다.
- 공개 resolve JSON 12개(상세 9개·랜딩 3개)를 배포 전후 전체 비교해 동일함을 확인했다. CMS 내용·추천 snapshot·발행 상태를 다시 쓰지 않았다. 검증 중 모든 non-GET/HEAD 요청을 전송 전에 차단했으며 예상하지 않은 애플리케이션 쓰기는 0건이다. 첫 실행은 Cloudflare `/cdn-cgi/rum` POST를 애플리케이션 쓰기와 함께 집계해 최종 감사 assertion이 실패했다. 이 telemetry만 별도로 분류하면서 차단은 유지한 후 재실행했고, telemetry 41건 차단을 기록한 최종 검증은 성공했다. 제품 코드를 추가로 변경하거나 재배포하지 않았다.
- 이미지 decode 및 갤러리의 유한 전환 종료·최종 opacity를 기다린 뒤 운영 스크린샷 24개를 저장했다. 검증 JSON과 공개 응답 baseline, 독립 서버 read-back은 `C:/Users/jowoo/AppData/Local/Temp/hcm-layout-live-20261001/`에 보관했다. 실제 모바일 파도 테이블과 속초 추천 카드 대표 이미지를 직접 확인했다.
- 이 결과의 후속 커밋은 문서만 변경하므로 운영에는 해당 두 문서와 별도 checksum만 동기화한다. 실행 애플리케이션 SHA는 위 `f832b86`을 유지한다. 영어 실제 발행·전체 제품 회귀·preview 모든 조합·백업 복원 검증은 미수행 범위로 남긴다.
