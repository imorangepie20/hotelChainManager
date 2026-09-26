#!/usr/bin/env bash
# Git 에 비밀값이 들어가지 않았는지 확인한다.
# .gitignore 가 infra/secrets 를 무시하는지 점검한다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail=0

echo "infra/secrets 가 .gitignore 에 있는지 확인"
if git -C "${ROOT}" check-ignore -q infra/secrets/tunnel.env \
   && git -C "${ROOT}" check-ignore -q infra/secrets/compose.env; then
  echo "  ok 무시됨"
else
  echo "  FAIL .gitignore 에 infra/secrets/tunnel.env 와 compose.env 가 필요하다"
  fail=1
fi

echo
echo "스테이지된 파일에 비밀값이 있는지 확인"
if git -C "${ROOT}" diff --cached --name-only \
  | grep -Eq '^infra/secrets/(tunnel|compose)\.env$'; then
  echo "  FAIL 실제 compose.env 또는 tunnel.env 가 스테이지됐다"
  fail=1
else
  echo "  ok 실제 비밀 파일은 스테이지되지 않음"
fi

echo
if git -C "${ROOT}" grep --cached -n -E \
  'TUNNEL_TOKEN=eyJ[A-Za-z0-9_-]{40,}' -- infra; then
  echo "  FAIL 스테이지된 infra 파일에 실제 형태의 터널 토큰이 있다"
  fail=1
else
  echo "  ok 자리 표시자만 있고 실제 형태의 터널 토큰은 없음"
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "전체 통과"
else
  echo "실패가 있다"
fi
exit "$fail"
