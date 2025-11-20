#!/usr/bin/env bash
set -e -x -o pipefail

git clone https://github.com/Bytekeeper/sc-docker.git
cp it/sc-docker-support/*.dockerfile sc-docker/docker/dockerfiles
pushd sc-docker
pip3 install wheel
python3 setup.py bdist_wheel
pip3 install dist/scbw*.whl
cd docker
./build_images.sh
popd
scbw.play --install

