package dev.molang.iamzombieq.internal.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;

class SpiderVehicleHorizontalEnvelopeTest {
    private static final long TICK_NANOS =
            SharedConstants.MILLIS_PER_TICK * 1_000_000L;
    private static final SpiderVehicleHorizontalEnvelope.MotionBound UNIT =
            new SpiderVehicleHorizontalEnvelope.MotionBound(1.0, 0.0);

    @Test
    void normalAcceptedMovementConsumesItsMinimumWholeQuantum() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();

        SpiderVehicleHorizontalEnvelope.Assessment assessment =
                session.assess(frame(1, TICK_NANOS, 0.0, 0.0, 1.0, 0.0), UNIT);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                assessment.outcome());
        assertEquals(1L, assessment.requiredQuanta());
        assertEquals(1L, assessment.mintedTotal());
        assertEquals(1L, assessment.carryBeforeCommit());

        session.commitAccepted(assessment);
        assertEquals(1L, session.snapshot().consumedTotal());
        assertEquals(0L, session.snapshot().carry());
        assertEquals(1.0, session.snapshot().acceptedX());
        assertEquals(0.0, session.snapshot().acceptedZ());
    }

    @Test
    void listenerTickPreparedOriginAllowsTheFirstLegalPacketWithoutBypassCredit() {
        SpiderVehicleHorizontalEnvelope.Session prepared =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(40, 1_000),
                        12.0,
                        0.0,
                        -4.0,
                        0.0);

        SpiderVehicleHorizontalEnvelope.Assessment firstPacket =
                prepared.assess(
                        new SpiderVehicleHorizontalEnvelope.Frame(
                                new SpiderVehicleHorizontalEnvelope.Clock(
                                        41, 1_000 + TICK_NANOS),
                                12.0,
                                0.0,
                                -4.0,
                                13.0,
                                0.0,
                                -4.0),
                        UNIT);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                firstPacket.outcome());
        assertEquals(1L, firstPacket.mintedTotal());
        assertEquals(1L, firstPacket.requiredQuanta());
    }

    @Test
    void switchedVehicleStartsFromItsOwnPreparedServerSample() {
        SpiderVehicleHorizontalEnvelope.Session previous = sessionAtOrigin();
        previous.commitAccepted(
                previous.assess(
                        frame(1, TICK_NANOS, 0.0, 0.0, 1.0, 0.0),
                        UNIT));

        SpiderVehicleHorizontalEnvelope.Session switched =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(
                                20, 20 * TICK_NANOS),
                        100.0,
                        0.0,
                        200.0,
                        0.0);
        SpiderVehicleHorizontalEnvelope.Assessment firstOnSwitchedVehicle =
                switched.assess(
                        new SpiderVehicleHorizontalEnvelope.Frame(
                                new SpiderVehicleHorizontalEnvelope.Clock(
                                        21, 21 * TICK_NANOS),
                                100.0,
                                0.0,
                                200.0,
                                101.0,
                                0.0,
                                200.0),
                        UNIT);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                firstOnSwitchedVehicle.outcome());
        assertEquals(100.0, switched.snapshot().acceptedX());
        assertEquals(1L, firstOnSwitchedVehicle.mintedTotal());
    }

    @Test
    void sameTickPacketFloodCannotMintASecondQuantum() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Frame first =
                frame(1, TICK_NANOS, 0.0, 0.0, 1.0, 0.0);
        session.commitAccepted(session.assess(first, UNIT));

        SpiderVehicleHorizontalEnvelope.Assessment second =
                session.assess(
                        frame(1, TICK_NANOS, 1.0, 0.0, 2.0, 0.0), UNIT);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                second.outcome());
        assertEquals(1L, second.mintedTotal());
        assertEquals(0L, second.carryBeforeCommit());
    }

    @Test
    void delayedBatchSpendsPreviouslyMintedCarryWithoutMintingAgain() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        long now = 4 * TICK_NANOS;

        SpiderVehicleHorizontalEnvelope.Assessment first =
                session.assess(frame(4, now, 0.0, 0.0, 1.0, 0.0), UNIT);
        session.commitAccepted(first);
        assertEquals(3L, session.snapshot().carry());

        SpiderVehicleHorizontalEnvelope.Assessment remainder =
                session.assess(frame(4, now, 1.0, 0.0, 4.0, 0.0), UNIT);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                remainder.outcome());
        assertEquals(3L, remainder.requiredQuanta());
        assertEquals(4L, remainder.mintedTotal());
        session.commitAccepted(remainder);
        assertEquals(0L, session.snapshot().carry());
    }

    @Test
    void acceptedZeroDistancePacketsConsumeOneQuantumAndCannotBankABurst() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();

        for (long tick = 1; tick <= 10; tick++) {
            SpiderVehicleHorizontalEnvelope.Assessment stationary =
                    session.assess(
                            frame(
                                    tick,
                                    tick * TICK_NANOS,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0),
                            UNIT);
            assertEquals(
                    SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                    stationary.outcome());
            assertEquals(
                    1L,
                    stationary.requiredQuanta(),
                    "every accepted packet consumes one source-derived client-tick slot");
            session.commitAccepted(stationary);
            assertEquals(0L, session.snapshot().carry());
        }

        SpiderVehicleHorizontalEnvelope.Assessment sameTickBurst =
                session.assess(
                        frame(
                                10,
                                10 * TICK_NANOS,
                                0.0,
                                0.0,
                                1.0,
                                0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                sameTickBurst.outcome(),
                "stationary accepted packets must not leave reusable time credit");
    }

    @Test
    void stationaryPacketsDoNotInventAccelerationAsHiddenVelocity() {
        SpiderVehicleHorizontalEnvelope.MotionBound noDrag =
                new SpiderVehicleHorizontalEnvelope.MotionBound(1.0, 1.0);
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();

        for (long tick = 1; tick <= 10; tick++) {
            SpiderVehicleHorizontalEnvelope.Assessment stationary =
                    session.assess(
                            frame(
                                    tick,
                                    tick * TICK_NANOS,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0),
                            noDrag);
            assertEquals(1L, stationary.requiredQuanta());
            session.commitAccepted(stationary);
            assertEquals(
                    0.0,
                    session.snapshot().velocityBound(),
                    "zero displacement has no observable acceleration to add");
        }

        SpiderVehicleHorizontalEnvelope.Assessment hiddenBurst =
                session.assess(
                        frame(
                                11,
                                11 * TICK_NANOS,
                                0.0,
                                0.0,
                                11.0,
                                0.0),
                        noDrag);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                hiddenBurst.outcome(),
                "ten idle packets must not manufacture velocity for an eleven-block burst");
    }

    @Test
    void stationaryPacketOnlyDecaysExistingVelocityAndDuplicateKeepsIt() {
        SpiderVehicleHorizontalEnvelope.MotionBound ordinaryDrag =
                new SpiderVehicleHorizontalEnvelope.MotionBound(1.0, 0.91);
        SpiderVehicleHorizontalEnvelope.Session session =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(0, 0),
                        0.0,
                        0.0,
                        0.0,
                        2.0);

        SpiderVehicleHorizontalEnvelope.Assessment stationary =
                session.assess(
                        frame(
                                1,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        ordinaryDrag);
        session.commitAccepted(stationary);
        double decayed = Math.nextUp(2.0 * 0.91);
        assertEquals(decayed, session.snapshot().velocityBound());

        SpiderVehicleHorizontalEnvelope.Assessment duplicate =
                session.assess(
                        frame(
                                1,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        ordinaryDrag);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                duplicate.outcome());
        assertEquals(0L, duplicate.requiredQuanta());
        session.commitAccepted(duplicate);
        assertEquals(decayed, session.snapshot().velocityBound());
    }

    @Test
    void sameTickStationaryDuplicatePassesWithoutMintingOrConsumingCredit() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Assessment first =
                session.assess(
                        frame(
                                1,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        UNIT);
        assertEquals(1L, first.requiredQuanta());
        session.commitAccepted(first);

        SpiderVehicleHorizontalEnvelope.Assessment duplicate =
                session.assess(
                        frame(
                                1,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                duplicate.outcome());
        assertEquals(0L, duplicate.requiredQuanta());
        assertEquals(0L, duplicate.carryBeforeCommit());
        session.commitAccepted(duplicate);
        assertEquals(1L, session.snapshot().mintedTotal());
        assertEquals(1L, session.snapshot().consumedTotal());
    }

    @Test
    void lowTpsUsesWallQuantaWhileTheTwoClocksAreNeverAdded() {
        SpiderVehicleHorizontalEnvelope.Session stalled = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Assessment legalDuringStall =
                stalled.assess(
                        frame(1, 4 * TICK_NANOS, 0.0, 0.0, 4.0, 0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                legalDuringStall.outcome());
        assertEquals(4L, legalDuringStall.mintedTotal());

        SpiderVehicleHorizontalEnvelope.Session serverAhead = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Assessment fourNotEight =
                serverAhead.assess(
                        frame(4, 4 * TICK_NANOS, 0.0, 0.0, 5.0, 0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                fourNotEight.outcome(),
                "server and wall clocks must use max, never sum");
        assertEquals(4L, fourNotEight.mintedTotal());
    }

    @Test
    void switchingWhichCumulativeClockLeadsDoesNotRecountOlderTime() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Assessment wallLeads =
                session.assess(
                        frame(1, 4 * TICK_NANOS, 0.0, 0.0, 1.0, 0.0),
                        UNIT);
        assertEquals(4L, wallLeads.mintedTotal());
        session.commitAccepted(wallLeads);

        SpiderVehicleHorizontalEnvelope.Assessment serverCatchesUp =
                session.assess(
                        frame(5, 4 * TICK_NANOS, 1.0, 0.0, 6.0, 0.0),
                        UNIT);
        assertEquals(5L, serverCatchesUp.mintedTotal());
        assertEquals(4L, serverCatchesUp.carryBeforeCommit());
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                serverCatchesUp.outcome(),
                "the server clock taking the lead mints only max(5,4)-max(1,4)");
    }

    @Test
    void elapsedTimeWithoutPacketsCanBeBankedButAnAcceptedIdlePacketConsumesOneSlot() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        long now = 10 * TICK_NANOS;
        SpiderVehicleHorizontalEnvelope.Assessment idle =
                session.assess(frame(10, now, 0.0, 0.0, 0.0, 0.0), UNIT);
        assertEquals(1L, idle.requiredQuanta());
        session.commitAccepted(idle);
        assertEquals(9L, session.snapshot().carry());

        SpiderVehicleHorizontalEnvelope.Assessment banked =
                session.assess(frame(10, now, 0.0, 0.0, 9.0, 0.0), UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                banked.outcome());
        assertEquals(9L, banked.requiredQuanta());

        SpiderVehicleHorizontalEnvelope.Assessment tooFar =
                session.assess(frame(10, now, 0.0, 0.0, 10.0, 0.0), UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                tooFar.outcome());
    }

    @Test
    void phaseCeilingIsAppliedOnceAtTheSessionOriginNotPerPacket() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();

        SpiderVehicleHorizontalEnvelope.Assessment first =
                session.assess(frame(0, 1_000_000L, 0.0, 0.0, 1.0, 0.0), UNIT);
        assertEquals(1L, first.mintedTotal());
        session.commitAccepted(first);

        SpiderVehicleHorizontalEnvelope.Assessment second =
                session.assess(
                        frame(0, 2_000_000L, 1.0, 0.0, 2.0, 0.0), UNIT);
        assertEquals(1L, second.mintedTotal());
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                second.outcome(),
                "ceil(delta-per-packet) would mint a second illegal quantum");
    }

    @Test
    void acceptedSampleResetCannotRecountMintedTime() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();

        SpiderVehicleHorizontalEnvelope.Assessment first =
                session.assess(
                        frame(2, 2 * TICK_NANOS, 0.0, 0.0, 1.0, 0.0),
                        UNIT);
        session.commitAccepted(first);
        assertEquals(1L, session.snapshot().carry());

        SpiderVehicleHorizontalEnvelope.Assessment afterSampleCommit =
                session.assess(
                        frame(3, 3 * TICK_NANOS, 1.0, 0.0, 4.0, 0.0),
                        UNIT);
        assertEquals(3L, afterSampleCommit.mintedTotal());
        assertEquals(2L, afterSampleCommit.carryBeforeCommit());
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                afterSampleCommit.outcome(),
                "resetting consumedTotal at sample commit would recount old time");
    }

    @Test
    void recurrenceFindsTheMinimumWholeQuantaAndTracksVelocityBound() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.MotionBound noDrag =
                new SpiderVehicleHorizontalEnvelope.MotionBound(1.0, 1.0);

        SpiderVehicleHorizontalEnvelope.Assessment assessment =
                session.assess(
                        frame(2, 2 * TICK_NANOS, 0.0, 0.0, 2.0, 0.0),
                        noDrag);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                assessment.outcome());
        assertEquals(
                2L,
                assessment.requiredQuanta(),
                "one quantum covers 1 block; two cover the source recurrence's 3");
        session.commitAccepted(assessment);
        assertEquals(2.0, session.snapshot().velocityBound(), Math.ulp(2.0) * 8);
    }

    @Test
    void onlyOneUlpExpansionIsNotAnEmpiricalDistanceTolerance() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        double oneUlp = Math.nextUp(1.0);
        SpiderVehicleHorizontalEnvelope.Assessment allowed =
                session.assess(
                        frame(1, TICK_NANOS, 0.0, 0.0, oneUlp, 0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                allowed.outcome());

        SpiderVehicleHorizontalEnvelope.Assessment beyondUlp =
                session.assess(
                        frame(
                                1,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                Math.nextUp(oneUlp),
                                0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REJECT,
                beyondUlp.outcome());
    }

    @Test
    void rejectedOrMerelyAssessedPacketNeverBecomesTheAcceptedBaseline() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Assessment allowedButNotCommitted =
                session.assess(
                        frame(1, TICK_NANOS, 0.0, 0.0, 1.0, 0.0), UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                allowedButNotCommitted.outcome());
        assertEquals(0.0, session.snapshot().acceptedX());

        SpiderVehicleHorizontalEnvelope.Assessment mismatchedServerBaseline =
                session.assess(
                        frame(1, TICK_NANOS, 1.0, 0.0, 2.0, 0.0), UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REBASE,
                mismatchedServerBaseline.outcome());
        assertEquals(0.0, session.snapshot().acceptedX());
    }

    @Test
    void clockRegressionAndOverflowRequireServerPositionRebase() {
        SpiderVehicleHorizontalEnvelope.Session regression = sessionAtOrigin();
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REBASE,
                regression
                        .assess(
                                frame(-1, TICK_NANOS, 0.0, 0.0, 0.0, 0.0),
                                UNIT)
                        .outcome());

        SpiderVehicleHorizontalEnvelope.Session overflow =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(
                                0, Long.MIN_VALUE),
                        0.0,
                        0.0,
                        0.0,
                        0.0);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REBASE,
                overflow
                        .assess(
                                frame(
                                        1,
                                        Long.MAX_VALUE,
                                        0.0,
                                        0.0,
                                        1.0,
                                        0.0),
                                UNIT)
                        .outcome());

        regression.rebase(
                new SpiderVehicleHorizontalEnvelope.Clock(10, 10 * TICK_NANOS),
                5.0,
                0.0,
                7.0,
                0.0);
        SpiderVehicleHorizontalEnvelope.Assessment afterRebase =
                regression.assess(
                        frame(
                                11,
                                11 * TICK_NANOS,
                                5.0,
                                7.0,
                                6.0,
                                7.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                afterRebase.outcome());
        assertEquals(1L, afterRebase.mintedTotal());
    }

    @Test
    void positionOnlyCollisionRebasePreservesVelocityForTheNextLegalPacket() {
        SpiderVehicleHorizontalEnvelope.MotionBound noDrag =
                new SpiderVehicleHorizontalEnvelope.MotionBound(1.0, 1.0);
        SpiderVehicleHorizontalEnvelope.Session collision =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(0, 0),
                        0.0,
                        0.0,
                        0.0,
                        1.0);
        SpiderVehicleHorizontalEnvelope.Assessment collidedMove =
                collision.assess(
                        frame(
                                1,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                2.0,
                                0.0),
                        noDrag);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                collidedMove.outcome());
        assertEquals(
                2.0,
                collidedMove.resultingVelocityBound(),
                Math.ulp(2.0) * 8);

        // ClientboundMoveVehiclePacket corrects position only. Preserve the
        // admitted recurrence velocity even if the collided server entity now
        // reports zero delta movement.
        collision.rebase(
                new SpiderVehicleHorizontalEnvelope.Clock(
                        1, TICK_NANOS),
                0.5,
                0.0,
                0.0,
                collidedMove.resultingVelocityBound());
        assertEquals(
                collidedMove.resultingVelocityBound(),
                collision.snapshot().velocityBound());

        SpiderVehicleHorizontalEnvelope.Assessment nextLegalPacket =
                collision.assess(
                        frame(
                                2,
                                2 * TICK_NANOS,
                                0.5,
                                0.0,
                                3.5,
                                0.0),
                        noDrag);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                nextLegalPacket.outcome(),
                "position-only collision correction must not erase the client's conservative velocity recurrence");

        SpiderVehicleHorizontalEnvelope.Session existingBound =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(0, 0),
                        0.0,
                        0.0,
                        0.0,
                        2.0);
        existingBound.rebase(
                new SpiderVehicleHorizontalEnvelope.Clock(
                        1, TICK_NANOS),
                0.0,
                0.0,
                0.0,
                0.0);
        assertEquals(
                2.0,
                existingBound.snapshot().velocityBound(),
                "position-only time/context rebases retain the prior model bound");
    }

    @Test
    void verticalOnlyAuthoritativeDriftForcesRebaseAndClearsOldCredit() {
        SpiderVehicleHorizontalEnvelope.Session session =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(0, 0),
                        0.0,
                        5.0,
                        0.0,
                        0.0);
        SpiderVehicleHorizontalEnvelope.Assessment idle =
                session.assess(
                        frame3d(
                                4,
                                4 * TICK_NANOS,
                                0.0,
                                5.0,
                                0.0,
                                0.0,
                                5.0,
                                0.0),
                        UNIT);
        session.commitAccepted(idle);
        assertEquals(3L, session.snapshot().carry());

        SpiderVehicleHorizontalEnvelope.Assessment teleported =
                session.assess(
                        frame3d(
                                5,
                                5 * TICK_NANOS,
                                0.0,
                                20.0,
                                0.0,
                                0.0,
                                20.0,
                                0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REBASE,
                teleported.outcome());
        session.rebase(
                new SpiderVehicleHorizontalEnvelope.Clock(
                        5, 5 * TICK_NANOS),
                0.0,
                20.0,
                0.0,
                0.0);
        assertEquals(0L, session.snapshot().carry());
        assertEquals(20.0, session.snapshot().acceptedY());
    }

    @Test
    void acceptedVerticalCandidateUpdatesAuthoritativeHeight() {
        SpiderVehicleHorizontalEnvelope.Session session =
                SpiderVehicleHorizontalEnvelope.start(
                        new SpiderVehicleHorizontalEnvelope.Clock(0, 0),
                        0.0,
                        5.0,
                        0.0,
                        0.0);
        SpiderVehicleHorizontalEnvelope.Assessment rising =
                session.assess(
                        frame3d(
                                1,
                                TICK_NANOS,
                                0.0,
                                5.0,
                                0.0,
                                1.0,
                                6.0,
                                0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                rising.outcome());
        session.commitAccepted(rising);
        assertEquals(6.0, session.snapshot().acceptedY());

        SpiderVehicleHorizontalEnvelope.Assessment next =
                session.assess(
                        frame3d(
                                2,
                                2 * TICK_NANOS,
                                1.0,
                                6.0,
                                0.0,
                                1.0,
                                6.0,
                                0.0),
                        UNIT);
        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.ALLOW,
                next.outcome(),
                "a committed vertical candidate must not look like later server drift");
    }

    @Test
    void serverTickJumpThatWallTimeCannotRepresentRequiresRebase() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();

        SpiderVehicleHorizontalEnvelope.Assessment discontinuity =
                session.assess(
                        frame(
                                3,
                                TICK_NANOS,
                                0.0,
                                0.0,
                                1.0,
                                0.0),
                        UNIT);

        assertEquals(
                SpiderVehicleHorizontalEnvelope.Outcome.REBASE,
                discontinuity.outcome());
    }

    @Test
    void staleAssessmentCannotBeCommittedAfterAnotherPacketWasAccepted() {
        SpiderVehicleHorizontalEnvelope.Session session = sessionAtOrigin();
        SpiderVehicleHorizontalEnvelope.Frame frame =
                frame(2, 2 * TICK_NANOS, 0.0, 0.0, 1.0, 0.0);
        SpiderVehicleHorizontalEnvelope.Assessment first =
                session.assess(frame, UNIT);
        SpiderVehicleHorizontalEnvelope.Assessment stale =
                session.assess(frame, UNIT);
        session.commitAccepted(first);

        assertThrows(
                IllegalStateException.class,
                () -> session.commitAccepted(stale));
    }

    private static SpiderVehicleHorizontalEnvelope.Session sessionAtOrigin() {
        return SpiderVehicleHorizontalEnvelope.start(
                new SpiderVehicleHorizontalEnvelope.Clock(0, 0),
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private static SpiderVehicleHorizontalEnvelope.Frame frame(
            long serverTick,
            long monotonicNanos,
            double serverX,
            double serverZ,
            double candidateX,
            double candidateZ) {
        return new SpiderVehicleHorizontalEnvelope.Frame(
                new SpiderVehicleHorizontalEnvelope.Clock(
                        serverTick, monotonicNanos),
                serverX,
                0.0,
                serverZ,
                candidateX,
                0.0,
                candidateZ);
    }

    private static SpiderVehicleHorizontalEnvelope.Frame frame3d(
            long serverTick,
            long monotonicNanos,
            double serverX,
            double serverY,
            double serverZ,
            double candidateX,
            double candidateY,
            double candidateZ) {
        return new SpiderVehicleHorizontalEnvelope.Frame(
                new SpiderVehicleHorizontalEnvelope.Clock(
                        serverTick, monotonicNanos),
                serverX,
                serverY,
                serverZ,
                candidateX,
                candidateY,
                candidateZ);
    }
}
