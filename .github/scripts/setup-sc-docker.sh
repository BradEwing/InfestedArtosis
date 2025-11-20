#!/usr/bin/env bash
set -e -x -o pipefail

CACHE_FILE="/tmp/docker-cache/starcraft-game.tar"

git clone https://github.com/Bytekeeper/sc-docker.git
cd sc-docker

pip3 install setuptools wheel
python3 setup.py bdist_wheel
pip3 install dist/basil_scbw-1.1.0-py3-none-any.whl

if [ -f "$CACHE_FILE" ]; then
  echo "Loading cached Docker images..."
  docker load -i "$CACHE_FILE"
else
  echo "No cache found, building images..."
  
  cd docker
  ./build_images.sh
  cd ../..
  
  mkdir -p /tmp/docker-cache
  docker save -o "$CACHE_FILE" starcraft:game
fi

scbw.play --install

