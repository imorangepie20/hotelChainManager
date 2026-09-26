# 라이브 관리자 직원 로그인 계정 시드

> 날짜: 2026-09-26

## 문제

`https://admin-hcm.approid.team/login`에서 `hq@hotel-chain.local`로
로그인하면 `직원 인증이 필요합니다.`가 표시됐다. 관리자 UI와 API
프록시는 응답했지만 서버의 `STAFF_DEV_ENABLED=false`였고 네 직원
비밀번호 환경 변수도 비어 있었다. DB에는 `@hotel-chain.local` 직원
계정이 한 건도 없어서 어떤 비밀번호로도 로그인할 수 없는 상태였다.

## 조치

- 사용자가 서버의 `infra/secrets/compose.env`에 네 직원 비밀번호를 직접
  저장하고 `STAFF_DEV_ENABLED=true`로 바꿨다. 비밀번호 값은 터미널 출력,
  문서, 저장소에 기록하지 않았다.
- API 컨테이너만 강제 재생성하고 health가 통과할 때까지 기다렸다.
- `StaffDevAccountInitializer`가 다음 6개 활성 계정을 만들었다.
  - `hq@hotel-chain.local` (`HQ_ADMIN`)
  - `editor@hotel-chain.local` (`HQ_EDITOR`)
  - `publisher@hotel-chain.local` (`HQ_PUBLISHER`)
  - 속초·설악산·제주 지점 직원 계정 (`BRANCH_STAFF`)

## 검증

- API 컨테이너 내부 `POST /api/staff/sessions`: 설정된 HQ 비밀번호로 성공
- 공개 관리자 도메인
  `POST https://admin-hcm.approid.team/api/staff/sessions`: 성공
- `https://admin-hcm.approid.team/`: HTTP 307로 `/login` 이동
- 비밀번호와 발급된 세션 토큰은 출력하지 않았다.

## 운영 주의

- dev 프로필의 initializer는 API가 시작될 때 환경 변수 비밀번호로 개발
  계정 해시를 다시 맞춘다. 운영 비밀번호 변경 절차를 별도로 만들기 전에는
  `compose.env`가 이 배포의 개발 계정 비밀번호 원본이다.
- 관리자 도메인의 Cloudflare Access 정책 적용 여부는 아직 확인하지 않았다.
