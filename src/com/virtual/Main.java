package com.virtual;

import tel.schich.javacan.*;
import tel.schich.javacan.util.CanBroker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Executors;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static tel.schich.javacan.CanSocketOptions.*;

public class Main {

    public static final NetworkDevice CAN_INTERFACE = lookupDev();

    private static NetworkDevice lookupDev() {
        try {
            return NetworkDevice.lookup("vcan0");
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return null;
    }

    public static void sendFrameViaUtils(NetworkDevice device, CanFrame frame) throws IOException, InterruptedException {
        StringBuilder data = new StringBuilder();
        if (frame.isRemoteTransmissionRequest()) {
            data.append('R');
        }
        ByteBuffer buf = JavaCAN.allocateOrdered(CanFrame.MAX_FD_DATA_LENGTH);
        frame.getData(buf);
        buf.flip();
        while (buf.hasRemaining()) {
            data.append(String.format("%02X", buf.get()));
        }

        String idString;
        if (frame.isExtended()) {
            idString = String.format("%08X", frame.getId());
        } else {
            idString = String.format("%03X", frame.getId());
        }

        String textFrame;
        if (frame.isFDFrame()) {
            textFrame = String.format("%s##%X%s", idString, frame.getFlags(), data);
        } else {
            textFrame = String.format("%s#%s", idString, data);
        }
        final ProcessBuilder cansend = new ProcessBuilder("cansend", device.getName(), textFrame);
        cansend.redirectError(ProcessBuilder.Redirect.INHERIT);
        cansend.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        cansend.redirectInput(ProcessBuilder.Redirect.INHERIT);
        final Process proc = cansend.start();
        int result = proc.waitFor();
        if (result != 0) {
            throw new IllegalStateException("Failed to use cansend to send a CAN frame!");
        }
    }

    public static void main(String[] args) {
        System.out.println("Hello, World!");

        try (final RawCanChannel socket = CanChannels.newRawChannel()) {
            socket.bind(CAN_INTERFACE);

            socket.setOption(LOOPBACK, false);
            socket.setOption(LOOPBACK, true);

            socket.setOption(LOOPBACK, true);
            socket.setOption(RECV_OWN_MSGS, true);
            socket.setOption(RECV_OWN_MSGS, false);

            socket.setOption(FD_FRAMES, true);
            socket.setOption(FD_FRAMES, false);

            socket.setOption(JOIN_FILTERS, true);
            socket.setOption(JOIN_FILTERS, false);

            socket.setOption(ERR_FILTER, 0xFF);

            final Duration readTimeout = ofSeconds(1);
            socket.setOption(SO_RCVTIMEO, readTimeout);

            // precision below seconds is not guaranteed
            Duration writeTimeout = ofMillis(1100);
            socket.setOption(SO_SNDTIMEO, writeTimeout);

            int newReceiveBufferSize = 2048;
            int oldReceiveBufferSize = socket.getOption(SO_RCVBUF) / 2;
            socket.setOption(SO_RCVBUF, newReceiveBufferSize);
            socket.setOption(SO_RCVBUF, oldReceiveBufferSize);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (final RawCanChannel socket = CanChannels.newRawChannel()) {
            socket.bind(CAN_INTERFACE);

            CanFilter[] input = {
                    new CanFilter(0x123, 0x234)
            };

            socket.setOption(FILTER, input);

            CanFilter[] output = socket.getOption(FILTER);

            System.out.println(output);
        } catch (Exception e) {
            e.printStackTrace();
        }

        CanBroker canBroker = null;
        try {
            canBroker = new CanBroker(Executors.defaultThreadFactory(), Duration.ofMillis(10));
            canBroker.addDevice(CAN_INTERFACE, (ch, frame) -> System.out.println("Frame " + frame));
            canBroker.addFilter(CanFilter.ANY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Goodbye, World!");
    }
}
