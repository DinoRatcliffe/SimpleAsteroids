FROM openjdk:11-stretch

WORKDIR /root
ADD . /root/SimpleAsteroids
WORKDIR /root/SimpleAsteroids

RUN ./gradlew build

CMD java -cp build/libs/SimpleAsteroids.jar spinbattle.network.SpinBattleServer
