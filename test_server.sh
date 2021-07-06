#!/usr/bin/env bash

for i in {9495..9510}
do
  python3 websocket_client.py -l "$i" -s "85.214.78.6" -p 5000 &
done

jobs

sleep 20s

for i in {16..1}
do
  kill %"$i"
  sleep 3s
done