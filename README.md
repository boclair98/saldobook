# 살도 (Saldobook)

로그인한 사용자만 자신의 수입·지출·예산을 관리할 수 있는 개인 가계부 서비스입니다.

[서비스 바로가기](https://saldobook.coders.kr) · [운영 가이드](README-SALDO.md) · [금융 연동 가이드](docs/FINANCIAL_INTEGRATION.md)

> [!NOTE]
> 현재 운영 배포본은 **수동 입력 가계부 모드**입니다. 오픈뱅킹 기능은 기본적으로 꺼져 있으며, 금융결제원 운영 승인 없이 실제 은행 잔액이나 거래내역을 제공하지 않습니다.

## 프로젝트 소개

살도는 “내가 기록한 돈의 흐름을 안전하게 이해한다”는 목표로 만든 개인 금융 기록 서비스입니다.

- Google·카카오 계정으로 로그인
- 사용자별 데이터 분리와 서버 세션 인증
- 수입·지출 직접 입력, 검색, 필터, 삭제 및 CSV 내보내기
- 월 예산과 사용률, 카테고리별 소비 분석
- 최근 6개월 수입·지출 추이
- 거래 페이지네이션과 DB 집계 기반 통계
- 금융결제원 오픈뱅킹 연동 구조(선택 기능, 기본 비활성)

## 주요 기능

| 영역 | 기능 | 설명 |
|---|---|---|
| 인증 | Google·카카오 OAuth 2.0 | 공식 인증 화면에서 로그인하고 서비스는 소셜 비밀번호를 받지 않습니다. |
| 가계부 | 수입·지출 등록/삭제 | 거래 내용, 금액, 유형, 카테고리를 저장합니다. |
| 거래 관리 | 검색·필터·페이지네이션 | 내용·카테고리 검색, 수입/지출 필터와 추가 목록 불러오기를 지원합니다. |
| 예산 | 월 예산 설정 | 이번 달 지출, 남은 금액과 예산 사용률을 계산합니다. |
| 분석 | 카테고리·월별 추이 | DB 집계 결과로 소비 비중과 최근 6개월 흐름을 보여줍니다. |
| 내보내기 | CSV 다운로드 | 현재 필터링된 거래를 UTF-8 CSV로 내려받습니다. |
| 보안 | 사용자별 데이터 격리 | 모든 조회·변경 요청에서 로그인 사용자와 데이터 소유자를 확인합니다. |
| 오픈뱅킹 | 계좌 연결·동기화 | 금융결제원 승인과 운영 설정이 완료된 경우에만 활성화할 수 있습니다. |

### 실제 사용 흐름

1. Google 또는 카카오로 로그인합니다.
2. 이번 달 수입·지출·예산 현황을 확인합니다.
3. 거래를 직접 추가하고 검색하거나 유형별로 필터링합니다.
4. 카테고리별 소비 비중과 최근 6개월 추이를 확인합니다.
5. 필요하면 CSV로 거래를 내려받습니다.

## 화면

화면 이미지의 이름, 금액, 계좌와 거래내역은 설명을 위한 샘플입니다. 실행 중인 서비스에는 임의의 데모 거래를 넣지 않습니다.

### 개인 대시보드

[![살도 개인 대시보드](docs/images/dashboard.png)](https://saldobook.coders.kr)

### 거래내역과 소비 분석

![살도 거래내역과 소비 분석](docs/images/transactions.png)

### 선택적 오픈뱅킹 계좌 관리

![살도 오픈뱅킹 계좌 관리](docs/images/account-management.png)

## 기술 스택

| 구분 | 기술 |
|---|---|
| Frontend | TypeScript, Next.js 16, React 19, CSS |
| Backend | Java 21, Spring Boot 3.4, Spring Data JPA, Spring Session JDBC |
| Database | PostgreSQL 16, Flyway |
| 인증 | Google OAuth 2.0, Kakao OAuth 2.0 |
| 금융 API | 금융결제원 오픈뱅킹 API v2.0 (선택 기능) |
| 실행/배포 | Docker Compose, nginx, coders.kr |
| CI | GitHub Actions |

## 아키텍처

```text
Browser / Mobile Web
        │ HTTPS
        ▼
nginx
  ├─ 정적 Next.js export (frontend/out)
  └─ /api/* reverse proxy
        │
        ▼
Spring Boot API
  ├─ OAuth 로그인과 JDBC 세션
  ├─ 사용자별 권한·데이터 소유권 확인
  ├─ 거래·예산·통계 API
  └─ 선택적 금융결제원 Open Banking API
        │
        ▼
PostgreSQL
  ├─ 사용자·거래·예산
  ├─ Spring Session
  └─ Flyway migration
```

프런트엔드는 정적 파일로 빌드되고 nginx가 제공합니다. 인증, 사용자별 데이터 접근, OAuth callback과 외부 금융 API 호출은 Spring Boot에서 처리합니다.

## 프로젝트 구조

```text
saldobook/
├── backend/
│   ├── src/main/java/kr/saldo/
│   │   ├── domain/       # 사용자, 거래, 예산, 계좌 도메인
│   │   ├── repo/         # JPA repository
│   │   ├── service/      # 인증·거래·통계·오픈뱅킹 로직
│   │   └── web/          # REST controller·request filter
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/ # Flyway schema migration
├── frontend/
│   ├── app/              # Next.js App Router 화면
│   ├── components/       # 공통 UI 컴포넌트
│   ├── lib/              # 클라이언트 API·인증 helper
│   └── Dockerfile        # 정적 빌드 + nginx 이미지
├── docs/
│   ├── FINANCIAL_INTEGRATION.md
│   └── images/
├── .github/workflows/ci.yml
├── coders.yaml
├── compose.yaml
└── .env.example
```

## API 개요

인증이 필요한 API는 로그인 세션의 사용자만 접근할 수 있습니다.

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/auth/me` | 로그인 상태와 OAuth 제공자 설정 확인 |
| `GET` | `/api/overview` | 월별 합계·카테고리·6개월 추이 |
| `GET` | `/api/transactions/page` | 페이지 단위 거래 조회 |
| `POST` | `/api/transactions` | 수입·지출 등록 |
| `DELETE` | `/api/transactions/{id}` | 내 거래 삭제 |
| `GET` / `PUT` | `/api/budget` | 월 예산 조회·저장 |
| `GET` | `/api/openbanking/accounts` | 선택 기능의 연결 상태 조회 |

오픈뱅킹 endpoint는 `OPEN_BANKING_ENABLED=true`일 때만 사용할 수 있습니다. 송금·결제 기능은 구현되어 있지 않습니다.

## 시작하기

### 요구 사항

- Docker Desktop
- Google·카카오 OAuth 개발용 키
- Java 21, Maven 3.9 이상 (백엔드 직접 실행 시)
- Node.js 22, pnpm 9 이상 (프런트엔드 직접 빌드 시)

### Docker Compose로 실행

```powershell
git clone https://github.com/boclair98/saldobook.git
cd saldobook
Copy-Item .env.example .env
```

`.env`에 OAuth 개발용 값을 입력한 뒤 실행합니다.

```powershell
docker compose up --build
```

실행 주소:

- Web: <http://localhost:3000>
- API health: <http://localhost:8000/actuator/health>
- PostgreSQL: `localhost:5432`

로컬 OAuth callback은 다음 주소를 각 제공자 콘솔에 등록합니다.

```text
Google: http://localhost:3000/api/auth/google/callback
Kakao:  http://localhost:3000/api/auth/kakao/callback
```

### 검증 명령

```powershell
# Frontend
cd frontend
pnpm install --frozen-lockfile
pnpm build

# Backend
cd ..\backend
mvn test
mvn package
```

## 환경 변수

실제 비밀값은 `.env` 또는 배포 플랫폼의 Secret에만 저장합니다. `.env`는 Git에 커밋하지 않습니다.

| 변수 | 필수 | 설명 |
|---|---:|---|
| `PUBLIC_URL` | 예 | 외부에서 접근하는 서비스 URL |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 예 | Google OAuth 클라이언트 |
| `KAKAO_REST_API_KEY` / `KAKAO_CLIENT_SECRET` | 예 | Kakao OAuth 애플리케이션 |
| `DATABASE_URL` | 예 | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | 예 | PostgreSQL 접속 계정 |
| `TOKEN_ENCRYPTION_KEY` | 예 | 금융 토큰용 Base64 인코딩 32바이트 키 |
| `SESSION_COOKIE_SECURE` | 배포 시 | HTTPS 배포에서는 `true` |
| `OPEN_BANKING_ENABLED` | 아니요 | 기본값 `false`; 승인된 환경에서만 `true` |
| `OPEN_BANKING_CLIENT_ID` / `OPEN_BANKING_CLIENT_SECRET` | 선택 | 금융결제원 Client ID·Secret |
| `OPEN_BANKING_REDIRECT_URI` | 선택 | 금융결제원 OAuth callback |
| `OPEN_BANKING_USE_ORG_CODE` | 선택 | 금융결제원 이용기관코드 10자리 |
| `OPEN_BANKING_AUTHORIZE_URL` | 선택 | 금융결제원 인가 URL |
| `OPEN_BANKING_TOKEN_URL` | 선택 | 금융결제원 토큰 URL |
| `OPEN_BANKING_API_BASE_URL` | 선택 | 금융결제원 API 기본 URL |

암호화 키 생성 예시:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

생성된 키는 GitHub, 이슈, 메신저에 올리지 않습니다.

## 보안과 데이터 보호

- 모든 가계부 데이터는 로그인한 사용자 ID를 기준으로 조회·변경합니다.
- 세션 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax` 정책을 사용합니다.
- POST·PUT·PATCH·DELETE 요청은 애플리케이션 전용 요청 헤더를 검증합니다.
- 금융 액세스 토큰과 리프레시 토큰은 AES-256-GCM으로 암호화합니다.
- 계좌 비밀번호와 소셜 로그인 비밀번호를 수집하지 않습니다.
- 화면에는 금융결제원이 제공한 마스킹 계좌번호만 노출합니다.
- 외부 거래 식별값을 이용해 동일 거래의 중복 저장을 방지합니다.

금융결제원 연동을 활성화하려면 [금융 연동 설정 가이드](docs/FINANCIAL_INTEGRATION.md)를 먼저 확인해야 합니다. 테스트베드 응답은 실제 은행 데이터가 아니며, 운영망 사용에는 별도의 이용기관 승인·계약·보안 점검이 필요합니다.

## 배포

`coders.yaml`에 coders.kr 배포 구성을 정의합니다.

| 서비스 | 역할 |
|---|---|
| `web` | Next.js 정적 산출물을 nginx로 제공하고 `/api`를 프록시 |
| `api` | Spring Boot 애플리케이션 |
| `db` | 관리형 PostgreSQL |

배포 환경에는 `.env`의 값을 플랫폼 Secret으로 등록합니다. 배포 후 다음 흐름을 확인합니다.

- `/actuator/health` 응답
- Google·카카오 로그인과 로그아웃
- 사용자 A/B 데이터 격리
- 거래 등록·삭제·페이지네이션
- 예산 저장과 통계 집계
- 세션 만료 후 재로그인
- 오픈뱅킹을 켠 경우 OAuth state 검증과 부분 실패 처리

## 진행 상황

- [x] Google·카카오 로그인
- [x] 사용자별 가계부 데이터 격리
- [x] 수입·지출 및 월 예산 관리
- [x] 검색·필터·CSV 내보내기
- [x] 카테고리 분석과 최근 6개월 추이
- [x] DB 집계 기반 통계와 거래 페이지네이션
- [x] 선택적 금융결제원 오픈뱅킹 모듈
- [ ] 개인정보처리방침·이용약관·회원 탈퇴 화면
- [ ] Android·iOS 앱 패키징 및 스토어 출시
- [ ] 금융결제원 운영 이용기관 승인 후 실계좌 조회

## 문서

- [운영 가이드](README-SALDO.md)
- [금융결제원 연동 설정](docs/FINANCIAL_INTEGRATION.md)
- [보안 정책](SECURITY.md)
- [coders.kr 배포 설정](coders.yaml)
- [CI workflow](.github/workflows/ci.yml)

## 라이선스

현재 별도 오픈소스 라이선스를 부여하지 않았습니다. 재사용·배포·상업적 이용은 저작권자의 사전 허가가 필요합니다.


