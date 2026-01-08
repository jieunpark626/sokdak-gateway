#!/bin/bash

# 1. 이전 컨테이너 종료 및 삭제
echo "Stopping old gateway container..."
docker stop sokdak-gateway || true
docker rm sokdak-gateway || true

# 2. 새로운 이미지 빌드
echo "Building new Gateway image..."
docker build -t sokdak-gateway:latest .

# 3. 새로운 컨테이너 실행
echo "Starting new Gateway container..."
docker run -d \
  --name sokdak-gateway \
  -p 80:8000 \
  --env-file .env \
  --network host \
  --restart always \
  sokdak-gateway:latest

echo "Gateway deployment successful!"
docker ps | grep sokdak-gateway