# 본사 재고·가격 - 재고 일괄 업로드(CSV)

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-24.

## 변경 요약

- `admin-menu-roadmap.md` 2번 `재고·가격`의 **마지막 남은 항목**을 구현했다. 본사가 여러 날짜의 재고를 바꾸려면 `PATCH .../inventory` 대화상자를 날짜 범위마다 다시 띄워야 했고, 대화상자는 **표시된 전체 숙박일을 하나의 같은 총량값으로 덮어버려서** "주말은 6실, 평일은 8실, 공사 주간은 0실" 같은 날짜별 차등을 한 번에 반영할 수 없었다.
- `GET /api/staff/hotels/{hotelId}/inventory/export`가 객실 유형별 일자 재고를 CSV로 내려준다. SELECT만 사용하고 재고를 변경하지 않는다. `from`·`to` 필터를 받고, 92일 초과는 400이다.
- `POST /api/staff/hotels/{hotelId}/inventory/import`가 CSV 본문을 받아 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 지점은 404다.
- **내보낸 CSV를 그대로 다시 올릴 수 있다.** 열 순서가 같고 헤더 행을 무시한다. 이것이 본사가 파일을 수정해서 다시 올리는 기본 흐름이다.
- **총량 열만 반영한다.** `보류`·`확정`·`잔여`·`판매 상태`는 읽기 전용이어서 올려도 무시한다. 업로드가 이 값을 바꾸면 확정 예약과 재고가 어긋난다.
- **한 파일의 모든 행을 한 트랜잭션에 처리한다.** 100행 중 99행만 성공하고 1행이 409로 거부되면 본사가 어느 날짜까지 반영됐는지 알 수 없으므로 전부 성공하거나 전부 실패한다.
- 값 검증은 기존 `PATCH .../inventory`와 같은 기준이다. 총량은 0 이상 1,000 이하, 없는 객실 유형·다른 지점의 유형은 404, 시드되지 않은 일자는 404 `INVENTORY_DAY_NOT_FOUND`, 확정·보류 건수 아래로 내리면 409 `INVENTORY_CAPACITY_CONFLICT`로 **아무것도 바꾸지 않는다**.
- **형식 오류는 400 `INVENTORY_IMPORT_FORMAT`으로 거부한다.** 헤더가 없거나, 열 수가 부족하거나, 날짜·총량이 숫자가 아니거나, 올릴 행이 없으면 400이고 이때도 아무것도 바꾸지 않는다. 행 수 제한은 1,000행이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 파일을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **새 마이그레이션이 없다.** `inventory_command` 표를 재사용하고 CSV 본문 전체가 요청 지문의 입력이 된다.
- 관리자 `/dashboard/inventory`에 `CSV 내보내기`·`CSV 업로드` 버튼을 추가했다. 내보내기는 브라우저에서 파일로 내려주고 UTF-8 BOM을 붙여 엑셀이 한글을 깨지지 않게 읽는다(감사 이력·운영 통계와 같은 패턴). 업로드 대화상자는 파일을 고르면 **본문을 미리 보여주고 올릴 행 수를 센 뒤** 올릴 수 있게 한다. **업로드는 매번 새 멱원 키를 쓴다.** 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.

## 서비스별 변경

### services/api

- `inventory/InventoryCsvService`(신규): CSV를 만들고 읽는다. 열은 `객실 유형 ID`·`객실 유형`·`숙박일`·`총량`·`보류`·`확정`·`잔여`·`판매 상태`·`최대 인원` 9개. 내보내기와 업로드가 같은 열 순서를 써서 내려받은 파일을 그대로 다시 올릴 수 있다. `parse`가 UTF-8 BOM을 떼고, quoted 필드와 쉼표를 포함한 필드를 나누고, 헤더·빈 행을 건너뛴다. `groupedAdjustments`가 기존 `PATCH .../inventory`가 받는 형식으로 행을 모은다. `Row`는 총량만 가지고 `보류`·`확정`은 읽지 않는다.
- `inventory/InventoryImportCommandService`(신규): CSV 본문으로 재고를 바꾼다. `@Transactional`로 한 파일의 모든 행을 묶어서 일부만 바뀌는 일이 없게 한다. 객실 유형별로 `select ... for update`로 쓰기를 직렬화하고, 총량을 내릴 때 확정·보류 건수와 비교한다. `requestHash`가 `staff_id`·지점·**CSV 본문 전체**의 SHA-256 지문을 만들어서 같은 파일을 다시 올리면 같은 지문이 나온다. `findExisting`이 같은 멱원 키 재호출과 같은 내용의 다른 키 재시도를 모두 잡는다.
- `inventory/InventoryImportResponse`(신규): `hotelId`·`totalRows`·`appliedRows`·`skippedRows`·`days`·`created`. `skippedRows`는 헤더·빈 행 수다.
- `inventory/InventoryImportFormatException`(신규): 400으로 매핑.
- `inventory/HotelInventoryController`: `GET .../inventory/export`·`POST .../inventory/import` 핸들러 추가. 내보내기 본문 앞에 `\ufeff` BOM을 붙이고 `Content-Type: text/csv`·`Content-Disposition: attachment`를 내린다. 새 결과는 201, 재호출은 200이다.
- `web/ApiExceptionHandler`: `InventoryImportFormatException` 400 `INVENTORY_IMPORT_FORMAT` 매핑 추가.
- **마이그레이션 변경이 없다.** `inventory_command` 표가 이미 `hotel_id`·`staff_id`·`room_type_id`·`idempotency_key`·`request_hash`를 가지고 있다. 기존 표를 변경하지 않는다.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `InventoryImportResult` 타입, `exportInventoryCsv`·`importInventoryCsv` 추가. `importFailureMessage`가 `INVENTORY_IMPORT_FORMAT`·`INVENTORY_DAY_NOT_FOUND`·403·404·409를 한국어 안내로 매핑한다. 업로드는 본문 앞에 UTF-8 BOM을 붙여서 보낸다.
- `src/components/hotel-admin/inventory-viewer.tsx`: `CSV 내보내기`·`CSV 업로드` 버튼과 업로드 대화상자를 추가했다. 파일을 고르면 본문을 미리 보여주고 `countImportRows`가 헤더·빈 행을 뺀 행 수를 센다. 서버 검증 실패 시 대화상자를 닫지 않는다. 버튼은 본사만 본다.
- `e2e/inventory-viewer.spec.ts`: 19→24건. CSV 내보내기(다운로드 본문에 헤더·객실 유형·날짜 포함)·업로드(미리 보기·행 수 안내·완료 안내·대화상자 닫힘)·409 `INVENTORY_CAPACITY_CONFLICT` 안내·400 `INVENTORY_IMPORT_FORMAT` 안내·지점 직원 버튼 숨김을 추가했다.

### 설계 변경

- [계획](../plans/2026-09-24-hq-inventory-import.md)을 먼저 쓰고 그대로 구현했다. 완료 기준 8개를 모두 충족한다.
- **부분 성공을 허용하지 않기로** 정했다. 전부 성공하거나 전부 실패하는 것이 완료 기준 3번이고, "실패한 행만 무시하고 나머지 적용"은 본사가 어느 날짜가 반영됐는지 알 수 없어서 뺐다.
- **요청 지문에 CSV 본문 전체를 쓰기로** 정했다. 행별로 쪼개면 같은 파일의 일부만 재시도했을 때 지문이 달라져서 멱원이 깨진다.
- **멱원 재호출은 첫 번째 객실 유형의 결과를 돌려주기로** 정했다. `inventory_command`가 유형별로 한 행을 저장하지만 가져오기 응답은 유형별 경계가 없으므로, 재호출이 멱원 키 하나에 대응하는 결과를 돌려주는 것으로 충분하다.
- **빈 파일은 내려주되 올릴 수는 없기로** 정했다. 헤더만 있는 CSV는 본사가 "데이터가 없다"는 것을 바로 알게 하고, 빈 본문을 올리면 400 `INVENTORY_IMPORT_FORMAT`으로 거부한다.
- **390px 검사는 기존 기준을 유지하기로** 정했다. 새 CSV 버튼은 날짜 범위 윗줄로 들어가고 `flex-wrap`으로 감싸서 줄바꿈한다. 390px 테스트는 통과했지만, 그 아래 그리드의 가로 스크롤은 여전히 그리드 자체가 담당한다.

## 검증

- API `mvnw -Dtest=InventoryImportIntegrationTest` **17건**이 종료 코드 0이다. 그 내용은: 내보내기 200(헤더·객실 유형·날짜 포함), 내보낸 CSV 그대로 재업로드 201 `created=true`·총량 유지, 날짜별 다른 총량 2행 201·`totalRows=2 appliedRows=2`, 객실 유형 2종 201, 확정 2건 아래로 내리는 행 1개로 전체 409 `INVENTORY_CAPACITY_CONFLICT`·**첫째 날도 유지**, 읽기 전용 열 무시(보류 9·확정 9·잔여 -5·`STOPPED`를 올려도 총량만 반영), 같은 키 재호출 200 `created=false`·`inventory_command` 1건, 새 키 같은 파일 200 `created=false`·명령 1건, 헤더 없음 400 `INVENTORY_IMPORT_FORMAT`, 숫자 아닌 총량 400, 잘못된 날짜 400, 총량 1001 400, 지점 직원 403·총량 유지, 세션 없음 401, 멱원 키 누락 400, 없는 지점 404, 다른 지점 유형 404.
- 인접 suite `InventoryCommandIntegrationTest` 16건·`InventoryQueryIntegrationTest` 9건·`SalesStatusIntegrationTest` 17건·`AvailabilityIntegrationTest` 6건, 합 **48건**도 종료 코드 0이다. 기존 `PATCH .../inventory`가 깨지지 않았다.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 변경 파일 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `inventory-viewer` **24건**(기존 19 + 신규 5)이 종료 코드 0이다. 신규 5건은: CSV 내보내기(다운로드 본문 확인)·업로드(미리 보기·행 수·완료 안내·대화상자 닫힘)·409 안내(대화상자 유지)·400 형식 안내·지점 직원 버튼 숨김.
- 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.

## 미검증 항목

- **라이브 PostgreSQL 개발 DB에서의 `export`·`import` 호출.** 라이브 재고를 본사가 임의로 바꾸면 고객 가용성에 즉시 영향을 주므로 사용자가 확인한다. compose `api` 재빌드도 하지 않았다.
- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- **엑셀(.xlsx) 형식은 지원하지 않는다.** CSV만 지원한다. 엑셀에서 CSV로 내보낸 파일은 BOM 유무에 관계없이 받는다.
- **두 본사 관리자가 같은 지점의 재고를 동시에 올릴 때의 경합.** 객실 유형별 `for update`와 트랜잭션으로 직렬화하지만 동시 쓰기를 직접 시도하지는 않았다.
- **1,000행 제한이 실제로 거부되는지.** 1,001행짜리 파일을 만들지 않았다. `MAX_ROWS` 검사가 `rows.size() > MAX_ROWS`로 1,001행째에서 `InventoryImportFormatException`을 던지는 것을 코드로 확인했다.
- 390px 모바일에서 업로드 대화상자의 `<pre>` 미리 보기가 넘치지 않는지. `max-h-[32vh] overflow-auto`를 붙였지만 390px에서 직접 열어보지는 않았다.
- 영문 전환. 한국어 단일 언어다.
- 재고를 바꾼 뒤 고객 웹의 `GET /api/availability` 잔여에 반영되는지. 고객 웹은 이 변경에 닫혀 있고, `AvailabilityService`가 같은 `inventory_day`에서 계산하는 것을 확인하지 않았다.
- **AI 예약 컨시어지의 추천이 바뀐 재고를 다루는지 확인하지 않는다.** AI는 가용성을 읽으므로 재고가 바뀌면 오퍼가 따라 바뀌지만 컨시어지 프롬프트 동작을 직접 검증하지는 않는다.
- 2번 `재고·가격` 영역이 완료됐다. 다음은 3번 `직원 계정 관리`의 삭제·본인 계정 수정이다.
