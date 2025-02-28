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

    public static final String STATE_FITNESS = "state-fitness";

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


    //private Socket connection;
    private RDDL rddl = null;
    private static int ID = 0;
    private static int DEFAULT_NUM_ROUNDS = 2;
    private static long DEFAULT_TIME_ALLOWED = 1080000; // milliseconds = 18 minutes
    private static boolean USE_TIMEOUT = true;
    private static boolean INDIVIDUAL_SESSION = false;
    private static String LOG_FILE = "rddl";
    private static boolean MONITOR_EXECUTION = false;
    private static String SERVER_FILES_DIR = "";
    private static String CLIENT_FILES_DIR = "";

    public int port;
    public int id;
    public String clientName = null;
    public static String requestedInstance = null;
    public RandomDataGenerator rand;
    public boolean executePolicy = true;
    public String inputLanguage = "rddl";
    public int numSimulations = 0;

    public static State state;
    public static INSTANCE instance;
    public static NONFLUENTS nonFluents;
    public static DOMAIN domain;
    public static int randomSeed = 0;
    public StateViz stateViz;

    /**
     * @Author Yi
     * 增加相关的数据结构；
     **/
    public static int clientNum = 2;
    public static int populationNum = 4;
    //用于存储client链接
    private static ArrayList<recordClient> clientList = new ArrayList<>();
    //存储实例名称
    public static ArrayList<String> instNameList = new ArrayList<>();

    //任务分配
    public static class mission {
        public int popID;
        public int deep;
    }

    //记录每个client的信息
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

    //记录探索的个体
    public static class individual {
        //id有什么用；
        public int id = 0;
        public int father = 0;
        //只记录当前步使用的policy，同时也可以作为执行的标记；最大表示不存在最大值；
        public int clientId = Integer.MAX_VALUE;
        public int horizon = 0;
        public State state = null;
        public PPDDL.Domain domain = null;
        public ArrayList<PVAR_INST_DEF> actionList = null;
        public Double reward = 0.0;
    }

    //记录个体的相关参数，不同的算法需要记录的参数也不同；
    public static class individualPara {
        //表示对应个体的编号，一般使用数组小标表示；
        public int id = 0;
        //用于选择下一个迭代种群，1表示一定被选择；值的大小跟适应度的排名有关；
        public int selectRandom = 0;
        //记录局部搜索概率；
        public double prob = 0.0;
        //用于传输随机数种子
        public RandomDataGenerator rand = new RandomDataGenerator();
        //记录适应度函数
        public double fitness = 0.0;
        //动作的位置，用于对动作的位置关系的判断;初步为随机的位置，如果人为优化位置编码，则会得到更优解；
        public ArrayList<Integer> location = new ArrayList<>();
        //标记爆炸半径（搜索深度）
        public int deep;
        //标记产生的spark数目
        public int numSpark = 3;

    }

    //用于记录state的树形结构；
    public static class recordState {
        //
        public int id = 0;
        public int father = 0;
        public int horizon = 0;
        //用于标记是否是通过deep计算得到的state
        public int deepMark = 0;
        //此处的State可以换为s._state,更加节约空间；
        //HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> _state
        public State state = null;
        public ArrayList<PVAR_INST_DEF> actionList = new ArrayList<>();

        public Double accum_reward = 0.0;
    }


    /**
     * @param args 1. rddl description file name (can be directory), in RDDL format, with complete path
     *             2. (optional) port number
     *             3. (optional) random seed
     */
    public static void main(String[] args) {

        // StateViz state_viz = new GenericScreenDisplay(true);
        StateViz state_viz = new NullScreenDisplay(false);
        //String instanceName = null;


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
            CLIENT_FILES_DIR = new String(args[0]);

            File[] subDirs = new File(args[0]).listFiles(File::isDirectory);
            // Check if there are subdirectories called "client" and "server"
            for (File subDir : subDirs) {
                if (subDir.getName().equals("server")) {
                    SERVER_FILES_DIR = new String(subDir.getPath());
                } else if (subDir.getName().equals("client")) {
                    CLIENT_FILES_DIR = new String(subDir.getPath());
                }
            }

            RDDL rddl = new RDDL(SERVER_FILES_DIR);

            //单独处理实例，并处理好参数信息；
//            if (args.length > 1) {
//                instanceName = args[1];
//            }
//            if (!rddl._tmInstanceNodes.containsKey(instanceName)) {
//                System.out.println("Instance name '" + instanceName + "' not found in " + args[0] + "\nPossible choices: " + rddl._tmInstanceNodes.keySet());
//                System.exit(1);
//            }
            if (args.length > 1) {
                port = Integer.valueOf(args[2]);
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
                if (args[4].equals("1")) {
                    INDIVIDUAL_SESSION = true;
                }
            }
            if (args.length > 5) {
                if (args[5].equals("0")) {
                    USE_TIMEOUT = false;
                } else {
                    USE_TIMEOUT = true;
                    DEFAULT_TIME_ALLOWED = Integer.valueOf(args[5]) * 1000;
                }
            }
            if (args.length > 6) {
                LOG_FILE = args[6] + "/logs";
            }
            if (args.length > 7) {
                assert (args[7].equals("0") || args[7].equals("1"));
                if (args[7].equals("1")) {
                    MONITOR_EXECUTION = true;
                }
            }
            if (args.length > 8) {
                state_viz = (StateViz) Class.forName(args[8]).newInstance();
            }


            System.out.println("RDDL Server Initialized");

            //server可以持续执行不同的client，可以持续执行多个instance；
            //初始化数据，注意清除之前迭代留下的残留数据;
            clientList.clear();

            //和client建立连接；
            for (int i = 0; i < clientNum; i++) {
                Socket connection = socket1.accept();
                RandomDataGenerator rdg = new RandomDataGenerator();
                rdg.reSeed(rand_seed + ID); // Ensures predictable but different seed on every session if a single client connects and all session requests run in same order
                //用于存储socket连接信息；
                recordClient re = new recordClient();
                re.socket = connection;
                clientList.add(re);
            }
            //todo 可以通过文件输入实例名称
            instNameList.add("manufacturer_inst_mdp__10");
            instNameList.add("manufacturer_inst_mdp__09");
            Simserver s = new Simserver(++ID, rddl, state_viz, port, new RandomDataGenerator());
            s.runClient();


            System.out.println("Single client has connected, no more are accepted.");
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    Simserver(int i, RDDL rddl, StateViz state_viz, int port, RandomDataGenerator rgen) {
//        this.connection = s;
        this.id = i;
        this.rddl = rddl;
        this.stateViz = state_viz;
        this.port = port;
        this.rand = rgen;
    }


    //局部搜索
    public void exploitation() {
    }

    //全局搜索
    public void exploration() {
    }


    //种群的选择，可以选择不同的选择策略；可以根据个体之间的距离计算概率
    public void selectSwarm(ArrayList<individual> swarm, ArrayList<individual> pop, int h, int size) {
        //选择最优的一个，剩下的轮盘赌选择，概率由个体的排名计算出来；同时和当前迭代次数相关
        // 而不是适应度，避免单个个体适应度过大而被选概率过大
        if (swarm.size() < populationNum) {
            System.err.println("numspark too small");
            return;
        }
        System.out.print("select next generation：");
        //清除当前的种群，用选择下一代种群，
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
        //概率的权重因子，在开始每个个体被选的概率几乎相同，迭代次数越大，概率差异越大，
        double a = (instance._nHorizon - h + 0.01) / instance._nHorizon;
        //不同的选择策略影响不是很大，影响小于随机误差；
//        double a = 1/(h+1);
//        double a = (instance._nHorizon - h + 0.01) / instance._nHorizon;
        double base = (n - 1) * (n - 1) * (1 + 2 * a);
        while (swarm.size() > 0) {
            int temp = 0;
            for (int i = 1; i < swarm.size(); i++) {
                if (swarm.get(temp).reward > swarm.get(i).reward) {
                    temp = i;
                }
            }
            //记录排名
            rankList[sign] = swarm.get(temp).id;
            //记录概率累加
            if (sign < n - 1)
                probList[sign + 1] = (sign + 1.0) * (sign + 1 + 2 * a * (n - 1)) / base;
//            probList[sign + 1] = (sign + 1) * (sign + 2) / n/(n-1);
            sign++;
            swarm.remove(temp);
        }
        while (pop.size() < populationNum) {
            double rand = Math.random();
            for (int i = 0; i < swarmTemp.size(); i++) {
                //在区间内且没有被标记已选,或reward最大的
                if ((probList[i] <= rand && probList[i + 1] > rand) ||
                        (i == swarmTemp.size() - 1)) {
                    if (rankList[i] < Integer.MAX_VALUE) {
                        int temp = rankList[i];
                        swarmTemp.get(temp).id = pop.size() + size;
                        swarmTemp.get(temp).horizon = h + 1;
                        pop.add(swarmTemp.get(temp));
                        System.out.print(temp + ",");
                        //选择标记
                        rankList[i] = Integer.MAX_VALUE;
                        break;
                    }
                }
            }
        }
        System.out.println();
        swarm.clear();

    }

    //获取最优个体
    public int getOptimal(ArrayList<individual> population) {
        int optimal = 0;
        for (int i = 0; i < population.size(); i++) {
            if (population.get(i).reward > population.get(optimal).reward)
                optimal = i;
        }
        return optimal;
    }

    //计算相关参数，搜索的深度和广度（深度表示回溯搜索的步长，广度表示搜索的个数）；
    //回溯的步长越长表示范围越大，越接近全局搜索；
    //todo 参数可以根据deep动态调节
    public ArrayList<individualPara> computePara(ArrayList<individual> pop, int h) {
//        需要计算的参数如下id\selectRandom\rand\fitness\location\amplitude\numSpark
        //初始化，将种群和参数列表一一对应；
        ArrayList<individualPara> pp = new ArrayList<>();
        for (individual i : pop) {
            individualPara ip = new individualPara();
            ip.id = i.id;
            pp.add(ip);
        }

        int factorSp = 7;
        double fMax = Double.MIN_VALUE;
        double sumFit = 0.0;
        //边界常数a<b<1；控制
        double a = 0.3;//min
        double b = 0.8;//max
        int factorDe = 1;
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
            //计算产生的火花数；
            double temp = ((factorDe * (pop.get(i).reward - fMin) + Double.MIN_VALUE) / (sumFun + Double.MIN_VALUE));
            //上下界限制；
            if (temp < a * factorSp)
                pp.get(i).numSpark = (int) (a * factorSp);//(int)去掉小数，保留整数部分
            else if (temp > b * factorSp)
                pp.get(i).numSpark = (int) (b * factorSp);
                //***注意小数和整数的转化方式
            else pp.get(i).numSpark = (int) (temp);
            //计算爆炸半径，可以类比为搜索的深度,搜索的深度也是和迭代次数相关
            int temp_d = (int) ((factorSp * (fMax - pop.get(i).reward) + Double.MIN_VALUE) / (sumFit + Double.MIN_VALUE));
            //边界限制，不能超过当前的迭代步数；
            if (temp_d >= h)
                temp_d = h;
            //todo 暂时将深度固定为3；
            pp.get(i).deep = 3;
            pp.get(i).deep = temp_d;
            //暂时局部搜索的概率固定为0.3；
            pp.get(i).prob = 0.3;
        }

        return pp;
    }

    //主要的写出
    public void crowdSourcing(ArrayList<recordState> recordStateTree, ArrayList<individual> population, ArrayList<individual> swarm,
                              DOMParser p, long timeAllowed, long start_time, double immediate_reward, double cur_discount,
                              int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore) {
        int cliNum = 0;
        ArrayList<individualPara> inPara = computePara(population, h);

        for (int j = 0; j < 3; j++) {
            for (individual is : population) {
                //getAction调用模板；如果返回值为false，则认为是动作集出错；
                //todo 执行局部搜索或者全局搜索，决定每个个体的探索方式，并进行搜索；
                //todo 通过搜索策略将对应的client，对个体进行处理后，再分配给client寻找动作；
                //todo 分配时注意每个client的资源占用情况，避免空闲，负载均衡度；
                individual i = new individual();
                //id表示在列表中的下标
                i.id = swarm.size();
                swarm.add(i);
                boolean b = getAction(p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore, i, recordStateTree.get(is.id), clientList.get(cliNum));
                //boolean b = getAction(p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore, i, is, clientList.get(is.clientId));
                cliNum++;
                if (cliNum >= clientList.size())
                    cliNum = 0;
                if (!b)
                    break;
            }
        }

    }


    public ArrayList<mission> allocate(ArrayList<recordState> recordStateTree, ArrayList<individual> population, ArrayList<individualPara> inPara) {
        //按照deep的深度排序，deep越大的排在越前面；
        ArrayList<mission> mi = new ArrayList<>();

        for (int k = 0; k < population.size(); k++) {
            for (int j = 0; j < inPara.get(k).numSpark; j++) {
                mission mis = new mission();
                if (Math.random() < inPara.get(k).prob && recordStateTree.get(recordStateTree.size() - 1).horizon > 5) {
                    //满足深度搜索概率，同时需要horizon要大于5（迭代开始时不需要搜索深度）
                    mis.popID = population.get(k).id;
                    for (int l = 1; l < inPara.get(k).deep; l++) {
                        mis.popID = recordStateTree.get(mis.popID).father;
                    }
                    //同时搜索深度的也会有随机性
                    mis.deep = (int)Math.random()*inPara.get(k).deep;
                } else {
                    //不满足深度搜索概率
                    mis.popID = population.get(k).id;
                    mis.deep = 1;
                }
                int l = 0;
                for (; l < mi.size(); l++) {
                    if (mis.deep > mi.get(l).deep)
                        break;
                }
                mi.add(l, mis);
            }
        }
        //进行变异操作：

        return mi;
    }

    //众包操作，将任务分配给client，使用多线程使得client同时进行计算；
    //同时需要注意，对计算时间进行限制，控制计算时间；
    //思考怎么并行向client发送和接受消息，同时怎么处理回溯的问题；
    public void crowdSourcingThread(ArrayList<recordState> recordStateTree, ArrayList<individual> swarm, ArrayList<individual> population,
                                    DOMParser p, long timeAllowed, long start_time, double immediate_reward, double cur_discount,
                                    int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore
    ) throws InterruptedException {

        ArrayList<individualPara> inPara = computePara(population, h);
        ArrayList<mission> missionsList = allocate(recordStateTree, population, inPara);

        // 开启线程个数和client个数相同；
        int threadNum = clientList.size();
        CountDownLatch threadSignal = new CountDownLatch(threadNum);
        // 创建固定长度的线程池,线程池大小和client的个数相关
        ExecutorService executor = Executors.newFixedThreadPool(threadNum);
        System.out.println("get action begin");

//        for (int j = 0; j < threadNum; j++) {
//            Runnable task = new actionThread(1, p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore, i, iP, c);
//            executor.execute(task);
//
//        }
        for (int j = 0; j < threadNum; j++) {

            //getAction调用模板；如果返回值为false，则认为是动作集出错；
            //todo 执行局部搜索或者全局搜索，决定每个个体的探索方式，并进行搜索；
            //todo 通过搜索策略将对应的client，对个体进行处理后，再分配给client寻找动作；
            //todo 分配时注意每个client的资源占用情况，避免空闲，负载均衡度；
            //id表示在列表中的下标
            Runnable task = new actionThread(threadSignal, missionsList, swarm, recordStateTree, p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore, clientList.get(j));
            executor.execute(task);
//                boolean b = getAction(p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore, i, is, clientList.get(cliNum));

        }

        // 等待所有子线程执行完
        threadSignal.await();
        //固定线程池执行完成后 将释放掉资源 退出主进程
        executor.shutdown();
        //退出主进程
        System.out.println("get Action end ");
    }

    private class actionThread implements Runnable {
        private DOMParser p;
        private long timeAllowed;
        private long start_time;
        private double immediate_reward;
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
                            DOMParser p, long timeAllowed, long start_time, double immediate_reward, double cur_discount,
                            int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                            recordClient c) {
            this.p = p;
            this.timeAllowed = timeAllowed;
            this.start_time = start_time;
            this.immediate_reward = immediate_reward;
            this.cur_discount = cur_discount;
            this.h = h;
            this.observStore = observStore;
            this.c = c;
            this.missionsList = missionsList;
            this.threadsSignal = threadsSignal;
//            this.population = population;
            this.swarm = swarm;
            this.recordStateTree = recordStateTree;
        }

        public void run() {
            System.out.println("get action from one client ;");

            while (missionsList.size() > 0) {
                //获取任务；
                mission mi;
                synchronized (this) {
                    if (missionsList.size() <= 0)
                        return;
                    //每次从队首获取任务，以保证deep大的任务可以首先执行；
                    mi = missionsList.get(0);
                    missionsList.remove(0);
                }
                individual i = new individual();
                //暂不处理深度问题
                boolean b = getAction(p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore, i, recordStateTree.get(mi.popID), c);
                if (!b) {
                    //getAction出错时，放弃当前任务；
                    System.err.println("getAction error!! continue ");
                    continue;
                }
                mi.deep--;
                //todo 当h超过当前的horizon时，注意处理出界错误；
                synchronized (this) {
                    //进行迭代搜索；
                    if (mi.deep > 0) {
                        //跳过swarm，直接记录到状态列表中
                        recordState re = new recordState();
                        re.state = cloneState(i.state);
                        re.father = i.father;
                        re.actionList = i.actionList;
                        //horizon 不变，连续相同的horizon表示这些相同的horizon都是由深度生成的；
                        //当前步记录的是下一次迭代步的种群或state
                        re.horizon = h + 1;
                        re.id = recordStateTree.size();
                        re.accum_reward = i.reward;
                        re.deepMark = 1;
                        recordStateTree.add(re);
                        mi.popID = re.id;
                        //将新任务加入到任务列表表头，以便优先执行；
                        missionsList.add(0, mi);
                    } else {
                        //记录找到的种群信息
                        i.id = swarm.size();
                        swarm.add(i);
                    }
                }
            }

            threadsSignal.countDown();
        }

    }

    //与client进行交互，发送状态并接受动作；
    //主要功能：发送状态并接收动作；输入：状态、client id ；输出：action list
    //immediate_reward可以删除；
    public synchronized boolean getAction(DOMParser p, long timeAllowed, long start_time, double immediate_reward, double cur_discount,
                                          int h, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                                          individual i, recordState is, recordClient c) {
        try {
            //is为上代种群，i为当前迭代的找出的swarm
            //此处的horizon可以删除，赋值是多余的；
            i.horizon = h;
            //后代指向父亲节点；
            i.father = is.id;
            //此处为上一次迭代的state，需要到下一代才可以；
            i.state = cloneState(is.state);
            if (h != 0)
                i.state.advanceNextState();

            c.msg = createXMLTurn(i.state, h + 1, domain, observStore, timeAllowed - System.currentTimeMillis() + start_time, immediate_reward);

//            线程休眠；
//            try {
//                Thread.currentThread().sleep(50);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            if (SHOW_MSG)
                System.out.println("Sending msg:\n" + c.msg);

            sendOneMessage(c.osw, c.msg);

            //获取动作；
            ArrayList<PVAR_INST_DEF> ds = null;
            while (ds == null) {
                c.isrc = readOneMessage(c.isr);
                if (c.isrc == null) {
                    throw new Exception("FATAL SERVER EXCEPTION: EMPTY CLIENT MESSAGE");
                }

                ds = processXMLAction(p, c.isrc, i.state);
                //@#
                if (ds == null) {
                    c.msg = createXMLResourceNotification(timeAllowed - System.currentTimeMillis() + start_time);
                    sendOneMessage(c.osw, c.msg);
                }
            }
            //记录动作列表；
            i.actionList = ds;

            // Check action preconditions (also checks maxNonDefActions)
            try {
                i.state.checkStateActionConstraints(ds);
            } catch (Exception e) {
                System.out.println("TRIAL ERROR -- ACTION NOT APPLICABLE:\n" + e);
                if (INDIVIDUAL_SESSION) {
                    try {
                        c.socket.close();
                    } catch (IOException ioe) {
                    }
                    System.exit(1);
                }
                return false;
            }

            //Sungwook: this is not required.  -Scott
            //if ( h== 0 && domain._bPartiallyObserved && ds.size() != 0) {
            //	System.err.println("the first action for partial observable domain should be noop");
            //}
            if (SHOW_ACTIONS && executePolicy) {
                boolean suppress_object_cast_temp = RDDL.SUPPRESS_OBJECT_CAST;
                RDDL.SUPPRESS_OBJECT_CAST = true;
                System.out.println("** horizon " + h + ",individual " + i.id + ",client name:" + c.name + ",Actions received: " + ds);
                RDDL.SUPPRESS_OBJECT_CAST = suppress_object_cast_temp;
            }

            try {
                i.state.computeNextState(ds, rand);
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

            if (domain._bPartiallyObserved)
                observStore = copyObserv(i.state._observ);

            // Calculate reward / objective and store
            immediate_reward = ((Number) domain._exprReward.sample(new HashMap<LVAR, LCONST>(),
                    i.state, rand)).doubleValue();
            i.reward += cur_discount * immediate_reward;
            //System.out.println("Accum reward: " + accum_reward + ", instance._dDiscount: " + instance._dDiscount +
            //   " / " + (cur_discount * reward) + " / " + reward);
            cur_discount *= instance._dDiscount;

            stateViz.display(i.state, h);

            return true;
        } catch (Exception e) {
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

    //记录state
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


    /**
     * @Author Yi
     * @Description 实现的智能优化算法，；
     * 输入：种群个体和client编号；
     * 输出：是将要处理的个体编号和对应的client；
     **/
    //也可以直接在原run()的基础上进行修改，
    public void runClient() {
        DOMParser p = new DOMParser();
        int numRounds = DEFAULT_NUM_ROUNDS;
        long timeAllowed = DEFAULT_TIME_ALLOWED;
        long start_time = System.currentTimeMillis();
        //可以将群优化算法的参数写在这个位置；
        int optimalIndvi;
        double totalmemory = 0.0;

        try {

            for (String instanceName : instNameList) {
                //server可以持续执行不同的client，可以持续执行多个instance；
                //初始化数据，注意清除之前迭代留下的残留数据;
                requestedInstance = instanceName;
                instance = rddl._tmInstanceNodes.get(instanceName);
                if (instance._sNonFluents != null) {
                    nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
                }
                domain = rddl._tmDomainNodes.get(instance._sDomain);
                initializeState(rddl, requestedInstance);


                //对不同的client初始化，发送当前的instance name，
                for (recordClient c : clientList) {
                    InetAddress ia = c.socket.getInetAddress();
                    c.client_hostname = ia.getCanonicalHostName();
                    c.client_IP = ia.getHostAddress();

                    System.out.println("Connection from client at address " + c.client_hostname + " / " + c.client_IP);
                    writeToLog(createClientHostMessage(c.client_hostname, c.client_IP));

                    // Begin communication protocol from PROTOCOL.txt
                    c.isr = new BufferedInputStream(c.socket.getInputStream());
                    c.isrc = readOneMessage(c.isr);
                    //get clientname
                    processXMLSessionRequest(p, c.isrc, this);
                    c.name = clientName;
                    System.out.println("Client name: " + clientName);

                    //send instance name
                    c.os = new BufferedOutputStream(c.socket.getOutputStream());
                    c.osw = new OutputStreamWriter(c.os, "US-ASCII");
                    c.msg = createXMLSessionInit(Double.valueOf(timeAllowed), this, requestedInstance);
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
                    if (!executePolicy) {
                        r--;
                    }

                    //重置初始状态，和初始种群等参数；
                    resetState();
                    //初始化群优化算法相关参数；
                    optimalIndvi = 0;
                    //迭代种群，从中选择出下一代种群N；如有个体附近的局部搜索，也可以使用；
                    //todo 也可以命名为candidate
                    ArrayList<individual> swarm = new ArrayList<>();
                    //初始化种群
                    ArrayList<individual> population = new ArrayList<>();
                    //使用树存储经过每一步的状态
                    ArrayList<recordState> recordStateTree = new ArrayList<>();
                    for (int i = 0; i < populationNum; i++) {
                        individual ind = new individual();
                        ind.id = i;
                        ind.state = cloneState(state);
                        ind.father = -1;
                        population.add(ind);
                    }
                    //记录初始种群（初始状态）
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

                    double immediate_reward = 0.0d;
                    double cur_discount = 1.0d;
                    int h = 0;
                    HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore = null;
                    while (true) {
                        //此处为对每个client循环执行一次，理论上还是每个client执行一个固定的序列；
                        //暂时无用，可以删除timer
                        Timer timer = new Timer();
                        //每个时间步开始时，清空所用存储变量；
                        swarm.clear();
                        //可以删除；
                        int cliNum = 0;
                        //ArrayList<individualPara> inPara = computePara(population, h);
                        //以下可以归纳成众包操作；
//                        crowdSourcing();
                        {
//                            顺序执行；
//                            crowdSourcing(population, swarm, p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore);
//                            多线程同步执行；
                            crowdSourcingThread(recordStateTree, swarm, population, p, timeAllowed, start_time, immediate_reward, cur_discount, h, observStore);
                        }
                        selectSwarm(swarm, population, h, recordStateTree.size());
                        //记录当前的种群信息；(记录的是当前的state、当前的action、当前的reward)
                        recState(population, recordStateTree);
                        //更新状态添加到getaction中，此处可以删除
                        //advanceState(population);

                        if (SHOW_TIMING)
                            System.out.println("**TIME to advance state: " + timer.GetTimeSoFarAndReset());
                        if (domain._bPartiallyObserved)
                            observStore = copyObserv(state._observ);
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
                    //round结束，需要选择最优的个体输出；
                    //todo 可以将最优解的每一步输出
                    optimalIndvi = getOptimal(population);
                    if (executePolicy) {
                        accum_total_reward += population.get(optimalIndvi).reward;
                        rewards.add(population.get(optimalIndvi).reward);
                        System.out.println("** Round best reward: " + population.get(optimalIndvi).reward);
                    }

                }
                //输出结束信号，结束当前循环；
                for (recordClient c : clientList) {
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
                }
                for (recordClient c : clientList)
                    System.out.println("Session finished successfully: " + c.name);
                System.out.println("Time left: " + (timeAllowed - System.currentTimeMillis() + start_time));
                System.out.println("*Time cost: " + (System.currentTimeMillis() - start_time) + "ms");
                System.out.println("*Average memory: " + totalmemory / numRounds);
                System.out.println("Number of simulations: " + numSimulations);
                System.out.println("*Number of runs: " + numRounds);
                System.out.println("Accumulated reward: " + (accum_total_reward));
                System.out.println("*Average reward: " + (accum_total_reward / numRounds));
                System.out.println("*Best reward of every round: " + rewards);


            }
            for (recordClient c : clientList) {
                c.socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\n>> TERMINATING TRIAL.");

        } finally {
//            try {
//                connection.close();
//            } catch (IOException e) {
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

    public static void initializeState(RDDL rddl, String requestedInstance) {
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


        // This allows object vals
        if (pvalue.startsWith("@")) {
            // Must be an enum
            return new ENUM_VAL(pvalue);
        } else {
            return new OBJECT_VAL(pvalue);
        }

    }

    static ArrayList<PVAR_INST_DEF> processXMLAction(DOMParser p, InputSource isrc,
                                                     State state) throws Exception {
        try {
            //showInputSource(isrc); System.exit(1);
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
                return new ArrayList<PVAR_INST_DEF>(); // FYI: May be unreachable. -Scott
            //} else {
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

    public static final int MAX_BYTES = 10485760;
    public static byte[] bytes = new byte[MAX_BYTES];

    // Synchronize because this uses a global bytes[] buffer
    public static synchronized InputSource readOneMessage(InputStream isr) {

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

    static String createXMLSessionInit(double timeAllowed, Simserver server, String problemName) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(SESSION_INIT);
            dom.appendChild(rootEle);

            INSTANCE instance = server.rddl._tmInstanceNodes.get(server.requestedInstance);
            DOMAIN domain = server.rddl._tmDomainNodes.get(instance._sDomain);

            String domainFile = CLIENT_FILES_DIR + "/" + domain._sFileName + "." + server.inputLanguage;
            String instanceFile = CLIENT_FILES_DIR + "/" + instance._sFileName + "." + server.inputLanguage;

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
            addOneText(dom, rootEle, TIME_ALLOWED, timeAllowed + "");
            addOneText(dom, rootEle, PROBLEM_NAME, problemName);
            return Client.serialize(dom);
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
                        // dom.createTextNode(value.toString());
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
            addOneText(dom, rootEle, MEMORY_LEFT, "enough");
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
}


