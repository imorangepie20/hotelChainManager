# 일반 콘텐츠 페이지 영구 삭제 설계

최종 갱신: 2026-09-11

## 목적

더 이상 필요하지 않은 일반 콘텐츠 페이지를 보관 상태에서 명시적으로 영구 삭제한다. 보관은 복원 가능한 공개 중단이고, 영구 삭제는 페이지·발행 이력·감사 이력·미디어 사용 위치를 되돌릴 수 없이 제거하는 별도 작업이다.

## 범위와 안전 규칙

- 대상은 본사 권한의 `ARCHIVED CONTENT_PAGE`만이다. `ACTIVE CONTENT_PAGE`, `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`은 삭제할 수 없다.
- 요청은 현재 lifecycle·draft·published version을 모두 포함한다. 잠금 후 값이 달라졌거나 삭제 대상이 활성 상태이면 409으로 거부한다.
- 삭제는 한 트랜잭션에서 `website_media_usage`, `website_page_version`, `website_page_audit`, `website_page` 순으로 정리한다. 이력과 감사 행은 FK 때문에 남길 수 없으므로 함께 삭제한다.
- 미디어 자산 파일과 `website_media_asset` 행은 삭제하지 않는다. 삭제 뒤 사용 위치가 0건이 된 자산은 기존 자산 보관·복원 정책으로 별도 관리한다.
- 현재 페이지 모델에서 `CONTENT_PAGE`는 자식을 둘 수 없다. 미래 데이터에 자식이 생긴 경우에는 하위 페이지를 자동 삭제하지 않고 409으로 거부한다.
- 서버 로그·외부 알림·별도 삭제 audit은 첫 범위에 넣지 않는다. 삭제한 audit을 같은 테이블에 보존할 수 없으며, 복구 가능한 기록 보관소는 별도 감사 설계에서 다룬다.

## API 계약

```http
DELETE /api/staff/website/pages/{pageId}
X-Staff-Session: <HQ session>
Content-Type: application/json

{
  "expectedLifecycleVersion": 2,
  "expectedDraftVersion": 3,
  "expectedPublishedVersion": 2
}
```

성공은 `204 No Content`이다.

| 경우 | 응답 |
| --- | --- |
| 본사가 현재 version의 보관 일반 페이지 삭제 | 204 |
| 활성 페이지, 오래된 version, 자식 보유 | 409 `WEBSITE_PAGE_DELETE_CONFLICT` |
| 없는 페이지 또는 일반 페이지가 아닌 대상 | 404 `WEBSITE_PAGE_NOT_FOUND` |
| 잘못된 또는 누락된 version | 400 `INVALID_REQUEST` |
| 지점 직원 | 403 `STAFF_HOTEL_ACCESS_DENIED` |
| 빈·무효 세션 | 401 `STAFF_AUTHENTICATION_REQUIRED` |

## 관리자 UX

일반 페이지가 보관 상태일 때만 상단 작업 영역에 `영구 삭제`를 표시한다. 클릭 뒤 확인 대화상자는 메뉴명, 고객 웹에서 이미 제외된 상태, 페이지·발행 이력·감사 이력·미디어 사용 위치가 사라지고 되돌릴 수 없다는 점을 설명한다. 확인 버튼은 `영구 삭제`, 취소 버튼은 `취소`다.

삭제에 성공하면 CMS는 고정 홈으로 이동하고 페이지 트리를 새로 불러온다. 실패하면 페이지를 그대로 유지하고 오류를 표시한다. 활성 페이지의 보관, 초안 저장, 발행, 미리보기, 발행본 비교·복원 흐름은 바꾸지 않는다.

## 검증 기준

- 서버 통합 테스트는 보관된 페이지 삭제 뒤 parent page, version, audit, media usage 행이 모두 0개이고 자산 자체는 남는지 확인한다.
- 활성 페이지, 오래된 version, SECTION, 지점 권한을 거부하는지 확인한다.
- 관리자 E2E는 보관 뒤 확인 dialog, DELETE 본문, 홈 이동, 트리 삭제를 확인한다.
- 서버 대상 테스트, 관리자 TypeScript와 대상 E2E를 실행한다. Docker 재빌드 뒤 health와 기존 공개 page resolve만 읽기 전용으로 확인하며 실제 사용자 페이지에는 삭제 요청을 보내지 않는다.
