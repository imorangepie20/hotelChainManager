# CMS 미디어 초안 사용 위치 일괄 교체 설계

작성일: 2026-09-13

## 목적

현재 `파일 교체`는 한 이미지 필드에서 새 자산을 업로드하고 그 위치만 바꾼다. 같은 자산을 여러 활성 페이지의 한국어·영어 초안에서 사용하는 경우에도 관리자는 위치마다 반복해야 한다. 이번 단계는 본사 관리자가 영향 범위를 먼저 확인한 뒤 해당 자산의 **활성 페이지 초안 사용 위치**를 새 업로드 자산으로 한 번에 교체하게 한다.

기존 파일·자산·공개본·발행 snapshot은 그대로 보존한다. 가격·재고·예약 도메인은 변경하지 않는다.

## 범위

### 포함

- `HQ_ADMIN` 전용 영향 조회와 일괄 교체 API
- `HOME_PAGE`, `HOTEL_LANDING`, 활성 `CONTENT_PAGE`의 한국어·영어 `DRAFT` 사용 위치
- 기존 PNG/JPEG 업로드 API로 생성한 새 활성 자산을 교체 대상으로 사용
- 페이지별 alt·캡션과 블록 순서 보존
- 모든 대상의 version 확인과 단일 트랜잭션 처리
- 영어 초안이 바뀔 때 기존 검토·승인 상태 무효화
- 페이지·locale별 감사 기록
- 관리자 영향 확인·확정 UI, 키보드와 390px 동작

### 제외

- `PUBLISHED` 문서와 과거 `website_page_version` snapshot 수정
- 보관 페이지 초안 변경
- 기존 자산 자동 보관·영구 삭제
- 파일 덮어쓰기
- 일부 대상만 성공하는 부분 적용
- 이미지 variant·crop·WebP/AVIF 변환·CDN·객체 저장소
- `HQ_EDITOR`, `HQ_PUBLISHER`, 지점 직원 권한 확대

## 핵심 불변 조건

1. 새 파일은 새 자산 UUID와 storage key를 가진다.
2. 영향 조회는 쓰기를 만들지 않는다.
3. 확정 전에는 어떤 페이지 초안도 바뀌지 않는다.
4. 확정 시 영향 조회와 같은 활성 초안 사용 위치만 바꾼다.
5. 대상 하나라도 version·상태·사용 위치가 달라졌으면 전체 요청을 `409`로 거부한다.
6. 교체는 자산 UUID와 전달 URL만 바꾼다. 위치별 alt·캡션은 보존한다.
7. 공개본·발행 usage·과거 snapshot은 바꾸지 않는다.
8. 기존 자산과 파일은 보존한다. 신규 자산도 취소 시 카탈로그에 남는다.
9. 고객 공개 변경은 각 페이지의 기존 검토·발행 절차를 거친 뒤에만 발생한다.

## 서버 설계

### 전용 조정 서비스

`WebsiteMediaDraftReplacementService`를 추가한다. 미디어 자산 검증, 한국어 페이지 초안 갱신, 영어 번역 초안 갱신을 한 트랜잭션으로 조정한다. JSON과 검토 상태를 컨트롤러에서 직접 수정하지 않는다.

기존 페이지·번역 서비스에는 한 초안의 자산 참조를 교체하는 좁은 내부 연산을 둔다. 이 연산은 기존 정규화, 미디어 usage 동기화, 영어 승인 무효화 규칙을 재사용한다. 같은 규칙을 새 서비스에 복제하지 않는다.

### 영향 조회

`GET /api/staff/website/media/{sourceMediaId}/draft-replacement-impact?targetMediaId={targetMediaId}`

서버는 다음을 확인한다.

- 호출자는 `HQ_ADMIN`이다.
- 원본과 대상은 서로 다른 `ACTIVE` 자산이다.
- 대상은 `UPLOADED` 출처의 활성 자산이다. 관리자 UI는 현재 교체 대화상자의 업로드 응답만 대상으로 허용한다. 서버는 대화상자 세션을 저장하지 않으므로 출처·활성 상태·version을 다시 확인한다.
- 원본 자산의 현재 `DRAFT` usage를 페이지·locale·field path 순서로 조회한다.
- 활성 페이지의 usage만 교체 대상으로 분류한다.
- `PUBLISHED` usage와 보관 페이지 `DRAFT` usage는 변경 제외 항목으로 집계한다.

응답은 원본·대상 자산 version, 교체 대상별 `pageId`, `pageLabel`, `locale`, `fieldPath`, `draftVersion`, 제외 사유별 수를 포함한다. 대상이 0개면 적용할 수 없다.

### 교체 확정

`POST /api/staff/website/media/{sourceMediaId}/draft-replacements`

요청은 다음 값을 보낸다.

- `targetMediaId`
- `expectedSourceVersion`
- `expectedTargetVersion`
- 영향 조회에서 받은 `pageId`, `locale`, `fieldPath`, `expectedDraftVersion` 목록

서버는 자산 행과 대상 페이지·번역 행을 UUID·locale 고정 순서로 잠근다. 잠금 뒤 활성 상태, 자산 version, 페이지 lifecycle, 초안 version, 실제 `DRAFT` usage 집합을 다시 계산한다. 요청 집합과 다르면 `WEBSITE_MEDIA_REPLACEMENT_CONFLICT`로 전체 롤백한다.

각 초안은 구조를 순회해 원본 자산 ID와 전달 URL 쌍만 대상 값으로 바꾼다. `fieldPath`를 쓰기 위치로 신뢰하지 않고 version 및 영향 집합 확인에만 사용한다. 변경 뒤 기존 문서 validator와 미디어 정규화를 통과시킨다.

한국어 초안은 `website_page.draft_version`을 올리고 `DRAFT` usage를 다시 동기화한다. 영어 초안은 `website_page_translation.draft_version`을 올린다. `IN_REVIEW`, `APPROVED`, `PUBLISHED` 상태였다면 기존 저장 규칙과 같은 승인 무효화 event를 남기고 `DRAFT`로 되돌린다. 이미 공개된 영어 snapshot과 `PUBLISHED` usage는 유지한다.

각 page·locale에는 기존 `DRAFT_SAVED` audit을 남기고 `details.operation`을 `MEDIA_DRAFT_USAGES_REPLACED`로 기록한다. 세부 정보는 원본·대상 자산 ID와 변경 위치 수만 기록한다. 원본 파일 경로나 사용자 입력 파일명은 기록하지 않는다.

## 관리자 흐름

1. 일반 `미디어 선택` 화면에서 활성 원본 자산과 사용 위치를 확인한다.
2. `초안 사용 위치 일괄 교체`를 선택한다.
3. 새 PNG/JPEG 파일, 자산명, 기본 alt를 입력해 새 자산을 업로드한다.
4. 영향 조회 결과에서 한국어·영어별 대상 페이지와 위치 수를 확인한다.
5. 확인창은 공개본·보관 페이지·과거 이력·기존 파일이 바뀌지 않음을 표시한다.
6. `초안 위치 교체하기`를 확정하면 단일 요청을 보낸다.
7. 성공 뒤 카탈로그와 usage를 다시 읽는다. 실패 시 현재 화면을 유지하고 다시 조회할 수 있는 오류를 표시한다.

업로드·영향 조회·교체 중에는 중복 실행을 막는다. 취소·Escape·닫기 뒤 도착한 응답은 현재 화면을 바꾸지 않는다. 대화상자를 닫으면 시작 버튼으로 포커스를 돌린다. 390px에서는 대상 목록을 세로로 표시하고 확정 버튼을 화면 안에서 사용할 수 있어야 한다.

## 오류 처리

- `400`: 같은 자산 선택, 필수 목록 누락, 잘못된 UUID·version·요청 구조
- `401`: 세션 없음 또는 만료
- `403`: `HQ_ADMIN` 외 호출
- `404`: 원본 또는 대상 자산 없음
- `409 WEBSITE_MEDIA_REPLACEMENT_CONFLICT`: 자산 version·상태, 페이지 lifecycle·version, usage 집합 변경
- `409 WEBSITE_MEDIA_REPLACEMENT_EMPTY`: 교체할 활성 초안 usage 없음

충돌 뒤 자동 재시도하지 않는다. 관리자가 영향 범위를 다시 조회하고 재확정한다.

## 데이터와 배포

새 테이블과 컬럼은 필요하지 않다. 기존 `website_page`, `website_page_translation`, `website_media_usage`, 번역 review event, `website_page_audit`을 사용한다. 신규 API를 모르는 이전 관리자와 함께 배포해도 기존 저장·발행 계약은 유지된다.

롤백은 신규 관리자 action과 API 코드를 제거하는 방식이다. 이미 교체된 초안은 정상 draft version으로 남는다. 공개본은 자동 변경되지 않았으므로 데이터 역변환 migration은 만들지 않는다.

## 테스트

### 서버 통합

- 영향 조회가 한국어·영어 활성 초안과 제외된 공개·보관 usage를 정확히 분류한다.
- 확정 시 여러 페이지·locale의 UUID·URL만 바뀌고 alt·캡션은 유지된다.
- 모든 초안 version과 `DRAFT` usage, audit이 갱신된다.
- 영어 검토·승인은 기존 규칙대로 무효화된다.
- 공개본, `PUBLISHED` usage, 과거 snapshot, 다른 자산·페이지, 기존 파일 바이트는 유지된다.
- 오래된 자산/page version, 변경된 usage, 보관 전환, 권한 오류에서 전체 롤백된다.

### 관리자 직접 E2E

- 업로드 전과 최종 확인 전 mutation 요청이 없다.
- 영향 목록과 공개 비변경 안내를 표시한다.
- 성공 뒤 대상 수와 카탈로그 usage가 갱신된다.
- 취소, Escape, 포커스 복귀, 중복 클릭 방지, 늦은 응답 무시를 확인한다.
- 390px에서 목록·확정 동작을 확인한다.

### 직접 검사

- 변경한 서버 통합 테스트만 실행한다.
- 변경한 관리자 E2E와 TypeScript 검사만 실행한다.
- 전체 서버 suite, 전체 브라우저 회귀, 고객 예약 회귀는 범위가 요구하지 않는 한 실행하지 않는다.

## 완료 기준

- 본사 관리자가 영향 범위를 확인한 뒤 여러 활성 초안 위치를 한 번에 교체할 수 있다.
- 확인 전에는 쓰기가 없고, 확인 후에는 영향 조회와 일치한 대상 전체가 성공하거나 전체가 롤백된다.
- alt·캡션·공개본·발행 usage·과거 snapshot·기존 자산·파일이 보존된다.
- 영어 승인 무효화와 페이지 version 충돌 규칙이 기존 단일 저장과 같다.
- 키보드와 390px 흐름, 직접 서버·관리자 테스트와 TypeScript 검사가 통과한다.
- 실제 사용자 페이지·자산에는 검증용 mutation을 보내지 않는다.
