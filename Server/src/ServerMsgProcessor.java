import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.socket.MessageProcessor;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.transport.AioSession;
import org.smartboot.socket.transport.WriteBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * @author BOBIN YUAN s3677943@student.rmit.edu.au
 * <p>
 * Handle the message and logic between the clients of communication
 */

public class ServerMsgProcessor implements MessageProcessor<String>, Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ServerMsgProcessor.class);

    private static final int GAME_POOL_LIMIT = 3;
    private static final int ATTEMPT_MAX = 4;
    private static final int MAX = 9;
    private static final int MIN = 0;
    private static final int AFK_TIMEOUT = 30;

    private static final String REG_CMD = "reg";
    private static final String GUESS_CMD = "g";
    private static final String ESCAPE_CMD = "e";
    private static final String PLAY_CMD = "p";
    private static final String QUIT_CMD = "q";

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(50);
    private static final ConcurrentHashMap<AioSession<String>, SessionProfile> SESSION_POOL = new ConcurrentHashMap<>();
    private static ConcurrentLinkedQueue<AioSession<String>> PENDING_QUEUE = new ConcurrentLinkedQueue<>();
    private static ConcurrentLinkedQueue<AioSession<String>> GAME_QUEUE = new ConcurrentLinkedQueue<>();
    private static ConcurrentLinkedQueue<AioSession<String>> UNDEFINED_QUEUE = new ConcurrentLinkedQueue<>();

    ServerMsgProcessor() {
        executorService.scheduleAtFixedRate(this, 100, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Process will handle all the message come from client
     *
     * @param session Income session
     * @param msg     String message
     */
    @Override
    public void process(AioSession<String> session, String msg) {
        try {
            //Update the last input time stamp
            updateLastInput(session);

            if (REG_CMD.equals(msg)) {
                registerHandler(session);
            } else if (msg.startsWith(GUESS_CMD)) {
                guessHandler(session, msg);
            } else if (ESCAPE_CMD.equals(msg)) {
                escapeHandler(session);
            } else if (PLAY_CMD.equals(msg)) {
                playAgainHandler(session);
            } else if (QUIT_CMD.equals(msg)) {
                quitHandler(session);
            } else {
                throw new InvalidInputException("unrecognized command");
            }
        } catch (InvalidInputException iie) {
            aioWrite(session, iie.getMessage());
        }
    }

    /**
     * Handle the session state event between the client and server
     *
     * @param session          Income aio session
     * @param stateMachineEnum Smart-socket state
     * @param throwable        any throwable exception
     */
    @Override
    public void stateEvent(AioSession<String> session,
                           StateMachineEnum stateMachineEnum, Throwable throwable) {
        switch (stateMachineEnum) {
            case NEW_SESSION:
                SESSION_POOL.put(session, new SessionProfile());
                aioWrite(session, "Welcome to the Number Guess Game");
                aioWrite(session, "Type reg <Name> to register to the game" +
                        "\nType g <Number> to guess a number" +
                        "\nType q to quit the game anytime");
                break;
            case SESSION_CLOSED:
                PENDING_QUEUE.remove(session);
                GAME_QUEUE.remove(session);
                UNDEFINED_QUEUE.remove(session);
                logger.info("{} quit the game", SESSION_POOL.get(session).getPlayerName());
                SESSION_POOL.remove(session);
                break;
            default:
                break;
        }
    }

    /**
     * Wrap the WriteBuffer write method
     *
     * @param session Refers to the client AioSession
     * @param msg     Message that need to send
     */
    private static void aioWrite(AioSession<String> session, String msg) {
        byte[] response = msg.getBytes();
        byte[] head = {(byte) response.length};
        WriteBuffer outputStream = session.writeBuffer();
        try {
            outputStream.write(head);
            outputStream.write(response);
            outputStream.flush();
        } catch (IOException e) {
            logger.error("Error with aio write");
        }
    }

    /**
     * Handle the register command scenario
     *
     * @param session Refers to the AioSession of client
     * @throws InvalidInputException will be handled by upper class
     */
    private void registerHandler(AioSession<String> session) throws InvalidInputException {
        if (SESSION_POOL.get(session).isReg()) {
            throw new InvalidInputException("reg command is currently unavailable");
        } else {
            //Split to get the valid player name
            String playerName = UUID.randomUUID()
                    .toString().toUpperCase().substring(25, 33);
            SESSION_POOL.get(session).setPlayerName(playerName);
            //transit to PENDING
            SESSION_POOL.get(session).setReg(true);
            PENDING_QUEUE.offer(session);
            aioWrite(session, String.format("Register as %s," +
                            " please wait for game round," +
                            " %d players ahead of you",
                    playerName, PENDING_QUEUE.size() - 1));

            try {
                logger.info("IPP INFO: {} -> Player {} registered successful",
                        session.getRemoteAddress(),
                        playerName);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Handle the guess command scenario
     *
     * @param session Refers to the AioSession of client
     * @param msg     The message client send
     * @throws InvalidInputException will be handled by upper class
     */
    private void guessHandler(AioSession<String> session, String msg)
            throws InvalidInputException {
        if (GAME_QUEUE.contains(session)) {
            if (SESSION_POOL.get(session).isFinish()) {
                throw new InvalidInputException("you have already finished guess");
            } else {
                try {
                    int clientGuessNum = Integer.parseInt(msg.split(" ")[1]);
                    if (clientGuessNum < MIN || clientGuessNum > MAX) {
                        throw new InvalidInputException("invalid number range");
                    } else {
                        logger.info("Player {} guess {}",
                                SESSION_POOL.get(session).getPlayerName(),
                                clientGuessNum);

                        guessNumCompare(session, clientGuessNum);
                    }
                } catch (NumberFormatException nfe) {
                    throw new InvalidInputException("g command is currently unavailable");
                }
            }
        } else {
            throw new InvalidInputException("g command is currently unavailable");
        }
    }

    /**
     * @param session        Refers to the AioSession of client current guessing
     * @param clientGuessNum The number that client guess
     */
    private void guessNumCompare(AioSession<String> session, int clientGuessNum) {
        SessionProfile tmpPair = SESSION_POOL.get(session);
        int targetNum = tmpPair.getGuessTarget();
        if (tmpPair.getGuessCount() < ATTEMPT_MAX) {
            if (clientGuessNum == targetNum) {
                tmpPair.countIncrement();
                tmpPair.setFinish(true);
                tmpPair.setGotCorrect(true);
                aioWrite(session,
                        String.format("Congratulation <%d> is correct", clientGuessNum));
            } else {
                if (clientGuessNum > targetNum) {
                    tmpPair.countIncrement();
                    aioWrite(session,
                            String.format("Your guess <%d> is too high", clientGuessNum));
                }
                if (clientGuessNum < targetNum) {
                    tmpPair.countIncrement();
                    aioWrite(session,
                            String.format("Your guess <%d> is too low", clientGuessNum));
                }
                if (tmpPair.getGuessCount() == ATTEMPT_MAX) {
                    aioWrite(session, "You have ran out of the guess try");
                    tmpPair.setFinish(true);
                }
            }
        } else {
            aioWrite(session, "You have ran out of the guess try");
        }
    }

    /**
     * Handle the escape command scenario
     *
     * @param session Refers to the AioSession of client
     * @throws InvalidInputException will be handled by upper class
     */
    private void escapeHandler(AioSession<String> session) throws InvalidInputException {
        if (GAME_QUEUE.contains(session)) {
            GAME_QUEUE.remove(session);
            String escapePlayer = SESSION_POOL.get(session).getPlayerName();

            //Info the rest of in game players the escape situation
            for (AioSession<String> aioSession : GAME_QUEUE) {
                aioWrite(aioSession, String.format("%s quit this guess round",
                        escapePlayer));
            }
            //Transit the escaped player to the UNDEFINED_QUEUE
            UNDEFINED_QUEUE.offer(session);
            logger.info("Player {} choose to escape the guess", escapePlayer);

            aioWrite(session, "Type p to play again or type q to quit");
        } else {
            throw new InvalidInputException("e command is currently unavailable");
        }
    }

    /**
     * Handle the play again command scenario
     *
     * @param session Refers to the AioSession of client
     * @throws InvalidInputException will be handled by upper class
     */
    private void playAgainHandler(AioSession<String> session) throws InvalidInputException {
        if (UNDEFINED_QUEUE.contains(session)) {
            logger.info("Player {} choose to play again",
                    SESSION_POOL.get(session).getPlayerName());
            aioWrite(session, "You are added to the pending queue now");
            //Refresh the profile
            SESSION_POOL.get(session).refresh();
            //add the player to the end of the queue
            PENDING_QUEUE.offer(session);
        } else {
            throw new InvalidInputException("p command is currently unavailable");
        }
    }

    /**
     * Handle the quit command scenario
     *
     * @param session Refers to the AioSession of client
     */
    private void quitHandler(AioSession<String> session) {
        session.close();
    }

    /**
     * Fixed Rate check the client input
     * <Strong>IMPORTANT!<Strong/>
     * Only the clients not in the pending queue will be check
     * Compare them to validate if the client is AFK
     * If the AFK time beyond limit then kick out the client
     */
    @Override
    public void run() {
        SESSION_POOL.forEach((aioSession, sessionProfile) -> {
            if (!PENDING_QUEUE.contains(aioSession) &&
                    System.nanoTime() - sessionProfile.getLastInput()
                            >= (long) (AFK_TIMEOUT * 1e9)) {

                logger.info("{} AFK {} seconds, force remove",
                        sessionProfile.getPlayerName(), AFK_TIMEOUT);

                aioWrite(aioSession,
                        String.format("AFK %d seconds, force remove", AFK_TIMEOUT));

                aioSession.close();
            }
        });
    }

    /**
     * Customized Exception that refers to the invalid input of client
     */
    static class InvalidInputException extends Exception {
        InvalidInputException(String err) {
            super("Invalid Input --> " + err);
        }
    }

    /**
     * @return If any one of the player haven't finished return false
     */
    static boolean finishCheck() {
        AtomicBoolean f = new AtomicBoolean(true);
        GAME_QUEUE.forEach(aioSession -> {
            if (aioSession != null && !SESSION_POOL.get(aioSession).isFinish()) {
                f.set(false);
            }
        });
        return f.get();
    }

    /**
     * Transit the players from GAME_QUEUE to UNDEFINE_QUEUE
     * <p/>
     * This function is called when a round end
     */
    static void transitGameToUndefine() {
        AioSession<String> aioSession;
        while ((aioSession = GAME_QUEUE.poll()) != null) {
            updateLastInput(aioSession);
            UNDEFINED_QUEUE.offer(aioSession);
            aioWrite(aioSession, "---> Type p to play again or type q to quit <---");
        }
    }

    /**
     * Transit the players from PENDING_QUEUE to GAME_QUEUE
     * <p/>
     * This function is called when start a round
     */
    static void transitPendingToGame() {
        for (int i = 0; i < GAME_POOL_LIMIT; i++) {
            AioSession<String> aioSession = PENDING_QUEUE.poll();
            if (aioSession != null) {
                updateLastInput(aioSession);
                String playerName = SESSION_POOL.get(aioSession).getPlayerName();
                GAME_QUEUE.offer(aioSession);
                logger.info("Add the player {} to the current round game queue", playerName);
            }
        }
    }

    /**
     * Assign the target guess number to the players in the GAME_QUEUE
     * <p/>
     * This function is called after the transition of PENDING TO GAME
     */
    static void assignTargetForGame(int target) {
        GAME_QUEUE.forEach(aioSession -> SESSION_POOL.get(aioSession).setGuessTarget(target));
    }

    /**
     * @param aioSession Session that need to update the last input time
     *                   <p>
     *                   This method must be called in every transition
     */
    private static void updateLastInput(AioSession<String> aioSession) {
        SESSION_POOL.get(aioSession).setLastInput(System.nanoTime());
    }

    /**
     * Wrap all he player name into string line
     */
    private static String getAllPlayerName() {
        StringBuilder playerNameLine = new StringBuilder();
        GAME_QUEUE.forEach(playerSession -> playerNameLine.append(SESSION_POOL
                .get(playerSession)
                .getPlayerName()).append(", "));
        //Remove the last comma
        playerNameLine.delete(playerNameLine.length() - 2, playerNameLine.length());
        return playerNameLine.toString();
    }


    /**
     * Send announcement to players in the GAME_QUEUE
     */
    static void roundStartAnnouncement() {
        String participants = getAllPlayerName();
        GAME_QUEUE.forEach(playerSession -> new Thread(() -> {
            aioWrite(playerSession,
                    "This round players: " + participants);
            aioWrite(playerSession,
                    "Game round start, you have 4 chances to guess");
        }).start());
    }

    /**
     * Print the rank board in format( The correct answer is also provided)
     * <p>
     * If there all the players choose to escape or quit or disconnect
     * No rank info will be printed
     */
    static void roundRankBoard(int target) {
        if (GAME_QUEUE.size() == 0) {
            logger.info("Oops, players all escaped, rank board unavailable");
        } else {
            List<String> rankBoard = new ArrayList<>(5);
            rankBoard.add(String.format("Correct Answer: %d", target));
            rankBoard.add(String.format("%-14s%-15s%s",
                    "[Rank]", "[Player]", "[Attempts]"));

            List<SessionProfile> rank = GAME_QUEUE
                    .stream().map(SESSION_POOL::get)
                    .filter(SessionProfile::isGotCorrect)
                    //From smaller to greater
                    .sorted(Comparator.comparingInt(SessionProfile::getGuessCount))
                    .collect(Collectors.toList());

            AtomicInteger rankNum = new AtomicInteger(1);
            rank.forEach(player -> rankBoard.add(String.format(" No.%-10d%-19s%d",
                    rankNum.getAndIncrement(), player.getPlayerName(),
                    player.getGuessCount())));

            if (rankBoard.size() == 2) {
                rankBoard.add("No one get the correct answer");
            }
            rankBoard.forEach(logger::info);

            GAME_QUEUE.forEach(aioSession ->
                    rankBoard.forEach(rl ->
                            aioWrite(aioSession, rl)));
        }
    }

    /**
     * @return return if pending queue is empty or not
     */
    static boolean isPendingEmpty() {
        return PENDING_QUEUE.size() == 0;
    }

}