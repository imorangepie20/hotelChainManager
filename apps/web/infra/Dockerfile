# 빌드 단계. Vite 가 정적 파일을 만든다.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /workspace

# pnpm-workspace.yaml 이 있으므로 corepack 으로 pnpm 을 쓴다.
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
# 정적 파일은 그대로 빌드한다. API 호출은 nginx 가 런타임에 프록시한다.
RUN pnpm run build

# 서비스 단계. 정적 파일을 nginx 로 서비스하고 /api 는 API 컨테이너로 보낸다.
# 같은 도메인에서 서비스하므로 CORS 설정 없이 동작한다.
FROM nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de AS serve
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY infra/nginx.conf /etc/nginx/templates/default.conf.template

# 80·8080 은 쓰지 않는다. 프로젝트 전용 대역인 3110 을 쓴다.
EXPOSE 3110