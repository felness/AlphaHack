"""Конфигурация приложения через pydantic-settings (.env)."""
import json
import secrets
from functools import lru_cache
from typing import Any

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Сервер
    host: str = "0.0.0.0"
    port: int = 8000
    workers: int = 1

    # Безопасность
    admin_key: str = secrets.token_hex(16)
    # Список систем, которым разрешено обращаться (через заголовок X-System-Id)
    allowed_systems: str = "default,test,prod"
    # Разрешить запросы без заголовка X-System-Id (нужно для нагрузочного теста,
    # т.к. контракт /process не предусматривает этот заголовок)
    allow_anonymous: bool = True

    # Логирование
    log_level: str = "INFO"

    # Лимиты
    max_payload_chars: int = 1_000_000  # ~100k токенов
    request_timeout_sec: float = 9.0    # чуть меньше таймаута проверки (10s)

    # Rate-limiting (429 при перегрузке)
    rate_limit_rps: int = 1000          # макс. запросов в секунду на систему
    rate_limit_window_sec: float = 1.0

    # Гибкая настройка по системам (JSON):
    # {
    #   "test": {"mask_types": ["fio","phone"], "allow_unmask": true, "mask_mode": "mask"},
    #   "prod": {"mask_types": ["*"], "allow_unmask": false, "mask_mode": "token"}
    # }
    # "*" = все типы. mask_mode: "mask" (звёздочки) или "token" ({TYPE_N}).
    # Если система не указана — все типы, демаскирование разрешено, режим "mask".
    system_policies: str = "{}"

    @property
    def allowed_systems_list(self) -> list[str]:
        return [s.strip() for s in self.allowed_systems.split(",") if s.strip()]

    @property
    def system_policies_dict(self) -> dict[str, dict[str, Any]]:
        try:
            data = json.loads(self.system_policies)
            return data if isinstance(data, dict) else {}
        except json.JSONDecodeError:
            return {}

    def get_system_policy(self, system_id: str) -> dict[str, Any]:
        """Возвращает политику для системы (с дефолтами)."""
        policies = self.system_policies_dict
        policy = policies.get(system_id, {})
        return {
            "mask_types": policy.get("mask_types", ["*"]),
            "allow_unmask": policy.get("allow_unmask", True),
            "mask_mode": policy.get("mask_mode", "mask"),
        }


@lru_cache
def get_settings() -> Settings:
    return Settings()