#!/bin/bash

cd /mnt/c/Users/AD33900/kafka_2.13-4.1.0/

KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"

bin/kafka-storage.sh format --standalone -t $KAFKA_CLUSTER_ID -c config/server.properties

nohup bin/kafka-server-start.sh config/server.properties &

bin/kafka-topics.sh --create --topic vehicle-positions --bootstrap-server localhost:9092

bin/kafka-topics.sh --create --topic trip-updates --bootstrap-server localhost:9092
