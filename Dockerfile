FROM openjdk:latest

WORKDIR /kvserver

COPY kvserver-0.0.1-SNAPSHOT.jar .

COPY docker-entrypoint.sh .

RUN chmod u+x docker-entrypoint.sh

ENTRYPOINT ["/kvserver/docker-entrypoint.sh"]

