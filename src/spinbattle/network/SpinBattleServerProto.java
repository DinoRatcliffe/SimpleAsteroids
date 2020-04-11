package spinbattle.network;

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
import spinbattle.actuator.SourceTargetJointActuator;
import spinbattle.core.SpinGameState;
import spinbattle.params.SpinBattleParams;
import spinbattle.players.FlatConverter;
import spinbattle.players.ImageObservationConverter;
import spinbattle.players.IterConverter;
import sun.java2d.pipe.SpanShapeRenderer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class SpinBattleServerProto extends Thread {
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

    public SpinBattleServerProto(Socket socket, int index) {
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
        byte[] proto = SpinGameState.toByteArray(gameState);
        //out.write(BigInteger.valueOf(proto.length).toByteArray());
        out.write(proto);
        out.flush();
//        String observationJson = gson.toJson(observationFunction.apply(gameState, 0));
//        out.format(observationJson + "\n");
//        out.flush();
    }


    private void sendBatchStates(BufferedOutputStream out, ArrayList<SpinGameState> states) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] currentBytes;
        try {
            for (SpinGameState state : states) {
                currentBytes = SpinGameState.toByteArray(state);
                baos.write(BigInteger.valueOf(currentBytes.length).toByteArray());
                baos.write(currentBytes);
            }
            byte[] data = baos.toByteArray();
            out.write(BigInteger.valueOf(data.length).toByteArray());
            out.write(data);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int[][] loadSolutions(DataInputStream dis) {
        int[][] solutions = null;
        int bits = 2;
        try {
            int nSolutions = dis.readByte();
            int solutionLength = dis.readByte();
            solutions = new int[nSolutions][solutionLength];
            for (int i = 0; i < nSolutions; i++) {
                for (int j = 0; j < solutionLength; j++) {
                    bits++;
                    solutions[i][j] = dis.readByte();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return solutions;
    }


    private double computeFitness(SpinGameState startState, SimplePlayerInterface opponent, int[] solution) {
        SpinGameState currentState = (SpinGameState) startState.copy();
        int[] actions = new int[2];
        for (int action : solution) {
            currentState = transition(currentState, action, opponent);
        }
        return currentState.currentScore;
    }


    private void sendFitnesses(BufferedOutputStream out, double[] fitnesses) {
        byte[] bytes = new byte[8*fitnesses.length];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        for (int i = 0; i < fitnesses.length; i++) {
            buffer.putDouble(fitnesses[i]);
        }
        try {
            out.write(buffer.array());
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<SpinGameState> loadBatchGameState(DataInputStream dis, int nStates) {
        ArrayList<SpinGameState> states = new ArrayList<SpinGameState>();
        try {
            byte[] size = new byte[4];
            int protoSize = 0;
            byte[] proto;
            for (int i = 0; i < nStates; i++) {
                dis.read(size);
                protoSize = ByteBuffer.wrap(size).getInt();
                proto = new byte[protoSize];
                dis.read(proto);
                states.add(SpinGameState.byteToState(proto));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return states;
    }

    private int[] loadBatchActions(DataInputStream dis, int nActions) {
        int[] actions = new int[nActions];
        for (int i = 0; i < nActions; i++) {
            try {
                actions[i] = dis.readByte();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return actions;
    }

    private SpinGameState loadGameState(DataInputStream dis) {
        SpinGameState gs = null;
        try {
            byte[] size = new byte[4];
            dis.read(size);
            int protoSize = ByteBuffer.wrap(size).getInt();
            byte[] proto = new byte[protoSize];

            dis.read(proto);
            gs = SpinGameState.byteToState(proto);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        System.out.println(gs);
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

    private int loadPlanets(Scanner in) {
        return Integer.parseInt(in.nextLine());
    }

    private int loadAction(Scanner in) {
        String actionStr = in.nextLine();
        System.out.println(actionStr);
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
        SpinGameState currentGameState = restartStaticGame(globalRandom, 6);
        long oldTime = System.nanoTime();
        long newTime = System.nanoTime();

        try {
            double totalTime = 0;
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());

            Actuator[] currentActuators = currentGameState.actuators;
            ArrayList<Integer> currentPlanetIdicies = currentGameState.planetIndicies;
            ArrayList<Integer> currentPlayerIndicies = currentGameState.playerIndicies;
            SpinBattleParams currentParams = currentGameState.params;

            /*
            SpinBattleView view = new SpinBattleView().setParams(params).setGameState(gameState);
            String title = "Spin Battle Game" ;
            JEasyFrame frame = new JEasyFrame(view, title + ": Waiting for Graphics");
            frame.setLocation(new Point(800, 0));

             */

            SimplePlayerInterface randomPlayer = new RandomAgent();
            SimplePlayerInterface doNothingPlayer = new DoNothingAgent();
            SimplePlayerInterface evoPolicyAgent = getPolicyEvoAgent(new RandomAgent());
            ArrayList<SimplePlayerInterface> curriculumOpponnets = new ArrayList<SimplePlayerInterface>();
            //curriculumOpponnets.add(randomPlayer);
            for (int i = 1; i < 20; i+=2) {
                PolicyEvoAgent pea = (PolicyEvoAgent) getPolicyEvoAgent(new RandomAgent());
                pea.setNEvals(i);
                curriculumOpponnets.add(pea);
            }
            SimplePlayerInterface evoAgent = evoPolicyAgent;
            int currentOpponent = 1;
            int currentPlanets = 6;
            int maxPlanets = 12;
            SimplePlayerInterface opponent = evoAgent;
            Random random = new Random();
            int playerFirst = random.nextInt(2);

            BiFunction observationFunction = new FlatConverter();

            SpinGameState startState;
            int[][] solutions;
            double[] fitnesses;
            ArrayList<SpinGameState> batchInStates = new ArrayList<SpinGameState>();
            ArrayList<SpinGameState> batchOutStates = new ArrayList<SpinGameState>();
            int[] batchActions;
            int batchSize;

            int action = 0;
            boolean requestClose = false;

            double prevTime;

            while (!requestClose) {
                byte cmd = dis.readByte();
                switch (cmd) {
                    case 6: currentPlanets = dis.readByte();
                            System.out.println(currentPlanets);
                            currentGameState = restartStaticGame(globalRandom, currentPlanets);
                            currentPlanetIdicies = currentGameState.planetIndicies;
                            currentParams = currentGameState.params;
                            playerFirst = random.nextInt(2);
                            currentGameState.playerFirst = playerFirst;
                            sendGameState(out, currentGameState, observationFunction);
                            break;
                    case 4: this.evalServer = true;
                            currentGameState = restartStaticGame(globalRandom, currentPlanets);
                            playerFirst = random.nextInt(2);
                            currentGameState.playerFirst = playerFirst;
                            sendGameState(out, currentGameState, observationFunction);
                            break;
                    case 5: this.evalServer = false;
                            currentGameState = restartStaticGame(globalRandom, currentPlanets);
                            playerFirst = random.nextInt(2);
                            currentGameState.playerFirst = playerFirst;
                            currentParams = currentGameState.params;
                            currentPlanetIdicies = currentGameState.planetIndicies;
                            currentPlayerIndicies = currentGameState.playerIndicies;
                            sendGameState(out, currentGameState, observationFunction);
                            break;
                    case 0: currentGameState = restartStaticGame(globalRandom, currentPlanets);
                            opponent.reset();
                            playerFirst = random.nextInt(2);
                            currentGameState.playerFirst = playerFirst;
                            sendGameState(out, currentGameState, observationFunction);
                            break;
                    case 1: currentGameState = loadGameState(dis);
                            currentGameState.actuators = currentActuators;
                            currentGameState.planetIndicies = currentPlanetIdicies;
                            currentGameState.playerIndicies = currentPlayerIndicies;
                            currentGameState.setParams(currentParams);
                            action = Byte.toUnsignedInt(dis.readByte());
                            currentGameState = transition(currentGameState, action, opponent);
                            sendGameState(out, currentGameState, observationFunction);
                            break;
                    case 8: if (currentPlanets < maxPlanets) {
                                currentPlanets += 2;
                            }
                            currentGameState = restartStaticGame(globalRandom, currentPlanets);
                            playerFirst = random.nextInt(2);
                            currentGameState.playerFirst = playerFirst;
                            System.out.println(currentGameState.planets.size());
                            sendGameState(out, currentGameState, observationFunction);
                            break;
                    case 2: // get start state
                            startState = loadGameState(dis);
                            startState.actuators = currentActuators;
                            startState.planetIndicies = currentPlanetIdicies;
                            startState.playerIndicies = currentPlayerIndicies;
                            startState.setParams(currentParams);
                            solutions = loadSolutions(dis);
                            fitnesses = new double[solutions.length];
                            for (int i = 0; i < solutions.length; i++) {
                                fitnesses[i] = computeFitness(startState, opponent, solutions[i]);
                            }
                            sendFitnesses(out, fitnesses);
                            break;
                    case 3: batchSize = dis.readByte();
                            batchInStates = loadBatchGameState(dis, batchSize);
                            batchActions = loadBatchActions(dis, batchSize);


                            batchOutStates.clear();
                            for (int i = 0; i < batchSize; i++) {
                                startState = batchInStates.get(i);
                                startState.actuators = currentActuators;
                                startState.planetIndicies = currentPlanetIdicies;
                                startState.playerIndicies = currentPlayerIndicies;
                                startState.setParams(currentParams);

                                batchOutStates.add(transition(startState, batchActions[i], opponent));
                            }
                            sendBatchStates(out, batchOutStates);
                            break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SpinGameState restartStaticGame(Random globalRandom, int planets) {
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
        params.nPlanets = planets;
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
        boolean useShiftBuffer = false;
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
        //serverSocket.setReceiveBufferSize(4096);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), defaultPort));
        //serverSocket.setPerformancePreferences(0, 2, 1);
        int i = 0;
        while (true) {
            System.out.println("waiting for client");
            Socket socket = serverSocket.accept();
            //socket.setTcpNoDelay(true);
            //socket.setSendBufferSize(4096);
            new SpinBattleServerProto(socket, i++);
        }
    }
}
