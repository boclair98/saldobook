import { ShieldCheck } from "lucide-react";

export function OpenBankingGuide() {
  return (
    <>
      <ol className="connect-steps">
        <li><span>1</span><div><b>은행과 계좌 선택</b><small>연결하려는 본인 명의 계좌를 선택해요.</small></div></li>
        <li><span>2</span><div><b>본인인증 및 조회 동의</b><small>금융결제원 화면에서 직접 인증하고 동의해요.</small></div></li>
        <li><span>3</span><div><b>거래 자동 불러오기</b><small>동의한 계좌의 잔액과 거래만 동기화해요.</small></div></li>
      </ol>
      <div className="security-note banking-security">
        <ShieldCheck size={15} />
        계좌 비밀번호는 살도가 받거나 저장하지 않으며, 연결은 언제든 해제할 수 있습니다.
      </div>
      <details className="operator-guide">
        <summary>서비스 운영 준비가 궁금한가요?</summary>
        <p>금융결제원 통합포털에서 이용기관 신청, 개발·보안 점검, 계약, 운영키 발급과 리다이렉트 URI 등록을 완료해야 실제 계좌 연결이 열립니다.</p>
        <a href="https://openapi.kftc.or.kr/service/openBanking" target="_blank" rel="noreferrer">오픈뱅킹 공식 안내 보기</a>
      </details>
    </>
  );
}
