# 직원 객실 운영 흐름 Implementation Plan

**Goal:** 실제 객실 배정부터 체크아웃·청소 완료까지의 서버 운영 흐름을 구현한다.

**Architecture:** Spring Boot `operations` 모듈이 `physical_room`, `reservation_room_assignment`를 관리한다. 모든 변경은 `StaffAccessService`로 지점 권한을 확인하고 JDBC 트랜잭션으로 수행한다.

**Spec:** [직원 객실 운영 흐름 설계](../specs/2026-09-10-staff-operations-design.md)

## Task 1: 실제 객실과 운영 전이

- [x] Flyway에 실제 객실·객실 배정 테이블을 추가한다.
- [x] 배정, 체크인, 체크아웃, 청소 완료 API를 추가한다.
- [x] 실제 객실의 지점·유형·숙박 기간 충돌과 직원 지점 권한을 서버에서 검사한다.
- [x] 체크아웃 후 청소 필요, 청소 완료 후 청결 상태를 통합 테스트한다.

## Task 2: 관리자 운영 화면

- [x] 선택한 지점의 당일 도착·출발·객실 배정·청소 목록 조회 API를 제공한다.
- [ ] 조회 API 결과를 관리자 화면에 표시한다.
- [ ] 지점 직원 UI에서 배정·체크인·체크아웃·청소 완료를 실행한다.
- [ ] UI 접근성·모바일 동작을 확인한다.

## Task 3: 기록 갱신

- [x] 구현 범위·검증·남은 노쇼와 객실 변경 기능을 기록한다.
