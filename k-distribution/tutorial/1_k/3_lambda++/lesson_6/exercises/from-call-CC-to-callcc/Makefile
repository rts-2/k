MAKEFILE_PATH := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
include $(MAKEFILE_PATH)/../../Makefile
export KOMPILE_FLAGS=--main-module CALLCC --transition strict
export KOMPILE_BACKEND?=java
