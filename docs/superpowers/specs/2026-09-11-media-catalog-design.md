# CMS 미디어 카탈로그와 안전한 이미지 업로드 설계

최종 갱신: 2026-09-11

## 목표

본사 관리자가 고객 웹에 쓰는 이미지를 카탈로그에서 선택하거나 안전하게 업로드하고, 페이지별 대체 텍스트와 초안·발행 사용 위치를 확인한다. 이미지 선택은 홈, 지점 랜딩, 일반 콘텐츠 페이지에 같은 방식으로 적용한다.

이번 설계는 V12 구현 범위다. 고객 웹의 예약·가격·재고·AI 예약 도우미는 미디어 CMS와 분리된 현재 소유권을 유지한다.

## 현재 문제

현재 CMS는 `imageSrc` 또는 `heroImage`에 문자열을 직접 입력한다. 구조화 페이지는 `/images/` 형태만 검사하지만 파일 존재 여부나 등록 상태를 확인하지 않고, 지점 랜딩은 비어 있지 않은 문자열이면 그대로 고객 `<img>`에 전달한다. 따라서 존재하지 않는 이미지, 임의 경로, 지점 랜딩의 원격 경로가 저장될 수 있다.

현재 고객 웹에서 CMS가 실제로 사용할 수 있는 정적 이미지는 `apps/web/public/images/sokcho-coast-hero.png` 한 개다. 루트 `public/`의 세 Gemini 이미지는 다른 브랜드 문구를 포함하고 있어 카탈로그 초기 데이터로 사용하지 않는다.

## 결정과 범위

- `website_media_asset`와 `website_media_usage`를 추가한다. 초기 카탈로그에는 현재 CMS가 사용하는 속초 해안 PNG 한 건을 안정적인 UUID로 등록한다.
- 새 이미지 업로드는 Docker Compose의 `hotel-media-data` named volume에 저장한다. API 컨테이너를 다시 만들더라도 볼륨은 유지된다. 운영 환경의 오브젝트 스토리지·CDN·변환본은 후속 단계다.
- 업로드는 PNG와 JPEG만 허용하며, 서버가 실제 이미지 형식·바이트 크기·픽셀 수를 검증한다. SVG, GIF, WebP, AVIF, 동영상, 원격 URL, data URL은 이번 범위에서 허용하지 않는다.
- 파일 크기는 최대 10 MiB, 이미지 면적은 최대 24,000,000 픽셀로 제한한다. 저장 이름은 UUID 기반 불변 키로 만들고, 원본 파일명은 공개 URL이나 파일 경로로 사용하지 않는다.
- 자산은 물리 삭제·보관 전환·교체를 이번 범위에 넣지 않는다. 사용 위치를 먼저 기록해 이후 삭제·보관 정책이 참조를 깨지 않게 한다.
- 기본 대체 텍스트는 카탈로그 메타데이터이고, 실제 `imageAlt`·`heroAlt`는 페이지별 문맥에 맞는 별도 필수 값이다. 선택 시 기본값을 복사하지만 이후 자산 기본값을 바꿔도 이미 저장된 페이지의 대체 텍스트는 바꾸지 않는다.

## 데이터 모델

### `website_media_asset`

| 필드 | 용도 |
| --- | --- |
| `id` | 고객 문서가 참조하는 UUID |
| `origin` | `BUNDLED` 또는 `UPLOADED` |
| `delivery_path` | 불변 공개 경로. 내장 파일은 `/images/...`, 업로드 파일은 `/api/website/media/{id}/content` |
| `storage_key` | 업로드 파일의 volume 내 UUID 파일명. 내장 자산은 `NULL` |
| `display_name`, `default_alt_text` | 관리자 카탈로그 표기와 선택 기본값 |
| `mime_type`, `byte_size`, `width`, `height` | 서버 검증 뒤 저장하는 이미지 메타데이터 |
| `status`, `version`, `created_at/by`, `updated_at/by` | 활성 선택 가능 여부, 메타데이터 변경 충돌, 감사 기준 |

`delivery_path`와 `storage_key`는 생성 뒤 바꾸지 않는다. 업로드 자산은 같은 UUID로 덮어쓰지 않는다.

### `website_media_usage`

`asset_id`, `page_id`, `document_state`(`DRAFT` 또는 `PUBLISHED`), `field_path`, `alt_text`, `updated_at`을 저장한다. `(page_id, document_state, field_path)`를 기본 키로 삼아 한 문서 위치가 한 자산만 참조하도록 한다. 사용 위치 API는 페이지 이름·경로·유형과 이 행을 조합해 관리자에 돌려준다.

## 콘텐츠 계약과 저장 흐름

구조화 페이지의 HERO는 다음을 저장한다.

```json
{
  "type": "HERO",
  "imageAssetId": "UUID",
  "imageSrc": "/images/sokcho-coast-hero.png",
  "imageAlt": "동해와 설악산을 바라보는 속초 해안"
}
```

지점 랜딩은 `heroAssetId`, 서버가 채운 `heroImage`, `heroAlt`를 저장한다. `imageSrc`·`heroImage`는 고객 렌더러와 기존 문서 호환을 위한 전달 값이다. 클라이언트가 보낸 값을 신뢰하지 않고 서버가 활성 자산의 `delivery_path`로 다시 채운다.

저장·생성 시 서버는 자산 ID가 활성 카탈로그 행인지 확인하고 문서를 정규화한다. 그 뒤 기존 페이지 버전 조건 UPDATE가 성공한 같은 트랜잭션 안에서 DRAFT 사용 위치를 교체한다. 발행 UPDATE가 성공하면 해당 발행 문서의 PUBLISHED 사용 위치를 교체한다. 버전 충돌이나 검증 실패에서는 페이지와 사용 위치 모두 바꾸지 않는다.

V12 Flyway는 기존 홈·일반 페이지 HERO와 지점 랜딩 히어로에 초기 내장 자산 ID를 넣고, 초안·발행 사용 위치를 역으로 생성한다. 기존 이력 스냅샷은 수정하지 않는다.

## API와 권한

모든 관리 API는 `HQ_ADMIN`만 사용할 수 있고 지점 직원은 403을 받는다.

| 메서드 | 경로 | 역할 |
| --- | --- | --- |
| `GET` | `/api/staff/website/media` | 선택 가능한 카탈로그 목록과 사용 수 |
| `POST` | `/api/staff/website/media` | `file`, `displayName`, `defaultAltText` multipart 업로드 |
| `GET` | `/api/staff/website/media/{mediaId}/usages` | 초안·발행 사용 위치 |
| `GET` | `/api/website/media/{mediaId}/content` | 고객·관리자 이미지 전달 |

이미지 전달 URL은 인증 헤더 없이 읽을 수 있다. CMS가 발행한 이미지 자체는 고객에게 공개되는 리소스이므로, 관리자 전용 헤더를 요구하면 `<img>`와 고객 웹이 읽을 수 없다. 업로드와 카탈로그 조회·사용 위치 조회만 직원 세션을 요구한다.

공개 전달은 카탈로그의 업로드 행만 API volume에서 읽고, 내장 자산은 기존 Vite `/images/` 경로를 계속 사용한다. 업로드 파일은 ID 기반 불변 URL과 장기 캐시 헤더를 사용한다.

## 업로드 안전성

1. Spring multipart 제한과 서비스의 바이트 제한을 모두 적용한다.
2. 서버는 요청 Content-Type과 확장자를 신뢰하지 않고 ImageIO reader로 실제 PNG·JPEG 형식, width·height, 디코드 가능 여부를 확인한다.
3. 사용자가 준 파일명은 표시 이름과 분리하며 파일 시스템 경로에는 쓰지 않는다.
4. 저장 대상은 정규화한 `hotel-media-data` 경로 아래의 UUID 키만 허용한다. 상위 경로 이동은 만들 수 없다.
5. 파일 저장 실패·DB 저장 실패가 나면 성공 응답을 만들지 않는다. 이미 정상화된 업로드 파일의 정리 실패는 서버 로그에 남기고 재시도 가능한 고아 파일로 남긴다.
6. 공개 전달은 DB의 `UPLOADED` 행과 일치하는 `storage_key`만 읽으며, MIME 타입과 `X-Content-Type-Options: nosniff`를 보낸다.

## 관리자 UX

기존 대표 이미지 경로 입력을 재사용 가능한 `MediaField`로 바꾼다. 필드는 16:9 미리보기, 선택 자산 이름·규격, `미디어 선택` 버튼, 이 페이지의 대체 텍스트 입력을 제공한다.

선택기는 제공된 관리자 테마의 Dialog·Card·Button을 사용한다. 상단에서 PNG/JPEG 파일, 자산명, 기본 대체 텍스트를 입력해 업로드하고, 중앙의 자산 카드 격자에서 한 자산을 선택한다. 클릭만으로 창을 닫지 않고 하단 `선택` 버튼이 문서 상태를 바꾼다. 선택한 자산의 사용 위치는 같은 Dialog 안에서 지연 조회한다. 키보드 포커스와 좁은 화면의 스크롤 영역을 보장한다.

## 고객 렌더링

고객 콘텐츠 파서는 HERO의 UUID와 같은 출처의 `/images/...` 또는 `/api/website/media/{uuid}/content` 전달 URL만 허용한다. 서버가 정상화한 발행 문서만 표시하고, 불량 응답은 기존 지점·홈 fallback을 유지한다. 해상도는 카탈로그·관리자 카드에 표시하며, CMS 문서 계약에는 별도 복사하지 않는다.

## 완료 기준과 검증

1. V12 migration은 초기 내장 자산, 기존 문서의 자산 ID, 초안·발행 사용 위치를 만들고 이전 공개 문서를 유지한다.
2. HQ만 목록·업로드·사용 위치를 사용할 수 있고, PNG/JPEG 유효 파일만 10 MiB·24 MP 한도 안에서 업로드된다.
3. 미등록·비활성 자산 ID, 위조 경로, 원격·data·상위 경로 이미지가 홈·일반 페이지·지점 랜딩 모두에서 거부된다.
4. 초안 저장과 발행은 각각 사용 위치를 정확히 교체하며, 버전 충돌은 usage를 바꾸지 않는다.
5. 관리자에서 홈·일반 페이지·지점 랜딩이 같은 선택기로 이미지를 고르고, 기본 alt가 페이지별 alt에 채워지며 저장 전 발행 차단이 유지된다.
6. 고객 공개 페이지는 내장·업로드 자산을 안전한 전달 URL로 표시하고 기존 예약 흐름을 유지한다.

## 후속 범위

자산 이름·기본 alt 편집, 보관·물리 삭제, 파일 교체, 이미지 변환·썸네일·EXIF 제거·바이러스 검사, WebP/AVIF·동영상, S3·CDN, 저작권·사용 기간, 다국어 alt는 후속 단계다.
