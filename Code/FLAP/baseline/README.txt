run Simple-Max

Examples：
	run Simple-Max server：（run from rddlsim-master directory）
		 ./run rddl.competition.ServerMaxSimple files/Domains 75 7200 500 files/Domains 2367 10

	run Simple-Max client：（run from rddlsim-master directory）
		 ./run rddl.competition.Client files\Domains localhost originclient2 rddl.policy.RandomConcurrentPolicy 2323 123456 manufacturer_inst_mdp__01

	run Simple-Max sogbofaclient:（run from SOGBOFA-master directory）
		./run-sogbofa.sh 2323




run Single

random Examples：
	run server：（run from rddlsim-master directory）
		 ./run rddl.competition.ServerMaxSimple files/Domains 75 7200 500 files/Domains 2323 10

	run client：（run from rddlsim-master directory）
		 ./run rddl.competition.Client files\Domains localhost originclient2 rddl.policy.RandomConcurrentPolicy 2323 123 manufacturer_inst_mdp__01
		

sogbofa Examples：
	run sogbofa server：（run from SOGBOFA-master directory）
		 ./run_server Domains 2323 75 1 10000 500
	run sogbofa sogbofaclient:（run from SOGBOFA-master directory）
		./run_sogbofa traffic_inst_mdp__01 2323
