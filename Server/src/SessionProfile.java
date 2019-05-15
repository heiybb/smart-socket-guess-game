import lombok.Getter;
import lombok.Setter;

/**
 * Wrap the playerName guessCount and target number into class
 * used in the guess map
 */

class SessionProfile {

    @Setter
    @Getter
    private String playerName;

    @Setter
    @Getter
    private int guessCount;

    @Setter
    @Getter
    private int guessTarget;

    @Setter
    @Getter
    private boolean reg;

    @Setter
    @Getter
    private boolean finish;

    @Setter
    @Getter
    private long lastInput;

    @Setter
    @Getter
    private boolean gotCorrect;

    SessionProfile() {
        reg = false;
        playerName = "Unknown";
        guessCount = 0;

        finish = false;
        lastInput = System.nanoTime();
        gotCorrect = false;
    }

    void countIncrement() {
        guessCount++;
    }

    void refresh() {
        this.guessCount = 0;
        this.finish = false;
        this.gotCorrect = false;
    }
}