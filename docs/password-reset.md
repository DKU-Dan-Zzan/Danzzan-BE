# 비밀번호 재설정 구현 요약

## API
- `POST /user/password/reset/request`
- `POST /user/password/reset/verify`
- `POST /user/password/reset`

## 요청/응답 핵심 필드
- 요청 단계 응답: `requestId`, `expiresInSec`
- 검증 단계 응답: `verificationToken`
- 재설정 단계 요청: `requestId`, `verificationToken`, `newPassword`

## 에러 응답 형식
```json
{
  "error": "인증코드가 만료되었습니다.",
  "errorCode": "PASSWORD_RESET_CODE_EXPIRED"
}
```

## 보안/운영 포인트
- `request` 응답은 계정 존재 여부와 무관하게 동일한 성공 응답으로 처리
- 인증코드/verificationToken 평문 저장 금지(해시 저장)
- verificationToken은 Redis Lua 스크립트로 원자적 1회성 소모
- 비밀번호 재설정 후 `tokenVersion` 증가로 기존 사용자 세션 무효화

## 환경변수(필수)
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`
- `JWT_SECRET`, `JWT_ACCESS_VALIDITY_MS`, `JWT_REFRESH_VALIDITY_MS`
- `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION`
- `PASSWORD_RESET_CODE_TTL_SEC`
- `PASSWORD_RESET_VERIFY_TOKEN_TTL_SEC`
- `PASSWORD_RESET_MAX_VERIFY_ATTEMPTS`
- `PASSWORD_RESET_RESEND_COOLDOWN_SEC`
- `PASSWORD_RESET_MAX_REQUEST_PER_WINDOW`
- `PASSWORD_RESET_REQUEST_RATE_WINDOW_SEC`
