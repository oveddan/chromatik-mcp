package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import lxmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXPath;

class ProjectsTest extends HeadlessLxTest {

  @Test
  void reportsVersionPathAndChannelCount() {
    LX lx = newHeadlessLx();
    Projects.ProjectInfo info = Projects.info(lx);
    assertEquals(LX.VERSION, info.lxVersion());
    assertNull(info.projectPath(), "headless LX has no project file");
    assertEquals(lx.engine.mixer.channels.size(), info.channelCount());
  }

  @Test
  void reportsOscEngineState() {
    LX lx = newHeadlessLx();
    Projects.OscInfo osc = Projects.info(lx).osc();
    assertEquals(lx.engine.osc.receivePort.getValuei(), osc.receivePort());
    assertEquals(lx.engine.osc.receiveActive.isOn(), osc.receiveActive());
    assertEquals(lx.engine.osc.transmitPort.getValuei(), osc.transmitPort());
    assertEquals(lx.engine.osc.transmitActive.isOn(), osc.transmitActive());
  }

  @Test
  void channelCountTracksMixer() {
    LX lx = newHeadlessLx();
    int before = Projects.info(lx).channelCount();
    lx.engine.mixer.addChannel();
    assertEquals(before + 1, Projects.info(lx).channelCount());
  }

  @Test
  void reportsOutputEngineState() {
    LX lx = newHeadlessLx();
    Projects.OutputInfo output = Projects.info(lx).output();
    assertEquals(lx.engine.output.enabled.isOn(), output.enabled());
    assertEquals(lx.engine.output.brightness.getValue(), output.brightness());
    assertSame(lx.engine.output.enabled, LXPath.get(lx, output.enabledPath()),
        "output.enabled path must round-trip through LXPath");
    assertSame(lx.engine.output.brightness, LXPath.get(lx, output.brightnessPath()),
        "output.brightness path must round-trip through LXPath");
  }

  @Test
  void outputSnapshotTracksEngineToggle() {
    LX lx = newHeadlessLx();
    lx.engine.output.enabled.setValue(false);
    assertEquals(false, Projects.info(lx).output().enabled());
    lx.engine.output.enabled.setValue(true);
    assertEquals(true, Projects.info(lx).output().enabled());
  }
}
