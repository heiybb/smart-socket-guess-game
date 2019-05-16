import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.socket.transport.AioQuickServer;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author BOBIN YUAN s3677943@student.rmit.edu.au
 * Guess game server
 * Handle some limit
 */
@SuppressWarnings("ALL")
public class Server {
    private static final int PLAYER_LIMIT = 3;
    private static final int MAX_ROUND = 50;

    private static final int WAIT_JOIN_INTERVAL = 15;
    private static final int ROUND_TIMEOUT = 35;

    private static int roundCounter = 1;
    private static boolean GAME_STATUS = false;
    private static final int MAX = 9;
    private static final int MIN = 0;
    private static int TARGET;
    private static final String BANNER = "GUESS GAME VER 1.0";

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    public static void main(String[] args) {
        logger.info(BANNER);
        AioQuickServer<String> server = new AioQuickServer<>(
                "0.0.0.0",
                51900,
                new StringProtocol(),
                new ServerMsgProcessor());
        try {
            server.start();
            roundLoop();
        } catch (IOException e) {
            logger.error("Error when try to start the server");
        } finally {
            server.shutdown();
        }
    }

    /**
     * Limit the virtual round to MAX_ROUND
     */
    private static void roundLoop() {
        while (!GAME_STATUS && roundCounter <= MAX_ROUND) {
            //If there is no players in pending then try to wait for join for 20s
            if (ServerMsgProcessor.isPendingEmpty()) {
                logger.warn("Players not enough, wait for {} seconds", WAIT_JOIN_INTERVAL);
                busyWaitSec(WAIT_JOIN_INTERVAL);
            }

            //Recheck the the PENDING_QUEUE if not null then start round
            if (!ServerMsgProcessor.isPendingEmpty()) {
                //Generate the target guess number
                TARGET = new Random().nextInt(MAX - MIN + 1) + MIN;

                logger.info("-- Round {} generated, Target Number: {} --",
                        roundCounter++,
                        TARGET);
                //Transit the PENDING players to GAME
                ServerMsgProcessor.transitPendingToGame();
                //Assign the same target to every player
                ServerMsgProcessor.assignTargetForGame(TARGET);

                roundProcess(ROUND_TIMEOUT);
            } else {
                logger.info("No player enter the game, start next round check");
            }
        }
    }

    /**
     * This counter is used to count the time in every round
     */
    static class RoundTimer extends Thread {
        private AtomicInteger count;
        private int limit;

        int getCount() {
            return count.get();
        }

        RoundTimer(int limit) {
            this.count = new AtomicInteger(0);
            this.limit = limit;
        }

        @Override
        public void run() {
            for (int i = 0; i < limit; i++) {
                try {
                    busyWaitSec(1);
                    count.getAndIncrement();
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Blocking wait method
     * Use nano time to ensure it is accurate
     * As Thread.sleep() only has 75% accuracy :)
     *
     * @param sec could be long value
     */
    private static void busyWaitSec(long sec) {
        long waitUntil = System.nanoTime() + (sec * (long) (1e9));
        while (waitUntil > System.nanoTime()) {
            ;
        }
    }

    /**
     * Start a new round for players with a specific time out setting
     */
    private static void roundProcess(int timeoutSec) {
        GAME_STATUS = true;

        //Send announcement to players
        ServerMsgProcessor.roundStartAnnouncement();

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        RoundTimer timeCounter = new RoundTimer(timeoutSec);

        executorService.submit(timeCounter);
        while (true) {
            if (timeCounter.getCount() == timeoutSec) {
                logger.info("Round time limit reached, end this round");
                break;
            }
            if (ServerMsgProcessor.finishCheck()) {
                logger.info("All players finish the game in advance, end this round");
                break;
            }
        }
        executorService.shutdown();

        //Print and send the rank board info
        ServerMsgProcessor.roundRankBoard(TARGET);
        //Transit the players from GAME_QUEUE to the UNDEFINED_QUEUE
        ServerMsgProcessor.transitGameToUndefine();
        GAME_STATUS = false;
    }
}