# python 2.7

import json
import sys
import time
import datetime
import socket
import subprocess
from random import randrange

ADB = "/Users/doug/Library/Android/sdk/platform-tools/adb"
#192.168.1.173:8082
PHONE_SERVER_IP = "127.0.0.1" #"192.168.1.173" #127.0.0.1" #"192.168.1.191" #"127.0.0.1"
PHONE_SERVER_PORT = 8082

def start_app():
	# make sure phone is awake and exposure notification app is running on phone
	#command = ADB + ' shell input keyevent 26'
	#command = ADB + ' -s HT85G1A05551 forward tcp:8081 tcp:8081\n' # forward localhost:8081 to phone:8081
	command = ADB + ' forward tcp:8082 tcp:8082\n' # forward localhost:8081 to phone:8081
	#command += ADB + ' shell am start -n "com.google.android.apps.exposurenotification.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER'
	p = subprocess.Popen(command, shell=True,
											 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	stdout, stderr = p.communicate()
	print('%s%s'%(stdout,stderr))
	time.sleep(1) # might take a while for phone to wake up

def open_conn():
	# open connection to server on phone
	s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	s.connect((PHONE_SERVER_IP, PHONE_SERVER_PORT))
	return s

def read_line(s,endline='\n'):
	line=""
	i=0
	while 1:
		res = s.recv(1)
		if (not res) or (res.decode("ascii") == endline): break
		line += res.decode("ascii")
	return line

def read_line_ok(s, cmd):
	# if response is not 'ok', bail
	resp = read_line(s)
	if (resp != 'ok'):  # something went wrong
		print(cmd + " : " + resp)
		exit(-1)

def send_cmd(s,cmd, readline = True):
	# send a command to the phone
	msg = bytearray()
	msg.extend(cmd.encode("ascii"))
	s.sendall(msg)
	if (readline):
		read_line_ok(s,cmd)

def start():
	send_cmd(s,"START\n")
	return

def stop():
	send_cmd(s,"STOP\n")
	return

def new():
	send_cmd(s,"NEWTEK\n")
	return
	
def get():
	send_cmd(s,"GETTEKS\n")
	return read_line(s,"#")

def startscan():
	send_cmd(s,"SCANON\n")
	return

def stopscan():
	send_cmd(s,"SCANOFF\n")
	return

def gaenscan():
	send_cmd(s,"SCANGAEN\n")
	return

on=300; off=900;
start_app()
print("opening socket ...")
s=open_conn()
print("done")

#test
#tek = "5007cd7ed50270e3c1359ecbd01b6803"
#rpi = "24970c8d7b256313d5f73950b685119b"
#aem = "38040a5f" # 40e10000
# value for myphone from luas test 18th june
tek="9dc84d4dcfc7d86ec5f8ce6d67d6ebc5"
rpi="3E8D23BE31031CB7C4EB50F09FB11215";
aem="F209C483"
# value for myphone from manyphones test 20th june
tek="c2b34b3aaeb339d84df02e2cb8f1d13f"
rpi="62242FE6905594F78613E89A73676FF9";
aem="C3ECBC52"
send_cmd(s,"DECRYPTAEM "+tek.lower()+" "+rpi.lower()+" "+aem.lower()+"\n",False)
print(read_line(s,"#"))
sys.exit(0)

#startscan()
#time.sleep(180)
#stopscan()
gaenscan()
time.sleep(60)
stopscan()
sys.exit(0)

for i in range(0,20):
	print("starting at ",datetime.datetime.now())
	startscan()
	time.sleep(30)
	#print("stopping at ",datetime.datetime.now())
	#stopscan()
	#time.sleep(60)
sys.exit(0)

stop() # in case already running
new()
print("on= ",on," off=",off)
print("starting at ",datetime.datetime.now())
start()
print(get())
time.sleep(on)
print("stopping at ",datetime.datetime.now())
s=open_conn()
stop()
time.sleep(off)
print("starting at ",datetime.datetime.now())
s=open_conn()
start()
time.sleep(on)
print("stopping at ",datetime.datetime.now())
s=open_conn()
print(get())


