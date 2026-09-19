# AI 도우미 Gemini LLM 연결 설계

## 목적

정해진 한국어 정규식으로만 조건을 추출하던 도우미에 Gemini를 연결한다. LLM이 조건 해석을 담당하고, Spring Boot 예약 API가 반환한 객실·가격·잔여 수는 여전히 API 결과만 사용한다.

## 경계 (core-principles.md 준수)

- LLM은 `criteria` 해석만 한다. 가격·빈 객실을 생성하지 않고 예약 생성·결제·취소를 실행하지 않는다.
- LLM 출력은 신뢰하지 않는다. 해석 결과는 정책 가드(`assert_no_forbidden_fields`)를 통과해야 조회로 넘어간다.
- LLM이 비어 있거나 형식이 맞지 않으면 정규식 폴백으로 내려간다. 외부 키 없이도 도우미가 동작한다.
- 비밀값은 루트 `.env`에만 두고 저장소에 넣지 않는다.

## 완료 기준

1. `POST /chat`가 `GOOGLE_API_KEY`가 있을 때 Gemini로 `region`·`checkIn`·`checkOut`·`adults`·`children`·`rooms`·`breakfastIncluded`를 추출한다.
2. 키가 없거나 LLM 호출이 실패하면 기존 정규식 추출로 동작한다. 응답에 거짓 정보가 섞이지 않는다.
3. LLM 출력에 `payment`·`confirm`·`reservationId`·`roomId`가 들어가면 400으로 거부된다.
4. 추천 후보의 금액·잔여 수는 Spring API 응답과 바이트 단위로 일치한다.
5. Docker 재빌드 뒤 실제 Gemini 호출로 위 1~4를 확인한다.

## 구현 범위

- `services/concierge/requirements.txt`에 `google-genai`를 추가한다.
- `services/concierge/app/llm.py`를 새로 작성한다. Gemini 호출, JSON 스키마 검증, 폴백을 담당한다.
- `services/concierge/app/graph.py`의 `extract_conditions`를 LLM 우선·정규식 폴백으로 바꾼다.
- `compose.yaml`의 `concierge` 서비스에 `GOOGLE_API_KEY`를 전달한다.
- `services/concierge/tests/test_llm.py`로 스키마 검증·폴백을 검증한다.

## 미구현 항목

- LLM 대화 이력의 영구 보관. `ConciergeState`는 요청 단위다.
- LLM 지연·비용·할루시네이션의 지속 측정. 일회성 라이브 호출로만 확인한다.
- 영문 입력 지원. 한국어 입력만 다룬다.
