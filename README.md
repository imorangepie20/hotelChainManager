# hotelChainManager

STAY HANEUL — React·Spring Boot 기반 호텔 체인 예약·운영 플랫폼 및 AI 예약 컨시어지.

## 실행

### 고객 웹 (:4000)

```powershell
cd C:\Users\jowoo\hotelChainManager\apps\web
pnpm exec vite --host 127.0.0.1 --port 4000
```

### 관리자 (:4001)

```powershell
cd C:\Users\jowoo\hotelChainManager\SDTPL_ADM
pnpm dev
```

### API·AI (루트 `.env` 그대로)

```powershell
cd C:\Users\jowoo\hotelChainManager
docker compose up -d db
docker compose up -d api
docker compose up -d concierge
```

## 링크

- 고객 웹 http://127.0.0.1:4000
- 관리자 http://127.0.0.1:4001 (루트 `.env`의 `STAFF_HQ_PASSWORD` 계정)
- API http://127.0.0.1:4080/actuator/health
- AI http://127.0.0.1:9000

## 비고

- `pnpm`이 PATH에 없으면 `scripts/pnpm.cmd` 백업 래퍼를 사용한다. 설치 방법은 [pnpm 권한 문제 수정](docs/changes/2026-09-18-pnpm-permission-fix.md)을 따른다.
- 브랜드명·지점·기술 스택과 검증 상태는 [프로젝트 분석](docs/architecture/analysis_results.md)과 [현재 개발 상태](docs/overview/current-development-context.md)를 따른다.
- 루트 `.env`의 비밀값은 저장소에 커밋하지 않는다.
