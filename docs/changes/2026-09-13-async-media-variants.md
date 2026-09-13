# CMS 비동기 미디어 variant 변경 기록

## 변경 이유와 범위

CMS에 업로드한 활성 PNG/JPEG 원본의 요청 처리를 지연시키지 않으면서 640px·1280px WebP를 생성하고, 본사 관리자가 처리 상태와 실패를 확인할 수 있게 했다. 기존 원본 전달 URL과 페이지 문서는 유지하며 고객 `<picture>`·`srcset`, CDN, 객체 저장소는 이번 범위에 포함하지 않았다.

## 구현 결과

### 스키마와 backfill

- 동결된 V25는 `website_media_variant`와 작업 조회용 부분 index를 추가한다. `(asset_id, format, target_width)`는 유일하며 상태는 `PENDING`, `PROCESSING`, `READY`, `FAILED`, 폭은 640·1280, 형식은 WebP로 제한한다.
- V25 backfill은 `ACTIVE`·`UPLOADED`인 기존 자산 중 원본 폭 이하의 대상만 `PENDING`으로 멱등 생성한다. 번들·보관 자산과 원본보다 큰 대상은 만들지 않고 기존 자산·usage·페이지 row를 수정하지 않는다.
- V26은 V25 checksum을 바꾸지 않고 READY 결과의 storage key·MIME·바이트·실제 폭·높이가 모두 유효하도록 additive CHECK 제약을 보강한다.
- V27은 nullable `claim_token UUID` 열만 추가한다. 기존 row를 다시 쓰지 않으며 새 애플리케이션이 선점할 때마다 발급한 token과 attempt를 완료 조건에 함께 사용한다.

### worker, lease와 재시도

- 기본 활성인 전용 단일 스레드 `websiteMediaVariantScheduler`가 2초 간격으로 PostgreSQL 큐에서 한 건씩 처리한다. Boot의 기본 scheduler를 유지해 미디어 변환 중에도 예약 만료 작업을 실행한다. 짧은 transaction의 `FOR UPDATE SKIP LOCKED`로 선점한 뒤 5분 lease와 시도 횟수·claim token을 기록하고, 실제 변환은 transaction 밖에서 수행한다.
- 원본 비율을 유지하는 bicubic 축소와 WebP lossy quality 0.82를 사용한다. 생성 파일을 다시 decode해 폭·높이를 확인하며 원본보다 큰 이미지는 생성하지 않는다.
- 실패 1회 뒤 30초, 2회 뒤 2분에 자동 재시도하고 3회 실패하면 자동 재시도를 끝낸다. lease가 만료된 `PROCESSING` attempt 3도 terminal `FAILED`로 정리한다. 정리 대상은 매 claim마다 `FOR UPDATE SKIP LOCKED LIMIT 1`로 잠가 한 건만 갱신하며, 다른 transaction이 잠근 terminal row가 무관한 eligible 작업 선점을 막지 않는다. 관리자 오류에는 allowlist의 비민감 요약만 저장한다.
- 완료 파일은 claim마다 UUID를 포함한 고유 immutable storage key를 사용한다. 파일 이동에 `REPLACE_EXISTING`을 사용하지 않고, 현재 row lock 아래에서 attempt와 claim token이 모두 일치할 때만 READY를 확정한다.
- rollback callback은 해당 claim이 만든 새 파일만 제거하고, commit callback만 잠금 중 확인한 이전 key의 파일을 제거한다. `STATUS_UNKNOWN`은 commit 가능성이 있으므로 새 파일과 이전 파일을 모두 보존하며 실제 rollback이었다면 남는 orphan은 후속 storage audit 대상이다.

### 공개 전달, 관리자 UI와 수명주기

- 관리자 자산 응답 끝에 additive `variants` 배열을 제공한다. READY가 아닐 때 결과 메타데이터와 `deliveryUrl`은 null이다. 관리자 클라이언트는 catalog·업로드·메타데이터·보관·복원·재시도·일괄 교체 영향 API가 반환한 자산의 누락 `variants`를 빈 배열로 정규화해 이전 API 롤백에도 기존 자산 선택을 유지한다.
- `GET /api/website/media/{mediaId}/variants/{targetWidth}.webp`는 활성 업로드 자산의 READY 파일만 전달한다. 응답은 `Content-Type: image/webp`, `Cache-Control: public, max-age=31536000, immutable`, `X-Content-Type-Options: nosniff`를 사용한다. 그 밖의 상태·보관·번들·파일 누락은 404이며 원본으로 대체하지 않는다.
- `POST /api/staff/website/media/{mediaId}/variants/{targetWidth}/retry`는 `HQ_ADMIN`만 호출할 수 있다. 640·1280의 FAILED row만 시도 횟수·lease·claim token·오류를 비우고 PENDING으로 되돌린다. 잘못된 폭은 400, 없는 대상은 404, 상태·수명주기 충돌은 409다.
- 관리자 미디어 선택기는 대기·처리·READY 규격/용량/링크·FAILED 시도/오류/재시도를 표시한다. 대화상자가 열려 있고 선택 자산이 ACTIVE이며 PENDING/PROCESSING 또는 FAILED 1·2회 variant가 있을 때만 앞 요청이 끝난 뒤 2초 polling을 반복한다. polling과 수동 재시도 응답은 variants만 병합해 입력 중 메타데이터·expectedVersion·카드·초점을 보존한다. 닫기·선택 전환·보관·terminal 상태에서는 중단하고 늦은 retry 응답도 자산·폭 문맥이 일치할 때만 반영한다.
- 보관하면 원본과 READY variant 공개 전달이 즉시 404가 되지만 row와 파일은 복원을 위해 유지한다. 복원은 기존 READY를 다시 전달하고 eligible 누락 row만 멱등 enqueue한다. 영구 삭제는 원본과 모든 READY 파일을 고유 `.trash` 경로로 함께 격리하며 DB commit이면 제거하고 rollback이면 각각 복원한다.
- 기존 `/api/website/media/{id}/content`, `WebsiteMediaAsset.deliveryUrl`, 한국어·영어 페이지 JSON과 고객 renderer는 바꾸지 않았다. 고객 화면은 아직 variant endpoint를 자동 선택하지 않는다.

## 호환성, 배포와 롤백

- V25→V26→V27은 모두 expand migration이다. 이전 애플리케이션은 새 table·열과 생성 파일을 참조하지 않아 기존 원본 전달과 페이지 공개를 계속할 수 있다.
- V27의 nullable 열은 migration과 구버전 binary의 스키마 공존을 허용한다. 다만 claim-token ABA fencing은 새 코드가 발급한 claim부터 보장되므로 rolling 배포에서 이전 worker를 먼저 drain하거나 중단하고, migration 직전 in-flight 작업은 lease 만료 후 새 코드가 재선점하게 해야 한다.
- 애플리케이션 롤백은 이전 API image를 먼저 배포하고 V25~V27 table과 variant 파일을 그대로 둔다. table drop과 파일 일괄 정리는 별도 승인된 contract 단계에서만 수행한다.

## 검증

### 최종 리뷰 보완 검증

- `WebsiteMediaVariantSchedulingTest`와 기존 서버 6개 대상 클래스, `PaymentExpiryIntegrationTest`를 함께 실행해 99건, failures/errors/skipped 0으로 통과했다. variant 통합은 기존 21건에서 잠금 회귀를 포함한 22건으로 늘었다.
- 실제 Spring scheduling 등록에서 인코더를 latch로 막아도 예약 만료 서비스 호출이 별도 스레드에서 실행됐으며, 미디어 scheduler의 단일 스레드와 Boot 기본 scheduler 공존을 확인했다. 큐 테스트는 실제 PostgreSQL 별도 connection으로 terminal row 잠금을 유지하고 claim transaction에 `lock_timeout=1s`를 적용했다. 기존 UPDATE는 SQL state `55P03`으로 실패했고 수정 후 다른 작업 선점·한 건씩 terminal 정리·잠금 해제 후 나머지 정리를 통과했다.
- 관리자 미디어 E2E 27건(variant 6건·기존 미디어 경로 21건)과 자산 API 경계 7건, 합계 34건이 통과했다. 메타데이터 버전 보호, FAILED 1·2회 backoff, 보관/terminal polling 중단과 이전 API 카탈로그 선택을 포함한다. 관리자 TypeScript는 오류 0이었다.
- Impeccable detector를 변경한 picker/API 대상에 한 번 실행해 `[]`, 종료 코드 0을 확인했다. 설계 문서의 EOF 빈 줄을 제거했다. 전체 브랜치 whitespace 검사는 최종 커밋 후 내부 수정 보고서에 기록한다.
- 위 보완은 테스트 DB에서만 실행했다. 개발 API/DB mutation smoke, 서버 재시작과 추가 Docker 배포는 하지 않았다. 아래 Docker/HTTP 결과는 최초 구현 검증 이력이다.

### 서버

```powershell
cd services/api
.\mvnw.cmd '-Dtest=WebsiteMediaVariantEncoderTest,WebsiteMediaVariantIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest,WebsiteTranslationIntegrationTest' test
```

- `WebContentIntegrationTest` 4건, `WebsiteMediaIntegrationTest` 21건, `WebsiteMediaVariantEncoderTest` 2건, `WebsiteMediaVariantIntegrationTest` 21건, `WebsitePageIntegrationTest` 26건, `WebsiteTranslationIntegrationTest` 18건이 통과했다.
- 합계 92건, failures 0, errors 0, skipped 0, `BUILD SUCCESS`였다. PostgreSQL test DB와 테스트별 격리 schema에서 V25, V26, V27 경로를 포함했다.
- PowerShell에서 브리프의 따옴표 없는 comma selector를 그대로 실행한 첫 시도는 Maven 실행 전 `ParserError`였고, 동일 selector를 단일 인자로 따옴표 처리해 위 결과를 얻었다.

### 관리자와 고객

- 관리자 브리프 grep은 한국어 제목 1건만 선택해 Chromium 1건이 통과했다. Task 4에 기록한 영문 미디어 제목 selector도 별도로 실행해 업로드·선택·메타데이터·보관/복원·영구 삭제·파일 교체·초안 사용 위치 교체 18건이 통과했다.
- 관리자 `pnpm exec tsc --noEmit`은 오류 0, 종료 코드 0이었다.
- 고객 `content-page.test.ts`, `content-collection.test.ts` 두 parser script는 각각 종료 코드 0이었다. 스크립트는 assertion 수를 출력하지 않는다.
- 고객 `pnpm build`는 TypeScript와 Vite production build를 완료했고 2,900개 module을 변환했다.

### Docker와 실제 HTTP

- `docker compose build api`가 Alpine 기반 image에서 119개 production source와 15개 test source를 컴파일하고 package했다. 실행 중인 개발 API 컨테이너는 교체하지 않았다.
- `db-test` 안의 일회성 DB `hotel_chain_task5_124e4956a8d6`, 전용 API 컨테이너 `hcm-task5-api-124e4956a8d6`, 4082 포트와 전용 volume `hcm-task5-media-124e4956a8d6`을 사용했다. 생성한 test-only 비밀번호와 데이터만 사용했다.
- 빈 DB에서 Flyway history의 V25 `website media variants`, V26 `harden website media variant ready constraint`, V27 `add website media variant claim token`이 순서대로 `success=true`였고 health는 HTTP 200·`UP`이었다.
- 1,672×941 PNG 2,336,001바이트를 HTTP 200으로 업로드했다. 640·1280 variant는 모두 attempt 1에 READY가 됐다.
- 640 endpoint는 HTTP 200, `image/webp`, 정확한 `public, max-age=31536000, immutable`, RIFF/WEBP bytes, 48,446바이트를 반환했다.
- 원본 endpoint의 처리 전·후 SHA-256은 모두 `d4ff6adf929730317f8efdbbe57f5e4207a29bcccb9b83f28fc28d6999a255ae`로 업로드 source와도 같았다.
- test-only 세션은 HTTP 204로 로그아웃했다. 격리 컨테이너·DB·volume은 모두 제거됐음을 각각 확인했고, 개발 4080 컨테이너 ID는 전후 `d43d06b326b9`로 같았다. 개발 DB와 사용자 자산·페이지에는 mutation을 보내지 않았다.

## 미검증과 다음 작업

- 실제 운영의 다중 인스턴스 rolling 배포와 장시간 CPU·메모리·큐 부하, 운영 알림, 실패·orphan storage audit은 검증하지 않았다.
- 고객 `<picture>`·`srcset` 전환과 브라우저별 선택·fallback은 구현·검증하지 않았다.
- CDN과 S3 호환 객체 저장소, AVIF, crop·초점·관리자 지정 규격, variant 일괄 재생성은 후속 범위다.
- 변경 경로 밖 전체 backend suite, 전체 관리자 E2E, 고객 예약·결제 회귀는 실행하지 않았다.
