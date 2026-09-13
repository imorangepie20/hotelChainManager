# CMS S3 호환 미디어 저장소 이관

## 목표와 완료 기준

기존 로컬 파일, DB storage key와 공개 URL을 유지한 채 신규 미디어를 로컬·S3에 이중 기록하고, 기존 참조 파일을 삭제 없이 backfill한 뒤 S3 우선 읽기와 로컬 fallback으로 전환할 수 있게 한다.

- S3 설정이 없는 `local` 모드는 기존 동작을 유지한다.
- `mirror`에서 신규 원본과 READY WebP variant가 로컬·S3 양쪽에 기록된다.
- DB 참조 객체만 최대 100개씩 멱등 backfill하며 내용이 다른 S3 객체를 덮어쓰지 않는다.
- `s3-primary`는 S3를 우선 읽고 실패하면 로컬을 한 번 읽으며 fallback을 계측한다.
- 영구 삭제의 격리·commit purge·rollback restore를 양쪽 저장소에 적용한다.
- 고객 URL, 페이지 JSON, MIME과 cache header를 변경하지 않는다.

## 구현 결과

`WebsiteMediaObjectStore`가 provider별 객체 조작을 소유하고 `WebsiteMediaStorageGateway`가 모드별 읽기·쓰기·보상과 격리 수명주기를 조정한다. 로컬 구현은 기존 경로 안전성과 create-only 쓰기를 보존한다. S3 구현은 AWS SDK for Java `2.54.17`을 사용하며 개발 계약 검증은 digest가 고정된 Adobe S3Mock `5.1.0`으로 수행한다.

지원 모드는 다음과 같다.

| 모드 | 읽기 | 신규 쓰기 | 로컬 사본 |
| --- | --- | --- | --- |
| `local` | 로컬 | 로컬 | 유지 |
| `mirror` | 로컬 | 로컬 성공 후 S3 | 유지 |
| `s3-primary` | S3 우선, 실패 시 로컬 1회 | S3 성공 후 로컬 | 유지 |

이중 쓰기 중 한쪽이 실패하면 이번 요청이 새로 만든 객체만 보상 삭제하고 DB를 확정하지 않는다. 보상 결과를 알 수 없으면 추가 삭제를 멈추고 로그·점검 대상으로 남긴다. 영구 삭제는 양쪽 객체를 transaction별 `.trash` key로 격리하며 DB commit 시 purge하고 rollback 시 원래 key로 복원한다.

저장소 점검 응답에는 기존 최상위 필드를 유지하면서 `mode`와 저장소별 `stores`를 추가했다. 이관 상태는 DB가 참조하는 업로드 원본과 READY variant를 `both`, `localOnly`, `s3Only`, `mismatch`, `missing`으로 집계한다. `mirror`에서만 한 번에 최대 100개 `localOnly` 객체를 S3에 추가 복사한다. 같은 내용은 멱등 처리하고 다른 내용은 `mismatch`로 남기며 로컬 파일과 DB를 변경하지 않는다.

관리자 미디어 선택기는 모드, 저장소별 점검, 이관 집계와 fallback 횟수를 보여준다. `mirror`에서만 `다음 100개 복사`를 제공하며 기존 로컬 파일을 삭제하지 않는다는 확인 문구를 표시한다. 자동 반복은 하지 않는다.

## 설정

기본값은 `local`이며 S3 설정 없이 시작할 수 있다.

- `WEBSITE_MEDIA_STORAGE_DIR`: 기존 로컬 저장 경로
- `WEBSITE_MEDIA_STORAGE_MODE`: `local`, `mirror`, `s3-primary`
- `WEBSITE_MEDIA_S3_ENDPOINT`: S3 호환 endpoint. AWS 기본 endpoint를 쓸 때는 비울 수 있다.
- `WEBSITE_MEDIA_S3_REGION`: region
- `WEBSITE_MEDIA_S3_BUCKET`: 비공개 bucket
- `WEBSITE_MEDIA_S3_PATH_STYLE`: path-style 사용 여부
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`: 표준 AWS credential provider chain 입력

자격 증명·endpoint query·절대 경로·객체 bytes는 API와 구조화 로그에 노출하지 않는다. Compose의 S3Mock은 명시적 `object-storage` profile에서만 실행한다.

## 운영 전환과 롤백

1. 새 코드를 기본 `local`로 배포하고 기존 URL과 checksum을 확인한다.
2. 모든 신규 인스턴스에 S3 설정을 넣고 `mirror`로 전환한다.
3. 이전 버전 worker를 drain하고 in-flight lease가 정리된 뒤 신규 원본·variant의 양쪽 쓰기를 확인한다.
4. 수동 backfill을 반복해 `localOnly=0`, `mismatch=0`, `missing=0`을 확인한다.
5. 모든 이전 worker가 종료된 뒤에만 `s3-primary`로 전환하고 fallback counter를 관찰한다.
6. 관찰 기간에도 이중 쓰기와 로컬 사본을 유지한다. 로컬 제거 또는 S3 전용 모드는 별도 승인 대상이다.

장애 시 `s3-primary → mirror → local` 순으로 설정을 되돌릴 수 있다. backfill은 로컬 파일과 DB를 삭제하지 않으며, 전환 뒤 만든 객체도 로컬에 남으므로 같은 공개 URL로 읽을 수 있다.

관측 counter는 S3 읽기 fallback, 이중 쓰기 실패, 보상 실패와 backfill의 `copied`, `skipped`, `mismatch`, `failed` 결과를 구분한다. storage key는 metric tag로 사용하지 않는다.

## 검증 결과

격리된 Compose project `hotel-media-s3-proof`에서 PostgreSQL `56432`, API `4180`, S3Mock `59190`을 별도 DB·media volume으로 실행했다. 사용자 개발 DB와 media volume은 사용하지 않았으며 검증 뒤 이 project의 container와 volume만 제거했다.

- `local`: 실제 PNG 업로드 후 640px·1280px READY variant 2개를 확인했다. 공개 원본과 로컬 volume의 SHA-256은 모두 `d4ff6adf929730317f8efdbbe57f5e4207a29bcccb9b83f28fc28d6999a255ae`였다.
- `mirror`: backfill 1회 뒤 DB 참조 객체 `both=6`, `localOnly=0`, `mismatch=0`을 확인했다. 두 번째 업로드 원본 checksum도 동일했다.
- `s3-primary`: 기존 공개 URL과 checksum을 유지했다. S3의 테스트 원본 하나만 일시 제거한 뒤 같은 URL이 로컬에서 HTTP 200으로 응답했고 fallback counter가 정확히 1 증가했다. 이후 `mirror` backfill로 S3 객체를 복구했다.
- `local` 롤백: S3 설정을 비운 채 API health `UP`, 자산 2개와 기존 checksum 보존을 확인했다.

최종 서버 집중 suite는 로컬 저장소 8건, S3 계약 3건, gateway 9건, 콘텐츠 4건, 미디어 26건, encoder 2건, variant 22건, 페이지 27건, 번역 18건으로 합계 119건이 실패·오류·skip 없이 통과했다. S3 계약은 create-only, head/list/digest, 격리·복원·purge와 1,001개 객체 pagination을 포함한다. gateway fault test는 fallback, 쓰기 보상과 양쪽 저장소 격리의 rollback restore·commit purge를 확인한다.

관리자 집중 Playwright는 저장소 상태·backfill·variant·업로드·영구 삭제 범위 8건이 통과했고 TypeScript 검사도 통과했다. 변경 파일 ESLint는 오류 0, 기존 경고 5건이며 UI 패턴 탐지는 `[]`였다. 390×844 모바일 상태 패널, 요청 중 비활성화, mismatch·부분 실패, 닫은 뒤 늦은 응답 무시를 포함한다.

## 계획 대비 검증 차이

계획은 영구 삭제 commit·rollback을 실제 PostgreSQL과 S3Mock을 함께 쓰는 두 개의 명명된 통합 테스트로 추가하도록 적었다. 구현에서는 같은 수명주기를 gateway의 양쪽 저장소 회귀 테스트로 검증하고, 실제 S3 동작은 별도 adapter 계약 테스트와 격리 Compose 전환 증거로 나눠 확인했다. 또한 실제 backfill 증거는 6개 참조 객체의 1회 batch였고, 101개를 두 batch로 나누는 API 시나리오는 자동화하지 않았다. 최대 100개 제한과 mismatch 비덮어쓰기는 서비스 구현·API 회귀에서 확인했다.

## 미검증 항목

- 실제 운영 AWS S3, Cloudflare R2 또는 운영 MinIO endpoint와 자격 증명
- CDN 계정, DNS, cache tag·purge 연동
- 다중 인스턴스 동시 backfill과 대용량 부하·성능
- 운영 bucket 정책, lifecycle, 암호화, 비용·복구 훈련

이 항목을 검증하기 전에는 로컬 사본 제거 또는 S3 전용 전환을 승인하지 않는다.
