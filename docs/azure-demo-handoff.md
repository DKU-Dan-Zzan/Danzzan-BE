# 단짠 Azure 준비·이미지 연동 인수인계

기준일: **2026-10-14, 한국 시간**. 현재까지 실제 수행한 작업과 동료 계정에서 재현할 절차를 정리한다. VM·네트워크·관리 ID, 새 DB 기반 앱 배포와 HTTPS 설정까지 진행했다. 이 문서는 리소스 생성이나 권한 변경을 자동 실행하지 않는다.

## 1. 현재 어디까지 완료했는가

| 항목 | 확인된 상태 | 근거·주의점 |
| --- | --- | --- |
| 학생 구독 | Azure for Students 사용 | 잔액은 사용자가 `US$136,475/US$136,475`, 만료일은 2027-10-12로 전달. 금액의 쉼표 의미는 확정하지 않음 |
| 리소스 그룹 | `rg-danzzan-demo`, Korea Central | 10-13 CLI 재조회 |
| 스토리지 계정 | `danzzandemo2026`, Korea Central | StorageV2 / Standard_LRS / Hot |
| 이미지 컨테이너 | `images`, 비공개 | CLI의 `publicAccess: null` 및 계정 익명 접근 비활성 확인 |
| 계정 키·익명 공개 | 둘 다 비활성 | Entra ID + 사용자 위임 SAS 사용 |
| 삭제 복구 | Blob·컨테이너 일시 삭제 각각 3일 | 삭제 후 보존 기간의 저장 비용은 남을 수 있음 |
| 개발자 인증 | Azure CLI 2.91.0 설치·본인 학교 계정 로그인 완료 | 로그인 정보는 다른 사람에게 전달하지 않음 |
| 개발자 권한 | 구독에서 상속된 Owner + `images`의 Storage Blob Data Contributor | 동료에게 Owner를 그대로 부여할 필요는 없음. 아래 최소 범위 참고 |
| BE·FE 이미지 전환 | BE `4d71343`, FE `3cb26497` 커밋 완료 | 원격 푸시는 별도이며, main clone만으로 변경이 전달되지 않음 |
| 실제 Azure 검증 | SDK 업로드·조회·만료·삭제, 테스트 공지 API·브라우저 조회 성공 | 5분 SAS 만료 403→재발급 PNG 200, 임시 공지 ID 2와 신규 Blob 정리 완료. 활성 Blob 이름·ETag 전후 0개 동일, 기존 공지·이미지 미변경 |
| 기존 NHN 이미지 | 이관하지 않음 | 졸업작품용 새 이미지를 사용하기로 결정 |
| VM·네트워크 | 10-14 생성 완료, 실행 중 | D2as_v4 / 2 vCPU / 8 GiB / 64 GiB Standard SSD, FE·BE·MySQL·Redis·Kafka 실행 구성 |
| VM 관리 ID | Blob 역할 2개 할당 및 VM 내부 인증 검증 완료 | 비공개 목록 조회와 사용자 위임 키 발급 HTTP 200. 기존 데이터 쓰기·삭제 없음 |
| HTTPS | Let’s Encrypt 인증서 발급 및 renewal dry-run 성공 | Azure 제공 DNS에서 HTTPS 실행. 현재 인증서 만료일 2027-01-12, systemd timer 활성화. dry-run과 deploy hook 수동 검증은 구분 |
| 새 시연 DB | `danzzan_demo` | 30 테이블, 합성 계정 2개와 공지 1건. 기존 게시물·이미지 미변경 |
| Compute·Network | 공급자 등록 완료 | 생성 직전 지역 vCPU 6 / DASv4 4, DASv5·DSv5 0. 동료 구독은 재확인 필요 |

코드·상세 검증 기록은 [azure-image-storage.md](azure-image-storage.md), 실제 VM 구성·접속·권한·비용은 [azure-vm-infrastructure.md](azure-vm-infrastructure.md), 앱·DB·HTTPS 배포와 운영 절차는 [azure-vm-deployment.md](azure-vm-deployment.md)에 있다. 아래 재현 절차는 현재 사용자 구독의 리소스를 중복 생성하라는 의미가 아니다.

## 2. 동료 계정으로 진행하기 전에 결정할 사항

**Azure 로그인 계정, Entra 테넌트(조직 디렉터리), 구독(과금 단위), VM 관리 ID는 서로 다르다.** 같은 학교 이메일이어도 포털에서 실제 테넌트·구독을 확인한다.

| 방식 | 필요한 준비 | 과금·데이터 영향 |
| --- | --- | --- |
| A. 동료 VM + 현재 저장소, 같은 테넌트 | 현재 저장소에 동료 사용자와 향후 VM 관리 ID의 필요한 역할을 각각 부여 | VM 비용은 동료 구독, 이미지 비용은 현재 저장소 구독에 발생 |
| B. 동료 구독에 VM·저장소 모두 새로 준비 | 새 고유 스토리지 이름, 비공개 `images`, 동료 계정/VM의 RBAC, BE endpoint 변경 | 동료 구독으로 비용을 모을 수 있음. 새 이미지로 시작하는 현재 방침과 잘 맞음 |
| C. 동료 VM과 현재 저장소의 테넌트가 다름 | 현재 코드의 시스템 할당 관리 ID 경로를 그대로 사용할 수 있다고 가정하지 않음 | 기본 재현안은 B. 별도 교차 테넌트 인증 설계는 이번 범위 밖 |

VM 관리 ID의 교차 디렉터리 사용은 지원되지 않는다. 외부 사용자를 게스트로 초대해 개발자 접근을 허용하는 것과 VM 관리 ID 인증은 별개다. [관리 ID FAQ](https://learn.microsoft.com/en-us/entra/identity/managed-identities-azure-resources/managed-identities-faq)

동료가 채울 항목:

- Azure 계정, **저장소가 속한 테넌트 ID**, 선택할 구독 ID, 크레딧 만료일·잔액
- VM과 저장소를 같은 테넌트/구독에 둘지 여부
- 리소스 그룹, 지역, 스토리지 계정 이름, 컨테이너 이름
- BE·FE 코드 전달 방식 및 실제 체크아웃할 커밋
- 평가 시작·종료일, 준비 테스트 시간, 보관할 디스크·공인 IP

비밀번호·계정 키·연결 문자열·JWT·SAS 전체 URL을 문서, Git, 메신저에 복사하지 않는다. 구독·테넌트 ID는 비밀키가 아니지만 공용 문서에는 실제 개인 ID 대신 자리표시자를 쓴다.

## 3. 코드 작업 상태를 먼저 확인한다

| 저장소 | 현재 로컬 경로 | 작업 브랜치·티켓 |
| --- | --- | --- |
| BE | `/Users/juhee/CAPSTONE_ROOT_DIR/GITHUB_REPO/Danzzan-BE` | `DANZ-372-be-azure배포용-이미지저장소-blob-storage전환` / DANZ-372 |
| FE | `/Users/juhee/CAPSTONE_ROOT_DIR/GITHUB_REPO/Danzzan-FE` | `DANZ-373-fe-azure-blob-storage-전환에-따른-이미지-업로드-연동-수정` / DANZ-373 |

저장소: [BE](https://github.com/DKU-Dan-Zzan/Danzzan-BE), [FE](https://github.com/DKU-Dan-Zzan/Danzzan-FE).

동료 컴퓨터에서는 자기 경로로 바꿔 `git status --short --branch`를 확인한다. 2026-10-13 사용자 요청에 따라 Azure 코드·테스트·문서를 BE와 FE 각각의 작업 브랜치에서 커밋한다. 동료가 원격에서 받으려면 이후 별도 푸시가 필요하다. 푸시 후에도 main이 아니라 표의 작업 브랜치를 체크아웃해야 한다. 정확한 커밋은 각 저장소의 `git log -1 --oneline`으로 확인한다. PR은 별도 요청 범위다. 기존 변경을 reset하거나 덮어쓰지 않는다.

주요 구현 위치:

- [AzureBlobStorageConfig.java](../src/main/java/com/danzzan/infra/azure/AzureBlobStorageConfig.java): CLI/관리 ID 인증 및 SDK 클라이언트
- [AzureBlobStorage.java](../src/main/java/com/danzzan/infra/azure/AzureBlobStorage.java): 업로드, 읽기 SAS, Blob 경로 정규화, 삭제
- [application-azure.yml](../src/main/resources/application-azure.yml): Azure 프로필·환경 변수
- [S3Uploader.java](../src/main/java/com/danzzan/infra/s3/S3Uploader.java), [S3PresignService.java](../src/main/java/com/danzzan/infra/s3/S3PresignService.java): 기존 호출부 호환 및 Azure 분기
- [ImageReadUrlController.java](../src/main/java/com/danzzan/infra/image/ImageReadUrlController.java): 읽기 URL 갱신
- FE: `src/components/common/AzureImage.tsx`, `src/hooks/app/useAzureImageUrl.ts`, `src/lib/app/azure/azureReadUrl.ts`

## 4. Azure 구독과 중복 리소스를 확인한다

1. 포털 우측 계정 메뉴에서 로그인 계정·디렉터리를 확인한다.
2. 구독 화면에서 사용할 학생 구독과 활성 상태를 확인한다.
3. [학생 크레딧 잔액 화면](https://www.microsoftazuresponsorships.com/balance)에서 같은 계정으로 잔액·만료일을 확인한다. 화면의 통화·소수점 표기를 포함해 기록한다.
4. **모든 리소스**와 대상 리소스 그룹에서 기존 스토리지·VM·디스크·공인 IP를 확인한다. 다른 구독/필터를 보고 빈 목록이라고 판단하지 않는다.
5. 현재 저장소를 공동 사용한다면 생성 절차를 건너뛰고 7번 권한 단계로 간다.

## 5. 새 저장소를 만드는 경우의 화면별 설정

새 계정 생성은 동료 구독의 과금 리소스를 추가하는 작업이다. 생성 직전에 대상 구독·계정 이름·설정·예상 비용을 확인한다. 아래는 현재 계정에서 선택했던 값이다.

### 5-1. 기본

| 항목 | 선택값 | 의미 |
| --- | --- | --- |
| 구독 | 본인이 사용할 Azure for Students | 비용·크레딧이 차감되는 구독 |
| 리소스 그룹 | 기존 그룹 또는 새 `rg-danzzan-demo` | 관련 리소스를 묶는 관리 단위. 그룹의 지역과 각 리소스 지역은 별도 확인 |
| 계정 이름 | 현재 `danzzandemo2026`; 동료는 새 고유 이름 | 전역 고유, 소문자·숫자 3~24자. 현재 이름을 다시 만들 수 없음 |
| 지역 | Korea Central | 현재 선택 지역. 동료 구독의 사용 가능 지역은 다시 확인 |
| 기본 서비스 | Azure Blob Storage 또는 Azure Data Lake Storage | 이미지 파일 저장에 사용할 서비스 |
| 워크로드 | 범용 | 포털 설정 추천용 분류 |
| 성능·계정 종류 | 표준 / 범용 v2(StorageV2) | 소규모 이미지 저장에 사용할 일반 계정 |
| 복제 | LRS | 한 지역 내 로컬 중복. GRS/RA-GRS처럼 다른 지역까지 복제하지 않아 비용 절약 |

### 5-2. 고급

| 항목 | 값 | 이유 |
| --- | --- | --- |
| 계층 구조 네임스페이스 | 끔 | Data Lake 파일시스템 기능이 필요하지 않음. `/`를 포함한 Blob 이름은 계속 사용 가능 |
| SFTP / NFS v3 | 끔 | 파일 전송 서버·NFS 마운트 대신 HTTPS SDK 사용 |
| 테넌트 간 복제 | 끔 | 별도 조직 간 객체 복제 작업을 사용하지 않음 |
| 액세스 계층 | Hot | 평가 중 자주 읽을 이미지. Cool/Cold는 낮은 저장 단가 외에 읽기·최소 보관 조건도 고려해야 함 |
| Azure Files의 SMB 관리 ID | 끔 | Azure Files를 사용하지 않음 |
| SMB 전송 암호화 요구 | 켬 | Azure Files 관련 기본 보안 설정; Blob 인증 설정과는 별개 |

### 5-3. 네트워킹

- 공용 네트워크 접근: **사용 / 모든 네트워크**. 로컬 BE와 사용자 브라우저가 HTTPS로 접근한다.
- 이것은 익명 공개 허용과 다르다. 실제 Blob 조회에는 Entra 인증 또는 유효한 SAS가 필요하다.
- 프라이빗 엔드포인트: 만들지 않음. 이번 구조에서 필요하지 않은 별도 유료 연결을 추가하지 않는다.
- 라우팅: Microsoft 네트워크 라우팅.

### 5-4. 데이터 보호

- Blob 일시 삭제: **사용, 3일**.
- 컨테이너 일시 삭제: **사용, 3일**.
- 특정 시점 복원 / 버전 관리 / 변경 피드 / 버전 수준 불변성: **사용 안 함**.
- Azure Files의 클래식 파일 공유 일시 삭제도 3일로 선택했지만 현재 파일 공유는 만들지 않았다.
- 일시 삭제는 실수 복구용이다. 삭제가 곧 저장 요금의 즉시 종료를 의미하지 않으며, DB 백업을 대신하지 않는다.

### 5-5. 보안

- REST API 보안 전송 요구: **사용**(HTTPS).
- 개별 컨테이너 익명 접근 허용: **사용 안 함**.
- 스토리지 계정 키 접근: **사용 안 함**. Shared Key 기반 계정 SAS/서비스 SAS가 아니라 **사용자 위임 SAS**를 사용한다.
- Portal에서 Microsoft Entra 인증 기본값 사용: **사용**. 기본 로그인 방식을 선택하는 옵션이며 역할을 자동으로 부여하지 않는다.
- 최소 TLS: **1.2**.
- 복사 작업 허용 범위: 당시 **모든 스토리지 계정** 선택. 복사 소스 제한 설정이며 익명 읽기 허용이나 테넌트 간 복제 활성화가 아니다. 현재 이미지 업로드는 복사 API를 사용하지 않는다.

### 5-6. 암호화·태그·최종 확인

- 암호화: **Microsoft 관리형 키(MMK)**. 별도 고객 키/Key Vault는 만들지 않는다.
- 고객 관리형 키 지원 범위: **Blob 및 파일만**. 현재 MMK 선택을 CMK로 바꾸는 옵션이 아니다.
- 인프라 암호화: **끔**. 기본 저장 암호화는 적용되고, 추가 이중 암호화 옵션은 사용하지 않는다.
- 태그: 당시 생략. 새로 정리한다면 `project=danzzan`, `purpose=graduation-demo`처럼 관리용 값을 사용할 수 있다.
- 검토 + 만들기에서 모든 값을 확인한 뒤 **만들기**. 배포가 Accepted/진행 중이면 아직 완료가 아니다. 성공 후 **리소스로 이동**한다.

[계정 생성 문서](https://learn.microsoft.com/en-us/azure/storage/common/storage-account-create), [중복성](https://learn.microsoft.com/en-us/azure/storage/common/storage-redundancy), [액세스 계층](https://learn.microsoft.com/en-us/azure/storage/blobs/access-tiers-overview)

## 6. 비공개 images 컨테이너를 만든다

1. 스토리지 계정 → **데이터 스토리지 → 컨테이너**.
2. 기존 `images`가 있으면 중복 생성하지 말고 접근 수준을 확인한다.
3. 없으면 **+ 컨테이너**, 이름 `images`, 익명 액세스 수준 **비공개**로 생성한다.
4. 현재 작업에서는 생성 성공 후 목록 조회 권한 오류가 발생했다. 컨테이너 생성 권한과 내부 Blob 데이터 접근 권한이 다르기 때문이다.

## 7. IAM에서 데이터 접근 권한을 부여한다

IAM은 **누가 어떤 범위에서 무엇을 할 수 있는지** 관리하는 화면이다. Owner라는 관리 권한만으로 Entra 인증을 통한 Blob 데이터 읽기가 자동 허용되지는 않는다.

최소 범위를 나누는 권장안:

| 대상 | 역할 | 범위 | 용도 |
| --- | --- | --- | --- |
| 로컬 BE를 실행할 동료 사용자 | Storage Blob Data Contributor | `images` 컨테이너 | 이미지 읽기·업로드·삭제 |
| 같은 사용자 | Storage Blob Delegator | 스토리지 계정 | 사용자 위임 키 발급 → 읽기 SAS 서명 |
| 향후 VM의 시스템 할당 관리 ID | 위 두 역할 | 각각 위 범위 | VM에서 키 없이 동일 작업 수행 |

위임 키 발급은 계정 수준 작업이므로 **컨테이너의 Data Contributor만으로는 충분하지 않다**. 현재 사용자는 상속된 구독 Owner로 관리 작업 권한을 갖고 있어 별도 Delegator 없이 위임 키 요청이 통과했다. 동료에게는 현재 사용자의 넓은 Owner 권한을 복제하지 말고 실제 필요한 권한을 위처럼 검토한다. 포털 탐색에 필요한 관리 읽기 권한이 없는 사용자라면 해당 계정의 Reader 역할도 별도로 검토한다.

역할 할당은 권한 변경이다. 저장소 관리자가 대상 사용자/관리 ID와 범위를 확인하고 진행한다.

1. `images` → **액세스 제어(IAM) → 역할 할당 추가**.
2. **Storage Blob Data Contributor(데이터 기여자)** 선택.
3. 구성원에서 **사용자, 그룹 또는 서비스 주체** → 실제 동료 계정 선택. VM에는 나중에 **관리 ID**를 선택한다.
4. **검토 + 할당**을 눌러 검토 화면으로 이동한다.
5. 역할·구성원·범위를 확인하고 **검토 + 할당을 다시 눌러 완료**한다. 첫 번째 클릭만으로 저장되지 않는다.
6. 성공 알림과 역할 할당 목록에 새 역할이 나타나는지 확인한다.
7. Delegator가 필요한 계정은 **스토리지 계정의 IAM**에서 같은 순서로 추가한다.
8. 반영 시간을 두고 `images`를 새로고침한다. 현재 작업에서는 최종 할당 성공 후 Entra 데이터 조회 오류가 사라졌다.

**‘적격 할당’의 Entra ID P2/ID Governance 라이선스 오류는 이번 일반 역할 할당과 별개다.** 이 오류를 해결하려고 유료 PIM 라이선스를 추가하지 않는다. 일반 역할 할당 성공 여부를 확인한다.

[Entra 데이터 접근 권한](https://learn.microsoft.com/en-us/azure/storage/blobs/assign-azure-role-data-access), [위임 키 범위](https://learn.microsoft.com/en-us/rest/api/storageservices/get-user-delegation-key)

## 8. 로컬 Azure CLI 인증을 준비한다

본인 컴퓨터에서 본인 계정으로 로그인한다. 다음은 macOS 예시다. Homebrew가 있는 경우에만 설치 명령을 사용한다. [Azure CLI 설치](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli)

```sh
# 설치 여부 확인. 이미 있으면 재설치하지 않는다.
az version
# 설치가 필요한 macOS 환경에서만:
brew install azure-cli

# 자리표시자를 실제 저장소 테넌트·사용할 구독으로 바꾼다.
az login --tenant '<STORAGE_TENANT_ID>'
az account set --subscription '<SUBSCRIPTION_ID>'
az account show --query '{name:name,tenant:tenantId,state:state}' -o json
```

`az account set`은 로컬 활성 구독을 바꾼다. 기존 프로젝트의 활성 구독을 기록해 둔다. 현재 Java 설정은 tenant/client ID를 명시적으로 지정하지 않는 `AzureCliCredentialBuilder`를 사용하므로 여러 조직에 로그인했다면 선택된 계정·테넌트를 반드시 확인한다. 로그인 토큰 파일을 다른 사람에게 복사하지 않는다.

비밀값 없는 읽기 확인 예시:

```sh
az group show --name '<RESOURCE_GROUP>' --query '{name:name,location:location}'
az resource list --resource-group '<RESOURCE_GROUP>' \
  --query '[].{name:name,type:type,location:location}'
az storage container show --account-name '<STORAGE_ACCOUNT>' --name images \
  --auth-mode login --query '{name:name,publicAccess:properties.publicAccess}'
```

`publicAccess: null`은 익명 접근 수준이 지정되지 않은 비공개 상태다. 목록 조회가 성공해도 SAS 발급까지 성공했다는 뜻은 아니므로 실제 읽기 URL 발급도 검증한다.

## 9. BE·FE를 Azure에 연결한다

### 9-1. 실제 요청 흐름

```text
이미지 업로드: 관리자 브라우저 → 기존 multipart BE API → Azure Blob SDK → 비공개 images
이미지 저장:   BE → DB에 azure:images/<blob-key>
화면 조회:    BE가 짧은 읽기 SAS 발급 → 브라우저가 Blob에서 직접 GET
만료 갱신:    FE → POST /images/read-url → 새 읽기 SAS → 이미지 다시 조회
```

현재 구현은 **multipart 업로드 유지**다. 처음 검토했던 브라우저 직접 PUT 방식은 채택하지 않았다. FE가 Blob에 `x-ms-blob-type`을 직접 보내는 작업도 없으며 SDK가 업로드를 처리한다. BE가 업로드한 Blob에는 이미지 Content-Type을 설정한다. S3 endpoint만 Azure 주소로 교체한 구현이 아니다.

DB의 canonical reference에는 계정 이름이 들어 있지 않다. **endpoint만 바꾸면 기존 `azure:images/...` 참조도 새 계정에서 해석된다.** 새 계정에 해당 Blob이 없으면 404가 된다. 동료 구독에서 새로 시작할 때 기존 DB를 그대로 복제하고 이미지도 옮겨진 것으로 간주하지 않는다. 기존 데이터 이관·수정은 별도 승인 작업이다.

### 9-2. 로컬 BE 설정

Java 17과 저장소의 Gradle wrapper를 사용한다. DB·Redis·Kafka는 기존 로컬 Docker를 사용할 수 있지만 Azure CLI 인증은 **BE를 실행하는 호스트**에서 접근 가능해야 한다.

```sh
export SPRING_PROFILES_ACTIVE=dev,azure
export IMAGE_STORAGE_PROVIDER=azure
export AZURE_STORAGE_CREDENTIAL=azure-cli
export AZURE_STORAGE_ENDPOINT='https://<STORAGE_ACCOUNT>.blob.core.windows.net'
export AZURE_STORAGE_CONTAINER=images
export AZURE_STORAGE_READ_SAS_EXPIRATION_SECONDS=300
# 기존 8080 서버와 충돌을 피할 예시. 실제 사용 중인 포트를 먼저 확인한다.
export SERVER_PORT=8081
# DB·Redis·Kafka 연결 및 기타 필수 로컬 설정까지 준비한 뒤에만 실행:
./gradlew bootRun
```

이 환경 변수만으로 신규 컴퓨터의 전체 앱이 준비되는 것은 아니다. DB URL·계정·비밀번호, Redis/Kafka 주소, JWT 및 앱 필수 외부 서비스 설정은 별도의 승인된 비밀 전달 경로로 준비한다. 로컬 프로필 파일은 Git에 들어 있는지와 비밀값 포함 여부를 먼저 확인한다. `.env` 파일을 만들기만 해서는 호스트 Java가 자동으로 읽지 않는다. 셸 export나 IDE 실행 설정 등 실제 주입 경로를 사용한다.

호스트의 `az login`은 기존 Docker BE에 자동 전달되지 않는다. 현재 BE runtime 이미지에 CLI도 자동 설치되지 않는다. 로그인 캐시를 컨테이너에 무작정 마운트하는 대신 이번 검증처럼 호스트 BE를 사용한다. 일반 앱을 기존 DB에 붙여 실행하면 예약 작업·Kafka 소비자가 동작할 수 있으므로 독립된 시연 DB를 우선 준비한다.

Azure 설정 기본값은 현재 계정 endpoint와 `managed-identity`다. 동료 로컬에서는 **endpoint와 `azure-cli`를 명시**한다. `local-storage`와 `azure`를 함께 활성화하지 않는다. 읽기 SAS 기본값은 300초이고 허용 범위는 1~1800초다.

### 9-3. FE 설정과 CORS

- FE에는 Azure 키·연결 문자열·SAS 발급용 자격 증명을 넣지 않는다.
- 개발 서버 예시: BE가 실제 8081에서 실행 중일 때 다음과 같이 연결한다. Node 환경은 FE Dockerfile 기준 22 계열이다.

```sh
npm ci
VITE_API_BASE_URL=http://127.0.0.1:8081 \
VITE_TICKETING_API_BASE_URL=http://127.0.0.1:8081 \
  npm run dev -- --host 127.0.0.1 --port 5174
```

- BE CORS가 이 개발 출처를 허용하는지 확인한다. 다른 컴퓨터의 브라우저에서 접속할 때 loopback 주소를 그대로 사용하지 않는다.
- FE는 SAS 만료 90초 전 갱신을 준비하고 최소 재시도 간격 30초, 탭 복귀 시 갱신, 이미지 오류 시 갱신을 처리한다. 같은 URL의 동시 갱신은 합친다.
- Blob CORS는 현재 **빈 규칙**이다. BE multipart 업로드와 일반 `<img>` 표시에는 Blob PUT CORS가 필요하지 않다.
- 향후 브라우저 fetch/canvas 또는 직접 업로드가 필요할 때만 출처·메서드·헤더를 구체적으로 정한다. CORS는 접근 권한을 대신하지 않는다.
- 향후 FE 컨테이너는 빌드 시 API base `/api`, Nginx는 `/api/`를 제거해 `app:8080`으로 보낸다. BE 컨트롤러마다 `/api` 포함 여부가 다르므로 실제 FE 호출 경로를 기준으로 검증하며 일괄 prefix 변경하지 않는다.

### 9-4. API와 파일 제한

- 기존 JSON 필드명과 multipart 업로드 경로는 유지한다. multipart 필드 `file`을 사용하고 FormData의 boundary는 브라우저가 정하게 한다.
- BE 직접 접속 기준 공지: `POST /api/admin/notices/images`; 광고: `POST /api/admin/ads/images/upload`; 아티스트: `POST /admin/timetable/artists/{artistId}/images/upload`; 주점: `POST /admin/map/pubs/{pubId}/images/upload`.
- `POST /images/read-url`: 요청 `{ "imageUrl": "기존 또는 만료된 URL" }`, 응답 `{ "imageUrl": "새 URL" }`, `Cache-Control: no-store`.
- 공개 화면에 등록된 이미지나 기존 관리자 권한으로 허용되는 임시 이미지만 갱신한다. 임의의 비공개 Blob을 익명으로 서명하지 않는다.
- 기존 presign API는 NHN/S3 모드에서는 유지되지만 Azure 모드에서는 multipart 사용 안내와 함께 400을 반환한다.
- 관리자 `S3Uploader` 경로는 기존 **5MiB 제한**, Azure 공통 업로더는 최대 **10MiB**, Spring multipart는 파일 10MB/요청 11MB다. 공통 상한이 10이라고 관리자 화면도 10까지 허용되는 것은 아니다.
- Azure 공통 업로드는 JPEG/JPG·PNG·WebP MIME만 허용하며 빈 파일을 거부한다. 이것이 모든 업로드 파일의 내용에 대한 악성코드 검사나 완전한 이미지 디코딩 검증을 뜻하지는 않는다.
- 게시물 이미지 등록 해제와 실제 Blob 삭제는 별개다. 기존 삭제 API에 모든 Blob을 자동 삭제하는 동작을 추가하지 않았다.
- SAS는 유효 기간 동안 URL을 가진 사람이 읽을 수 있는 임시 접근권이다. DB·로그·공용 문서에 SAS 전체 URL을 저장하지 않는다.

## 10. 테스트는 새 이미지·새 공지만 사용한다

### 10-1. 일반 자동 테스트

```sh
# BE 저장소에서, Java 17 환경
./gradlew test
# FE 저장소에서
npm test
npm run typecheck
npm run lint
npm run build
```

외부 서비스 환경 때문에 건너뛰거나 실패한 항목은 결과에서 분리한다. 일반 테스트 실행만으로 Azure 실연동이 증명되는 것은 아니다.

### 10-2. 실제 Azure SDK smoke

현재 [AzureBlobStorageSmokeTest.java](../src/test/java/com/danzzan/infra/azure/AzureBlobStorageSmokeTest.java)는 **`danzzandemo2026/images`로 대상이 하드코딩되어 있다. `AZURE_STORAGE_ENDPOINT`를 바꿔도 이 테스트 대상은 바뀌지 않는다.** 동료의 새 저장소에서 실행하려면 먼저 테스트 대상을 명시적으로 바꾸거나 안전하게 파라미터화하는 코드 작업이 필요하다. 현재 테스트를 그대로 실행해 동료 저장소가 검증됐다고 보고하지 않는다.

기존 대상에서 테스트 이미지 생성·조회·삭제와 소량 과금이 승인된 경우에만:

```sh
AZURE_IMAGE_SMOKE=true ./gradlew test \
  --tests 'com.danzzan.infra.azure.AzureBlobStorageSmokeTest' --rerun-tasks
```

새 `smoke/<UUID>.png` 한 개를 생성하고 바이트 일치, 익명 접근 차단, MIME 거부, 1초 SAS 만료, 새 URL, 삭제 후 404를 확인한다. `finally`에서 자기 테스트 Blob만 삭제한다. 프로세스 강제 종료나 인증 장애로 정리가 실패하면 출력된 **정확한 키**를 기록하고 그 객체만 확인한다. 컨테이너 전체 삭제·일괄 삭제는 하지 않는다.

### 10-3. 앱 공지 검증·정리 절차

1. 독립 시연 DB를 우선 사용한다. 공유 DB라면 기존 행을 변경하지 않도록 대상과 실행 영향부터 확인한다.
2. 고유 제목 `AZURE-E2E-<UUID>`와 작은 새 PNG를 사용한다. 생성된 공지 ID·Blob key를 기록하되 토큰은 기록하지 않는다.
3. 무인증 업로드 거부, 잘못된 MIME·빈 파일·초과 용량의 거부, 정상 업로드를 확인한다.
4. 새 공지만 등록·수정한다. DB 이미지 값이 `azure:images/<key>`인지, `sig=`가 저장되지 않는지 확인한다.
5. 공개 공지 API와 실제 FE 상세 화면에서 이미지 표시를 확인한다.
6. 별도 검증 서버에서만 SAS TTL을 짧게 바꿔 만료 URL의 403, 갱신 URL의 200, 브라우저 자동 갱신을 확인한다. 정상 설정은 300초로 복구한다.
7. 업로드 실패 시 성공한 것처럼 공지가 등록되지 않는지 확인한다. 실제 저장소 장애를 유발하지 말고 자동 테스트와 실검증 결과를 구분한다.
8. 테스트 공지를 삭제한다. 소프트 삭제·첨부 잔여 여부를 확인하고, 물리 정리가 필요하면 **정확한 ID와 UUID 제목 모두 일치하는 행만** 별도 승인된 트랜잭션에서 정리한다.
9. 생성한 Blob key만 삭제하고 해당 객체의 exists=false 또는 읽기 404를 확인한다. 복구용 soft delete 3일은 유지한다.
10. 기존 게시물·첨부의 내용과 수가 보존됐는지, 이번 테스트 데이터가 남지 않았는지 확인하고 임시 서버만 종료한다.

이전 공지 E2E는 임시 JUnit harness로 실행했고 일반 저장소에 재사용 가능한 E2E 명령으로 편입하지 않았다. `/tmp`의 개인 환경 스크립트·로그에는 환경 의존성과 인증 정보가 있을 수 있으므로 동료에게 복사하지 않는다. 재실행 시 정식 테스트 준비와 대상 확인이 필요하다.

## 11. 실제로 검증한 결과와 한계

| 날짜 | 실행한 검증 | 결과 |
| --- | --- | --- |
| 10-12 | 실제 Azure SDK smoke | 1개 통과. 업로드·바이트 일치·익명 접근 차단·MIME 거부·만료 403·갱신 200·삭제 404 |
| 10-13 | 로컬 실제 DB + Azure 공지 API | 테스트 공지 ID 47 생성·수정·조회·삭제, canonical reference 저장 확인 |
| 10-13 | Vite 실제 브라우저 | 공지 상세의 1×1 테스트 PNG 표시, 새로고침 없이 SAS 갱신·이미지 유지 확인 |
| 10-13 | 테스트 데이터 정리 | 기존 공지 4개·첨부 14개 유지, 전후 전체 행 내용 해시 일치, 테스트 공지 0개, 테스트 Blob exists=false |
| 10-13 | BE 전체 테스트 | 482개 중 469 통과·13 건너뜀·실패/오류 0. 커밋 전 `./gradlew test --rerun-tasks` 재실행 및 XML 집계 확인 |
| 10-13 커밋 전 | FE 테스트·typecheck·lint·build | 122개 파일/555개 테스트 통과, typecheck·lint·build 모두 재실행 성공 |

실연동에서 SDK가 Blob 이름의 `/`를 `%2F`로 인코딩하여 저장 요청이 400이 되는 문제를 수정했다. 한 번 디코딩한 뒤 키를 검증하며, 실제 SDK URL을 사용하는 회귀 테스트로 확인했다.

10-14 VM 배포에서 추가 검증한 항목은 [배포 기록](azure-vm-deployment.md)에 있다. Docker 내부 관리 ID, 새 DB의 `ddl-auto: validate`, HTTPS 및 인증서 갱신 모의 실행, 300초 SAS 만료·재발급, 서비스 재시작 후 데이터 보존을 확인했다.

남은 미검증 범위:

- 관리자 로그인·파일 선택 UI를 포함한 모든 화면의 수동 E2E
- 장시간 체류 및 실제 Azure 장애 상황
- VM에서의 실제 메모리·CPU 부하 시험
- VM 자체 재부팅/할당 취소 후 DB·Redis·Kafka 데이터 영속성
- 카카오맵 도메인 등록 후 동작과 미설정 메일·문자·AI 외부 연동

## 12. 문제 발생 시 확인 순서

| 증상 | 먼저 확인할 것 |
| --- | --- |
| `images`를 만들었지만 Entra 목록 조회 거부 | Data Contributor가 실제 최종 할당됐는지, 대상 계정·컨테이너 범위, 반영 시간 |
| 역할이 여전히 Owner만 표시 | 검토 화면에서 두 번째 최종 할당을 눌렀는지, 성공 알림·역할 목록 |
| 적격 할당에서 P2 라이선스 오류 | 일반 역할 할당 탭으로 돌아감. 이 작업 때문에 PIM 라이선스를 구입하지 않음 |
| Blob 업로드는 되지만 SAS 발급 실패 | 계정 범위 위임 키 권한(Delegator 또는 해당 관리 권한), 로그인 테넌트 |
| 로컬에서 ManagedIdentity 인증 실패 | `AZURE_STORAGE_CREDENTIAL=azure-cli`, 호스트 `az login`, BE 프로세스의 az PATH |
| Docker에서 CLI 인증 실패 | 호스트 로그인은 컨테이너에 전달되지 않음. 로컬 호스트 BE로 먼저 확인 |
| 이미지 URL이 잠시 후 403 | SAS의 만료, 서버 시간, `/images/read-url` 요청·응답, 갱신 권한 |
| 익명 URL이 409 | 현재 계정은 `PublicAccessNotPermitted` 409를 반환할 수 있음. SAS 없는 접근 차단 확인 |
| 새 저장소로 바꾸자 기존 이미지 404 | DB reference만 있고 새 계정에 Blob이 없는지. endpoint 변경은 파일 이관이 아님 |
| Blob PUT CORS 오류 | 현재 계약은 BE multipart. 오래된 직접 업로드 호출이 남았는지 먼저 확인 |
| Azure presign 400 | 의도한 동작. 현재 FE는 multipart 경로 사용 |
| VM 생성 후보가 안 보임 | 올바른 구독·지역, Compute 등록, 총 vCPU/제품군 쿼터, SKU 제한·실제 용량 |

## 13. VM 선택과 예상 비용

**10-14 갱신:** 사용자 승인 후 D2as_v4 VM·네트워크·관리 ID를 생성했다. VM 단가는 $0.118/시간, 168시간의 VM·E6 디스크·공인 IP 기본 합계는 약 **$21.77**이다. 실제 구성과 검증은 [VM 생성 기록](azure-vm-infrastructure.md)을 따른다. 아래 표는 10-13의 초기 후보 비교이며 현재 선택을 뜻하지 않는다.

목표: x86 Linux VM 한 대에서 FE/Nginx·Spring Boot·MySQL·Redis·Kafka 운영, 이미지는 별도 Blob. Kafka는 소비자·outbox 때문에 유지한다. 이미지 연동 검증을 먼저 끝내고 VM을 준비한다.

현재 로컬 컨테이너 단일 측정: BE 757.3MiB, MySQL 795.8MiB, Redis 20.77MiB, Kafka 1.065GiB, 합계 약 2.6GiB. OS·Nginx·부하 증가를 고려해 **2 vCPU / 8GiB RAM / 64GiB Standard SSD E6 LRS**를 후보로 삼았다. 이것은 동시 접속 처리량 보증이나 VM 부하 테스트 결과가 아니다.

2026-10-13 Azure Retail Prices API, Korea Central, Linux 일반 종량제, 7일=168시간, 월 환산 730시간 기준:

| 후보 | VM 시간당 | VM 일주일 | E6 64GiB + Standard 공인 IPv4 포함 기본 비용 |
| --- | ---: | ---: | ---: |
| B2as_v2 (2 vCPU / 8GiB) | $0.0936 | $15.72 | $17.67 |
| B2s_v2 (2 vCPU / 8GiB) | $0.104 | $17.47 | $19.42 |
| **D2as_v5 (2 vCPU / 8GiB)** | **$0.106** | **$17.81** | **$19.75** |
| D2s_v5 (2 vCPU / 8GiB) | $0.118 | $19.82 | $21.77 |

초기 추천 후보는 D2as_v5였지만 10-14 공급자 등록 후 해당 제품군 쿼터가 0임을 확인했다. 실제 생성은 쿼터가 확보된 D2as_v4로 진행했다. B 계열 CPU 크레딧 소진에 따른 성능 제한을 피하려는 선택이며, 학생 구독의 금전 크레딧과 B 계열 CPU 크레딧은 서로 다르다.

- E6: 월 $4.80, 168시간 약 $1.10. **I/O 작업 비용 별도**.
- Standard 공인 IPv4: 시간당 $0.005, 168시간 $0.84.
- Hot LRS Blob 첫 저장 구간: GB당 월 $0.02. 읽기/쓰기 요청·인터넷 송신 등 별도.
- 평가 일주일 예산은 소규모 사용을 가정해 $25~30 정도를 계획했지만 과금 상한은 아니다. 사전 테스트 시간·세금·할인·실제 청구 단가는 별도로 확인한다.
- 할당 취소 후에도 E6 디스크 + 공인 IP를 보관하면 월 약 **$8.45 + Blob 비용**. 일정이 미정인 동안 너무 일찍 생성하면 대기 비용이 쌓인다.
- 10-13에는 B2ms가 `NotAvailableForSubscription`, 표의 네 SKU는 제한 없음으로 조회됐지만 Compute 미등록으로 쿼터를 확인하지 못했다. **SKU 제한 없음과 제품군 쿼터 확보는 별개**이며, 실제 생성 가능 여부는 10-14 다시 확인했다. 동료 구독에는 이 결과를 그대로 적용하지 않는다.

[가격 API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices), [디스크 과금](https://azure.microsoft.com/en-us/pricing/details/managed-disks/), [B 계열 CPU 크레딧](https://learn.microsoft.com/en-us/azure/virtual-machines/sizes/b-series-cpu-credit-model), [VM 상태별 과금](https://learn.microsoft.com/en-us/azure/virtual-machines/states-billing)

### 전체 앱 배포 상태와 평가 전 체크 항목

현재 사용자 구독에서는 VM 사양 확정, 공급자 등록, VM·네트워크 생성, 관리 ID 역할 할당, 새 DB 기반 앱 배포와 HTTPS 설정을 진행했다. 실제 운영 절차는 [Azure VM 시연 배포 기록](azure-vm-deployment.md)을 따른다. 동료 구독에서는 인프라 사전 확인부터 다시 진행한다.

1. 동료 구독·테넌트·남은 크레딧과 평가 일정을 확정한다.
2. 해당 구독의 SKU·지역·총/제품군 vCPU 쿼터와 최종 견적을 확인한다. Compute 등록·리소스 생성은 승인 후 진행한다.
3. Azure 전용 Compose와 비밀 주입 설정을 준비한다. 현재 `docker-compose.prod.yml`의 NHN 고정 Kafka 주소와 host network를 그대로 사용하지 않는다.
4. 새 DB 스키마·시연 데이터를 준비한다. `ddl-auto: validate`는 빈 DB 스키마를 자동으로 만들어 주지 않으므로 schema bootstrap이 필요하다.
5. VM에는 시스템 할당 관리 ID를 사용하고, 7번의 Blob 데이터 역할·위임 역할을 부여한다. 현재 Java 코드는 사용자 할당 관리 ID 선택용 client ID를 지정하지 않는다.
6. BE를 `prod,azure` + `AZURE_STORAGE_CREDENTIAL=managed-identity`로 구성하고 실제 컨테이너에서 관리 ID 인증을 검증한다. SDK 라이브러리 추가만으로 VM IAM 설정이 완료되는 것은 아니다.
7. OS 영구 디스크의 Docker 볼륨에 MySQL·Redis·Kafka 데이터를 둔다. 서비스 재시작 후 MySQL 행 수·Redis AOF 키·Kafka 독립 topic 메시지 보존을 확인했고 검증용 키·topic은 정리했다. Azure 할당 취소/재할당 후 보존은 아직 검증하지 않았다. 데이터에 임시 디스크를 사용하지 않는다. `docker compose down -v`는 볼륨을 삭제하므로 사용하지 않는다.
8. Nginx 업로드 본문 크기·타임아웃·API 경로, HTTPS, 필요한 인바운드 포트만 공개하는 방화벽을 검증한다. DB·Redis·Kafka 포트를 인터넷에 공개하지 않는다. Certbot renewal dry-run은 성공했다.
9. JVM/Kafka 메모리, MySQL 버퍼, 스레드 수, Docker 로그 회전, 디스크 여유를 실제 부하에 맞게 조정한다. VM 내부 빌드의 순간 메모리도 별도 고려한다.
10. 실제 테스트 공지·이미지만 사용해 업로드·조회·만료·삭제와 핵심 시연 흐름을 확인한다. 5분 SAS 만료 후 403, BE 읽기 URL 재발급 후 PNG 200, 브라우저 이미지 로드를 확인했다. 임시 공지 ID 2와 신규 Blob은 정확한 ID·UUID 제목 guard를 거쳐 정리했다.
11. 준비가 끝나면 **중지(할당 취소)**하고 상태를 확인한다. 게스트 OS 내부 종료만으로는 컴퓨팅 요금이 멈추지 않을 수 있다.
12. 평가 전에 다시 켜고 재접속·서비스 상태·데이터·이미지를 확인한다. 할당 취소 후 재가동 시 지역 용량 확보는 보장되지 않으므로 평가 직전까지 기다리지 않는다.

## 14. 인수인계 완료 체크리스트

- [x] BE·FE 이미지 전환 코드·문서 커밋 완료. 원격 전달은 별도
- [x] 현재 VM·저장소는 본인 학생 구독 및 동일 테넌트 사용으로 확정
- [ ] 기존 저장소 공동 사용 또는 새 저장소 준비 결정
- [x] 비공개 컨테이너, HTTPS, Shared Key 비활성, 3일 soft delete 및 renewal dry-run 성공 확인
- [ ] 동료 개발자의 데이터 역할·위임 권한과 CLI 로그인 확인
- [ ] 새 endpoint를 BE에 명시하고 smoke 하드코딩 대상 확인
- [x] 임시 공지·새 이미지 정리와 서비스 재시작 보존 확인
- [ ] VM 할당 취소·재시작 후 서비스와 영구 볼륨 보존 확인
- [x] Azure Compose·새 DB·시연 데이터 준비 및 Docker 내부 관리 ID 검증
- [ ] 평가 일정·중지 후 보관 비용·종료 후 보관/삭제 정책 확정

10-13에는 이미지 전환 코드·테스트·문서를 BE·FE에 각각 커밋했다. 10-14에는 별도 사용자 승인에 따라 VM·네트워크 생성과 VM 관리 ID 역할 할당·인증 검증, 새 DB 기반 앱 배포와 HTTPS 구성을 수행했다. 기존 이미지/게시물은 수정·삭제하지 않았다. 배포 파일과 문서는 BE·FE 각 작업 브랜치의 커밋 대상이며, 원격 푸시는 별도 작업이다.
