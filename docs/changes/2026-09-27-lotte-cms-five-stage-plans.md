# 롯데리조트급 CMS 기준선 감사·5단계 구현 게이트 분리 기록

최종 갱신: 2026-09-30

## 변경 이유

CMS 목표 설계 13절은 콘텐츠 기반, 상세 페이지, 도메인 연결, 편집 운영, 다국어·거버넌스의 의존 순서만 정의했다. 실제 구현 전에 V9~V27과 후속 코드에 이미 들어간 기능을 다시 만들지 않도록 read-only 기준선 감사와 단계별 진입 조건이 필요했다.

## 변경 내용

- 1단계는 정확한 파일과 실행 명령을 가진 read-only 기준선 감사 계획으로 바꿨다. 산출물은 구현/부분 구현/미구현/결함 표이며 product code·DB를 변경하지 않는다.
- 2~5단계는 1단계 산출물과 앞 단계 계약이 승인되기 전에는 실행하지 않는 구현 게이트다. 현재 문서는 범위·데이터 보호·배포 순서를 정의하지만 파일·테스트 단위 구현 계획을 표방하지 않는다.
- 감사 후 실제 격차마다 `writing-plans` 형식의 별도 상세 구현 계획을 작성한다. 새 DB 변경은 실행 시점의 다음 미사용 Flyway 번호를 사용하며 기존 migration을 수정하지 않는다.
- 공통 배포 순서를 DB expand → 호환 reader → 고객·관리자 parser → writer → 발행 활성화로 고정했다.
- 객실 유형 삭제의 CMS 연결 해제 보호는 롤백 대상에서 제외하고, 페이지 이동 후 redirect read를 유지하며, 예약 발행은 승인 시점 불변 snapshot을 참조하도록 선결 위험을 구체화했다.

## 산출물

- [1단계 콘텐츠 기반 기준선 감사](../superpowers/plans/2026-09-27-lotte-cms-step-1-content-foundation-gaps.md)
- [2단계 상세 페이지 구현 게이트](../superpowers/plans/2026-09-27-lotte-cms-step-2-detail-page-foundation-gaps.md)
- [3단계 도메인 연결 구현 게이트](../superpowers/plans/2026-09-27-lotte-cms-step-3-domain-connections-gaps.md)
- [4단계 편집 운영 구현 게이트](../superpowers/plans/2026-09-27-lotte-cms-step-4-editorial-operations-gaps.md)
- [5단계 다국어·거버넌스 구현 게이트](../superpowers/plans/2026-09-27-lotte-cms-step-5-localization-governance-gaps.md)

## 검증 결과

- 상위 설계 13절과 다섯 계획의 상호 링크를 확인했다.
- 1단계에는 실제 저장소 경로, 현재 사용 가능한 Maven/pnpm/Playwright 명령과 기대 결과를 적었다. 내부 링크와 경로의 존재를 정적 검사했다.
- 코드·DB·배포 환경은 변경하지 않았으며 실제 사용자 page, version, media, relation에는 어떤 쓰기 요청도 보내지 않았다.

## 미검증·다음 작업

- 이 변경은 감사·게이트 문서만 정리했다. migration, API, UI, E2E와 rollback rehearsal은 아직 실행하지 않았다.
- 다음 작업은 1단계 read-only 기준선 감사다. 감사 결과를 검토한 뒤 실제 격차만 상세 구현 계획으로 만들고, 각 구현 단계가 끝날 때 별도 변경 기록과 콘텐츠 checksum 증거를 남긴다.
