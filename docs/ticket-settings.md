# 관리자 티켓 설정 (DANZ-369)

## API와 데이터 흐름

| API | 동작 |
| --- | --- |
| GET `/festival/settings` | 축제/회차 설정 및 nullable `ticketingBackgroundImageUrl`, `ticketCardBackgroundImageUrl` 반환 |
| PUT `/admin/festival/settings` | 운영 권한으로 축제 기본 정보 수정. 연결 이벤트 제목 갱신, 기존 회차 공연일이 운영 범위 안인지 검증 |
| PUT `/admin/festival/ticketing-settings` | 티켓 권한으로 ON/OFF, 회차, 두 배경 URL 저장. 회차 ID를 유지하며 연결 `festival_events` 동기화 |
| POST `/admin/festival/ticketing-background` | multipart `file` 업로드, `{key,url}` 반환. 별도 PUT 저장 후 실제 적용 |
| GET `/tickets/events` | 설정 회차에 연결된 이벤트 목록. `ticketOpenAt`, `eventDate`, `totalCount` 사용 |
| GET `/api/admin/events` | 동일한 연결 이벤트 목록, `ticketingStartTime` 추가 |

표의 경로는 컨트롤러 기준이며 로컬 프런트 프록시의 `/api` 접두어와 구분한다.

- 이벤트 제목은 **축제 이름 + DAY n**. n은 공연일과 축제 시작일의 차이 + 1이다. 표시 순서·DB ID가 아니다.
- 기존 DB의 독립 이벤트는 삭제하지 않으면서 현재 설정 목록에서 제외한다. 공연일·시작 시각·ID 순으로 정렬한다.
- OFF는 새 예매와 GET `/tickets/me` 조회를 403으로 차단한다. 사용자 인증/가입·내정보와 관리자 팔찌 API는 기존 인증·권한에 따라 이용 가능하며 회차·티켓은 보존한다. ON으로 저장하면 기존 티켓을 다시 조회할 수 있다. CORS OPTIONS 요청은 통과한다.
- 시작된 회차(이벤트 READY 아님) 또는 발급 티켓이 있는 회차의 시간·수량·공연일 변경을 거부한다. 내용이 동일한 잠긴 회차를 포함한 저장은 허용한다.
- 회차 수정과 자동 오픈은 같은 이벤트 행 잠금을 사용한다. 축제 이름 수정은 이벤트 제목만 갱신하여 재고·오픈 상태를 덮어쓰지 않는다.
- 삭제는 수정 잠금과 별도다. 발급된 티켓이 있으면 `confirmedTicketCancelRoundIds` 확인 후 티켓·대기열·이벤트·Redis 데이터를 함께 정리한다. 이번 수동 검증에서는 삭제하지 않았다.

## 이미지 정책

`ticketingBackgroundImageUrl`은 OFF 안내, `ticketCardBackgroundImageUrl`은 발급 카드 전용이다. 전체 티켓팅 페이지 ON 배경 설정은 없다. URL 생략은 기존 값 보존, 명시적 null은 기본값 복원이다.

업로드는 티켓 매니저/통합 매니저/최고 관리자에게 허용한다. JPG/PNG, 10MB, 4천만 화소 제한과 실제 디코딩·형식 일치를 검사한다. 업로드 뒤 설정 저장을 취소하거나 저장이 실패하면 미사용 파일이 남을 수 있으며 자동 정리는 이번 범위에 없다.

기존 S3/NHN 연결을 사용한다. 내부 엔드포인트와 브라우저 주소가 다르면 선택 설정 `nhn.object-storage.public-base-url`에 버킷까지 포함한 공개 기본 URL을 넣는다. 생략하면 기존 URL 생성 규칙을 유지한다. 이번 수동 업로드 확인은 로컬 Docker 저장소에서 수행했으며 클라우드 S3/NHN 접근 검증 결과는 아니다.

## DB 변경 및 기존 DB 적용

**새 DB나 새 테이블을 만들지 않는다.** 기존 `festival_setting`에 nullable VARCHAR(2048) 컬럼 두 개를 추가한다.

| 컬럼 | 마이그레이션 |
| --- | --- |
| `ticketing_background_image_url` | `scripts/add_ticketing_background.sql` |
| `ticket_card_background_image_url` | `scripts/add_ticket_card_background.sql` |

대상 스키마와 백업을 확인한 뒤 첫 실행 전에 위 순서로 실행한다. 스크립트는 information_schema로 기존 컬럼 유무를 검사하므로 재실행 시 중복 추가하지 않는다. `ddl-auto=update`만으로 배포 절차를 대신하지 않는다.

```sh
mysql [연결 옵션] [대상 DB] < scripts/add_ticketing_background.sql
mysql [연결 옵션] [대상 DB] < scripts/add_ticket_card_background.sql
```

`festival_setting`, `festival_ticketing_round`, 관리자 권한 테이블/컬럼은 선행 main 기능이다. 오래된 DB라면 기존 마이그레이션 적용 상태를 확인하여 `add_manager_role.sql` → `add_manager_permissions.sql` 등을 먼저 적용한다. 이번 PR을 위해 기존 DB를 비우거나 예매 데이터를 초기화할 필요는 없다.

로컬 DB에는 이전 실험의 `ticketing_open_background_image_url` 컬럼도 남아 있으나 최종 엔티티/API는 사용하지 않으며, 이번 PR의 신규 스키마도 아니다. 이번 문서화 과정에서는 해당 컬럼을 삭제하지 않았다.

## application-dev.yml과 현재 로컬 환경

- `src/main/resources/application-dev.yml`은 기존부터 gitignore 대상이다. 로컬 파일은 존재하며 `ddl-auto: update`, multipart 10MB/20MB로 사용 중이나 **이번 PR에는 포함되지 않는다**.
- 추적되는 `application.yml`, `application-prod.yml`은 이 PR에서 변경하지 않았다.
- 현재 로컬 Docker는 실행 환경변수로 MySQL `danzzan`, Redis, Kafka, 로컬 S3 호환 저장소를 연결한다. AWS RDS/S3에 직접 연결해서 캡처한 결과가 아니다.
- 환경 파일과 비밀값은 커밋하지 않는다. API 이미지 URL은 브라우저에서 접근 가능한 주소여야 한다.

## 예매 처리 보완

- PROCESSING 상태에서 대기열 정보가 null이어도 상태 조회가 예외로 실패하지 않는다.
- 입장 순번은 원본 Redis 사용자 대기열 해시에서 읽는다.
- 입장 자격 만료/비활성은 HTTP 400 + `RESERVE_ADMISSION_EXPIRED`로 구분한다.
- 비동기 발급 요청 등록 실패 시 eventId/requestId를 기록하고 처리 상태·claim을 복구한다.
- FE는 PROCESSING 결과를 조회하며 중복 요청을 자동 재전송하지 않는다.

## 검증 결과

2026-10-01 전체 테스트: **383개 중 371개 통과, 12개 스킵, 실패·오류 0**. Java 17 Docker, 실제 서비스 Redis와 분리된 임시 Redis 및 테스트용 S3 환경값으로 실행했다. 테스트 전용 Redis는 실행 종료 후 제거했다.

주요 검증: 설정/이벤트 동기화, 시작된 회차 수정 거부 및 동일 값 저장, 두 배경 URL 생략/null 구분, 이미지 검증, 연결 이벤트 필터링, OFF 시 내 티켓 조회 차단과 계정 기능 유지, PROCESSING null 상태, 만료 오류 처리. 전체 테스트 성공은 실제 클라우드나 고부하 환경의 성능 검증을 의미하지 않는다.

브라우저에서는 ON/OFF 저장, 기존 회차/발급 수 보존, 이미지 업로드 URL HTTP 200 및 기본 배경 복원, 사용자·팔찌 목록을 확인했다. 기존 회차 3개 및 발급 티켓 1장을 유지했으며 신규 발급·삭제는 실행하지 않았다. 상세 스크린샷은 연동 FE PR의 `docs/pr/danz-368/README.md`에 수록한다.
