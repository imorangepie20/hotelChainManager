# 고객 웹 Pretendard 적용

## 요청과 범위

고객 웹의 한국어와 영어 전체에 Pretendard를 사용한다. 본문과 UI뿐 아니라 기존 `Playfair Display`를 사용하던 브랜드·제목도 같은 서체로 통일한다. 관리자 화면은 이번 변경 범위에 포함하지 않는다.

## 변경

- 공식 `pretendard` npm 패키지 `1.3.9`를 고객 웹 의존성에 고정했다.
- 공식 가변 다이나믹 서브셋 CSS를 로컬 패키지에서 불러온다.
- 고객 웹의 전역, 브랜드, 제목, 캘린더, 콘텐츠 페이지와 AI 패널 서체를 `Pretendard Variable` 우선 체인으로 통일했다.
- 기존 Google Fonts 요청과 `DM Sans`, `Noto Sans KR`, `Playfair Display` 선언을 제거했다.
- 패키지의 SIL Open Font License 1.1을 따른다.

## 완료 기준과 검증

- 영문 CMS 화면의 전역·브랜드·대표 제목 계산 스타일이 모두 `Pretendard Variable`을 우선 사용한다.
- 한국어와 영어 실제 고객 화면에서 Pretendard 선언과 폰트 로딩 상태를 확인한다.
- 기존 고객 헤더의 데스크톱·반응형 배치와 영문 CMS 공개 동작이 유지된다.
- 고객 웹 production build와 관련 Playwright 회귀 테스트를 통과한다.

검증 결과 영문 CMS 1280px·390px, 한국어 헤더 1186px·1024px, 데스크톱 우측 액션 1440px 회귀 5건이 통과했다. 고객 production build는 2,898개 모듈을 변환했고 Pretendard 가변 서브셋 파일을 산출했다. 실제 1441px 한국어 화면에서 한글·라틴 글리프 로딩과 가로 오버플로 부재를 확인했으며, 영문 콘텐츠 화면에서도 라틴 글리프 로딩과 Pretendard 계산 스타일을 확인했다. 1280px·390px 캡처에서 제목과 본문이 새 서체로 표시되고 레이아웃이 겹치지 않았다.

실제 iOS Safari와 Android Chrome 기기 검증은 수행하지 않았다.
