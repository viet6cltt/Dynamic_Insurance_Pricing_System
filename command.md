Dùng lệnh này để build lại image và recreate đúng service vừa sửa:

  `docker compose -f docker-compose.full.yml up -d --build --force-recreate <service-name>`

Ví dụ với authorization-server:

  `docker compose -f docker-compose.full.yml up -d --build --force-recreate authorization-server`

Xem tên service trong compose:

  `docker compose -f docker-compose.full.yml config --services`

Xem log sau khi chạy lại:

  `docker compose -f docker-compose.full.yml logs -f authorization-server`

Dừng toàn bộ container nhưng không xóa container:

  `docker compose -f docker-compose.full.yml stop`

Dừng và xóa toàn bộ container cùng network:

  `docker compose -f docker-compose.full.yml down`

Dùng, xóa container, network và volume:

  `docker compose -f docker-compose.full.yml down -v`
