#!/bin/bash
#dir='~/yi/code/FWP/SOGBOFA-master'

for inst in $(cat aaubunut-baseline.txt)
do
 ./run_sogbofa $inst $ 1
done
