package spinbattle.players;

import ggi.core.AbstractGameState;
import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;

import java.util.HashMap;
import java.util.function.BiFunction;

public class ImageObservationConverter implements BiFunction<AbstractGameState, Integer, double[][][]> {
    @Override
    public double[][][] apply(AbstractGameState abstractGameState, Integer playerId) {
        return stateToInput((SpinGameState) abstractGameState, playerId);
    }

    private double[][][] stateToInput(SpinGameState gameState, int playerId) {

        HashMap<Integer, Double> transitCounts = new HashMap<Integer, Double>();
        for (Planet planet : gameState.planets) {
            Transporter transporter = planet.getTransporter();
            if (transporter != null) {
                double payload = transporter.payload;
                if (transporter.ownedBy != playerId) {
                    payload *= -1;
                }
                if (!transitCounts.containsKey(transporter.target)) {
                    transitCounts.put(transporter.target, 0.0);
                }
                transitCounts.put(transporter.target, transitCounts.get(transporter.target) + payload);
            }
        }

        double [][][] image = new double[gameState.params.width][gameState.params.height][5];
        for (Planet planet : gameState.planets) {
            double[] featureValues = new double[5];
            featureValues[0] = planet.ownedBy == 0 ? 1 : 0;
            featureValues[1] = planet.ownedBy == 1 ? 1 : 0;
            featureValues[2] = planet.growthRate;
            featureValues[3] = planet.shipCount;
            if (transitCounts.containsKey(planet.index)) {
                featureValues[4] = transitCounts.get(planet.index);
            } else {
                featureValues[4] = 0;
            }

            image = drawCircle(image, planet.position.x, planet.position.y, planet.getRadius(), featureValues);
        }
        return image;
    }

    private double[][][] drawCircle(double[][][] img, double x, double y, double r, double[] values) {
        int roundedX = (int) Math.round(x);
        int roundedY = (int) Math.round(y);
        int roundedR = (int) Math.round(r);
        for (int w = roundedX-roundedR; w < roundedX+roundedR; w++) {
            for (int h = roundedY-roundedR; h < roundedY + roundedR; h++) {
                if (insideCircle(w, h, x, y, r)) {
                    for (int i = 0; i < values.length; i++) {
                        img[w][h][i] = values[i];
                    }
                }
            }
        }
        return img;
    }

    private boolean insideCircle(int w, int h, double x, double y, double r) {
        return true;
    }
}
