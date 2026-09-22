# Деплой на сервер (reg.ru VPS)

## 1. Подготовка сервера

### 1.1. Установка Docker и Docker Compose

```bash
# Обновление пакетов
sudo apt update && sudo apt upgrade -y

# Установка Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Проверка
docker --version
docker compose version
```

### 1.2. Клонирование репозитория

```bash
sudo mkdir -p /opt/pii-security-module
sudo chown $USER:$USER /opt/pii-security-module
cd /opt/pii-security-module
git clone <URL_репозитория> .
```

### 1.3. Настройка SSH-ключа для GitHub Actions

На сервере сгенерируйте SSH-ключ:

```bash
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/github_actions -N ""
```

Добавьте публичный ключ в `~/.ssh/authorized_keys`:

```bash
cat ~/.ssh/github_actions.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Приватный ключ (`~/.ssh/github_actions`) — добавьте в секреты GitHub.

## 2. Настройка секретов GitHub

В репозитории: **Settings → Secrets and variables → Actions → New repository secret**

| Секрет | Значение |
|--------|----------|
| `SERVER_HOST` | IP-адрес сервера (например, `123.45.67.89`) |
| `SERVER_USER` | Пользователь SSH (например, `ubuntu` или `root`) |
| `SERVER_SSH_KEY` | Приватный SSH-ключ (содержимое `~/.ssh/github_actions`) |

## 3. Автодеплой

При push в ветку `main` GitHub Actions:
1. Собирает и тестирует приложение (job `test`)
2. Деплоит на сервер через SSH (job `deploy`)

```bash
git add .
git commit -m "Update"
git push origin main
```

## 4. Ручной деплой

Если нужно задеплоить вручную:

```bash
cd /opt/pii-security-module
git pull origin main
docker compose up -d --build
```

## 5. Проверка

```bash
# Health check
curl http://<SERVER_IP>:8080/actuator/health

# Маскирование
curl -X POST http://<SERVER_IP>:8080/process \
  -H "Content-Type: application/json" \
  -d '{"payload":"паспорт 4509 123456","payload_id":"demo-1"}'

# Метрики
curl http://<SERVER_IP>:8080/actuator/prometheus
```

## 6. Открытие портов

На reg.ru откройте порты в файрволе:
- `8080` — приложение
- `6379` — Redis (только для внутреннего использования, не открывать наружу)

```bash
sudo ufw allow 8080/tcp
sudo ufw allow OpenSSH
sudo ufw enable
```

## 7. Секреты приложения

Конфигурация через переменные окружения в `docker-compose.yml`:
- `SPRING_DATA_REDIS_HOST` — хост Redis (по умолчанию `redis`)
- `SPRING_DATA_REDIS_PORT` — порт Redis (по умолчанию `6379`)
- `PII_STORE_TYPE` — тип хранилища (`redis`)
- `SERVER_PORT` — порт приложения (по умолчанию `8080`)