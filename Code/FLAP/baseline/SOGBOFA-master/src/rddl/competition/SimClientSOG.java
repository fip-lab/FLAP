package rddl.competition;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.xerces.impl.dv.util.Base64;
import org.apache.xerces.parsers.DOMParser;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.DOMAIN;
import rddl.RDDL.IF_EXPR;
import rddl.RDDL.INSTANCE;
import rddl.RDDL.LCONST;
import rddl.RDDL.NONFLUENTS;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.State;
import rddl.TEState;
import rddl.parser.ParseException;
import rddl.parser.parser;
import rddl.policy.Policy;
import rddl.viz.StateViz;

/**
 * @author: Yi
 * @Date: 2021/07/09
 * 改动sogbofa作为FWP的一个client，改动消息和循环；
 **/


public class SimClientSOG {

    public static final boolean SHOW_XML = false;
    public static final boolean SHOW_MSG = false;
    public static final boolean SHOW_MEMORY_USAGE = true;
    public static final Runtime RUNTIME = Runtime.getRuntime();
    public static final int DEFAULT_RANDOM_SEED = 0;
    private static DecimalFormat _df = new DecimalFormat("0.##");

    enum XMLType {
        ROUND, TURN, ROUND_END, END_TEST, NONAME
    }

    private static RDDL rddl = new RDDL();

    static int numRounds;
    static double timeAllowed;
    static double totalTimeAllowed;
    public static String instanceName = null;
    static double stepTimeAllowed = Double.MAX_VALUE;
    static int curRound;
    double reward;
    static int roundsLeft;
    static int turnLeft;
    int id;
    public static int horizon = 0;

    //used for policy pre-processing
    //number of additional rounds to test the depth setup
    SimClientSOG() {
        numRounds = 0;
        timeAllowed = 0;
        curRound = 0;
        reward = 0;
        id = 0;
    }


    public static void Run(State state, INSTANCE instance, NONFLUENTS nonFluents, DOMAIN domain,
                           InputSource isrc, Client client, OutputStreamWriter osw, String msg, Policy policy, Policy fitPolicy, Class c, BufferedInputStream isr,
                           DOMParser p, String instanceName, String clientName, int tag) throws Exception {
        int r = 0;
        //number of rounds as sum of original rounds and testing rounds
        //todo 删除所有的round的信息，，
        //todo 注意！！！！！删除round信息后，time allowed可能会计算出错，使用steptime控制搜索的时间；
        //todo 同时删除多余的没用的语句；
        long totalRounds = Client.numRounds;
        long totalStepLeft = totalRounds * instance._nHorizon;
        long totalStepUsed = 0;
        double totalTimeUsage = 0;

        double totalTimeForInit = 0;

        double TotalTimeAllowed = client.timeAllowed;//120 * totalStepLeft * 1000;
        double emergentTime = 3.0 * totalStepLeft / (80 * 100);

        //add
        double totalmemory = 0.0;

        long start_time = System.currentTimeMillis();


        // todo 删除round
        boolean gono = true;
        int resetTag = 0;
        while (gono) {
            resetTag++;
            double t0 = System.currentTimeMillis();
            //暂时删除state初始化部分
//            if (SHOW_MEMORY_USAGE)
//                System.out.print("[ Memory usage: " +
//                        _df.format((RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1e6d) + "Mb / " +
//                        _df.format(RUNTIME.totalMemory() / 1e6d) + "Mb" +
//                        " = " + _df.format(((double) (RUNTIME.totalMemory() - RUNTIME.freeMemory()) /
//                        (double) RUNTIME.totalMemory())) + " ]\n");
//
//            totalmemory += (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1e6d;
//
//            //每个round的初始会进行状态重置，但应该不会影响状态的搜索过程；
//            state.init(domain._hmObjects, nonFluents != null ? nonFluents._hmObjects : null, instance._hmObjects,
//                    domain._hmTypes, domain._hmPVariables, domain._hmCPF,
//                    instance._alInitState, nonFluents == null ? new ArrayList<PVAR_INST_DEF>() : nonFluents._alNonFluents, instance._alNonFluents,
//                    domain._alStateConstraints, domain._alActionPreconditions, domain._alStateInvariants,
//                    domain._exprReward, instance._nNonDefActions);

            //todo 删除roundRequest
//            msg = createXMLRoundRequest(true);
            totalTimeUsage += System.currentTimeMillis() - t0;
            totalTimeForInit += System.currentTimeMillis() - t0;
            double timePerInit = totalTimeForInit / (r + 1);

//            Server.sendOneMessage(osw, msg);
//            isrc = Server.readOneMessage(isr);

            t0 = System.currentTimeMillis();
//            processXMLRoundInit(p, isrc, r + 1);

//            int h = 0;
            //System.out.println(instance._nHorizon);
            boolean round_ended_early = false;
            //todo 删除horizon
//            for (; h < instance._nHorizon; h++) {
            //first estimate the avg updating time per node
            if (SHOW_MSG) System.out.println("Reading turn message");
            totalTimeUsage += System.currentTimeMillis() - t0;
            isrc = Server.readOneMessage(isr);

            t0 = System.currentTimeMillis();
            Element e = parseMessage(p, isrc);
            if (e.getNodeName().equals(Simserver.TURN)) {
                //在指定一轮后
                if (resetTag == 2000) {
                    System.out.println("****************************initialize State");
                    state = new State();
                    instance = rddl._tmInstanceNodes.get(instanceName);
                    nonFluents = null;
                    if (instance._sNonFluents != null) {
                        nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
                    }
                    domain = rddl._tmDomainNodes.get(instance._sDomain);
                    if (nonFluents != null && !instance._sDomain.equals(nonFluents._sDomain)) {
                        try {
                            throw new Exception("Domain name of instance and fluents do not match: " +
                                    instance._sDomain + " vs. " + nonFluents._sDomain);
                        } catch (Exception ee) {
                            ee.printStackTrace();
                        }
                    }
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
                    resetTag = 0;
                }
                //todo 合适的方式处理round_ended_early
//                round_ended_early = e.getNodeName().equals(Server.ROUND_END);
//                if (round_ended_early)
//                    break;
                if (SHOW_MSG) System.out.println("Done reading turn message");

                ArrayList<PVAR_INST_DEF> obs = processXMLTurn(e, state);
                boolean ifEmeergency = false;

                //Default stepTime = 0.5s
                //calculate total time for this step
                double timeForStep = stepTimeAllowed;
//                double timeForStep = (timeAllowed / totalStepLeft) < stepTimeAllowed ? (timeAllowed / totalStepLeft) : stepTimeAllowed;
//                System.out.println("###################:::::" + timeForStep);
                System.out.print("tag：" + tag + ";;");
                if (r == 0 && tag == 0) {
                    System.out.println("******************** FirstStep ********************");
                    Policy.ifFirstStep = true;
                    double stepAllowed = instance._nHorizon * totalRounds * stepTimeAllowed;
                    timeForStep = (stepAllowed < timeAllowed ? stepAllowed : timeAllowed) * 0.02;
                } else {
                    Policy.ifFirstStep = false;
                }
                tag++;
                //calculate avg time usage
                //if the avg time usage is too long to complete the step
                //simply do random

                totalStepUsed++;

                if (SHOW_MSG) System.out.println("Done parsing turn message");
                if (obs == null) {
                    if (SHOW_MSG) System.out.println("No state/observations received.");
                    if (SHOW_XML)
                        Server.printXMLNode(p.getDocument()); // DEBUG
                } else if (domain._bPartiallyObserved) {
                    state.clearPVariables(state._observ);
                    state.setPVariables(state._observ, obs);
                } else {
                    state.clearPVariables(state._state);
                    state.setPVariables(state._state, obs);
                }
//                policy.setCurrentRound(h);
                policy.setRddlDomain(rddl);
                policy.setTimeAllowed(new Double(timeForStep).longValue());
                policy.timeAllowed = client.timeAllowed*0.6;
                policy.instanceName = instanceName;
                policy.algoName = clientName;

                //yi: use to compute state fitness
                fitPolicy.setTimeAllowed(new Double(stepTimeAllowed * 0.6).longValue());
                fitPolicy.setHorizon(horizon);

//                System.out.println("Steps left: " + totalStepLeft);
//                System.out.println("Time allowed for this step: " + timeForStep);

                ArrayList<PVAR_INST_DEF> actions = null;
                if (ifEmeergency) {
                    System.out.println("Time is not sufficient. Require: " + (totalTimeUsage / totalStepUsed)
                            + ", but only have " + timeAllowed);
                    try {
                        actions = policy.EmergencyReturn(state);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                } else {
                    actions = policy.getActions(obs == null ? null : state);
                }
                System.out.println("Actions: " + actions);

                double reward = 0.0;
                //compute next state and reward
                RandomDataGenerator rand = new RandomDataGenerator();
                rand.reSeed(System.currentTimeMillis());
                try {
                    state.checkStateActionConstraints(actions);
                } catch (Exception eee) {
                    System.out.println("CONSTRAINTS ERROR -- ACTION NOT APPLICABLE:\n" + eee);
                    break;
                }

                try {
                    state.computeNextState(actions, rand);
                } catch (Exception ee) {
                    System.out.println("COMPUTE NEXT STATE ERROR !!!! FATAL SERVER EXCEPTION:\n" + ee);
                    //ee.printStackTrace();
                    throw ee;
                }
//                        reward = RDDL.ConvertToNumber(state._reward.sample(new HashMap<LVAR,LCONST>(), state, rand)).doubleValue();
                reward = ((Number) domain._exprReward.sample(new HashMap<RDDL.LVAR, LCONST>(), state, rand)).doubleValue();
                System.out.println("reward:" + reward);
                state.advanceNextState();
                //compute state fitness;
                int searchdeep = 40 < (fitPolicy._totalHorizon - fitPolicy._horizon) ? 40 : (fitPolicy._totalHorizon - fitPolicy._horizon);

                long stareTime = new Double(stepTimeAllowed * 0.4).longValue();
                Double stateFitness = fitPolicy.getStateFitness(state, (long) stareTime, searchdeep);


                System.out.println("^^^^^^^^^^^^^^stare time" + stareTime);
                System.out.println("%%%%%%%%%%%%state fitness =" + stateFitness);
                msg = createXMLPlan(state, domain, state._observ, reward, actions, stateFitness);
//                    System.out.println("Sending: " + msg);
                totalTimeUsage += System.currentTimeMillis() - t0;
                Server.sendOneMessage(osw, msg);
                //todo 忽略steptime的计算过程，每一步的时间恒定；
                totalStepLeft--;
//            }
//            if (h < instance._nHorizon) {
//                break;
//            }
//                if (!round_ended_early) // otherwise isrc is the round-end message
//                    isrc = Server.readOneMessage(isr);
//                Element round_end_msg = parseMessage(p, isrc);
//                double reward = processXMLRoundEnd(round_end_msg);
//
//                policy.roundEnd(reward);


                //System.out.println("Round reward: " + reward);
//                if (getTimeLeft(round_end_msg) <= 0l)
//                    break;
            } else {
                //跳出循环；
                //接收结束信号，输出相关信息；
                //// TODO: 2021/6/14 为什么使用 Simserver.INSTANCE_END，而不是ROUND_END；
                if (e.getNodeName().equals(Simserver.INSTANCE_END)) {
                    double total_reward = processXMLSessionEnd(p, isrc);
                    policy.sessionEnd(total_reward);
                    gono = false;
                }
                break;
            }
            //todo 注意循环结束的条件；
        }
//        Records visRec = new Records();
//        double res = 0;
//        if (policy._visCounter.randomTime == 0)
//            res = 0.0;
//        else
//            res = policy._visCounter.randomInTotal / policy._visCounter.randomTime;
//        double res2 = 0;
//        double res3 = 0;
//        double res5 = 0.0;
//        if (policy._visCounter.updateTime == 0)
//            res2 = 0.0;
//        else
//            res2 = policy._visCounter.updatesInTotal / policy._visCounter.updateTime;
//        if (policy._visCounter.SeenTime == 0)
//            res3 = 0.0;
//        else
//            res3 = policy._visCounter.SeenInTotal / policy._visCounter.SeenTime;
//        double res4 = 0;
//        if (policy._visCounter.depthTime == 0)
//            res4 = 0.0;
//        else
//            res4 = policy._visCounter.depthInTotal / policy._visCounter.depthTime;
//
//        res5 = policy._visCounter.sizeInTotal / policy._visCounter.depthTime;
//        visRec.fileAppend(clientName + "_" + (int) Math.round(client.timeAllowed / 1000) + "_" + instanceName + "_" + "rrCounter", String.valueOf(res));
//        visRec.fileAppend(clientName + "_" + (int) Math.round(client.timeAllowed / 1000) + "_" + instanceName + "_" + "updatesCounter", String.valueOf(res2));
//        visRec.fileAppend(clientName + "_" + (int) Math.round(client.timeAllowed / 1000) + "_" + instanceName + "_" + "seenCounter", String.valueOf(res3));
//        visRec.fileAppend(clientName + "_" + (int) Math.round(client.timeAllowed / 1000) + "_" + instanceName + "_" + "depthCounter", String.valueOf(res4));
//        visRec.fileAppend(clientName + "_" + (int) Math.round(client.timeAllowed / 1000) + "_" + instanceName + "_" + "sizeCounter", String.valueOf(res5));

//        isrc = Server.readOneMessage(isr);
//        double total_reward = processXMLSessionEnd(p, isrc);
//        policy.sessionEnd(total_reward);
//        //cord to file
//        //format::   clientName，Time cost，Average memory，Number of runs，Average reward
//        String result = "sogb" + instanceName + "," + String.valueOf(System.currentTimeMillis() - start_time)
//                + "," + String.valueOf(totalmemory / totalRounds) + "," + String.valueOf(totalRounds)
//                + "," + String.valueOf(total_reward / totalRounds);
//        //add to end of the file
//        writeFile(result, "result/result.csv");
    }

    public static String dataDir = "";

    public static void main(String[] args) {

        /** Define a host server */
        String host = "localhost";
        /** Define a port */
        int port = Server.PORT_NUMBER;
        String clientName = "SOGBOFA";

        int randomSeed = DEFAULT_RANDOM_SEED;


        StateViz stateViz;
        StringBuffer instr = new StringBuffer();

        Socket connection = null;
        try {
            try {

                // get all parameters
                //todo 改为从client接收实例名称；
//                instanceName = args[0];
                port = Integer.valueOf(args[0]);
                host = args[1];
                dataDir = new String(args[2]);
                if (args.length > 3 && args[3].equals("Original")) {
                    clientName = "SOGBOFA_Original";
                } else {
                    clientName = "L_C_SOGBOFA";
                }


                //connect to the server
                //建立连接
                InetAddress address = InetAddress.getByName(host);
                connection = new Socket(address, port);
                System.out.println("RDDL client initialized");

                BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
                OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");


                //todo 开始循环；
                while (true) {
                    //reset policy
                    Policy.resetPolicy();

                    State state;
                    INSTANCE instance;
                    NONFLUENTS nonFluents = null;
                    DOMAIN domain;
                    System.out.println("begin new session");
                    //session begin siginal;
                    int tag = 0;
//                    String instanceName = null;


                    /** Write across the socket connection and flush the buffer */
                    String msg = createXMLSessionRequest(clientName + "_"+(System.nanoTime() % 10000));
                    Server.sendOneMessage(osw, msg);
                    BufferedInputStream isr = new BufferedInputStream(connection.getInputStream());
                    /**Instantiate an InputStreamReader with the optional
                     * character encoding.
                     */
                    //InputStreamReader isr = new InputStreamReader(bis, "US-ASCII");
                    DOMParser p = new DOMParser();

                    /**Read the socket's InputStream and append to a StringBuffer */
                    InputSource isrc = Server.readOneMessage(isr);
                    //输入实例名称
                    Client client = processXMLSessionInit(p, isrc);
                    System.out.println("get instance name :" + instanceName);

//                    System.out.println(client.id + ":" + client.numRounds);
//                    System.out.println("Total time allowed: " + client.timeAllowed);

                    VisCounter visCounter = new VisCounter();

                    // Cannot assume always in rddl.policy
                    Class c = Class.forName("rddl.policy." + clientName);
                    Class fitCl = Class.forName("rddl.policy.DBNSearch");

                    //prepare for planning
                    state = new State();

                    // Note: following constructor approach suggested by Alan Olsen
                    Policy policy = (Policy) c.getConstructor(
                            new Class[]{String.class}).newInstance(new Object[]{instanceName});
                    //policy.setRDDL(rddl);


                    policy.setRandSeed(randomSeed);
                    policy.setVisCounter(visCounter);

                    //yi: use to compute state fitness fitPolicy
                    Policy fitPolicy = (Policy) fitCl.getConstructor(
                            new Class[]{String.class}).newInstance(new Object[]{instanceName});
                    fitPolicy.setRDDL(rddl);
                    fitPolicy.setRandSeed(randomSeed);
                    fitPolicy.setTimeAllowed(new Double(stepTimeAllowed).longValue());

                    instance = rddl._tmInstanceNodes.get(instanceName);

                    if (instance._sNonFluents != null) {
                        nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
                    }

                    domain = rddl._tmDomainNodes.get(instance._sDomain);
                    if (nonFluents != null && !instance._sDomain.equals(nonFluents._sDomain)) {
                        System.err.println("Domain name of instance and fluents do not match: " +
                                instance._sDomain + " vs. " + nonFluents._sDomain);
                        System.exit(1);
                    }

                    state.init(domain._hmObjects, nonFluents != null ? nonFluents._hmObjects : null, instance._hmObjects,
                            domain._hmTypes, domain._hmPVariables, domain._hmCPF,
                            instance._alInitState, nonFluents == null ? new ArrayList<PVAR_INST_DEF>() : nonFluents._alNonFluents, instance._alNonFluents,
                            domain._alStateConstraints, domain._alActionPreconditions, domain._alStateInvariants,
                            domain._exprReward, instance._nNonDefActions);

                    // If necessary, correct the partially observed flag since this flag determines what content will be seen by the Client
                    if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
                            || (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
                        boolean observations_present = (state._alObservNames.size() > 0);
                        System.err.println("WARNING: Domain '" + domain._sDomainName
                                + "' partially observed (PO) flag and presence of observations mismatched.\nSetting PO flag = " + observations_present + ".");
                        domain._bPartiallyObserved = observations_present;
                    }

                    // Not strictly enforcing flags anymore...
                    //if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
                    //		|| (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
                    //	System.err.println("Domain '" + domain._sDomainName + "' partially observed flag and presence of observations mismatched.");
                    //}
                    policy.horizon = instance._nHorizon;
                    policy.setInstance(instanceName);
                    policy.setVisCounter(visCounter);
                    Policy.ifFirstStep = true;
                    Policy.timeUsedForCal = 0;
                    Policy.updatesIntotal = 0;
                    Policy.numberNodesUpdates = 0;
                    Policy.randomAction = new ArrayList<>();
                    Policy.ifConstructConstraints = true;
                    //yi: use to compute state fitness
                    fitPolicy.initialTree(state);
                    fitPolicy.setTotalHorizon(instance._nHorizon);
                    fitPolicy.setSearchPara(4, 3);

                    Run(state, instance, nonFluents, domain, isrc, client, osw, msg, policy, fitPolicy, c, isr, p, instanceName, clientName, tag);
                }
            } finally {
                /** Close the socket connection. */
                connection.close();
                System.out.println(instr);
            }
        } catch (Exception g) {
            System.out.println("Exception: " + g);
            g.printStackTrace();
        }
    }

    static Element parseMessage(DOMParser p, InputSource isrc) throws RDDLXMLException {
        try {
            p.parse(isrc);
        } catch (SAXException e1) {
            e1.printStackTrace();
            // Debug info to explain parse error
            //Server.showInputSource(isrc);
            throw new RDDLXMLException("sax exception");
        } catch (IOException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("io exception");
        }
        if (SHOW_XML)
            Server.printXMLNode(p.getDocument()); // DEBUG

        return p.getDocument().getDocumentElement();
    }

    static String serialize(Document dom) {
        OutputFormat format = new OutputFormat(dom);
//		format.setIndenting(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        XMLSerializer xmls = new XMLSerializer(baos, format);
        try {
            xmls.serialize(dom);
            return baos.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static XMLType getXMLType(DOMParser p, InputSource isrc) {
        Element e = p.getDocument().getDocumentElement();
        if (e.getNodeName().equals("turn")) {
            return XMLType.TURN;
        } else if (e.getNodeName().equals("round")) {
            return XMLType.ROUND;
        } else if (e.getNodeName().equals("round-end")) {
            return XMLType.ROUND_END;
        } else if (e.getNodeName().equals("end-test")) {
            return XMLType.END_TEST;
        } else {
            return XMLType.NONAME;
        }
    }

    static String createXMLSessionRequest(String clientName) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            //get an instance of builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            //create an instance of DOM
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(Server.SESSION_REQUEST);
            dom.appendChild(rootEle);
            Server.addOneText(dom, rootEle, Server.CLIENT_NAME, clientName);
//            Server.addOneText(dom, rootEle, Server.PROBLEM_NAME, problemName);
            Server.addOneText(dom, rootEle, Server.INPUT_LANGUAGE, "rddl");
            return serialize(dom);
        } catch (Exception e) {
            return null;
        }
    }

    static String createXMLRoundRequest(boolean ifExcutePolicy) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(Server.ROUND_REQUEST);
            dom.appendChild(rootEle);
            Server.addOneText(dom, rootEle, Server.EXECUTE_POLICY, ifExcutePolicy ? "yes" : "no");
            return serialize(dom);
        } catch (Exception e) {
            return null;
        }
    }

    static Client processXMLSessionInit(DOMParser p, InputSource isrc) throws RDDLXMLException {
        try {
            p.parse(isrc);
        } catch (SAXException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("sax exception");
        } catch (IOException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("io exception");
        }
        Client c = new Client();
        Element e = p.getDocument().getDocumentElement();
        byte[] rddlDesc = null;

        if (!e.getNodeName().equals(Server.SESSION_INIT)) {
            throw new RDDLXMLException("not session init");
        }
        ArrayList<String> r = Server.getTextValue(e, Server.SESSION_ID);
        if (r != null) {
            c.id = Integer.valueOf(r.get(0));
        }
        r = Server.getTextValue(e, Server.NUM_ROUNDS);
        if (r != null) {
            c.numRounds = Integer.valueOf(r.get(0));
        }
        r = Server.getTextValue(e, Server.TIME_ALLOWED);
        if (r != null) {
            c.timeAllowed = Double.valueOf(r.get(0));
        }
        r = Server.getTextValue(e, Server.TASK_DESC);
        if (r != null) {
            rddlDesc = Base64.decode(r.get(0));
        }
        // add by yi
        r = Server.getTextValue(e, "step-time-allowed");
        if (r != null) {
            if (r.size() > 0)
                stepTimeAllowed = Double.valueOf(r.get(0));
        }
        r = Simserver.getTextValue(e, Simserver.PROBLEM_NAME);
        if (r != null) {
            instanceName = String.valueOf(r.get(0));
            System.out.println("get instance name :" + instanceName);
        }

        Records rec = new Records();
//        File file = new File(System.getProperty("user.dir") + "/run-data" + System.getProperties().getProperty("file.separator") + instanceName + ".rddl");
//        if (!judeFileExists(file))
        rec.fileAppend(instanceName + ".rddl", new String(rddlDesc), true, dataDir);

        //read the file
        MyPath myPath = new MyPath();
        String absPath = System.getProperty("user.dir") + "/run-data" + "/" + dataDir + System.getProperties().getProperty("file.separator") + instanceName + ".rddl";
        try {
            rddl = new RDDL(absPath);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
//        File file = new File(System.getProperty("user.dir") + "/run-data"+ "/"+dataDir + System.getProperties().getProperty("file.separator") + instanceName + ".rddl");
//        if (file.exists()) {
//            file.delete();
//            System.out.println("delete temporary file successfully");
//        }

        return c;
    }

    static String createXMLAction(ArrayList<PVAR_INST_DEF> ds) {
        //static String createXMLAction(State state, Policy policy) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element actions = dom.createElement(Server.ACTIONS);
            dom.appendChild(actions);
            for (PVAR_INST_DEF d : ds) {
                Element action = dom.createElement(Server.ACTION);
                actions.appendChild(action);
                Element name = dom.createElement(Server.ACTION_NAME);
                action.appendChild(name);
                Text textName = dom.createTextNode(d._sPredName.toString());
                name.appendChild(textName);
                for (RDDL.LCONST lc : d._alTerms) {
                    Element arg = dom.createElement(Server.ACTION_ARG);
                    Text textArg = dom.createTextNode(lc.toSuppString());
                    arg.appendChild(textArg);
                    action.appendChild(arg);
                }
                Element value = dom.createElement(Server.ACTION_VALUE);
                Text textValue = d._oValue instanceof RDDL.LCONST
                        ? dom.createTextNode(((RDDL.LCONST) d._oValue).toSuppString())
                        : dom.createTextNode(d._oValue.toString());
                value.appendChild(textValue);
                action.appendChild(value);
            }
            // Sungwook: a noop is just an all-default action, not a special
            // action.  -Scott
            //if ( ds.size() == 0) {
            //	Element noop = dom.createElement(Server.NOOP);
            //	actions.appendChild(noop);
            //}

            if (SHOW_XML) {
                Server.printXMLNode(dom);
                System.out.println();
                System.out.flush();
            }

            return serialize(dom);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    static int getANumber(DOMParser p, InputSource isrc,
                          String parentName, String name) {
        Element e = p.getDocument().getDocumentElement();
        if (e.getNodeName().equals(parentName)) {
            String turnnum = Server.getTextValue(e, name).get(0);
            return Integer.valueOf(turnnum);
        }
        return -1;
    }

    static long processXMLRoundInit(DOMParser p, InputSource isrc,
                                    int curRound) throws RDDLXMLException {
        try {
            p.parse(isrc);
        } catch (SAXException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("sax exception");
        } catch (IOException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("io exception");
        }
        Element e = p.getDocument().getDocumentElement();
        if (!e.getNodeName().equals(Server.ROUND_INIT)) {
            return -1;
        }
        ArrayList<String> r = Server.getTextValue(e, Server.ROUND_NUM);
        if (r == null || curRound != Integer.valueOf(r.get(0))) {
            return -1;
        }
        r = Server.getTextValue(e, Server.TIME_LEFT);
        if (r == null) {
            return -1;
        }
        r = Server.getTextValue(e, Server.ROUND_LEFT);
        if (r == null) {
            return -1;
        }

        return 0;
    }

    static long getTimeLeft(Element e) {
        ArrayList<String> r = Server.getTextValue(e, Server.TIME_LEFT);
        if (r == null) {
            return -1;
        }
        return Long.valueOf(r.get(0));
    }

    static ArrayList<PVAR_INST_DEF> processXMLTurn(Element e,
                                                   State state) throws RDDLXMLException {

        if (e.getNodeName().equals(Server.TURN)) {

            timeAllowed = Double.valueOf(Server.getTextValue(e, Server.TIME_LEFT).get(0));

            // We need to be able to distinguish no observations from
            // all default observations.  -Scott
            if (e.getElementsByTagName(Server.NULL_OBSERVATIONS).getLength() > 0) {
                return null;
            }
            {
                ArrayList<String> r = Simserver.getTextValue(e, Simserver.TURN_NUM);
                if (r != null) {
                    horizon = Integer.valueOf(r.get(0));
                }
            }
            // FYI: I think nl is never null.  -Scott
            NodeList nl = e.getElementsByTagName(Server.OBSERVED_FLUENT);
            if (nl != null && nl.getLength() > 0) {
                ArrayList<PVAR_INST_DEF> ds = new ArrayList<PVAR_INST_DEF>();
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    String name = Server.getTextValue(el, Server.FLUENT_NAME).get(0);
                    ArrayList<String> args = Server.getTextValue(el, Server.FLUENT_ARG);
                    ArrayList<RDDL.LCONST> lcArgs = new ArrayList<RDDL.LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String value = Server.getTextValue(el, Server.FLUENT_VALUE).get(0);
                    Object r = Server.getValue(name, value, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, r, lcArgs);
                    ds.add(d);
                }
                return ds;
            } else
                return new ArrayList<PVAR_INST_DEF>();
        }
        throw new RDDLXMLException("Client.processXMLTurn: Should not reach this point");
        //return null;
    }

    static double processXMLRoundEnd(Element e) throws RDDLXMLException {
        if (e.getNodeName().equals(Server.ROUND_END)) {
            ArrayList<String> text = Server.getTextValue(e, Server.ROUND_REWARD);
            if (text == null) {
                return -1;
            }
            return Double.valueOf(text.get(0));
        }
        return -1;
    }

    static double processXMLSessionEnd(DOMParser p, InputSource isrc) throws RDDLXMLException {
        try {
            p.parse(isrc);
        } catch (SAXException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("sax exception");
        } catch (IOException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("io exception");
        }
        Element e = p.getDocument().getDocumentElement();
        if (e.getNodeName().equals(Server.SESSION_END)) {
            ArrayList<String> text = Server.getTextValue(e, Server.TOTAL_REWARD);
            if (text == null) {
                return -1;
            }
            return Double.valueOf(text.get(0));
        }
        return -1;
    }

    //判断文件是否存在；
    public static boolean judeFileExists(File file) {
        if (file.exists()) {
            System.out.println("file exists");
            return true;
        } else {
            System.out.println("file not exists");
            return false;
        }
    }

    // record to file
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

    static String createXMLPlan(State state, DOMAIN domain, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                                double immediateReward, ArrayList<PVAR_INST_DEF> ds, Double stateFitness) throws Exception {
        try {
            //初始化
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();

            //添加节点
            //标记为规划解；
            Element plan = dom.createElement("plan");
            dom.appendChild(plan);
            //action
            for (PVAR_INST_DEF d : ds) {
                Element action = dom.createElement(Simserver.ACTION);
                plan.appendChild(action);
                Element name = dom.createElement(Simserver.ACTION_NAME);
                action.appendChild(name);
                Text textName = dom.createTextNode(d._sPredName.toString());
                name.appendChild(textName);
                for (LCONST lc : d._alTerms) {
                    Element arg = dom.createElement(Simserver.ACTION_ARG);
                    Text textArg = dom.createTextNode(lc.toSuppString());
                    arg.appendChild(textArg);
                    action.appendChild(arg);
                }
                Element value = dom.createElement(Simserver.ACTION_VALUE);
                Text textValue = d._oValue instanceof LCONST
                        ? dom.createTextNode(((LCONST) d._oValue).toSuppString())
                        : dom.createTextNode(d._oValue.toString());
                value.appendChild(textValue);
                action.appendChild(value);
            }
            // Sungwook: a noop is just an all-default action, not a special
            // action.  -Scott
            //if ( ds.size() == 0) {
            //	Element noop = dom.createElement(Server.NOOP);
            //	actions.appendChild(noop);
            //}


            //state
//            Element turnNum = dom.createElement(Simserver.TURN_NUM);
//            Text textTurnNum = dom.createTextNode(turn + "");
//            turnNum.appendChild(textTurnNum);
//            rootEle.appendChild(turnNum);
//            Element timeElem = dom.createElement(Simserver.TIME_LEFT);
//            Text textTimeElem = dom.createTextNode(timeLeft + "");
//            timeElem.appendChild(textTimeElem);
//            rootEle.appendChild(timeElem);
            Element immediateRewardElem = dom.createElement(Simserver.IMMEDIATE_REWARD);
            Text textImmediateRewardElem = dom.createTextNode(immediateReward + "");
            immediateRewardElem.appendChild(textImmediateRewardElem);
            plan.appendChild(immediateRewardElem);

            Element fitnessElem = dom.createElement(Simserver.STATE_FITNESS);
            Text textFitnessElem = dom.createTextNode(stateFitness + "");
            fitnessElem.appendChild(textFitnessElem);
            plan.appendChild(fitnessElem);

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
                        Element ofEle = dom.createElement(Simserver.OBSERVED_FLUENT);
                        plan.appendChild(ofEle);
                        Element pName = dom.createElement(Simserver.FLUENT_NAME);
                        Text pTextName = dom.createTextNode(pn.toString());
                        pName.appendChild(pTextName);
                        ofEle.appendChild(pName);
                        for (LCONST lc : gfluent) {
                            Element pArg = dom.createElement(Simserver.FLUENT_ARG);
                            Text pTextArg = dom.createTextNode(lc.toSuppString());
                            pArg.appendChild(pTextArg);
                            ofEle.appendChild(pArg);
                        }
                        Element pValue = dom.createElement(Simserver.FLUENT_VALUE);
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
                Element ofEle = dom.createElement(Simserver.NULL_OBSERVATIONS);
                plan.appendChild(ofEle);
            }
            if (SHOW_XML) {
                Simserver.printXMLNode(dom);
                System.out.println();
                System.out.flush();
            }
            return serialize(dom);

        } catch (ParserConfigurationException e) {
            System.out.println("createXMLState ERROR!: " + e);
            e.printStackTrace();
        }
        return null;
    }
//    public static void resetPolicy(){
//        Policy.RAND_SEED = -1;
//
//        Policy._random = new RandomDataGenerator();
//        Policy._sInstanceName = null;
//        Policy._rddl = null;
//        Policy._timeAllowed = 0;
//        Policy.currentRound = 0;
//        Policy._visCounter = null;
//        Policy. horizon = 0;
//
//        //conformant related
//        Policy. expectMaxVarDepth = 10;
//        Policy.maxVarDepth = 10;
//        Policy.theRatio = -1;
//        Policy. ifConformant = true;
//
//        Policy. searchDepth = -1;
//        Policy. avgVarDepth = 0;
//        Policy. avgUpdates = 0;
//        Policy.avgSearchDepth = 0;
//        Policy.effectiveSteps = 0;
//        Policy.tmpUpdatesChange = 0;
//        Policy.tmpVarDepthChange = 0;
//        Policy.tmpSearchDepthChange = 0;
//
//        Policy.theDepth = 0;
//        Policy.ifReallyRun = false;
//        Policy.ifUseRatio = false;
//        Policy.ifUseFix = false;
//
//        Policy. numberNodesUpdates = 0;
//        Policy. timeUsedForCal = 0;
//        Policy.ifFirstStep = false;
//        Policy. updatesIntotal = 0;
//        Policy. nodesIntotal = 0;
//        Policy. algoName = null;
//        Policy. instanceName = null;
//        Policy. timeAllowed = 0;
//
//        Policy.timeHis = new LinkedList<>();
//        Policy.nodesupdateHis = new LinkedList<>();
//        Policy.gradientCost = 0;
//        Policy.fndalhpaCost = 0;
//        //constraints for projection
//        // built in buildF, used in projection
//        Policy.sumVars = new ArrayList<>();
//        Policy.sumCoeffecients = new ArrayList<>();
//        Policy.sumLimits = new ArrayList<>();
//        Policy.sumLimitsExpr = new ArrayList<>();
//        Policy.ifInSumConstraints = null;
//        Policy.ifEqual = new ArrayList<>();
//        Policy.ifConstructConstraints = true;
//        // const for random policy
//        Policy.randomAction = new ArrayList<>();
//        Policy. countActBits = 0;
//
//        Policy. ifInGraph = null;
//        Policy.ifForcednotChoose = null;
//        //minimal probability of action vars
//        //caused by XXXX=>move() preconditions
//        //used in projection
//        Policy.minimalProb = new ArrayList<>();
//
//        //for constraints
//        //used for adding additional effects to
//        Policy._extraEffects = new HashMap<>();
//        Policy._extraEffectsLVARS = new HashMap<>();
//        //static public HashMap<PVAR_NAME, HashMap<ArrayList<TYPE_NAME>, ArrayList<BOOL_EXPR>>> _forcingConditions = new HashMap<>();
//
//        Policy._forceActionCondForall = new HashMap<>();
//        Policy._forcedCondForallLVARS = new HashMap<>();
//
//        Policy. _forceActionCondExist = new HashMap<>();
//        Policy._forcedCondExistLVARS = new HashMap<>();
//
//        Policy.LVARRecord = new HashMap<>();
//        Policy.TYPERecord = new HashMap<>();
//        Policy.superClass = new HashMap<>();
//        Policy.childClass = new HashMap<>();
//
//        //this mode is used to groups several nodes together into a sum node
//        //guarentee the sum is flattened
//        //used for a+b=C==k only
//        Policy.groupMode = false;
//
//        Policy.ifPrintSizePredict = false;
//
//        Policy.act2Int = new HashMap<>();
//
//        //record each step state in the graph
//        //used for retreiving satate variable value
//        //when doing projection for XXXXX=>move()
//        Policy.stateHistory = new ArrayList<>();
//    }
}

