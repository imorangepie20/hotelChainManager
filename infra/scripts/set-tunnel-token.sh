#!/usr/bin/env bash
# Cloudflare 터널 토큰을 저장한다.
#
# 터널을 만드는 두 가지 방법:
#
# 1. Cloudflare 대시보드 (추천)
#    Zero Trust -> Networks -> Tunnels -> Create a tunnel
#    터널 이름을 hcm 으로 만들고 Install 화면에서 토큰을 복사한다.
#
# 2. cloudflared CLI
#    cloudflared tunnel login          # 브라우저 인증 (한 번)
#    cloudflared tunnel create hcm    # 터널 ID 와 JSON 파일이 만들어진다
#
# 토큰을 받은 뒤 이 스크립트로 저장한다.
#   ./infra/scripts/set-tunnel-token.sh eyJhIjoi...
#
# 토큰은 infra/secrets/tunnel.env 에 들어가고 .gitignore 가 무시한다.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "사용법: $0 <TUNNEL_TOKEN>"
  echo "예:    $0 eyJhIjoi..."
  exit 2
fi

TOKEN="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SECRETS_DIR="${ROOT}/infra/secrets"
ENV_FILE="${SECRETS_DIR}/tunnel.env"

if [ -z "${TOKEN}" ] || [ "${#TOKEN}" -lt 40 ]; then
  echo "토큰이 너무 짧다. Cloudflare 가 만든 전체 토큰을 붙여 넣는다."
  exit 2
fi

mkdir -p "${SECRETS_DIR}"

cat >"${ENV_FILE}" <<EOF
TUNNEL_TOKEN=${TOKEN}
EOF
chmod 600 "${ENV_FILE}"

echo "저장됨: ${ENV_FILE}"
echo
echo "다음 단계:"
echo "  1. Cloudflare Zero Trust -> Networks -> Tunnels -> hcm -> Public Hostname"
echo "     - hcm.approid.team/api/*  -> api:4080"
echo "     - hcm.approid.team/       -> web:3110"
echo "     - admin-hcm.approid.team  -> admin:3111"
echo "     자세한 등록 절차는 infra/CLOUDFLARE-TUNNEL.md"
echo "  2. docker compose -f infra/compose.zorin.yml --profile tunnel up -d"
echo
echo "팁: 같은 토큰으로 여러 호스트를 한 터널에 묶을 수 있다."
echo "    Path 가 다르면 한 서브도메인에서 여러 서비스를 서비스한다."
