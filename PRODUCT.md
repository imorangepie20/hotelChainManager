# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- 고객은 국내 리조트 지점을 탐색하고 날짜·인원·지역 조건으로 객실과 요금제를 비교해 예약, 테스트 결제, 조회와 취소를 수행한다.
- 지점 직원은 당일 도착·출발, 객실 배정·변경, 체크인·체크아웃·노쇼, 고객 요청과 청소·점검을 관리한다.
- 본사 관리자는 지점·객실 유형·요금·판매 상태·직원 권한·공통 정책, CMS 콘텐츠와 발행 이력을 운영한다.

## Product Purpose

STAY HANEUL은 고객 예약 경험과 호텔 체인 운영을 하나의 플랫폼으로 연결한다. 고객은 신뢰 가능한 실시간 조건으로 숙소를 선택하고, 운영자는 실제 객실 상태와 권한에 따라 예약 및 콘텐츠를 관리한다.

## Positioning

가격·재고·예약 확정은 Spring Boot 예약 도메인이 서버에서 재계산하고 권한을 확인한다. AI 컨시어지와 CMS는 이 결정권을 갖지 않으며, 검증된 조회 결과와 발행된 콘텐츠만 고객 경험에 연결한다.

## Operating Context

- 국내 지점은 속초, 설악산, 제주도이며 속초와 설악산은 별도 지점이다.
- 고객 웹은 지점·객실·오퍼 소개, 예약 검색과 AI 컨시어지를 제공한다.
- 관리자 CMS는 홈·지점 랜딩·일반 페이지의 초안/발행, 구조화 콘텐츠, 미디어 카탈로그, 사용 위치, 이력과 미리보기를 제공한다.
- 한국어와 영어, 모바일 사용을 지원 목표로 둔다.

## Capabilities and Constraints

- 고객 예약·결제·조회·취소와 실제 객실·요금제 비교를 제공한다.
- AI는 예약 조건을 대화로 보완하고 추천 적용 시 Spring availability를 다시 조회한다.
- 외부 이미지·외부 CTA·임의 HTML/script는 CMS 콘텐츠에 허용하지 않는다.
- 객실 유형별 일자 재고와 실제 객실 번호를 분리한다.
- CMS 콘텐츠는 초안과 발행본을 분리하며, 공개 화면은 발행 snapshot과 활성 미디어만 사용한다.

## Brand Commitments

- 데모 브랜드명은 `STAY HANEUL`이며 실제 호텔 정보가 아닌 가상 주소와 콘텐츠를 사용한다.
- 관리자 UI는 제공된 SDTPL_ADM의 기존 테마 구조와 공통 컴포넌트를 우선 활용한다.
- 고객 웹은 고객 경험에 맞는 별도 디자인을 유지한다.

## Evidence on Hand

- 제품 범위와 현재 기능은 `docs/overview/project-brief.md`, `docs/overview/current-development-context.md`에 기록한다.
- CMS 설계와 현재 API 경계는 `docs/architecture/lotte-resort-level-cms-functional-design.md`, `docs/architecture/cms-functional-specification.md`에 기록한다.
- 실제 고객·호텔·가격·후기·성과 수치는 제공되지 않았으며, UI에서 사실처럼 만들지 않는다.

## Product Principles

1. 예약의 가격·재고·확정 판단은 항상 서버의 검증 결과를 따른다.
2. 고객은 지점과 객실을 이해하고 예약 조건을 통제할 수 있어야 한다.
3. 운영자는 실제 객실 상태와 역할 권한 안에서만 업무를 처리한다.
4. CMS 콘텐츠는 구조화하고, 초안과 공개본의 차이를 안전하게 관리한다.
5. 고객 웹과 관리자 도구는 각자의 사용 장면에 맞게 명확하고 접근 가능해야 한다.

## Accessibility & Inclusion

- 고객 웹과 관리자 UI는 키보드 조작, 명확한 field label과 오류 복구 안내, 모바일 동작을 고려한다.
- 갤러리·표·대화상자 등 기능성 컴포넌트는 기존 접근성 동작을 유지하거나 검증한다.
