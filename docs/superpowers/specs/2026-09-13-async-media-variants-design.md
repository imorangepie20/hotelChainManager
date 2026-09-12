# 비동기 CMS 미디어 variant 설계

## 1. 목표

CMS에 업로드한 활성 PNG/JPEG 원본으로부터 640px와 1280px WebP variant를 요청 처리와 분리해 생성한다. 관리자에게 생성 상태, 결과 크기, 실패 원인과 재시도 수단을 제공하되 기존 페이지 문서와 공개 이미지 URL은 바꾸지 않는다.

## 2. 범위

### 포함

- PostgreSQL을 내구성 있는 작업 큐로 사용하는 비동기 variant 생성
- 원본보다 작거나 같은 640px·1280px 폭의 WebP 생성
- `PENDING → PROCESSING → READY/FAILED` 상태와 처리 lease
- 실패 작업 최대 3회 자동 재시도와 본사 직원의 수동 재시도
- API 재시작 뒤 미완료 작업 복구와 여러 API 인스턴스의 중복 처리 방지
- 기존 활성 업로드 자산의 점진적 backfill
- 관리자 미디어 화면의 상태·규격·용량·오류 표시
- variant 전달 endpoint와 원본·variant의 일관된 보관/복원/영구 삭제 처리

### 제외

- 고객 웹의 `<picture>`, `srcset` 또는 기존 문서 이미지 URL 전환
- CDN, S3 호환 객체 저장소, 외부 메시지 큐
- AVIF, 임의 crop, 초점 좌표, 관리자 지정 크기·품질
- 번들 자산 변환과 기존 원본의 재인코딩·덮어쓰기
- variant 일괄 재생성, 변환 정책 version 관리, 운영 알림

## 3. 현재 구조와 제약

- `website_media_asset`가 UUID, 원본 저장 키, MIME, 크기와 수명주기를 소유한다.
- 업로드는 API 프로세스의 로컬/볼륨 파일 저장소와 DB insert를 한 요청에서 처리한다.
- 페이지 문서는 `/api/website/media/{assetId}/content`를 저장하고 고객 웹과 관리자 미리보기가 이 경로를 검증한다.
- 공개 원본 endpoint는 활성 업로드 자산만 전달한다. variant도 같은 자산 활성 상태를 요구한다.
- 원본은 최대 10 MiB·24 MP의 실제 PNG/JPEG로 이미 검증된다.
- 영구 삭제는 원본을 격리한 뒤 DB transaction 완료 결과에 따라 삭제 또는 복구한다. variant 파일도 같은 원칙을 적용한다.

## 4. 선택한 접근

PostgreSQL의 variant row가 작업 상태와 결과 메타데이터를 함께 소유한다. Spring의 주기 작업자는 짧은 transaction에서 처리 대상을 선점한 뒤 transaction 밖에서 파일 변환을 수행하고, 별도 transaction에서 결과를 확정한다. 외부 큐를 추가하지 않아도 프로세스 재시작과 다중 인스턴스를 견딜 수 있고 현재 Spring/PostgreSQL 운영 경계 안에서 끝난다.

WebP encoder는 Apache-2.0의 `com.github.usefulness:webp-imageio:0.11.0`을 runtime dependency로 고정하고 Java ImageIO SPI를 통해 사용한다. 구현 첫 단계에서 Windows JDK 21과 현재 Alpine 기반 API image 모두 실제 encode/decode를 검증한다. Alpine에서 native library가 로드되지 않으면 임의 포맷으로 대체하지 않고 API runtime base만 digest가 고정된 Temurin 21 Debian 계열로 변경한 뒤 같은 검증을 통과시킨다.

## 5. 데이터 모델

Flyway V25에서 `website_media_variant`를 추가한다.

| 필드 | 의미 |
| --- | --- |
| `id UUID` | variant와 전달 경로의 내부 식별자 |
| `asset_id UUID` | `website_media_asset(id)` 참조, 자산 삭제 시 cascade |
| `format VARCHAR` | 이번 단계에서는 `WEBP`만 허용 |
| `target_width INTEGER` | 이번 단계에서는 `640`, `1280`만 허용 |
| `status VARCHAR` | `PENDING`, `PROCESSING`, `READY`, `FAILED` |
| `storage_key VARCHAR` | READY 파일의 저장 키, 그 전에는 null |
| `mime_type VARCHAR` | READY일 때 `image/webp` |
| `byte_size BIGINT` | READY 결과 바이트 수 |
| `width`, `height INTEGER` | READY 결과 실제 크기 |
| `attempt_count INTEGER` | 선점된 총 시도 횟수, 0~3 |
| `next_attempt_at TIMESTAMPTZ` | 자동 재시도 가능 시각 |
| `lease_expires_at TIMESTAMPTZ` | PROCESSING 작업의 복구 기준 시각 |
| `last_error VARCHAR(500)` | 관리자용 비민감 실패 요약 |
| `created_at`, `updated_at` | 생성·최종 변경 시각 |

`(asset_id, format, target_width)`는 unique다. 작업 조회용 부분 index는 처리 가능한 `PENDING/FAILED`와 만료된 `PROCESSING`을 빠르게 찾도록 구성한다. 상태별 nullable 필드 조합은 CHECK 제약으로 보호한다.

V25는 활성 `UPLOADED` 자산 중 원본 폭이 target 폭 이상인 경우에만 PENDING row를 멱등 insert한다. 작은 원본은 확대하지 않으며 생성할 row가 없다는 사실을 관리자 응답에서 원본 폭으로 설명한다. 보관 자산은 backfill하지 않고, 복원 시 누락된 대상 row를 생성한다.

## 6. 작업 흐름

### 6.1 업로드와 복원

1. 기존 검증과 원본 파일 저장을 수행한다.
2. 자산 insert와 같은 DB transaction에서 적용 가능한 640·1280 PENDING row를 생성한다.
3. 원본 자산 응답을 즉시 반환한다. 응답의 `variants`에는 PENDING 상태가 포함된다.
4. 보관 자산을 복원할 때도 같은 멱등 enqueue를 실행한다.

variant 생성 실패는 원본 업로드·복원을 되돌리지 않는다. 기존 공개 경로는 variant 상태와 무관하게 계속 동작한다.

### 6.2 작업 선점과 생성

1. scheduler가 2초 간격으로 작업 하나를 요청한다.
2. claim transaction은 `FOR UPDATE SKIP LOCKED`로 PENDING, 재시도 시각이 지난 FAILED, lease가 만료된 PROCESSING 중 하나를 선택한다.
3. 선택한 row를 PROCESSING으로 바꾸고 `attempt_count`를 증가시키며 5분 lease를 기록한다.
4. transaction 밖에서 활성 여부와 원본 파일을 확인하고 원본 비율을 유지해 정확한 target 폭으로 축소한다.
5. WebP lossy quality 0.82로 임시 파일에 encode하고 다시 decode해 MIME·폭·높이와 읽기 가능 여부를 검증한다.
6. 임시 파일을 `{assetId}-{targetWidth}.webp`로 원자 이동한 뒤 READY와 결과 메타데이터를 기록한다.

이미 READY인 row와 파일은 덮어쓰지 않는다. API 인스턴스가 처리 중 종료되면 lease 만료 뒤 다른 인스턴스가 같은 row를 다시 선점한다. 최종 이름으로 이동하기 전 임시 파일만 사용하므로 부분 파일은 공개되지 않는다.

### 6.3 실패와 재시도

- 변환·저장·검증 실패는 임시 파일을 제거하고 짧은 비민감 메시지와 함께 FAILED로 기록한다.
- 자동 재시도는 30초, 2분 간격으로 최대 3회 수행한다.
- 3회 실패한 row는 자동 선점하지 않는다.
- 본사 관리자·편집자·게시자는 실패 row의 수동 재시도를 요청할 수 있다. 요청은 활성 업로드 자산과 FAILED 상태를 다시 확인하고 시도 수·오류·재시도 시각을 초기화해 PENDING으로 만든다.
- 수동 재시도는 READY 파일을 재생성하거나 원본을 바꾸지 않는다.

## 7. 저장소와 수명주기

- 원본과 variant는 같은 `website.media.storage-dir` 아래 서로 다른 storage key를 사용한다.
- 자산 보관 즉시 원본과 READY variant endpoint가 모두 404가 된다. 파일과 row는 복원을 위해 유지한다.
- 자산 복원 시 기존 READY variant는 다시 전달 가능하며, 빠진 대상만 enqueue한다.
- 영구 삭제는 원본과 존재하는 모든 variant 파일의 정확한 경로를 먼저 확인한다. 파일들을 `.trash` 아래 고유 경로로 이동하고 DB 자산 삭제를 실행한다.
- transaction commit이면 격리 파일을 제거하고, rollback이면 모든 파일을 원래 위치로 되돌린다. 복구 실패는 asset/variant 식별자와 경로를 구조화 로그에 남기되 사용자 데이터나 파일 내용은 기록하지 않는다.

## 8. API 계약

### 관리자 카탈로그

기존 `WebsiteMediaAsset` 응답에 additive `variants` 배열을 추가한다.

각 항목은 `id`, `format`, `targetWidth`, `status`, `deliveryUrl`, `mimeType`, `byteSize`, `width`, `height`, `attemptCount`, `lastError`, `updatedAt`을 제공한다. READY가 아니면 결과 전용 필드와 `deliveryUrl`은 null이다. 기존 필드는 변경하지 않는다.

### 수동 재시도

`POST /api/staff/website/media/{mediaId}/variants/{targetWidth}/retry`

- 본사 역할만 허용한다.
- 대상 폭은 640 또는 1280이어야 한다.
- 없는 자산/variant는 404, 보관 또는 번들 자산은 409, FAILED가 아니면 409다.
- 성공 시 갱신된 `WebsiteMediaAsset`을 반환한다.

### variant 전달

`GET /api/website/media/{mediaId}/variants/{targetWidth}.webp`

- 활성 업로드 자산의 READY variant만 `image/webp`로 반환한다.
- 미생성·처리 중·실패·보관·없는 파일은 공개본이나 원본으로 대체하지 않고 404다.
- `X-Content-Type-Options: nosniff`와 `Cache-Control: public, max-age=31536000, immutable`을 적용한다.
- 이번 단계에서는 고객 페이지 문서나 renderer가 이 endpoint를 자동 선택하지 않는다.

## 9. 관리자 UI

미디어 선택 dialog의 선택 자산 정보에 `반응형 이미지` 영역을 추가한다.

- PENDING: `변환 대기 중`
- PROCESSING: `WebP 변환 중`
- READY: `640×높이 · WebP · 용량` 형태와 새 탭 미리보기 링크
- FAILED: 시도 횟수, 서버가 정리한 오류, `다시 시도` 버튼
- 원본 폭이 640px 미만: `원본보다 큰 이미지는 생성하지 않습니다.`

dialog가 열려 있고 선택 자산에 PENDING/PROCESSING이 있을 때만 2초 후 catalog를 다시 읽는다. dialog를 닫거나 terminal 상태가 되면 polling을 중단한다. 기존 업로드·메타데이터·보관·교체 interaction은 유지한다.

## 10. 호환성과 롤백

- migration은 새 테이블과 index만 추가하고 기존 자산·usage·페이지 문서를 수정하지 않는 expand 단계다.
- 기존 `deliveryUrl`과 공개 원본 endpoint를 유지하므로 이전 관리자와 고객 앱이 새 API와 함께 동작한다.
- 새 API 배포 중 worker가 생성한 파일은 기존 애플리케이션에서 참조하지 않는다.
- 애플리케이션 롤백은 이전 API image를 먼저 배포한다. V25 테이블과 variant 파일은 그대로 두어도 기존 기능에 영향이 없다.
- 테이블 drop과 variant 파일 정리는 별도 승인된 contract 단계로만 수행한다. 이번 작업에서는 실행하지 않는다.

## 11. 보안과 운영 기준

- target 폭과 storage key는 서버 상수와 DB 값으로만 정하며 요청 문자열로 파일 경로를 만들지 않는다.
- 모든 resolved path가 정규화된 미디어 root 아래인지 확인한다.
- 공개 endpoint는 asset ACTIVE, origin UPLOADED, variant READY, 파일 존재를 모두 확인한다.
- `last_error`에는 예외 클래스, 절대 경로, SQL, 토큰, 사용자 입력 원문을 포함하지 않는다.
- 한 scheduler 실행은 한 작업만 처리해 CPU·메모리 급증을 제한한다. 업로드 제한 24 MP를 그대로 사용한다.
- 운영 health는 원본 서비스와 분리한다. variant 실패만으로 API readiness를 내리지 않고 관리자 상태와 로그로 관찰한다.

## 12. 검증과 완료 기준

- V25가 기존 자산·usage·페이지 checksum을 바꾸지 않고 활성 업로드 자산만 멱등 backfill한다.
- 실제 PostgreSQL과 임시 미디어 디렉터리에서 PNG/JPEG 업로드가 즉시 PENDING을 반환하고 worker 호출 뒤 640·1280 READY가 된다.
- 결과 파일이 WebP로 decode되고 비율·폭·MIME·바이트 수가 DB와 일치하며 원본 파일 checksum은 유지된다.
- 원본보다 큰 target은 생성하지 않는다.
- 두 worker가 경쟁해도 한 row만 선점하고, 만료 lease는 복구되며, 실패는 정해진 backoff와 3회 제한을 따른다.
- 수동 재시도의 권한·상태·폭 검증과 성공 상태 전이를 확인한다.
- 보관/복원과 영구 삭제 commit/rollback에서 원본·variant 접근성과 파일 보존이 일치한다.
- 관리자 Playwright에서 네 상태 표시, terminal 상태 polling 중단, 실패 재시도 요청을 확인한다.
- 관리자 TypeScript, 고객 직접 parser 테스트와 production build를 통과한다.
- Windows JDK 21 직접 테스트와 Docker API image에서 실제 WebP encode/decode, Flyway V25, health `UP`, variant HTTP header를 확인한다.

## 13. 선택하지 않은 접근

- 요청 중 동기 변환은 transaction과 사용자 대기 시간을 결합하고 한 variant 실패가 원본 업로드까지 막으므로 선택하지 않았다.
- 외부 큐·이미지 서비스는 독립 확장에는 유리하지만 현재 작업량과 운영 환경에는 구성·복구 지점이 과도하다.
- PNG/JPEG 축소본은 WebP 목표를 충족하지 않으므로 encoder 문제가 생겨도 자동 fallback으로 사용하지 않는다.

