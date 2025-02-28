clientNum=$1

for i in {0..50}
do
	if [ $i -ge ${clientNum} ];then
		echo end
		break
	fi
	echo $i
nohup ./run_simSOG.sh $2 localhost &

done
