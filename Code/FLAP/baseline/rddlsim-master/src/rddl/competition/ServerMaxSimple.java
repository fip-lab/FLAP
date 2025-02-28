/**
 * @Author Yi
 * @Description 重写的server simple max ；原本的serverMAX不可处理sogbofa的问题；
 * 1、解决sogbofa问题（sogbofa暂时可以运行，但存在1、client端脚本不可运行问题；；2、运行报错问题；）；
 * 2、可以处理prost（注意server的参数设置问题，尝试使用original server运行prost ）；
 * 3、增加相关信息的输出，并思考可以用来共同对比的实例，
 * 4、增加写入log文件的功能；
 * <p>
 * 注意：写入文件时不可对文件进行其他操作；这会导致写入文件错误；
 * @Date 2021/5/12
 **/

package rddl.competition;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.*;
import rddl.State;
import rddl.parser.parser;
import rddl.policy.Policy;
import rddl.policy.RandomBoolPolicy;
import rddl.viz.GenericScreenDisplay;
import rddl.viz.NullScreenDisplay;
import rddl.viz.StateViz;

import util.Pair;
import util.Timer;

public class ServerMaxSimple implements Runnable {

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
    public static final String TOTAL_REWARD = "total-reward";
    public static final String IMMEDIATE_REWARD = "immediate-reward";
    public static final String NUM_ROUNDS = "num-rounds";
    public static final String TIME_ALLOWED = "time-allowed";
    public static final String ROUNDS_USED = "rounds-used";

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

    public static final int PORT_NUMBER = 2356;
    public static final String HOST_NAME = "localhost";
    public static final int DEFAULT_SEED = 0;

    public static final String NO_XML_HEADER = "no-header";
    public static boolean NO_XML_HEADING = false;
    public static final boolean SHOW_MEMORY_USAGE = true;
    public static final Runtime RUNTIME = Runtime.getRuntime();
    private static DecimalFormat _df = new DecimalFormat("0.##");


    //    private Socket connection;
    private RDDL rddl = null;
    private static int ID = 0;
    private static int DEFAULT_NUM_ROUNDS = 50;
    private static long DEFAULT_TIME_ALLOWED = 1080000; // milliseconds = 18 minutes
    //    private static double stateActionFitness = 0.0;
    private static boolean USE_TIMEOUT = true;
    private static boolean INDIVIDUAL_SESSION = false;
    private static String LOG_FILE = "rddl";
    private static boolean MONITOR_EXECUTION = false;
    private static String SERVER_FILES_DIR = "";
    //    private static String CLIENT_FILES_DIR = "";
    //add by yi
    private static long STEP_TIME_ALLOWED = Integer.MAX_VALUE;
    private static String ORIGIN_FILES_DIR = "files\\baseline";
    //    private static String ORIGIN_FILES_DIR14 = "files\\comp-2014";
//    private static String ORIGIN_FILES_DIR11 = "files\\comp-2011";
    private static String SOGBOFA_FILES_DIR = "files\\sogDomains";
    private static String PROST_FILES_DIR = "files\\prostDomains";

    public int port;
    public int id;
    //    public String clientName = null;
    public String requestedInstance = null;
    public RandomDataGenerator rand;
    public boolean executePolicy = true;
    public String inputLanguage = "rddl";
    public int numSimulations = 0;

    public State state;
    public INSTANCE instance;
    public NONFLUENTS nonFluents;
    public DOMAIN domain;
    public StateViz stateViz;

    /**
     * @Author Yi
     * 增加相关的数据结构；
     **/
    public static int clientNum = 3;
    //用于存储client链接
    private static ArrayList<recordClient> clientList = new ArrayList<>();
    private static ArrayList<recordClient> clientErr = new ArrayList<>();
    //存储实例名称
    public static ArrayList<String> instNameList = new ArrayList<>();


    //记录每个client的信息
    public static class recordClient {
        public String clientName = null;
        //id用于标记client的类型，并用于选择执行不同的inst实例；
        public int id = 0;
        public Socket socket = null;
        public String client_hostname = "";
        public String client_IP = "";
        public BufferedInputStream isr = null;
        public InputSource isrc = null;
        public BufferedOutputStream os = null;
        public OutputStreamWriter osw = null;
        public String msg = null;
//        public long start_time = 0;

        //add store instance
        public ArrayList<PVAR_INST_DEF> action = null;
        boolean roundRequested = false;
        public double reward = 0.0;
        public double fitnesss = 0.0;
    }

    /**
     * @param args 1. rddl description file name (can be directory), in RDDL format, with complete path
     *             2. (optional) port number
     *             3. (optional) random seed
     */
    public static void main(String[] args) {

        // StateViz state_viz = new GenericScreenDisplay(true);
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
            // Load RDDL files
            SERVER_FILES_DIR = new String(args[0]);
//            CLIENT_FILES_DIR = new String(args[0]);
            ORIGIN_FILES_DIR = new String(args[0]);

            File[] subDirs = new File(args[0]).listFiles(File::isDirectory);
            // Check if there are subdirectories called "client" and "server"
//            for (File subDir : subDirs) {
//                if (subDir.getName().equals("server")) {
//                    SERVER_FILES_DIR = new String(subDir.getPath());
//                } else if (subDir.getName().equals("client")) {
//                    CLIENT_FILES_DIR = new String(subDir.getPath());
//                }
//            }

            //读取rddl文件,写到每个client中；
            //RDDL rddl = new RDDL(SERVER_FILES_DIR);

            if (args.length > 1) {
                DEFAULT_NUM_ROUNDS = Integer.valueOf(args[1]);
            }

            if (args.length > 2) {
                if (args[2].equals("0")) {
                    USE_TIMEOUT = false;
                } else {
                    USE_TIMEOUT = true;
                    DEFAULT_TIME_ALLOWED = Integer.valueOf(args[2]) * 1000;
                }
            }
            //by yi
            //add step time allowed;
            if (args.length > 3) {
                STEP_TIME_ALLOWED = Integer.valueOf(args[3]);
            }
            if (args.length > 4) {
                SOGBOFA_FILES_DIR = new String(args[4]);
            }
            if (args.length > 5) {
                port = Integer.valueOf(args[5]);
//                PROST_FILES_DIR = new String(args[5]);
            }
            ServerSocket socket1 = new ServerSocket(port);
            if (args.length > 6) {
                clientNum = Integer.valueOf(args[6]);
            }
            int rand_seed = 666;
            if (args.length > 7) {
                rand_seed = Integer.valueOf(args[7]);
            } else {
                rand_seed = DEFAULT_SEED;
            }
            if (args.length > 8) {
                LOG_FILE = args[8] + "/logs";
            }
            if (args.length > 9) {
                assert (args[9].equals("0") || args[9].equals("1"));
                if (args[9].equals("1")) {
                    MONITOR_EXECUTION = true;
                }
            }
            if (args.length > 10) {
                state_viz = (StateViz) Class.forName(args[10]).newInstance();
            }
            System.out.println("RDDL Server Initialized");
            while (true) {
//                Socket connection = socket1.accept();
                clientList.clear();
                clientErr.clear();
                System.out.println("wait client connect.....");
                for (int i = 0; i < clientNum; i++) {
                    Socket connection = socket1.accept();
                    //用于存储socket连接信息；
                    recordClient re = new recordClient();
                    re.id = i;
                    re.socket = connection;
                    clientList.add(re);
                    System.out.println("client connect ip :" + connection.getInetAddress());
                }
                RandomDataGenerator rdg = new RandomDataGenerator();
                rdg.reSeed(rand_seed + ID);
                // Ensures predictable but different seed on every session if a single client connects and all session requests run in same order
                ServerMaxSimple serverMaxSimple = new ServerMaxSimple(++ID, state_viz, port, rdg);
                serverMaxSimple.run();
                if (INDIVIDUAL_SESSION) {
                    break;
                }
                //clear
                for (recordClient c : clientList)
                    c.socket.close();
                for (recordClient c : clientList)
                    c.socket.close();
                clientList.clear();
                clientErr.clear();
            }
            System.out.println("Single client has connected, no more are accepted.");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            System.out.println(e);
            e.printStackTrace();
        }
    }

    ServerMaxSimple(int i, StateViz state_viz, int port, RandomDataGenerator rgen) {
//        this.connection = s;
        this.id = i;
//        this.rddl = rddl;
        this.stateViz = state_viz;
        this.port = port;
        this.rand = rgen;
    }

    public void run() {
        DOMParser p = new DOMParser();
        int numRounds = DEFAULT_NUM_ROUNDS;
        long timeAllowed = DEFAULT_TIME_ALLOWED;
        long stepTime = STEP_TIME_ALLOWED;
        double totalmemory = 0.0;
        state = new State();

        try {
            long start_time = System.currentTimeMillis();
            String instanceName = "";
            ArrayList<State> stateList = new ArrayList<>();
//            ArrayList<State> initialStateList = new ArrayList<>();
            ArrayList<RDDL> rddlList = new ArrayList<>();
            for (int i = 0; i < clientList.size(); i++) {
                State st = new State();
                recordClient c = clientList.get(i);
                // Log client host name and IP address
                InetAddress ia = c.socket.getInetAddress();
                c.client_hostname = ia.getCanonicalHostName();
                c.client_IP = ia.getHostAddress();
//                c.start_time = System.currentTimeMillis();
                System.out.println("Connection from client at address " + c.client_hostname + " / " + c.client_IP);
                writeToLog(createClientHostMessage(c.client_hostname, c.client_IP));

                // Begin communication protocol from PROTOCOL.txt
                c.isr = new BufferedInputStream(c.socket.getInputStream());
                c.isrc = readOneMessage(c.isr);

                requestedInstance = null;
                processXMLSessionRequest(p, c, this);
                //需要确保每个client的instance name相同
                if (i == 0) {
                    instanceName = requestedInstance;

                    //初始化rddl
                    if (c.clientName.contains("SOG")) {
                        //sogbofa
                        rddl = new RDDL(SOGBOFA_FILES_DIR);
                        SERVER_FILES_DIR = SOGBOFA_FILES_DIR;
                    } else if (c.clientName.contains("ori")) {
                        //origin
                        rddl = new RDDL(ORIGIN_FILES_DIR);
                        SERVER_FILES_DIR = ORIGIN_FILES_DIR;
                    } else if (c.clientName.contains("pro")) {
                        //prost
                        rddl = new RDDL(PROST_FILES_DIR);
                        SERVER_FILES_DIR = PROST_FILES_DIR;
                    } else {
                        //// TODO: 2021/5/8 client没有返回正确的clientName，处理报错；
                        System.err.println("client initial error");
                        rddl = new RDDL(ORIGIN_FILES_DIR);
                        SERVER_FILES_DIR = ORIGIN_FILES_DIR;
                    }
                    if (!rddl._tmInstanceNodes.containsKey(requestedInstance)) {
                        System.out.println("Instance name '" + requestedInstance + "' not found.");
                        return;
                    }
                    initializeState(rddl, requestedInstance, state);
                } else {
                    if (!instanceName.equals(requestedInstance)) {
                        System.out.println("instance error!!!");
                        return;
                    }
                }
                RDDL rddl1 = new RDDL(SERVER_FILES_DIR);
                initializeState(rddl1, requestedInstance, st);
                rddlList.add(rddl1);
//                initialStateList.add(st);
                stateList.add(st);
                System.out.println("Client name: " + c.clientName);
                System.out.println("Instance requested: " + requestedInstance);


                c.os = new BufferedOutputStream(c.socket.getOutputStream());
                c.osw = new OutputStreamWriter(c.os, "US-ASCII");
                String msg = createXMLSessionInit(numRounds, timeAllowed, stepTime, this);
                sendOneMessage(c.osw, msg);
            }
//            State testState = new State();
//            initializeState(rddl, requestedInstance, testState);
//            print log
//            {
//                writeFile("-----------------------------------------------------------------------------------------------", "result-log.txt");
//                String m = requestedInstance + "----client num=" + Integer.toString(clientList.size()) + "----" + "client-name=";
//                for (recordClient c : clientList)
//                    m += c.clientName + "---";
//                writeFile(m, "result-log.txt");
//            }
            boolean OUT_OF_TIME = false;
            double accum_total_reward = 0d;
            ArrayList<Double> rewards = new ArrayList<Double>();
            ArrayList<Double> rewardList = new ArrayList<Double>();
            int r = 0;
            for (; r < numRounds && !OUT_OF_TIME; r++) {
//                boolean roundRequested = false;
                for (recordClient c : clientList) {
                    // roundRequested是标记，每个client都request以后才能修改为true；
                    c.roundRequested = false;
                    while (!c.roundRequested) {
                        c.isrc = readOneMessage(c.isr);
                        c.roundRequested = processXMLRoundRequest(p, c.isrc, this);
                        if (!c.roundRequested) {
                            c.msg = createXMLResourceNotification(timeAllowed - System.currentTimeMillis() + start_time);
                            sendOneMessage(c.osw, c.msg);
                        }
                    }

                    if (!executePolicy) {
                        r--;
                    }


                    c.msg = createXMLRoundInit(r + 1, numRounds, timeAllowed - System.currentTimeMillis() + start_time, timeAllowed);
                    sendOneMessage(c.osw, c.msg);
                }
                for (int i = 0; i < stateList.size(); i++) {
                    resetState(stateList.get(i), rddlList.get(i));
//                    stateList.clear();
//                    stateList.add(cloneState(initialStateList.get(i)));

                }
                resetState(state, rddl);

//                resetState(testState);
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
                    System.out.println("current client number :" + clientList.size());
                }

                double immediate_reward = 0.0d;
                double accum_reward = 0.0d;
                double cur_discount = 1.0d;
                int h = 0;
                HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore = null;
                while (true) {
                    Timer timer = new Timer();

                    //if ( observStore != null) {
                    //	for ( PVAR_NAME pn : observStore.keySet() ) {
                    //		System.out.println("check3 " + pn);
                    //		for( ArrayList<LCONST> aa : observStore.get(pn).keySet()) {
                    //			System.out.println("check3 :" + aa + ": " + observStore.get(pn).get(aa));
                    //		}
                    //	}
                    //}
                    //// TODO: 2021/5/13 使用多线程，并行执行；
                    getActionThread(timeAllowed, start_time, h, state, stateList, observStore);
//                    getActionCy(timeAllowed, start_time, h, observStore,cloneState(state));

                    //select actionList
                    recordClient maxCli = selectAction(h, instance._nHorizon);

                    //greedy SM
//                    int maxNum = 0;
//                    for (int i = 1; i < clientList.size(); i++) {
//                        if (clientList.get(maxNum).reward < clientList.get(i).reward)
//                            maxNum = i;
//                    }
//                    recordClient maxCli = clientList.get(maxNum);


//                    {
//                        String m = h + "chose max client;;";
//                        for (int i = 0; i < clientList.size(); i++) {
//                            m += i + "=" + clientList.get(i).reward + "--";
//                        }
//                        m += ";;---max client:" + maxNum + "--";
////                        writeFile(m, "result-log.txt");
//                        System.out.println(m);
//                    }

                    try {
                        state.computeNextState(maxCli.action, rand);
                        state.checkStateActionConstraints(maxCli.action);
                    } catch (Exception ee) {
                        System.out.println("FATAL SERVER EXCEPTION:\n" + ee);
                        //ee.printStackTrace();

                        throw ee;
                    }


                    // Calculate reward / objective and store
                    immediate_reward = ((Number) domain._exprReward.sample(new HashMap<LVAR, LCONST>(),
                            state, rand)).doubleValue();
                    rewards.add(immediate_reward);
                    accum_reward += cur_discount * immediate_reward;
                    System.out.println("total reward========" + accum_reward);
                    //System.out.println("Accum reward: " + accum_reward + ", instance._dDiscount: " + instance._dDiscount +
                    //   " / " + (cur_discount * reward) + " / " + reward);
                    cur_discount *= instance._dDiscount;

                    if (SHOW_TIMING)
                        System.out.println("**TIME to copy observations & update rewards: " + timer.GetTimeSoFarAndReset());

                    stateViz.display(state, h);
                    state.advanceNextState();
//                    copyState(state._state,testState._state,state);

                    if (SHOW_TIMING)
                        System.out.println("**TIME to advance state: " + timer.GetTimeSoFarAndReset());

                    // Scott: Update 2014 to check for out of time... this can trigger
                    //        an early round end
                    OUT_OF_TIME = ((System.currentTimeMillis() - start_time) > timeAllowed) && USE_TIMEOUT;
                    h++;

                    // Thomas: Update 2018 to allow simulation of SSPs
                    if (OUT_OF_TIME) {
                        // System.out.println("OUT OF TIME!");
                        break;
                    }
                    if ((instance._termCond == null) && (h == instance._nHorizon)) {
                        // System.out.println("Horizon reached");
                        break;
                    }
                    if ((instance._termCond != null) && state.checkTerminationCondition(instance._termCond)) {
                        // System.out.println("Terminal state reached");
                        break;
                    }
                }
                rewardList.add(accum_reward);
                if (executePolicy) {
                    accum_total_reward += accum_reward;
                    System.out.println("** Round reward: " + accum_reward);
                }
                for (recordClient c : clientList) {
                    c.msg = createXMLRoundEnd(requestedInstance, r, accum_reward, h,
                            timeAllowed - System.currentTimeMillis() + start_time,
                            c.clientName, immediate_reward);
                    if (SHOW_MSG)
                        System.out.println("Sending msg:\n" + c.msg);
                    sendOneMessage(c.osw, c.msg);

                    writeToLog(c.msg);
                }
            }
            String cliNa = "";
            for (recordClient c : clientList) {
                c.msg = createXMLSessionEnd(requestedInstance, accum_total_reward, r,
                        timeAllowed - System.currentTimeMillis() + start_time, c.clientName, this.id);
                if (SHOW_MSG)
                    System.out.println("Sending msg:\n" + c.msg);
                sendOneMessage(c.osw, c.msg);
                writeToLog(c.msg);
                System.out.println("Session finished successfully: " + c.clientName);
                cliNa += c.clientName + "=" + c.socket.getInetAddress() + ";";
            }
            accum_total_reward = 0;
            String roundReward = "";
            Double maxReward = -Double.MAX_VALUE;
            for (Double re : rewardList) {
                accum_total_reward += re;
                roundReward += re + ",";
                if (re > maxReward)
                    maxReward = re;
            }

            //输出方差和每一步的reward信息；
            double deviation = 0.0;
            double standardDeviation = 0.0;
            double averageReward = accum_total_reward / r;
            for (Double re : rewardList) {
                deviation += Math.pow((re - averageReward), 2);
            }
            deviation = deviation / rewardList.size();
            standardDeviation = Math.sqrt(deviation);
            for (recordClient c : clientList)
                System.out.println("*Session finished successfully: " + c.clientName);
            System.out.println("Time left: " + (timeAllowed - System.currentTimeMillis() + start_time));
            System.out.println("*Time cost: " + (System.currentTimeMillis() - start_time) + "ms");
            System.out.println("*Average memory: " + totalmemory / r);
            System.out.println("Number of simulations: " + numSimulations);
            System.out.println("*Number of runs: " + r);
            System.out.println("Accumulated reward: " + (accum_total_reward));
            System.out.println("*Average reward: " + (accum_total_reward / r));
            System.out.println("*Best reward of every round: " + maxReward);
            System.out.println("The standard deviation : " + standardDeviation);
            System.out.println("The deviation : " + deviation);
            //format::   Session，Time cost，Average memory，Number of runs，Average reward
            String result = requestedInstance + "," + clientList.size() + "," + clientNum + "," + String.valueOf(System.currentTimeMillis() - start_time)
                    + "," + String.valueOf(totalmemory / r) + "," + String.valueOf(r)
                    + "," + String.valueOf(accum_total_reward / r) + "," + cliNa + "," + maxReward + "," + standardDeviation + "," + deviation + "," + roundReward;
            //add to end of the file
            writeFile(result, "result-DBN-6client.csv");
//            {
//                String m = "*Time cost: " + (System.currentTimeMillis() - start_time) + "ms" + ";;" + "*Average reward: " + (accum_total_reward / numRounds) + ";;";
//                writeFile(m, "result-log.txt");
//            }


        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\n>> TERMINATING TRIAL.");
//            if (INDIVIDUAL_SESSION) {
//                try {
//                    connection.close();
//                } catch (IOException ioe) {
//                }
//                System.exit(1);
//            }
        } finally {
            try {
                //清除client列表,并断开链接；
                for (recordClient c : clientList)
                    c.socket.close();
                for (recordClient c : clientErr)
                    c.socket.close();
                clientList.clear();
                clientErr.clear();
            } catch (IOException e) {
            }
        }
    }


    //add by yi
    public void getActionCy(long timeAllowed, long start_time, int h, HashMap<
            PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore, State state) {
//        State state = cloneState(state)
        System.out.println(h + "get Action begin ");
        for (recordClient c : clientList) {
            DOMParser p = new DOMParser();
            try {
                c.msg = createXMLTurn(state, h + 1, domain, observStore, timeAllowed - System.currentTimeMillis() + start_time, c.reward);

//                if (SHOW_TIMING)
//                    System.out.println("**TIME to create XML turn: " + timer.GetTimeSoFarAndReset());

                if (SHOW_MSG)
                    System.out.println("Sending msg:\n" + c.msg);

                sendOneMessage(c.osw, c.msg);

                c.action = null;
                while (c.action == null) {
                    c.isrc = readOneMessage(c.isr);
                    if (c.isrc == null) {
                        throw new Exception("FATAL SERVER EXCEPTION: EMPTY CLIENT MESSAGE");
                    }
                    c.action = processXMLAction(p, c.isrc, state, c);
                    if (c.action == null) {
                        c.msg = createXMLResourceNotification(timeAllowed - System.currentTimeMillis() + start_time);
                        sendOneMessage(c.osw, c.msg);
                        System.out.println("action == null");
                        break;
                    }
                }

                // Check action preconditions (also checks maxNonDefActions)
                try {
                    state.checkStateActionConstraints(c.action);
                } catch (Exception e) {
                    System.out.println("TRIAL ERROR -- ACTION NOT APPLICABLE:\n" + e);
                    if (INDIVIDUAL_SESSION) {
                        try {
                            c.socket.close();
                        } catch (IOException ioe) {
                        }
                        System.exit(1);
                    }
                }
                //Sungwook: this is not required.  -Scott
                //if ( h== 0 && domain._bPartiallyObserved && ds.size() != 0) {
                //	System.err.println("the first action for partial observable domain should be noop");
                //}
                if (SHOW_ACTIONS && executePolicy) {
                    boolean suppress_object_cast_temp = RDDL.SUPPRESS_OBJECT_CAST;
                    RDDL.SUPPRESS_OBJECT_CAST = true;
                    System.out.println(h + "** " + c.clientName + " Actions received: " + c.action);
                    RDDL.SUPPRESS_OBJECT_CAST = suppress_object_cast_temp;
                }

                try {
                    state.computeNextState(c.action, rand);
                } catch (Exception ee) {
                    System.out.println("FATAL SERVER EXCEPTION:\n" + ee);
                    //ee.printStackTrace();
                    if (INDIVIDUAL_SESSION) {
                        try {
                            c.socket.close();
                        } catch (IOException ioe) {
                        }
                        System.exit(1);
                    }
                    throw ee;
                }
                //for ( PVAR_NAME pn : state._observ.keySet() ) {
                //	System.out.println("check1 " + pn);
                //	for( ArrayList<LCONST> aa : state._observ.get(pn).keySet()) {
                //		System.out.println("check1 :" + aa + ": " + state._observ.get(pn).get(aa));
                //	}
                //}

//                if (SHOW_TIMING)
//                    System.out.println("**TIME to compute next state: " + timer.GetTimeSoFarAndReset());

                if (domain._bPartiallyObserved)
                    observStore = copyObserv(state._observ);

                // Calculate reward / objective and store
                c.reward = ((Number) domain._exprReward.sample(new HashMap<LVAR, LCONST>(),
                        state, rand)).doubleValue();
            } catch (Exception e) {
                System.err.println(h + "接收动作出错error:" + c.clientName);
//                writeFile(h + "接收动作出错error:" + c.clientName, "result-log.txt");
                c.reward = -Double.MAX_VALUE;
                c.action = new ArrayList<>();
            }
        }
        System.out.println("get Action end ");
    }


    //add by yi
    public void getActionThread(long timeAllowed, long start_time, int h, State state, ArrayList<State> stateList,
                                HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore) throws InterruptedException {
        // 开启线程个数和client个数相同；
        int threadNum = clientList.size();
        CountDownLatch threadSignal = new CountDownLatch(threadNum);
        // 创建固定长度的线程池,线程池大小和client的个数相关
        ExecutorService executor = Executors.newFixedThreadPool(threadNum);
        System.out.println(h + "get action begin");
//        {
//            String m = h + "get action begin" + "--";
//            writeFile(m, "result-log.txt");
//        }
        for (int j = 0; j < threadNum; j++) {
            //todo 写一个深度拷贝_state的函数
            State s = stateList.get(j);
            s._state = state._state;
            DOMParser p = new DOMParser();
            Runnable task = new actionThread(p, threadSignal, clientList.get(j), timeAllowed, start_time, h, observStore, j, s);
            executor.execute(task);
        }
        // 等待所有子线程执行完
        threadSignal.await();
        //固定线程池执行完成后 将释放掉资源 退出主进程
        executor.shutdown();
        //退出主进程
        System.out.println(h + "get Action end ");
//        {
//            String m = h + "get Action end " + "--";
//            writeFile(m, "result-log.txt");
//        }
    }

    //add by yi
    private class actionThread implements Runnable {
        private recordClient c;
        long timeAllowed;
        long start_time;
        int h;
        DOMParser p;
        CountDownLatch threadsSignal;
        HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore;
        int clientNum;
        State s;

        public actionThread(DOMParser p, CountDownLatch threadsSignal, recordClient c, long timeAllowed, long start_time, int h,
                            HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore, int j, State s) {
            this.c = c;
            this.timeAllowed = timeAllowed;
            this.start_time = start_time;
            this.h = h;
            this.threadsSignal = threadsSignal;
            this.observStore = observStore;
            this.p = p;
            this.clientNum = j;
            this.s = s;
        }

        public void run() {
//            synchronized (this) {
            try {

                c.msg = createXMLTurn(s, h + 1, domain, observStore, timeAllowed - System.currentTimeMillis() + start_time, c.reward);

//                if (SHOW_TIMING)
//                    System.out.println("**TIME to create XML turn: " + timer.GetTimeSoFarAndReset());

                if (SHOW_MSG)
                    System.out.println("Sending msg:\n" + c.msg);

                sendOneMessage(c.osw, c.msg);

                c.action = null;
                while (c.action == null) {
                    c.isrc = readOneMessage(c.isr);
                    if (c.isrc == null) {
                        throw new Exception("FATAL SERVER EXCEPTION: EMPTY CLIENT MESSAGE");
                    }
                    c.action = processXMLAction(p, c.isrc, s, c);
                    if (c.action == null) {
                        c.msg = createXMLResourceNotification(timeAllowed - System.currentTimeMillis() + start_time);
                        sendOneMessage(c.osw, c.msg);
                    }
                }
//                synchronized (this) {
//                {
//                    String m = h + "::" + clientNum + ":" + c.clientName + "Actions received: " + c.action + "--";
//                    writeFile(m, "result-log.txt");
//                }
                // Check action preconditions (also checks maxNonDefActions)
                try {
                    s.checkStateActionConstraints(c.action);
                } catch (Exception e) {
                    System.out.println("TRIAL ERROR -- ACTION NOT APPLICABLE:\n" + e);
                    if (INDIVIDUAL_SESSION) {
                        try {
                            c.socket.close();
                        } catch (IOException ioe) {
                        }
                        System.exit(1);
                    }
                }
                //Sungwook: this is not required.  -Scott
                //if ( h== 0 && domain._bPartiallyObserved && ds.size() != 0) {
                //	System.err.println("the first action for partial observable domain should be noop");
                //}
                if (SHOW_ACTIONS && executePolicy) {
                    boolean suppress_object_cast_temp = RDDL.SUPPRESS_OBJECT_CAST;
                    RDDL.SUPPRESS_OBJECT_CAST = true;
                    System.out.println("** " + c.clientName + " Actions received: " + c.action);
                    RDDL.SUPPRESS_OBJECT_CAST = suppress_object_cast_temp;
                }
//                synchronized (this) {
                try {
                    s.computeNextState(c.action, rand);
                } catch (Exception ee) {
                    System.out.println("get action error FATAL SERVER EXCEPTION:\n" + ee);
//                    writeFile("get action error FATAL SERVER EXCEPTION:\n" + ee, "result-log.txt");
                    //ee.printStackTrace();
                    if (INDIVIDUAL_SESSION) {
                        try {
                            c.socket.close();
                        } catch (IOException ioe) {
                        }
                        System.exit(1);
                    }
                    throw ee;
                }
//                }
                //for ( PVAR_NAME pn : state._observ.keySet() ) {
                //	System.out.println("check1 " + pn);
                //	for( ArrayList<LCONST> aa : state._observ.get(pn).keySet()) {
                //		System.out.println("check1 :" + aa + ": " + state._observ.get(pn).get(aa));
                //	}
                //}

//                if (SHOW_TIMING)
//                    System.out.println("**TIME to compute next state: " + timer.GetTimeSoFarAndReset());

                if (domain._bPartiallyObserved)
                    observStore = copyObserv(s._observ);

                // Calculate reward / objective and store
                c.reward = ((Number) domain._exprReward.sample(new HashMap<LVAR, LCONST>(),
                        s, rand)).doubleValue();
                System.out.println("reward :=" + c.reward);
//                }
            } catch (Exception e) {
                System.err.println(h + "接收动作出错error:" + c.clientName);
//                writeFile(h + "接收动作出错error:" + c.clientName, "result-log.txt");
                c.reward = -Double.MAX_VALUE;
                c.action = new ArrayList<>();
            }
            threadsSignal.countDown();
//            }
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

        //System.out.println("Observation pvars: " + observ);
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

    void initializeState(RDDL rddl, String requestedInstance, State state) {
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
                // TODO Auto-generated catch block
                e.printStackTrace();
//                if (INDIVIDUAL_SESSION) {
//                    try {
//                        connection.close();
//                    } catch (IOException ioe) {
//                    }
//                    System.exit(1);
//                }
            }
        }
    }

    void resetState(State state, RDDL rddl) {
        INSTANCE instance = rddl._tmInstanceNodes.get(requestedInstance);
        DOMAIN domain = rddl._tmDomainNodes.get(instance._sDomain);
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

        // Get the fluent value's range
        TYPE_NAME tname = state._hmPVariables.get(new PVAR_NAME(pname))._typeRange;

        // TYPE_NAMES are interned so that equality can be tested directly
        // (also helps enforce better type safety)
        if (TYPE_NAME.INT_TYPE.equals(tname)) {
            return Integer.valueOf(pvalue);
        }

        if (TYPE_NAME.BOOL_TYPE.equals(tname)) {
            return Boolean.valueOf(pvalue);
        }

        if (TYPE_NAME.REAL_TYPE.equals(tname)) {
            return Double.valueOf(pvalue);
        }

        // TODO: handle vectors <>
        // TODO: are enum int values handled correctly?  need an @

        // This allows object vals
        // TODO: should really verify tname is an enum val here by looking up it's definition
        if (pvalue.startsWith("@")) {
            // Must be an enum
            return new ENUM_VAL(pvalue);
        } else {
            return new OBJECT_VAL(pvalue);
        }

        //if ( state._hmObject2Consts.containsKey(tname)) {
        //	return new OBJECT_VAL(pvalue);
        //for( LCONST lc : state._hmObject2Consts.get(tname)) {
        //	if ( lc.toString().equals(pvalue)) {
        //		return lc;
        //	}
        //}
        //}

        //if ( state._hmTypes.containsKey(tname)) {
        //	return new ENUM_VAL(pvalue);
        //if ( state._hmTypes.get(tname) instanceof ENUM_TYPE_DEF ) {
        //	ENUM_TYPE_DEF etype = (ENUM_TYPE_DEF)state._hmTypes.get(tname);
        //	for ( ENUM_VAL ev : etype._alPossibleValues) {
        //		if ( ev.toString().equals(pvalue)) {
        //			return ev;
        //		}
        //	}
        //}
        //}

        //return null;
    }

    //// TODO: 2021/5/14  测试是否需要加锁synchronized
    static synchronized ArrayList<PVAR_INST_DEF> processXMLAction(DOMParser p, InputSource isrc,
                                                                  State state, recordClient c) throws Exception {
        try {
            //showInputSource(isrc); System.exit(1); // TODO
            p.parse(isrc);
            Element e = p.getDocument().getDocumentElement();
            if (SHOW_XML) {
                System.out.println("Received action msg:");
                printXMLNode(e);
            }
            if (e.getNodeName().equals(RESOURCE_REQUEST)) {
                return null;
            }
//            ArrayList<String> ir = getTextValue(e, "state-fitness");
//            if (ir != null) {
//                c.fitnesss = Double.valueOf(ir.get(0));
////                System.out.println("%%%%%%%%%%%%state fitness =" + c.fitnesss);
//            }
            c.fitnesss=0;
            if (!e.getNodeName().equals(ACTIONS)) {
                System.out.println("ERROR: NO ACTIONS NODE");
                System.out.println("Received action msg:");
                printXMLNode(e);
                throw new Exception("ERROR: NO ACTIONS NODE");
            }
            NodeList nl = e.getElementsByTagName(ACTION);
//			System.out.println(nl);
            if (nl != null) { // && nl.getLength() > 0) {
                ArrayList<PVAR_INST_DEF> ds = new ArrayList<PVAR_INST_DEF>();
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    String name = getTextValue(el, ACTION_NAME).get(0);
                    ArrayList<String> args = getTextValue(el, ACTION_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        //System.out.println("arg: " + arg);
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else // TODO $ <> (forgiving)... done$
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String pvalue = getTextValue(el, ACTION_VALUE).get(0);
                    Object value = getValue(name, pvalue, state); // TODO $ <> (forgiving)... done$
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, value, lcArgs);
                    ds.add(d);
                }
                return ds;
            } else
                return new ArrayList<PVAR_INST_DEF>(); // FYI: May be unreachable. -Scott
            //} else { // TODO: Removed by Scott, NOOP should not be handled differently
            //	nl = e.getElementsByTagName(NOOP);
            //	if ( nl != null && nl.getLength() > 0) {
            //		ArrayList<PVAR_INST_DEF> ds = new ArrayList<PVAR_INST_DEF>();
            //		return ds;
            //	}
            //}
        } catch (Exception e) {
            System.out.println("FATAL SERVER ERROR:\n" + e);
            //t.printStackTrace();
            throw e;
        }
    }

    public static void sendOneMessage(OutputStreamWriter osw, String msg) throws IOException {
//		System.out.println(msg);
        if (NO_XML_HEADING) {
//			System.out.println(msg.substring(39));
            osw.write(msg.substring(39));
        } else {
            osw.write(msg + '\0');
        }
        osw.flush();
    }


    // Synchronize because this uses a global bytes[] buffer
    public static synchronized InputSource readOneMessage(InputStream isr) {
        final int MAX_BYTES = 10485760;
        byte[] bytes = new byte[MAX_BYTES];
        try {

            int cur_pos = 0;
            //System.out.println("\n===\n");
            while (true && cur_pos < MAX_BYTES) {
                cur_pos += isr.read(bytes, cur_pos, 1);
                if (/* Socket closed  */ cur_pos == -1 ||
                        /* End of message */ bytes[cur_pos - 1] == '\0')
                    break;
                //System.out.print(cur_pos + "[" + Byte.toString(bytes[cur_pos - 1]) + "]");
            }
            //System.out.println("\n===\n");

            //while((character = isr.read()) != '\0' && character != -1) {
            //	message.append((char)character);
            //}
            if (SHOW_MSG) {
                System.out.println("Received message [" + (cur_pos - 1) + "]: **" + new String(bytes, 0, cur_pos - 1) + "**");
            }
            //ByteArrayInputStream bais = new ByteArrayInputStream(message.toString().getBytes());
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, cur_pos - 1); // No '\0'
            InputSource isrc = new InputSource();
            isrc.setByteStream(bais);
            return isrc;
        } catch (IOException e) {
            // TODO Auto-generated catch block
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
            return Client.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static String createXMLSessionInit(int numRounds, double timeAllowed, double stepTime, ServerMaxSimple server) {
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

            // NONFLUENTS nonFluents = null;
            // if (instance._sNonFluents != null) {
            //     nonFluents = server.rddl._tmNonFluentNodes.get(instance._sNonFluents);
            // }
            StringBuilder task = new StringBuilder(new String(Files.readAllBytes(Paths.get(domainFile))));
            // if (nonFluents != null) {
            // task.append(System.getProperty("line.separator"));
            // task.append(System.getProperty("line.separator"));
            // task.append(new String(Files.readAllBytes(Paths.get(nonFluents._sFileName))));
            // }
            task.append(System.getProperty("line.separator"));
            task.append(System.getProperty("line.separator"));
            task.append(new String(Files.readAllBytes(Paths.get(instanceFile))));
            task.append(System.getProperty("line.separator"));

            // We have to send the description encoded to Base64 as "<"
            // and ">" signs are replaced in XML text by &lt; and &gt;,
            // respectively. This seems the cleanest solution, even
            // though it requires the client to decode the description.
            byte[] encodedBytes = Base64.getEncoder().encode(task.toString().getBytes());

            addOneText(dom, rootEle, TASK_DESC, new String(encodedBytes));
            addOneText(dom, rootEle, SESSION_ID, server.id + "");
            addOneText(dom, rootEle, NUM_ROUNDS, numRounds + "");
            addOneText(dom, rootEle, TIME_ALLOWED, timeAllowed + "");
            //add by yi
            addOneText(dom, rootEle, "step-time-allowed", stepTime + "");
            return Client.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static void processXMLSessionRequest(DOMParser p, recordClient c,
                                         ServerMaxSimple server) {
        try {
            p.parse(c.isrc);
            Element e = p.getDocument().getDocumentElement();
            if (e.getNodeName().equals(SESSION_REQUEST)) {
                server.requestedInstance = getTextValue(e, PROBLEM_NAME).get(0);
                c.clientName = getTextValue(e, CLIENT_NAME).get(0);
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

    static boolean processXMLRoundRequest(DOMParser p, InputSource isrc,
                                          ServerMaxSimple server) {
        try {
            p.parse(isrc);
            Element e = p.getDocument().getDocumentElement();
            if (e.getNodeName().equals(ROUND_REQUEST)) {
                if (MONITOR_EXECUTION) {
                    // System.out.println("Monitoring execution!");
                    String executePolicyString = "no";
                    ArrayList<String> exec_pol = getTextValue(e, EXECUTE_POLICY);
                    if (exec_pol != null && exec_pol.size() > 0) {
                        executePolicyString = exec_pol.get(0).trim();
                    }
                    if (executePolicyString.equals("yes")) {
                        server.executePolicy = true;
                    } else {
                        assert (executePolicyString.equals("no"));
                        server.executePolicy = false;
                        server.numSimulations++;
                        // System.out.println("Do not execute the policy!");
                    }
                }
                return true;
            } else if (e.getNodeName().equals(RESOURCE_REQUEST)) {
                return false;
            }
            System.out.println("Illegal message from server: " + e.getNodeName());
            System.out.println("round request or time left request expected");
            System.exit(1);
        } catch (SAXException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return false;
    }

    public static ArrayList<String> getTextValue(Element ele, String tagName) {
        ArrayList<String> returnVal = new ArrayList<String>();
//		NodeList nll = ele.getElementsByTagName("*");

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

            //System.out.println("PO: " + domain._bPartiallyObserved);
            if (!domain._bPartiallyObserved || observStore != null) {
                for (PVAR_NAME pn :
                        (domain._bPartiallyObserved
                                ? observStore.keySet()
                                : state._state.keySet())) {
                    //System.out.println(turn + " check2 Partial Observ " + pn +" : "+ domain._bPartiallyObserved);

                    // No problem to overwrite observations, only ever read from
                    if (domain._bPartiallyObserved && observStore != null)
                        state._observ.put(pn, observStore.get(pn));

                    ArrayList<ArrayList<LCONST>> gfluents = state.generateAtoms(pn);
                    for (ArrayList<LCONST> gfluent : gfluents) {
                        //for ( Map.Entry<ArrayList<LCONST>,Object> gfluent :
                        //	(domain._bPartiallyObserved
                        //			? observStore.get(pn).entrySet()
                        //					: state._state.get(pn).entrySet())) {
                        Element ofEle = dom.createElement(OBSERVED_FLUENT);
                        rootEle.appendChild(ofEle);
                        Element pName = dom.createElement(FLUENT_NAME);
                        Text pTextName = dom.createTextNode(pn.toString());
                        pName.appendChild(pTextName);
                        ofEle.appendChild(pName);
                        for (LCONST lc : gfluent) {
                            Element pArg = dom.createElement(FLUENT_ARG);
                            Text pTextArg = dom.createTextNode(lc.toSuppString()); // TODO $ <>... done$
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
                                : dom.createTextNode(value.toString()); // TODO $ <>... done$
                        // dom.createTextNode(value.toString()); // TODO $ <>
                        pValue.appendChild(pTextValue);
                        ofEle.appendChild(pValue);
                    }
                }
            } else {
                // No observations (first turn of POMDP)
                Element ofEle = dom.createElement(NULL_OBSERVATIONS);
                rootEle.appendChild(ofEle);
            }
            if (SHOW_XML) {
                printXMLNode(dom);
                System.out.println();
                System.out.flush();
            }
            return (Client.serialize(dom));

        } catch (Exception e) {
            System.out.println("FATAL SERVER EXCEPTION: " + e);
            e.printStackTrace();
            throw e;
            //System.exit(1);
            //return null;
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
            // TODO: memory left is not implemented yet
            addOneText(dom, rootEle, MEMORY_LEFT, "enough");
            return Client.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static String createXMLRoundInit(int round, int numRounds, double timeLeft,
                                     double timeAllowed) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(ROUND_INIT);
            dom.appendChild(rootEle);
            addOneText(dom, rootEle, ROUND_NUM, round + "");
            addOneText(dom, rootEle, ROUND_LEFT, (numRounds - round) + "");
            addOneText(dom, rootEle, TIME_LEFT, timeLeft + "");
            return Client.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static String createXMLRoundEnd(String requested_instance, int round, double reward,
                                    int turnsUsed, long timeLeft, String client_name, double immediateReward) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(ROUND_END);
            dom.appendChild(rootEle);
            addOneText(dom, rootEle, INSTANCE_NAME, requested_instance);
            addOneText(dom, rootEle, CLIENT_NAME, client_name + "");
            addOneText(dom, rootEle, ROUND_NUM, round + "");
            addOneText(dom, rootEle, ROUND_REWARD, reward + "");
            addOneText(dom, rootEle, TURNS_USED, turnsUsed + "");
            addOneText(dom, rootEle, TIME_LEFT, timeLeft + "");
            addOneText(dom, rootEle, IMMEDIATE_REWARD, immediateReward + "");
            return Client.serialize(dom);
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
            return Client.serialize(dom);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //                              DEBUG
    ///////////////////////////////////////////////////////////////////////

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

    // add by  yi record to file
    public static void writeFile(String content, String fileName) {

        // 在文件夹目录下新建文件
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
            // 写入内容
            osw.write(content);
            // 换行
            osw.write("\r\n");
            System.out.println("write success");
        } catch (Exception e) {
            System.err.println("write to file error");
        } finally {
            // 关闭流
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

    public static void copyState(HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> state, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> newState, State s) {
        //clear
        for (PVAR_NAME p : newState.keySet())
            newState.get(p).clear();
//        for (HashMap<ArrayList<LCONST>, Object> pred_assign : newState.values())
//            pred_assign.clear();
        //set
        for (PVAR_NAME p : state.keySet()) {
            HashMap<ArrayList<LCONST>, Object> pred_assign = newState.get(p);
            HashMap<ArrayList<LCONST>, Object> assigned = state.get(p);
            for (ArrayList<LCONST> al : assigned.keySet()) {
                ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                if (al.get(0)._sConstValue.contains("@")) {
                    for (LCONST lc : al) {
                        lcArgs.add(new RDDL.ENUM_VAL(lc._sConstValue));
                    }
                } else {
                    for (LCONST lc : al) {
                        lcArgs.add(new RDDL.OBJECT_VAL(lc._sConstValue));
                    }
                }
                Object r = Server.getValue(p._sPVarName, assigned.get(al).toString(), s);
                pred_assign.put(lcArgs, r);
            }
        }
    }

    //input: horizon,
    //output: best action;
    public static recordClient selectAction(int h, int Horizon) {
        int selectNum = -1;
        //迭代后期选择最优；
        if (h > 0/*Horizon * 0.5*/) {
            selectNum = 0;
            for (int i = 1; i < clientList.size(); i++) {
                if (clientList.get(selectNum).reward < clientList.get(i).reward)
                    selectNum = i;
            }
//            System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^select:" + selectNum);
            return clientList.get(selectNum);
        }
        //迭代前期概率选择；
        ArrayList<recordClient> tempClient = new ArrayList<>();
        for (recordClient c : clientList) {
            tempClient.add(c);
        }
        //factor relate to horizon
        double alpha = (Horizon - h + 0.01) / Horizon;
        double base = (clientList.size() - 1) * (clientList.size() - 1) * (1 + 2 * alpha);
        double[] problist = new double[clientList.size() + 1];
        int[] rankList = new int[clientList.size()];
        problist[0] = 0;
        int sign = 0;
        while (tempClient.size() > 0) {
            int temp = 0;
            for (int i = 1; i < tempClient.size(); i++) {
                if (tempClient.get(temp).reward > tempClient.get(i).reward) {
                    temp = i;
                }
            }
            rankList[sign] = tempClient.get(temp).id;
            problist[sign + 1] = (sign + 1.0) * (sign + 1 + 2 * alpha * (clientList.size() - 1)) / base;
            tempClient.remove(temp);
            sign++;
        }
        while (selectNum < 0) {
            double rand = Math.random();
            for (int i = 0; i < clientList.size(); i++) {
                if (problist[i] <= rand && problist[i + 1] > rand) {
                    selectNum = rankList[i];
                    break;
                }
            }
        }
//        System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^select:" + selectNum);
        return clientList.get(selectNum);
    }

}

