#!/bin/bash

mvn -Dmaven.test.skip=true clean package
mv target/kvserver-0.0.1-SNAPSHOT.jar .
docker build -t kvserver:latest .
docker-compose up
docker rmi $(docker images -f "dangling=true" -q) > /dev/null 2>&1