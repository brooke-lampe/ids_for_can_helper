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
    private static int threshold = 10;
    public static String[] ATMAOrder = null;
    public static boolean profileMatrix[][] = null;
    public static boolean trainingMode = false;
    public static boolean IDSMode = false;

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
                currentIDs = new ArrayList<>();

                String frame_all = frame.toString();
                //System.out.println(frame_all);
                String[] frame_elements = frame_all.split(",", 4);
                String frame_id = frame_elements[0];
                String frame_data = frame_elements[3];
                frame_id = frame_id.split("=")[1];
                frame_data = frame_data.split("=")[1];
                frame_data = frame_data.replaceAll("[\\[\\],)]", "");
                System.out.println("frame_id: " + frame_id + ", frame_data: " + frame_data);

                Set<String> data;
                if (ATMAMap.containsKey(frame_id)) {
                    data = ATMAMap.get(frame_id);
                } else {
                    data = new HashSet<>();
                }
                data.add(frame_data);
                ATMAMap.put(frame_id, data);

                System.out.println("ATMAMap");
                System.out.println(ATMAMap);

                ATMATrace.add(frame_id);
                System.out.println("in main() -- ATMATrace");
                System.out.println(ATMATrace);

                currentIDs.add(frame_id);
                System.out.println("in main() -- currentIDs");
                System.out.println(currentIDs);

                counter++;
                System.out.println("Counter: " + counter);

                if (trainingMode && counter >= threshold) {
                    createMatrix();
                }
                if (IDSMode) {
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

        // Efficient algorithm to find all unique elements of the ArrayList--how about a HashSet?
        // Then, for each element in the HashSet,
        // iterate over the trace, and add any ID that follows the current ID into the matrix as true (default is false)
        // Done!

        // Can't be a matrix
        // How about an ArrayList of IDs, and for each ID,
        // we have a key, value dictionary
        // where the key is the ID, and the value is true/false, depending on if it is valid or not

        // Alternatively, if we still want a matrix, we can save an ArrayList (or something)
        // of the proper order of rows and columns
        // Each time we encounter an ID, we find its position in the ArrayList (or String array[])
        // And update the same position in the true/false matrix

        // Which is more efficient? Checking an array for the position of the currentID,
        // then the position of the ID-to-check?
        // Or a HashMap where we find the currentID in the outer HashMap,
        // followed by the ID-to-check in the inner HashMap?

        System.out.println("in createMatrix() -- ATMATrace");
        System.out.println(ATMATrace);

        HashSet<String> ATMASet = new HashSet<>(ATMATrace);
        ArrayList<String> uniqueATMA = new ArrayList(ATMASet);
        ATMAOrder = uniqueATMA.toArray(new String[uniqueATMA.size()]);
        Arrays.sort(ATMAOrder);

        profileMatrix = new boolean[ATMAOrder.length][ATMAOrder.length];
        //profileMatrix[0][0] = true;
        //profileMatrix[3][3] = true;

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

            // Because we're iterating through ATMAOrder,
            // the current index is the index we would find by binary search
            //int row = Arrays.binarySearch(ATMAOrder, currentId);

            for (String id : ATMATrace) {
                if (checkNext) {
                    // This ID was preceded by the current ID,
                    // meaning it is a valid transition and should be changed to true
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

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        trainingMode = false;
    }

    public static void idsDetect() {
        // Use the matrix/profile to check current traffic,
        // update false positives,
        // and raise alerts

        if (currentIDs.isEmpty()) {
            // No data received; no alert
            return;
        }

        if (false) {
            System.out.println("***** ALERT! Suspicious traffic detected! *****");
        }
    }
}
