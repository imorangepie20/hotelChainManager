# AI 예약 도우미 조건 동기화와 최신 검색 설계

작성일: 2026-09-11

## 목적

AI 예약 도우미가 고객이 이미 예약 바에 입력한 조건을 이어 받아 대화하고, 고객이 추천 조건을 적용할 때 Spring Boot의 최신 재고·가격 검색을 다시 수행하게 한다.

## 변경 전 근거

변경 전 FastAPI/LangGraph는 Spring의 호텔·판매 가능 객실 API만 호출하며 후보의 금액과 잔여 객실을 그대로 보여 주었다. 당시 고객 웹은 AI 패널에 빈 조건 객체를 전달했고, `이 조건으로 객실 검색`은 예약 바 상태만 바꾼 뒤 고객이 다시 검색 버튼을 눌러야 했다. 이 흐름에서는 AI 후보와 실제 예약 후보의 시점이 분리됐다.

## 범위

- 현재 예약 바의 지점 지역, 체크인·아웃, 성인·아동·객실 수, 조식 조건을 AI 패널의 초기 criteria로 전달한다.
- 외부 예약 조건이 바뀌면 이전 AI 후보를 무효화한다.
- `이 조건으로 객실 검색`은 예약 바 상태를 반영하고 같은 조건으로 Spring `GET /api/availability`를 한 번 더 호출한다.
- 최신 응답으로만 객실 카드를 채우고, AI 응답의 후보는 예약 대상으로 재사용하지 않는다. 예약 조건이 바뀌면 진행 중인 이전 availability 요청을 무효화한다.
- 최신 검색 실패 또는 0건이면 기존 객실 카드·선택을 비우고 현재 검색 영역에 안내를 표시한다.

## 제외 범위

- LLM 연결, 정책 RAG/임베딩, 예약 생성·결제 자동화
- FastAPI 또는 Spring의 가격·재고 계산 변경
- AI가 후보를 대신 확정하거나 테스트 결제를 시작하는 흐름
- 새 고객 웹 테스트 프레임워크 도입

## 동작 흐름

1. 고객이 예약 바에서 지점·일정·인원을 지정한다.
2. AI 패널은 그 조건을 `/chat` 요청의 `criteria`로 보낸다. 고객이 "조식 포함"처럼 일부 조건만 말해도 기존 값은 유지된다.
3. FastAPI는 LangGraph와 Spring 조회 결과를 사용해 후보를 설명한다.
4. 고객이 적용을 누르면 패널은 App에 완성 criteria를 넘기고 처리 중 상태가 된다.
5. App은 지역에서 현재 호텔 ID를 찾고 모든 예약 바 state를 같은 criteria로 갱신한다.
6. App은 Spring availability API를 직접 다시 호출하고 그 응답만 객실 카드에 표시한다.
7. 성공하면 패널을 닫고 결과 영역으로 안내한다. 실패하면 패널과 결과 영역 모두 최신 재조회 실패를 알리고, 이전 AI 후보는 예약 대상으로 사용하지 않는다.

## 컴포넌트 계약

`ConciergePanel`은 `criteria`를 입력으로 받고 다음 비동기 callback을 사용한다.

```ts
type ConciergeCriteria = {
  region?: string;
  checkIn?: string;
  checkOut?: string;
  adults?: number;
  children?: number;
  rooms?: number;
  breakfastIncluded?: boolean;
};

onApply: (criteria: ConciergeCriteria) => Promise<boolean>
```

`true`면 최신 Spring 검색이 성공해 패널을 닫고, `false`면 패널은 열어 둔 채 재조회 실패를 알려 준다. App은 합성 form event를 만들지 않고, 선택한 조건을 인자로 받는 검색 함수를 사용한다.

## 진실성·오류 규칙

- AI 후보의 `total`과 `remaining`은 설명용이며 선택 가능한 예약 offer가 아니다.
- 적용 시 API 결과가 없으면 예약 목록은 비고, `조식 포함` 필터 여부에 맞는 안내만 표시한다.
- `/chat` 실패는 직접 검색 안내를 유지한다.
- Spring availability 실패는 기존 결과와 선택을 유지하지 않는다.
- 예약 바를 수동으로 수정하면 패널의 이전 result를 지운다. 따라서 예전 대화의 적용 버튼을 누를 수 없다. 같은 조건을 AI 적용으로 state에 반영하며 시작한 새 availability 요청은 유지하고, 그 밖의 늦은 응답은 카드·선택·안내를 갱신하지 못한다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다.

## 검증 기준

- AI 요청은 현재 예약 바의 criteria를 포함한다.
- 추천 적용은 Spring availability를 한 번 새로 호출하며, 응답 카드에는 그 최신 total·remaining만 나타난다.
- 재조회 실패·0건에서 AI 후보가 예약으로 이어지지 않는다.
- 고객 웹 production build와 안전한 실제 AI 대화·최신 조회를 확인한다. 예약 생성·결제는 이 검증에서 실행하지 않는다.
