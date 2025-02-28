package rddl.competition;

/**
 * @author: Yi
 * @Date: 2021/03/12
 **/


import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ppddl.PPDDL;
import rddl.RDDL;
import rddl.RDDL.*;
import rddl.State;
import rddl.viz.NullScreenDisplay;
import rddl.viz.StateViz;
import util.Pair;
import util.Timer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Simserver {

    public static final boolean SHOW_ACTIONS = true;
    public static final boolean SHOW_XML = false;
    public static final boolean SHOW_MSG = false;
    public static final boolean SHOW_TIMING = false;

    /**
     * following is XML definitions
     */
    public static final String SESSION_REQUEST = "session-request";
    public static final String CLIENT_NAME = "client-name";
    public static final String INSTANCE_NAME = "instance-name";
    public static final String INPUT_LANGUAGE = "input-language";
    public static final String PROBLEM_NAME = "problem-name";
    public static final String SESSION_INIT = "session-init";
    public static final String SESSION_ID = "session-id";
    public static final String TASK_DESC = "task";
    public static final String SESSION_END = "session-end";
    public static final String INSTANCE_END = "instant-end";
    public static final String TOTAL_REWARD = "total-reward";
    public static final String IMMEDIATE_REWARD = "immediate-reward";
    public static final String NUM_ROUNDS = "num-rounds";
    public static final String TIME_ALLOWED = "time-allowed";
    public static final String ROUNDS_USED = "rounds-used";
    //by yi
    public static final String STEP_ALLOWED = "step-time-allowed";
    private static double STEP_TIME_ALLOWED = Integer.MAX_VALUE;

    public static final String SF = "sf";
    public static final String P = "p";
    public static final String B_F = "bf";

    public static final String CLIENT_INFO = "client-info";
    public static final String CLIENT_HOSTNAME = "client-hostname";
    public static final String CLIENT_IP = "client-ip";

    public static final String ROUND_REQUEST = "round-request";
    public static final String EXECUTE_POLICY = "execute-policy";
    public static final String ROUND_INIT = "round-init";
    public static final String ROUND_NUM = "round-num";
    public static final String ROUND_LEFT = "round-left";
    public static final String TIME_LEFT = "time-left";
    public static final String ROUND_END = "round-end";
    public static final String ROUND_REWARD = "round-reward";
    public static final String TURNS_USED = "turns-used";
    public static final String TIME_USED = "time-used";

    public static final String RESOURCE_REQUEST = "resource-request";
    public static final String RESOURCE_NOTIFICATION = "resource-notification";
    public static final String MEMORY_LEFT = "memory-left";

    public static final String TURN = "turn";
    public static final String TURN_NUM = "turn-num";
    public static final String OBSERVED_FLUENT = "observed-fluent";
    public static final String NULL_OBSERVATIONS = "no-observed-fluents";
    public static final String FLUENT_NAME = "fluent-name";
    public static final String FLUENT_ARG = "fluent-arg";
    public static final String FLUENT_VALUE = "fluent-value";

    public static final String ACTIONS = "actions";
    public static final String ACTION = "action";
    public static final String ACTION_NAME = "action-name";
    public static final String ACTION_ARG = "action-arg";
    public static final String ACTION_VALUE = "action-value";
    public static final String DONE = "done";

    public static final int PORT_NUMBER = 2323;
    public static final String HOST_NAME = "localhost";
    public static final int DEFAULT_SEED = 0;

    public static final String NO_XML_HEADER = "no-header";
    public static boolean NO_XML_HEADING = false;
    public static final boolean SHOW_MEMORY_USAGE = true;
    public static final Runtime RUNTIME = Runtime.getRuntime();
    private static DecimalFormat _df = new DecimalFormat("0.##");


    private RDDL rddl = null;
    private static int ID = 0;
    private static int DEFAULT_NUM_ROUNDS = 75;
    private static long DEFAULT_TIME_ALLOWED = 7200000;
    private static boolean USE_TIMEOUT = true;
    private static boolean INDIVIDUAL_SESSION = false;
    private static String LOG_FILE = "rddl";
    private static boolean MONITOR_EXECUTION = false;
    private static String SERVER_FILES_DIR = "";

    public int port;
    public int id;
    public String clientName = null;
    public String requestedInstance = null;
    public RandomDataGenerator rand;
    public boolean executePolicy = true;
    public String inputLanguage = "rddl";
    public int numSimulations = 0;

    public State state;
    public INSTANCE instance;
    public NONFLUENTS nonFluents;
    public DOMAIN domain;
    public static int randomSeed = 0;
    public StateViz stateViz;

    public static boolean sh = true;
    public static int clientNum = 4;
    public static int populationNum = 7;
    public static String file = "result.csv";
    public static int BF = 5;
    private static ArrayList<recordClient> clientList = new ArrayList<>();
    public static ArrayList<String> instNameList = new ArrayList<>();


    public static class mission {
        public int popID;
        public int deep;
    }

    public static class recordClient {
        public String name = "";
        public Socket socket = null;
        public String client_hostname = "";
        public String client_IP = "";
        public BufferedInputStream isr = null;
        public InputSource isrc = null;
        public BufferedOutputStream os = null;
        public OutputStreamWriter osw = null;
        public String msg = null;
    }


    public static class individual {
        public int id = 0;
        public int father = 0;
        public int clientId = Integer.MAX_VALUE;
        public int horizon = 0;
        public State state = null;
        public PPDDL.Domain domain = null;
        public ArrayList<PVAR_INST_DEF> actionList = null;
        public Double reward = 0.0;
        public Double exRe = -Double.MAX_VALUE;
    }


    public static class individualPara {
        public int id = 0;
        public int selectRandom = 0;
        public double prob = 0.0;
        public RandomDataGenerator rand = new RandomDataGenerator();
        public double fitness = 0.0;
        public ArrayList<Integer> location = new ArrayList<>();
        public int deep;
        public int numSpark = 3;

    }

    public static class recordState {
        public int id = 0;
        public int father = 0;
        public int horizon = 0;
        public int deepMark = 0;
        public State state = null;
        public ArrayList<PVAR_INST_DEF> actionList = new ArrayList<>();
        public Double accum_reward = 0.0;
    }


    public static void main(String[] args) {

        StateViz state_viz = new NullScreenDisplay(false);
        ArrayList<RDDL> rddls = new ArrayList<RDDL>();
        int port = PORT_NUMBER;
        if (args.length < 1) {
            System.out.println("usage: rddlfilename-or-dir (optional) portnumber num-rounds random-seed use-timeout individual-session log-folder monitor-execution state-viz-class-name");
            System.out.println("\nexample 1: Server rddlfilename-or-dir");
            System.out.println("example 2: Server rddlfilename-or-dir 2323");
            System.out.println("example 3: Server rddlfilename-or-dir 2323 100 0 0 1 experiments/experiment23/ 1 rddl.viz.GenericScreenDisplay");
            System.exit(1);
        }

        try {
            SERVER_FILES_DIR = new String(args[0]);

            File[] subDirs = new File(args[0]).listFiles(File::isDirectory);
            for (File subDir : subDirs) {
                if (subDir.getName().equals("server")) {
                    SERVER_FILES_DIR = new String(subDir.getPath());
                } else if (subDir.getName().equals("client")) {
                    SERVER_FILES_DIR = new String(subDir.getPath());
                }
            }

            RDDL rddl;

            if (args.length > 1) {
                port = Integer.valueOf(args[1]);
            }
            ServerSocket socket1 = new ServerSocket(port);
            if (args.length > 2) {
                DEFAULT_NUM_ROUNDS = Integer.valueOf(args[2]);
            }
            int rand_seed = -1;
            if (args.length > 3) {
                rand_seed = Integer.valueOf(args[3]);
            } else {
                rand_seed = DEFAULT_SEED;
            }
            if (args.length > 4) {
                clientNum = Integer.valueOf(args[4]);
            }
            if (args.length > 5) {
                if (args[5].equals("0")) {
                    USE_TIMEOUT = false;
                    DEFAULT_TIME_ALLOWED = Integer.MAX_VALUE;
                } else {
                    USE_TIMEOUT = true;
                    DEFAULT_TIME_ALLOWED = Integer.valueOf(args[5]) * 1000;
                }
            }
            if (args.length > 6) {
                STEP_TIME_ALLOWED = Integer.valueOf(args[6]);
            } else {
                STEP_TIME_ALLOWED = 500;
            }
            if (args.length > 7) {
                populationNum = Integer.valueOf(args[7]);
            }
            if (args.length > 9) {
                file = args[9];
            }
            if (args.length > 10) {
                BF = Integer.valueOf(args[10]);
            }
            if (args.length > 11) {
                state_viz = (StateViz) Class.forName(args[11]).newInstance();//+
            }
            if (sh)
                System.out.println("RDDL Server Initialized");

            {
                FileInputStream fis = new FileInputStream("files/Domains/aabaseline-inst.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                String line = null;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                    instNameList.add(line);
                }
                br.close();
                fis.close();
            }

            for (String instanceName : instNameList) {

                for (recordClient c : clientList) {
                    c.socket.close();
                }
                clientList.clear();
                System.out.println("wait client connect....");
                for (int i = 0; i < clientNum; i++) {
                    Socket connection = socket1.accept();
                    RandomDataGenerator rdg = new RandomDataGenerator();
                    rdg.reSeed(rand_seed + ID);
                    recordClient re = new recordClient();
                    connection.setKeepAlive(true);
                    re.socket = connection;
                    clientList.add(re);
                    System.out.println("client connect:" + connection.getInetAddress());
                }

                rddl = new RDDL(SERVER_FILES_DIR);
                Simserver s = new Simserver(++ID, rddl, state_viz, port, new RandomDataGenerator());
                s.runClient(instanceName);
            }

            System.out.println("Single client has connected, no more are accepted.");
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }


    Simserver(int i, RDDL rddl, StateViz state_viz, int port, RandomDataGenerator rgen) {
        this.id = i;
        this.rddl = rddl;
        this.stateViz = state_viz;
        this.port = port;
        this.rand = rgen;
    }


    public void exploitation() {
    }

    public void exploration() {
    }

    public void selectSwarmMix(ArrayList<individual> swarm, ArrayList<individual> pop, int h, int size) {

        if (swarm.size() < populationNum) {
            if (sh)
                System.err.println("numspark too small");
            return;
        }
        pop.clear();
        int rankList[] = new int[swarm.size()];
        double probList[] = new double[swarm.size()];
        ArrayList<individual> swarmTemp = new ArrayList<>();
        for (individual i : swarm) {
            swarmTemp.add(i);
        }
        int sign = 0;
        probList[sign] = 0.0;
        int n = swarm.size();
        double a = (instance._nHorizon - h + 0.01) / instance._nHorizon;
        double base = (n - 1) * (n - 1) * (1 + 2 * a);
        while (swarm.size() > 0) {
            int temp = 0;
            for (int i = 1; i < swarm.size(); i++) {
                if (swarm.get(temp).exRe > swarm.get(i).exRe) {
                    temp = i;
                }
            }
            rankList[sign] = swarm.get(temp).id;
            if (sign < n - 1)
                probList[sign + 1] = (sign + 1.0) * (sign + 1 + 2 * a * (n - 1)) / base;
            sign++;
            swarm.remove(temp);
        }
        {
            int temp = rankList[swarmTemp.size() - 1];
            swarmTemp.get(temp).id = pop.size() + size;
            swarmTemp.get(temp).horizon = h + 1;
            pop.add(swarmTemp.get(temp));
        }
        while (pop.size() < populationNum) {
            double rand = Math.random();
            for (int i = 0; i < swarmTemp.size(); i++) {
                if (probList[i] <= rand && probList[i + 1] > rand) {
                    if (rankList[i] < Integer.MAX_VALUE) {
                        int temp = rankList[i];
                        swarmTemp.get(temp).id = pop.size() + size;
                        swarmTemp.get(temp).horizon = h + 1;
                        pop.add(swarmTemp.get(temp));

                        rankList[i] = Integer.MAX_VALUE;
                        break;
                    }
                }
            }
        }
        if (sh)
            System.out.println();
        swarm.clear();
    }

    public int getOptimal(ArrayList<individual> population) {
        int optimal = 0;
        for (int i = 0; i < population.size(); i++) {
            if (population.get(i).reward > population.get(optimal).reward)
                optimal = i;
        }
        return optimal;
    }

    public ArrayList<individualPara> computePara(ArrayList<individual> pop, int h) {
        ArrayList<individualPara> pp = new ArrayList<>();
        for (individual i : pop) {
            individualPara ip = new individualPara();
            ip.id = i.id;
            pp.add(ip);
        }
        int factorSp = 8;
        double fMax = -Double.MAX_VALUE;
        double sumFit = 0.0;
        double a = 0.5;
        double b = 0.8;
        int factorDe = 5;
        double fMin = Double.MAX_VALUE;
        double sumFun = 0.0;
        for (int i = 0; i < pop.size(); i++) {
            if (pop.get(i).reward > fMax)
                fMax = pop.get(i).reward;
            if (pop.get(i).reward < fMin)
                fMin = pop.get(i).reward;
        }
        for (int i = 0; i < pop.size(); i++) {
            sumFit += (fMax - pop.get(i).reward);
            sumFun += (pop.get(i).reward - fMin);
        }
        for (int i = 0; i < pop.size(); i++) {
            double temp = ((factorSp * (pop.get(i).reward - fMin) + Double.MIN_VALUE) / (sumFun + Double.MIN_VALUE));
            if (temp < a * factorSp)
                pp.get(i).numSpark = (int) (a * factorSp);
            else if (temp > b * factorSp)
                pp.get(i).numSpark = (int) (b * factorSp);
            else pp.get(i).numSpark = (int) (temp);
            int temp_d = (int) ((factorDe * Math.abs(fMax - pop.get(i).reward) + Double.MIN_VALUE) / (sumFit + Double.MIN_VALUE));
            if (h < 10)
                temp_d = 1;
            else if (temp_d >= (h * 0.2))
                temp_d = (int) (h * 0.2);
            else if (temp_d < 1)
                temp_d = 1;
            pp.get(i).deep = temp_d;
            if (h >= instance._nHorizon - 5)
                pp.get(i).deep = 1;
            pp.get(i).prob = 0.5;
        }
        return pp;
    }

    public ArrayList<mission> allocate(ArrayList<recordState> recordStateTree, ArrayList<individual> population, ArrayList<individualPara> inPara) {
        ArrayList<mission> mi = new ArrayList<>();
        for (int k = 0; k < population.size(); k++) {
            for (int j = 0; j < inPara.get(k).numSpark; j++) {
                mission mis = new mission();
                if (mis.deep != 1 && recordStateTree.get(recordStateTree.size() - 1).horizon > 10 &&
                        recordStateTree.get(recordStateTree.size() - 1).horizon < instance._nHorizon - 10) {
                    mis.deep = inPara.get(k).deep;
                    if (mis.deep < 1)
                        mis.deep = 1;
                    else if (mis.deep > inPara.get(k).deep)
                        mis.deep = inPara.get(k).deep;
                    mis.popID = population.get(k).id;
                    for (int l = 1; l < mis.deep; l++) {
                        mis.popID = recordStateTree.get(mis.popID).father;
                    }
                    if (mis.popID == -1) {
                        mis.popID = 0;
                    }
                } else {
                    mis.popID = population.get(k).id;
                    mis.deep = 1;
                }
                mis.deep = 1;
                if (mis.deep == 1)
                    mi.add(mis);
                else
                    mi.add(0, mis);
            }
        }
        return mi;
    }

    public void crowdSourcingThread(ArrayList<recordState> recordStateTree, ArrayList<individual> swarm, ArrayList<individual> population,
                                    long timeAllowed, long start_time, double cur_discount,
                                    int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore
    ) throws InterruptedException {
        ArrayList<individualPara> inPara = computePara(population, h);
        ArrayList<mission> missionsList = allocate(recordStateTree, population, inPara);
        int threadNum = clientList.size();
        CountDownLatch threadSignal = new CountDownLatch(threadNum);
        ExecutorService executor = Executors.newFixedThreadPool(threadNum);
        System.out.println("get action begin");
        boolean getActionS = false;
        while (!getActionS) {
            for (int j = 0; j < threadNum; j++) {
                System.out.println("clinet£º" + clientList.size());
                DOMParser p = new DOMParser();
                Runnable task = new actionThread(threadSignal, missionsList, swarm, recordStateTree, p, timeAllowed, start_time, cur_discount, h, observStore, clientList.get(j));
                executor.execute(task);
            }
            if (!threadSignal.await(60, TimeUnit.SECONDS)) {
                executor.shutdown();
                executor.shutdownNow();
                executor = Executors.newFixedThreadPool(threadNum);
            } else {
                getActionS = true;
            }
        }
        threadSignal.await();
        executor.shutdown();
        executor.shutdownNow();
        executor = null;
    }

    private class actionThread implements Runnable {
        private DOMParser p;
        private long timeAllowed;
        private long start_time;
        private double cur_discount;
        private int h;
        private HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore;
        private individual i;
        private individual iP;
        private recordClient c;
        private ArrayList<mission> missionsList;
        private CountDownLatch threadsSignal;
        private ArrayList<individual> population;
        ArrayList<individual> swarm;
        ArrayList<recordState> recordStateTree;

        public actionThread(CountDownLatch threadsSignal, ArrayList<mission> missionsList,
                            ArrayList<individual> swarm, ArrayList<recordState> recordStateTree,
                            DOMParser p, long timeAllowed, long start_time, double cur_discount,
                            int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                            recordClient c) {
            this.p = p;
            this.timeAllowed = timeAllowed;
            this.start_time = start_time;
            this.cur_discount = cur_discount;
            this.h = h;
            this.observStore = observStore;
            this.c = c;
            this.missionsList = missionsList;
            this.threadsSignal = threadsSignal;
            this.swarm = swarm;
            this.recordStateTree = recordStateTree;
        }

        public void run() {
            System.out.println("get action from one client ;");
            while (missionsList.size() > 0) {
                mission mi;
                synchronized (this) {
                    if (missionsList.size() <= 0)
                        return;
                    else {
                        mi = missionsList.get(0);
                        missionsList.remove(0);
                    }
                }
                individual i = new individual();
                boolean b = getAction(p, timeAllowed, start_time, cur_discount, h, observStore, i, recordStateTree.get(mi.popID), c);
                if (!b) {
                    System.err.println("getAction error!! continue , execute empty action, by yi");
                    continue;
                }
                mi.deep--;
                synchronized (this) {
                    if (mi.deep > 0) {
                        recordState re = new recordState();
                        re.state = cloneState(i.state);
                        re.father = i.father;
                        re.actionList = i.actionList;
                        re.horizon = h + 1;
                        re.id = recordStateTree.size();
                        re.accum_reward = i.reward;
                        re.deepMark = 1;
                        recordStateTree.add(re);
                        mi.popID = re.id;
                        missionsList.add(0, mi);
                    } else {
                        i.id = swarm.size();
                        swarm.add(i);
                    }
                }
            }
            threadsSignal.countDown();
        }

    }

    public boolean getAction(DOMParser p, long timeAllowed, long start_time, double cur_discount,
                             int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                             individual i, recordState is, recordClient c) {
        try {
            ArrayList<PVAR_INST_DEF> reAct = new ArrayList<>();
            ArrayList<PVAR_INST_DEF> reSt = new ArrayList<>();
            Pair<Double, Double> rewards = null;

            i.horizon = h;
            i.father = is.id;
            i.state = cloneState(is.state);
            i.reward = is.accum_reward;
            c.msg = createXMLTurn(i.state, h + 1, domain, observStore, timeAllowed - System.currentTimeMillis() + start_time, 0.0);

            if (domain._bPartiallyObserved)
                observStore = copyObserv(i.state._observ);

            if (SHOW_MSG)
                System.out.println("Sending msg:\n" + c.msg);
            sendOneMessage(c.osw, c.msg);
            c.isrc = readOneMessage(c.isr);
            if (c.isrc == null) {
                c.socket.close();
                clientList.remove(c);
                throw new Exception("FATAL SERVER EXCEPTION: EMPTY CLIENT MESSAGE");
            }

            rewards = processXMLPlan(p, c.isrc, i.state, reAct, reSt);

            if (reAct == null) {
                c.msg = createXMLResourceNotification(timeAllowed - System.currentTimeMillis() + start_time);
                sendOneMessage(c.osw, c.msg);
                c.isrc = readOneMessage(c.isr);
                rewards = processXMLPlan(p, c.isrc, i.state, reAct, reSt);
            }

            if (reSt == null) {
                if (SHOW_MSG) System.out.println("No state/observations received.");
                if (SHOW_XML)
                    printXMLNode(p.getDocument());
            } else if (domain._bPartiallyObserved) {
                i.state.clearPVariables(i.state._observ);
                i.state.setPVariables(i.state._observ, reSt);
            } else {
                i.state.clearPVariables(i.state._state);
                i.state.setPVariables(i.state._state, reSt);
            }
            i.actionList = reAct;

            i.reward += cur_discount * rewards._o1;
            double ratio = h / instance._nHorizon;
            if (ratio > 1)
                ratio = 1.0;
            else if (ratio <= 0)
                ratio = 0.0;
            i.exRe = (1 - ratio) * rewards._o2 + ratio * rewards._o1;

            cur_discount *= instance._dDiscount;
            System.out.println("** horizon " + h + ",individual " + i.id + ",father" + i.father + ",client name:" + c.name + ",irew:" + rewards._o1 + "\t,reward:" + i.reward + "\t,Actions received: " + reAct);

            stateViz.display(i.state, h);
            return true;
        } catch (Exception e) {
            System.out.println("get action error :" + e);
            i.exRe = -Double.MAX_VALUE;
            i.actionList = new ArrayList<>();

            return false;
        }

    }

    public void advanceState(ArrayList<individual> pop) {
        for (individual i : pop) {
            try {
                i.state.advanceNextState();
            } catch (Exception e) {
                System.out.println("individual" + i + "acvance next state error!!");
            }
        }
    }


    public void recState(ArrayList<individual> population, ArrayList<recordState> recordStateTree) {
        int id = recordStateTree.size();
        for (individual i : population) {
            recordState re = new recordState();
            re.state = cloneState(i.state);
            re.father = i.father;
            re.actionList = i.actionList;
            re.horizon = i.horizon;
            re.id = id;
            re.accum_reward = i.reward;
            recordStateTree.add(re);
            id++;
        }
    }


    public void runClient(String instanceName) {

        try {
            DOMParser p = new DOMParser();
            int numRounds = DEFAULT_NUM_ROUNDS;
            long timeAllowed = DEFAULT_TIME_ALLOWED;
            double stepTime = STEP_TIME_ALLOWED;
            long start_time = System.currentTimeMillis();
            int optimalIndvi;
            double totalmemory = 0.0;
            requestedInstance = instanceName;
            if (!rddl._tmInstanceNodes.containsKey(requestedInstance)) {
                System.out.println("Instance name '" + requestedInstance + "' not found.");
                return;
            }
            instance = rddl._tmInstanceNodes.get(instanceName);
            nonFluents = null;
            if (instance._sNonFluents != null) {
                nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
            } else {
                System.err.println("instance.noFulents = null;;" + instanceName);
            }
            domain = rddl._tmDomainNodes.get(instance._sDomain);
            initializeState(rddl, requestedInstance);

            for (recordClient c : clientList) {
                InetAddress ia = c.socket.getInetAddress();
                c.client_hostname = ia.getCanonicalHostName();
                c.client_IP = ia.getHostAddress();

                System.out.println("Connection from client at address " + c.client_hostname + " / " + c.client_IP);
                writeToLog(createClientHostMessage(c.client_hostname, c.client_IP));

                c.isr = new BufferedInputStream(c.socket.getInputStream());
                c.isrc = readOneMessage(c.isr);
                processXMLSessionRequest(p, c.isrc, this);
                c.name = clientName;
                System.out.println("Client name: " + clientName);

                c.os = new BufferedOutputStream(c.socket.getOutputStream());
                c.osw = new OutputStreamWriter(c.os, "US-ASCII");
                c.msg = createXMLSessionInit(DEFAULT_NUM_ROUNDS, Double.valueOf(timeAllowed), stepTime, this, requestedInstance);
                sendOneMessage(c.osw, c.msg);
                System.out.println("Instance requested: " + requestedInstance);

                if (!rddl._tmInstanceNodes.containsKey(requestedInstance)) {
                    System.out.println("Instance name '" + requestedInstance + "' not found.");
                    return;
                }

            }
            boolean OUT_OF_TIME = false;


            double accum_total_reward = 0d;
            ArrayList<Double> rewards = new ArrayList<Double>();
            int r = 0;
            for (; r < numRounds && !OUT_OF_TIME; r++) {

                if (OUT_OF_TIME == true)
                    System.out.println("time error£»£»£»£¡");

                if (!executePolicy) {
                    r--;
                }

                resetState();
                ArrayList<individual> swarm = new ArrayList<>();
                ArrayList<individual> population = new ArrayList<>();
                ArrayList<recordState> recordStateTree = new ArrayList<>();
                for (int i = 0; i < populationNum; i++) {
                    individual ind = new individual();
                    ind.id = i;
                    ind.state = cloneState(state);
                    ind.father = -1;
                    population.add(ind);
                }
                recState(population, recordStateTree);

                if (executePolicy) {
                    System.out.println("Round " + (r + 1) + " / " + numRounds + ", time remaining: " + (timeAllowed - System.currentTimeMillis() + start_time));
                    if (SHOW_MEMORY_USAGE) {
                        totalmemory += (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1e6d;
                        System.out.print("[ Memory usage: " +
                                _df.format((RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1e6d) + "Mb / " +
                                _df.format(RUNTIME.totalMemory() / 1e6d) + "Mb" +
                                " = " + _df.format(((double) (RUNTIME.totalMemory() - RUNTIME.freeMemory()) /
                                (double) RUNTIME.totalMemory())) + " ]\n");
                    }
                }

                double cur_discount = 1.0d;
                int h = 0;
                HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore = null;
                while (true) {
                    Timer timer = new Timer();
                    swarm.clear();
                    crowdSourcingThread(recordStateTree, swarm, population, timeAllowed, start_time, cur_discount, h, observStore);
                    selectSwarmMix(swarm, population, h, recordStateTree.size());

                    recState(population, recordStateTree);

                    if (domain._bPartiallyObserved)
                        observStore = copyObserv(state._observ);
                    OUT_OF_TIME = ((System.currentTimeMillis() - start_time) > timeAllowed) && USE_TIMEOUT;
                    h++;

                    if (OUT_OF_TIME) {
                        break;
                    }
                    if ((instance._termCond == null) && (h == instance._nHorizon)) {
                        break;
                    }
                    if ((instance._termCond != null) && state.checkTerminationCondition(instance._termCond)) {
                        break;
                    }

                }
                optimalIndvi = getOptimal(population);
                if (executePolicy) {
                    accum_total_reward = population.get(optimalIndvi).reward;
                    rewards.add(population.get(optimalIndvi).reward);
                    System.out.println("** Round best reward: " + population.get(optimalIndvi).reward);
                }
            }
            for (recordClient c : clientList) {
                try {
                    System.out.println("clientList size==" + clientList.size());
                    c.msg = createXMLSessionEnd(requestedInstance, accum_total_reward, r,
                            timeAllowed - System.currentTimeMillis() + start_time, this.clientName, this.id);
                    if (SHOW_MSG)
                        System.out.println("Sending msg:\n" + c.msg);
                    sendOneMessage(c.osw, c.msg);

                    writeToLog(c.msg);
                    if (INDIVIDUAL_SESSION) {
                        try {
                            c.socket.close();
                        } catch (IOException ioe) {
                        }
                        System.exit(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("send session end message error!");
                    System.out.println("client name:" + c.name);
                    c.socket.close();
                    clientList.remove(c);
                }
            }
            accum_total_reward = 0;
            Double maxReward = -Double.MAX_VALUE;
            for (Double re : rewards) {
                accum_total_reward += re;
                if (re > maxReward)
                    maxReward = re;
            }

            double D = 0.0;
            double SD = 0.0;
            double averageReward = accum_total_reward / r;
            for (Double re : rewards) {
                D += Math.pow((re - averageReward), 2);
            }
            D = D / rewards.size();
            SD = Math.sqrt(D);

            System.out.println("Time left: " + (timeAllowed - System.currentTimeMillis() + start_time));
            System.out.println("*Time cost: " + (System.currentTimeMillis() - start_time) + "ms");
            System.out.println("*Average memory: " + totalmemory / numRounds);
            System.out.println("Number of simulations: " + numSimulations);
            System.out.println("*total Number of runs: " + numRounds);
            System.out.println("*actual Number of runs: " + r);
            System.out.println("Accumulated reward: " + (accum_total_reward));
            System.out.println("*Average reward: " + (accum_total_reward / r));
            String result = requestedInstance + "," + clientList.size() + "," + "," + String.valueOf(System.currentTimeMillis() - start_time)
                   + "," + String.valueOf(r) + "," + String.valueOf(averageReward) + "," + maxReward + "," + SD + "," + D ;
            writeFile(result, file);

            for (recordClient c : clientList) {
                c.socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\n>> TERMINATING TRIAL.");

        } finally {
        }

    }

    public static State cloneState(State currentState) {

        State s = new State();

        s._hmPVariables = currentState._hmPVariables;
        s._hmTypes = currentState._hmTypes;
        s._hmCPFs = currentState._hmCPFs;

        s._hmObject2Consts = new HashMap<RDDL.TYPE_NAME, ArrayList<LCONST>>(currentState._hmObject2Consts);

        s._alStateNames = new ArrayList<RDDL.PVAR_NAME>(currentState._alStateNames);
        s._alActionNames = new ArrayList<RDDL.PVAR_NAME>(currentState._alActionNames);
        s._tmIntermNames = new TreeMap<Pair, PVAR_NAME>(currentState._tmIntermNames);
        s._alIntermNames = new ArrayList<RDDL.PVAR_NAME>(currentState._alIntermNames);
        s._alObservNames = new ArrayList<RDDL.PVAR_NAME>(currentState._alObservNames);
        s._alNonFluentNames = new ArrayList<RDDL.PVAR_NAME>(currentState._alNonFluentNames);

        s._hmTypeMap = new HashMap<String, ArrayList<PVAR_NAME>>();
        for (String key : currentState._hmTypeMap.keySet()) {
            ArrayList<PVAR_NAME> value = currentState._hmTypeMap.get(key);
            s._hmTypeMap.put(key, new ArrayList<RDDL.PVAR_NAME>(value));
        }

        s._state = new HashMap<RDDL.PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();
        for (PVAR_NAME key : currentState._state.keySet()) {
            HashMap<ArrayList<LCONST>, Object> value = currentState._state.get(key);
            s._state.put(key, new HashMap<ArrayList<LCONST>, Object>(value));
        }

        s._nonfluents = new HashMap<RDDL.PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();
        for (PVAR_NAME key : currentState._nonfluents.keySet()) {
            HashMap<ArrayList<LCONST>, Object> value = currentState._nonfluents.get(key);
            s._nonfluents.put(key, new HashMap<ArrayList<LCONST>, Object>(value));
        }

        s._actions = new HashMap<RDDL.PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();
        for (PVAR_NAME key : currentState._actions.keySet()) {
            HashMap<ArrayList<LCONST>, Object> value = currentState._actions.get(key);
            s._actions.put(key, new HashMap<ArrayList<LCONST>, Object>(value));
        }

        s._interm = new HashMap<RDDL.PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();
        for (PVAR_NAME key : currentState._interm.keySet()) {
            HashMap<ArrayList<LCONST>, Object> value = currentState._interm.get(key);
            s._interm.put(key, new HashMap<ArrayList<LCONST>, Object>(value));
        }

        s._observ = new HashMap<RDDL.PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();
        for (PVAR_NAME key : currentState._observ.keySet()) {
            HashMap<ArrayList<LCONST>, Object> value = currentState._observ.get(key);
            s._observ.put(key, new HashMap<ArrayList<LCONST>, Object>(value));
        }

        s._alIntermGfluentOrdering = currentState._alIntermGfluentOrdering;
        s._alActionPreconditions = currentState._alActionPreconditions;
        s._alStateInvariants = currentState._alStateInvariants;
        s._reward = currentState._reward;
        s._nMaxNondefActions = currentState._nMaxNondefActions;

        s._nextState = new HashMap<RDDL.PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();
        for (PVAR_NAME key : currentState._nextState.keySet()) {
            HashMap<ArrayList<LCONST>, Object> value = currentState._nextState.get(key);
            s._nextState.put(key, new HashMap<ArrayList<LCONST>, Object>(value));
        }

        return s;
    }


    public void writeToLog(String msg) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(LOG_FILE + "-" + this.port + ".log", true));
        bw.write(msg);
        bw.newLine();
        bw.flush();
        bw.close();
    }

    HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> copyObserv(
            HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observ) {
        HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> r = new
                HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>();

        for (PVAR_NAME pn : observ.keySet()) {
            HashMap<ArrayList<LCONST>, Object> v =
                    new HashMap<ArrayList<LCONST>, Object>();
            for (ArrayList<LCONST> aa : observ.get(pn).keySet()) {
                ArrayList<LCONST> raa = new ArrayList<LCONST>();
                for (LCONST lc : aa) {
                    raa.add(lc);
                }
                v.put(raa, observ.get(pn).get(aa));
            }
            r.put(pn, v);
        }
        return r;
    }

    void initializeState(RDDL rddl, String requestedInstance) {
        state = new State();
        instance = rddl._tmInstanceNodes.get(requestedInstance);
        nonFluents = null;
        if (instance._sNonFluents != null) {
            nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
        }
        domain = rddl._tmDomainNodes.get(instance._sDomain);
        if (nonFluents != null && !instance._sDomain.equals(nonFluents._sDomain)) {
            try {
                throw new Exception("Domain name of instance and fluents do not match: " +
                        instance._sDomain + " vs. " + nonFluents._sDomain);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void resetState() {
        state.init(domain._hmObjects, nonFluents != null ? nonFluents._hmObjects : null, instance._hmObjects,
                domain._hmTypes, domain._hmPVariables, domain._hmCPF,
                instance._alInitState, nonFluents == null ? new ArrayList<PVAR_INST_DEF>() : nonFluents._alNonFluents, instance._alNonFluents,
                domain._alStateConstraints, domain._alActionPreconditions, domain._alStateInvariants,
                domain._exprReward, instance._nNonDefActions);

        if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
                || (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
            boolean observations_present = (state._alObservNames.size() > 0);
            System.err.println("WARNING: Domain '" + domain._sDomainName
                    + "' partially observed (PO) flag and presence of observations mismatched.\nSetting PO flag = " + observations_present + ".");
            domain._bPartiallyObserved = observations_present;
        }

    }

    static Object getValue(String pname, String pvalue, State state) {

        TYPE_NAME tname = state._hmPVariables.get(new PVAR_NAME(pname))._typeRange;

        if (TYPE_NAME.INT_TYPE.equals(tname)) {
            return Integer.valueOf(pvalue);
        }

        if (TYPE_NAME.BOOL_TYPE.equals(tname)) {
            return Boolean.valueOf(pvalue);
        }

        if (TYPE_NAME.REAL_TYPE.equals(tname)) {
            return Double.valueOf(pvalue);
        }


        if (pvalue.startsWith("@")) {
            return new ENUM_VAL(pvalue);
        } else {
            return new OBJECT_VAL(pvalue);
        }

    }

    static ArrayList<PVAR_INST_DEF> processXMLAction(DOMParser p, InputSource isrc,
                                                     State state) throws Exception {
        try {
            p.parse(isrc);
            Element e = p.getDocument().getDocumentElement();
            if (SHOW_XML) {
                System.out.println("Received action msg:");
                printXMLNode(e);
            }
            if (e.getNodeName().equals(RESOURCE_REQUEST)) {
                return null;
            }

            if (!e.getNodeName().equals(ACTIONS)) {
                System.out.println("ERROR: NO ACTIONS NODE");
                System.out.println("Received action msg:");
                printXMLNode(e);
                throw new Exception("ERROR: NO ACTIONS NODE");
            }
            NodeList nl = e.getElementsByTagName(ACTION);
            if (nl != null) {
                ArrayList<PVAR_INST_DEF> ds = new ArrayList<PVAR_INST_DEF>();
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    String name = getTextValue(el, ACTION_NAME).get(0);
                    ArrayList<String> args = getTextValue(el, ACTION_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String pvalue = getTextValue(el, ACTION_VALUE).get(0);
                    Object value = getValue(name, pvalue, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, value, lcArgs);
                    ds.add(d);
                }
                return ds;
            } else
                return new ArrayList<PVAR_INST_DEF>();

        } catch (Exception e) {
            System.out.println("FATAL SERVER ERROR:\n" + e);
            throw e;
        }
    }

    public static void sendOneMessage(OutputStreamWriter osw, String msg) throws IOException {
        if (NO_XML_HEADING) {
            osw.write(msg.substring(39));
        } else {
            osw.write(msg + '\0');
        }
        osw.flush();
    }

    public static final int MAX_BYTES = 10485760;


    public static InputSource readOneMessage(InputStream isr) {
        byte[] bytes = new byte[MAX_BYTES];
        try {

            int cur_pos = 0;
            while (true && cur_pos < MAX_BYTES) {
                cur_pos += isr.read(bytes, cur_pos, 1);
                if (cur_pos == -1 ||
                        bytes[cur_pos - 1] == '\0')
                    break;
            }
            if (SHOW_MSG) {
                System.out.println("Received message [" + (cur_pos - 1) + "]: **" + new String(bytes, 0, cur_pos - 1) + "**");
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, cur_pos - 1);
            InputSource isrc = new InputSource();
            isrc.setByteStream(bais);
            return isrc;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static public String createClientHostMessage(String client_hostname, String client_IP) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(CLIENT_INFO);
            dom.appendChild(rootEle);
            addOneText(dom, rootEle, CLIENT_HOSTNAME, client_hostname);
            addOneText(dom, rootEle, CLIENT_IP, client_IP);
            return Simclient.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static String createXMLSessionInit(int numRounds, double timeAllowed, double stepTime, Simserver server, String problemName) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(SESSION_INIT);
            dom.appendChild(rootEle);

            INSTANCE instance = server.rddl._tmInstanceNodes.get(server.requestedInstance);
            DOMAIN domain = server.rddl._tmDomainNodes.get(instance._sDomain);

            String domainFile = SERVER_FILES_DIR + "/" + domain._sFileName + "." + server.inputLanguage;
            String instanceFile = SERVER_FILES_DIR + "/" + instance._sFileName + "." + server.inputLanguage;

            StringBuilder task = new StringBuilder(new String(Files.readAllBytes(Paths.get(domainFile))));
            task.append(System.getProperty("line.separator"));
            task.append(System.getProperty("line.separator"));
            task.append(new String(Files.readAllBytes(Paths.get(instanceFile))));
            task.append(System.getProperty("line.separator"));

            byte[] encodedBytes = Base64.getEncoder().encode(task.toString().getBytes());

            addOneText(dom, rootEle, TASK_DESC, new String(encodedBytes));
            String a = new String(encodedBytes);
            addOneText(dom, rootEle, SESSION_ID, server.id + "");
            addOneText(dom, rootEle, NUM_ROUNDS, numRounds + "");
            addOneText(dom, rootEle, TIME_ALLOWED, timeAllowed + "");
            //by yi
            addOneText(dom, rootEle, B_F, BF + "");
            addOneText(dom, rootEle, STEP_ALLOWED, stepTime + "");
            addOneText(dom, rootEle, PROBLEM_NAME, problemName);
            return Simclient.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static void processXMLSessionRequest(DOMParser p, InputSource isrc,
                                         Simserver server) {
        try {
            p.parse(isrc);
            Element e = p.getDocument().getDocumentElement();
            if (e.getNodeName().equals(SESSION_REQUEST)) {
                server.clientName = getTextValue(e, CLIENT_NAME).get(0);
                ArrayList<String> lang = getTextValue(e, INPUT_LANGUAGE);
                if (lang != null && lang.size() > 0) {
                    if (lang.get(0).trim().equals("pddl")) {
                        server.inputLanguage = "pddl";
                    }
                }
                NodeList nl = e.getElementsByTagName(NO_XML_HEADER);
                if (nl.getLength() > 0) {
                    NO_XML_HEADING = true;
                }
            }
            return;
        } catch (SAXException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return;
    }


    public static ArrayList<String> getTextValue(Element ele, String tagName) {
        ArrayList<String> returnVal = new ArrayList<String>();

        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            for (int i = 0; i < nl.getLength(); i++) {
                Element el = (Element) nl.item(i);
                returnVal.add(el.getFirstChild().getNodeValue());
            }
        }
        return returnVal;
    }

    static String createXMLTurn(State state, int turn, DOMAIN domain,
                                HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                                double timeLeft, double immediateReward) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(TURN);
            dom.appendChild(rootEle);
            Element turnNum = dom.createElement(TURN_NUM);
            Text textTurnNum = dom.createTextNode(turn + "");
            turnNum.appendChild(textTurnNum);
            rootEle.appendChild(turnNum);
            Element timeElem = dom.createElement(TIME_LEFT);
            Text textTimeElem = dom.createTextNode(timeLeft + "");
            timeElem.appendChild(textTimeElem);
            rootEle.appendChild(timeElem);
            Element immediateRewardElem = dom.createElement(IMMEDIATE_REWARD);
            Text textImmediateRewardElem = dom.createTextNode(immediateReward + "");
            immediateRewardElem.appendChild(textImmediateRewardElem);
            rootEle.appendChild(immediateRewardElem);

            if (!domain._bPartiallyObserved || observStore != null) {
                for (PVAR_NAME pn :
                        (domain._bPartiallyObserved
                                ? observStore.keySet()
                                : state._state.keySet())) {
                    if (domain._bPartiallyObserved && observStore != null)
                        state._observ.put(pn, observStore.get(pn));

                    ArrayList<ArrayList<LCONST>> gfluents = state.generateAtoms(pn);
                    for (ArrayList<LCONST> gfluent : gfluents) {
                        Element ofEle = dom.createElement(OBSERVED_FLUENT);
                        rootEle.appendChild(ofEle);
                        Element pName = dom.createElement(FLUENT_NAME);
                        Text pTextName = dom.createTextNode(pn.toString());
                        pName.appendChild(pTextName);
                        ofEle.appendChild(pName);
                        for (LCONST lc : gfluent) {
                            Element pArg = dom.createElement(FLUENT_ARG);
                            Text pTextArg = dom.createTextNode(lc.toSuppString());
                            pArg.appendChild(pTextArg);
                            ofEle.appendChild(pArg);
                        }
                        Element pValue = dom.createElement(FLUENT_VALUE);
                        Object value = state.getPVariableAssign(pn, gfluent);
                        if (value == null) {
                            System.out.println("STATE:\n" + state);
                            throw new Exception("ERROR: Could not retrieve value for " + pn + gfluent.toString());
                        }

                        Text pTextValue = value instanceof LCONST
                                ? dom.createTextNode(((LCONST) value).toSuppString())
                                : dom.createTextNode(value.toString());
                        pValue.appendChild(pTextValue);
                        ofEle.appendChild(pValue);
                    }
                }
            } else {
                Element ofEle = dom.createElement(NULL_OBSERVATIONS);
                rootEle.appendChild(ofEle);
            }
            if (SHOW_XML) {
                printXMLNode(dom);
                System.out.println();
                System.out.flush();
            }
            return (Simclient.serialize(dom));

        } catch (Exception e) {
            System.out.println("FATAL SERVER EXCEPTION: " + e);
            e.printStackTrace();
            throw e;
        }
    }

    static String createXMLResourceNotification(double timeLeft) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(RESOURCE_NOTIFICATION);
            dom.appendChild(rootEle);
            addOneText(dom, rootEle, TIME_LEFT, timeLeft + "");
            addOneText(dom, rootEle, MEMORY_LEFT, "enough");
            return Simclient.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    public static void addOneText(Document dom, Element p,
                                  String name, String value) {
        Element e = dom.createElement(name);
        Text text = dom.createTextNode(value);
        e.appendChild(text);
        p.appendChild(e);
    }

    static String createXMLSessionEnd(String requested_instance,
                                      double reward, int roundsUsed, long timeLeft,
                                      String clientName, int sessionId) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(SESSION_END);
            dom.appendChild(rootEle);
            addOneText(dom, rootEle, INSTANCE_NAME, requested_instance);
            addOneText(dom, rootEle, TOTAL_REWARD, reward + "");
            addOneText(dom, rootEle, ROUNDS_USED, roundsUsed + "");
            addOneText(dom, rootEle, CLIENT_NAME, clientName + "");
            addOneText(dom, rootEle, SESSION_ID, sessionId + "");
            addOneText(dom, rootEle, TIME_LEFT, timeLeft + "");
            return Simclient.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    public static void showInputSource(InputSource isrc) {
        InputStream is = isrc.getByteStream();
        byte[] bytes;
        try {
            int size = is.available();
            bytes = new byte[size];
            is.read(bytes);
            System.out.println("==BEGIN IS==");
            System.out.write(bytes, 0, size);
            System.out.println("\n==END IS==");
        } catch (IOException e) {
            System.out.println(">>> Inputstream error");
            e.printStackTrace();
        }
    }

    public static void printXMLNode(Node n) {
        printXMLNode(n, "", 0);
    }

    public static void printXMLNode(Node n, String prefix, int depth) {

        try {
            System.out.print("\n" + Pad(depth) + "[" + n.getNodeName());
            NamedNodeMap m = n.getAttributes();
            for (int i = 0; m != null && i < m.getLength(); i++) {
                Node item = m.item(i);
                System.out.print(" " + item.getNodeName() + "=" + item.getNodeValue());
            }
            System.out.print("] ");

            NodeList cn = n.getChildNodes();

            for (int i = 0; cn != null && i < cn.getLength(); i++) {
                Node item = cn.item(i);
                if (item.getNodeType() == Node.TEXT_NODE) {
                    String val = item.getNodeValue().trim();
                    if (val.length() > 0) System.out.print(" \"" + item.getNodeValue().trim() + "\"");
                } else
                    printXMLNode(item, prefix, depth + 2);
            }
        } catch (Exception e) {
            System.out.println(Pad(depth) + "Exception e: ");
        }
    }

    public static StringBuffer Pad(int depth) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < depth; i++)
            sb.append("  ");
        return sb;
    }

    //by yi
    public static void writeFile(String content, String fileName) {
        File file = new File(fileName);
        FileOutputStream fos = null;
        OutputStreamWriter osw = null;
        try {
            if (!file.exists()) {
                boolean hasFile = file.createNewFile();
                if (hasFile) {
                    System.err.println("file not exists, create new file");
                }
                fos = new FileOutputStream(file);
            } else {
                fos = new FileOutputStream(file, true);
            }
            osw = new OutputStreamWriter(fos, "utf-8");
            osw.write(content);
            osw.write("\r\n");
            System.out.println("write success");
        } catch (Exception e) {
            System.err.println("write to file error");
        } finally {
            try {
                if (osw != null) {
                    osw.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                System.err.println("stream closed error");
            }
        }
    }


    static Pair<Double, Double> processXMLPlan(DOMParser p, InputSource isrc, State state, ArrayList<PVAR_INST_DEF> reAct, ArrayList<PVAR_INST_DEF> reSt) throws Exception {
        double immediateReward = 0.0;
        double stateFitness = 0.0;
        try {
            p.parse(isrc);
            Element e = p.getDocument().getDocumentElement();

            if (e.getNodeName().equals(RESOURCE_REQUEST)) {
                return new Pair<>(0.0, 0.0);
            }

            if (!e.getNodeName().equals(P)) {
                System.out.println("receive plan error!!!!");
                System.out.println("Received action msg:");
                printXMLNode(e);
                throw new Exception("ERROR: NO ACTIONS NODE");
            }
            NodeList nl = e.getElementsByTagName(ACTION);
            if (nl != null) {
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    String name = getTextValue(el, ACTION_NAME).get(0);
                    ArrayList<String> args = getTextValue(el, ACTION_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String pvalue = getTextValue(el, ACTION_VALUE).get(0);
                    Object value = getValue(name, pvalue, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, value, lcArgs);
                    reAct.add(d);
                }
            } else
                reAct = new ArrayList<PVAR_INST_DEF>();

            if (e.getElementsByTagName(NULL_OBSERVATIONS).getLength() > 0) {
                reSt = null;
                System.out.println("receive state == null ");
            }

            ArrayList<String> ir = getTextValue(e, IMMEDIATE_REWARD);
            if (ir != null) {
                immediateReward = Double.valueOf(ir.get(0));
            }
            ir = getTextValue(e, SF);
            if (ir != null) {
                stateFitness = Double.valueOf(ir.get(0));
            }

            NodeList nls = e.getElementsByTagName(OBSERVED_FLUENT);
            if (nls != null && nls.getLength() > 0) {
                for (int i = 0; i < nls.getLength(); i++) {
                    Element el = (Element) nls.item(i);
                    String name = getTextValue(el, FLUENT_NAME).get(0);
                    ArrayList<String> args = getTextValue(el, FLUENT_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String value = getTextValue(el, FLUENT_VALUE).get(0);
                    Object r = getValue(name, value, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, r, lcArgs);
                    reSt.add(d);
                }
            } else
                reSt = new ArrayList<PVAR_INST_DEF>();

        } catch (Exception e) {
            System.out.println("processXMLState ERROR:\n" + e);

            throw e;

        }
        return new Pair<>(immediateReward, stateFitness);
    }

}


