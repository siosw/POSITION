#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Mon Jan 25 15:03:02 2021

@author: anwaldt
"""

import argparse
import sys
import signal
import socket
import select

from pythonosc import osc_server, dispatcher
import threading
import time
from typing import List, Any

exit_event = threading.Event()

class Connection:

    def __init__(self, conn, addr, cl):

        self.connection = conn
        self.address    = addr
        self.clients    = cl
        
        self.is_connected = True
        
        self.echo_thread = threading.Thread(target=self.echo)
        self.echo_thread.deamon = True
        self.echo_thread.start()    
        
    def echo(self):
    
        while self.is_connected == True:
    
            self.connection.settimeout(10e5)
            
            data = self.connection.recv(1024)
            
            if not data:                
                print('Disconnected!')
                self.is_connected = False
                
                
            else:
                for c in self.clients:            
                    c.connection.sendall(data)
            
        
class TcpOscEcho():
    
    def __init__(self, tcp_port, osc_port):
        self.HOST = ''
        self.PORT = tcp_port
        self.osc_clients = list()
        self.clients = list()
        self.threads = list()

        self.serv_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.serv_sock.settimeout(10e5)
        self.serv_sock.getsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.serv_sock.settimeout(0.2)
        self.serv_sock.bind((self.HOST, self.PORT))
        self.serv_sock.listen(1)

        self.dispatcher  = dispatcher.Dispatcher()       
        self.dispatcher.set_default_handler(self.default_handler)
        self.server = osc_server.ThreadingOSCUDPServer(( "0.0.0.0", osc_port), self.dispatcher) 
        
        self.init_threads()         

    
    def init_threads(self):
        targets = [self.start_server, self.connect_sockets, self.disconnect_sockets]
        for target in targets:
            th = threading.Thread(target=target)
            th.start()
            self.threads.append(th)


    def default_handler(self, address: str, *osc_arguments: List[Any]) -> None:
         l = len(osc_arguments)
         
         for c in self.clients:
 
                data = address
                
                for i in range(l):
                    thisarg = osc_arguments[i]
                    res = isinstance(thisarg, str)
                    if res == False:
                        thisarg = str(thisarg)
                    data=data+" "+thisarg

                if(c.is_connected):
                    c.connection.send(data.encode())


    def start_server(self):
        print("starting the server...")
        th = threading.Thread(target=self.server.serve_forever)
        th.start()

        exit_event.wait()
        self.server.shutdown()
        self.server.server_close()
        print("osc server shut down...")

   
    def connect_sockets(self):
        print("Connected to "+str(len(self.clients))+" clients!")
        print("Ready for new connection")

        while True:
            if exit_event.is_set():
                break

            try:
                conn, addr = self.serv_sock.accept()
            except socket.timeout:
                pass
            except:
                raise
            else:
                self.clients.append(Connection(conn, addr, self.clients))
                print ("Client %s connected" %str(addr))
                print("Connected to "+str(len(self.clients))+" clients!")
                print("Ready for new connection")

        print("connect_sockets shut down...")


    def disconnect_sockets(self):
        while True:
            if exit_event.is_set():
                break

            client_disconnected = False
            for c in self.clients[:]:
                if not c.is_connected:                    
                    self.clients.remove(c)
                    client_disconnected = True
                            
            if client_disconnected:
                print("Connected to "+str(len(self.clients))+" clients!")
            
            time.sleep(1)

        print("disonnect_sockets shut down...")   
            
                
    def keyboardInterruptHandler(self, signal, _frame):
        print("KeyboardInterrupt (ID: {}) has been caught. Cleaning up...".format(signal))
        exit_event.set()
        for t in self.threads:
            t.join()

        # print(self.threads)
        time.sleep(1)
        
        # :(
        try:
            sys.exit(2)
        except:
            pass


if __name__ == "__main__":

    parser = argparse.ArgumentParser()        
    
    parser.add_argument("-o", "--osc-port", dest = "osc_port", default = 5005, help="Port for receiving local OSC messages.")
    parser.add_argument("-t", "--tcp-port", dest = "tcp_port", default = 5000, help="Port for the remote TCP connection.")

    args = parser.parse_args()

    echo = TcpOscEcho(int(args.tcp_port), int(args.osc_port))
    signal.signal(signal.SIGINT, echo.keyboardInterruptHandler)
    
