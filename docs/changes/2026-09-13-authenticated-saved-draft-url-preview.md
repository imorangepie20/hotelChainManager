# 인증된 저장 초안 실제 URL 미리보기

## 변경 이유와 구현 범위

저장 전 인메모리 dialog는 유지하면서 저장된 최신 초안을 실제 고객 renderer에서 임시 검토하는 경로를 추가했다. 영구 공유·댓글·PDF·초안 메뉴 트리는 범위에 포함하지 않는다.

- V24 `website_preview_grant`: 32바이트 난수의 Base64url token, SHA-256 해시만 저장, 10분 만료, 페이지·언어·초안 버전·경로 결합.
- `POST /api/staff/website/pages/{pageId}/preview-grants`: 본사 관리자·편집자·게시자만 발급. 오래된 초안 버전은 409, 없는 영어 초안·비활성 페이지는 404. 같은 발급자·페이지·언어의 이전 활성 링크만 폐기한다.
- `DELETE /api/staff/website/preview-grants/{grantId}`: 발급자 또는 본사 관리자만 폐기. 이미 만료·폐기된 grant는 성공 처리하며 변경하지 않는다.
- `GET /api/website/pages/preview?path=...&locale=...`: `X-Website-Preview`로 읽기 전용 조회. 모르는 token·잘못된 형식·경로/언어 불일치는 404, 알려진 만료·폐기·버전/경로 변경·보관은 410. 성공·미리보기 오류 응답은 `no-store`, 성공 응답은 만료 시각 header를 포함한다. 초안 본문과 연결 정보는 동일한 반복 읽기 DB snapshot으로 조회한다.
- 관리자 버튼은 저장 완료 후 발급·팝업 격리·팝업 차단 시 복사·폐기·실패 재시도를 지원한다. 고객은 fragment를 요청 전에 제거하고 탭 세션에만 저장한다. 저장소 접근이 막혀도 현재 메모리에서 검토와 종료가 가능하다.
- 홈·지점·일반 페이지의 한국어/영어 기존 renderer에 배너·만료·종료·`noindex,nofollow`를 적용했다. 잘못된 링크나 호텔 의존성 조회 실패는 공개본으로 대체하지 않는다. 예약 입력·검색·예약·결제·취소·AI와 HERO/일반/예약/지도 CTA를 차단하고 기존 예약 관리 저장값은 보존한다.

## 보안·데이터 불변 조건

- 원문 token은 DB·서버 로그·고객 URL query·`localStorage`·분석 이벤트에 기록하지 않는다. 관리자 메모리/복사 링크와 고객의 해당 탭 세션만 사용한다. 링크를 아는 사람은 만료 전 초안을 볼 수 있으므로 외부 공유에 주의한다.
- GET은 grant 정리·만료 갱신·초안 저장·발행·이력·감사 로그를 쓰지 않는다. 오래된 만료/폐기 grant의 24시간 보존 후 정리는 발급 transaction에서만 실행한다. 별도 scheduler를 추가하지 않았다.
- 기존 가격·재고·예약 확정 권한과 공개 endpoint는 변경하지 않는다. 미리보기 handler도 예약 mutation을 거부한다.

## 직접 검증

자동 검증은 로컬 테스트 DB(`hotel_chain_test`)와 Playwright API 모형을 사용했다. 테스트 token·staff·문서는 검증용이며 실제 사용자 데이터로 요청하지 않았다. 이후 개발 API를 현재 코드로 재빌드해 V24~V27 migration과 readiness를 확인하고, 실제 관리자 화면에서 임시 grant를 발급해 고객 화면까지 수동 점검했다. 페이지 저장·발행이나 예약·결제 mutation은 실행하지 않았다.

| 검사 | 결과 |
| --- | --- |
| 서버 grant/전송/page/translation 대상 테스트 | 55개 통과, failures 0·errors 0·skipped 0 (grant 9, 전송 2, page 26, translation 18) |
| 관리자 Chromium Playwright | 6개 통과, 1280px·390px, 저장 전 차단/저장 버전 발급/복사/폐기/Escape 포커스/팝업 격리/409 실패/게시자 영어 조회/없는 영어 초안 |
| 고객 Chromium Playwright | 14개 통과, 1280px·390px, fragment 제거/새로고침/일반 이동 종료/세 유형 한국어·영어/deep 영어 경로/410 비fallback/만료 화면/예약 입력·AI·CTA 차단/호텔 실패/저장소 차단 |
| 고객 순수 session 검사 | 25개 assertion 통과 |
| 고객 preview API 검사 | 전용 header·no-store·credentials omit·만료 header·410·비보안 전송 전 차단 통과 |
| 관리자 TypeScript | exit 0 |
| 관리자 대상 ESLint | exit 0, 오류 0·경고 8. 기존 파일 경고 6, 새 action의 context reset effect/ref cleanup 경고 2 |
| 고객 프로덕션 타입 검사·Vite build | exit 0 |
| 코드 검토 | 읽기 전용 검토 후 지적된 경로/의존성/CTA/입력/HTTPS/복사 문제를 수정. 재검토에서 남은 중요 항목 없음 |
| 실제 관리자 → 고객 미리보기 | 관리자 4001에서 발급 성공, 고객 4000의 저장 초안 배너·남은 시간·예약/AI 차단·종료 후 공개본 복귀 확인 |
| 고객 390×844 제목 회귀 | `word-break: keep-all` 직접 Playwright 검사와 실제 화면에서 `사이에서` 어절 분리 없음 확인 |

서버 테스트는 두 locale의 세 유형 저장 초안·공개본 분리, 반복 GET 전후 grant/page/translation/audit 동일성, 정확한 만료 경계, 폐기·버전·경로·보관 무효화, 역할·24시간 정리·HTTPS 차단을 확인한다. 고객 테스트는 예약 조회 복원·availability·mutation 요청이 미리보기에서 발생하지 않는지도 확인한다.

## 운영 설정과 미검증

- API의 `website.preview.require-https`는 기본 `true`이며 `dev` profile만 `false`다. 운영에서는 이를 끄지 않는다. 고객/관리자도 비로컬 HTTP로 token을 전송하거나 링크를 발급하지 않는다. 로컬 개발 주소 `localhost`/`127.0.0.1`/`::1`만 HTTP 예외를 둔다.
- TLS 종료 프록시가 있는 운영에서는 신뢰된 프록시만 API에 접근하도록 제한하고 HTTPS 정보가 `request.isSecure()`에 반영되는지 확인한다. 필요한 경우 `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 신뢰 경계 안에서 설정한다. 잘못된 구성은 403으로 차단되며 실제 운영 TLS/CDN/프록시는 이번에 검증하지 않았다.
- `NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN`은 운영 고객 HTTPS origin으로 설정한다. 링크/토큰 header를 proxy access log·분석·오류 수집·브라우저 trace에 추가하지 않는다. 신규 고객 테스트의 trace는 껐다.
- 전체 서버 suite·전체 브라우저 회귀·실제 운영 배포·실제 사용자 CMS 저장/발행·실제 예약/결제 mutation은 실행하지 않았다. 대상 서버 테스트는 실제 PostgreSQL/Flyway V24를 사용했고 사용자 데이터 transaction은 테스트 종료 시 rollback한다.
- V24는 별도 grant 테이블만 추가한다. 배포 rollback은 이전 애플리케이션을 먼저 복구하고 grant 테이블 정리는 별도 승인된 DB 작업으로 처리한다. 기존 초안·공개본·예약 테이블을 되돌리지 않는다.
- 작업 시작부터 존재한 사용자 변경과 이번 구현이 겹치는 파일은 처음에는 stage/commit하지 않았다. 이후 사용자가 기존 CMS·다국어 변경을 포함한 통합 커밋과 `codex/saved-draft-url-preview` 새 브랜치 푸시를 승인했다. 승인 범위의 소스·테스트·migration·문서를 함께 기록하고 임시 이미지·비밀값·빌드 결과물은 제외한다. 기존 main 브랜치는 변경하지 않는다.

## 통합 커밋 전 재검증

- 사용자 승인 범위의 통합 커밋 전 서버 grant/전송/page/translation/media 대상 74개(failures 0·errors 0·skipped 0), 관리자 미리보기/내비게이션 Playwright 8개, 고객 미리보기 Playwright 14개를 재실행해 통과했다.
- 관리자 TypeScript, 고객 session 25개 assertion, preview API 계약 검사와 고객 production build도 다시 통과했다. staged 공백 검사에 오류가 없고 비밀값 주요 패턴 검사에 일치 항목이 없었다.

## 다음 작업

실제 10분 만료 시간 경과 동작을 추가로 확인한다. 운영 반영 전에는 HTTPS/프록시 설정을 배포 환경에서 확인한다.
