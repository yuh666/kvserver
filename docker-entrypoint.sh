#!/bin/bash

if  [ ! -n "$serverPort" ] ;then
    echo "serverPort must not be null"
    exit 1
fi


if  [ ! -n "$serverPeers" ] ;then
    echo "serverPeers must not be null"
    exit 1
fi


exec /usr/bin/java -jar -Dserver.port=$serverPort -Dserver.peers=$serverPeers /kvserver/kvserver-0.0.1-SNAPSHOT.jar
