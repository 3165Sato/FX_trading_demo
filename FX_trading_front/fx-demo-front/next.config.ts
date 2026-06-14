import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Development-only access for devices on the local network.
  allowedDevOrigins: ["localhost", "127.0.0.1", "192.168.68.58"],
};

export default nextConfig;
