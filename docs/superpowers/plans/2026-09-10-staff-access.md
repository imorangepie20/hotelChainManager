# 직원 개발 로그인·지점 권한 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 직원 개발 로그인과 서버 지점 접근 제한의 최소 기반을 만든다.

**Architecture:** Spring Boot의 JDBC 모듈에 직원·세션 테이블과 전용 `staff` 패키지를 추가한다. 고객 예약 토큰 인증은 변경하지 않는다. 인증된 직원 정보는 컨트롤러가 아니라 서비스 경계에서 지점 ID와 대조한다.

**Tech Stack:** Spring Boot 4.1.1, JDBC, PostgreSQL, Flyway, BCrypt.

**Spec:** [직원 개발 로그인·지점 권한 설계](../specs/2026-09-10-staff-access-design.md)

## Task 1: 데이터와 인증 경계

**Files:**
- Create: `services/api/src/main/resources/db/migration/V6__staff_access.sql`
- Create: `services/api/src/main/java/team/hotelchain/staff/*`
- Modify: `services/api/pom.xml`
- Modify: `services/api/src/main/resources/application-dev.yml`
- Create: `services/api/src/test/java/team/hotelchain/staff/StaffAccessIntegrationTest.java`

- [x] 비밀번호 해시·세션 토큰 해시·역할·지점 소속을 저장한다.
- [x] 로그인·현재 세션·로그아웃 API와 개발 전용 계정 환경 변수를 추가한다.
- [x] 본사와 지점 직원의 지점 접근을 서버에서 검사한다.
- [x] 지점 권한 거부와 본사 전체 접근을 통합 테스트한다.

## Task 2: 관리자 로그인 화면

**Files:**
- Modify: `SDTPL_ADM/src/app/*`
- Create: `SDTPL_ADM/src/components/hotel-admin/*`
- Create: `SDTPL_ADM/e2e/staff-login.spec.ts`

- [x] 루트 경로에서 직원 로그인 화면을 보여 주고, 로그인 성공 시 세션을 브라우저에 저장해 운영 대시보드로 이동한다.
- [x] 세션 역할·지점에 맞는 관리자 컨텍스트만 선택할 수 있게 한다.
- [x] 테스트 계정 안내는 개발 환경의 환경 변수 설정 방식으로 표시한다.

## Task 3: 기록 갱신

- [x] 구현·검증·미연결 운영 API를 한국어 변경 기록에 남긴다.
