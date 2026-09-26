import type { NextConfig } from "next";

// API_PROXY_TARGET 은 빌드 시점에 평가돼서 routes-manifest.json 에
// 구워진다. Next.js rewrites 의 destination 은 런타임 환경변수를
// 읽지 않으므로 컨테이너 배포에서는 Dockerfile 의 build arg 로
// http://api:4080 을 넣어야 한다.
//
// 로컬 개발은 127.0.0.1:4080 이 기본값이다.
const apiTarget = process.env.API_PROXY_TARGET ?? "http://127.0.0.1:4080";
// 컨테이너 배포에서 도우미가 같은 네트워크에 없으면 127.0.0.1:9000 은
// 관리자 컨테이너 자신을 가리킨다. 빈 build arg 를 쓰면 rewrite 자체를
// 만들지 않아서 /concierge/* 가 404 로 떨어진다.
const conciergeTarget = process.env.CONCIERGE_PROXY_TARGET ?? "http://127.0.0.1:9000";

const nextConfig: NextConfig = {
  output: "standalone",
  allowedDevOrigins: ["127.0.0.1"],
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${apiTarget}/api/:path*` },
      ...(conciergeTarget
        ? [
            {
              source: "/concierge/:path*",
              destination: `${conciergeTarget}/:path*`,
            },
          ]
        : []),
    ];
  },
};

export default nextConfig;
