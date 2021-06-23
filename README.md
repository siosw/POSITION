### preparing the Server
* run `sclang simple_SERVER.sc` to start the SuperCollider Server
* the server prints out the `<SERVER_OSC_PORT>`, take note of this
* run `python3 websocket_client.py` to broadcast the position data

### running the GUI
* run `python3 websocket_client.py` to connect to the server via websocket
* run `python3 visual_positions.py -c <YOUR_SOURCE_INDEX> -r <SERVER_IP> -t <SERVER_OSC_PORT>` 

### system overview
```
┌────────────────────┐            ┌────────────────────┐
│Server              │            │Client              │
│                    │            │                    │
│┌──────────────────┐│updated     │┌──────────────────┐│
││                  ││positions   ││                  ││
││  simple_SERVER   ◀┼────────────┼┤ visual_positions ││
││                  ││            ││                  ││
│└──┬───────────────┘│            │└───────────────▲──┘│
│   │                │            │                │   │
│   │positions       │            │       positions│   │
│   │                │            │                │   │
│   │                │establish   │                │   │
│┌──▼───────────────┐│connection  │┌───────────────┴──┐│
││                  ◀┼────────────┼┤                  ││
││ websocket_server ││            ││ websocket_client ││
││                  ├┼────────────┼▶                  ││
│└──────────────────┘│positions   │└──────────────────┘│
└────────────────────┘            └────────────────────┘
```

### list of all possible arguments
```
optional arguments:
  -h, --help            show this help message and exit
  -c CONTROLLED_SOURCE, --controlled-source CONTROLLED_SOURCE
                        Index of the source you want to control
  -i LISTEN_IP, --listen-ip LISTEN_IP
                        IP of the server sending the position data
  -p LISTEN_PORT, --listen-port LISTEN_PORT
                        Port of the server sending the position data
  -r RECV_IP, --recv-ip RECV_IP
  -t RECV_PORT, --recv-port RECV_PORT
  -s SCALE, --scale SCALE
                        set the zoom level
```