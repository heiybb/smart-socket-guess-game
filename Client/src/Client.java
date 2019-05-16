import org.smartboot.socket.transport.AioQuickClient;
import org.smartboot.socket.transport.AioSession;
import org.smartboot.socket.transport.WriteBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * @author BOBIN YUAN s3677943@student.rmit.edu.au
 * <p>
 * Handle the input from user
 */
@SuppressWarnings("StatementWithEmptyBody")
class Client {
    /**
     * State record in the client side
     */
    static boolean PENDING = false;
    static boolean UNDEFINE = false;
    static boolean GAMING = false;

    /**
     * Auto Test function
     */
    static boolean AUTO_GUESS = false;
    private static boolean AUTO_REG = false;

    private static AioSession<String> clientAioSession;

    Client(String host, int port, boolean autoReg, boolean autoGuess) throws IOException {
        AUTO_REG = autoReg;
        AUTO_GUESS = autoGuess;

        AioQuickClient<String> client = new AioQuickClient<>(host, port,
                new StringProtocol(),
                new ClientMsgProcessor());

        try {
            clientAioSession = client.start();

            //auto register
            if (AUTO_REG) {
                //Wait for 1 sec to register
                long waitUntil = System.nanoTime() + (long) (1e9);
                while (waitUntil > System.nanoTime()) {
                }
                clientAioWrite("reg");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String msg;
            while ((msg = br.readLine()) != null) {
                //input check to send or not
                inputCheck(msg);
            }
        } catch (ExecutionException | InterruptedException e) {
            System.out.println(String
                    .format("Can't connect to server %s:%d", host, port));

            System.out.println("Close game now");
            System.exit(0);
        }
    }

    /**
     * Input check by the client state to save resource
     *
     * @param msg pre sent message to server
     */
    private static void inputCheck(String msg) {
        boolean rt = false;
        if (PENDING) {
            if ("q".equals(msg)) {
                rt = true;
            }
        } else if (UNDEFINE) {
            if ("q".equals(msg)) {
                rt = true;
            }
            if ("p".equals(msg)) {
                rt = true;
            }
        } else if (GAMING) {
            if ("e".equals(msg)) {
                rt = true;
            }
            if (msg.startsWith("g ")) {
                rt = true;
            }
            if ("q".equals(msg)) {
                rt = true;
            }
        } else {
            rt = "reg".equals(msg);
        }
        if (rt) {
            clientAioWrite(msg);
        } else {
            System.out.println("Input invalid");
        }
    }

    /**
     * Wrap the aioSession write method
     *
     * @param msg String message prepare to send
     */
    private static void clientAioWrite(String msg) {
        WriteBuffer writeBuffer = clientAioSession.writeBuffer();
        byte[] msgBody = msg.getBytes();
        byte[] msgHead = {(byte) msgBody.length};
        try {
            writeBuffer.write(msgHead);
            writeBuffer.write(msgBody);
            writeBuffer.flush();
        } catch (IOException e) {
            System.out.println("Error with Network IO");
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 4) {
                throw new IndexOutOfBoundsException();
            } else {
                String host = args[0];
                int port = Integer.parseInt(args[1]);
                String autoReg = args[2];
                String autoGuess = args[3];
                boolean reg = false;
                boolean guess = false;
                System.out.println("Initialize environment......");

                if ("t".equals(autoReg)) {
                    reg = true;
                    System.out.println("Auto register --> Enable");
                } else {
                    System.out.println("Auto register --> Disable");
                }

                if ("t".equals(autoGuess)) {
                    guess = true;
                    System.out.println("Auto guess --> Enable");
                } else {
                    System.out.println("Auto guess --> Disable");
                }
                System.out.println("Remote address --> " + host);
                System.out.println("Remote port --> " + port + "\n");

                new Client(host, port, reg, guess);
            }
        } catch (UnresolvedAddressException host) {
            System.out.println("Can't resolve the remote address");
        } catch
        (IOException io) {
            System.out.println("Error while creating aio session");
        } catch (IndexOutOfBoundsException e) {
            System.out.println("Args error");
        }
    }

    /**
     * Auto guess process
     */
    static void autoGuess() {
        ConcurrentHashMap<Integer, String> guessMap = new ConcurrentHashMap<>(20);
        guessMap.put(0, "g 0");
        guessMap.put(1, "g 1");
        guessMap.put(2, "g 2");
        guessMap.put(3, "g 3");
        guessMap.put(4, "g 4");
        guessMap.put(5, "g 5");
        guessMap.put(6, "g 6");
        guessMap.put(7, "g 7");
        guessMap.put(8, "g 8");
        guessMap.put(9, "g 9");
        guessMap.put(10, "g 58");
        guessMap.put(11, "e");
        guessMap.put(12, "g know what");
        guessMap.put(13, "e -13");
        guessMap.put(14, "reg -13");
        guessMap.put(15, "g -13");
        guessMap.put(16, "random inp");
        guessMap.put(17, "q -13");
        guessMap.put(18, "reg");
        guessMap.put(19, "g -13");
        List<String> guessed = new ArrayList<>(20);

        new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                String guess;
                do {
                    guess = guessMap.get(new Random().nextInt(guessMap.size()));
                } while (guessed.contains(guess));
                guessed.add(guess);
                String line = guess;
                System.out.println(line);
                inputCheck(line);
                long waitUntil = System.nanoTime() + (long) (0.5 * 1e9);
                while (waitUntil > System.nanoTime()) {
                }
            }
        }).start();
    }
}