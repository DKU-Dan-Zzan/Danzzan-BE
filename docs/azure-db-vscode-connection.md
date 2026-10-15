# Azure 시연 DB에 VS Code로 접속하기

확인일: **2026-10-15 KST**. 대상은 Azure VM 안의 Docker MySQL이다. Azure Database for MySQL 관리형 서비스가 아니다. 아래 절차는 동료 PC에서 SSH 터널을 열고, VS Code의 MySQL 연결 기능으로 현재 서비스 DB에 접속하는 방법이다.

## 1. 접속 정보와 준비물

| 항목 | 값 |
| --- | --- |
| SSH 서버 | `danzzan.koreacentral.cloudapp.azure.com` |
| 현재 공인 IP | `20.214.148.172` |
| SSH 포트 / 사용자 | `22` / `danzzan` |
| SSH 인증 | 별도로 전달받은 개인 키 파일 |
| VM 내부 MySQL 주소 | `172.18.0.3:3306` — 아래 재확인 방법 참고 |
| 현재 서비스 DB | `danzzan_fall_demo` |
| DB 사용자 | `danzzan` |
| DB 비밀번호 | 운영자에게 별도 보안 채널로 전달받은 DB 비밀번호 |
| 로컬 터널 주소 | `127.0.0.1:13306` |

SSH 키와 DB 비밀번호는 서로 다르다. SSH 키만으로 MySQL 로그인이 되지는 않는다. 운영자는 비공개 `deploy/azure/secrets/app.env`의 `DB_PASSWORD`를 확인하되, 환경 파일 전체를 전달하지 않는다. 비밀번호·키·덤프는 이 문서나 Git에 넣지 않는다. 시연 사이트 관리자 로그인 비밀번호도 DB 비밀번호와 별개다.

동료에게 전달할 값은 `danzzan` 계정의 `DB_PASSWORD`다. `MYSQL_ROOT_PASSWORD`는 최고 관리자 계정용이므로 이 연결에 사용하거나 전달하지 않는다. `mysql.env`의 `MYSQL_PASSWORD`는 일반 계정 초기 생성용 설정으로, 이후 DB에서 비밀번호를 바꿨다면 현재 비밀번호와 다를 수 있다.

현재 DB는 서비스가 실제 사용하는 DB다. 연결 확인은 아래 읽기 전용 SQL로 진행한다. 기존 `danzzan_demo`와 로컬 `festival_test`를 선택하지 않는다.

## 2. SSH 연결 확인

동료 PC의 VS Code **로컬 터미널**에서 실행한다. 아래 키 경로는 실제 받은 파일 경로로 바꾼다. Windows에서는 `C:/Users/사용자/.ssh/키파일`처럼 본인 경로를 사용한다.

macOS/Linux에서 개인 키 파일의 접근 권한을 설정한다.

```bash
chmod 600 "/실제/경로/키파일"
```

```bash
ssh -i "/실제/경로/키파일" -o IdentitiesOnly=yes -o ConnectTimeout=10 danzzan@danzzan.koreacentral.cloudapp.azure.com
```

처음 접속할 때 표시되는 서버 ED25519 키 지문은 다음과 비교한다. 일치하면 서버 등록을 진행한다. 다른 키 종류가 표시되거나 지문이 다르면 운영자에게 확인한다.

```text
SHA256:/WoY9fAxsMBDUhccNsD+a7F5UcC7PPfNctkYKupxSeo
```

접속에 성공하면 서버 터미널에서 `exit`로 나온다. 다음 터널 명령은 다시 **동료 PC의 로컬 터미널**에서 실행한다. VS Code Remote-SSH로 원격 창을 열 필요는 없다.

## 3. SSH 터널 열기

```bash
ssh -N -i "/실제/경로/키파일" -o IdentitiesOnly=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -L 127.0.0.1:13306:172.18.0.3:3306 danzzan@danzzan.koreacentral.cloudapp.azure.com
```

연결 후 화면에 아무 출력 없이 계속 실행되는 것이 정상이다. 터미널을 열어 둔 채 DB에 접속한다. `Ctrl+C` 또는 터미널 종료로 터널을 닫으면 DB 연결도 끊긴다.

```text
VS Code MySQL 연결
  → 동료 PC 127.0.0.1:13306
  → SSH 암호화 터널 (Azure VM TCP 22)
  → VM 내부 MySQL 172.18.0.3:3306
```

`127.0.0.1`에만 바인딩하므로 동료 PC의 다른 네트워크 장치에 포워딩 포트를 공개하지 않는다. Azure NSG나 Docker에 DB 3306 공개 설정을 추가할 필요가 없다.

## 4. VS Code MySQL 연결 설정

동료가 사용하는 MySQL 확장의 새 연결 화면에 아래 값을 입력한다. 확장마다 필드 명칭은 다를 수 있다. 위 명령으로 터널을 이미 열었으므로 확장 자체의 SSH 터널 기능은 끈다.

| 필드 | 입력값 |
| --- | --- |
| 연결 종류 | MySQL |
| 연결 이름 | `Danzzan Azure (SSH tunnel)` 등 구분 가능한 이름 |
| Host / Server | `127.0.0.1` |
| Port | `13306` |
| Database | `danzzan_fall_demo` |
| Username / User | `danzzan` |
| Password | 별도로 전달받은 DB 비밀번호 |
| SSH tunnel | 사용 안 함 — 로컬 터미널에서 이미 연결 |
| MySQL SSL / TLS | 현재 서버 구성에서는 사용 안 함 — PC와 VM 구간은 SSH로 암호화 |

연결 테스트 후 다음 읽기 전용 SQL을 실행한다.

```sql
SELECT DATABASE() AS current_database, CURRENT_USER() AS authenticated_user;
SELECT 1 AS connection_ok;
SHOW TABLES;
```

`current_database`가 `danzzan_fall_demo`, `connection_ok`가 `1`이면 대상 DB 연결을 확인한 것이다. 비밀번호를 저장한다면 확장의 보안 저장 기능을 사용하며, 저장소의 JSON 설정 파일에 평문으로 넣지 않는다.

## 5. 접속이 안 될 때

| 증상 | 확인할 내용 |
| --- | --- |
| SSH `Connection timed out` | 현재 공인 IP가 승인된 `/32`인지, 최신 서버 주소를 썼는지 확인한다. 학교·집·핫스팟·VPN 전환 시 공인 IP가 바뀔 수 있다. |
| `Permission denied (publickey)` | 서버까지는 연결된 상태다. 사용자 `danzzan`, 받은 키 파일 경로·권한·키 암호를 확인한다. |
| SSH 호스트 키 변경 경고 | 기존 기록을 지우거나 검사를 끄지 말고 운영자에게 서버 지문을 확인한다. |
| `Address already in use` | 로컬 `13306`이 사용 중이다. 다른 터널을 닫거나 명령의 첫 포트를 `13307`로 바꾸고 VS Code 포트도 맞춘다. |
| 터널은 실행 중인데 DB 연결 실패 | 서버 컨테이너 상태·IP를 재확인한다. 터널 생성 성공만으로 MySQL 연결 성공이 증명되지는 않는다. |
| MySQL `Access denied` | SSH 키와 별개인 DB 사용자·비밀번호 문제다. 운영자에게 DB 자격 증명을 확인한다. |
| MySQL 인증 플러그인 / public key 오류 | 오류 전문과 사용하는 MySQL 확장 이름을 운영자에게 전달한다. 임의로 서버 인증 방식을 바꾸지 않는다. |

Docker 컨테이너 IP는 재생성 시 바뀔 수 있다. 운영자는 VM에서 아래 읽기 전용 명령으로 주소와 공개 포트를 확인한다.

```bash
sudo docker inspect --format '{{.Name}} {{.State.Status}} {{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}} {{json .NetworkSettings.Ports}}' danzzan-azure-mysql-1
```

2026-10-15 결과는 `running`, `172.18.0.3`, `3306/tcp: null`이었다. `null`은 호스트에 포트가 게시되지 않았다는 뜻이며, VM에서 컨테이너 IP로 접근하는 터널 방식에서는 정상이다. IP가 바뀌면 터널 명령의 `172.18.0.3` 부분만 실제 값으로 바꾼다.

## 6. 이번 접속 허용과 검증 범위

- NSG `nsg-danzzan-demo`에 `Allow-SSH-Teammate`, 우선순위 `120`, Inbound Allow TCP, 출발지 `61.254.29.153/32`, 목적지 포트 `22`를 추가했다.
- 기존 `Allow-Web`(100, Internet → TCP 80/443), `Allow-SSH-Operator`(110, `61.254.29.164/32` → TCP 22)는 설정을 보존했다. Azure가 갱신한 `etag` 메타데이터 외 변경이 없음을 비교했다.
- NIC의 실제 적용 규칙에도 동료 허용 규칙이 나타남을 확인했다. DB 3306은 공개하지 않았다.
- 운영자 SSH 접속, 현재 앱 DB URL·사용자, MySQL 컨테이너 실행 상태와 VM 내부 TCP 연결을 확인했다. 비밀번호와 개인 키는 출력하지 않았다.
- **동료 PC의 실제 SSH 키 인증 및 VS Code DB 로그인은 아직 확인 전이다.** 위 절차로 동료가 접속한 결과를 확인해야 한다.
- 기존 게시물·이미지·DB 데이터는 수정하지 않았으며, 서비스나 컨테이너도 재시작하지 않았다.

현재 서버 주소는 `danzzan.koreacentral.cloudapp.azure.com`이다. 이전 `danzzan-demo-2026.koreacentral.cloudapp.azure.com`은 이번 확인에서 DNS가 조회되지 않았다.

관련 문서: [인프라·네트워크](azure-vm-infrastructure.md), [현재 DB 이관 기록](azure-db-migration.md), [이미지 업로드·DB 경로](azure-image-manual-mapping.md).
