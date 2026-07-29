import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "살도 — 돈의 흐름이 보이는 가계부",
  description: "수입, 지출, 예산과 연결 자산을 한눈에 관리하는 생활 금융 서비스",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
