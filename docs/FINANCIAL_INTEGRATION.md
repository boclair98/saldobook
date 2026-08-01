# 로그인·금융 연동 설정

비밀값은 GitHub나 메신저에 올리지 말고 배포 플랫폼의 Secret에만 저장합니다. 아래 운영 주소는 `https://saldobook.coders.kr` 기준입니다.

## 1. Google 로그인

1. [Google Cloud OAuth 클라이언트](https://console.cloud.google.com/auth/clients)에서 애플리케이션 유형을 `웹 애플리케이션`으로 생성합니다.
2. 승인된 JavaScript 원본에 `https://saldobook.coders.kr`를 등록합니다.
3. 승인된 리디렉션 URI에 `https://saldobook.coders.kr/api/auth/google/callback`을 등록합니다.
4. `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`을 Secret에 저장합니다.

## 2. 카카오 로그인

1. [Kakao Developers 내 애플리케이션](https://developers.kakao.com/console/app)에서 앱을 생성합니다.
2. 카카오 로그인을 활성화하고 Web 플랫폼 사이트 도메인에 `https://saldobook.coders.kr`를 등록합니다.
3. Redirect URI에 `https://saldobook.coders.kr/api/auth/kakao/callback`을 등록합니다.
4. 보안에서 Client Secret을 발급하고 활성화합니다.
5. REST API 키를 `KAKAO_REST_API_KEY`, Client Secret을 `KAKAO_CLIENT_SECRET`에 저장합니다.

## 3. 금융결제원 오픈뱅킹 테스트베드

1. [금융결제원 오픈 API 개발자사이트](https://openapi.kftc.or.kr/)에서 이용기관 계정을 만들고 오픈뱅킹 이용 신청을 진행합니다.
2. API Key를 생성해 오픈뱅킹 서비스에 연결합니다.
3. Callback URL에 `https://saldobook.coders.kr/api/openbanking/callback`을 등록합니다.
4. 아래 값을 Secret에 저장합니다.

| Secret | 가져올 값 |
|---|---|
| `OPEN_BANKING_CLIENT_ID` | 발급된 Client ID |
| `OPEN_BANKING_CLIENT_SECRET` | 발급된 Client Secret |
| `OPEN_BANKING_USE_ORG_CODE` | 이용기관코드 10자리 |
| `OPEN_BANKING_REDIRECT_URI` | 위 Callback URL |

테스트 정보 관리에서 테스트 사용자, 계좌, 잔액과 거래내역 응답을 등록해야 동기화 결과가 나타납니다. 테스트베드는 실제 계좌를 조회하거나 돈을 이체하지 않습니다.

## 4. 암호화 키

PowerShell에서 한 번만 생성합니다.

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

결과를 `TOKEN_ENCRYPTION_KEY` Secret에 저장합니다. 이 키를 잃으면 기존에 암호화한 금융 토큰을 복구할 수 없고, 키를 바꾸면 모든 사용자가 계좌를 다시 연결해야 합니다.

## 5. 테스트와 실제 운영의 경계

현재 기본 URL은 금융결제원 테스트베드입니다. 실계좌 조회와 이체·출금 기능은 코드에 키를 넣는 것만으로 활성화되지 않습니다. 금융결제원과의 이용계약, 서비스별 승인, 보안 점검, 사용자 동의 화면 검수와 운영 키가 필요합니다.

운영 승인을 받은 뒤 금융결제원이 제공한 값으로 다음 세 URL을 함께 교체합니다.

```text
OPEN_BANKING_AUTHORIZE_URL
OPEN_BANKING_TOKEN_URL
OPEN_BANKING_API_BASE_URL
```

조회 기능과 자금이동 기능의 권한은 별도입니다. 출금·이체를 신청하지 않은 서비스에는 결제 버튼을 노출하면 안 됩니다.

## 6. 전달 전 확인

- Secret을 채팅이나 GitHub에 보내지 않았는지 확인
- Google·카카오 Redirect URI가 완전히 일치하는지 확인
- 테스트 사용자 동의 후 계좌 목록·잔액·거래 2페이지 이상 동기화
- 같은 거래를 다시 동기화해도 중복 저장되지 않는지 확인
- 계좌 연결 해제 후 암호화 토큰이 삭제되는지 확인
- 운영 전 개인정보처리방침·이용약관·탈퇴 및 동의 철회 절차 마련
