# SPDX-License-Identifier: GPL-3.0-only
import uvicorn
from configuration import load_config

if __name__ == "__main__":
    config = load_config()
    uvicorn.run(
        "server:app",
        host=config.get("listen_host", "127.0.0.1"),
        port=config.get("listen_port", 8768),
        proxy_headers=False,
        access_log=False,
        log_level="warning",
    )
