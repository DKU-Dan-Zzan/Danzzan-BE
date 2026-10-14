# 로컬 festival_test → Azure 시연 DB 이관

실행일: 2026-10-14 KST. 로컬 원본을 변경하지 않고 기존 Azure VM의 MySQL 컨테이너 안에 별도 DB를 복원한 뒤 앱 연결을 전환했다. Azure 관리형 MySQL 리소스를 새로 만든 작업은 아니다.

## 현재 연결과 보존한 데이터

| 구분 | 대상 | 결과 |
| --- | --- | --- |
| 로컬 원본 | Docker `danzzan-mysql`, `festival_test`, MySQL 8.4.8 | 조회·일관된 덤프만 수행, 전후 데이터 해시 일치 |
| 현재 Azure DB | `danzzan_fall_demo`, MySQL 8.4.11 | 40개 테이블·36,174행 복원 후 앱 연결 전환 |
| 이전 Azure DB | `danzzan_demo` | 스키마·행 유지, 전환 후 모든 테이블 데이터 해시 일치 |
| 앱 DB 사용자 | 기존 `danzzan` | 새 DB에만 SELECT·INSERT·UPDATE·DELETE 추가, 기존 암호 유지 |
| Redis | 같은 컨테이너의 DB 1 | 전환 전 비어 있음을 확인. 이전 DB 0은 삭제하지 않음 |
| Kafka 소비자 그룹 | `ticket-issue-fall-demo-20261014` | 전환 전 요청·DLQ 업무 토픽의 모든 파티션 end offset 0 확인 |

공통 30개 테이블의 스키마는 기존 Azure DB와 같았다. 로컬에만 있던 10개 테이블도 포함했다. `forced_tickets_danzzan`, `lost_item`, 7개 `naverpay_*` 보관 테이블, `user_tickets_backup`이다. 사용자의 현재 로컬 DB를 그대로 복원했으며 계정·게시물·이미지 참조를 가공하거나 익명화하지 않았다. 이 덤프는 공개 저장소·공용 문서에 올리지 않는다.

## 수행한 순서와 검증

1. 모든 원본 테이블이 InnoDB임을 확인하고 스키마·행 수·행별 해시를 수집했다. 원본 조회는 읽기 전용 트랜잭션에서 수행했다.
2. `mysqldump --single-transaction --quick --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --column-statistics=0 --hex-blob --skip-add-drop-table --skip-comments --complete-insert --default-character-set=utf8mb4 festival_test`로 내보냈다. 비밀번호는 컨테이너 내부 환경변수를 사용했고 출력하지 않았다.
3. 덤프에 데이터베이스 선택·생성 및 DROP 문이 없음을 확인했다. 새 DB가 이미 존재하면 중단하는 절차로 `danzzan_fall_demo`를 생성하고 SSH로 복원했다. 문자 집합은 `utf8mb4`, 정렬은 `utf8mb4_0900_ai_ci`다.
4. 원본과 대상의 스키마를 비교하고, 각 행의 모든 열을 NULL과 값으로 구분해 해시화한 뒤 정렬한 테이블별 SHA-256을 비교했다. 40개 테이블·36,174행이 모두 일치했다. 단순 행 수만 비교한 결과가 아니다.
5. 기존 VM Compose를 비공개 디렉터리에 백업한 후 DB URL·Redis DB·Kafka 소비자 그룹만 변경했다. `app`만 재생성하고 건강 상태를 확인한 뒤 Nginx를 검사·reload했다. MySQL·Redis·Kafka는 재시작하지 않았다.
6. 앱 시작 후에도 원본/새 DB 전체 해시 일치와 이전 Azure DB 보존을 재확인했다. 앱은 `prod,azure` 프로필과 `ddl-auto: validate`로 정상 기동했고 재시작 횟수는 0이었다. Redis 실제 연결도 DB 1을 사용했다.
7. 신뢰할 수 있는 HTTPS 연결로 화면 3개와 조회 API 10개가 HTTP 200임을 확인했다. 공지 목록 4건, 주점 5건, 홈 이미지 참조 1건이 응답됐다. 이미지 파일이 실제 표시된다는 의미는 아니다.

확인한 경로: `/`, `/notice`, `/admin/login`, `/api/health`, `/api/festival/settings`, `/api/notices`, 공지 상세, `/api/home/images`, `/api/map/booth-map`, `/api/map/pubs`, 날짜별 공연, `/api/timetable/content-images`, `/api/festival/1/branding`.

이관 전 outbox 2건은 SENT, 발권 요청 2건은 SUCCESS, 대기열과 전화 인증 세션은 0건이었다. 실행 중 앱에는 스케줄러와 Kafka 소비자가 있으므로 이후 사용·예약 시각에 따라 새 DB/캐시 값이 변경될 수 있다. 기록한 해시는 이관 직후 검증 시점의 값이다.

## 이미지와 계정: 남은 작업

- 이미지 참조 74건은 원본 값 그대로 복원했다. NHN 69건(고유 URL 65개), localhost 5건(로컬 파일 3개)이다. 이번 작업에서 이미지 파일을 Azure Blob으로 복사하거나 기존 Blob을 변경하지 않았다.
- NHN 참조는 확인 당시 익명 요청에 401을 반환했다. 원본을 가져올 수 없다는 사용자 판단에 따라 수동 교체 대상으로 남겼다. `localhost:8080/uploads/...` 참조 역시 다른 방문자의 브라우저에서는 로컬 개발 서버 이미지로 연결되지 않는다.
- 이미지는 비공개 `images`에 업로드하고 DB에는 `azure:images/<blob-key>` 식별자를 저장한다. 만료되는 SAS URL을 DB에 저장하지 않는다. 가능하면 관리자 업로드 기능을 사용한다. 포스터 후속 처리에는 `festival/<festivalId>/poster/...` 경로 조건도 있으므로 임의 이름을 넣기 전에 해당 기능의 업로드 경로를 따른다.
- 새 DB는 로컬의 계정 10개와 기존 비밀번호 해시를 그대로 사용한다. 이전 Azure 합성 계정 2개를 새 DB에 추가하지 않았다. 로컬 비공개 `deploy/azure/secrets/demo-login.txt`는 이전 DB용 정보임을 표시했다.
- 이번 전환 후 로그인, 이미지 업로드·삭제, 발권 등 쓰기 기능은 재시험하지 않았다. 기존 게시물에 시험 작업을 하지 말고 별도 테스트 데이터로 확인한다.
- Octomo·OpenAI·DeepL·메일의 대기 중인 키 설정은 VM에 반영하지 않았다. 대기 중인 `external-services.env` 분리 변경은 이번 DB 이관 커밋에서 제외한다.

## 비공개 복원 자료

개발 컴퓨터의 `deploy/azure/secrets/db-migration-20261014/`에 다음 파일을 보관한다. 디렉터리는 700, 파일은 600이며 Git과 Docker 빌드에서 제외된다.

- `festival_test.sql`: 이관 시점 덤프(2,511,712바이트)
- `manifest.json`: 원본 스키마·테이블별 행 수/해시·덤프 해시
- `verified.json`: 복원 비교 결과
- `old-azure-before.json`: 전환 전 기존 Azure DB 검증 기준
- `http-checks.json`: 공개 경로의 상태 코드·집계 결과

비공개 덤프에는 계정 및 과거 운영 데이터가 포함된다. 동료에게 코드만 전달하면 이 데이터는 전달되지 않는다. 필요한 데이터와 전달 대상을 정한 뒤 비공개로 전달한다. 임시 검사 스크립트는 `/tmp`에 있었으므로 영구 배포 도구로 간주하지 않는다.

새 VM의 기본 초기화 SQL은 여전히 `danzzan_demo`를 만든다. `MYSQL_DATABASE`와 MySQL healthcheck도 이를 대상으로 유지했다. 현재 앱을 새 VM에서 재현하려면 `danzzan_fall_demo` 복원·CRUD 권한 부여를 **앱 시작 전에** 완료해야 한다. `MYSQL_DATABASE`만 바꿔도 기존 볼륨에 DB가 자동 복원되지는 않는다. 별도 복원을 하지 않는 새 시연 환경은 DB URL·Redis DB·Kafka 그룹을 해당 환경에 맞게 선택한다.

## 이전 DB로 되돌리는 절차

현재 VM에는 `/opt/danzzan/db-migration-backup-20261014/docker-compose.before-db-switch.yml`을 보관했다(디렉터리 700, 파일 600). 앱만 이전 DB로 연결하며 두 DB나 볼륨을 삭제하지 않는다. 전환 이후 새 DB에 작성한 내용은 이전 DB로 자동 합쳐지지 않는다.

이 백업은 **2026-10-14 전환 직전 설정**이다. 이후 API 키·서비스 설정을 바꿨다면 전체 사본 복원 대신 현재 Compose의 DB URL·Redis DB·Kafka 그룹 세 항목을 검토해 되돌린다. 아래 전체 사본 복원은 다른 설정 변경이 없는 경우에만 사용한다.

```bash
cd /opt/danzzan/Danzzan-BE
sudo cp -p docker-compose.azure.yml \
  /opt/danzzan/db-migration-backup-20261014/docker-compose.before-rollback.yml
sudo chmod 600 /opt/danzzan/db-migration-backup-20261014/docker-compose.before-rollback.yml
sudo cp /opt/danzzan/db-migration-backup-20261014/docker-compose.before-db-switch.yml \
  docker-compose.azure.yml
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml config --quiet
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml up -d --no-deps --force-recreate app
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml ps
# app가 healthy가 된 뒤 실행한다.
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml exec -T frontend nginx -t
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml exec -T frontend nginx -s reload
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/api/health
```

이번 작업에서 복구 절차를 실제로 실행해 재전환하지는 않았다. `down -v`, DB DROP, Redis FLUSH, Kafka 토픽 삭제는 이 복구에 필요하지 않다. Kafka 업무 메시지가 생긴 이후에는 이전 소비자 그룹의 처리 위치도 검토한 뒤 전환한다.
