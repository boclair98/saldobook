import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "살도 | 오늘 써도 되는 생활비",
  description: "내가 기록한 돈의 흐름으로 오늘의 안심 사용액과 월말 계획을 확인하는 개인 가계부.",
  keywords: ["가계부", "생활비", "예산 관리", "지출 기록", "개인 금융"],
  icons: {
    icon: "/icon.svg",
    apple: "/icon.svg",
  },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
