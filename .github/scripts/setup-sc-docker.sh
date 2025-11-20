#!/usr/bin/env bash
set -e -x -o pipefail

git clone https://github.com/Bytekeeper/sc-docker.git
ls -la
cd sc-docker
pip3 install setuptools wheel
python3 setup.py bdist_wheel
pip3 install dist/scbw*.whl
cd docker
./build_images.sh
cd ..
scbw.play --install

