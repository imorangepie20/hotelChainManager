# 미디어 초안 사용 위치 일괄 교체 변경 기록

## 배경

같은 미디어를 여러 페이지 초안에서 교체할 때 위치마다 수동 저장하면 누락과 부분 반영 위험이 있었다. 공개 상태를 건드리지 않으면서 현재 활성 초안 전체를 한 번에 바꾸는 본사 관리자 흐름이 필요했다.

## 구현

- `HQ_ADMIN` 전용 영향 조회와 확정 API를 추가했다.
- 새 PNG/JPEG 업로드 자산을 대상으로 활성 `HOME_PAGE`, `HOTEL_LANDING`, `CONTENT_PAGE`의 한국어·영어 `DRAFT` usage만 영향 목록에 포함한다.
- 확정 요청은 원본·대상 자산 version과 영향 조회에서 받은 모든 초안 위치·version을 전달한다. 서버는 자산을 고정 순서로 잠그고 현재 집합과 정확히 비교한 뒤 한 트랜잭션에서 적용한다.
- 문서에서는 자산 UUID와 전달 URL만 바꾼다. 페이지별 alt·caption·블록 순서를 유지하고 usage를 다시 동기화한다.
- 공개본·발행 usage·과거 snapshot·보관 페이지 초안·기존 자산과 파일은 변경하지 않는다.
- 영어 초안 교체는 기존 저장 규칙대로 검토·승인을 `DRAFT`로 무효화하고 `APPROVAL_INVALIDATED` event를 남긴다.
- DB 제약에 이미 허용된 `DRAFT_SAVED` audit action을 사용하고 `details.operation`에 `MEDIA_DRAFT_USAGES_REPLACED`를 기록했다. 새 migration이나 파일 덮어쓰기는 추가하지 않았다.
- 관리자 대화상자에서 새 자산 업로드, 한국어·영어 위치와 제외된 발행·보관 위치 수 확인, 명시적 확정, 충돌 안내를 제공한다. 취소와 좁은 화면에서도 기존 선택기로 포커스를 돌려준다.

## 검증

- 서버: `WebsiteMediaIntegrationTest` 19건 통과, 실패 0, 오류 0, 건너뜀 0.
- 관리자: 일괄 교체 핵심·취소·충돌·390px Chromium Playwright 4건 통과.
- 관리자 TypeScript: `pnpm exec tsc --noEmit` exit code 0.
- 서버 테스트는 영향 조회의 읽기 불변성, 활성 한국어·영어 초안 교체, 발행본·snapshot·alt·기존 파일 보존, 영어 승인 무효화, stale 확정의 사전 전체 거부, 본사 관리자 외 역할 거부를 확인한다.

## 미검증과 다음 작업

- 실제 사용자 CMS 자산이나 페이지에는 업로드·교체 mutation을 보내지 않았다.
- 전체 서버 suite, 전체 관리자 브라우저 회귀, 고객 예약·결제 회귀는 실행하지 않았다.
- 객체 저장소, CDN, 이미지 variant·변환은 후속 범위다.
