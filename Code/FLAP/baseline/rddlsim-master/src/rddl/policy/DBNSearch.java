package rddl.policy;

import org.apache.commons.math3.random.RandomDataGenerator;
import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.*;
import rddl.State;
import rddl.competition.ServerMaxSimple;
import rddl.translate.RDDL2Format;
import util.Pair;
import util.Permutation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author: Yi
 * @Date: 2021/11/01
 **/

public class DBNSearch extends Policy {

    public int MAX_CONCURRENT_ACTIONS = 20; // Since we could have: max-nondef-actions = pos-inf;
    public int MAX_INT_VALUE = 5; // Max int value to use when selecting random action
    public double MAX_REAL_VALUE = 5.0d; // Max real value to use when selecting random action
    long t0 = 0;

    public DBNSearch() {
        super();
    }

    public DBNSearch(String instance_name) {
        super(instance_name);
    }

    public void setActionMaxIntValue(int max_int_value) {
        MAX_INT_VALUE = max_int_value;
    }

    public void setActionMaxRealValue(double max_real_value) {
        MAX_REAL_VALUE = max_real_value;
    }


    static boolean shouwMsg = true;
    //存储序列，用于计算ID编码；
    public static ArrayList<String> stateList = new ArrayList<>();
    public static ArrayList<PVAR_INST_DEF> actionList = new ArrayList<>();
    //用于标记状态是否被访问；
    public static HashMap<BigInteger, Boolean> recordStateTag = new HashMap<>();
    //存储state对照表,
    public static HashMap<BigInteger, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>> stateTable = new HashMap<>();

    public static HashMap<BigInteger, HashMap<BigInteger, HashMap<BigInteger, Integer>>> recordState = new HashMap<>();
    //                       state              action               state  , prob

    public static HashMap<BigInteger, HashMap<BigInteger, Pair<Double, Double>>> recordReward = new HashMap<>();
    //                                                         expect，real


    /**
     * @Author Yi
     * @Description
     * @Date 2021/11/12
     * 注意client端的参数设置，传递当前相关信息
     * policy.initialTree(state);
     * policy.setTotalHorizon(instance._nHorizon);
     * policy.setSearchPara(4,3);
     * policy.setHorizon(horizon);
     **/
    public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {
        //add by yi
        _random.reSeed(System.currentTimeMillis());

        ArrayList<PVAR_INST_DEF> actions = new ArrayList<RDDL.PVAR_INST_DEF>();


        long tempTime1 = System.nanoTime();

//        actionList = getActionList(s);
//        stateList = getStateVariableNames(s);

        int searchdeep = 40 < (_totalHorizon - _horizon) ? 40 : (_totalHorizon - _horizon);

        ArrayList<BigInteger> searchStateList = new ArrayList<>();
        State state = ServerMaxSimple.cloneState(s);
        BigInteger stateID = getStateLabel(s);
        searchStateList.add(stateID);
        long timeLimit = 300000000L;
        checkSearchPara();
        //注意设置不同的宽度；
        searchTreeBFS(state, timeLimit, System.nanoTime(), 4, 3, searchdeep, 10, searchStateList);
//        searchTree(state, timeLimit, 5, 10, searchdeep, 10);


        Pair<BigInteger, Double> bestAction = getBestActioDFS(stateID, searchdeep, 10);


        actions = getActionFluent(bestAction._o1, getActionList(s));
        if (shouwMsg)
            System.out.println("^^^^^^^^^get best action:" + actions + ";;mean reward:" + bestAction._o2);

        if (searchdeep == 1 && shouwMsg) {
            System.out.println("%%%%%%%%%%%%%%%% search state size:" + recordState.size());
        }

        long tempTime2 = System.nanoTime();
        // If noop, potentially no action was legal so should check that noop is legal
        if (actions.size() == 0) {
            // Try empty action
            actions.clear();
            try {
                s.checkStateActionConstraints(actions);
            } catch (EvalException e) {
                System.out.println(actions + " : " + e);
                throw new EvalException("No actions satisfied state constraints, not even noop!");
            }
        }

        System.out.println("**Action: " + actions);
        return actions;
    }

    public static class NextState {
        public BigInteger stateId;
        public double problic;
        public double reward;
        public double meanReward;
        //计算期望reward的抽样深度；-1为初始值；
        public int deep = 0;

        NextState(BigInteger stateId, double problic, double reward, double meanReward, int deep) {
            this.problic = problic;
            this.reward = reward;
            this.stateId = stateId;
            this.meanReward = meanReward;
            this.deep = deep;
        }
    }

    //store action name
    public static class FluentPair implements java.io.Serializable {
        public ArrayList<LCONST> terms;
        public String _sPVarName;

        FluentPair(String _sPVarName, ArrayList<LCONST> terms) {
            this.terms = terms;
            this._sPVarName = _sPVarName;
        }

        public String toString() {
            return "<" + _sPVarName.toString() + ", " + terms.toString() + ">";
        }
    }

    public void roundInit(double timeLeft, int horizon, int roundNumber, int totalRounds) {
        // super.roundInit(timeLeft, horizon, roundNumber, totalRounds);


    }

    public void checkSearchPara() {
        if (_stateWideLimit <= 0)
            _stateWideLimit = 3;
        if (_actionWideLimit <= 0)
            _actionWideLimit = 4;
    }

    public void initialTree(State s) {
        System.out.println("\n*********************************************************");
        System.out.println(">>> INITIAL POLICY%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        System.out.println("*********************************************************");

        //initial tree
        stateList = new ArrayList<>();
        actionList = new ArrayList<>();
        stateList = getStateVariableNames(s);
        actionList = getActionList(s);

        recordState = new HashMap<>();
        recordReward = new HashMap<>();
        recordStateTag = new HashMap<>();
        stateTable = new HashMap<>();

        //get time cost of the action random policy ;
        averageStepTime = 0;
        averageComputeTime = 0;
        averageCheckTime = 0;
        averagePerRandomTime = 0;
        getTimePara(s, new RandomDataGenerator());

    }


    public static ArrayList<PVAR_INST_DEF> getDBN(State s, long timeLimit, long stepTime, int deep, ArrayList<PVAR_INST_DEF> action) throws Exception {
        ArrayList<PVAR_INST_DEF> bestAction = new ArrayList<>();
        //construct DBN
        BigInteger stateNumber = getStateLabel(s);
//        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%stateID" + stateNumber);
//        long tempTime = System.nanoTime();
        BigInteger actionNumber = getActionLabel(s, action);
//        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%actionID" + actionNumber);
        //可以遍历
//        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%actionLength" + actionNumber.bitLength());
//        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%actionBit" + actionNumber.testBit(60));
//        System.out.println("action id time =" + (System.nanoTime() - tempTime));
        //使用state进行搜索，
        // 使用StateLabel进行标记（可能会用于后续搜索或者找出环等操作，）
        // 同时存储已经搜索的过程；

        return bestAction;
    }

//    public static ArrayList<Long> distributeTime() {
//        ArrayList<Long> disTime = new ArrayList<>();
//
//
//        return disTime;
//    }


    //深度优先搜索，根据搜索的深度计算action期望；
    //todo rewardMean 计算有问题
    public static Pair<BigInteger, Double> getBestActioDFS(BigInteger stateID, int searchDeep, long timeLimit) {
        if (shouwMsg)
            System.out.println("getBestAction");
        BigInteger bestAction = BigInteger.ZERO;
        Double bestReward = -Double.MAX_VALUE;
        //清除状态标记；
        cleanTag(recordStateTag);
        //更新均值信息；
        Pair<Integer, Double> pa = updateMean(stateID, searchDeep, 1, recordState, recordReward);
        if (shouwMsg) System.out.println("&&&&&&&&&&search deep :" + pa._o1);

        HashMap<BigInteger, Pair<Double, Double>> actionRewardSet = recordReward.get(stateID);
//        if (actionRewardSet == null)
//            return new Pair<>(BigInteger.ZERO, 0.0);
        for (BigInteger actionID : actionRewardSet.keySet()) {
            Pair<Double, Double> pair = actionRewardSet.get(actionID);
            if (shouwMsg)
                System.out.println("^^^^^^^^^reward mean:" + pair._o1 + ";;;;;;imrew:" + pair._o2);
            if (pair._o1 > bestReward) {
                bestAction = actionID;
                bestReward = pair._o1;
            }
        }

        Pair<BigInteger, Double> bestAaction = new Pair<>(bestAction, bestReward);
        return bestAaction;
    }

    //计算state的期望；
    public static Pair<Integer, Double> computeMeanFitDFS(BigInteger stateID/*,ArrayList<PVAR_INST_DEF> action*/, int searchDeep, long timeLimit) {
        if (shouwMsg)
            System.out.println("getStateMean");
        Double sumReward = 0.0;
        //清除状态标记；
        cleanTag(recordStateTag);
        //更新均值信息；
        Pair<Integer, Double> pa = updateMean(stateID, searchDeep, 1, recordState, recordReward);
        if (shouwMsg) System.out.println("&&&&&&&&&&search deep :" + pa._o1);

        HashMap<BigInteger, Pair<Double, Double>> actionRewardSet = recordReward.get(stateID);
//        if (actionRewardSet == null)
//            return new Pair<>(BigInteger.ZERO, 0.0);
        for (BigInteger actionID : actionRewardSet.keySet()) {
            Pair<Double, Double> pair = actionRewardSet.get(actionID);
            sumReward += pair._o1;
        }
        Double meanReward = sumReward / actionRewardSet.size();

        return new Pair<>(pa._o1, meanReward);
    }


    public static Pair<Integer, Double> updateMean(BigInteger stateID, int searchDeep, Integer deep/*搜索初始为1*/,
                                                   HashMap<BigInteger, HashMap<BigInteger, HashMap<BigInteger, Integer>>> recordState,
                                                   HashMap<BigInteger, HashMap<BigInteger, Pair<Double, Double>>> recordReward) {
        if (!recordReward.containsKey(stateID) || recordState.get(stateID) == null || recordReward.get(stateID).size() <= 0)
            return new Pair<>(deep - 1, 0.0);
        Double meanReward = 0.0;
        HashMap<BigInteger, Pair<Double, Double>> actionRewardSet = recordReward.get(stateID);

        if (recordStateTag.get(stateID).equals(true)) {
            //当前state被访问，动作的期望就是执行的reward值
            for (BigInteger actionID : actionRewardSet.keySet()) {
                Pair<Double, Double> pair = actionRewardSet.get(actionID);
                meanReward += pair._o2 / deep;
            }
            meanReward = meanReward / recordReward.get(stateID).size();
            recordStateTag.put(stateID, true);
            return new Pair<>(deep, meanReward);
        } else if (searchDeep <= 1) {
            //第零层，动作的期望就是执行的reward值；

            for (BigInteger actionID : actionRewardSet.keySet()) {
                Pair<Double, Double> pair = actionRewardSet.get(actionID);
                pair._o1 = pair._o2 / deep;
                meanReward += pair._o1;
            }
            meanReward = meanReward / recordReward.get(stateID).size();
            recordStateTag.put(stateID, true);
            return new Pair<>(deep, meanReward);
        } else {
            recordStateTag.put(stateID, true);
            //非零层，动作期望是后面状态动作的期望和；
            //记录最大搜索深度；
            Integer maxDeep = 1;
            for (BigInteger actionID : actionRewardSet.keySet()) {
                Double nextMeanReward = 0.0;
                Pair<Double, Double> pair = actionRewardSet.get(actionID);
                //记录单个动作的最大搜索深度；
                Integer actionDeep = 1;
                if (recordState.get(stateID).get(actionID) != null) {
//                    HashMap<BigInteger, Integer> nextStateSet = recordState.get(stateID).get(actionID);
                    HashMap<BigInteger, Double> nextStateProb = computeStateProb(stateID, actionID, recordState);
                    for (BigInteger nextState : nextStateProb.keySet()) {
                        //next state 的期望 == stateMean*prob的求和；同时避免重复访问节点；
                        Pair<Integer, Double> getMeanReward = updateMean(nextState, searchDeep - 1, deep + 1, recordState, recordReward);
//                        System.out.println("^^^^^^^^^^^^^^deep:" + getMeanReward._o1 + "^^^^^^^^^^^^^^search deep:" + maxDeep);
                        nextMeanReward += nextStateProb.get(nextState) * getMeanReward._o2;
                        if (actionDeep < getMeanReward._o1)
                            actionDeep = getMeanReward._o1;
                    }
                }
                pair._o1 = pair._o2 / actionDeep + nextMeanReward;
                meanReward += pair._o1;
                if (maxDeep < actionDeep)
                    maxDeep = actionDeep;
            }
            //返回当前状态，的所有动作的均值；
            meanReward = meanReward / recordReward.get(stateID).size();
//            System.out.println("max deep " + maxDeep);

            return new Pair<>(maxDeep, meanReward);
        }

    }

    public static void cleanTag(HashMap<BigInteger, Boolean> stateTag) {
        for (BigInteger stateID : stateTag.keySet()) {
            stateTag.put(stateID, false);
        }
    }

    //更新状态的概率，使得计算后继状态概率更加准确；
    public static HashMap<BigInteger, Double> computeStateProb(BigInteger stateID, BigInteger actionID,
                                                               HashMap<BigInteger, HashMap<BigInteger, HashMap<BigInteger, Integer>>> recordState) {
        if (!recordState.containsKey(stateID) || !recordState.get(stateID).containsKey(actionID))
            return null;
        HashMap<BigInteger, Double> stateProb = new HashMap<>();
        Double count = 0.0;
        HashMap<BigInteger, Integer> stateSet = recordState.get(stateID).get(actionID);
        for (BigInteger state : stateSet.keySet()) {
            count += stateSet.get(state);
        }
        for (BigInteger state : stateSet.keySet()) {
            stateProb.put(state, new Double(stateSet.get(state) / count));
        }
        return stateProb;
    }

    public static void outputInfo() {
        System.out.println("----------------------------searchTree--------------------------");

        System.out.println("!!!!!!!!!!!!!!!!!!stateList");
        System.out.println(stateList);

        System.out.println("-------------------------outputEnd-----------------------------");
    }

    public static long averageStepTime = 0;
    public static long averageComputeTime = 0;
    public static long averageCheckTime = 0;
    public static long averagePerRandomTime = 0;
    public static long timePerLayer = 0;

    //根据当前的时间分配搜索宽度和深度大小；
    public static ArrayList<Integer> distributeSource(long remainTime) {
        ArrayList<Integer> arrange = new ArrayList<>();
        if (remainTime > timePerLayer)
            //执行一层；
            ;
        return arrange;

//        否则，消耗完时间，同时终止剩下的嵌套；
    }

    //计算action的期望值，用于评估nextState；
    public Double getStateActionFitness(State s, ArrayList<PVAR_INST_DEF> action, Long time, int stateNum, int searchdeep) {

        Long timeLimit = time * 1000 * 1000 /*/ stateNum*/;
        State state = ServerMaxSimple.cloneState(s);
        BigInteger stateID = getStateLabel(s);
        BigInteger actionID = getActionLabel(s, action);

        double currentReward = 0.0;

        ArrayList<BigInteger> nexthStateList = new ArrayList<>();

        HashMap<BigInteger, HashMap<BigInteger, Integer>> actionSet = null;
        HashMap<BigInteger, Pair<Double, Double>> actionReward = null;
        if (recordState.containsKey(stateID))
            actionSet = recordState.get(stateID);
        else {
            actionSet = new HashMap<>();
            recordState.put(stateID, actionSet);
        }
        if (recordReward.containsKey(stateID))
            actionReward = recordReward.get(stateID);
        else {
            actionReward = new HashMap<>();
            recordReward.put(stateID, actionReward);
        }
        if (!recordStateTag.containsKey(stateID))
            recordStateTag.put(stateID, false);

        //simulate next state
        for (int i = 0; i < stateNum; i++) {
            if (actionSet.containsKey(actionID)) {
                //action重复时，减少搜索次数,增加深度搜索
                i++;

                HashMap<BigInteger, Integer> nextStateSet = actionSet.get(actionID);


                BigInteger nextState = simulateAction(s, action, new RandomDataGenerator());
                //添加进搜索列表；
                nexthStateList.add(nextState);

                if (nextStateSet.containsKey(nextState)) {
                    //compute probability
                    nextStateSet.put(nextState, new Integer(nextStateSet.get(nextState) + 1));
                } else {
                    //compute probability
                    nextStateSet.put(nextState, new Integer(1));
                }
                currentReward = actionReward.get(actionID)._o1;

            } else {

                HashMap<BigInteger, Integer> nextStateSet = new HashMap<>();

                actionSet.put(actionID, nextStateSet);

                BigInteger nextState = simulateAction(s, action, new RandomDataGenerator());

                nextStateSet.put(nextState, new Integer(1));

                //添加进搜索列表；
                nexthStateList.add(nextState);

                //update reward tree
                Pair<Double, Double> rewards = new Pair<>(0.0, 0.0);
                try {
                    rewards._o1 = ((Number) s._reward.sample(new HashMap<LVAR, LCONST>(), s, new RandomDataGenerator())).doubleValue();
                    currentReward = rewards._o1;
                    rewards._o2 = rewards._o1;
                } catch (Exception e) {
                    System.out.println("compute reawrd error !" + e);
                }
                actionReward.put(actionID, rewards);

            }
        }

        //build search tree
        checkSearchPara();
        for (BigInteger st : nexthStateList) {
            ArrayList<BigInteger> searchStateList = new ArrayList<>();
            searchStateList.add(st);
            searchTreeBFS(state, timeLimit, System.nanoTime(), 4, 3, searchdeep, 10, searchStateList);
        }
        //compute next state fitness
        Double meanFitness = 0.0;
        int maxDeep = 0;
        for (BigInteger nextState : nexthStateList) {
            Pair<Integer, Double> stateFit = computeMeanFitDFS(nextState, searchdeep, 10);
            if (stateFit._o1 != 0)
                meanFitness += stateFit._o2 / stateFit._o1;
            if (maxDeep < stateFit._o1)
                maxDeep = stateFit._o1;
        }

        if (maxDeep != 0)
            meanFitness +=  currentReward / maxDeep;
        return meanFitness;
    }

    //计算state的期望值，用于评估nextState；
    public Double getStateFitness(State s, Long time/* ms */, int searchdeep) {
        if (shouwMsg)
            System.out.println("get state fitness");
        Long timeLimit = time * 1000 * 1000;
        if (shouwMsg)
            System.out.printf("get state fitness time limit:" + timeLimit);
        State state = ServerMaxSimple.cloneState(s);
        ArrayList<BigInteger> searchStateList = new ArrayList<>();
        BigInteger stateID = getStateLabel(s);

        //simulate next state
        searchStateList.add(stateID);

        //build search tree
        checkSearchPara();
        searchTreeBFS(state, timeLimit, System.nanoTime(), 4, 3, searchdeep, 10, searchStateList);

        //compute next state fitness
        Pair<Integer, Double> stateFit = computeMeanFitDFS(stateID, searchdeep, 10);

        return stateFit._o2;
    }


    //使用函数嵌套的方式，自动进行深度和宽度的搜索；
    //同时要进行搜索时间的平衡；
    //采用深度优先采样
    public static void searchTreeBFS(State state, long timeLimit/* long minTimeLimit,*/, long starTime, int actionWideLimit, int stateWideLimit,
                                     int deepLimit, int horizon, ArrayList<BigInteger> stateList) {


//        if (shouwMsg)
//            System.out.println("build search tree");
        long searchStartTime = System.nanoTime();

        RandomDataGenerator random = new RandomDataGenerator();
        random.reSeed(System.currentTimeMillis());

        if (stateList.isEmpty() || deepLimit < 0)
            return;
        //stateID只计算一次
        BigInteger stateID = stateList.get(0);
        stateList.remove(0);

        State s = ServerMaxSimple.cloneState(state);
        HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> _state = getStateFulent(stateID, stateTable);
        if (_state != null) {
            s._state = _state;
        }


        HashMap<BigInteger, HashMap<BigInteger, Integer>> actionSet = null;
        HashMap<BigInteger, Pair<Double, Double>> actionReward = null;
        if (recordState.containsKey(stateID))
            actionSet = recordState.get(stateID);
        else {
            actionSet = new HashMap<>();
            recordState.put(stateID, actionSet);
        }
        if (recordReward.containsKey(stateID))
            actionReward = recordReward.get(stateID);
        else {
            actionReward = new HashMap<>();
            recordReward.put(stateID, actionReward);
        }
        if (!recordStateTag.containsKey(stateID))
            recordStateTag.put(stateID, false);

        //宽度搜索
        //防止，只有一个可行动作，无限循环；
        int actionIteration = 0;
        //todo 注意限制动作集个数
        //随机抽样个数，
        while ((System.nanoTime() - starTime) < timeLimit) {
            //todo 此处也是两个超参；；；
            //每次的时间在总时间0.2-0.8之间随机
            ArrayList<PVAR_INST_DEF> actions = getRandomAction(s, random, (long) (averagePerRandomTime/* * (Math.random() * 1) - 0.5*/));

            actionIteration++;

            BigInteger actionID = getActionLabel(s, actions);

            //todo 限制后继状态有限的情况
            //todo 可用动作少或后继状态少时，提前中断，节约时间；
            //todo 设置随机中断，减少穷举消耗时间；
            int stateCount = 0;
            //限制
            if (actionSet.containsKey(actionID)) {
                actionIteration += actionWideLimit / 2;
                stateCount += stateWideLimit / 2;
            }
            //todo time 限制时间
            while ((System.nanoTime() - starTime) < timeLimit) {
                //todo 降低已经执行的动作的概率；同时需要判断，节点是否由继续搜索的必要，
                // 获得搜索的时间的多少（理论上说，后继节点和状态越多的节点搜索的步骤越少）；

                if (actionSet.containsKey(actionID)) {
                    //action重复时，减少搜索次数,增加深度搜索


                    HashMap<BigInteger, Integer> nextStateSet = actionSet.get(actionID);

                    //todo 注意限制计算后继state个数,最大为5
                    if (stateCount >= stateWideLimit || nextStateSet.size() >= 5)
                        break;
                    //后继状态大的时候仍然进行后继搜索；
                    if (nextStateSet.size() >= stateWideLimit)
                        stateCount += 2;

                    BigInteger nextState = simulateAction(s, actions, random);
                    //添加进搜索列表；
                    stateList.add(nextState);

                    //todo 处理相同后继状态的情况
                    if (nextStateSet.containsKey(nextState)) {
                        //compute probability
                        nextStateSet.put(nextState, new Integer(nextStateSet.get(nextState) + 1));
                        stateCount += 2;
                    }
                    //todo 处理不相同后继状态的情况
                    else {
                        //compute probability
                        nextStateSet.put(nextState, new Integer(1));


                        stateCount++;
                    }
                    //只在第一次执行动作的时候计算并更新reward，

                } else {

                    HashMap<BigInteger, Integer> nextStateSet = new HashMap<>();

                    actionSet.put(actionID, nextStateSet);

                    BigInteger nextState = simulateAction(s, actions, random);

                    nextStateSet.put(nextState, new Integer(1));

                    //添加进搜索列表；
                    stateList.add(nextState);

                    //update reward tree
                    Pair<Double, Double> rewards = new Pair<>(0.0, 0.0);
                    try {
                        rewards._o1 = ((Number) s._reward.sample(new HashMap<LVAR, LCONST>(), s, random)).doubleValue();
                        rewards._o2 = rewards._o1;
                    } catch (Exception e) {
                        System.out.println("compute reawrd error !" + e);
                    }
                    actionReward.put(actionID, rewards);

//                    System.out.println("add action :"+getActionFluent(actionID,actionList)+";;reward:"+rewards);
                    stateCount++;
                }
                if ((System.nanoTime() - starTime) > timeLimit)
                    return;
            }
            // todo time 限制时间,抽样动作数和时间有关；宽度的扩展有限，但至少执行一次；
            if (actionIteration >= actionWideLimit)
                break;
            if ((System.nanoTime() - starTime) > timeLimit)
                return;
        }

        if ((System.nanoTime() - starTime) > timeLimit)
            return;
        //多余；
//        timePerLayer = (System.nanoTime() - searchStartTime + timePerLayer) / 2;

        //宽度优先搜索
        if (!stateList.isEmpty()) {
            searchTreeBFS(state, timeLimit, starTime, actionWideLimit, stateWideLimit, deepLimit, horizon + 1, stateList);
        }


    }


    //todo 返回搜索深度；
    //深度优先采样，构建搜索树（搜索的深度和当前的horizon相关）
    public static void searchTree(State state, long timeLimit/* long minTimeLimit,*/, int actionWideLimit, int stateWideLimit, int deepLimit, int horizon) {
        if (shouwMsg)
            System.out.println("build search tree");
        long searchStartTime = System.nanoTime();

        RandomDataGenerator random = new RandomDataGenerator();
        random.reSeed(System.currentTimeMillis());
        //stateID只计算一次
        BigInteger stateID = getStateLabel(state);
        HashMap<BigInteger, HashMap<BigInteger, Integer>> actionSet = null;
        HashMap<BigInteger, Pair<Double, Double>> actionReward = null;
        if (recordState.containsKey(stateID))
            actionSet = recordState.get(stateID);
        else {
            actionSet = new HashMap<>();
            recordState.put(stateID, actionSet);
        }
        if (recordReward.containsKey(stateID))
            actionReward = recordReward.get(stateID);
        else {
            actionReward = new HashMap<>();
            recordReward.put(stateID, actionReward);
        }
        if (!recordStateTag.containsKey(stateID))
            recordStateTag.put(stateID, false);


        //用于处理，只有一个可行动作，无限循环；
        int actionIteration = 0;
        //宽度搜索
        //todo time 限制时间,抽样动作数和时间有关；
        while ((timeLimit > (System.nanoTime() - searchStartTime) * 2)) {
            //todo 注意限制动作集个数
            //随机抽样个数，动作抽样最大为10个；
            if (actionSet.size() >= 10 || actionIteration >= actionWideLimit)
                break;
            actionIteration++;
            ArrayList<PVAR_INST_DEF> actions = getRandomAction(state, random, (System.nanoTime() - searchStartTime) / 20);

            BigInteger actionID = getActionLabel(state, actions);

            //todo 限制后继状态有限的情况
            //todo 可用动作少或后继状态少时，提前中断，节约时间；
            //todo 设置随机中断，减少穷举消耗时间；
            int stateRepeat = 0;
            //todo time 限制时间
            while ((timeLimit > (System.nanoTime() - searchStartTime) * 11)) {
                State s = ServerMaxSimple.cloneState(state);
                //todo 降低已经执行的动作的概率；同时需要判断，节点是否由继续搜索的必要，
                // 获得搜索的时间的多少（理论上说，后继节点和状态越多的节点搜索的步骤越少）；
                if (actionSet.containsKey(actionID)) {
                    //action重复时，减少搜索次数
                    actionIteration++;

                    HashMap<BigInteger, Integer> nextStateSet = actionSet.get(actionID);

                    //todo 注意限制计算后继state个数
                    if (nextStateSet.size() > stateWideLimit || stateRepeat >= 3)
                        break;

                    BigInteger nextState = simulateAction(s, actions, random);

                    //todo 处理相同后继状态的情况
                    if (nextStateSet.containsKey(nextState)) {
                        //compute probability
                        nextStateSet.put(nextState, new Integer(nextStateSet.get(nextState) + 1));
                        stateRepeat++;
                    }
                    //todo 处理不相同后继状态的情况
                    else {
                        //compute probability
                        nextStateSet.put(nextState, new Integer(1));

                        if (stateRepeat > 0)
                            stateRepeat--;
                    }
                    //只在第一次执行动作的时候计算并更新reward，

                } else {

                    HashMap<BigInteger, Integer> nextStateSet = new HashMap<>();

                    actionSet.put(actionID, nextStateSet);

                    BigInteger nextState = simulateAction(s, actions, random);

                    nextStateSet.put(nextState, new Integer(1));

                    //update reward tree
                    Pair<Double, Double> rewards = new Pair<>(0.0, 0.0);
                    try {
                        rewards._o1 = ((Number) s._reward.sample(new HashMap<LVAR, LCONST>(), s, random)).doubleValue();
                        rewards._o2 = rewards._o1;
                    } catch (Exception e) {
                        System.out.println("compute reawrd error !" + e);
                    }
                    actionReward.put(actionID, rewards);

//                    System.out.println("add action :"+getActionFluent(actionID,actionList)+";;reward:"+rewards);

                }
            }

        }
        deepLimit -= 1;
        actionWideLimit -= 2;
        stateWideLimit -= 3;


        //深度搜索
        //套娃----函数嵌套
        //todo 处理时间问题,自动平衡时间问题；
        long timeRemain = System.nanoTime() - searchStartTime;
        if (timeRemain < 1 * 100000 && deepLimit <= 0 && actionWideLimit <= 0 && stateWideLimit <= 0)
            return;
        long timeDeepLimit = 0;

        ArrayList<BigInteger> actionIDSet = new ArrayList<>();
        for (BigInteger actionID : actionSet.keySet()) {
            actionIDSet.add(actionID);
        }
        for (BigInteger actionID : actionIDSet) {
            timeDeepLimit = timeRemain / actionSet.size();
            //todo 时间小于0.1ms时终止；
            if (timeDeepLimit < 1 * 100000)
                return;

            //Exception: java.util.ConcurrentModificationException
            ArrayList<BigInteger> nextStateIDSet = new ArrayList<>();
            for (BigInteger sID : actionSet.get(actionID).keySet()) {
                nextStateIDSet.add(sID);
            }
            for (BigInteger nextStateID : nextStateIDSet) {
                //get next state
                long stepTime = timeDeepLimit / actionSet.get(actionID).size();
                if (stepTime < 1 * 100000)
                    return;
                HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> _state = getStateFulent(nextStateID, stateTable);
                if (_state != null) {
                    State s = ServerMaxSimple.cloneState(state);
                    s._state = _state;
                    searchTree(s, stepTime, actionWideLimit, stateWideLimit, deepLimit, horizon + 1);
                }
            }
        }

    }


    public static ArrayList<PVAR_INST_DEF> getRandomAction(State s, RandomDataGenerator rand, long timeAllowed) {
        long randomActionStartTime = System.nanoTime();
        ArrayList<PVAR_INST_DEF> actions = new ArrayList<>();
        rand.reSeed(System.currentTimeMillis());
        int yitemp = 0;
        try {
//            ArrayList<PVAR_INST_DEF> actionList = getActionList(s);
            int[] index_permutation = Permutation.permute(actionList.size(), rand);

            for (int i = 0; i < index_permutation.length; i++) {
                //action的深拷贝；
                //only allowed bool_type action; deep clone
                PVAR_INST_DEF act = new PVAR_INST_DEF(actionList.get(index_permutation[i])._sPredName._sPVarName,
                        true, actionList.get(index_permutation[i])._alTerms);

                actions.add(act);
                yitemp++;

                try {
                    s.checkStateActionConstraints(actions);
                } catch (EvalException e) {

                    actions.remove(actions.size() - 1);

                } catch (Exception e) {
                    System.out.println("\nERROR evaluating constraint on action set: " +
                            actions + /*"\nConstraint: " +*/ e + "\n");
                    e.printStackTrace();
                    throw new EvalException(e.toString());
                }
                //todo time 检测时间；注意设置时间余量；(可以去最大值的95%)
                if (timeAllowed < (System.nanoTime() - randomActionStartTime))
                    break;

            }

        } catch (Exception e) {
            System.out.println("get action list error !!!");
        }
//        if (shouwMsg)
//            System.out.println("take time:" + (System.nanoTime() - randomActionStartTime) / 1000 + "μs;; search :" + yitemp + "，get actions::" + actions);
        return actions;
    }
    //        public static long averageStepTime = 0;
//        public static long averageComputeTime = 0;
//        public static long averageCheckTime = 0;
//        public static long averagePerActionTime = 0;

    //use to compute time cost of instance;
    public static void getTimePara(State state, RandomDataGenerator rand) {
        State s = ServerMaxSimple.cloneState(state);
        long randomActionStartTime = System.nanoTime();
        long actionConstrainStartTime = System.nanoTime();
        long stateComputeStartTime = System.nanoTime();
        ArrayList<PVAR_INST_DEF> actions = new ArrayList<>();
        rand.reSeed(System.currentTimeMillis());
        int yitemp = 0;
        try {
//            ArrayList<PVAR_INST_DEF> actionList = getActionList(s);
            int[] index_permutation = Permutation.permute(actionList.size(), rand);
            randomActionStartTime = System.nanoTime();
            for (int i = 0; i < index_permutation.length; i++) {
                //action的深拷贝；
                //only allowed bool_type action; deep clone
                PVAR_INST_DEF act = new PVAR_INST_DEF(actionList.get(index_permutation[i])._sPredName._sPVarName,
                        true, actionList.get(index_permutation[i])._alTerms);

                actions.add(act);
                yitemp++;

                try {
                    if (i == 0)
                        actionConstrainStartTime = System.nanoTime();
                    s.checkStateActionConstraints(actions);
                    if (i == 0)
                        averageCheckTime = System.nanoTime() - actionConstrainStartTime;
                } catch (EvalException e) {

                    actions.remove(actions.size() - 1);

                } catch (Exception e) {
                    System.out.println("\nERROR evaluating constraint on action set: " +
                            actions + /*"\nConstraint: " +*/ e + "\n");
                    e.printStackTrace();
                    throw new EvalException(e.toString());
                }


            }
            averageStepTime = (System.nanoTime() - randomActionStartTime) / index_permutation.length;
            stateComputeStartTime = System.nanoTime();
            s.computeNextState(actions, rand);
            averageComputeTime = System.nanoTime() - stateComputeStartTime;
            averagePerRandomTime = (System.nanoTime() - randomActionStartTime);
        } catch (Exception e) {
            System.out.println("get action list error !!!");
        }

    }


    //注意返回值！！！！！！
    public static BigInteger simulateAction(State s, ArrayList<PVAR_INST_DEF> action, RandomDataGenerator random) {
        State state = ServerMaxSimple.cloneState(s);

//        double reward = 0.0;
        try {
            // Check state-action constraints
            state.checkStateActionConstraints(action);

            // Compute next state (and all intermediate / observation variables)
            state.computeNextState(action, random);

//            //todo reward计算可以写在search中；
//            reward = ((Number) s._reward.sample(new HashMap<LVAR, LCONST>(), s, random)).doubleValue();

            state.advanceNextState(false);
        } catch (EvalException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        BigInteger nextState = getStateLabel(state);
        //存储搜索过的state
        stateTable.put(nextState, state._state);

        return nextState;
    }

    //判断动作集相等；
    public static boolean isActionEqual(ArrayList<PVAR_INST_DEF> a1, ArrayList<PVAR_INST_DEF> a2) {
        boolean isEqual = false;
        for (PVAR_INST_DEF i : a1) {
            boolean singleEqual = false;
            for (PVAR_INST_DEF j : a2)
                singleEqual = singleEqual || i.equals(j);
            isEqual = isEqual && singleEqual;
        }
        return isEqual;
    }

    //生成复杂的ID大约需要1.5ms
    public static BigInteger getStateLabel(State s) {
//        long tempTime = System.nanoTime();
        BigInteger stateId = BigInteger.ZERO;
//        System.out.println("time1=" + (System.nanoTime() - tempTime));
        ArrayList<Boolean> variableValues = getVariableValues(s);

        for (int i = 0; i < variableValues.size(); i++) {
            Boolean variableValue = variableValues.get(i);
            if (variableValue)
                stateId = stateId.setBit(i);
        }
//        System.out.println("time2=" + (System.nanoTime() - tempTime));
        return stateId;
    }

    public static BigInteger getActionLabel(State s, ArrayList<PVAR_INST_DEF> action) {
//        long tempTime = System.nanoTime();
        BigInteger stateId = BigInteger.ZERO;
//        System.out.println("time1=" + (System.nanoTime() - tempTime));
        ArrayList<Boolean> variableValues = getActionValues(s, action);
        for (int i = 0; i < variableValues.size(); i++) {
            Boolean variableValue = variableValues.get(i);
            if (variableValue)
                stateId = stateId.setBit(i);
        }
//        System.out.println("time2=" + (System.nanoTime() - tempTime));
        return stateId;
    }

    public static ArrayList<PVAR_INST_DEF> getActionFluent(BigInteger actionId, ArrayList<PVAR_INST_DEF> getActionList) {
        ArrayList<PVAR_INST_DEF> actions = new ArrayList<>();
        for (int i = 0; i < getActionList.size(); i++) {
            if (actionId.testBit(i))
                actions.add(getActionList.get(i));
        }
        return actions;
    }

    public static HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> getStateFulent
            (BigInteger stateID, HashMap<BigInteger, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>> stateTable) {

        if (stateTable.containsKey(stateID))
            return stateTable.get(stateID);
        else
            return null;
    }

    public static ArrayList<Boolean> getVariableValues(State s) {
        HashMap<String, Boolean> variableValuesAsHashMap = new HashMap<String, Boolean>();
        for (PVAR_NAME p : s._alStateNames) {
            try {
                ArrayList<ArrayList<RDDL.LCONST>> gfluents = s.generateAtoms(p);
                for (ArrayList<RDDL.LCONST> gfluent : gfluents) {
                    String variableName = RDDL2Format.CleanFluentName(p._sPVarName + gfluent);
                    //todo 此方法会记录所有的state的，包括false的state，可以优化只记录true的state；
                    Boolean variable_value = (Boolean) s.getPVariableAssign(p, gfluent);
                    variableValuesAsHashMap.put(variableName, variable_value);
                }
            } catch (Exception ex) {
                System.out.println("get state variable values error! ");
                System.err.println("EnumerableStatePolicy: could not retrieve assignment for " + p + "\n");
            }
        }

        ArrayList<Boolean> variableValues = new ArrayList<Boolean>();
        //todo 次数应该使用全局变量，全局变量应该加入policy或simClient,每次规划只计算一次；
        // 或者考虑不是使用，但需要确保每次都会生成所有的state名称，包括false的state；
        ArrayList<String> stateVariableNames = stateList;


        for (String stateVar : stateVariableNames) {
            Boolean variableValue = false;
            if (variableValuesAsHashMap.containsKey(stateVar)) {
                variableValue = variableValuesAsHashMap.get(stateVar);
                variableValues.add(variableValue);
            } else {
                System.out.println("get variable values sequence error! ");
                System.out.printf("Warning ! Variable [%s] not found in state representation", stateVar);
                System.out.println();
            }
        }
        return variableValues;
    }

    //get state sequence
    public static ArrayList<String> getStateVariableNames(State s) {
        ArrayList<String> stateVariableNames = new ArrayList<>();
        for (PVAR_NAME p : s._alStateNames) {
            try {
                ArrayList<ArrayList<RDDL.LCONST>> gfluents = s.generateAtoms(p);
                for (ArrayList<RDDL.LCONST> gfluent : gfluents) {
                    String variableName = RDDL2Format.CleanFluentName(p._sPVarName + gfluent);
                    stateVariableNames.add(variableName);
                }
            } catch (Exception ex) {
                System.err.println("EnumerableStatePolicy: could not retrieve assignment for " + p + "\n");
            }
        }
        return stateVariableNames;
    }


    public static ArrayList<Boolean> getActionValues(State s, ArrayList<PVAR_INST_DEF> action) {

        ArrayList<Boolean> variableValues = new ArrayList<Boolean>();

        //todo 次数应该使用全局变量，全局变量应该加入policy或simClient,每次规划只计算一次；
        // 或者考虑不是使用，但需要确保每次都会生成所有的state名称，包括false的state；
//        ArrayList<PVAR_INST_DEF> actionList = getActionList(s);

        for (PVAR_INST_DEF actionVar : actionList) {
            Boolean variableValue = false;
            if (action.contains(actionVar)) {
                variableValue = (Boolean) actionVar._oValue;
                variableValues.add(variableValue);
            } else
                variableValues.add(false);
        }

        return variableValues;
    }

    //get action sequence
    public static ArrayList<PVAR_INST_DEF> getActionList(State s) {
        ArrayList<PVAR_INST_DEF> actionList = new ArrayList<>();

        for (int j = 0; j < s._alActionNames.size(); j++) {
            PVAR_NAME p = s._alActionNames.get(j);
            PVARIABLE_DEF pvar_def = s._hmPVariables.get(p);
            try {
                ArrayList<ArrayList<LCONST>> inst = s.generateAtoms(p);
                for (int i = 0; i < inst.size(); i++) {
                    RDDL.PVARIABLE_ACTION_DEF action_def = (RDDL.PVARIABLE_ACTION_DEF) pvar_def;
                    Object value = null;
                    //todo 处理static的问题；
//                value = getRandomValue(s, action_def._typeRange);
                    value = true;
                    actionList.add(new PVAR_INST_DEF(p._sPVarName, value, inst.get(i)));
                }
            } catch (Exception e) {
                System.out.println("get terms error!" + e);
            }

        }

        return actionList;
    }


    // Recursive?
    public Object getRandomValue(State s, TYPE_NAME type) throws EvalException {

        if (type.equals(RDDL.TYPE_NAME.BOOL_TYPE)) {
            // bool
            return new Boolean(true); // Not random: we'll assume false is default so return non-default value
        } else if (type.equals(RDDL.TYPE_NAME.INT_TYPE)) {
            // int
            return new Integer(_random.nextInt(0, MAX_INT_VALUE));
        } else if (type.equals(RDDL.TYPE_NAME.REAL_TYPE)) {
            // real
            return new Double(_random.nextUniform(-MAX_REAL_VALUE, MAX_REAL_VALUE));
        } else {
            // a more complex type -- have to retrieve and process
            TYPE_DEF tdef = s._hmTypes.get(type);

            if (tdef == null) {
                throw new EvalException("Cannot find type definition for '" + type + "' to generate policy.");
            } else if (tdef instanceof STRUCT_TYPE_DEF) {

                // recursively get values for subtypes
                STRUCT_TYPE_DEF sdef = (STRUCT_TYPE_DEF) tdef;
                STRUCT_VAL sval = new STRUCT_VAL();
                for (STRUCT_TYPE_DEF_MEMBER m : sdef._alMembers) {
                    sval.addMember(m._sName, getRandomValue(s, m._type));
                }
                return sval;

            } else if (tdef instanceof LCONST_TYPE_DEF) {

                // randomly return one of the legal values for this ENUM or OBJECT type
                ArrayList<RDDL.LCONST> possible_values = ((LCONST_TYPE_DEF) tdef)._alPossibleValues;
                int index = _random.nextInt(0, possible_values.size() - 1);
                return possible_values.get(index);

            } else {
                throw new EvalException("Don't know how to sample from '" + type + "' of type '" + tdef + "'.");
            }
        }
    }
}
