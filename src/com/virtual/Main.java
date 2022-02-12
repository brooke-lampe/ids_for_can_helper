package com.virtual;

import tel.schich.javacan.*;
import tel.schich.javacan.util.CanBroker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Main {

    public static final NetworkDevice CAN_INTERFACE = lookupDev();
    public static HashMap<String, Set<String>> ATMAMap = new HashMap<>();
    public static ArrayList<String> ATMATrace = new ArrayList<>();
    public static ArrayList<String> currentIDs = new ArrayList<>();
    private static int counter = 0;
    private static int threshold = 10;
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

        HashMap<String, HashMap<String, Boolean>> parentMatrix = new HashMap<>();

        // Create a HashMap of all IDs with the values initialized to false
        // If we do not see them in the trace, they will remain false
        HashMap<String, Boolean> falseMatrix = new HashMap<>();
        for (String id : ATMASet) {
            falseMatrix.put(id, false);
        }

        HashMap<String, Boolean> childMatrix = new HashMap<>(falseMatrix);

        boolean checkNext = false;

        for (String currentId : ATMASet) {
            for (String id : ATMATrace) {
                if (currentId.equals(id)) {
                    checkNext = true;
                } else {
                    checkNext = false;
                }

                if (checkNext) {
                    // This ID was preceded by the current ID,
                    // meaning it is a valid transition and should be changed to true
                    childMatrix.put(id, true);
                }
            }

            // Add the child HashMap for this ID into the parent HashMap of all IDs
            parentMatrix.put(currentId, childMatrix);
            childMatrix = new HashMap<>(falseMatrix);
        }

        // Now that our matrix/profile, in the form of a HashMap of HashMaps, is generated
        // We need to save it as JSON
        //JSONObject jsonObject = new JSONObject(parentMatrix);

        profileMatrix = new boolean[6][6];
        profileMatrix[0][0] = true;
        profileMatrix[3][3] = true;

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
