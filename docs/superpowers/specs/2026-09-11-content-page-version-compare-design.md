# 일반 콘텐츠 페이지 발행 이력 비교 설계

최종 갱신: 2026-09-11

## 목적

본사 관리자가 `CONTENT_PAGE`를 복원하기 전에 두 발행본의 경로·메뉴·SEO·구조화 콘텐츠 차이를 읽기 전용으로 확인한다. 비교는 초안, 현재 공개본, 페이지 수명주기, 미디어 사용 위치, 감사 로그를 변경하지 않는다.

## 범위와 결정

- 대상은 본사 권한의 `CONTENT_PAGE`만이다. `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`은 첫 범위에서 제외한다.
- `ACTIVE`와 `ARCHIVED` 페이지 모두 비교할 수 있다. 보관은 발행 이력을 지우지 않으므로 과거 내용을 검토하는 데 막을 이유가 없다.
- 두 버전은 같은 페이지에 속한 양수 발행본이어야 하며 `baseVersion < compareVersion`이다.
- 서버는 두 불변 snapshot을 반환하고 범용 JSON diff를 만들지 않는다. 비교 표현은 관리자 UI가 담당한다.
- 오래된 이미지가 보관되어 공개 URL이 404가 되더라도 이력 비교는 가능해야 한다. 따라서 비교 조회에서는 활성 미디어 확인, URL 정규화, 이미지 로드를 하지 않고 당시의 `imageAssetId`, `imageSrc`, `imageAlt`를 텍스트로 표시한다.
- 블록은 안정 식별자가 없으므로 V1은 배열 위치와 block type을 기준으로 필드 차이를 표시한다. 블록 이동 감지는 하지 않는다.

## API 계약

```http
GET /api/staff/website/pages/{pageId}/versions/compare?baseVersion=2&compareVersion=3
X-Staff-Session: <HQ session>
```

성공 응답은 두 발행 시점 snapshot만 담는다.

```json
{
  "pageId": "uuid",
  "base": {
    "version": 2,
    "publishedAt": "2026-09-11T02:00:00Z",
    "metadata": {
      "slug": "story",
      "path": "/brand/story",
      "menuLabel": "브랜드 이야기",
      "menuVisible": true,
      "menuOrder": 10
    },
    "publishedFromDraftVersion": 3,
    "content": { "seo": {}, "blocks": [] }
  },
  "compare": { "version": 3, "publishedAt": "2026-09-11T03:00:00Z", "metadata": {}, "publishedFromDraftVersion": 4, "content": {} }
}
```

| 경우 | 응답 |
| --- | --- |
| 본사가 같은 일반 페이지의 두 정상 버전을 비교 | 200 |
| 0 이하, 같은 버전, 역순 버전, 손상된 snapshot | 400 `INVALID_REQUEST` |
| 페이지가 없거나 일반 페이지가 아님, 해당 페이지에 version이 없음 | 404 `WEBSITE_PAGE_NOT_FOUND` |
| 지점 직원 | 403 `STAFF_HOTEL_ACCESS_DENIED` |
| 빈·무효 세션 | 401 `STAFF_AUTHENTICATION_REQUIRED` |
| 보관된 일반 페이지 | 200 |

Spring의 query parameter 바인딩 자체가 막는 누락·숫자 아닌 값은 기존 Spring 400 동작을 유지한다. 이를 위해 전역 바인딩 예외 형식을 새로 바꾸지 않는다.

## 서버 설계

`WebsitePageService.compareContentPageVersions`는 `@Transactional(readOnly = true)`다. 본사 권한을 확인하고 대상 행이 `CONTENT_PAGE`인지 확인한 뒤, 두 version의 순서를 검증한다. 각 snapshot은 반드시 `website_page_version where page_id = ? and version = ?`로 개별 조회한다.

snapshot envelope의 `pageType=CONTENT_PAGE`, `hotelId=null`, 현재 페이지의 `parentId` 일치 여부를 확인한다. `slug`, `path`, `menu`, `publishedFromDraftVersion`의 타입과 범위를 확인하고, `content`는 `ContentPageValidator`로 검증한다. 이 검증은 역사 데이터의 내부 무결성만 확인하며 현재 활성 자산 또는 현재 초안·공개 상태에는 의존하지 않는다.

새 DTO는 다음 역할을 가진다.

```java
public record WebsitePageVersionSnapshot(
    int version,
    Instant publishedAt,
    WebsitePageMetadata metadata,
    Integer publishedFromDraftVersion,
    Map<String, Object> content
) {}

public record WebsitePageVersionComparison(
    UUID pageId,
    WebsitePageVersionSnapshot base,
    WebsitePageVersionSnapshot compare
) {}
```

새 Flyway migration, audit action, 미디어 usage 변경은 없다.

## 관리자 UX

`CONTENT_PAGE` 발행 이력에서 현재 발행본보다 이전인 카드에 `현재 발행본과 비교` 버튼을 둔다. 기본 선택은 해당 카드의 version과 현재 발행본이며, 대화상자 안의 `기준 발행본`·`비교 발행본` 선택기로 같은 페이지의 임의의 두 서로 다른 발행본을 다시 선택할 수 있다. 선택은 오름차순 제약을 유지해 기준이 항상 더 이전 버전이 되도록 한다.

비교는 파괴적 동작이 아니므로 `AlertDialog`가 아닌 `Dialog`를 사용한다. 제목은 `발행본 비교`, 설명은 `읽기 전용이며 초안과 고객 웹을 변경하지 않습니다.`로 둔다. 넓은 스크롤 대화상자는 데스크톱에서 좌우 두 열, 좁은 화면에서 위아래 순서로 표시한다. `닫기` 버튼과 Escape로 닫을 수 있으며, 닫힌 뒤 시작 버튼으로 포커스가 돌아가야 한다.

각 열에는 version·발행 시각·발행 당시 초안 버전과 페이지 메타데이터를 표시한다. SEO, HERO, TEXT, CTA는 편집 폼이 아닌 읽기 전용 필드 표로 렌더링한다. 값마다 `동일`, `변경`, `추가`, `제거`를 텍스트로 표시해 색만으로 상태를 전달하지 않는다. snapshot 요청이 실패하면 dialog를 유지하고 `role="alert"` 오류를 표시한다.

## 검증 기준

- 서버 통합 테스트는 두 발행본의 historical metadata·content·timestamp가 정확한지, 비교 전후 draft/public/lifecycle/audit/media usage가 변하지 않는지 확인한다.
- 보관 페이지 비교, 지점 직원 거부, 0·같음·역순 version 거부, 페이지에 없는 version·다른 페이지 version·SECTION 거부를 확인한다.
- 관리자 E2E는 비교 dialog의 두 snapshot, 필드 변경 라벨, Select 변경, `닫기`와 Escape, 시작 버튼 포커스 복귀, 실패 시 alert와 dialog 유지, POST/PUT/발행/복원 요청 부재를 확인한다.
- 대상 서버 테스트, 관리자 TypeScript와 대상 E2E를 실행한다. Docker API는 재빌드 후 health와 기존 공개 resolve만 읽기 전용으로 확인한다.
