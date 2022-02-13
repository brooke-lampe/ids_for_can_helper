package com.virtual;

import tel.schich.javacan.*;
import tel.schich.javacan.util.CanBroker;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {

    public static final NetworkDevice CAN_INTERFACE = lookupDev();
    public static HashMap<String, Set<String>> ATMAMap = new HashMap<>();
    public static ArrayList<String> ATMATrace = new ArrayList<>();
    public static ArrayList<String> currentIDs = new ArrayList<>();
    private static int counter = 0;
    private static int threshold = 1000000;
    public static String[] ATMAOrder = null;
    public static boolean profileMatrix[][] = null;
    public static boolean trainingMode = false;
    public static boolean IDSMode = false;
    public static int anomalyCounter = 0;
    public static int anomalyThreshold = 10;
    public static int healthyCounter = 0;
    public static int healthyThreshold = 2000;
    public static ArrayList<String> anomalyTrace = new ArrayList<>();
    public static double anomalyRatio = 0;
    public static double healthyRatio = 0;
    public static double ratioThreshold = 0.9;
    public static double totalTrafficThreshold = 2000;

    private static final int invalid_id_alert = 1;
    private static final int invalid_id_sequence_alert = 2;

    private static NetworkDevice lookupDev() {
        try {
            return NetworkDevice.lookup("vcan0");
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println("Connect vcan0");

        trainingMode = true;

        CanBroker canBroker = null;
        try {
            canBroker = new CanBroker(Executors.defaultThreadFactory(), Duration.ofMillis(10));
            canBroker.addDevice(CAN_INTERFACE, (ch, frame) -> {
                if (currentIDs.size() > 10) {
                    currentIDs = new ArrayList<>();
                }

                String frame_all = frame.toString();
                //System.out.println(frame_all);
                String[] frame_elements = frame_all.split(",", 4);
                String frame_id = frame_elements[0];
                String frame_data = frame_elements[3];
                frame_id = frame_id.split("=")[1];
                frame_data = frame_data.split("=")[1];
                frame_data = frame_data.replaceAll("[\\[\\],)]", "");
                //System.out.println("frame_id: " + frame_id + ", frame_data: " + frame_data);

                Set<String> data;
                if (ATMAMap.containsKey(frame_id)) {
                    data = ATMAMap.get(frame_id);
                } else {
                    data = new HashSet<>();
                }
                data.add(frame_data);
                ATMAMap.put(frame_id, data);

                //System.out.println("ATMAMap");
                //System.out.println(ATMAMap);

                ATMATrace.add(frame_id);
                //System.out.println("in main() -- ATMATrace");
                //System.out.println(ATMATrace);

                currentIDs.add(frame_id);
                //System.out.println("in main() -- currentIDs");
                //System.out.println(currentIDs);

                counter++;
                if (counter % 10000 == 0) {
                    System.out.println("Counter: " + counter);
                }

                //System.out.println("trainingMode: " + trainingMode + ", IDSMode: " + IDSMode);

                if (trainingMode && counter >= threshold) {
                    createMatrix();
                }
                if (IDSMode && currentIDs.size() > 10) {
                    idsDetect();
                }
            });
            canBroker.addFilter(CanFilter.ANY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Disconnect vcan0");
    }

    public static void createMatrix() {
        // Create the matrix/profile for this vehicle, which enables the IDS to function
        System.out.println("Creating the matrix...");

        System.out.println("ATMATrace.length -- " + ATMATrace.size());

        //System.out.println("in createMatrix() -- ATMATrace");
        //System.out.println(ATMATrace);

        // HashSet removes duplicates
        // then back to ArrayList
        // then to primitive Array (for performance)
        // then sort (for performance)
        HashSet<String> ATMASet = new HashSet<>(ATMATrace);
        ArrayList<String> uniqueATMA = new ArrayList(ATMASet);
        ATMAOrder = uniqueATMA.toArray(new String[uniqueATMA.size()]);
        Arrays.sort(ATMAOrder);

        profileMatrix = new boolean[ATMAOrder.length][ATMAOrder.length];

        // This is an example of the profileMatrix
        // "ATMAOrder" is the order for both the rows and the columns
        // the row is the previous ID
        // the column is any subsequent ID that can follow the previous ID in attack-free traffic

        //    0  1  2  3
        // 0  f  f  f  t
        // 1  f  f  t  f
        // 2  f  t  f  f
        // 3  t  f  f  f

        boolean checkNext = false;

        for (int i = 0; i < ATMAOrder.length; i++) {
            String currentId = ATMAOrder[i];

            for (String id : ATMATrace) {
                if (checkNext) {
                    // This ID was preceded by the current ID,
                    // meaning it is a valid transition and should be changed to true

                    // We know "i" is the row because we are iterating by ATMAOrder
                    // and we can find "j" for the column using binarySearch, since we sorted the array
                    int j = Arrays.binarySearch(ATMAOrder, id);
                    profileMatrix[i][j] = true;
                }

                if (currentId.equals(id)) {
                    checkNext = true;
                } else {
                    checkNext = false;
                }
            }
        }

        // BEGIN -- PRINTING FOR VALIDATION / DEBUGGING
        // **
        System.out.println("ATMAOrder.length -- " + ATMAOrder.length);
        System.out.println("profileMatrix.length -- " + profileMatrix.length + ", profileMatrix.width -- " + profileMatrix[0].length);

        System.out.println("in createMatrix() -- ATMAOrder");
        System.out.printf("%-10s", "");
        for (int i = 0; i < ATMAOrder.length; i++) {
            System.out.printf("%-10s", ATMAOrder[i]);
        }
        System.out.println();

        System.out.println("in createMatrix() -- profileMatrix");
        for (int i = 0; i < ATMAOrder.length; i++) {
            System.out.printf("%-10s", ATMAOrder[i]);
            for (int j = 0; j < ATMAOrder.length; j++) {
                System.out.printf("%-10s", profileMatrix[i][j]);
            }
            System.out.println();
        }
        // **
        // END -- PRINTING FOR VALIDATION / DEBUGGING

        trainingMode = false;
        IDSMode = true;

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void idsDetect() {
        // Use the matrix/profile to check current traffic,
        // update false positives, and raise alerts

        //System.out.println("In idsDetect");

        if (currentIDs.isEmpty()) {
            // No data received; no alert
            System.out.println("Error: No data received!");
            return;
        }

        // We need to check each pair of adjacent IDs in currentIDs
        // if pair (i, i + 1) is a valid transition (true), we do nothing
        // if pair (i, i + 1) is not a valid transition (false), we update the anomaly counter
        // When the anomaly counter reaches the anomaly threshold, we raise an alert

        //System.out.println("currentIDs.length -- " + currentIDs.size());

        for (int i = 0; i < currentIDs.size() - 1; i++) {
            String prevID = currentIDs.get(i);
            String nextID = currentIDs.get(i + 1);

            int row = Arrays.binarySearch(ATMAOrder, prevID);
            int col = Arrays.binarySearch(ATMAOrder, nextID);
            if (row < 0) {
                System.out.println("This is an anomaly: This ID is not valid");
                System.out.println("prevID: " + prevID);

                // Given the size of our trace, we would never expect a previously unknown ECU to start transmitting
                // As such, we expect an unknown identifier to indicate an attack
                sendNotification(invalid_id_alert);
            } else if (col < 0) {
                    System.out.println("This is an anomaly: This ID is not valid");
                    System.out.println("nextID: " + nextID);

                    // Given the size of our trace, we would never expect a previously unknown ECU to start transmitting
                    // As such, we expect an unknown identifier to indicate an attack
                    sendNotification(invalid_id_alert);
            } else if (!profileMatrix[row][col]) {
                System.out.println("This is an anomaly: This sequence is not valid");
                System.out.println("prevID: " + prevID + ", nextID: " + nextID);
                if (false) {
                //if (counter >= 2000000) {
                    System.out.println("in idsDetect() -- ATMAOrder");
                    System.out.printf("%-10s", "");
                    for (int x = 0; x < ATMAOrder.length; x++) {
                        System.out.printf("%-10s", ATMAOrder[x]);
                    }
                    System.out.println();

                    System.out.println("in idsDetect() -- profileMatrix");
                    for (int x = 0; x < ATMAOrder.length; x++) {
                        System.out.printf("%-10s", ATMAOrder[x]);
                        for (int y = 0; y < ATMAOrder.length; y++) {
                            System.out.printf("%-10s", profileMatrix[x][y]);
                        }
                        System.out.println();
                    }
                }
                anomalyTrace.add(prevID);
                anomalyTrace.add(nextID);
                anomalyCounter++;
                anomalyRatio++;
            } else {
                //System.out.println("This is normal");
                //System.out.println("prevID: " + prevID + ", nextID: " + nextID);
                healthyCounter++;
                healthyRatio++;
            }
        }

        if (anomalyCounter >= anomalyThreshold) {
            sendNotification(invalid_id_sequence_alert);
        }

        // If we have an extended period of healthy traffic,
        // then previous suspicious traffic may have been false positives
        // and we should update the matrix
        // so that we do not see the same false positives
        if (healthyCounter >= healthyThreshold) {
            System.out.println("healthyThreshold reached, updating matrix...");

            // We are going to perform the matrix update, we need to reset the healthyCounter
            healthyCounter = 0;
            updateMatrix();
        }

        // If we have mostly healthy traffic and very few anomalies
        // then the anomalies may have been false positives, and we can update the matrix accordingly
        // We don't want to update too often, so we will check when totalTraffic reaches totalTrafficThreshold
        double totalTraffic = anomalyRatio + healthyRatio;
        double percentHealthyTraffic = healthyRatio / totalTraffic;
        if (totalTraffic > totalTrafficThreshold && percentHealthyTraffic > ratioThreshold) {
            System.out.println("ratioThreshold reached, updating matrix...");
            anomalyRatio = 0;
            healthyRatio = 0;
            updateMatrix();
        }
    }

    public static void updateMatrix() {
        // If we see very few anomalies over a significant period,
        // then the "anomalies" are probably false positives
        // We should record them so that we can update the matrix if have not hit the anomaly threshold
        // (We do not think the "anomalies" were part of an attack--we think they were false positives)

        // We need to iterate by pairs, because each pair is part of the trace,
        // but it is not associated with the next pair
        for (int i = 0; i < anomalyTrace.size() - 1; i += 2) {
            String prevId = anomalyTrace.get(i);
            int row = Arrays.binarySearch(ATMAOrder, prevId);

            String nextId = anomalyTrace.get(i + 1);
            int col = Arrays.binarySearch(ATMAOrder, nextId);

            profileMatrix[row][col] = true;
        }

        anomalyTrace = new ArrayList<>();
    }

    public static void sendNotification(int alert_type) {
        System.out.println("***** ALERT! Suspicious traffic detected! *****");

        switch (alert_type) {
            case invalid_id_alert:
                System.out.println("***** Invalid messages have been detected. This may indicate a bus error or an attack. *****");
                break;
            case invalid_id_sequence_alert:
                System.out.println("***** Unusual patterns of messages have been detected. This may be the result of unusual activity, or it may indicate an attack. *****");
                break;
            default:
                System.out.println("***** Unknown. *****");
        }

        anomalyCounter = 0;

        // We've encountered suspicious traffic, so we need to reset the healthyCounter
        healthyCounter = 0;

        // We've encountered suspicious traffic, so we need to remove the anomalies
        // because we think they are suspicious traffic, not false positives
        anomalyTrace = new ArrayList<>();
    }
}
