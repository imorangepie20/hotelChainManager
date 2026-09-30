# 지점 랜딩 추천 즐길 거리 상세 페이지 연결

날짜: 2026-09-30
상태: **리뷰 Important 수정·최종 대상 검증 완료, 커밋·푸시·Zorin 배포 진행**
계획: [지점 랜딩 추천 즐길 거리 구현 계획](../plans/2026-09-30-landing-recommended-experiences.md)

## 변경 이유

기존 지점 랜딩의 `experiences`는 관리자가 제목·설명·분류를 직접 입력해 실제 상세 페이지와 연결되지 않았다. 고객이 카드를 눌러 상세 정보로 이동할 수 없고, 상세 페이지의 발행 상태·지점 소유권·언어별 주소와 카드 정보가 서로 어긋날 수 있었다.

추천 카드를 상세 페이지 관계로 바꾸고 랜딩 발행 시 카드 값을 snapshot으로 만들어, 상세 페이지 초안 변경이나 재발행이 이미 발행된 랜딩을 암묵적으로 바꾸지 않게 했다.

## API

- `HOTEL_LANDING`의 `draftConnections`·`publishedConnections`에 순서 있는 `MANUAL_CARD` 관계를 저장한다.
- 같은 지점의 활성 `DINING`, `FACILITY`, `EXPERIENCE` 상세 페이지만 허용한다.
- 최대 3개, 중복 없는 대상, 0부터 이어지는 `displayOrder`를 검증한다. 객실 유형·대상 지점 연결은 랜딩에서 거부한다.
- 초안에는 미발행 관계를 보존할 수 있지만 랜딩 발행은 상세 페이지 발행본이 없으면 거부한다.
- 한국어 랜딩 발행 시 상세 페이지 한국어 발행본의 유형·제목·요약·대표 이미지·발행 경로를 `recommendedExperiences`에 저장한다.
- 영어 랜딩 발행 시 각 상세 페이지의 영어 발행본과 영어 발행 경로로 별도 snapshot을 만든다. 한국어 snapshot이나 경로를 재사용하지 않는다.
- 발행 뒤 관계 초안이나 상세 페이지를 바꿔도 기존 랜딩의 `publishedConnections`와 `recommendedExperiences`는 랜딩을 다시 발행하기 전까지 유지된다.
- 기존 `/api/staff/web-content/hotels/{hotelId}` 관리 계약에도 연결 요청·응답을 추가했다. 연결 필드를 보내지 않는 기존 호출은 현재 초안 관계를 보존한다.

## 관리자

- 한국어·영어 지점 랜딩의 자유 입력 경험 카드 편집을 발행 상세 페이지 선택기로 교체했다.
- 콘텐츠 참조 카탈로그에서 현재 지점의 `DINING`, `FACILITY`, `EXPERIENCE`만 표시한다.
- 카탈로그 API는 `locale=ko|en`을 받으며 각 언어 발행본의 제목·경로를 반환한다. 늦은 한국어 응답이 영어 선택기를 덮어쓰지 않도록 요청 세대를 검사한다.
- 최대 3개를 체크박스로 선택하고 위·아래 버튼으로 순서를 바꾼다. 한도에 도달하면 선택되지 않은 항목을 비활성한다.
- 키보드 Space 선택과 Enter 순서 이동을 390px viewport에서 검증했다.
- 기존 `experiences` 값은 삭제하지 않고 저장 content에 남겨 과거 발행본과 rollback 호환성을 유지한다.
- 카탈로그에서 사라진 보관 페이지도 선택 목록의 독립 제거 버튼으로 삭제하고 남은 추천을 저장할 수 있다. 한국어·영어 키보드 제거를 검증했다.

## 고객 웹

- 유효한 `recommendedExperiences`가 있으면 기존 `experiences`보다 우선한다.
- UUID, 허용 유형, 최대 3개, 안전한 내부 경로, 안전한 이미지 전달 경로를 검사한 뒤 카드 모델로 변환한다.
- 추천 카드는 대표 이미지·유형·제목·요약·상세 보기 링크를 렌더링한다. 한국어와 영어 모두 snapshot에 포함된 각 언어 경로를 그대로 사용한다.
- 추천 snapshot이 없거나 손상됐으면 기존 자유 입력 `experiences` 카드로 복귀하며, 한국어 fallback 카드의 기존 예약 CTA도 유지한다.

## 테스트 우선 근거

- API 한국어 테스트는 추천 관계와 발행 snapshot 지원 전 실패한 뒤 구현 후 통과했다.
- API 영어 테스트는 영어 상세 발행 snapshot을 만들지 못해 실패한 뒤, 영어 발행본·영어 경로 해석을 추가해 통과했다.
- legacy 관리 계약 테스트는 연결을 받는 서비스 overload와 응답 필드가 없어 컴파일 실패한 뒤 통과했다.
- 관리자 Playwright는 `추천 즐길 거리` 선택기가 없어 실패한 뒤 한국어·영어 payload와 순서 저장을 검증했다.
- 고객 parser 테스트는 기존 `experiences`를 반환해 실패했고, 고객 Playwright는 추천 카드가 `#booking`으로 연결돼 실패한 뒤 상세 링크·이미지를 검증했다.

## 검증 결과

- API: Docker Maven의 `-Dtest=WebsitePageIntegrationTest,WebsiteTranslationIntegrationTest,WebContentIntegrationTest test` 종료 코드 0. Surefire XML 집계 **58건 통과**(34·19·5), 실패·오류·건너뜀 0건.
- 관리자: `npx tsc --noEmit` 통과, `website-content-editor.spec.ts --workers=4 --max-failures=1` **68건 통과**. 390px 키보드 추천 선택·순서 이동, 한국어·영어 stale 추천 제거, 언어 응답 경합을 포함한다.
- 관리자 변경 파일 ESLint 종료 코드 0, 오류 0·경고 6건(effect 상태 갱신·의존성, 이미지 요소, 테스트 미사용 변수). 초기 플러그인 누락은 최종 재실행에서 더 이상 재현되지 않았다.
- 고객: `content-page.test.ts` 통과, production build 통과, `destination-hero-carousel.spec.ts` **11건 통과**.
- `git diff --check` 통과.

## 독립 리뷰 후 보완

- Important 3건을 수정하고 부모 작업 트리의 최종 대상 테스트로 재검증했다. 별도 독립 재리뷰 승인은 수행하지 않았다.
- 랜딩 `recommendedExperiences`의 업로드 이미지도 언어·초안/발행 상태별 media usage에 포함한다. 상세 페이지 이미지를 교체·재발행해도 기존 랜딩 snapshot 이미지의 보관을 차단한다.
- 영어-only 발행 상세가 영어 카탈로그에 포함되고 한국어-only 발행 상세는 제외되는 API 회귀를 추가했다.
- 카탈로그에서 사라진 추천의 제거·저장 UI 회귀를 양 언어에 추가했다.

## 미검증·다음 작업

- 실제 운영 DB에 추천 관계를 저장하거나 랜딩을 발행하지 않았다.
- 사용자 요청에 따라 검증된 소스를 main에 커밋·푸시하고 clean HEAD archive로 기존 Zorin 서버에 배포한다. 기존 CMS 콘텐츠는 자동 재발행하지 않는다.
- 배포 후 서비스 health와 공개 URL 응답을 확인한다. 운영 관리자 선택·저장·발행 및 실제 고객 카드 클릭은 사용자가 브라우저에서 확인한다.
- 전체 API·관리자 회귀, Firefox/WebKit과 고객 영어 추천 링크·손상 fallback 브라우저 회귀는 이번 최종 대상 검증 범위 밖이다.
- Minor 3건(이미지 alt/제목 중복 낭독, 제목 기반 React key, 고객 영어 추천 브라우저 회귀 부족)은 후속으로 남겼다.

## 롤백

- 관리자 선택기를 기존 자유 입력 UI로 되돌려도 기존 `experiences` 데이터는 보존돼 있다.
- 고객 웹에서 `recommendedExperiences` 우선 처리를 제거하면 기존 자유 입력 카드로 즉시 복귀한다.
- DB 마이그레이션은 없으며 새 관계와 snapshot은 기존 JSONB·연결 테이블을 사용한다.
