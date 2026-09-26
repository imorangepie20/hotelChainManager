# 빌드 단계. Next.js standalone 출력을 만든다.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /workspace

# rewrites 의 destination 이 빌드 시점에 매니페스트에 구워지므로
# 컨테이너 배포에서는 build arg 로 API 주소를 넣는다.
ARG API_PROXY_TARGET=http://127.0.0.1:4080
ENV API_PROXY_TARGET=${API_PROXY_TARGET}

# NEXT_PUBLIC_* 도 빌드 시점에 구워진다. 고객 웹 공개 도메인이 없으면
# 관리자의 미디어 미리보기가 로컬 개발 주소를 가리키게 된다.
ARG NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN=http://127.0.0.1:4000
ENV NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN=${NEXT_PUBLIC_CUSTOMER_WEB_ORIGIN}

# 빈 값을 주면 /concierge rewrite 를 아예 만들지 않는다. 이 배포에
# 도우미가 없으면 502 대신 404 가 돌아간다.
ARG CONCIERGE_PROXY_TARGET=http://127.0.0.1:9000
ENV CONCIERGE_PROXY_TARGET=${CONCIERGE_PROXY_TARGET}

RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
# output: standalone 이 .next/standalone 과 .next/static 을 만든다.
# rewrite 의 목적지는 런타임 환경변수 API_PROXY_TARGET 으로 정해진다.
RUN pnpm run build

# 서비스 단계. standalone 서버가 /api rewrite 를 환경변수로 받아
# 런타임에 API 컨테이너를 가리킨다.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS serve
WORKDIR /app

ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
# 3000 은 쓰지 않는다. 프로젝트 전용 대역인 3111 을 쓴다.
ENV PORT=3111

COPY --from=build /workspace/.next/standalone ./
COPY --from=build /workspace/.next/static ./.next/static
COPY --from=build /workspace/public ./public

EXPOSE 3111
CMD ["node", "server.js"]
