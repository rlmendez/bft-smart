#!/bin/sh

java -cp lib/netty-3.1.1.GA.jar:lib/slf4j-jdk14-1.5.8.jar:lib/slf4j-api-1.5.8.jar:../dist/SMaRt.jar navigators.smart.reconfiguration.ReconfigurationTest "$@"
