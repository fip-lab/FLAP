package rddl.policy;

import org.apache.commons.math3.random.RandomDataGenerator;
import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.*;
import rddl.State;
import rddl.competition.Simserver;
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


    public int MAX_INT_VALUE = 5;
    public double MAX_REAL_VALUE = 5.0d;
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
    public static ArrayList<String> staLi = new ArrayList<>();
    public static ArrayList<PVAR_INST_DEF> actLi = new ArrayList<>();
    public static HashMap<BigInteger, Boolean> STag = new HashMap<>();
    public static HashMap<BigInteger, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>> stTab = new HashMap<>();

    public static HashMap<BigInteger, HashMap<BigInteger, HashMap<BigInteger, Integer>>> reSt = new HashMap<>();

    public static HashMap<BigInteger, HashMap<BigInteger, Pair<Double, Double>>> RL = new HashMap<>();


    public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {

        ArrayList<PVAR_INST_DEF> actions = new ArrayList<RDDL.PVAR_INST_DEF>();
        return actions;
    }

    public void roundInit(double timeLeft, int horizon, int roundNumber, int totalRounds) {
    }

    public void check() {
        if (_atLim <= 0)
            _atLim = 3;
        if (_actLim <= 0)
            _actLim = 4;
    }

    public void initialTree(State s) {

        staLi = new ArrayList<>();
        actLi = new ArrayList<>();
        staLi = getStateVariableNames(s);
        actLi = getActionList(s);

        reSt = new HashMap<>();
        RL = new HashMap<>();
        STag = new HashMap<>();
        stTab = new HashMap<>();

        AST = 0;
        compute = 0;
        check = 0;
        randTim = 0;
        getTimpar(s, new RandomDataGenerator());

    }

    public static Pair<Integer, Double> BAct(BigInteger s, int SD, long TLim) {
        Double sumReward = 0.0;
        CTag(STag);
        Pair<Integer, Double> pa = comp(s, SD, 1, reSt, RL);

        HashMap<BigInteger, Pair<Double, Double>> RSet = RL.get(s);
        for (BigInteger AID : RSet.keySet()) {
            Pair<Double, Double> pair = RSet.get(AID);
            sumReward += pair._o1;
        }
        Double MR = sumReward / RSet.size();

        return new Pair<>(pa._o1, MR);
    }


    public static Pair<Integer, Double> comp(BigInteger ID, int SD, Integer deep,
                                                   HashMap<BigInteger, HashMap<BigInteger, HashMap<BigInteger, Integer>>> SL,
                                                   HashMap<BigInteger, HashMap<BigInteger, Pair<Double, Double>>> RL) {
        if (!RL.containsKey(ID) || SL.get(ID) == null || RL.get(ID).size() <= 0)
            return new Pair<>(deep - 1, 0.0);
        Double MR = 0.0;
        HashMap<BigInteger, Pair<Double, Double>> RSet = RL.get(ID);

        if (STag.get(ID).equals(true)) {
            for (BigInteger AID : RSet.keySet()) {
                Pair<Double, Double> pair = RSet.get(AID);
                MR += pair._o2 / deep;
            }
            MR = MR / RL.get(ID).size();
            STag.put(ID, true);
            return new Pair<>(deep, MR);
        } else if (SD <= 1) {

            for (BigInteger AID : RSet.keySet()) {
                Pair<Double, Double> pair = RSet.get(AID);
                pair._o1 = pair._o2 / deep;
                MR += pair._o1;
            }
            MR = MR / RL.get(ID).size();
            STag.put(ID, true);
            return new Pair<>(deep, MR);
        } else {
            STag.put(ID, true);
            Integer maxDeep = 1;
            for (BigInteger AID : RSet.keySet()) {
                Double meanR = 0.0;
                Pair<Double, Double> pair = RSet.get(AID);
                Integer AD = 1;
                if (SL.get(ID).get(AID) != null) {
                    HashMap<BigInteger, Double> SPro = comP(ID, AID, SL);
                    for (BigInteger nextState : SPro.keySet()) {
                        Pair<Integer, Double> mean = comp(nextState, SD - 1, deep + 1, SL, RL);
                        meanR += SPro.get(nextState) * mean._o2;
                        if (AD < mean._o1)
                            AD = mean._o1;
                    }
                }
                pair._o1 = pair._o2 / AD + meanR;
                MR += pair._o1;
                if (maxDeep < AD)
                    maxDeep = AD;
            }
            MR = MR / RL.get(ID).size();

            return new Pair<>(maxDeep, MR);
        }

    }

    public static void CTag(HashMap<BigInteger, Boolean> STag) {
        for (BigInteger ID : STag.keySet()) {
            STag.put(ID, false);
        }
    }

    public static HashMap<BigInteger, Double> comP(BigInteger ID, BigInteger AID,
                                                               HashMap<BigInteger, HashMap<BigInteger, HashMap<BigInteger, Integer>>> SL) {
        if (!SL.containsKey(ID) || !SL.get(ID).containsKey(AID))
            return null;
        HashMap<BigInteger, Double> SProb = new HashMap<>();
        Double count = 0.0;
        HashMap<BigInteger, Integer> stateSet = SL.get(ID).get(AID);
        for (BigInteger state : stateSet.keySet()) {
            count += stateSet.get(state);
        }
        for (BigInteger state : stateSet.keySet()) {
            SProb.put(state, new Double(stateSet.get(state) / count));
        }
        return SProb;
    }


    public static long AST = 0;
    public static long compute = 0;
    public static long check = 0;
    public static long randTim = 0;



    public Double Fitness(State s, Long time/* ms */, int deep,int BF) {
        if (shouwMsg)
            System.out.println("get state fitness");
        Long TLim = time * 1000 * 1000;
        if (shouwMsg)
            System.out.printf("time limit:" + TLim);
        State state = Simserver.cloneState(s);
        ArrayList<BigInteger> SL1 = new ArrayList<>();
        BigInteger ID = getLab(s);

        SL1.add(ID);

        check();
        Compute(state, TLim, System.nanoTime(), 4, 3, deep, 10, SL1,BF);

        Pair<Integer, Double> stateFit = BAct(ID, deep, 10);

        return stateFit._o2;
    }


    public static void Compute(State state, long TLim, long STim, int actLim, int atLim,
                                     int deepLimit, int horizon, ArrayList<BigInteger> staLi,int BF) {

        RandomDataGenerator random = new RandomDataGenerator();
        random.reSeed(System.currentTimeMillis());

        if (staLi.isEmpty() || deepLimit < 0)
            return;
        BigInteger ID = staLi.get(0);
        staLi.remove(0);

        State s = Simserver.cloneState(state);
        HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> _state = getSt(ID, stTab);
        if (_state != null) {
            s._state = _state;
        }


        HashMap<BigInteger, HashMap<BigInteger, Integer>> acSet = null;
        HashMap<BigInteger, Pair<Double, Double>> rward = null;
        if (reSt.containsKey(ID))
            acSet = reSt.get(ID);
        else {
            acSet = new HashMap<>();
            reSt.put(ID, acSet);
        }
        if (RL.containsKey(ID))
            rward = RL.get(ID);
        else {
            rward = new HashMap<>();
            RL.put(ID, rward);
        }
        if (!STag.containsKey(ID))
            STag.put(ID, false);

        int acLim = 0;
        while ((System.nanoTime() - STim) < TLim) {
            ArrayList<PVAR_INST_DEF> actions = RanAct(s, random, (long) (randTim * (Math.random() * 0.5) + 0.5));

            acLim++;

            BigInteger AID = ActLab(s, actions);

            int sCoun = 0;
            if (acSet.containsKey(AID)) {
                acLim += actLim / 2;
                sCoun += atLim / 2;
            }
            while ((System.nanoTime() - STim) < TLim) {
                if (acSet.containsKey(AID)) {

                    HashMap<BigInteger, Integer> nStSet = acSet.get(AID);

                    if (sCoun >= atLim || nStSet.size() >= BF)
                        break;
                    if (nStSet.size() >= atLim)
                        sCoun += 2;

                    BigInteger nextState = simAct(s, actions, random);
                    staLi.add(nextState);

                    if (nStSet.containsKey(nextState)) {
                        nStSet.put(nextState, new Integer(nStSet.get(nextState) + 1));
                        sCoun += 2;
                    }
                    else {
                        nStSet.put(nextState, new Integer(1));


                        sCoun++;
                    }

                } else {
                    HashMap<BigInteger, Integer> nStSet = new HashMap<>();
                    acSet.put(AID, nStSet);
                    BigInteger nextState = simAct(s, actions, random);
                    nStSet.put(nextState, new Integer(1));
                    staLi.add(nextState);
                    Pair<Double, Double> rewards = new Pair<>(0.0, 0.0);
                    try {
                        rewards._o1 = ((Number) s._reward.sample(new HashMap<LVAR, LCONST>(), s, random)).doubleValue();
                        rewards._o2 = rewards._o1;
                    } catch (Exception e) {
                        System.out.println("compute reawrd error !" + e);
                    }
                    rward.put(AID, rewards);

                    sCoun++;
                }
                if ((System.nanoTime() - STim) > TLim)
                    return;
            }
            if (acLim >= actLim)
                break;
            if ((System.nanoTime() - STim) > TLim)
                return;
        }

        if ((System.nanoTime() - STim) > TLim)
            return;
        if (!staLi.isEmpty()) {
            Compute(state, TLim, STim, actLim, atLim, deepLimit, horizon + 1, staLi,BF);
        }


    }


    public static ArrayList<PVAR_INST_DEF> RanAct(State s, RandomDataGenerator rand, long timeAllowed) {
        long staActTim = System.nanoTime();
        ArrayList<PVAR_INST_DEF> actions = new ArrayList<>();
        rand.reSeed(System.currentTimeMillis());
        int ytemp = 0;
        try {
            int[] index_permutation = Permutation.permute(actLi.size(), rand);

            for (int i = 0; i < index_permutation.length; i++) {
                PVAR_INST_DEF act = new PVAR_INST_DEF(actLi.get(index_permutation[i])._sPredName._sPVarName,
                        true, actLi.get(index_permutation[i])._alTerms);

                actions.add(act);
                ytemp++;

                try {
                    s.checkStateActionConstraints(actions);
                } catch (EvalException e) {

                    actions.remove(actions.size() - 1);

                } catch (Exception e) {
                    System.out.println("\nERROR evaluating constraint on action set: " +
                            actions + e + "\n");
                    e.printStackTrace();
                    throw new EvalException(e.toString());
                }
                if (timeAllowed < (System.nanoTime() - staActTim))
                    break;

            }

        } catch (Exception e) {
            System.out.println("get action list error !!!");
        }return actions;
    }
    public static void getTimpar(State state, RandomDataGenerator rand) {
        State s = Simserver.cloneState(state);
        long staActTim = System.nanoTime();
        long staStTim = System.nanoTime();
        ArrayList<PVAR_INST_DEF> actions = new ArrayList<>();
        rand.reSeed(System.currentTimeMillis());
        int yitemp = 0;
        try {
            int[] index_permutation = Permutation.permute(actLi.size(), rand);
            staActTim = System.nanoTime();
            for (int i = 0; i < index_permutation.length; i++) {
                PVAR_INST_DEF act = new PVAR_INST_DEF(actLi.get(index_permutation[i])._sPredName._sPVarName,
                        true, actLi.get(index_permutation[i])._alTerms);

                actions.add(act);
                yitemp++;

                try {
                    s.checkStateActionConstraints(actions);
                } catch (EvalException e) {

                    actions.remove(actions.size() - 1);

                } catch (Exception e) {
                    System.out.println("\nERROR evaluating constraint on action set: " +
                            actions +  e + "\n");
                    e.printStackTrace();
                    throw new EvalException(e.toString());
                }


            }
            AST = (System.nanoTime() - staActTim) / index_permutation.length;
            staStTim = System.nanoTime();
            s.computeNextState(actions, rand);
            compute = System.nanoTime() - staStTim;
            randTim = (System.nanoTime() - staActTim);
        } catch (Exception e) {
            System.out.println("get action list error !!!");
        }

    }


    public static BigInteger simAct(State s, ArrayList<PVAR_INST_DEF> action, RandomDataGenerator random) {
        State state = Simserver.cloneState(s);

        try {
            state.checkStateActionConstraints(action);

            state.computeNextState(action, random);


            state.advanceNextState(false);
        } catch (EvalException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        BigInteger nextState = getLab(state);
        stTab.put(nextState, state._state);

        return nextState;
    }


    public static BigInteger getLab(State s) {
        BigInteger stateId = BigInteger.ZERO;
        ArrayList<Boolean> varVals = getVal(s);

        for (int i = 0; i < varVals.size(); i++) {
            Boolean variableValue = varVals.get(i);
            if (variableValue)
                stateId = stateId.setBit(i);
        }
        return stateId;
    }

    public static BigInteger ActLab(State s, ArrayList<PVAR_INST_DEF> action) {
        BigInteger stateId = BigInteger.ZERO;
        ArrayList<Boolean> varVals = getActionValues(s, action);
        for (int i = 0; i < varVals.size(); i++) {
            Boolean variableValue = varVals.get(i);
            if (variableValue)
                stateId = stateId.setBit(i);
        }
        return stateId;
    }


    public static HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> getSt
            (BigInteger ID, HashMap<BigInteger, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>> stTab) {

        if (stTab.containsKey(ID))
            return stTab.get(ID);
        else
            return null;
    }

    public static ArrayList<Boolean> getVal(State s) {
        HashMap<String, Boolean> Values = new HashMap<String, Boolean>();
        for (PVAR_NAME p : s._alStateNames) {
            try {
                ArrayList<ArrayList<RDDL.LCONST>> gfluents = s.generateAtoms(p);
                for (ArrayList<RDDL.LCONST> gfluent : gfluents) {
                    String varNa = RDDL2Format.CleanFluentName(p._sPVarName + gfluent);
                    Boolean varVal = (Boolean) s.getPVariableAssign(p, gfluent);
                    Values.put(varNa, varVal);
                }
            } catch (Exception ex) {
                System.out.println("get state variable values error! ");
                System.err.println("EnumerableStatePolicy: could not retrieve assignment for " + p + "\n");
            }
        }

        ArrayList<Boolean> varVals = new ArrayList<Boolean>();
        ArrayList<String> stVaNa = staLi;


        for (String stateVar : stVaNa) {
            Boolean variVal = false;
            if (Values.containsKey(stateVar)) {
                variVal = Values.get(stateVar);
                varVals.add(variVal);
            } else {
                System.out.println("get variable values sequence error! ");
                System.out.printf("Warning ! Variable [%s] not found in state representation", stateVar);
                System.out.println();
            }
        }
        return varVals;
    }

    public static ArrayList<String> getStateVariableNames(State s) {
        ArrayList<String> stVaNa = new ArrayList<>();
        for (PVAR_NAME p : s._alStateNames) {
            try {
                ArrayList<ArrayList<RDDL.LCONST>> gfluents = s.generateAtoms(p);
                for (ArrayList<RDDL.LCONST> gfluent : gfluents) {
                    String varNa = RDDL2Format.CleanFluentName(p._sPVarName + gfluent);
                    stVaNa.add(varNa);
                }
            } catch (Exception ex) {
                System.err.println("EnumerableStatePolicy: could not retrieve assignment for " + p + "\n");
            }
        }
        return stVaNa;
    }


    public static ArrayList<Boolean> getActionValues(State s, ArrayList<PVAR_INST_DEF> action) {

        ArrayList<Boolean> varVals = new ArrayList<Boolean>();


        for (PVAR_INST_DEF actionVar : actLi) {
            Boolean variableValue = false;
            if (action.contains(actionVar)) {
                variableValue = (Boolean) actionVar._oValue;
                varVals.add(variableValue);
            } else
                varVals.add(false);
        }

        return varVals;
    }

    public static ArrayList<PVAR_INST_DEF> getActionList(State s) {
        ArrayList<PVAR_INST_DEF> actLi = new ArrayList<>();

        for (int j = 0; j < s._alActionNames.size(); j++) {
            PVAR_NAME p = s._alActionNames.get(j);
            PVARIABLE_DEF pvar_def = s._hmPVariables.get(p);
            try {
                ArrayList<ArrayList<LCONST>> inst = s.generateAtoms(p);
                for (int i = 0; i < inst.size(); i++) {
                    RDDL.PVARIABLE_ACTION_DEF action_def = (RDDL.PVARIABLE_ACTION_DEF) pvar_def;
                    Object value = null;
                    value = true;
                    actLi.add(new PVAR_INST_DEF(p._sPVarName, value, inst.get(i)));
                }
            } catch (Exception e) {
                System.out.println("get terms error!" + e);
            }

        }
        return actLi;
    }

}
