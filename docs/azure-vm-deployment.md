# Azure VM 시연 배포 기록

기준일: **2026-10-14 KST**. 이 문서는 `vm-danzzan-demo`에서 실제로 실행 중인 단짠 시연 환경을 기록한다. 기존 게시물과 기존 이미지 Blob은 이 배포에서 생성·수정·삭제하지 않았다. MySQL은 별도 영속 볼륨의 새 `danzzan_demo` 데이터베이스만 사용한다.

## 현재 상태

| 항목 | 상태 |
| --- | --- |
| 접속 주소 | `https://danzzan.koreacentral.cloudapp.azure.com` |
| 공개 서비스 | FE/Nginx만 TCP 80·443 공개. BE, MySQL, Redis, Kafka는 Compose 내부 네트워크만 사용 |
| TLS | Let's Encrypt 인증서 발급 완료. 현재 인증서 만료일은 **2027-01-12** |
| 컨테이너 | `frontend`, `app`, `mysql`, `redis`, `kafka`, `kafka-init` 실행 구성 |
| BE 저장소 인증 | VM의 시스템 할당 관리 ID. 비공개 `images` 컨테이너와 사용자 위임 SAS를 사용 |
| DB | 새 `danzzan_demo`, 부팅 시 `ddl-auto: validate` 성공 |
| 합성 시연 데이터 | 공지 1건, 계정 2개: `90000001`(관리자), `90000002`(일반 사용자), 이미지 참조 0건 |

비밀번호, JWT 비밀값, DB 비밀번호, Azure 토큰·SAS·계정 키, 외부 API 키는 이 문서나 Git에 기록하지 않는다.

## DNS 이름 변경 기록 (2026-10-14)

기존 `danzzan-demo-2026.koreacentral.cloudapp.azure.com`에서 **`danzzan.koreacentral.cloudapp.azure.com`**으로 변경했다. Azure 이름 가용성 API의 `available: true`를 확인한 뒤 기존 `pip-danzzan-demo`의 DNS label만 `danzzan`으로 갱신했다. 공인 IP `20.214.148.172`와 VM·데이터 볼륨·Blob 저장소는 그대로다. 이전 이름은 Azure DNS에서 해제되므로 이전 주소의 리디렉션을 보장하지 않는다.

새 이름으로 Let's Encrypt 인증서를 발급하고 Nginx의 `server_name`·인증서 경로 및 BE의 `CORS_ALLOWED_ORIGINS`를 변경했다. FE 이미지를 다시 빌드하고 `app`·`frontend`만 재생성했다. MySQL·Redis·Kafka 컨테이너와 기존 게시물·이미지는 변경하지 않았다.

검증 결과:

- 새 DNS → 기존 공인 IP 확인, 새 인증서를 신뢰하는 HTTPS 요청 성공.
- 홈페이지·관리자 로그인 경로·공지·API health HTTP 200 및 HTTP → 새 HTTPS 301 확인.
- 관리자/일반 합성 계정의 새 Origin 로그인 성공. 내부 BE preflight의 새 Origin 허용 헤더와 기존/임의 Origin의 HTTP 403 확인.
- 실제 브라우저에서 새 주소의 공지 목록 표시 확인.
- 새 인증서의 `certbot renew --cert-name danzzan.koreacentral.cloudapp.azure.com --dry-run --no-random-sleep-on-renew` 성공, Nginx 검사·reload 후크 별도 성공.

이전 도메인 갱신 설정은 `/etc/letsencrypt/renewal-retired/`로 옮겨 자동 갱신 대상에서 제외했고, 이전 인증서·개인 키는 삭제하지 않았다. 이전 Compose/Nginx 사본은 VM의 `/opt/danzzan/dns-change-backup-20261014/`에 보관했다. 이름을 되돌리려면 이전 DNS label의 가용성을 다시 확인해야 한다.

로컬 비공개 시연 로그인 안내의 URL도 갱신했다. 주소 변경으로 브라우저 로그인 상태가 이어지지 않을 수 있으므로 새 주소에서 다시 로그인한다. 카카오 앱 관리자는 **새 짧은 주소**를 JavaScript SDK 도메인에 등록해야 한다.

## 실제 구성

배포 루트는 VM의 `/opt/danzzan/Danzzan-BE`이며, Compose 파일은 `docker-compose.azure.yml`이다. `frontend`가 정적 FE를 제공하고 `/api/` 요청을 내부 `app:8080`으로 프록시한다. `app`은 MySQL·Redis·Kafka와 같은 Compose 네트워크에만 연결한다.

```text
브라우저 ─ HTTPS ─ frontend (Nginx)
                      └─ /api/ ─ app (Spring Boot)
                                      ├─ mysql:3306
                                      ├─ redis:6379
                                      ├─ kafka:9092
                                      └─ Azure Blob (관리 ID)
```

호스트에는 80과 443만 게시한다. `3306`, `6379`, `8080`, `9092`, `9093` 및 `9094`는 공개하지 않는다. Kafka는 outbox/소비자 처리에 필요하므로 실행 구성에서 제거하지 않았다.

Compose의 `mysql_data`, `redis_data`, `kafka_data` 명명 볼륨은 VM OS 영구 디스크(`/dev/sda1`)에 있다. `/mnt` 임시 디스크에는 데이터를 두지 않는다. `docker compose down -v`는 이 볼륨을 삭제하므로 시연 환경에서 실행하지 않는다.

## 새 DB와 합성 데이터의 범위

`deploy/azure/schema.sql`은 새 `danzzan_demo` 데이터베이스만 대상으로 테이블을 준비하고, `deploy/azure/demo-seed.sql`을 배포 전 비밀 디렉터리에 복사해 MySQL 초기화에 사용했다. 두 SQL 파일은 대상 DB가 `danzzan_demo`가 아니면 중단하도록 보호한다.

생성된 합성 데이터는 다음으로 제한한다.

| 구분 | 값 | 용도 |
| --- | --- | --- |
| 관리자 | 학번 `90000001` | 관리 기능 시연 |
| 일반 사용자 | 학번 `90000002` | 일반 사용자 흐름 시연 |
| 공지 | 1건 | 새 DB에서만 보이는 합성 공지 |

로컬의 `deploy/azure/secrets/demo-login.txt`(권한 `600`)에는 생성 당시의 시연 계정 로그인 정보가 있으며 Git에 포함하지 않는다. 서버에는 실행용 `app.env`·`mysql.env`, FE 공개 키 빌드 설정 및 BCrypt 해시가 들어간 seed 파일을 둔다. 계정 평문 비밀번호 목록은 전송하지 않았다. 스키마 정의만 참고했으며 기존 NHN/로컬/운영 데이터베이스의 데이터 행, 기존 게시물, 기존 이미지 참조는 이 DB에 복사하지 않았다.

## 이미지 검증

실제 브라우저와 API에서 다음 흐름을 확인했다.

1. 관리자 인증 및 권한 확인
2. 새 DB의 합성 공지에 테스트 이미지를 multipart로 업로드
3. 비공개 Blob의 짧은 읽기 SAS로 브라우저 이미지 조회
4. SAS 만료 후 `/images/read-url`을 통한 URL 갱신

이미지 Blob은 `images` 컨테이너에만 생성됐으며, 기존 Blob이나 기존 게시물은 변경하지 않았다. 원본 5분 SAS가 만료된 뒤 Blob 요청이 HTTP 403이 되는 것을 확인했고, 만료 URL로 BE `/images/read-url`을 요청해 새 SAS를 발급한 뒤 PNG 조회 HTTP 200을 확인했다. 실제 브라우저의 Azure Blob 이미지도 `naturalWidth=8`로 로드됐다.

임시 공지 **ID 2 한 건**과 해당 테스트 Blob도 정리했다. API 삭제 뒤 정확한 ID와 UUID 제목을 함께 확인하여 해당 테스트 공지와 이미지 참조 행만 물리 삭제했고, VM 관리 ID로 새 테스트 Blob 한 개를 HTTP 202로 삭제했다. Blob soft delete 3일 설정은 유지된다. seed DB는 공지 1행·계정 2행·이미지 참조 0행으로 돌아갔다. Azure `images` 컨테이너의 활성 Blob 이름과 ETag도 전후 모두 0개로 완전히 일치했다.

MySQL·Redis·Kafka를 실제로 재시작한 뒤에도 MySQL 행 수, Redis AOF 테스트 키, Kafka 독립 테스트 topic의 메시지 보존을 각각 확인했다. 검증용 Redis 키와 Kafka topic은 이후 정리했다. VM을 Azure에서 할당 취소한 뒤 다시 시작하는 수준의 보존 검증은 아직 실행하지 않았다.

## HTTPS와 갱신

인증서 대상 FQDN은 `danzzan.koreacentral.cloudapp.azure.com`이다. Nginx는 HTTP-01 경로 `/.well-known/acme-challenge/`를 호스트의 `/var/www/certbot`에서 제공한다. Certbot은 webroot 방식을 사용하며, 성공적으로 새 인증서가 배포될 때만 `deploy/azure/renew-nginx.sh`가 Nginx 설정 검사 후 reload한다.

Certbot은 연락처 이메일 없이 등록됐고 systemd timer가 활성화되어 있다. `certbot renew --dry-run`은 webroot 갱신 시뮬레이션으로 최종 성공했다. 이 dry-run은 deploy hook을 기본 실행하지 않으므로, `deploy/azure/renew-nginx.sh`의 Nginx 설정 검사와 reload도 별도로 수동 성공을 확인했다. 운영 중 인증서나 갱신 설정을 다시 만들기 위해 반복 발급하지 않는다.

Nginx 컨테이너가 Certbot의 `live/` 심볼릭 링크가 가리키는 인증서 파일을 읽을 수 있도록 `/etc/letsencrypt`를 읽기 전용으로 마운트한다. 읽기 전용은 컨테이너의 변경을 막을 뿐 private key 읽기 권한을 없애지는 않는다. 따라서 해당 컨테이너 이미지·Compose 접근 권한을 제한하고, 이 경로를 다른 서비스에 마운트하지 않는다.

## 배포·재배포 절차

다음 명령은 VM에서 실행하는 운영 절차다. VM에는 allowlist 기반 소스 아카이브만 전송했고 `.git` 디렉터리가 없으므로 VM에서 `git status`를 실행해 배포 버전을 판별할 수 없다. 배포할 브랜치·커밋·로컬 변경은 전송 전에 개발 컴퓨터의 Git 작업 트리에서 확인한다. VM에서는 아카이브 내용과 비밀 파일 권한을 확인한다. 현재 일반 VM 사용자는 `docker` 그룹에 넣지 않았으므로 Docker 명령에 `sudo`를 붙인다.

```bash
cd /opt/danzzan/Danzzan-BE
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml config >/dev/null
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml up -d --build
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml ps
```

`app.env`, `mysql.env`, `frontend-build.env` 및 배포 시 사용하는 seed 파일은 `deploy/azure/secrets/`에 둔다. 디렉터리 권한은 `700`, 환경 파일 권한은 `600`으로 유지한다. MySQL 컨테이너가 초기화 SQL을 읽어야 하므로 Compose에 바인드한 seed SQL 파일은 `644`다. 이 파일에는 평문 비밀번호를 넣지 않는다.

단순 서비스 재시작은 볼륨을 지우지 않는다.

```bash
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml restart
```

중지 후 다시 올릴 때도 같은 볼륨을 유지한다.

```bash
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml stop
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml start
```

`down -v`, `volume rm`, MySQL 초기화 파일 변경 후 기존 볼륨을 지우는 작업은 새 DB와 합성 데이터도 삭제한다. 그런 초기화는 별도의 대상 확인 후에만 한다.

## 실행 중 확인 항목

```bash
cd /opt/danzzan/Danzzan-BE
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml ps
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/ >/dev/null
curl -fsSI https://danzzan.koreacentral.cloudapp.azure.com/ | head
sudo certbot certificates
```

로그에 비밀값이나 SAS URL이 보이면 로그를 외부 공유하지 않는다. 이미지·계정·공지 API 검증은 새 `danzzan_demo`의 두 합성 계정과 합성 공지만 대상으로 한다.

## 현재 미설정 또는 조사 중인 기능

다음은 핵심 시연 경로와 분리되어 있으며, 설정하지 않았거나 원인이 확정되지 않았다.

- SMTP 메일 발송
- Octomo API
- OpenAI API
- DeepL API
- 카카오맵 SDK Azure 도메인 등록

카카오맵은 기존 public JavaScript 키로 정상 SDK endpoint를 변경 전 Azure FQDN Referer와 함께 요청했을 때 HTTP 401을 반환했다. 키 비활성화·잘못된 키 가능성도 남아 있지만, 현재 Azure 도메인이 Kakao Developers의 해당 앱 JavaScript SDK 도메인에 등록되지 않았을 가능성이 높다. 동료 또는 앱 관리자는 `https://danzzan.koreacentral.cloudapp.azure.com`을 등록한 뒤 지도를 다시 검증한다. [Kakao Maps Web 안내](https://apis.map.kakao.com/web/guide/)

이 기능들을 활성화하려면 각각의 실제 비밀값·비용·외부 서비스 약관을 별도로 검토한다. 빈 값이나 테스트용 더미 값을 실제 키로 바꾸기 위해 기존 설정 파일의 비밀값을 복사하지 않는다.

## 동료 계정 또는 다른 VM으로 옮길 때

Azure 제공 DNS 이름은 공인 IP 리소스에 연결된다. 다른 구독·다른 VM으로 이동하면 FQDN이 달라질 수 있으며, 다음 위치를 새 실제 FQDN에 맞춰 바꾼다.

| 위치 | 변경할 값 |
| --- | --- |
| `docker-compose.azure.yml` | `CORS_ALLOWED_ORIGINS` |
| FE/Nginx HTTPS 서버 설정 | `server_name`, 인증서 경로와 HTTP redirect 대상 |
| Certbot 발급·webroot 설정 | `-d <새 FQDN>` 및 인증서 이름 |
| `deploy/azure/renew-nginx.sh` | Compose 경로가 달라졌을 때만 실제 배포 경로 |
| Azure NSG | 웹 80·443, 작업자 SSH `/32` 규칙 |

다른 Azure 테넌트의 VM은 현재 저장소 계정의 시스템 할당 관리 ID를 그대로 사용할 수 없다. 저장소를 함께 쓸 경우 대상 VM 관리 ID에 `images` 컨테이너의 Storage Blob Data Contributor와 저장소 계정의 Storage Blob Delegator를 새로 할당한다. 새 저장소로 옮기면 endpoint와 컨테이너, 역할 범위를 함께 바꾸고 기존 Blob이 자동 이관되지 않는다는 점을 확인한다.

## 평가 종료 후

서비스 준비가 끝났고 평가 기간이 아닐 때는 포털 또는 CLI에서 VM을 **할당 취소**한다. OS 종료만으로는 컴퓨팅 과금이 멈추지 않을 수 있다. 디스크·정적 공인 IP·Blob은 할당 취소 후에도 비용이 남으므로, 평가가 끝난 뒤 보관 기간과 삭제 대상을 별도로 결정한다.
