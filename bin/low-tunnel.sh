#!/usr/bin/env bash
set -ex

ssh -vv -N -T -L \
	8119:localhost:8118 low cat -
