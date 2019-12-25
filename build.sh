#!/bin/bash

mvn -Dmaven.test.skip=true clean package
mv target/kvserver-0.0.1-SNAPSHOT.jar .
docker build -t kvserver:latest .