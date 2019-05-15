import org.smartboot.socket.transport.AioQuickClient;
import org.smartboot.socket.transport.AioSession;
import org.smartboot.socket.transport.WriteBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
    static boolean AUTO_GUESS = false;
    private static boolean AUTO_REG = false;
    private static AioSession<String> clientAioSession;

    private Client(String host, int port) throws IOException {
        AioQuickClient<String> client = new AioQuickClient<>(host, port,
                new StringProtocol(),
                new ClientMsgProcessor());

        try {
            clientAioSession = client.start();

            //auto register
            if (AUTO_REG) {
                long waitUntil = System.nanoTime() + (long) (1e9);
                while (waitUntil > System.nanoTime()) {
                }
                clientAioWrite("reg");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String msg;
            while ((msg = br.readLine()) != null) {
                clientAioWrite(msg);
            }
        } catch (ExecutionException | InterruptedException e) {
            System.out.println(String
                    .format("Can't connect to server %s:%d", host, port));

            System.out.println("Close game now");
            System.exit(0);
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
                System.out.println("Initialize environment......");
                if ("t".equals(autoReg)) {
                    AUTO_REG = true;
                    System.out.println("Auto register --> Enable");
                } else {
                    System.out.println("Auto register --> Disable");
                }

                if ("t".equals(autoGuess)) {
                    AUTO_GUESS = true;
                    System.out.println("Auto guess --> Enable");
                } else {
                    System.out.println("Auto guess --> Disable");
                }
                System.out.println("Remote address --> " + host);
                System.out.println("Remote port --> " + port + "\n");

                new Client(host, port);
            }
        } catch (IOException io) {
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
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {

        }
        new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                String guess;
                do {
                    guess = guessMap.get(new Random().nextInt(guessMap.size()));
                } while (guessed.contains(guess));
                guessed.add(guess);
                String line = guess;
                System.out.println(line);
                clientAioWrite(line);
                long waitUntil = System.nanoTime() + (long) (0.5 * 1e9);
                while (waitUntil > System.nanoTime()) {
                }
            }
        }).start();
    }
}