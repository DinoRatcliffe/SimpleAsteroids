package spinbattle.network;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.util.Random;
import java.util.Scanner;
import java.util.function.BiFunction;

import agents.dummy.DoNothingAgent;
import agents.dummy.RandomAgent;
import agents.evo.EvoAgent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import evodef.DefaultMutator;
import evodef.EvoAlg;
import ga.SimpleRMHC;
import ggi.agents.PolicyEvoAgent;
import ggi.agents.SimpleEvoAgent;
import ggi.core.SimplePlayerInterface;
import spinbattle.actuator.Actuator;
import spinbattle.actuator.ActuatorAdapter;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.actuator.SourceTargetJointActuator;
import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;
import spinbattle.params.SpinBattleParams;
import spinbattle.players.ImageObservationConverter;
import spinbattle.players.IterANNPlayer;
import spinbattle.players.IterConverter;
import spinbattle.players.FlatConverter;
import utilities.StatSummary;

public class SpinBattleServer extends Thread {
    public static int defaultPort = 3000;

    public static Random globalRandom = new Random();

    Socket socket;
    Gson gson;
    int index;
    boolean evalServer;
    long[] eval_seeds = {42,};
//                         42L,
//                         1010L,
//                         -983324923894L,
//                         92348927349823L};
    int current_eval_seed;

    public SpinBattleServer(Socket socket, int index) {
        this.socket = socket;
        this.index = index;
        this.evalServer = false;
        this.current_eval_seed = 0;

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Actuator.class, new ActuatorAdapter());
        gson = builder.create();

        start();
    }

    private void sendGameState(BufferedOutputStream out, SpinGameState gameState, BiFunction observationFunction) throws InterruptedException, IOException {
        String json = gson.toJson(gameState);
        out.write((json + "\n").getBytes());
        out.flush();
//        String observationJson = gson.toJson(observationFunction.apply(gameState, 0));
//        out.format(observationJson + "\n");
//        out.flush();
    }

    private SpinGameState loadGameState(Scanner in) {
        String jsonGameState = in.nextLine();
        SpinGameState gs = gson.fromJson(jsonGameState, SpinGameState.class);
        gs.setParams(gs.params);
        if(gs.vectorField != null) {
            gs.vectorField.setParams(gs.params);
        }
        return gs;
    }

    private void sendParams(BufferedOutputStream out, SpinBattleParams params) throws IOException {
        String json = gson.toJson(params);
        out.write((json + "\n").getBytes());
        out.flush();
    }

    private SpinBattleParams loadParams(Scanner in) {
        String jsonParams = in.nextLine();
        return gson.fromJson(jsonParams, SpinBattleParams.class);
    }

    private int loadAction(Scanner in) {
        String actionStr = in.nextLine();
        return Integer.parseInt(actionStr);
    }

    private BiFunction getObservationFunction(Scanner in) {
        String observationFunctionName = in.nextLine();
        BiFunction observationFunction;
        switch (observationFunctionName) {
            case "img": observationFunction = new ImageObservationConverter();
                        break;
            case "iter": observationFunction = new IterConverter();
                         break;
            default: observationFunction = new FlatConverter();
        }
        return observationFunction;
    }

    private SpinGameState transition(SpinGameState state, int action, SimplePlayerInterface opponent) {
        int[] actions = new int[2];
        if (state.playerFirst == 0) {
            actions[0] = action;
            actions[1] = opponent.getAction(state, 1);
        } else {
            actions[1] = action;
            actions[0] = opponent.getAction(state, 0);
        }
        SpinGameState new_state = (SpinGameState) state.next(actions);
        return new_state;
    }

    public void run() {
        SpinBattleParams currentParams = new SpinBattleParams();
        SpinGameState currentGameState = restartStaticGame(globalRandom);

        try {
            double totalTime = 0;
            Scanner in = new Scanner(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());

            Actuator[] currentActuators = currentGameState.actuators;

            /*
            SpinBattleView view = new SpinBattleView().setParams(params).setGameState(gameState);
            String title = "Spin Battle Game" ;
            JEasyFrame frame = new JEasyFrame(view, title + ": Waiting for Graphics");
            frame.setLocation(new Point(800, 0));

             */

            SimplePlayerInterface randomPlayer = new agents.dummy.RandomAgent();
            SimplePlayerInterface doNothingPlayer = new DoNothingAgent();
            SimplePlayerInterface evoAgent = getPolicyEvoAgent(new RandomAgent());
            SimplePlayerInterface opponent = evoAgent;

            Random random = new Random();
            int playerFirst = random.nextInt(2);

            BiFunction observationFunction = new FlatConverter();

            int action = 0;
            boolean requestClose = false;

            double prevTime;

            while (!requestClose) {
                String cmd = in.nextLine();
                switch (cmd) {
                    case "set_observation_function": observationFunction = getObservationFunction(in);
                                                     break;
                    case "set_params": currentParams = loadParams(in);
                                       currentGameState = restartStaticGame(globalRandom);
                                       playerFirst = random.nextInt(2);
                                       currentGameState.playerFirst = playerFirst;
                                       sendGameState(out, currentGameState, observationFunction);
                                       break;
                    case "get_params": sendParams(out, currentParams);
                                       break;
                    case "set_state": currentGameState = loadGameState(in);
                                      break;
                    case "set_eval_games": this.evalServer = true;
                                           currentGameState = restartStaticGame(globalRandom);
                                           playerFirst = random.nextInt(2);
                                           currentGameState.playerFirst = playerFirst;
                                           sendGameState(out, currentGameState, observationFunction);
                                           break;
                    case "set_random_games": this.evalServer = false;
                                             currentGameState = restartStaticGame(globalRandom);
                                             playerFirst = random.nextInt(2);
                                             currentGameState.playerFirst = playerFirst;
                                             sendGameState(out, currentGameState, observationFunction);
                                             break;
                    case "get_state": sendGameState(out, currentGameState, observationFunction);
                                      break;
                    case "reset":  currentGameState = restartStaticGame(globalRandom);
                                   opponent.reset();
                                   playerFirst = random.nextInt(2);
                                   currentGameState.playerFirst = playerFirst;
                                   sendGameState(out, currentGameState, observationFunction);
                                   break;
                    case "transition":  currentGameState = loadGameState(in);
                                        action = loadAction(in);
                                        currentGameState = transition(currentGameState, action, opponent);
                                        sendGameState(out, currentGameState, observationFunction);
                                        break;
                    case "close": requestClose = true;
                                  break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SpinGameState restartStaticGame(Random globalRandom) {
        // Game Setup
        if (this.evalServer) {
            SpinBattleParams.random = new Random(eval_seeds[current_eval_seed]);
            current_eval_seed += 1;
            current_eval_seed = current_eval_seed % eval_seeds.length;
        } else {
            SpinBattleParams.random = globalRandom;
        }

        SpinBattleParams params = new SpinBattleParams();
        params.width = (int) (params.width*1.5);
        params.height = (int) (params.height*1.5);
        params.maxTicks = 500;
        params.nPlanets = 12;
        params.nToAllocate = 6;
        params.transitSpeed = 30;
        params.useVectorField = false;
        params.useProximityMap = false;
//        params.minGrowth = 0.5;
        params.maxGrowth = 0.25;
        params.symmetricMaps = true;
        params.includeTransitShipsInScore = true;
        SpinGameState gameState = new SpinGameState().setParams(params).setPlanets();
        gameState.actuators[0] = new SourceTargetJointActuator().setPlayerId(0);
        gameState.actuators[1] = new SourceTargetJointActuator().setPlayerId(1);
        return gameState;
    }

    static boolean useSimpleEvoAgent = false;
    static SimplePlayerInterface getEvoAgent() {

        if (useSimpleEvoAgent) {
            return new SimpleEvoAgent().setOpponent(new DoNothingAgent());
        }
        //
        int nResamples = 1;

        DefaultMutator mutator = new DefaultMutator(null);
        // setting to true may give best performance
        // mutator.totalRandomChaosMutation = true;
        mutator.pointProb = 10;

        SimpleRMHC simpleRMHC = new SimpleRMHC();
        simpleRMHC.setSamplingRate(nResamples);
        simpleRMHC.setMutator(mutator);

        EvoAlg evoAlg = simpleRMHC;

        // evoAlg = new SlidingMeanEDA();
        // evoAlg = new SimpleGA();

        int nEvals = 20;
        int seqLength = 100;
        EvoAgent evoAgent = new EvoAgent().setEvoAlg(evoAlg, nEvals).setSequenceLength(seqLength);
        boolean useShiftBuffer = true;
        evoAgent.setUseShiftBuffer(useShiftBuffer);
        //evoAgent.setVisual();

        return evoAgent;
    }

    static SimplePlayerInterface getPolicyEvoAgent(SimplePlayerInterface policy) {
        PolicyEvoAgent evoAgent = new PolicyEvoAgent();
        evoAgent.setUseShiftBuffer(true);
        evoAgent.setNEvals(20);
        evoAgent.setSequenceLength(100);
        evoAgent.setPolicy(policy);
        return evoAgent;
    }

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReceiveBufferSize(4096*2);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), defaultPort));
        serverSocket.setPerformancePreferences(0, 2, 1);
        int i = 0;
        while (true) {
            System.out.println("waiting for client");
            Socket socket = serverSocket.accept();
            socket.setTcpNoDelay(true);
            //socket.setSendBufferSize(4096);
            new SpinBattleServer(socket, i++);
        }
    }
}
