#!/bin/bash

mvn -Dmaven.test.skip=true clean package
cp target/kvserver-0.0.1-SNAPSHOT.jar .
docker build -t kvserver:latest .
rm -f kvserver-0.0.1-SNAPSHOT.jar
docker-compose up -d
docker rmi $(docker images -f "dangling=true" -q) > /dev/null 2>&1