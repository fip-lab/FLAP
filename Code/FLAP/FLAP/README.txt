run FWAP

Examples：
run FWA server：（run from rddlsim directory）
	./run rddl.competition.Simserver files/Domains 2323 75 123 10 7200 500 7 result-test.csv 5

run FWA client：（run from rddlsim directory）
	./run-simclient.sh files/Domains localhost clientname1 2323 RandomConcurrentPolicy1

run FWA sogbofaclient:（run from SOGBOFA-master directory）
	./run_simSOG.sh 2323 localhost
