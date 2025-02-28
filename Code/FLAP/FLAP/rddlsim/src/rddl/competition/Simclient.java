/**
 * RDDL: Main client code for interaction with RDDLSim server
 *
 * @author Sungwook Yoon (sungwook.yoon@gmail.com)
 * @version 10/1/10
 **/

package rddl.competition;

import org.apache.commons.math3.random.RandomDataGenerator;
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
import rddl.RDDL.*;
import rddl.State;
import rddl.policy.Policy;
import rddl.viz.StateViz;
import util.Pair;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * The SocketClient class is a simple example of a TCP/IP Socket Client.
 */

public class Simclient {

    public static final boolean SHOW_XML = false;
    public static final boolean SHOW_MSG = false;
    public static final boolean SHOW_MEMORY_USAGE = true;
    public static final Runtime RUNTIME = Runtime.getRuntime();
    public static final int DEFAULT_RANDOM_SEED = 0;
    private static DecimalFormat _df = new DecimalFormat("0.##");
    /**
     * @Author Yi
     * @Description 添加相关参数；
     **/
    public static String instanceName = null;
    static double stepTimeAllowed = Double.MAX_VALUE;
    static int BF = 5;
    public static int horizon = 0;

    enum XMLType {
        ROUND, TURN, ROUND_END, END_TEST, NONAME
    }

    private static RDDL rddl = null;

    int numRounds;
    double timeAllowed;
    int curRound;
    double reward;
    int id;

    Simclient() {
        numRounds = 0;
        timeAllowed = 0;
        curRound = 0;
        reward = 0;
        id = 0;
    }


    public static void main(String[] args) {

        String host = Simserver.HOST_NAME;
        int port = Simserver.PORT_NUMBER;
        String clientName = "random";

        int randomSeed = (int) (System.currentTimeMillis() % 678) * 123;

        State state;
        StateViz stateViz;

        StringBuffer instr = new StringBuffer();
        String TimeStamp;

        if (args.length < 3) {
            System.out.println("usage: rddlfilename hostname clientname policyclassname " +
                    "(optional) portnumber randomSeed instanceName/directory");
            System.exit(1);
        }
        host = args[1];
        clientName = args[2];
        port = Integer.valueOf(args[3]);

        double timeLeft = 0;
        try {
            Class c = Class.forName(args[4]);
            Class fitCl = Class.forName("rddl.policy.DBNSearch");

            rddl = new RDDL(args[0]);


            InetAddress address = InetAddress.getByName(host);
            Socket connection = new Socket(address, port);
            System.out.println("RDDL client connect ");

            BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
            OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");

            while (true) {
                INSTANCE instance;
                NONFLUENTS nonFluents = null;
                DOMAIN domain;
                String msg = createXMLSessionRequest(clientName);
                Simserver.sendOneMessage(osw, msg);
                BufferedInputStream isr = new BufferedInputStream(connection.getInputStream());
                state = new State();
                DOMParser p = new DOMParser();
                InputSource isrc = Simserver.readOneMessage(isr);
                Simclient client = processXMLSessionInit(p, isrc);

                Policy policy = (Policy) c.getConstructor(
                        new Class[]{String.class}).newInstance(new Object[]{instanceName});
                policy.setRDDL(rddl);
                policy.setRandSeed(randomSeed);
                policy.setTimeAllowed(new Double(stepTimeAllowed).longValue());

                Policy fitPolicy = (Policy) fitCl.getConstructor(
                        new Class[]{String.class}).newInstance(new Object[]{instanceName});
                fitPolicy.setRDDL(rddl);
                fitPolicy.setRandSeed(randomSeed);
                fitPolicy.setTimeAllowed(new Double(stepTimeAllowed).longValue());


                instance = rddl._tmInstanceNodes.get(instanceName);
                if (instance._sNonFluents != null) {
                    nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
                } else {
                    System.err.println("instance.noFulents = null" + instanceName);
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
                System.out.println("client inital instance" + instanceName);
                if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
                        || (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
                    boolean observations_present = (state._alObservNames.size() > 0);
                    System.err.println("WARNING: Domain '" + domain._sDomainName
                            + "' partially observed (PO) flag and presence of observations mismatched.\nSetting PO flag = " + observations_present + ".");
                    domain._bPartiallyObserved = observations_present;
                }
                State initialState = cloneState(state);

                policy.initialTree(state);
                policy.setTotalHorizon(instance._nHorizon);
                policy.setSearchPara(4, 3);

                fitPolicy.initialTree(state);
                fitPolicy.setTotalHorizon(instance._nHorizon);
                fitPolicy.setSearchPara(4, 3);

                boolean gono = true;
                int resetTag = 0;
                while (gono) {
                    resetTag++;
                    if (resetTag == 100) {
                        state = cloneState(initialState);
                        resetTag = 0;
                    }

                    if (timeLeft < 0) {
                        break;
                    }

                    if (SHOW_MSG) System.out.println("Reading turn message");
                    isrc = Simserver.readOneMessage(isr);
                    Element e = parseMessage(p, isrc);
                    if (e.getNodeName().equals(Simserver.TURN)) {
                        if (SHOW_MSG) System.out.println("Done reading turn message");

                        ArrayList<PVAR_INST_DEF> obs = processXMLTurn(e, state);
                        if (SHOW_MSG) System.out.println("Done parsing turn message");
                        if (obs == null) {
                            if (SHOW_MSG) System.out.println("No state/observations received.");
                            if (SHOW_XML)
                                Simserver.printXMLNode(p.getDocument());
                        } else if (domain._bPartiallyObserved) {
                            state.clearPVariables(state._observ);
                            state.setPVariables(state._observ, obs);
                        } else {
                            state.clearPVariables(state._state);
                            state.setPVariables(state._state, obs);
                        }
                        policy.setTimeAllowed(new Double(stepTimeAllowed).longValue());
                        policy.setHorizon(horizon);
                        fitPolicy.setTimeAllowed(new Double(stepTimeAllowed).longValue());
                        fitPolicy.setHorizon(horizon);

                        fitPolicy.setTimeAllowed(new Double(stepTimeAllowed * 0.6).longValue());

                        ArrayList<PVAR_INST_DEF> actions =
                                policy.getActions(obs == null ? null : state);

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
                            throw ee;
                        }

                        reward = ((Number) domain._exprReward.sample(new HashMap<LVAR, LCONST>(), state, rand)).doubleValue();
                        System.out.println("reward:" + reward);
                        state.advanceNextState();
                        int searchdeep = 40 < (fitPolicy._totalHorizon - fitPolicy._horizon) ? 40 : (fitPolicy._totalHorizon - fitPolicy._horizon);

                        long stareTime = new Double(stepTimeAllowed * 0.5).longValue();
                        Double stateFitness = fitPolicy.Fitness(state, (long) stareTime, searchdeep, BF);


                        msg = createXMLPlan(state, domain, state._observ, reward, actions, stateFitness);
                        if (SHOW_MSG)
                            System.out.println("Sending: " + msg);
                        Simserver.sendOneMessage(osw, msg);
                    } else {
                        gono = false;
                        break;
                    }
                }

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
            Simserver.printXMLNode(p.getDocument());

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


    static String createXMLSessionRequest(String clientName) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element rootEle = dom.createElement(Simserver.SESSION_REQUEST);
            dom.appendChild(rootEle);
            Simserver.addOneText(dom, rootEle, Simserver.CLIENT_NAME, clientName);
            return serialize(dom);
        } catch (Exception e) {
            return null;
        }
    }


    static Simclient processXMLSessionInit(DOMParser p, InputSource isrc) throws RDDLXMLException {
        try {
            p.parse(isrc);
        } catch (SAXException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("sax exception");
        } catch (IOException e1) {
            e1.printStackTrace();
            throw new RDDLXMLException("io exception");
        }
        Simclient c = new Simclient();
        Element e = p.getDocument().getDocumentElement();

        if (!e.getNodeName().equals(Simserver.SESSION_INIT)) {
            throw new RDDLXMLException("not session init");
        }
        ArrayList<String> r = Simserver.getTextValue(e, Simserver.SESSION_ID);
        if (r != null) {
            c.id = Integer.valueOf(r.get(0));
        }
        r = Simserver.getTextValue(e, Simserver.TIME_ALLOWED);
        if (r != null) {
            c.timeAllowed = Double.valueOf(r.get(0));
        }
        //by yi
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
        r = Server.getTextValue(e, Simserver.B_F);
        if (r != null) {
            BF = Integer.valueOf(r.get(0));
        }
        return c;
    }

    static String createXMLAction(ArrayList<PVAR_INST_DEF> ds) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();
            Element actions = dom.createElement(Simserver.ACTIONS);
            dom.appendChild(actions);
            for (PVAR_INST_DEF d : ds) {
                Element action = dom.createElement(Simserver.ACTION);
                actions.appendChild(action);
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
            if (SHOW_XML) {
                Simserver.printXMLNode(dom);
                System.out.println();
                System.out.flush();
            }

            return serialize(dom);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }


    static ArrayList<PVAR_INST_DEF> processXMLTurn(Element e,
                                                   State state) throws RDDLXMLException {

        if (e.getNodeName().equals(Simserver.TURN)) {
            if (e.getElementsByTagName(Simserver.NULL_OBSERVATIONS).getLength() > 0) {
                return null;
            }

            {
                ArrayList<String> r = Simserver.getTextValue(e, Simserver.TURN_NUM);
                if (r != null) {
                    horizon = Integer.valueOf(r.get(0));
                }
            }

            NodeList nl = e.getElementsByTagName(Simserver.OBSERVED_FLUENT);
            if (nl != null && nl.getLength() > 0) {
                ArrayList<PVAR_INST_DEF> ds = new ArrayList<PVAR_INST_DEF>();
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    String name = Simserver.getTextValue(el, Simserver.FLUENT_NAME).get(0);
                    ArrayList<String> args = Simserver.getTextValue(el, Simserver.FLUENT_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String value = Simserver.getTextValue(el, Simserver.FLUENT_VALUE).get(0);
                    Object r = Simserver.getValue(name, value, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, r, lcArgs);
                    ds.add(d);
                }
                return ds;
            } else
                return new ArrayList<PVAR_INST_DEF>();
        }
        throw new RDDLXMLException("Client.processXMLTurn: Should not reach this point");
    }


    static double processXMLPlan(DOMParser p, InputSource isrc, State state, ArrayList<PVAR_INST_DEF> action,
                                 ArrayList<PVAR_INST_DEF> reSt) throws Exception {
        double immediateReward = 0.0;
        try {
            p.parse(isrc);
            Element e = p.getDocument().getDocumentElement();

            if (e.getNodeName().equals(Simserver.RESOURCE_REQUEST)) {
                action = null;
                reSt = null;
                return 0.0;
            }

            if (!e.getNodeName().equals(Simserver.P)) {
                System.out.println("receive plan error!!!!");
                System.out.println("Received action msg:");
                Simserver.printXMLNode(e);
                throw new Exception("ERROR: NO ACTIONS NODE");
            }

            NodeList nl = e.getElementsByTagName(Simserver.ACTION);
            if (nl != null) {
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    String name = Simserver.getTextValue(el, Simserver.ACTION_NAME).get(0);
                    ArrayList<String> args = Simserver.getTextValue(el, Simserver.ACTION_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String pvalue = Simserver.getTextValue(el, Simserver.ACTION_VALUE).get(0);
                    Object value = Simserver.getValue(name, pvalue, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, value, lcArgs);
                    action.add(d);
                }
            } else
                action = new ArrayList<PVAR_INST_DEF>();


            if (e.getElementsByTagName(Simserver.NULL_OBSERVATIONS).getLength() > 0) {
                reSt = null;
            }

            ArrayList<String> ir = Simserver.getTextValue(e, Simserver.IMMEDIATE_REWARD);
            if (ir != null) {
                immediateReward = Double.valueOf(ir.get(0));
            }

            NodeList nls = e.getElementsByTagName(Simserver.OBSERVED_FLUENT);
            if (nls != null && nls.getLength() > 0) {
                for (int i = 0; i < nls.getLength(); i++) {
                    Element el = (Element) nls.item(i);
                    String name = Simserver.getTextValue(el, Simserver.FLUENT_NAME).get(0);
                    ArrayList<String> args = Simserver.getTextValue(el, Simserver.FLUENT_ARG);
                    ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
                    for (String arg : args) {
                        if (arg.startsWith("@"))
                            lcArgs.add(new RDDL.ENUM_VAL(arg));
                        else
                            lcArgs.add(new RDDL.OBJECT_VAL(arg));
                    }
                    String value = Simserver.getTextValue(el, Simserver.FLUENT_VALUE).get(0);
                    Object r = Simserver.getValue(name, value, state);
                    PVAR_INST_DEF d = new PVAR_INST_DEF(name, r, lcArgs);
                    reSt.add(d);
                }
            } else
                reSt = new ArrayList<PVAR_INST_DEF>();

            return immediateReward;
        } catch (Exception e) {
            System.out.println("processXMLState ERROR:\n" + e);
            throw e;
        }

    }

    static String createXMLPlan(State state, DOMAIN domain, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> observStore,
                                double immediateReward, ArrayList<PVAR_INST_DEF> ds, Double stateFitness) throws Exception {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.newDocument();

            Element plan = dom.createElement(Simserver.P);
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
        if (e.getNodeName().equals(Simserver.SESSION_END)) {
            ArrayList<String> text = Simserver.getTextValue(e, Simserver.TOTAL_REWARD);
            if (text == null) {
                return -1;
            }
            return Double.valueOf(text.get(0));
        }
        return -1;
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

    static public HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> selectMeanNextState(State s, ArrayList<PVAR_INST_DEF> action, DOMAIN domain,
                                                                                             int statenNum, int sampleNum, Policy policy, RandomDataGenerator rand) throws EvalException {
        HashMap<HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>, Double> nextStateMeanList = new HashMap();
        double meanValue = 0.0;
        for (int i = 0; i < statenNum; i++) {
            rand.reSeed(System.currentTimeMillis());
            s.computeNextState(action, rand);
            nextStateMeanList.put(s._nextState, 0.0);
        }
        for (HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> nextSt : nextStateMeanList.keySet()) {
            double MR = 0.0;
            for (int i = 0; i < sampleNum; i++) {
                State state = cloneState(s);
                state._state = nextSt;
                policy.setTimeAllowed(new Double(stepTimeAllowed).longValue());
                ArrayList<PVAR_INST_DEF> actions = policy.getActions(state);
                for (PVAR_NAME p : state._actions.keySet())
                    state._actions.get(p).clear();
                state.setPVariables(state._actions, actions);
                double reward = ((Number) domain._exprReward.sample(new HashMap<LVAR, LCONST>(), state, rand)).doubleValue();
                MR += reward;
                System.out.println("reward:" + reward);
            }
            System.out.println("meanValue" + MR / sampleNum);
            meanValue += MR / sampleNum;
            nextStateMeanList.put(nextSt, new Double(MR / sampleNum));
        }
        HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> nextState = null;
        double dif = Double.MAX_VALUE;
        for (HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> nextSt : nextStateMeanList.keySet()) {
            if (Math.abs(nextStateMeanList.get(nextSt) - meanValue) < dif)
                nextState = nextSt;
        }
        return nextState;
    }


}

