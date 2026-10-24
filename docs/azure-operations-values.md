# Azure 공개 문서와 비공개 운영 값

공개 문서는 절차와 검증 결과를 설명하며, 구독·관리 ID 식별자, 작업자·동료 IP, 로컬 경로와 서버 백업 위치는 `${변수명}`으로 표시한다. 이는 인증 비밀값과 별개로 불필요한 운영 정보 공개를 줄이기 위한 구분이다. 공개 서비스 URL, 저장소 endpoint, 소스·설정에 사용되는 이름, 일반 포트와 루프백 주소는 그대로 남아 있다.

## 실제 값 받기

운영자 PC의 저장소 상대 경로 `deploy/azure/secrets/operations-private.md`에 실제 값과 사용 위치를 별도로 정리한다. 이 파일은 Git에 포함되지 않으므로 저장소를 clone해도 내려오지 않는다. 동료는 운영자에게 필요한 값만 팀에서 승인한 비공개 채널로 전달받는다.

- 비공개 디렉터리는 권한 `700`, 안내 파일은 `600`으로 관리한다.
- `.gitignore`의 `deploy/azure/secrets/` 규칙으로 비공개 안내와 검증용 매핑 파일을 제외한다. 강제 추가(`git add -f`)하거나 PR에 첨부하지 않는다.
- 이 안내에는 비밀번호·API 키·개인키 본문·SAS·DB 덤프를 넣지 않는다. 그런 자격 증명은 기존 비공개 환경 파일 및 승인된 보안 채널에서 관리한다.
- 실제 값은 기존 운영 기록에서 옮긴 것이며, 이번 문서 변경으로 Azure 설정을 변경하거나 IP를 다시 검증한 것은 아니다. 동료 IP·MySQL 컨테이너 IP는 접속 전에 재확인한다.

## 공개 명령 실행 전

`${변수명}`은 Bash/Zsh 변수다. Markdown 파일 자체를 `source`로 실행하지 않는다. 비공개 안내의 필요한 변수만 같은 터미널에 설정한다. 아래 `REPLACE_...`는 실제 값이 아니며, 그대로 실행하지 않는다.

```bash
AZURE_SUBSCRIPTION_ID='REPLACE_WITH_SUBSCRIPTION_ID'
AZURE_RESOURCE_GROUP='REPLACE_WITH_RESOURCE_GROUP'
VM_NAME='REPLACE_WITH_VM_NAME'
```

VM 명령 실행 전에는 값이 비어 있거나 예시인 상태에서 다른 기본 구독을 사용하지 않도록 확인한다.

```bash
for required_name in AZURE_SUBSCRIPTION_ID AZURE_RESOURCE_GROUP VM_NAME; do
  # Bash에서 실행한다. 값 자체는 출력하지 않는다.
  required_value="${!required_name}"
  if [[ -z "$required_value" || "$required_value" == REPLACE_* ]]; then
    printf '필수 운영 값 설정 필요: %s\n' "$required_name" >&2
    exit 1
  fi
done
```

위 검사는 Bash 전용이다. Zsh 사용자는 Bash 터미널에서 값을 설정하고 검사하거나 각 변수의 입력 여부를 직접 확인한다. 이후 [VM 시작·종료](azure-vm-stop-start.md) 명령을 실행한다. Azure 로그인과 대상 구독에 대한 권한도 필요하며, 식별자만으로 권한이 생기지는 않는다.

| 변수 | 의미 |
| --- | --- |
| `AZURE_SUBSCRIPTION_ID`, `VM_PRINCIPAL_ID` | 대상 구독 및 VM 관리 ID 식별자 |
| `AZURE_RESOURCE_GROUP`, `VM_NAME`, `OS_DISK_NAME` | 관리 대상 리소스 이름 |
| `PUBLIC_IP_NAME`, `NIC_NAME`, `NSG_NAME`, `VNET_NAME`, `SUBNET_NAME` | 네트워크 리소스 이름 |
| `VM_PUBLIC_IP`, `VM_PRIVATE_IP`, `VNET_CIDR`, `SUBNET_CIDR` | 서버·네트워크 주소 |
| `OPERATOR_PUBLIC_IP`, `TEAMMATE_PUBLIC_IP` | SSH 접속을 허용한 작업자 주소. 규칙에서 `/32`를 붙인다. |
| `MYSQL_CONTAINER_IP` | VM 내부 MySQL 주소. 컨테이너 재생성 후 재확인한다. |
| `SSH_KEY_PATH`, `SSH_HOST_KEY_FINGERPRINT` | 본인 PC의 키 파일 위치 및 신뢰할 채널로 확인한 서버 지문 |
| `LOCAL_REPO_ROOT`, `DEPLOY_ROOT` | 본인 PC 저장소 상위 경로 및 VM 배포 루트 |
| `BACKUP_..._DIR`, `RELEASE_PREPARATION_DIR` | 작업별 복구 자료·배포 준비 위치. 서로 다른 작업의 백업을 혼용하지 않는다. |

경로에 공백이 있을 수 있으므로 셸 명령에서는 `"${DEPLOY_ROOT}/Danzzan-BE"`처럼 따옴표를 유지한다. 변수 값은 본인 환경에 맞게 확인하며 Azure 리소스 이름이나 방화벽 규칙을 예시값으로 변경하지 않는다.

## 2026-10-25 공개 범위 정리

관련 Azure 문서와 BE PR 본문에서 실제 운영 식별자·개인 IP·개인 경로·백업 위치를 변수로 치환하고, PR의 문서 링크는 수정된 커밋을 가리키도록 갱신한다. 실행 코드·환경 파일·DB·Blob·VM 설정은 변경하지 않는다.

이번 변경은 최신 문서와 PR 본문에 대한 정리다. 과거 커밋·이전 diff·PR 수정 이력·외부 복사본의 값을 소급 삭제하지 않는다. 이력 재작성이나 인증정보 회전은 수행하지 않았다. 나중에 실제 비밀번호·키 유출이 발견되면 문구 교체만으로 해결하지 말고 해당 자격 증명의 폐기·재발급부터 진행한다.
