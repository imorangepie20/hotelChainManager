# CMS S3 호환 미디어 저장소 이관 설계

## 1. 배경

현재 CMS 업로드 원본과 WebP variant는 API 컨테이너의 `/app/media` named volume에 저장된다. 업로드, 공개 전달, 비동기 변환, 재시도, 영구 삭제 보상, 저장소 점검이 모두 같은 로컬 파일 경로를 직접 사용한다. 이 구조는 단일 호스트에서는 단순하지만 API·worker 다중 인스턴스, 호스트 교체, 외부 CDN origin 구성에 적합하지 않다.

이 설계는 AWS S3, Cloudflare R2, MinIO가 공통으로 사용할 수 있는 S3 호환 저장소를 추가한다. 기존 로컬 파일과 공개 URL을 유지한 채 `local → mirror → s3-primary` 순서로 전환하고, 로컬 사본은 롤백 경로로 보존한다.

## 2. 목표

- 기존 업로드 원본과 READY variant를 중단 없이 S3 호환 저장소에 복사한다.
- 신규 원본과 variant를 로컬·S3 양쪽에 기록해 전환 중 어느 버전의 API도 읽을 수 있게 한다.
- S3 우선 읽기에서 객체가 없거나 일시적으로 읽히지 않으면 로컬 사본으로 fallback한다.
- 기존 `storage_key`, DB 행, `/api/website/media/...` 공개 URL과 페이지 JSON을 바꾸지 않는다.
- 기존 보관·복원·영구 삭제와 transaction rollback 의미를 양쪽 저장소에서 유지한다.
- 본사 관리자가 저장소별 정합성과 backfill 진행 상태를 확인하고 명시적으로 다음 batch를 실행할 수 있게 한다.
- 전환 실패 시 환경 설정만 되돌려 로컬 우선 읽기로 복귀할 수 있게 한다.

## 3. 제외 범위

- 기존 로컬 파일, S3 객체 또는 `.trash` 객체의 자동·일괄 삭제
- S3 전용 모드와 로컬 named volume 제거
- 객체 저장소의 공개 ACL, presigned URL 또는 브라우저의 S3 직접 접근
- AWS CloudFront, Cloudflare 등 특정 CDN의 배포·purge API 연동
- DB storage key 변경, 자산별 저장소 열 추가, 페이지 문서 migration
- AVIF, crop, 초점, 사용자 지정 variant 규격과 일괄 variant 재생성
- 자동 scheduler backfill과 운영 알림 전송

## 4. 검토한 접근

### 4.1 선택: 전역 모드 기반 이중 기록

storage key가 이미 provider 중립적인 UUID 기반 값이고 기존 URL이 API를 통과하므로 DB에 저장 위치를 추가하지 않는다. 애플리케이션의 전역 모드만 바꾸고 같은 key를 로컬과 S3에서 사용한다.

장점은 DB·페이지 호환성과 롤백이 단순하다는 점이다. 모든 지원 모드에서 로컬 사본을 계속 기록하므로 이전 binary와 새 binary가 잠시 공존해도 로컬 읽기는 유지된다. 단점은 전환 기간과 롤백 보존 기간 동안 저장 용량을 두 배 사용하고 두 저장소의 쓰기 가용성에 의존한다는 점이다.

### 4.2 보류: 일괄 중단 후 전환

업로드와 worker를 멈추고 파일 전체를 복사한 뒤 전역 경로를 한 번에 S3로 바꾸는 방식이다. 구현량은 적지만 중단 시간, 복사 중 신규 파일 경합, 부분 실패 후 재개와 롤백 증명이 약하다. 현재 플랫폼의 비동기 worker와 운영 중 CMS를 고려해 사용하지 않는다.

### 4.3 보류: 자산·variant별 저장소 열

각 DB 행에 `LOCAL` 또는 `S3` 위치를 기록하면 단계별 상태는 명확하다. 반면 원본·variant 읽기, 보관, 삭제, audit, 재시도와 공개 응답이 모두 행별 분기를 갖고 migration과 rollback도 DB 변경에 묶인다. 현재는 동일 key의 이중 사본만 필요하므로 추가 복잡도를 채택하지 않는다.

## 5. 저장소 경계

### 5.1 공통 계약

`WebsiteMediaObjectStore`는 상대 storage key만 받으며 다음 책임을 가진다.

- 새 key에 byte 또는 로컬 임시 파일을 저장하고 content type, byte size, SHA-256 metadata를 기록한다.
- 객체를 byte 또는 지정한 로컬 임시 경로로 읽는다.
- key의 존재, byte size, 최종 수정 시각과 SHA-256 metadata를 조회한다.
- prefix 아래 객체를 pagination하며 나열한다.
- 같은 저장소 안에서 격리 key로 복사하고 원본을 삭제한다.
- 지정 key를 멱등 삭제한다.

절대 경로, `..`, 빈 key와 `.trash`를 일반 공개 key로 받지 않는다. 저장소 구현은 provider 예외에 storage 이름과 작업 종류를 붙이되 endpoint, access key, secret, 실제 로컬 절대 경로는 API 응답이나 로그에 넣지 않는다.

### 5.2 구현

- `LocalWebsiteMediaObjectStore`는 현재 `Files` 동작을 이동한다. 새 파일은 기존과 같이 덮어쓰지 않고 작성하며, 격리는 같은 root의 `.trash/{transactionId}/...`를 사용한다.
- `S3WebsiteMediaObjectStore`는 AWS SDK for Java 2.x S3 client를 사용한다. custom endpoint, region, bucket, path-style 설정을 지원해 AWS S3·R2·MinIO에 같은 구현을 쓴다. bucket은 비공개이며 API만 자격 증명을 가진다.
- `WebsiteMediaStorageGateway`는 모드에 따라 쓰기·읽기·격리를 조정한다. 한 저장소 쓰기 후 다른 저장소 쓰기가 실패하면 성공한 신규 객체를 멱등 삭제한다. 보상 삭제도 실패하면 원래 오류와 함께 운영 로그에 남기며 storage audit이 orphan으로 표시한다.

### 5.3 모드

| 모드 | 신규 쓰기 | 공개 읽기 | 영구 삭제·복원 | 용도 |
| --- | --- | --- | --- | --- |
| `local` | 로컬 | 로컬 | 로컬 | 현재 기본값과 이전 binary 호환 |
| `mirror` | 로컬 + S3 | 로컬 | 양쪽 | backfill과 혼합 버전 배포 |
| `s3-primary` | S3 + 로컬 | S3, 실패 시 로컬 fallback | 양쪽 | 전환 완료 후 운영 |

이번 범위에는 `s3-only`를 두지 않는다. `mirror`와 `s3-primary`는 region·bucket·자격 증명이 빠지면 애플리케이션 시작을 실패시킨다. custom endpoint는 AWS S3에서 생략할 수 있고 R2·MinIO 같은 호환 저장소에서만 지정한다. `local`은 S3 client를 만들지 않으며 기존 환경 설정만으로 동일하게 동작한다.

## 6. 데이터 흐름

### 6.1 원본 업로드

서버가 기존 PNG/JPEG signature, decode, 용량·픽셀 검증을 마친 뒤 같은 UUID storage key를 현재 모드의 저장소에 기록한다. `mirror`와 `s3-primary`에서는 양쪽 저장이 모두 성공한 뒤에만 자산 DB 행과 variant queue를 commit한다. DB insert가 rollback되면 transaction synchronization이 이번 요청이 만든 양쪽 객체만 삭제한다.

한쪽 쓰기가 실패하면 다른 쪽 신규 객체를 즉시 보상 삭제하고 DB 행을 만들지 않는다. 정리 결과를 알 수 없으면 객체를 보존하고 점검 대상으로 남긴다.

### 6.2 WebP worker

이미지 인코더는 계속 로컬 `Path`를 사용한다. worker는 원본을 작업용 임시 디렉터리로 materialize하고, 고유 `.tmp`에 WebP를 생성·검증한 뒤 claim별 고유 최종 key를 양쪽 저장소에 기록한다. 양쪽 기록과 현재 claim 확인이 끝난 뒤에만 variant를 READY로 commit한다.

DB rollback은 해당 claim이 만든 양쪽 최종 객체만 삭제한다. commit은 잠금 중 확인한 이전 READY key의 양쪽 객체를 삭제한다. `STATUS_UNKNOWN`이면 기존 규칙대로 새 객체와 이전 객체를 모두 보존해 다음 audit에서 확인한다. 작업용 로컬 임시 파일은 항상 finally에서 정리한다.

### 6.3 공개 읽기

기존 공개 controller와 URL, MIME, cache header를 유지한다. `local`과 `mirror`는 로컬을 읽는다. `s3-primary`는 S3를 먼저 읽고 객체 없음, timeout 또는 일시적 provider 오류일 때 로컬을 한 번 읽는다. fallback 성공은 정상 응답을 반환하되 구조화 로그와 metric counter를 남긴다. 양쪽 모두 실패하면 기존 404 계약을 유지한다.

보관 상태와 자산·variant DB 상태는 저장소 읽기 전에 계속 검증한다. S3 bucket이나 객체는 공개하지 않으므로 보관된 자산이 저장소 URL로 우회 노출되지 않는다.

### 6.4 영구 삭제

영구 삭제는 현재 자산 version, 보관 30일, 사용 위치 0건을 DB 잠금에서 확인한다. 각 저장소에서 원본과 READY variant를 `.trash/{transactionId}/{storageKey}`로 복사하고 byte size·SHA-256을 확인한 뒤 원본 key를 삭제한다.

모든 저장소의 격리가 성공해야 DB 삭제를 진행한다. DB commit callback은 양쪽 trash 객체를 삭제한다. rollback callback은 trash에서 원래 key를 복원·검증한 뒤 trash를 삭제한다. 중간 실패는 이미 격리한 객체를 역순 복원하며, 복원 결과를 알 수 없으면 자동으로 더 삭제하지 않고 오류와 audit 대상으로 남긴다.

## 7. 기존 파일 backfill

backfill 대상은 DB가 참조하는 업로드 원본과 storage key가 있는 READY variant뿐이다. 로컬 orphan, 최근 `.tmp`, `.trash`는 복사하지 않는다.

`HQ_ADMIN` 전용 상태 조회는 각 key를 다음처럼 집계한다.

- `both`: 로컬과 S3의 byte size·SHA-256이 일치
- `localOnly`: 로컬에만 존재
- `s3Only`: S3에만 존재
- `mismatch`: 양쪽에 있지만 내용 검증 불일치
- `missing`: 양쪽 모두 없음

명시적 backfill 요청은 `localOnly` key를 최대 100개씩 S3에 복사한다. 동일 SHA-256 객체가 이미 있으면 성공으로 건너뛰고, 다른 객체가 있으면 덮어쓰지 않고 `mismatch`로 보고한다. batch가 중간 실패해도 이미 검증된 객체를 유지하며 같은 요청을 다시 실행할 수 있다. 자동 반복이나 삭제는 하지 않는다.

CMS는 현재 미디어 선택기의 저장소 점검 영역에서 모드와 위 집계를 보여준다. `mirror`에서만 `다음 100개 복사`를 제공하며 실행 전 복사는 추가 사본을 만들고 기존 파일을 삭제하지 않는다고 설명한다. 완료 후 상태를 다시 조회한다.

## 8. API와 응답 호환성

- 기존 `GET /api/staff/website/media/storage-audit`의 최상위 `healthy`, 누락·orphan·오래된 임시 파일 목록은 현재 읽기 우선 저장소 결과로 유지한다.
- 응답에 `mode`와 `stores`를 additive하게 추가한다. 각 store는 이름, health, 누락·orphan·오래된 임시 key를 상대 경로로만 제공한다.
- `GET /api/staff/website/media/storage-migration`은 backfill 집계와 최근 fallback 수를 반환한다.
- `POST /api/staff/website/media/storage-migration/backfill`은 최대 100개의 idempotent 복사 결과를 반환한다.
- 두 migration API는 `HQ_ADMIN`만 사용할 수 있다. 기존 media·page API와 고객 응답에는 필드를 추가하지 않는다.

## 9. 설정과 비밀값

기존 로컬 경로 설정 `website.media.storage-dir`과 `WEBSITE_MEDIA_STORAGE_DIR`은 그대로 유지하고, `website.media.storage.mode`, `website.media.storage.s3.endpoint`, `website.media.storage.s3.region`, `website.media.storage.s3.bucket`, `website.media.storage.s3.path-style`을 추가한다. access key와 secret key는 표준 AWS credential provider chain 또는 환경 변수로만 주입한다. `.env.example`에는 값이 없는 변수 이름과 로컬 S3Mock 사용법만 기록하고 실제 운영 비밀값은 저장소에 넣지 않는다.

Docker Compose에는 명시적 profile에서만 실행되는 Adobe S3Mock 개발 서비스를 추가한다. MinIO OSS repository는 보관 상태이고 공개된 보안 권고가 모든 최종 OSS release에 적용되므로 새 로컬 의존성으로 도입하지 않는다. 기본 `docker compose up`은 현재 PostgreSQL·API·concierge 구성과 로컬 named volume을 그대로 사용한다. S3Mock은 `adobe/s3mock:5.1.0@sha256:65cf60155a2e235fe7d5bf6c633747d6fc7ed93f9f5a6727d86470026b83c2a2`로 고정하고 `127.0.0.1`에만 노출한다.

## 10. CDN 경계

고객과 CMS는 계속 `/api/website/media/{id}/content`와 `/api/website/media/{id}/variants/{width}.webp`를 사용한다. CDN은 이 API를 origin으로 구성할 수 있으며 애플리케이션은 현재 원본 재검증 cache와 variant immutable cache header를 유지한다.

이번 단계는 특정 CDN 계정, DNS, purge, cache tag를 호출하지 않는다. CDN이 없는 개발 환경에서도 동일한 API가 동작하고, S3 endpoint와 bucket 정보는 고객 응답에 나타나지 않는다.

## 11. 배포 순서

1. 새 코드를 `local` 기본값으로 배포한다. 기존 동작과 파일 checksum을 확인한다.
2. 모든 새 인스턴스에 S3 설정을 넣고 `mirror`로 배포한다. 이전 worker가 남아 있는 동안 공개 읽기는 로컬을 유지한다.
3. 이전 worker를 drain한 뒤 신규 업로드·variant가 양쪽에 기록되는지 확인한다.
4. 관리자 상태 조회와 backfill batch를 반복해 `localOnly=0`, `mismatch=0`, `missing=0`을 확인한다.
5. `s3-primary`로 배포하고 S3 읽기와 fallback counter를 확인한다.
6. 운영 관찰 기간에도 양쪽 쓰기와 로컬 파일을 유지한다. S3 전용 전환과 로컬 제거는 별도 설계·승인 후 수행한다.

혼합 버전 중 이전 binary가 로컬에만 만든 객체는 backfill이 복구한다. `s3-primary`는 모든 이전 worker가 drain되고 backfill 검증이 끝난 뒤에만 사용한다.

## 12. 롤백

- `s3-primary` 장애는 `mirror` 또는 `local`로 환경 설정을 되돌리고 재배포한다.
- 모든 지원 모드가 신규 객체를 로컬에도 기록하므로 전환 후 생성된 자산과 variant도 로컬에서 읽을 수 있다.
- backfill은 로컬 원본을 삭제하거나 DB를 변경하지 않으므로 중단해도 고객 응답에 영향이 없다.
- S3 객체 삭제와 bucket 제거는 롤백에 필요하지 않으며 이번 범위에서 실행하지 않는다.
- 영구 삭제 중 부분 실패는 자동 재시도 삭제를 하지 않고 자산 DB transaction을 rollback한 뒤 저장소별 audit 결과로 운영자에게 노출한다.

## 13. 오류와 관측

- 저장소 작업 로그에는 operation, store, 상대 key, media/variant ID, claim 또는 transaction ID를 기록한다. 자격 증명, endpoint query, 절대 경로와 객체 byte는 기록하지 않는다.
- S3 읽기 fallback, mirror write 실패, 보상 실패, backfill copied/skipped/mismatch/failed를 counter로 기록한다.
- 정상적인 객체 없음과 provider 장애를 구분한다. 공개 응답은 기존 404를 유지하지만 운영 로그와 metric에는 원인을 남긴다.
- CMS는 provider 원문 예외를 표시하지 않고 저장소 작업 종류와 재시도 가능한 상태만 안내한다.

## 14. 검증 전략

- 로컬 구현 계약 테스트로 경로 검증, create-only write, read, list, 격리·복원, 멱등 삭제를 확인한다.
- Adobe S3Mock 5.1.0 실제 API 계약 테스트로 같은 동작과 metadata·pagination·custom endpoint·path-style을 확인한다. 운영 배포 전에는 선택한 AWS S3·R2·MinIO 대상에서도 같은 계약 검사를 실행한다.
- fault-injection store로 mirror 두 번째 쓰기 실패, 보상 실패, S3 읽기 실패 후 로컬 fallback과 양쪽 읽기 실패를 재현한다.
- 실제 PostgreSQL·S3Mock 통합 테스트로 업로드 DB rollback, variant READY/rollback/재시도, 보관·복원, 영구 삭제 commit/rollback과 storage audit을 확인한다.
- backfill 테스트는 100개 제한, 재실행, 기존 동일 객체 skip, mismatch 비덮어쓰기, DB 미참조 파일 제외를 확인한다.
- 기존 `WebsiteMediaIntegrationTest`, `WebsiteMediaVariantIntegrationTest`, 공개 콘텐츠·페이지 resolve 테스트로 기존 URL·MIME·cache header와 페이지 JSON이 유지됨을 확인한다.
- 관리자 Playwright는 local·mirror·s3-primary 상태, batch 실행 확인, loading·오류·부분 성공과 모바일 dialog를 검증한다.
- 로컬 Compose S3Mock에서 실제 원본 업로드, 640/1280 WebP 생성, backfill, S3 우선 읽기와 강제 fallback을 수행하고 원본·variant checksum을 비교한다.

## 15. 완료 기준

- S3 설정이 없는 `local` 모드의 기존 테스트와 실제 개발 흐름이 동일하게 동작한다.
- `mirror`에서 신규 원본·READY variant가 같은 key와 SHA-256으로 양쪽 저장소에 존재한다.
- 기존 DB 참조 파일을 삭제 없이 batch backfill하고 재실행해도 결과가 변하지 않는다.
- `s3-primary`가 S3를 읽고 장애 시 로컬로 fallback하며 fallback이 관측된다.
- 영구 삭제 commit과 rollback이 양쪽 저장소에서 기존 자산 수명주기 의미를 보존한다.
- 저장소별 점검은 상대 key만 표시하고 자동 삭제·복구를 수행하지 않는다.
- 기존 공개 URL, 페이지 JSON, cache header와 고객·CMS 이미지 동작이 유지된다.
- 배포·롤백 절차와 미검증 운영 항목을 한국어 변경 기록에 남긴다.
