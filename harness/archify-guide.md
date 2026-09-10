# Archify 설치와 하네스 적용

## 용도

`archify`는 저장소 근거 또는 확정된 설계를 typed JSON으로 작성한 뒤 아키텍처, workflow, sequence, data flow, lifecycle 다이어그램을 독립 실행형 HTML/SVG로 렌더링하고 검증하는 조건부 스킬입니다.

다음 작업에서 사용합니다.

- 시스템 경계와 런타임 구성 설명
- 주문·재고·출고·반품·정산 같은 업무 흐름 시각화
- API 호출 sequence와 상태 전이 설명
- 데이터 흐름과 신뢰 경계 검토
- Mermaid 초안을 검증 가능한 배포용 다이어그램으로 변환

다이어그램이 필요 없는 일반 기능 구현에는 호출하지 않습니다. 그림은 아키텍처·업무 문서의 근거를 설명하는 산출물이며 원문을 대체하지 않습니다.

## Codex 전역 설치

```powershell
npx.cmd -y skills add tt-a1i/archify --skill archify --agent codex --global --copy --yes
```

설치 위치는 `C:\Users\<사용자>\.agents\skills\archify`입니다. 설치 후 새 Codex 대화에서 available skills에 `archify`가 나타나는지 확인합니다.

현재 검증한 소스는 다음과 같습니다.

- source: `https://github.com/tt-a1i/archify.git`
- commit: `10722002bb8777ecb639d93c49586fae4adf3ae4`
- skill metadata version: `2.17`
- skill folder hash: `8aa4cadc3cde102db4009f23f951cb4cf1e28d61`
- runtime: Node.js 18 이상

## 설치 검증

```powershell
Set-Location "$HOME\.agents\skills\archify"
node bin\archify.mjs doctor
```

Node.js, renderer, schema validator, preview, visual check 항목이 모두 `[ok]`이고 마지막에 `Archify is ready.`가 출력되어야 합니다.

## 프로젝트 적용 절차

1. 확정 문서와 실제 저장소를 읽고 표현할 사실과 아직 확인하지 않은 가정을 구분합니다.
2. `architecture`, `workflow`, `sequence`, `dataflow`, `lifecycle` 중 목적에 맞는 한 형식을 선택합니다.
3. 설치된 스킬의 해당 schema, common schema, 같은 형식 example 하나만 읽습니다.
4. 프로젝트의 `docs/diagrams/<주제>.json`에 typed JSON 원본을 작성합니다.
5. `validate`가 통과한 원본만 `deliver`로 HTML을 생성합니다.
6. HTML과 JSON을 함께 보관하고 관련 Markdown 문서에서 링크합니다.

```powershell
$archify = "$HOME\.agents\skills\archify\bin\archify.mjs"
node $archify validate architecture docs\diagrams\system.json --quality showcase --json
node $archify deliver architecture docs\diagrams\system.json docs\diagrams\system.html --quality showcase --json
```

`showcase` 완료 기준은 artifact checks 9개 통과, composition error 0개, warning 0개입니다. 실패한 `deliver` 결과를 완료 산출물로 기록하지 않습니다.

## 저장소와 보안 경계

- 저장소에는 검증된 JSON 원본, 전달용 HTML/SVG, 생성 근거와 검증 결과만 둡니다.
- 사용자 전역 스킬 사본, installer cache, 업데이트 상태 파일은 프로젝트에 복사하지 않습니다.
- 비밀번호, token, cookie, 내부 주소처럼 공개하면 안 되는 값을 다이어그램에 넣지 않습니다.
- 실제 저장소를 근거로 그릴 때 확인하지 않은 구성 요소나 연결을 사실처럼 추가하지 않습니다.
- 자동 업데이트 알림용 네트워크 확인이 필요 없으면 `ARCHIFY_UPDATE_CHECK_DISABLED=1`을 사용합니다.

## 업데이트

공식 설치 명령을 다시 실행하기 전에 source, version, folder hash 변경을 확인합니다. 업데이트 후 `doctor`와 기존 핵심 다이어그램의 `validate`를 다시 실행하고 하네스 manifest와 스킬 목록의 버전·hash를 갱신합니다.
