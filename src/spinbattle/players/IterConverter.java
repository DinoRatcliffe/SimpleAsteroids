package spinbattle.players;

import ggi.core.AbstractGameState;
import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;

import java.util.HashMap;
import java.util.function.BiFunction;

public class IterConverter implements BiFunction<AbstractGameState, Integer, double[]> {
    @Override
    public double[] apply(AbstractGameState abstractGameState, Integer playerId) {
        return stateToInput((SpinGameState) abstractGameState, playerId);
    }

    private double[] stateToInput(SpinGameState gameState, int playerId) {
        double  playerShips = 0,
                playerGrowth = 0,
                opponentShips = 0,
                opponentGrowth = 0,
                neutralShips = 0;
        HashMap<Integer, Double> transitCounts = new HashMap<Integer, Double>();

        for (Planet planet : gameState.planets) {
            if (planet.ownedBy == playerId) {
                playerShips += planet.shipCount;
                playerGrowth += planet.growthRate;
            } else if (planet.ownedBy < 2) {
                opponentShips += planet.shipCount;
                opponentGrowth += planet.growthRate;
            } else {
                neutralShips += planet.shipCount;
            }
            Transporter transporter = planet.getTransporter();
            if (transporter != null) {
                double payload = transporter.payload;
                if (transporter.ownedBy != playerId) {
                    payload *= -1;
                }
                if (!transitCounts.containsKey(planet.index)) {
                    transitCounts.put(planet.index, 0.0);
                }
                transitCounts.put(planet.index, transitCounts.get(planet.index) + payload);
            }
        }

        double[] featureVector = new double[18 * (gameState.planets.size() * gameState.planets.size() - gameState.planets.size())];
        int currentIdx = 0;

        Planet src;
        Planet dest;
        for (int i = 0; i < gameState.planets.size(); i++) {
            for (int j = 0; j < gameState.planets.size(); j++) {
                if (i != j) {
                    src = gameState.planets.get(i);
                    dest = gameState.planets.get(j);

                    //global features
                    featureVector[currentIdx++] = playerShips;
                    featureVector[currentIdx++] = playerGrowth;
                    featureVector[currentIdx++] = opponentShips;
                    featureVector[currentIdx++] = opponentGrowth;
                    featureVector[currentIdx++] = neutralShips;

                    // planet specific features
                    featureVector[currentIdx++] = src.shipCount;
                    featureVector[currentIdx++] = src.growthRate;
                    featureVector[currentIdx++] = src.ownedBy == 0 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = src.ownedBy == 1 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = src.ownedBy == 2 ? 1.0 : 0.0;
                    if (transitCounts.containsKey(src.index)) {
                        featureVector[currentIdx++] = transitCounts.get(src.index);
                    } else {
                        featureVector[currentIdx++] = 0;
                    }

                    featureVector[currentIdx++] = dest.shipCount;
                    featureVector[currentIdx++] = dest.growthRate;
                    featureVector[currentIdx++] = dest.ownedBy == 0 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = dest.ownedBy == 1 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = dest.ownedBy == 2 ? 1.0 : 0.0;
                    if (transitCounts.containsKey(dest.index)) {
                        featureVector[currentIdx++] = transitCounts.get(dest.index);
                    } else {
                        featureVector[currentIdx++] = 0;
                    }

                    // planet pair features
                    featureVector[currentIdx++] = src.position.dist(dest.position);
                }
            }
        }
        return featureVector;
    }
}
