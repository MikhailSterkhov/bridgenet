package me.moonways.bridgenet.test.engine.unit.step;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.test.engine.TestBridgenetBootstrap;
import me.moonways.bridgenet.test.engine.unit.TestClassUnit;
import me.moonways.bridgenet.test.engine.unit.TestRunnableStep;

@Log4j2
public class TestEmulateRunningStep implements TestRunnableStep {

    @Override
    public void process(TestBridgenetBootstrap bootstrap, TestClassUnit testUnit) throws Exception {
        log.info("TestEngine was running for §c{}", testUnit.getName());

        // emulation test running.
        Thread.sleep(2000);
    }
}
