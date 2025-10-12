package com.gatto.dragon.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for ProbabilityCalibrator.
 * Note: in production alpha = 10.0 (see class code).
 * If you change alpha — update expected values in tests.
 */
class ProbabilityCalibratorTest {

    private ProbabilityCalibrator cal;

    @BeforeEach
    void setUp() {
        cal = new ProbabilityCalibrator();
    }

    @Test
    @DisplayName("Known label returns prior when no stats recorded")
    void knownLabelReturnsPriorWithoutStats() {
        // from PRIOR: "sure thing" = 0.95; "risky" = 0.40
        assertThat(cal.calibratedProb("sure thing")).isEqualTo(0.95);
        assertThat(cal.calibratedProb("risky")).isEqualTo(0.40);
    }

    @Test
    @DisplayName("Unknown and null labels fall back to default 0.5 when no stats")
    void unknownOrNullFallsBackToDefault() {
        assertThat(cal.calibratedProb("totally new label")).isEqualTo(0.5);
        assertThat(cal.calibratedProb(null)).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Case-insensitive labels are treated identically")
    void labelsAreCaseInsensitive() {
        // preparation: recorded 3 successes and 1 failure
        cal.recordOutcome("RiSkY", true);
        cal.recordOutcome("risky", true);
        cal.recordOutcome("RISKY", true);
        cal.recordOutcome("risky", false);

        // both forms should give the same result
        double p1 = cal.calibratedProb("risky");
        double p2 = cal.calibratedProb("RISKY");
        assertThat(p1).isEqualTo(p2);
    }

    @Test
    @DisplayName("recordOutcome increments successes and attempts as expected")
    void recordOutcomeIncrementsCounters() {
        // risky prior = 0.40; alpha = 10
        // add 2 successes and 1 failure -> successes=2, attempts=3
        cal.recordOutcome("risky", true);
        cal.recordOutcome("risky", true);
        cal.recordOutcome("risky", false);

        // check via snapshot (raw counters are visible there)
        var row = cal.snapshot().stream()
                .filter(r -> r.label().equals("risky"))
                .findFirst()
                .orElseThrow();

        assertThat(row.successes()).isEqualTo(2.0);
        assertThat(row.attempts()).isEqualTo(3.0);
        assertThat(row.empirical()).isEqualTo(2.0 / 3.0);
    }

    @Test
    @DisplayName("calibratedProb matches Bayesian smoothing with alpha=10")
    void calibratedProbMatchesFormula() {
        // take "risky": prior=0.40, alpha=10
        // record successes=3, attempts=5
        for (int i = 0; i < 3; i++) cal.recordOutcome("risky", true);
        for (int i = 0; i < 2; i++) cal.recordOutcome("risky", false);

        double prior = 0.40;
        double alpha = 10.0;
        double successes = 3.0;
        double attempts  = 5.0;

        double expected = (successes + alpha * prior) / (attempts + alpha);

        assertThat(cal.calibratedProb("risky"))
                .isCloseTo(expected,  within(1e-6));
    }

    @Test
    @DisplayName("snapshot contains prior-only rows and is sorted by calibrated ascending")
    void snapshotContainsAllAndSortedByCalibrated() {
        // Add data for two labels
        cal.recordOutcome("sure thing", true); // greatly increases empirical
        cal.recordOutcome("risky", false);     // decreases empirical

        var rows = cal.snapshot();

        // At least these two labels should be present
        assertThat(rows.stream().anyMatch(r -> r.label().equals("sure thing"))).isTrue();
        assertThat(rows.stream().anyMatch(r -> r.label().equals("risky"))).isTrue();

        // List is sorted by calibrated in ascending order
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i).calibrated())
                    .isGreaterThanOrEqualTo(rows.get(i - 1).calibrated());
        }
    }

    @Test
    @DisplayName("dump does not throw and prints a formatted table")
    void dumpIsSafe() {
        // Some data so dump outputs something
        cal.recordOutcome("piece of cake", true);
        cal.recordOutcome("piece of cake", true);
        cal.recordOutcome("piece of cake", false);

        // Check that it does not throw (do not intercept the log itself)
        cal.dump("G-123");
    }

}
