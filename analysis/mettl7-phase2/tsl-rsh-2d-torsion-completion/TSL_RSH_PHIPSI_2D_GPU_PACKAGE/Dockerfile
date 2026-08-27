FROM nvidia/cuda:12.4.1-devel-ubuntu22.04
ENV DEBIAN_FRONTEND=noninteractive PIP_NO_CACHE_DIR=1
RUN apt-get update && apt-get install -y python3 python3-pip build-essential git && rm -rf /var/lib/apt/lists/*
RUN python3 -m pip install pyscf==2.14.0 gpu4pyscf-cuda12x==1.8.0 cupy-cuda12x==13.4.1 cutensor-cu12==2.2.0 dftd3==1.5.0 geometric==1.1.1 numpy
WORKDIR /opt/tsl-rsh
COPY . /opt/tsl-rsh
RUN python3 self_verify.py --offline
ENTRYPOINT ["python3","run_2d_gpu.py"]
