# 살도 운영 가이드

살도는 Java 21, Spring Boot, Spring Data JPA, PostgreSQL, TypeScript와 Next.js로 구성된 사용자별 가계부입니다. Google·카카오 OAuth로 로그인하며, 모든 조회·저장·삭제 API는 서버 세션과 데이터 소유자를 함께 확인합니다.

운영 환경은 정적 프런트엔드를 CDN 캐시로 제공하고, API는 PostgreSQL 커넥션 풀·응답 압축·graceful shutdown을 사용합니다. `DB_POOL_MAX`, `SERVER_TOMCAT_THREADS_MAX` 등 환경 변수는 실제 동시 접속과 DB 용량을 측정한 뒤 조정합니다.

## 현재 제공 기능

- Google·카카오 소셜 로그인과 JDBC 영속 세션
- 사용자별 데이터 격리
- 수입·지출 등록과 삭제
- 월 예산 저장 및 사용률 계산
- 거래 검색, 유형 필터, CSV 내보내기
- 카테고리 분석과 최근 6개월 추이
- 금융결제원 오픈뱅킹 OAuth, 계좌 목록, 잔액·거래 동기화
- AES-256-GCM 토큰 암호화와 중복 거래 방지

## 오픈뱅킹을 운영 환경에서 여는 순서

1. [금융결제원 통합포털](https://openapi.kftc.or.kr/service/openBanking)에서 오픈뱅킹 이용기관을 신청합니다.
2. 서비스 모델, 이용 API, 개인정보 처리 흐름과 보안 체계를 제출합니다.
3. 테스트 이용 승인을 받은 뒤 개발용 Client ID와 Secret으로 계좌등록·조회 API를 검증합니다.
4. 보안 점검과 계약을 완료하고 운영 Client ID와 Secret을 발급받습니다.
5. 금융결제원에 운영 리다이렉트 URI를 등록합니다.
6. 배포 환경에 아래 값을 비밀 환경 변수로 설정하고 재배포합니다.

```text
PUBLIC_URL=https://saldobook.coders.kr
OPEN_BANKING_CLIENT_ID=발급값
OPEN_BANKING_CLIENT_SECRET=발급값
OPEN_BANKING_REDIRECT_URI=https://saldobook.coders.kr/api/openbanking/callback
OPEN_BANKING_USE_ORG_CODE=이용기관코드 10자리
OPEN_BANKING_AUTHORIZE_URL=금융결제원 운영 인가 URL
OPEN_BANKING_TOKEN_URL=금융결제원 운영 토큰 URL
OPEN_BANKING_API_BASE_URL=금융결제원 운영 API v2.0 기본 URL
TOKEN_ENCRYPTION_KEY=Base64로 인코딩한 32바이트 무작위 키
```

## 실제 사용자가 보는 연결 흐름

1. 살도 로그인
2. `계좌 연결` 선택
3. 연결할 은행과 본인 명의 계좌 선택
4. 금융결제원 화면에서 본인인증 및 조회 동의
5. 살도로 돌아와 연결된 계좌 확인
6. 동의한 범위의 잔액·거래만 자동 동기화
7. 설정에서 계좌 연결 해제 또는 동의 철회

살도는 계좌 비밀번호를 입력받거나 저장해서는 안 됩니다. 액세스·리프레시 토큰은 평문 DB 저장을 금지하고 서버 비밀키로 암호화해야 합니다. 실제 운영 전에는 개인정보처리방침, 이용약관, 동의 철회, 탈퇴·파기, 장애 대응과 금융 API 감사 로그를 준비해야 합니다.
