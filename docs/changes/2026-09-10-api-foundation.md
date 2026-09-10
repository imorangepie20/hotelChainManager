# API 기반과 객실 검색

Spring Boot 4.1.1, Java 21, Maven Wrapper, PostgreSQL 16.15 기반을 구성했다. 개발 DB는 영구 볼륨, 테스트 DB는 tmpfs이며 모두 루프백 포트에만 바인딩한다. API는 4080에서 실행한다.

속초·설악산·제주도 지점과 속초 객실 유형 3개를 개발 프로필에 구성했다. 속초는 실행 날짜부터 90일간 평일·주말 요금과 재고를 제공한다. 설악산·제주도는 지점 목록에는 표시되지만 재고가 없어 판매되지 않는다.

TDD 증거: DB 미기동 시 DatabaseReadinessTest가 연결 거부로 실패했고 DB 기동 후 통과했다. AvailabilityIntegrationTest는 구현 전 컴파일 실패 후 검색 구현으로 5개가 통과했다. 최종 확인은 테스트 6개, readiness UP, 지점 3개, 속초 offer 3개다.

Spring Initializr 응답의 `4.1.1.RELEASE` 좌표는 Maven Central에 없었다. Maven Central에 존재하는 `4.1.1`로 수정했다. DB 중단 readiness 확인은 최초 기본 타임아웃으로 오래 대기해 Hikari 타임아웃을 줄였으며, 반복 검증을 줄이기 위해 DOWN 재확인은 남겨두었다.
