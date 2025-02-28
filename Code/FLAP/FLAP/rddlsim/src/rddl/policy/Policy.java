/**
 * RDDL: Implements abstract policy interface.
 *
 * @author Scott Sanner (ssanner@gmail.com)
 * @version 10/10/10
 **/

package rddl.policy;

import org.apache.commons.math3.random.RandomDataGenerator;
import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.State;

import java.util.ArrayList;

public abstract class Policy {

    public long RAND_SEED = -1;

    public RandomDataGenerator _random = new RandomDataGenerator();
    public String _sInstanceName;
    public RDDL _rddl;
    public long _timeAllowed = 0;

    //add by yi
    public int _horizon = 0;
    public int _totalHorizon = 0;
    public int _actLim = 0;
    public int _atLim = 0;


    public Policy() {

    }

    public Policy(String instance_name) {
        _sInstanceName = instance_name;
    }

    public void setInstance(String instance_name) {
        _sInstanceName = instance_name;
    }

    public void setRDDL(RDDL rddl) {
        _rddl = rddl;
    }

    public void setLimitTime(Integer time) {
    }

    public void setTimeAllowed(long time) {
        _timeAllowed = time;
    }

    public int getNumberUpdate() {
        return 0;
    }

    public void setRandSeed(long rand_seed) {
        RAND_SEED = rand_seed;
        _random = new RandomDataGenerator();
        _random.reSeed(RAND_SEED);
    }

    // Override if needed
    public void roundInit(double time_left, int horizon, int round_number, int total_rounds) {
        System.out.println("\n*********************************************************");
        System.out.println(">>> ROUND INIT " + round_number + "/" + total_rounds + "; time remaining = " + time_left + ", horizon = " + horizon);
        System.out.println("*********************************************************");
    }
    //add by yi
    public void setHorizon(int h) {
        _horizon = h;
    }
    public void setTotalHorizon(int h) {
        _totalHorizon = h;
    }
    public void setSearchPara(int actionWide, int stateWide) {
        System.out.println("set seerch tree parameter:");
        _atLim = stateWide;
        _actLim = actionWide;
    }
    public Double Fitness(State s, Long TLim, int searchdeep,int BF) {
        System.out.println("return null ");
        return 0.0;
    }

    public void initialTree(State s) {
        System.out.println("\n*********************************************************");
        System.out.println(">>> INITIAL POLICY");
        System.out.println("*********************************************************");
    }

    // Override if needed
    public void roundEnd(double reward) {
        System.out.println("\n*********************************************************");
        System.out.println(">>> ROUND END, reward = " + reward);
        System.out.println("*********************************************************");
    }

    // Override if needed
    public void sessionEnd(double total_reward) {
        System.out.println("\n*********************************************************");
        System.out.println(">>> SESSION END, total reward = " + total_reward);
        System.out.println("*********************************************************");
    }

    // Must override
    public abstract ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException;

    public String toString() {
        return "Policy for '" + _sInstanceName + "'";
    }

}
