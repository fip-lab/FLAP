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
        long totalRounds = Client.numRounds;
        long totalStepLeft = totalRounds * instance._nHorizon;
        long totalStepUsed = 0;
        double totalTimeUsage = 0;

        double totalTimeForInit = 0;

        double TotalTimeAllowed = client.timeAllowed;
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
            totalTimeUsage += System.currentTimeMillis() - t0;
            totalTimeForInit += System.currentTimeMillis() - t0;
            double timePerInit = totalTimeForInit / (r + 1);

            t0 = System.currentTimeMillis();

            boolean round_ended_early = false;
            if (SHOW_MSG) System.out.println("Reading turn message");
            totalTimeUsage += System.currentTimeMillis() - t0;
            isrc = Server.readOneMessage(isr);

            t0 = System.currentTimeMillis();
            Element e = parseMessage(p, isrc);
            if (e.getNodeName().equals(Simserver.TURN)) {
                if (resetTag == 2000) {
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
                if (SHOW_MSG) System.out.println("Done reading turn message");

                ArrayList<PVAR_INST_DEF> obs = processXMLTurn(e, state);
                boolean ifEmeergency = false;

                double timeForStep = stepTimeAllowed;
                if (r == 0 && tag == 0) {
                    Policy.ifFirstStep = true;
                    double stepAllowed = instance._nHorizon * totalRounds * stepTimeAllowed;
                    timeForStep = (stepAllowed < timeAllowed ? stepAllowed : timeAllowed) * 0.02;
                } else {
                    Policy.ifFirstStep = false;
                }
                tag++;

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
                policy.setRddlDomain(rddl);
                policy.setTimeAllowed(new Double(timeForStep).longValue());
                policy.timeAllowed = client.timeAllowed*0.6;
                policy.instanceName = instanceName;
                policy.algoName = clientName;

                //yi: use to compute state fitness
                fitPolicy.setTimeAllowed(new Double(stepTimeAllowed * 0.6).longValue());
                fitPolicy.setHorizon(horizon);


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
                reward = ((Number) domain._exprReward.sample(new HashMap<RDDL.LVAR, LCONST>(), state, rand)).doubleValue();
                System.out.println("reward:" + reward);
                state.advanceNextState();
                int searchdeep = 40 < (fitPolicy._totalHorizon - fitPolicy._horizon) ? 40 : (fitPolicy._totalHorizon - fitPolicy._horizon);

                long stareTime = new Double(stepTimeAllowed * 0.4).longValue();
                Double stateFitness = fitPolicy.Fitness(state, (long) stareTime, searchdeep);


                msg = createXMLPlan(state, domain, state._observ, reward, actions, stateFitness);
                totalTimeUsage += System.currentTimeMillis() - t0;
                Server.sendOneMessage(osw, msg);
                totalStepLeft--;
            } else {
                if (e.getNodeName().equals(Simserver.INSTANCE_END)) {
                    double total_reward = processXMLSessionEnd(p, isrc);
                    policy.sessionEnd(total_reward);
                    gono = false;
                }
                break;
            }
        }
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

                port = Integer.valueOf(args[0]);
                host = args[1];
                dataDir = new String(args[2]);
                if (args.length > 3 && args[3].equals("Original")) {
                    clientName = "SOGBOFA_Original";
                } else {
                    clientName = "L_C_SOGBOFA";
                }

                InetAddress address = InetAddress.getByName(host);
                connection = new Socket(address, port);
                System.out.println("RDDL client initialized");

                BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
                OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");

                while (true) {
                    Policy.resetPolicy();

                    State state;
                    INSTANCE instance;
                    NONFLUENTS nonFluents = null;
                    DOMAIN domain;
                    System.out.println("begin new session");
                    int tag = 0;

                    String msg = createXMLSessionRequest(clientName + "_"+(System.nanoTime() % 10000));
                    Server.sendOneMessage(osw, msg);
                    BufferedInputStream isr = new BufferedInputStream(connection.getInputStream());
                    DOMParser p = new DOMParser();

                    InputSource isrc = Server.readOneMessage(isr);
                    Client client = processXMLSessionInit(p, isrc);
                    System.out.println("get instance name :" + instanceName);

                    VisCounter visCounter = new VisCounter();

                    Class c = Class.forName("rddl.policy." + clientName);
                    Class fitCl = Class.forName("rddl.policy.DBNSearch");

                    state = new State();

                    Policy policy = (Policy) c.getConstructor(
                            new Class[]{String.class}).newInstance(new Object[]{instanceName});


                    policy.setRandSeed(randomSeed);
                    policy.setVisCounter(visCounter);

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

                    if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
                            || (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
                        boolean observations_present = (state._alObservNames.size() > 0);
                        System.err.println("WARNING: Domain '" + domain._sDomainName
                                + "' partially observed (PO) flag and presence of observations mismatched.\nSetting PO flag = " + observations_present + ".");
                        domain._bPartiallyObserved = observations_present;
                    }

                    policy.horizon = instance._nHorizon;
                    policy.setInstance(instanceName);
                    policy.setVisCounter(visCounter);
                    Policy.ifFirstStep = true;
                    Policy.timeUsedForCal = 0;
                    Policy.updatesIntotal = 0;
                    Policy.numberNodesUpdates = 0;
                    Policy.randomAction = new ArrayList<>();
                    Policy.ifConstructConstraints = true;
                    fitPolicy.initialTree(state);
                    fitPolicy.setTotalHorizon(instance._nHorizon);
                    fitPolicy.setSearchPara(4, 3);

                    Run(state, instance, nonFluents, domain, isrc, client, osw, msg, policy, fitPolicy, c, isr, p, instanceName, clientName, tag);
                }
            } finally {
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
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document dom = db.newDocument();
            Element rootEle = dom.createElement(Server.SESSION_REQUEST);
            dom.appendChild(rootEle);
            Server.addOneText(dom, rootEle, Server.CLIENT_NAME, clientName);
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
        rec.fileAppend(instanceName + ".rddl", new String(rddlDesc), true, dataDir);

        MyPath myPath = new MyPath();
        String absPath = System.getProperty("user.dir") + "/run-data" + "/" + dataDir + System.getProperties().getProperty("file.separator") + instanceName + ".rddl";
        try {
            rddl = new RDDL(absPath);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return c;
    }

    static String createXMLAction(ArrayList<PVAR_INST_DEF> ds) {
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

            if (e.getElementsByTagName(Server.NULL_OBSERVATIONS).getLength() > 0) {
                return null;
            }
            {
                ArrayList<String> r = Simserver.getTextValue(e, Simserver.TURN_NUM);
                if (r != null) {
                    horizon = Integer.valueOf(r.get(0));
                }
            }
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

    public static boolean judeFileExists(File file) {
        if (file.exists()) {
            System.out.println("file exists");
            return true;
        } else {
            System.out.println("file not exists");
            return false;
        }
    }

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

    static String createXMLPlan(State state, DOMAIN domain, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                                double immediateReward, ArrayList<PVAR_INST_DEF> ds, Double stateFitness) throws Exception {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();

            Element plan = dom.createElement("p");
            dom.appendChild(plan);
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
            Element immediateRewardElem = dom.createElement(Simserver.IMMEDIATE_REWARD);
            Text textImmediateRewardElem = dom.createTextNode(immediateReward + "");
            immediateRewardElem.appendChild(textImmediateRewardElem);
            plan.appendChild(immediateRewardElem);

            Element fitnessElem = dom.createElement(Simserver.SF);
            Text textFitnessElem = dom.createTextNode(stateFitness + "");
            fitnessElem.appendChild(textFitnessElem);
            plan.appendChild(fitnessElem);

            if (!domain._bPartiallyObserved || observStore != null) {
                for (PVAR_NAME pn :
                        (domain._bPartiallyObserved
                                ? observStore.keySet()
                                : state._state.keySet())) {
                    if (domain._bPartiallyObserved && observStore != null)
                        state._observ.put(pn, observStore.get(pn));

                    ArrayList<ArrayList<LCONST>> gfluents = state.generateAtoms(pn);
                    for (ArrayList<LCONST> gfluent : gfluents) {
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
                        pValue.appendChild(pTextValue);
                        ofEle.appendChild(pValue);
                    }
                }
            } else {
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
}

