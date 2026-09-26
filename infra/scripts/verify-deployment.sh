#!/usr/bin/env bash
# 배포가 끝난 뒤 서비스가 살아 있는지 확인한다.
# 이 배포는 호스트 포트를 열지 않으므로(포트 충돌 회피),
# 로컬 헬스체크는 docker compose exec 로 컨테이너 안에서,
# 공개 검사는 Cloudflare Tunnel 경유로 한다.
set -euo pipefail

DOMAIN="${HCM_DOMAIN:-hcm.approid.team}"
ADMIN_DOMAIN="${HCM_ADMIN_DOMAIN:-admin-hcm.approid.team}"
COMPOSE_ARGS=(--env-file infra/secrets/compose.env -f infra/compose.zorin.yml)

fail=0

echo "컨테이너 헬스체크 (docker compose exec)"

echo "  api   readiness"
if docker compose "${COMPOSE_ARGS[@]}" --profile tunnel exec -T api \
     wget -q -O - http://127.0.0.1:4080/actuator/health/readiness 2>/dev/null | grep -q UP; then
  echo "  ok api"
else
  echo "  FAIL api 가 준비되지 않았다. Flyway 마이그레이션이 끝날 때까지 기다린다."
  fail=1
fi

echo "  web   root"
if docker compose "${COMPOSE_ARGS[@]}" --profile tunnel exec -T web \
     wget -q -O - http://127.0.0.1:3110/ 2>/dev/null | grep -q "<html"; then
  echo "  ok web"
else
  echo "  FAIL web 가 응답하지 않는다"
  fail=1
fi

echo "  admin root"
if docker compose "${COMPOSE_ARGS[@]}" --profile tunnel exec -T admin \
     wget -q -O - http://127.0.0.1:3111/ 2>/dev/null | grep -q "<html"; then
  echo "  ok admin"
else
  echo "  FAIL admin 가 응답하지 않는다"
  fail=1
fi

echo
echo "터널 경유 (Cloudflare)"

echo "  api    https://${DOMAIN}/api/hotels"
if wget -q -O - "https://${DOMAIN}/api/hotels" 2>/dev/null | grep -Eq '^\['; then
  echo "  ok api via tunnel"
else
  echo "  FAIL 터널이 api 에 연결되지 않았다. Public Hostname 의 URL 이 api:4080 인지 확인한다."
  fail=1
fi

echo "  web    https://${DOMAIN}/"
if wget -q -O - "https://${DOMAIN}/" 2>/dev/null | grep -q "<html"; then
  echo "  ok web via tunnel"
else
  echo "  FAIL 터널이 web 에 연결되지 않았다. 전체 경로(비움) 의 URL 이 web:3110 인지 확인한다."
  fail=1
fi

echo "  admin  https://${ADMIN_DOMAIN}/"
if wget -q -O - "https://${ADMIN_DOMAIN}/" 2>/dev/null | grep -q "<html"; then
  echo "  ok admin via tunnel"
else
  echo "  FAIL 터널이 admin 에 연결되지 않았다. ${ADMIN_DOMAIN} 호스트가 터널에 등록됐는지 확인한다."
  fail=1
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "전체 통과"
else
  echo "실패가 있다"
  echo
  echo "참고: infra/CLOUDFLARE-TUNNEL.md 의 10번(자주 하는 실수)을 확인한다."
fi
exit "$fail"
