FROM openjdk:11-stretch

RUN apt-get update -y && \
    apt-get install -y --no-install-recommends \
    libxrender1 \
    libxtst6 \
    libxi6 && \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*

WORKDIR /javalibs
RUN wget https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow_jni-cpu-linux-x86_64-1.14.0.tar.gz && \
    tar xf libtensorflow_jni-cpu-linux-x86_64-1.14.0.tar.gz
ENV LD_LIBRARY_PATH=/javalibs

WORKDIR /root
ADD . /root/SimpleAsteroids
WORKDIR /root/SimpleAsteroids

RUN ./gradlew build

CMD java -cp build/libs/SimpleAsteroids.jar spinbattle.network.SpinBattleServer
