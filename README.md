# 살도 (Saldobook)

Google·카카오 로그인과 금융결제원 오픈뱅킹 연동을 지원하는 사용자별 가계부입니다.

- 운영 URL: [https://saldobook.coders.kr](https://saldobook.coders.kr)
- 현재 금융 연동 상태: 금융결제원 **테스트베드**
- 백엔드: Java 21, Spring Boot 3.4, Spring Data JPA, Flyway
- 프런트엔드: TypeScript, Next.js 16, React 19
- 데이터베이스: PostgreSQL 16

> 현재 배포본은 실제 은행 운영망이 아닙니다. 실제 계좌 잔액과 거래내역을 제공하려면 금융결제원의 이용기관 운영 승인, 계약, 보안 점검 및 운영 키 발급이 필요합니다.

## 주요 기능

- Google·카카오 OAuth 2.0 로그인
- JDBC 기반 서버 세션과 사용자별 데이터 격리
- 수입·지출 등록, 삭제, 검색 및 필터
- 월 예산과 사용률
- 카테고리 분석과 최근 6개월 추이
- CSV 내보내기
- 금융결제원 오픈뱅킹 OAuth 계좌 연결
- 여러 계좌 등록과 계좌별 잔액·거래 동기화
- 계좌별 부분 실패 처리 및 금융결제원 응답 코드 표시
- 개별 계좌 제거와 전체 연결 해제
- AES-256-GCM 토큰 암호화
- 외부 거래 지문을 이용한 중복 저장 방지

## 아키텍처

```text
Browser
  └─ HTTPS
      └─ nginx / Next.js static frontend
          ├─ /                 정적 SPA
          └─ /api/*            Spring Boot로 프록시
                                ├─ PostgreSQL / Flyway
                                ├─ Google OAuth
                                ├─ Kakao OAuth
                                └─ 금융결제원 Open Banking API
```

프런트엔드는 빌드 시 정적 파일로 내보내 nginx가 제공합니다. 인증, 데이터 소유권 확인, OAuth 콜백, 금융 API 호출은 모두 Spring Boot에서 처리합니다.

## 디렉터리

```text
backend/
  pom.xml
  src/main/java/kr/saldo/
  src/main/resources/
    application.yml
    db/migration/
frontend/
  app/
  components/
  Dockerfile
coders.yaml
compose.yaml
```

## 로컬 실행

### 1. 환경 변수 준비

```powershell
Copy-Item .env.example .env
```

`.env`에 본인이 발급받은 개발용 값을 입력합니다. `.env`는 Git에서 제외됩니다.

### 2. OAuth Redirect URI 등록

로컬 HTTPS 프록시를 사용하지 않는 경우 OAuth 제공자가 HTTP localhost 콜백을 허용하는지 확인해야 합니다.

```text
Google: {PUBLIC_URL}/api/auth/google/callback
Kakao:  {PUBLIC_URL}/api/auth/kakao/callback
KFTC:   {PUBLIC_URL}/api/openbanking/callback
```

### 3. Docker Compose 실행

```powershell
docker compose up --build
```

- 웹: `http://localhost:3000`
- API 상태: `http://localhost:8000/actuator/health`
- PostgreSQL: `localhost:5432`

## 환경 변수

| 변수 | 필수 | 설명 |
|---|---:|---|
| `PUBLIC_URL` | Y | 외부에서 접근하는 서비스 원본 URL |
| `GOOGLE_CLIENT_ID` | Y | Google OAuth 웹 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | Y | Google OAuth Client Secret |
| `KAKAO_REST_API_KEY` | Y | 카카오 REST API 키 |
| `KAKAO_CLIENT_SECRET` | Y | 카카오 로그인 Client Secret |
| `OPEN_BANKING_CLIENT_ID` | Y | 금융결제원 Client ID |
| `OPEN_BANKING_CLIENT_SECRET` | Y | 금융결제원 Client Secret |
| `OPEN_BANKING_REDIRECT_URI` | Y | 금융결제원 OAuth Callback URL |
| `OPEN_BANKING_USE_ORG_CODE` | Y | 금융결제원 이용기관코드 10자리 |
| `TOKEN_ENCRYPTION_KEY` | Y | Base64 인코딩된 무작위 32바이트 키 |
| `OPEN_BANKING_AUTHORIZE_URL` | N | 기본값은 금융결제원 테스트 인가 URL |
| `OPEN_BANKING_TOKEN_URL` | N | 기본값은 금융결제원 테스트 토큰 URL |
| `OPEN_BANKING_API_BASE_URL` | N | 기본값은 금융결제원 테스트 API v2.0 |
| `DATABASE_URL` | Y | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Y | DB 사용자 |
| `DATABASE_PASSWORD` | Y | DB 비밀번호 |

암호화 키 예시 생성:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

생성 결과는 비밀 저장소에만 보관하고 GitHub, 문서, 메신저에 올리지 않습니다.

## 금융결제원 테스트베드

테스트베드에서는 실제 은행 잔액이 아니라 금융결제원 포털에 등록한 테스트 응답 데이터가 반환됩니다.

1. 오픈뱅킹 서비스 신청 및 `이용 중` 확인
2. API Key 생성
3. 오픈뱅킹 서비스에 API Key 등록
4. Callback URL 등록
5. 테스트 정보 관리 권한 활성화
6. 사용자정보·잔액·거래내역 테스트 응답 등록
7. 살도에서 계좌 연결 후 전체 계좌 새로고침

운영 전환 시 테스트 URL 세 개를 금융결제원이 안내한 운영 URL로 교체하고 운영 Client ID/Secret을 별도 비밀값으로 등록해야 합니다.

## 데이터 보호

- 모든 가계부 조회와 변경은 로그인된 사용자 ID로 제한합니다.
- 세션 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax`로 설정합니다.
- 금융 액세스 토큰과 리프레시 토큰은 AES-256-GCM으로 암호화해 저장합니다.
- 계좌 비밀번호, 실계좌번호, 소셜 로그인 비밀번호는 수집하지 않습니다.
- 계좌번호는 금융결제원이 제공한 마스킹 값만 화면에 노출합니다.
- Secret은 코드나 Git 이력에 저장하지 않습니다.

## 배포

`coders.yaml`은 coders.kr 멀티 서비스 배포 구성을 정의합니다.

- `web`: 정적 Next.js 산출물을 제공하는 nginx
- `api`: Spring Boot 애플리케이션
- `db`: 관리형 PostgreSQL
- 실행 모드: 자체 OAuth를 사용하는 `standalone`

배포 환경에는 위 환경 변수를 플랫폼의 Secret/환경 변수 관리 기능으로 등록해야 합니다. 배포 후 다음을 점검합니다.

```text
GET /actuator/health
GET /api/auth/me
Google 로그인 및 로그아웃
카카오 로그인 및 로그아웃
오픈뱅킹 OAuth state 검증
계좌 목록·잔액·거래내역 부분 실패
사용자 A/B 데이터 격리
세션 만료 및 재로그인
개별/전체 연결 해제
DB 백업과 복원
```

## 운영 전 체크리스트

- [ ] 개인정보처리방침과 이용약관 게시
- [ ] 금융결제원 이용기관 운영 승인 및 계약
- [ ] 운영 OAuth 앱 검수와 Redirect URI 고정
- [ ] Secret 회전 절차와 접근 통제
- [ ] PostgreSQL 자동 백업 및 복구 훈련
- [ ] 오류·감사 로그의 개인정보 마스킹
- [ ] 장애 알림, 지표, 상태 페이지
- [ ] 사용자 탈퇴 시 데이터·토큰 파기
- [ ] 동의 철회와 금융결제원 해지 절차
- [ ] 취약점 점검과 의존성 업데이트 정책

## 검증

프런트엔드:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

백엔드:

```powershell
cd backend
mvn test
mvn package
```

전체 컨테이너:

```powershell
docker compose build
docker compose up
```

## 라이선스

현재 별도 오픈소스 라이선스를 부여하지 않았습니다. 외부 공개 또는 재사용이 필요하면 라이선스와 법률 검토를 먼저 진행하세요.
