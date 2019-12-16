package spinbattle.network;

import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

import agents.dummy.DoNothingAgent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ggi.core.SimplePlayerInterface;
import spinbattle.actuator.Actuator;
import spinbattle.actuator.ActuatorAdapter;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.core.SpinGameState;
import spinbattle.params.SpinBattleParams;

public class SpinBattleServer extends Thread {
    public static int defaultPort = 3000;

    Socket socket;
    Gson gson;

    public SpinBattleServer(Socket socket) {
        this.socket = socket;

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Actuator.class, new ActuatorAdapter());
        gson = builder.create();

        start();
    }

    private void sendGameState(PrintStream out, SpinGameState gameState) {
        String json = gson.toJson(gameState);
        out.format(json + "\n");
    }

    private SpinGameState loadGameState(Scanner in) {
        String jsonGameState = in.nextLine();
        SpinGameState gs = gson.fromJson(jsonGameState, SpinGameState.class);
        return gs;
    }

    private void sendParams(PrintStream out, SpinBattleParams params) {
        String json = gson.toJson(params);
        out.format(json + "\n");
    }

    private SpinBattleParams loadParams(Scanner in) {
        String jsonParams = in.nextLine();
        return gson.fromJson(jsonParams, SpinBattleParams.class);
    }

    private int loadAction(Scanner in) {
        String actionStr = in.nextLine();
        return Integer.parseInt(actionStr);
    }

    private SpinGameState transition(SpinGameState state, int action, SimplePlayerInterface opponent) {
        int[] actions = new int[2];
        int opponent_action = opponent.getAction(state, 1);
        actions[0] = action;
        actions[1] = opponent_action;
        SpinGameState new_state = (SpinGameState) state.next(actions);
        return new_state;
    }

    public void run() {
        System.out.println("Started a new Socket: " + socket);

        try {
            Scanner in = new Scanner(socket.getInputStream());
            PrintStream out = new PrintStream(socket.getOutputStream());

            SpinBattleParams currentParams = new SpinBattleParams();
            SpinGameState currentGameState = restartStaticGame();
            Actuator[] currentActuators = currentGameState.actuators;

            /*
            SpinBattleView view = new SpinBattleView().setParams(params).setGameState(gameState);
            String title = "Spin Battle Game" ;
            JEasyFrame frame = new JEasyFrame(view, title + ": Waiting for Graphics");
            frame.setLocation(new Point(800, 0));

             */

            SimplePlayerInterface randomPlayer = new agents.dummy.RandomAgent();
            SimplePlayerInterface doNothingPlayer = new DoNothingAgent();
            SimplePlayerInterface opponent = randomPlayer;
            
            boolean requestClose = false;
            while (!requestClose) {
                String cmd = in.nextLine();
                switch (cmd) {
                    case "set_params": System.out.println("set_params");
                                       currentParams = loadParams(in);
                                       currentGameState = restartStaticGame();
                                       sendGameState(out, currentGameState);
                                       break;
                    case "get_params": System.out.println("get_params");
                                       sendParams(out, currentParams);
                                       break;
                    case "set_state": System.out.println("set_state");
                                      currentGameState = loadGameState(in);
                                      break;
                    case "get_state": System.out.println("get_state");
                                      sendGameState(out, currentGameState);
                                      break;
                    case "reset":  System.out.println("reset");
                                   System.out.println(currentGameState.getScore());
                                   currentGameState = restartStaticGame();
                                   sendGameState(out, currentGameState);
                                   break;
                    case "transition": //System.out.println("transition");
                                       currentGameState = loadGameState(in);
                                       currentGameState = transition(currentGameState, loadAction(in), opponent);
                                       sendGameState(out, currentGameState);
                                       break;
                    case "close": requestClose = true;
                                  break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static SpinGameState restartStaticGame() {
        // Game Setup
        long seed = -6330548296303013003L;
        System.out.println("Setting seed to: " + seed);
        SpinBattleParams.random = new Random(seed);
        SpinBattleParams params = new SpinBattleParams();
        params.maxTicks = 500;
        params.nPlanets = 5;
        params.transitSpeed = 30;
        params.useVectorField = false;
        params.useProximityMap = false;
        params.includeTransitShipsInScore = true;
        SpinGameState gameState = new SpinGameState().setParams(params).setPlanets();
        gameState.actuators[0] = new SourceTargetActuator().setPlayerId(0);
        gameState.actuators[1] = new SourceTargetActuator().setPlayerId(1);
        return gameState;
    }

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(defaultPort);
        while (true) {
            System.out.println("waiting for client");
            Socket socket = serverSocket.accept();
            new SpinBattleServer(socket);
        }
    }
}
