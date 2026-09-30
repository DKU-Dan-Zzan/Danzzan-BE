# 관리자 티켓 설정 (DANZ-369)

## API와 데이터
- GET `/festival/settings`: 기존 필드 + nullable `ticketingBackgroundImageUrl`.
- PUT `/admin/festival/ticketing-settings`: 기존 회차 ID를 유지하여 예매 시각·정원·공연 날짜 변경. `festival_events`도 같은 트랜잭션에서 변경한다.
- POST `/admin/festival/ticketing-background`: multipart `file`, JPG/PNG 10MB/4천만 화소 이하; `{key,url}` 반환. 업로드 후 PUT으로 URL을 저장해야 화면에 적용된다.
- URL 필드 생략은 기존 값 보존, 명시적 null은 기본 배경 복원이다. 수정 중 취소된 업로드는 게시되지 않으며 저장소에 미사용 파일이 남을 수 있다.
- 이미지 업로드와 티켓 설정은 티켓 매니저/통합 매니저/최고 관리자에게만 허용한다.
- 관리자 이벤트 목록 `/api/admin/events`에 `ticketingStartTime` 추가. 사용자 `/tickets/events`의 기존 `totalCount`, `ticketOpenAt`, `eventDate`를 사용한다.
- OFF는 새 예매만 차단한다. `/user/**`, `/auth/**`, GET `/tickets/me`, 관리자 팔찌 API는 기존 인증/권한에 따라 이용 가능하다. 회차/기존 티켓을 삭제하지 않는다.
- 열린 회차·발급 티켓이 있는 회차는 수정 불가. 변경하지 않은 열린 회차를 포함해 다른 설정을 저장할 수 있다. 회차 수정과 오픈은 동일 이벤트 행 잠금으로 직렬화한다.

## 배포
기존 DB에서는 앱 실행 전 `scripts/add_ticketing_background.sql`을 대상 스키마를 지정하여 실행한다. `festival_setting` 기본 테이블과 기존 관리자 권한 마이그레이션이 선행되어 있어야 한다. 스크립트는 컬럼이 이미 있으면 다시 추가하지 않는다.

기존 S3/NHN 설정을 사용하며 이미지 URL은 브라우저에서 조회 가능해야 한다. 내부 저장소 주소와 외부 주소가 다르면 선택 설정 `nhn.object-storage.public-base-url`에 버킷까지 포함한 공개 기본 URL을 넣는다. 미설정이면 기존 URL 생성 규칙을 유지한다.

로컬 검증은 클라우드 버킷의 NoSuchBucket 응답 때문에 별도 Docker 저장소에서 완료했다. 개발/운영 배포 전에 실제 S3 버킷과 접근 권한을 확인해야 한다.
