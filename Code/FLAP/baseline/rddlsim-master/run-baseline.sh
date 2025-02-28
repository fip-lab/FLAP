#!/bin/bash
dir='/home/fip/Y-Code/FWP/baseline-code/rddlsim-master/domain-name'
#run baseline
# ./run-baseline.sh 178662(seed)
localhost="localhost"


for inst in $(cat files/Domains/aaubunut-baseline.txt)
do
 ./run rddl.competition.Client files/Domains localhost randoriginal$inst rddl.policy.RandomConcurrentPolicy1 2356 $1 $inst
done


