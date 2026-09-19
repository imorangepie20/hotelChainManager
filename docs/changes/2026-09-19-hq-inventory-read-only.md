# 본사 재고·가격 읽기 전용 조회

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-19.

## 변경 이유

- `admin-menu-roadmap.md` 2번 `재고·가격`이 다음 대상이었다. `inventory_day`는 예약·만료·변경 작업이 내부적으로만 변경하고 본사가 객실 유형별 일자 재고를 직접 볼 수단이 없었다.
- 1번 메뉴와 같은 순서로 읽기 전용 조회를 먼저 붙였다. 본사가 조정 대상을 식별하려면 먼저 현재 재고를 봐야 하기 때문이다. 재고 조정·판매 중지 구간 설정·일괄 업로드는 쓰기 동작이므로 다음 작업으로 뺐다.

## 구현 범위

### 서버 (services/api)

- `inventory/InventoryQueryService`: SELECT만 사용한다. `room_type` JOIN `inventory_day`로 객실 유형별 일자 재고를 내보낸다. 각 행은 `stayDate`·`capacity`·`held`·`confirmed`·`remaining`(`capacity - held - confirmed`)을 가진다. 재고가 없는 일자는 빈 행으로 채우지 않고 실제 있는 일자만 노출한다.
- `inventory/HotelInventoryController`: `GET /api/staff/hotels/{hotelId}/inventory`. 기존 카탈로그 컨트롤러와 같은 권한·지점 확인 순서를 쓴다. 권한 실패가 403, 지점 부재가 404, 세션 부재가 401.
- `from`·`to`는 ISO 날짜. 생략하면 오늘부터 14일. 역순이면 400, 92일을 초과하면 400.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`의 `getStaffInventory`·`InventoryView`·`RoomTypeInventory`·`InventoryDay`.
- `components/hotel-admin/inventory-viewer.tsx`: 지점 선택기, 시작일·종료일 입력, 7일·14일·30일 기간 프리셋, 객실 유형(행) × 숙박일(열) 그리드. 모든 객실 유형을 한 화면에 비교한다. 셀의 숫자는 잔여 수이고 0이면 `매진`으로 표시하며, 해당 일자에 재고가 없으면 `-`로 표시한다. 주말 열은 배경을 구분하고, 객실 유형 열은 sticky로 좌측에 고정한다. `HQ_ADMIN`만 조회·새로고침이 가능하고 다른 역할은 본사 전용 안내를 본다.
- 첫 버전은 객실 유형을 select로 한 번에 하나씩만 볼 수 있었고 14일이 세로로 쌓여 흐름이 보이지 않았다. 사용자가 이 점을 불편하다고 해서 그리드로 바꿨다.
- 객실 유형 선택을 파생값으로 처리한 부분은 그리드에서 더 이상 선택할 필요가 없어졌다.
- `app/(dashboard)/dashboard/inventory/page.tsx`.
- `nav.ts`의 `catalogGroup`에 `재고·가격`을 추가했다. `HQ_ADMIN`에만 노출된다.

### 테스트

- `InventoryQueryIntegrationTest` 9종: 본사 200(`remaining` 계산 포함), 지점 타 지점 403, 지점 본인 지점 200, 세션 부재 401, 없는 지점 404, 역순 400, 92일 초과 400, 재고 없는 기간 빈 목록, 기본 범위.
- `SDTPL_ADM/e2e/inventory-viewer.spec.ts` 6종: 메뉴 노출 권한, 그리드의 객실 유형·매진 표시, 모든 유형이 한 화면에 표시됨, 기간 프리셋 재조회, 지점 직원 안내, 장애 안내.
- `SDTPL_ADM/e2e/inventory-mobile.spec.ts` 1종: 390×844 뷰포트에서 가로 넘침이 없음.

## 자동 검증

- API `mvnw -Dtest=InventoryQueryIntegrationTest,HotelCatalogQueryIntegrationTest test` **16건** 종료 코드 0.
- API `mvnw compile`·`test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 `hotel-catalog.tsx`·`ai-operations.tsx`와 같은 패턴).
- Playwright: `inventory-viewer` 6건·`inventory-mobile` 1건·`hotel-catalog` 5건·`ai-operations` 4건·`admin-navigation` 2건 **18건** 종료 코드 0(20.8s).
- concierge Python `pytest` 27건 종료 코드 0(이번 변경과 무관, 회귀).
- 라이브: API 이미지 재빌드 뒤 health 200. 본사 세션으로 속초 재고가 200으로 내려왔고 `capacity=12, confirmed=2, remaining=10`으로 데모 데이터와 일치했다. 지점 타 지점 403, 없는 지점 404, 역순 기간 400, 세션 없음 401. 관리자 4001의 `/dashboard/inventory` 200.

## 미검증 항목

- 재고 조정(일자 `capacity` 변경), 판매 중지 구간 설정, 재고 일괄 업로드. 쓰기 동작은 다음 작업이다.
- 가격(요금) 변경. 1번 메뉴의 쓰기 범위와 겹친다.
- 재고 부족 알림·자동 가격 조정. 운영 범위가 아니다.
- 영문 전환. 메뉴·재고 안내는 한국어 단일 언어다.
