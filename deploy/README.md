# 배포 절차

## 디렉토리 구조

```
deploy/
├── systemd/
│   ├── backup-server.service       # systemd unit 파일
│   └── backup-server.env.template  # 환경변수 템플릿 (시크릿 미포함)
└── nginx/
    └── backup-server.conf          # Nginx 리버스 프록시 설정
```

---

## 1. 사전 준비

### OS 사용자/그룹 생성

```bash
sudo useradd -r -s /sbin/nologin backup
```

### 디렉토리 생성

```bash
sudo mkdir -p /var/backup/{incoming,artifacts,trash}
sudo chown -R backup:backup /var/backup
sudo chmod 750 /var/backup

sudo mkdir -p /opt/backup-server
sudo chown backup:backup /opt/backup-server
```

### 시크릿 파일 배치

```bash
sudo mkdir -p /etc/backup-server
sudo cp deploy/systemd/backup-server.env.template /etc/backup-server/backup-server.env
# 실제 값 입력
sudo vi /etc/backup-server/backup-server.env
sudo chmod 600 /etc/backup-server/backup-server.env
sudo chown root:root /etc/backup-server/backup-server.env
```

---

## 2. 애플리케이션 배포

```bash
# 빌드
./gradlew :boot:bootJar

# 서버에 복사
scp boot/build/libs/boot-*.jar SERVER:/opt/backup-server/app.jar
```

---

## 3. systemd 등록

```bash
sudo cp deploy/systemd/backup-server.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable backup-server
sudo systemctl start backup-server

# 상태 확인
sudo systemctl status backup-server
sudo journalctl -u backup-server -f
```

---

## 4. Nginx 설정

`backup-server.conf`에서 `BACKUP_SERVER_DOMAIN`을 실제 도메인으로 교체한다.

```bash
sudo cp deploy/nginx/backup-server.conf /etc/nginx/conf.d/backup-server.conf
sudo nginx -t
sudo systemctl reload nginx
```

---

## 5. 업데이트 배포

```bash
./gradlew :boot:bootJar
scp boot/build/libs/boot-*.jar SERVER:/opt/backup-server/app.jar
ssh SERVER "sudo systemctl restart backup-server"
```
