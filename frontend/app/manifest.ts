import type { MetadataRoute } from "next";

export const dynamic = "force-static";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "살도 | 오늘 써도 되는 생활비",
    short_name: "살도",
    description: "내 기록으로 오늘의 안심 사용액을 확인하는 개인 가계부",
    start_url: "/",
    display: "standalone",
    background_color: "#f7f7f2",
    theme_color: "#246b50",
    lang: "ko",
    icons: [
      {
        src: "/icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "any",
      },
    ],
    shortcuts: [
      { name: "내역 추가", short_name: "기록", url: "/#transactions" },
      { name: "생활비 내비게이터", short_name: "오늘 한도", url: "/#navigator" },
    ],
  };
}
