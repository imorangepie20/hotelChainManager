# 일반 콘텐츠 페이지 발행본 초안 복원 설계

작성일: 2026-09-11

## 목적

본사 관리자가 일반 콘텐츠 페이지의 이전 **발행본 콘텐츠만** 현재 초안에 가져와 검토한 뒤 다시 발행할 수 있게 한다. 고객 공개본은 이 작업만으로 바뀌지 않는다.

## 현재 근거

`website_page_version`은 발행 때마다 경로·메뉴·콘텐츠의 불변 스냅샷을 보관하지만, 관리 API와 화면은 버전 번호·발행 시각 목록만 보여 준다. V14에서 `CONTENT_PAGE`는 보관할 수 있으며 보관 페이지는 저장·발행을 할 수 없다. 따라서 복원은 기존 페이지 수명주기와 미디어 참조 규칙을 우회하면 안 된다.

## 범위

- 대상은 `ACTIVE` 상태의 `CONTENT_PAGE`만이다.
- 선택한 `website_page_version.page_snapshot.content`를 현재 `draft_content`로 복사한다.
- 현재 초안의 slug, 경로, 메뉴 이름·노출·순서, 부모와 현재 공개본은 유지한다.
- 복원으로 새 초안 버전을 만들고 `website_media_usage`의 `DRAFT` 참조를 동기화한다.
- 감사 로그에 `VERSION_RESTORED`와 원본 발행 버전을 남긴다.
- 관리자 이력 카드에서 명시적인 확인 대화상자를 거쳐 실행한다.

## 제외 범위

- `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`의 버전 복원
- URL·메뉴·부모 이동, 영구 삭제, 깊은 트리
- 고객에게 이전 발행본을 직접 노출하는 미리보기
- 자동 발행, 예약된 발행, 발행본 간 시각 비교

메타데이터를 함께 되돌리면 메뉴와 주소가 의도치 않게 바뀔 수 있으므로, 이 증분에서는 콘텐츠만 복원한다. 경로·트리 설계는 별도 단계에서 다룬다.

## 동작 흐름

1. 본사는 일반 페이지를 열고 발행 이력에서 복원할 버전을 선택한다.
2. 미저장 편집이 있으면 복원 제어를 비활성화한다. 저장된 초안을 덮어쓸 수 있다는 확인 대화상자도 표시한다.
3. 화면은 `expectedLifecycleVersion`, `expectedDraftVersion`, `expectedPublishedVersion`과 선택한 source version을 서버에 보낸다.
4. 서버는 본사 권한, 활성 상태, 세 버전 조건, `0 < sourceVersion < current publishedVersion`, source version의 소유 페이지를 같은 트랜잭션에서 확인한다.
5. 서버는 스냅샷 콘텐츠를 현재 콘텐츠 계약과 활성 미디어 자산으로 다시 검증·정규화한다.
6. 서버는 현재 초안 콘텐츠만 바꾸고 `draft_version`을 1 올린다. 현재 공개 문서와 `published_version`은 바꾸지 않는다.
7. 서버는 초안 미디어 usage를 교체하고 `VERSION_RESTORED` 감사 로그를 기록한다.
8. 화면은 반환된 문서와 이력 목록을 다시 표시하고, 본사가 검토 후 명시적으로 발행하도록 안내한다.

## API 계약

```http
POST /api/staff/website/pages/{pageId}/versions/{sourceVersion}/restore-draft
X-Staff-Session: <HQ session>
Content-Type: application/json

{
  "expectedLifecycleVersion": 1,
  "expectedDraftVersion": 4,
  "expectedPublishedVersion": 3
}
```

성공 시 현재 초안을 포함한 `WebsitePageDocument`를 반환한다. `draftVersion`만 증가하고 `publishedContent`, `publishedVersion`, `publishedMetadata`는 이전 값과 같다.

| 경우 | 응답 |
| --- | --- |
| 지점 직원 | 403 |
| 존재하지 않는 페이지·다른 페이지의 버전 | 404 |
| 0 이하 source version 또는 현재·미래 source version | 400 `INVALID_REQUEST` |
| 보관 페이지·오래된 lifecycle/draft/published 버전 | 409 `WEBSITE_PAGE_LIFECYCLE_CONFLICT` |
| 과거 스냅샷이 현재 콘텐츠 계약 또는 활성 미디어 규칙을 만족하지 않음 | 400 |

## 서버 설계

`WebsitePageService`에 `restoreContentPageVersionDraft`를 추가한다. 이 메서드는 `lockedContentPageForUpdate`로 현재 행을 잠그고 V14와 같은 lifecycle request를 검증한 뒤, source version이 현재 `publishedVersion`보다 엄격히 작은지 확인한다. `website_page_version`은 `page_id`와 `sourceVersion`으로만 읽어 다른 페이지 이력을 복원할 수 없게 한다.

스냅샷의 `pageType`, `hotelId`, `parentId`, `content`를 읽어 현재 페이지와 일치하는지 확인한다. `content`는 `WebsiteMediaReferenceService.normalizeStructuredContent`와 `ContentPageValidator`를 통과해야 한다. 이 검증은 V12 이전 스냅샷에 없던 자산 ID나 보관된 자산을 자동으로 되살리지 않는다.

조건부 UPDATE는 `id`, `page_type = 'CONTENT_PAGE'`, `lifecycle_status = 'ACTIVE'`, lifecycle·draft·published version을 모두 비교한다. 성공 후에만 `synchronizeDraft`를 호출한다. `website_page_audit` action 제약에 `VERSION_RESTORED`를 추가하는 V15 Flyway migration을 둔다.

## 관리자 UX와 접근성

- 이력 카드의 현재 공개본보다 이전 버전에만 `발행본 vN 콘텐츠를 초안으로 복원` 버튼을 둔다.
- 복원은 현재 페이지가 활성이고 미저장 입력이 없을 때만 가능하며, 미저장 상태에서는 저장 후 복원 가능하다는 사유를 이력 카드에 표시한다.
- 확인 대화상자는 현재 초안이 선택 버전의 콘텐츠로 바뀌며 고객 웹은 바뀌지 않는다는 점, 재발행이 필요하다는 점을 설명한다.
- 처리 중 버튼을 비활성화한다.
- 성공·충돌·검증 실패는 `role=status` 또는 기존 오류 영역으로 명확히 알린다.

## 완료 기준

- 이전 발행본 콘텐츠는 새 초안이 되지만, 고객 공개 resolve·navigation은 재발행 전까지 기존 발행본을 유지한다.
- 복원 뒤 발행하면 선택 버전 콘텐츠가 고객 공개본이 된다.
- 미디어 usage는 새 초안의 자산만 가리킨다.
- 보관·충돌·권한·다른 페이지 version·현재 발행본 version을 안전하게 거부한다.
- 서버 통합 테스트, 관리자 TypeScript 검사와 대상 CMS E2E를 통과한다.
