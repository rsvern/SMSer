#!/usr/bin/python
# Send SMS via SMSer using telnetlib.

import telnetlib
import sys
import argparse

SMSER_IP = '192.168.42.129'
SMSER_PORT = 8484

parser = argparse.ArgumentParser()
parser.add_argument('--phone', required=True,
                    help='target phone number')
parser.add_argument('message', nargs=argparse.REMAINDER)
args = parser.parse_args()

msg = ' '.join(args.message)
if not msg:
    msg = 'Default test message'

tn = telnetlib.Telnet(SMSER_IP, SMSER_PORT)

r = tn.read_until('\r\n', 10)
if r != 'Hello from SMSer\r\n':
  sys.exit(1)

tn.write(args.phone + '\r\n')
r = tn.read_until('\r\n', 10)
if r != 'Address OK\r\n':
  sys.exit(2)

tn.write('%s\r\n' % (msg))
tn.write('.\r\n')

r = tn.read_until('\r\n', 10)

tn.close()
sys.exit(0)
