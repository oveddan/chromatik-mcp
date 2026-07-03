package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;

class ProjectsTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

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
}
