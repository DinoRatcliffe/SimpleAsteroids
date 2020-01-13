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
    int index;
    boolean evalServer;
    long[] eval_seeds = {-6330548296303013003L,};
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
        SpinBattleParams currentParams = new SpinBattleParams();
        SpinGameState currentGameState = restartStaticGame();

        try {
            Scanner in = new Scanner(socket.getInputStream());
            PrintStream out = new PrintStream(socket.getOutputStream());

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
            int action = 0;
            while (!requestClose) {
                String cmd = in.nextLine();
                switch (cmd) {
                    case "set_params": currentParams = loadParams(in);
                                       currentGameState = restartStaticGame();
                                       sendGameState(out, currentGameState);
                                       break;
                    case "get_params": sendParams(out, currentParams);
                                       break;
                    case "set_state": currentGameState = loadGameState(in);
                                      break;
                    case "set_eval_games": this.evalServer = true;
                                           currentGameState = restartStaticGame();
                                           sendGameState(out, currentGameState);
                                           break;
                    case "set_random_games": this.evalServer = false;
                                             currentGameState = restartStaticGame();
                                             sendGameState(out, currentGameState);
                                             break;
                    case "get_state": sendGameState(out, currentGameState);
                                      break;
                    case "reset":  currentGameState = restartStaticGame();
                                   sendGameState(out, currentGameState);
                                   break;
                    case "transition": currentGameState = loadGameState(in);
                                       action = loadAction(in);
                                       currentGameState = transition(currentGameState, action, opponent);
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

    public SpinGameState restartStaticGame() {
        // Game Setup
        if (this.evalServer) {
            SpinBattleParams.random = new Random(eval_seeds[current_eval_seed]);
            current_eval_seed += 1;
            current_eval_seed = current_eval_seed % eval_seeds.length;
        } else {
            SpinBattleParams.random = new Random(eval_seeds[0]);
        }
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
        int i = 0;
        while (true) {
            System.out.println("waiting for client");
            Socket socket = serverSocket.accept();
            new SpinBattleServer(socket, i++);
        }
    }
}
