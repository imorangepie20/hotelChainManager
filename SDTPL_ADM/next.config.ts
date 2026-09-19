import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: ["127.0.0.1"],
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://127.0.0.1:4080/api/:path*" },
      { source: "/concierge/:path*", destination: "http://127.0.0.1:9000/:path*" },
    ];
  },
};

export default nextConfig;
